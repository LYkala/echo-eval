"""Wireless ADB helpers. Guards against Git Bash/MSYS path mangling."""
from __future__ import annotations

import os
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


class AdbError(RuntimeError):
    pass


@dataclass
class DeviceState:
    serial: str
    state: str

    @property
    def ok(self) -> bool:
        return self.state == "device"


def _adb_env() -> dict[str, str]:
    """Prevent MSYS/Git Bash from rewriting /sdcard paths passed to adb.exe."""
    env = os.environ.copy()
    env.setdefault("MSYS_NO_PATHCONV", "1")
    env.setdefault("MSYS2_ARG_CONV_EXCL", "*")
    return env


def _remote_arg(path: str) -> str:
    """Make a device POSIX path safer when a shell still mangles single-leading-slash paths."""
    if path.startswith("/") and not path.startswith("//"):
        return "/" + path  # //sdcard/... → one leading slash on device after some converters
    return path


class AdbClient:
    def __init__(self, serial: str, adb_path: str = "") -> None:
        self.serial = serial
        self.adb_bin = adb_path.strip() or shutil.which("adb") or ""
        if not self.adb_bin:
            raise AdbError(
                "未找到 adb。请安装 Android platform-tools 并加入 PATH，"
                "或在 configs/device.yaml 设置 adb.adb_path。"
            )

    def _base(self) -> list[str]:
        return [self.adb_bin, "-s", self.serial]

    def run(
        self,
        args: Sequence[str],
        *,
        check: bool = True,
        timeout: float | None = None,
        text: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        cmd = [self.adb_bin, *args]
        try:
            proc = subprocess.run(
                cmd,
                capture_output=True,
                text=text,
                timeout=timeout,
                env=_adb_env(),
                check=False,
            )
        except subprocess.TimeoutExpired as e:
            raise AdbError(f"adb 超时: {' '.join(cmd)}") from e
        if check and proc.returncode != 0:
            err = (proc.stderr or proc.stdout or "").strip()
            raise AdbError(f"adb 失败 ({proc.returncode}): {' '.join(cmd)}\n{err}")
        return proc

    def version(self) -> str:
        proc = self.run(["version"], check=True)
        return (proc.stdout or "").strip().splitlines()[0] if proc.stdout else ""

    def connect(self, timeout_sec: float = 15) -> DeviceState:
        hostport = self.serial
        self.run(["connect", hostport], check=False, timeout=timeout_sec)
        deadline = time.time() + timeout_sec
        last = self.device_state()
        while time.time() < deadline:
            last = self.device_state()
            if last and last.ok:
                return last
            time.sleep(0.4)
            self.run(["connect", hostport], check=False, timeout=5)
        detail = last.state if last else "missing"
        raise AdbError(
            f"无线连接未就绪: {hostport} 当前状态={detail}。"
            "请确认设备与电脑同网、已开启无线调试，并在设备上允许调试。"
        )

    def devices(self) -> list[DeviceState]:
        proc = self.run(["devices"], check=True)
        out: list[DeviceState] = []
        for line in (proc.stdout or "").splitlines()[1:]:
            line = line.strip()
            if not line or line.startswith("*"):
                continue
            parts = line.split()
            if len(parts) >= 2:
                out.append(DeviceState(serial=parts[0], state=parts[1]))
        return out

    def device_state(self) -> DeviceState | None:
        for d in self.devices():
            if d.serial == self.serial:
                return d
        return None

    def shell(
        self,
        remote_cmd: str,
        *,
        check: bool = True,
        timeout: float | None = None,
    ) -> subprocess.CompletedProcess[str]:
        # Pass as one remote string so local shells do not split/convert pieces.
        return self.run(
            ["-s", self.serial, "shell", remote_cmd],
            check=check,
            timeout=timeout,
        )

    def shell_ok(self) -> bool:
        try:
            proc = self.shell("echo ok", check=True, timeout=10)
            return "ok" in (proc.stdout or "")
        except AdbError:
            return False

    def ensure_remote_dir(self, remote_dir: str) -> None:
        path = remote_dir.rstrip("/")
        self.shell(f'mkdir -p "{path}"', check=True, timeout=15)

    def screenrecord(self, remote_path: str, duration_sec: int) -> None:
        if duration_sec < 1:
            raise AdbError("duration_sec 必须 >= 1")
        dur = int(duration_sec)
        cmd = f'screenrecord --time-limit {dur} "{remote_path}"'
        self.shell(cmd, check=True, timeout=dur + 30)

    def screenrecord_start(
        self, remote_path: str, duration_sec: int
    ) -> subprocess.Popen[str]:
        """Start screenrecord in the background so host audio can play during capture."""
        if duration_sec < 1:
            raise AdbError("duration_sec 必须 >= 1")
        dur = int(duration_sec)
        remote_cmd = f'screenrecord --time-limit {dur} "{remote_path}"'
        cmd = [self.adb_bin, "-s", self.serial, "shell", remote_cmd]
        try:
            return subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=_adb_env(),
            )
        except OSError as e:
            raise AdbError(f"无法启动 screenrecord: {e}") from e

    def screenrecord_wait(
        self, proc: subprocess.Popen[str], *, duration_sec: int
    ) -> None:
        try:
            stdout, stderr = proc.communicate(timeout=int(duration_sec) + 30)
        except subprocess.TimeoutExpired as e:
            proc.kill()
            raise AdbError("screenrecord 等待超时") from e
        if proc.returncode != 0:
            err = (stderr or stdout or "").strip()
            raise AdbError(f"screenrecord 失败 ({proc.returncode}): {err}")

    def pull(self, remote_path: str, local_path: Path) -> Path:
        local_path = Path(local_path)
        local_path.parent.mkdir(parents=True, exist_ok=True)
        # Prefer double-slash form for the remote arg when invoking Windows adb from MSYS.
        remote = _remote_arg(remote_path)
        self.run(
            ["-s", self.serial, "pull", remote, str(local_path)],
            check=True,
            timeout=120,
        )
        if not local_path.is_file() or local_path.stat().st_size <= 0:
            raise AdbError(f"pull 后本地文件无效: {local_path}")
        return local_path

    def rm_remote(self, remote_path: str) -> None:
        self.shell(f'rm -f "{remote_path}"', check=False, timeout=15)

    def has_screenrecord(self) -> bool:
        proc = self.shell("command -v screenrecord || which screenrecord", check=False)
        return bool((proc.stdout or "").strip())

    def press_home(self, *, timeout: float = 10) -> None:
        """Press Home (keycode 3) to return to the system launcher."""
        # Prefer numeric 3: some OEM builds are flaky with KEYCODE_HOME token
        # when the remote command is passed as a single adb shell argument.
        try:
            self.shell("input keyevent 3", check=True, timeout=timeout)
        except AdbError:
            self.shell("input keyevent KEYCODE_HOME", check=True, timeout=timeout)

    def force_stop(self, package: str, *, timeout: float = 15) -> None:
        """am force-stop <package> to clear previous case app process."""
        pkg = (package or "").strip()
        if not pkg:
            raise AdbError("force_stop: package 为空")
        self.shell(f"am force-stop {pkg}", check=True, timeout=timeout)

    def tap(self, x: int, y: int, *, timeout: float = 10) -> None:
        self.shell(f"input tap {int(x)} {int(y)}", check=True, timeout=timeout)

    def wm_size(self, *, timeout: float = 10) -> str:
        """Return raw `wm size` output, e.g. 'Physical size: 1080x2340'."""
        proc = self.shell("wm size", check=False, timeout=timeout)
        return (proc.stdout or proc.stderr or "").strip()

    def screencap_png(self, local_path: Path, *, timeout: float = 30) -> Path:
        """Capture device screen to a local PNG via screencap -p + pull."""
        local_path = Path(local_path)
        local_path.parent.mkdir(parents=True, exist_ok=True)
        remote = "/sdcard/echo_eval/pre_screencap.png"
        self.ensure_remote_dir("/sdcard/echo_eval")
        self.rm_remote(remote)
        self.shell(f'screencap -p "{remote}"', check=True, timeout=timeout)
        self.pull(_remote_arg(remote), local_path)
        self.rm_remote(remote)
        if not local_path.is_file() or local_path.stat().st_size <= 0:
            raise AdbError(f"screencap 后本地文件无效: {local_path}")
        return local_path
