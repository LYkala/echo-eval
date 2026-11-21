#!/usr/bin/env python3
"""SSH bridge to target mini_adb_auth (jump-host deployment)."""
from __future__ import annotations

import json
import os
import re
import base64
import time
import threading
from pathlib import Path
from typing import Optional

import paramiko

CREDS_FILE = Path(__file__).resolve().parent.parent / "android-emulator" / ("华为云服务器.txt")

DEFAULT_TARGET = (
    os.environ.get("EMU_TARGET_HOST", "121.37.53.201"),
    int(os.environ.get("EMU_TARGET_PORT", "40204")),
)
DEFAULT_JUMP = (
    os.environ.get("EMU_JUMP_HOST", "124.71.69.102"),
    int(os.environ.get("EMU_JUMP_PORT", "22")),
)
LAB = os.environ.get("EMU_LAB_REMOTE", "/root/aisf-voice-lab")
ADB = f"python3 {LAB}/mini_adb_auth.py"
MANAGE_SH = f"{LAB}/manage_emulator.sh"
LOCAL_MANAGE_SH = (
    Path(__file__).resolve().parent.parent
    / "android-emulator"
    / "aisf-voice-lab"
    / "manage_emulator.sh"
)
LOCAL_QOS_MONITOR = (
    Path(__file__).resolve().parent.parent
    / "android-emulator"
    / "aisf-voice-lab"
    / "emu_qos_monitor.py"
)
LOCAL_QOS_START = (
    Path(__file__).resolve().parent.parent
    / "android-emulator"
    / "aisf-voice-lab"
    / "start_emu_qos_monitor.sh"
)
ADB_IME_PKG = "com.android.adbkeyboard"
ADB_IME = "com.android.adbkeyboard/.AdbIME"
ADB_KEYBOARD_APK = Path(__file__).resolve().parent / "apks" / "adbkeyboard" / "ADBKeyboard.apk"


def _load_emu_env_files() -> None:
    """Load EMU_* from shell env files if process was started without them."""
    candidates = [
        os.environ.get("EMU_ENV_FILE", "").strip(),
        "/root/testApp/env_web.sh",
        str(Path(__file__).resolve().parent / "env_emu.sh"),
    ]
    for path in candidates:
        if not path:
            continue
        p = Path(path)
        if not p.is_file():
            continue
        for raw in p.read_text(encoding="utf-8", errors="replace").splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[7:].strip()
            if "=" not in line:
                continue
            key, val = line.split("=", 1)
            key = key.strip()
            val = val.strip().strip("'").strip('"')
            if key.startswith("EMU_") or key.startswith("WEB_DEVICE_"):
                os.environ.setdefault(key, val)


def _parse_creds():
    _load_emu_env_files()
    jump_pass = os.environ.get("EMU_JUMP_PASS", "")
    tgt_pass = os.environ.get("EMU_TARGET_PASS", "")
    jump_user = os.environ.get("EMU_JUMP_USER", "root")
    tgt_user = os.environ.get("EMU_TARGET_USER", "root")
    if CREDS_FILE.is_file():
        text = CREDS_FILE.read_text(encoding="utf-8", errors="replace")
        for line in text.splitlines():
            line = line.strip()
            if line.startswith("root") and "CLOUD" in line:
                jump_pass = jump_pass or line.split()[-1]
            if line.startswith("root") and "Clab" in line:
                tgt_pass = tgt_pass or line.split()[-1]
    if not tgt_pass:
        raise RuntimeError(
            "set EMU_TARGET_PASS or place credentials file beside android-emulator/"
        )
    return (jump_user, jump_pass), (tgt_user, tgt_pass)


class TargetAdb:
    """Persistent SSH to target; run mini_adb against emulator :5555."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._jump: Optional[paramiko.SSHClient] = None
        self._target: Optional[paramiko.SSHClient] = None
        self.width = 720
        self.height = 1280
        self.ready = False
        self.error = ""
        self.mode = os.environ.get("EMU_WEB_MODE", "jump").strip().lower()
        self._soft_kb_shown = False
        # Active emulator ADB host port (5555 for emulator-5554).
        self.adb_port = int(os.environ.get("EMU_ADB_PORT", "5555") or 5555)
        self.active_avd = os.environ.get("EMU_ACTIVE_AVD", "") or ""

    def is_alive(self) -> bool:
        """True if the SSH transport to the ARM host is still usable."""
        t = self._target
        if not t:
            return False
        try:
            tr = t.get_transport()
            return bool(tr and tr.is_active())
        except Exception:
            return False

    def ping(self, timeout: float = 12.0) -> bool:
        """Cheap remote check; reconnects once via _exec if transport died."""
        with self._lock:
            try:
                code, out, _err = self._exec("echo __emu_ping__", timeout=timeout)
                return code == 0 and "__emu_ping__" in (out or "")
            except Exception:
                self.ready = False
                return False

    def _connect_unlocked(self) -> None:
        """Establish SSH. Caller must hold self._lock."""
        self._close_unlocked()
        (_ju, jp), (tu, tp) = _parse_creds()
        th, tport = DEFAULT_TARGET
        on_jump = self.mode == "jump" or os.environ.get("EMU_ON_JUMP", "").strip().lower() in (
            "1",
            "true",
            "yes",
        )
        if not jp:
            on_jump = True

        target = paramiko.SSHClient()
        target.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        if on_jump:
            target.connect(th, tport, username=tu, password=tp, timeout=60, banner_timeout=60)
            self._jump = None
        else:
            jh, jport = DEFAULT_JUMP
            ju, _ = _parse_creds()[0]
            jump = paramiko.SSHClient()
            jump.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            jump.connect(jh, jport, username=ju, password=jp, timeout=60, banner_timeout=60)
            jump.get_transport().set_keepalive(30)
            chan = jump.get_transport().open_channel(
                "direct-tcpip", (th, tport), ("127.0.0.1", 0)
            )
            target.connect(
                th, tport, username=tu, password=tp, sock=chan, timeout=60, banner_timeout=60
            )
            self._jump = jump
        target.get_transport().set_keepalive(30)
        self._target = target
        self.ready = True
        self.error = ""
        self._refresh_size_unlocked()
        # Emulator may have just booted/restarted — apply DDS guest net async.
        threading.Thread(target=self.ensure_dds_guest_net, daemon=True).start()

    def connect(self) -> None:
        with self._lock:
            self._connect_unlocked()

    def _close_unlocked(self) -> None:
        for c in (self._target, self._jump):
            try:
                if c:
                    c.close()
            except Exception:
                pass
        self._target = None
        self._jump = None
        self.ready = False

    def close(self) -> None:
        with self._lock:
            self._close_unlocked()

    def _ensure_transport_unlocked(self) -> None:
        """Reconnect if SSH dropped. Caller must hold self._lock."""
        if self.is_alive():
            return
        self._connect_unlocked()

    def ensure_guest_wifi(self) -> None:
        """Reconnect emulator default AndroidWifi (often lost after qemu restart)."""
        try:
            self._shell_ok("svc", "wifi", "enable", timeout=15)
            self._shell_ok(
                "su", "0", "cmd", "wifi", "connect-network", "AndroidWifi", "open",
                timeout=25,
            )
            time.sleep(2)
        except Exception:
            pass

    def ensure_guest_geo(
        self,
        lon: Optional[float] = None,
        lat: Optional[float] = None,
        alt: Optional[float] = None,
    ) -> dict:
        """Inject mock GPS (Xi'an by default).

        On this headless AVD, `adb emu geo fix` alone often leaves fused/gps at the
        US default; override gps/network/fused via `cmd location` test providers.
        """
        lon_v = float(lon if lon is not None else os.environ.get("EMU_GEO_LON", "108.9398"))
        lat_v = float(lat if lat is not None else os.environ.get("EMU_GEO_LAT", "34.3416"))
        alt_v = float(alt if alt is not None else os.environ.get("EMU_GEO_ALT", "400"))
        loc = f"{lat_v:.6f},{lon_v:.6f}"
        try:
            self._shell_ok("settings", "put", "secure", "location_mode", "3")
            self._shell_ok(
                "settings", "put", "secure", "location_providers_allowed", "+gps,+network"
            )
            for pkg in ("com.android.shell", "2000"):
                try:
                    self._shell_ok("appops", "set", pkg, "android:mock_location", "allow")
                except Exception:
                    pass
            for p in ("gps", "network", "fused", "mockgps"):
                try:
                    self.adb("shell", "cmd", "location", "providers", "remove-test-provider", p, timeout=15.0)
                except Exception:
                    pass
            self.adb(
                "shell", "cmd", "location", "providers", "add-test-provider", "gps",
                "--requiresSatellite", "--supportsAltitude", "--supportsSpeed", "--supportsBearing",
                timeout=20.0,
            )
            self.adb(
                "shell", "cmd", "location", "providers", "add-test-provider", "network",
                "--requiresNetwork", "--supportsAltitude",
                timeout=20.0,
            )
            self.adb(
                "shell", "cmd", "location", "providers", "add-test-provider", "fused",
                "--supportsAltitude", "--supportsSpeed", "--supportsBearing",
                timeout=20.0,
            )
            for p in ("gps", "network", "fused"):
                self.adb(
                    "shell", "cmd", "location", "providers", "set-test-provider-enabled", p, "true",
                    timeout=15.0,
                )
                self.adb(
                    "shell", "cmd", "location", "providers", "set-test-provider-location", p,
                    "--location", loc, "--accuracy", "5",
                    timeout=15.0,
                )
            try:
                self.adb(
                    "emu", "geo", "fix",
                    f"{lon_v:.6f}", f"{lat_v:.6f}", f"{alt_v:.1f}",
                    timeout=20.0,
                )
            except Exception:
                pass
            return {"ok": True, "lon": lon_v, "lat": lat_v, "alt": alt_v, "location": loc}
        except Exception as e:
            return {"ok": False, "lon": lon_v, "lat": lat_v, "alt": alt_v, "error": str(e)}

    def ensure_hide_a11y_button(self) -> None:
        """Hide system Accessibility floating button (Android robot icon).

        Keeps accessibility services enabled; only clears the shortcut button that
        overlays maps and steals mouse clicks in the web control panel.
        """
        try:
            self.adb("shell", "settings", "delete", "secure", "accessibility_button_targets", timeout=15.0)
        except Exception:
            pass
        try:
            self._shell_ok("settings", "put", "secure", "accessibility_button_mode", "0")
        except Exception:
            pass

    def ensure_dds_guest_net(self) -> None:
        """Best-effort: guest CONNECT gw + iptables after emu boot/reconnect."""
        script = f"{LAB}/start_emu_dds_guest_gw.sh"
        watch = f"{LAB}/install_emu_dds_net_watch.sh"
        try:
            with self._lock:
                if not self._target:
                    return
                self._exec(
                    f"test -x {script} && bash {script}; "
                    f"test -x {watch} && bash {watch}; true",
                    timeout=90.0,
                )
        except Exception:
            # DDS net is best-effort; do not fail the ADB bridge.
            pass
        # WiFi must be up for TestApp -> WebServer (jump:9527)
        self.ensure_guest_wifi()
        self.ensure_guest_geo()
        self.ensure_hide_a11y_button()

    def restart_emulator(self, timeout: float = 480.0) -> str:
        """Stop/start qemu on target, wait for boot, apply DDS guest net."""
        script = f"{LAB}/restart_emulator.sh"
        avd = (self.active_avd or "").strip()
        # Prefer manage_emulator start for named AVDs (Pulse + hostmicon).
        if avd and avd not in ("AISF_EulerOS_ARM64",):
            # stop+start via manage script keeps Pulse binding.
            self.manage_emulator("stop", "--name", avd, timeout=120.0)
            started = self.manage_emulator(
                "start", "--name", avd, "--port", "5556", timeout=timeout
            )
            if not started.get("ok"):
                raise RuntimeError(started.get("error") or str(started))
            # Re-apply DDS guest net after boot.
            try:
                self.ensure_guest_net(timeout=120.0, force=True)
            except Exception:
                pass
            return json.dumps(started, ensure_ascii=False)
        env_prefix = ""
        if avd:
            env_prefix = f"AISF_AVD_NAME={self._q(avd)} "
        with self._lock:
            if not self._target:
                raise RuntimeError("not connected")
            code, out, err = self._exec(
                f"test -x {script} || chmod +x {script}; {env_prefix}bash {script}",
                timeout=timeout,
            )
            text = ((out or "") + (err or "")).strip()
            if code != 0:
                raise RuntimeError(text or f"restart exit {code}")
            self._refresh_size_unlocked()
            return text

    def _adb_bin(self) -> str:
        """mini_adb_auth invocation, optionally targeting a non-default serial."""
        port = int(self.adb_port or 5555)
        if port == 5555:
            return ADB
        console = port - 1
        return f"{ADB} -s emulator-{console}"

    def set_active_emulator(self, *, adb_port: int, avd_name: str = "") -> None:
        """Switch MJPEG/control bridge to a specific emulator ADB port."""
        self.adb_port = int(adb_port)
        self.active_avd = avd_name or self.active_avd
        with self._lock:
            try:
                self._refresh_size_unlocked()
            except Exception:
                pass

    def ensure_manage_script(self) -> None:
        """Upload manage_emulator.sh to the ARM lab dir if local copy exists."""
        with self._lock:
            self._ensure_manage_script_unlocked()

    def _ensure_manage_script_unlocked(self) -> None:
        if not LOCAL_MANAGE_SH.is_file():
            return
        if not self._target:
            raise RuntimeError("not connected")
        sftp = self._target.open_sftp()
        try:
            remote = MANAGE_SH
            sftp.put(str(LOCAL_MANAGE_SH), remote)
            self._exec(f"chmod +x {self._q(remote)}", timeout=15)
        finally:
            try:
                sftp.close()
            except Exception:
                pass

    def manage_emulator(self, action: str, *extra_args: str, timeout: float = 600.0) -> dict:
        """Run manage_emulator.sh action; return parsed JSON object."""
        import json

        args = " ".join(self._q(a) for a in extra_args)
        cmd = (
            f"test -x {MANAGE_SH} || chmod +x {MANAGE_SH}; "
            f"bash {MANAGE_SH} {self._q(action)} {args}"
        )
        with self._lock:
            if not self._target:
                raise RuntimeError("not connected")
            self._ensure_manage_script_unlocked()
        # Long jobs (start/clone) must not hold _lock, so list/progress can poll concurrently.
        code, out, err = self._exec(cmd, timeout=timeout)
        text = ((out or "") + "\n" + (err or "")).strip()
        # Prefer last JSON object in output
        payload = None
        for line in reversed(text.splitlines()):
            line = line.strip()
            if line.startswith("{") and line.endswith("}"):
                try:
                    payload = json.loads(line)
                    break
                except Exception:
                    continue
        if payload is None:
            try:
                payload = json.loads(text[text.find("{") : text.rfind("}") + 1])
            except Exception:
                payload = {"ok": code == 0, "raw": text[-2000:]}
        if code != 0 and not payload.get("ok"):
            raise RuntimeError(payload.get("error") or text[-500:] or f"exit {code}")
        return payload

    def read_manage_progress(self) -> dict:
        """Read live progress written by manage_emulator.sh (best-effort)."""
        import json

        cmd = f"cat {self._q(f'{LAB}/runs/manage_progress.json')} 2>/dev/null || true"
        try:
            code, out, err = self._exec(cmd, timeout=8.0)
            text = (out or "").strip()
            if not text:
                return {}
            data = json.loads(text)
            if not isinstance(data, dict):
                return {}
            pct = data.get("pct")
            try:
                pct_i = max(0, min(100, int(pct)))
            except Exception:
                return {}
            msg = str(data.get("msg") or "").strip()
            return {"pct": pct_i, "msg": msg}
        except Exception:
            return {}

    def ensure_qos_monitor(self, *, force_upload: bool = False) -> None:
        """Best-effort start ARM-side emu_qos_monitor (latency + smoothness).

        Uploads scripts at most once per bridge process unless force_upload=True.
        """
        try:
            with self._lock:
                need_upload = force_upload or not getattr(self, "_qos_scripts_synced", False)
                if need_upload and self._target and LOCAL_QOS_MONITOR.is_file() and LOCAL_QOS_START.is_file():
                    sftp = self._target.open_sftp()
                    try:
                        sftp.put(str(LOCAL_QOS_MONITOR), f"{LAB}/emu_qos_monitor.py")
                        sftp.put(str(LOCAL_QOS_START), f"{LAB}/start_emu_qos_monitor.sh")
                        self._exec(
                            f"chmod +x {LAB}/start_emu_qos_monitor.sh {LAB}/emu_qos_monitor.py",
                            timeout=10.0,
                        )
                        self._qos_scripts_synced = True
                    finally:
                        try:
                            sftp.close()
                        except Exception:
                            pass
                self._exec(
                    f"bash {LAB}/start_emu_qos_monitor.sh >/dev/null 2>&1 || true",
                    timeout=15.0,
                )
        except Exception:
            pass

    def read_emu_qos(self) -> dict:
        """Read /root/aisf-voice-lab/runs/emu_qos.json from ARM host."""
        try:
            with self._lock:
                code, out, _err = self._exec(
                    f"cat {LAB}/runs/emu_qos.json 2>/dev/null || true",
                    timeout=12.0,
                )
            if code != 0 or not (out or "").strip():
                return {}
            data = json.loads(out)
            return data if isinstance(data, dict) else {}
        except Exception:
            return {}

    def list_emulators(self) -> list:
        data = self.manage_emulator("list", timeout=60.0)
        items = list(data.get("emulators") or [])
        # Attach realtime latency / smoothness when monitor is running.
        try:
            # Do not re-upload scripts on every list poll; just read qos json.
            qos = self.read_emu_qos()
            if not qos:
                self.ensure_qos_monitor()
                qos = self.read_emu_qos()
            by_name = (qos.get("emulators") or {}) if isinstance(qos, dict) else {}
            updated_at = qos.get("updated_at") if isinstance(qos, dict) else None
            for it in items:
                name = it.get("name") or ""
                q = by_name.get(name) if isinstance(by_name, dict) else None
                if not isinstance(q, dict):
                    # also try match by serial
                    serial = it.get("serial") or ""
                    for _n, cand in (by_name or {}).items():
                        if isinstance(cand, dict) and cand.get("serial") == serial:
                            q = cand
                            break
                if not isinstance(q, dict):
                    continue
                it["latency_ms"] = q.get("latency_ms")
                it["gw_latency_ms"] = q.get("gw_latency_ms")
                it["net_latency_ms"] = q.get("net_latency_ms")
                it["hub_latency_ms"] = q.get("hub_latency_ms")
                it["latency_src"] = q.get("latency_src")
                it["jitter_ms"] = q.get("jitter_ms")
                smooth = q.get("smoothness_score")
                if smooth is None and q.get("stutter_score") is not None:
                    try:
                        smooth = max(0, min(100, 100 - int(q.get("stutter_score"))))
                    except Exception:
                        smooth = None
                it["smoothness_score"] = smooth
                it["qemu_cpu_pct"] = q.get("qemu_cpu_pct")
                it["guest_load1"] = q.get("guest_load1")
                it["qos_ok"] = q.get("ok")
                it["qos_updated_at"] = q.get("ts") or updated_at
        except Exception:
            pass
        return items

    def next_console_port(self) -> int:
        """Pick next free console port (5554, 5556, ...)."""
        used = set()
        for emu in self.list_emulators():
            if emu.get("console_port"):
                used.add(int(emu["console_port"]))
            if emu.get("adb_port"):
                used.add(int(emu["adb_port"]) - 1)
        port = 5554
        while port in used:
            port += 2
            if port > 5650:
                raise RuntimeError("no free emulator console port")
        return port

    def diagnose_virtual_mic(self, timeout: float = 45.0) -> dict:
        """Probe Pulse / VirtualMic / qemu host-audio binding on target."""
        avd = (self.active_avd or "").strip()
        sink = f"VirtualMic_{avd}" if avd else "VirtualMic"
        probe = rf"""
export PULSE_RUNTIME_PATH=/tmp/pulse-root XDG_RUNTIME_DIR=/tmp/pulse-root
SINK="${{AISF_VIRTUAL_MIC:-{sink}}}"
echo "SINK_NAME=$SINK"
if pactl info >/dev/null 2>&1; then
  echo PULSE_OK=1
else
  echo PULSE_OK=0
fi
if pactl list short sinks 2>/dev/null | awk '{{print $2}}' | grep -qx "$SINK"; then
  echo SINK_OK=1
else
  echo SINK_OK=0
fi
if pactl list short sources 2>/dev/null | awk '{{print $2}}' | grep -qx "${{SINK}}.monitor"; then
  echo SOURCE_OK=1
else
  echo SOURCE_OK=0
fi
DEF="$(pactl get-default-source 2>/dev/null || true)"
echo "DEFAULT_SOURCE=$DEF"
# Multi-AVD: qemu uses PULSE_SOURCE=VirtualMic_<avd>.monitor; default-source need not match.
if [ "$DEF" = "${{SINK}}.monitor" ]; then
  echo DEFAULT_OK=1
else
  echo DEFAULT_OK=0
fi
AVD_NAME="{avd}"
if [ -n "$AVD_NAME" ]; then
  PID="$(pgrep -f "qemu-system-aarch64.*-avd ${{AVD_NAME}}( |$)" | head -1 || true)"
else
  PID="$(pgrep -f qemu-system-aarch64-headless | head -1 || true)"
fi
echo "QEMU_PID=${{PID:-}}"
if [ -n "$PID" ] && [ -r "/proc/$PID/environ" ]; then
  ENV="$(tr '\\0' '\\n' < "/proc/$PID/environ")"
  echo "$ENV" | grep -q '^PULSE_RUNTIME_PATH=' && echo QEMU_PULSE_ENV=1 || echo QEMU_PULSE_ENV=0
  echo "$ENV" | grep -q "^PULSE_SOURCE=${{SINK}}.monitor" && echo QEMU_PULSE_SOURCE_OK=1 || echo QEMU_PULSE_SOURCE_OK=0
  CMDLINE="$(tr '\\0' ' ' < "/proc/$PID/cmdline" 2>/dev/null || true)"
  echo "$CMDLINE" | grep -q -- '-allow-host-audio' && echo QEMU_HOST_AUDIO=1 || echo QEMU_HOST_AUDIO=0
  echo "$CMDLINE" | grep -q -- '-audio sdl' && echo QEMU_AUDIO_SDL=1 || echo QEMU_AUDIO_SDL=0
else
  echo QEMU_PULSE_ENV=0
  echo QEMU_PULSE_SOURCE_OK=0
  echo QEMU_HOST_AUDIO=0
  echo QEMU_AUDIO_SDL=0
fi
"""
        with self._lock:
            if not self._target:
                raise RuntimeError("not connected")
            _code, out, err = self._exec(f"bash -lc {self._q(probe)}", timeout=timeout)
            text = ((out or "") + (err or "")).strip()

        flags: dict[str, str] = {}
        for line in text.splitlines():
            line = line.strip()
            if "=" in line and not line.startswith(" "):
                k, v = line.split("=", 1)
                if k in (
                    "SINK_NAME",
                    "PULSE_OK",
                    "SINK_OK",
                    "SOURCE_OK",
                    "DEFAULT_SOURCE",
                    "DEFAULT_OK",
                    "QEMU_PID",
                    "QEMU_PULSE_ENV",
                    "QEMU_PULSE_SOURCE_OK",
                    "QEMU_HOST_AUDIO",
                    "QEMU_AUDIO_SDL",
                ):
                    flags[k] = v

        def ok(key: str) -> bool:
            return flags.get(key) == "1"

        issues: list[dict] = []
        if not ok("PULSE_OK"):
            issues.append(
                {
                    "id": "pulse_down",
                    "level": "error",
                    "msg": "PulseAudio 未运行或无法连接",
                    "fixable": True,
                }
            )
        if not ok("SINK_OK"):
            issues.append(
                {
                    "id": "virtualmic_missing",
                    "level": "error",
                    "msg": f"缺少 VirtualMic sink（{flags.get('SINK_NAME') or sink}）",
                    "fixable": True,
                }
            )
        if ok("SINK_OK") and not ok("SOURCE_OK"):
            issues.append(
                {
                    "id": "virtualmic_monitor_missing",
                    "level": "error",
                    "msg": "缺少 VirtualMic.monitor 信源",
                    "fixable": True,
                }
            )
        if ok("SOURCE_OK") and not ok("DEFAULT_OK"):
            issues.append(
                {
                    "id": "default_source_wrong",
                    "level": "info",
                    "msg": (
                        f"系统默认麦不是 {flags.get('SINK_NAME') or sink}.monitor"
                        f"（当前: {flags.get('DEFAULT_SOURCE') or '无'}；多实例时以 PULSE_SOURCE 为准）"
                    ),
                    "fixable": False,
                }
            )
        if flags.get("QEMU_PULSE_SOURCE_OK") == "0" and ok("QEMU_PULSE_ENV"):
            issues.append(
                {
                    "id": "qemu_pulse_source_mismatch",
                    "level": "warn",
                    "msg": f"qemu 未绑定本机 sink 源 {flags.get('SINK_NAME') or sink}.monitor，建议按 AVD 重启",
                    "fixable": True,
                }
            )
        if not flags.get("QEMU_PID"):
            issues.append(
                {
                    "id": "qemu_down",
                    "level": "error",
                    "msg": "未发现 qemu 进程",
                    "fixable": False,
                    "need_restart": True,
                }
            )
        else:
            if not ok("QEMU_PULSE_ENV"):
                issues.append(
                    {
                        "id": "qemu_no_pulse_env",
                        "level": "error",
                        "msg": "qemu 未绑定 PULSE_RUNTIME_PATH（灌麦进不了模拟器，需重启虚拟机）",
                        "fixable": False,
                        "need_restart": True,
                    }
                )
            if not ok("QEMU_HOST_AUDIO"):
                issues.append(
                    {
                        "id": "qemu_no_host_audio",
                        "level": "error",
                        "msg": "qemu 未带 -allow-host-audio（需重启虚拟机）",
                        "fixable": False,
                        "need_restart": True,
                    }
                )

        # hostmicon cannot be queried reliably; always treat as soft check
        issues.append(
            {
                "id": "hostmicon_unknown",
                "level": "info",
                "msg": "hostmicon 状态无法直接查询，修复时会重新开启",
                "fixable": True,
            }
        )

        need_restart = any(i.get("need_restart") for i in issues)
        fixable = [i for i in issues if i.get("fixable") and i.get("level") in ("error", "warn")]
        return {
            "flags": flags,
            "issues": issues,
            "need_restart": need_restart,
            "has_fixable": bool(fixable),
            "healthy": not any(i.get("level") == "error" for i in issues if i["id"] != "hostmicon_unknown"),
            "raw": text[-800:],
        }

    def fix_virtual_mic(self, timeout: float = 90.0) -> dict:
        """Diagnose, restore Pulse VirtualMic + hostmicon, diagnose again."""
        before = self.diagnose_virtual_mic(timeout=min(45.0, timeout))
        script = f"{LAB}/setup_virtual_mic.sh"
        avd = (self.active_avd or "").strip()
        sink = f"VirtualMic_{avd}" if avd else "VirtualMic"
        with self._lock:
            if not self._target:
                raise RuntimeError("not connected")
            sink_q = self._q(sink)
            cmd = (
                f"export PULSE_RUNTIME_PATH=/tmp/pulse-root "
                f"XDG_RUNTIME_DIR=/tmp/pulse-root "
                f"AISF_VIRTUAL_MIC={sink_q}; "
                f"cd {LAB} && "
                f"test -x {script} || chmod +x {script}; "
                f"sed -i 's/\\r$//' {script}; "
                f"AISF_VIRTUAL_MIC={sink_q} bash {script} --ensure && "
                f"(python3 {LAB}/mini_adb_auth.py emu avd hostmicon || true)"
            )
            code, out, err = self._exec(cmd, timeout=timeout)
            text = ((out or "") + (err or "")).strip()
            if code != 0:
                raise RuntimeError(text or f"fix-mic exit {code}")

        after = self.diagnose_virtual_mic(timeout=min(45.0, timeout))
        fixed_ids = []
        before_errs = {
            i["id"]
            for i in before.get("issues", [])
            if i.get("level") in ("error", "warn") and i.get("fixable")
        }
        after_errs = {
            i["id"]
            for i in after.get("issues", [])
            if i.get("level") in ("error", "warn") and i.get("fixable")
        }
        for iid in sorted(before_errs - after_errs):
            fixed_ids.append(iid)

        remaining = [
            i
            for i in after.get("issues", [])
            if i.get("level") == "error" and i["id"] != "hostmicon_unknown"
        ]
        need_restart = bool(after.get("need_restart"))
        if need_restart:
            recommendation = "仍需「重启虚拟机」才能让 qemu 重新绑定麦克风"
        elif remaining:
            recommendation = "修麦后仍有问题，请查看诊断详情或重启虚拟机"
        elif before_errs or before.get("need_restart"):
            recommendation = "宿主机麦链路已修复；若仍无声且诊断提示需重启，请重启虚拟机"
            if before.get("need_restart"):
                recommendation = "Pulse/VirtualMic 已修复，但 qemu 未绑定 Pulse，请点「重启虚拟机」"
        else:
            recommendation = "诊断未见明显异常；已重开 hostmicon，可再试灌语音"

        return {
            "before": before,
            "after": after,
            "fixed_ids": fixed_ids,
            "need_restart": need_restart,
            "recommendation": recommendation,
            "fix_log": text[-600:],
        }

    def ensure_guest_net(self, timeout: float = 90.0, *, force: bool = False) -> dict:
        """Check (or force-refresh) in-guest DDS CONNECT gateway.

        Default is probe-only: do NOT kill gw / flush iptables (that drops live DDS WS).
        Pass force=True only for recovery after emu reboot.
        """
        probe = r"""
ADB='python3 /root/aisf-voice-lab/mini_adb_auth.py'
echo -n 'GW_PID='; $ADB shell 'su 0 cat /data/local/tmp/gw.pid 2>/dev/null' | tr -d '\r'
echo
echo -n 'GW_ALIVE='; $ADB shell 'su 0 sh -c "pidof guest_connect_gw || true"' | tr -d '\r'
echo
echo -n 'IPT_8731='; $ADB shell 'su 0 iptables -t nat -C OUTPUT -d 124.71.69.102 -p tcp --dport 8731 -j DNAT --to-destination 10.0.2.2:8731 2>/dev/null && echo 1 || echo 0' | tr -d '\r'
echo
echo -n 'IPT_443='; $ADB shell 'su 0 iptables -t nat -C OUTPUT -p tcp --dport 443 -j REDIRECT --to-ports 18744 2>/dev/null && echo 1 || echo 0' | tr -d '\r'
echo
"""
        with self._lock:
            if not self._target:
                raise RuntimeError("not connected")
            _c, out, err = self._exec(f"bash -lc {self._q(probe)}", timeout=min(30.0, timeout))
            probe_text = ((out or "") + (err or "")).strip()

        flags = {}
        for line in probe_text.splitlines():
            if "=" in line:
                k, v = line.split("=", 1)
                flags[k.strip()] = v.strip()

        alive = bool(flags.get("GW_ALIVE"))
        ipt_ok = flags.get("IPT_8731") == "1" and flags.get("IPT_443") == "1"
        healthy = alive and ipt_ok

        if healthy and not force:
            return {
                "ok": True,
                "force": False,
                "skipped_refresh": True,
                "flags": flags,
                "recommendation": "访客机网关已在运行，未刷新（避免打断 DDS 连接）",
                "fix_log": probe_text[-400:],
            }

        script = f"{LAB}/start_emu_dds_guest_gw.sh"
        with self._lock:
            if not self._target:
                raise RuntimeError("not connected")
            cmd = (
                f"cd {LAB} && sed -i 's/\\r$//' {script} 2>/dev/null; "
                f"chmod +x {LAB}/guest_connect_gw {script} 2>/dev/null; "
                f"bash {script}"
            )
            code, out, err = self._exec(cmd, timeout=timeout)
            log = ((out or "") + (err or "")).strip()
        ok = (
            ("CONNECT ok" in log)
            or ("DDS guest CONNECT gateway ready" in log)
            or (code == 0)
        )
        return {
            "ok": bool(ok),
            "force": bool(force),
            "skipped_refresh": False,
            "flags": flags,
            "recommendation": (
                "访客机 DDS CONNECT 网关已刷新"
                if ok
                else "访客机网关刷新可能失败，请查看 fix_log"
            ),
            "fix_log": (probe_text + "\n" + log)[-800:],
        }

    def ensure_virtual_mic(self, timeout: float = 90.0, *, force: bool = False) -> dict:
        """Pre-inject gate: diagnose, fix if unhealthy (or force), always refresh hostmicon.

        Prefer this over fix_virtual_mic when about to inject voice — skips full setup when
        already healthy, but still re-opens hostmicon (state drifts).
        """
        before = self.diagnose_virtual_mic(timeout=min(45.0, timeout))
        healthy = bool(before.get("healthy"))
        need_restart = bool(before.get("need_restart"))

        if force or not healthy:
            result = self.fix_virtual_mic(timeout=timeout)
            result["skipped_full_fix"] = False
            result["ensured"] = True
            after = result.get("after") or {}
            result["healthy"] = bool(after.get("healthy"))
            return result

        # Healthy: only refresh hostmicon (cheap) then re-check soft flags
        with self._lock:
            if not self._target:
                raise RuntimeError("not connected")
            cmd = (
                f"export PULSE_RUNTIME_PATH=/tmp/pulse-root "
                f"XDG_RUNTIME_DIR=/tmp/pulse-root; "
                f"cd {LAB} && "
                f"(python3 {LAB}/mini_adb_auth.py emu avd hostmicon || true)"
            )
            code, out, err = self._exec(cmd, timeout=min(30.0, timeout))
            host_log = ((out or "") + (err or "")).strip()
            if code != 0 and not host_log:
                host_log = f"hostmicon exit {code}"

        after = self.diagnose_virtual_mic(timeout=min(45.0, timeout))
        recommendation = "麦克风已就绪；已重开 hostmicon，可灌语音"
        if after.get("need_restart"):
            recommendation = "诊断显示 qemu 未绑定 Pulse，需「重启虚拟机」后再灌语音"
        return {
            "before": before,
            "after": after,
            "fixed_ids": [],
            "need_restart": bool(after.get("need_restart") or need_restart),
            "recommendation": recommendation,
            "fix_log": host_log[-400:],
            "skipped_full_fix": True,
            "ensured": True,
            "healthy": bool(after.get("healthy")),
        }

    @staticmethod
    def _is_ssh_dead_error(ex: BaseException) -> bool:
        msg = str(ex).lower()
        needles = (
            "not active",
            "not connected",
            "socket is closed",
            "connection reset",
            "connection lost",
            "broken pipe",
            "key-exchange timed out",
            "no existing session",
            "session not active",
            "channel closed",
            "server connection dropped",
        )
        if any(n in msg for n in needles):
            return True
        return isinstance(ex, (EOFError, ConnectionError, BrokenPipeError))

    def _exec(self, cmd: str, timeout: float = 30.0, binary: bool = False):
        """Run remote command. Caller typically holds self._lock (adb/adb_bytes)."""
        last_ex: Optional[BaseException] = None
        for attempt in range(2):
            try:
                self._ensure_transport_unlocked()
                if not self._target:
                    raise RuntimeError("not connected")
                _stdin, stdout, stderr = self._target.exec_command(cmd, timeout=timeout)
                if binary:
                    data = stdout.read()
                    err = stderr.read()
                    code = stdout.channel.recv_exit_status()
                    return code, data, err
                out = stdout.read().decode("utf-8", "replace")
                err = stderr.read().decode("utf-8", "replace")
                code = stdout.channel.recv_exit_status()
                return code, out, err
            except Exception as ex:
                last_ex = ex
                if attempt == 0 and self._is_ssh_dead_error(ex):
                    try:
                        self._close_unlocked()
                    except Exception:
                        pass
                    continue
                raise
        raise RuntimeError(f"宿主机 SSH 断开且重连失败: {last_ex}")

    @staticmethod
    def _q(s: str) -> str:
        return "'" + s.replace("'", "'\"'\"'") + "'"

    def adb(self, *args: str, timeout: float = 30.0) -> str:
        with self._lock:
            cmd = " ".join([self._adb_bin()] + [self._q(a) for a in args])
            code, out, err = self._exec(cmd, timeout=timeout, binary=False)
            if code != 0 and not out:
                raise RuntimeError(err.strip() or f"adb exit {code}")
            return out

    def adb_on_serial(self, serial: str, *args: str, timeout: float = 30.0) -> str:
        """Run mini_adb against an explicit emulator serial (e.g. emulator-5556)."""
        ser = (serial or "").strip()
        if not ser:
            return self.adb(*args, timeout=timeout)
        with self._lock:
            cmd = " ".join([ADB, "-s", self._q(ser)] + [self._q(a) for a in args])
            code, out, err = self._exec(cmd, timeout=timeout, binary=False)
            if code != 0 and not out:
                raise RuntimeError(err.strip() or f"adb -s {ser} exit {code}")
            return out or ""

    def adb_bytes(self, *args: str, timeout: float = 30.0) -> bytes:
        with self._lock:
            cmd = " ".join([self._adb_bin()] + [self._q(a) for a in args])
            code, data, err = self._exec(cmd, timeout=timeout, binary=True)
            if code != 0 and not data:
                raise RuntimeError(
                    err.decode("utf-8", "replace").strip() or f"adb exit {code}"
                )
            return data

    def _refresh_size_unlocked(self) -> None:
        if not self._target:
            return
        try:
            _stdin, stdout, stderr = self._target.exec_command(
                f"{self._adb_bin()} shell wm size", timeout=15
            )
            text = (stdout.read() or b"").decode("utf-8", "replace")
            # Prefer Override size (wm size WxH) over Physical size.
            m = re.search(r"Override size:\s*(\d+)x(\d+)", text)
            if not m:
                m = re.search(r"Physical size:\s*(\d+)x(\d+)", text)
            if not m:
                m = re.search(r"(\d+)x(\d+)", text)
            if m:
                self.width = int(m.group(1))
                self.height = int(m.group(2))
        except Exception:
            pass

    def _parse_wm_size(self) -> dict:
        """Parse `wm size` into physical/override/current dimensions."""
        text = self._shell_ok("wm", "size", timeout=15) or ""
        physical = override = None
        mp = re.search(r"Physical size:\s*(\d+)x(\d+)", text)
        if mp:
            physical = (int(mp.group(1)), int(mp.group(2)))
        mo = re.search(r"Override size:\s*(\d+)x(\d+)", text)
        if mo:
            override = (int(mo.group(1)), int(mo.group(2)))
        current = override or physical or (self.width, self.height)
        return {
            "physical": physical,
            "override": override,
            "width": int(current[0]),
            "height": int(current[1]),
            "raw": text.strip(),
        }

    def get_display_orientation(self) -> dict:
        """Return current portrait/landscape based on effective wm size."""
        info = self._parse_wm_size()
        w, h = info["width"], info["height"]
        orientation = "landscape" if w > h else "portrait"
        self.width, self.height = w, h
        return {
            "orientation": orientation,
            "width": w,
            "height": h,
            "physical": (
                {"width": info["physical"][0], "height": info["physical"][1]}
                if info["physical"]
                else None
            ),
            "override": (
                {"width": info["override"][0], "height": info["override"][1]}
                if info["override"]
                else None
            ),
        }

    def set_display_orientation(self, orientation: str) -> dict:
        """
        Switch guest display to portrait or landscape.

        Uses wm size override (swap physical sides) rather than sensor rotation,
        so TV / landscape-locked apps get a true landscape framebuffer.
        """
        mode = (orientation or "").strip().lower()
        if mode in ("land", "landscape", "horizontal", "横屏"):
            mode = "landscape"
        elif mode in ("port", "portrait", "vertical", "竖屏"):
            mode = "portrait"
        else:
            raise ValueError("orientation must be portrait or landscape")

        info = self._parse_wm_size()
        phys = info["physical"] or (self.width, self.height)
        short_side, long_side = sorted(phys)

        # Lock auto-rotate; keep user_rotation at 0 so size alone defines orientation.
        self._shell_ok("settings", "put", "system", "accelerometer_rotation", "0")
        self._shell_ok("settings", "put", "system", "user_rotation", "0")

        if mode == "landscape":
            self.adb("shell", "wm", "size", f"{long_side}x{short_side}", timeout=20)
        else:
            # Prefer resetting to physical portrait when physical is already portrait.
            if phys[0] <= phys[1]:
                self.adb("shell", "wm", "size", "reset", timeout=20)
            else:
                self.adb("shell", "wm", "size", f"{short_side}x{long_side}", timeout=20)

        # Refresh cached size for tap/swipe coordinate mapping.
        with self._lock:
            self._refresh_size_unlocked()
        return self.get_display_orientation()

    def screencap(self) -> bytes:
        data = self.adb_bytes("exec-out", "screencap", "-p", timeout=20)
        if data.startswith(b"\x89PNG"):
            return data
        fixed = data.replace(b"\r\n", b"\n")
        if fixed.startswith(b"\x89PNG"):
            return fixed
        raise RuntimeError("bad screencap")

    def keyevent(self, code: str) -> None:
        self.adb("shell", "input", "keyevent", code)

    def wake(self) -> None:
        """Wake from off/doze and try to dismiss lock screen (KEYCODE_WAKEUP alone is often not enough)."""
        # Prefer staying on while we interact
        try:
            self.adb("shell", "svc", "power", "stayon", "true", timeout=15)
        except Exception:
            pass
        self.keyevent("224")  # WAKEUP
        time.sleep(0.25)
        try:
            self.adb("shell", "wm", "dismiss-keyguard", timeout=15)
        except Exception:
            pass
        # Raise brightness a bit in case screen looks black
        try:
            self.adb("shell", "settings", "put", "system", "screen_brightness_mode", "0", timeout=10)
            self.adb("shell", "settings", "put", "system", "screen_brightness", "180", timeout=10)
        except Exception:
            pass
        # Swipe up to leave AOD / lock UI
        w, h = max(self.width, 1), max(self.height, 1)
        try:
            self.swipe(w // 2, int(h * 0.88), w // 2, int(h * 0.25), 350)
        except Exception:
            pass
        self.keyevent("224")

    def tap(self, x: int, y: int) -> None:
        self.adb("shell", "input", "tap", str(x), str(y))

    def swipe(self, x1: int, y1: int, x2: int, y2: int, dur: int = 300) -> None:
        self.adb(
            "shell",
            "input",
            "swipe",
            str(x1),
            str(y1),
            str(x2),
            str(y2),
            str(dur),
        )

    def text(self, text: str, press_enter: bool = False) -> None:
        """Inject Unicode text via ADB Keyboard (supports Chinese)."""
        self.ensure_adb_keyboard()
        b64 = base64.b64encode(text.encode("utf-8")).decode("ascii")
        out = self.adb(
            "shell",
            "am",
            "broadcast",
            "-a",
            "ADB_INPUT_B64",
            "--es",
            "msg",
            b64,
            timeout=30,
        )
        if "Broadcast completed" not in (out or "") and "result=0" not in (out or "") and out.strip():
            # Some builds only print result code; still try plaintext fallback.
            try:
                self.adb(
                    "shell",
                    "am",
                    "broadcast",
                    "-a",
                    "ADB_INPUT_TEXT",
                    "--es",
                    "msg",
                    text,
                    timeout=30,
                )
            except Exception:
                pass
        if press_enter:
            time.sleep(0.15)
            self.keyevent("66")

    def delete_char(self, times: int = 1) -> None:
        """Delete characters in focused field (KEYCODE_DEL=67)."""
        self.ensure_adb_keyboard()
        n = max(1, min(int(times), 200))
        for _ in range(n):
            try:
                self.adb(
                    "shell",
                    "am",
                    "broadcast",
                    "-a",
                    "ADB_INPUT_CODE",
                    "--ei",
                    "code",
                    "67",
                    timeout=15,
                )
            except Exception:
                self.keyevent("67")

    def clear_text_field(self) -> None:
        """Clear focused EditText via ADB Keyboard."""
        self.ensure_adb_keyboard()
        self.adb("shell", "am", "broadcast", "-a", "ADB_CLEAR_TEXT", timeout=20)

    def show_soft_keyboard(self) -> None:
        """Switch back to Gboard/system IME so on-screen keyboard can appear."""
        # Prefer Google Latin IME on AVD; fallback to ime reset
        candidates = [
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
            "com.android.inputmethod.latin/.LatinIME",
        ]
        switched = False
        for ime in candidates:
            try:
                self.adb("shell", "ime", "enable", ime, timeout=15)
                self.adb("shell", "ime", "set", ime, timeout=15)
                switched = True
                break
            except Exception:
                continue
        if not switched:
            try:
                self.adb("shell", "ime", "reset", timeout=15)
            except Exception:
                pass
        # Tap focused edit again to request keyboard
        pos = self.find_edittext_center(prefer_package="com.huawei.aisftvassistant")
        if pos:
            self.tap(pos[0], pos[1])
            time.sleep(0.2)
            self.tap(pos[0], pos[1])

    def hide_soft_keyboard(self) -> None:
        """Hide on-screen keyboard by switching back to ADB Keyboard (no soft UI)."""
        try:
            self.adb("shell", "ime", "enable", ADB_IME, timeout=15)
            self.adb("shell", "ime", "set", ADB_IME, timeout=15)
        except Exception:
            self.ensure_adb_keyboard()
        # Optional: BACK once can also dismiss IME without leaving if IME consumes it
        try:
            self.keyevent("111")  # ESCAPE
        except Exception:
            pass

    def toggle_soft_keyboard(self) -> str:
        """Toggle soft keyboard visibility. Returns 'shown' or 'hidden'."""
        if getattr(self, "_soft_kb_shown", False):
            self.hide_soft_keyboard()
            self._soft_kb_shown = False
            return "hidden"
        self.show_soft_keyboard()
        self._soft_kb_shown = True
        return "shown"

    def ensure_adb_keyboard(self) -> None:
        """Install/enable ADB Keyboard IME so Unicode (Chinese) injection works."""
        try:
            path_out = self.adb("shell", "pm", "path", ADB_IME_PKG, timeout=20)
        except Exception:
            path_out = ""
        if "package:" not in (path_out or ""):
            if not ADB_KEYBOARD_APK.is_file():
                raise RuntimeError(
                    f"缺少 ADBKeyboard.apk: {ADB_KEYBOARD_APK}（请部署 webserverV2/apks/adbkeyboard/）"
                )
            self.install_apk(
                str(ADB_KEYBOARD_APK),
                timeout=180,
                package_name=ADB_IME_PKG,
                replace_incompatible=True,
            )
        # Enable + switch IME (idempotent)
        try:
            self.adb("shell", "ime", "enable", ADB_IME, timeout=20)
        except Exception:
            pass
        self.adb("shell", "ime", "set", ADB_IME, timeout=20)

    @staticmethod
    def _escape_input_text(text: str) -> str:
        # Legacy ASCII-only helper (kept for diagnostics).
        out = []
        for ch in text:
            o = ord(ch)
            if ch == " ":
                out.append("%s")
            elif ch == "%":
                out.append("%%")
            elif 32 <= o <= 126 and ch not in "\\'\"<>|;()&*`~":
                out.append(ch)
        return "".join(out)

    def _list_edittexts(self) -> list:
        """Parse all EditText nodes from a fresh uiautomator dump."""
        try:
            self.adb("shell", "uiautomator", "dump", "/sdcard/uidump.xml", timeout=20)
            xml = self.adb("shell", "cat", "/sdcard/uidump.xml", timeout=15)
        except Exception:
            return []
        nodes = []
        for part in xml.split("<node "):
            if "EditText" not in part or "bounds=" not in part:
                continue
            bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', part)
            if not bm:
                continue
            pkg_m = re.search(r'package="([^"]*)"', part)
            foc_m = re.search(r'focused="([^"]*)"', part)
            rid_m = re.search(r'resource-id="([^"]*)"', part)
            txt_m = re.search(r'\btext="([^"]*)"', part)
            nodes.append(
                {
                    "pkg": pkg_m.group(1) if pkg_m else "",
                    "focused": (foc_m.group(1).lower() == "true") if foc_m else False,
                    "rid": rid_m.group(1) if rid_m else "",
                    "text": txt_m.group(1) if txt_m else "",
                    "x1": int(bm.group(1)),
                    "y1": int(bm.group(2)),
                    "x2": int(bm.group(3)),
                    "y2": int(bm.group(4)),
                }
            )
        return nodes

    @staticmethod
    def _edittext_center(n: dict) -> tuple:
        return (n["x1"] + n["x2"]) // 2, (n["y1"] + n["y2"]) // 2

    def find_edittext_center(
        self,
        prefer_package: str = "",
        tap_x: Optional[int] = None,
        tap_y: Optional[int] = None,
    ) -> Optional[tuple]:
        """Pick EditText by last tap point, else currently focused, else None."""
        nodes = self._list_edittexts()
        if not nodes:
            return None

        if tap_x is not None and tap_y is not None:
            containing = [
                n
                for n in nodes
                if n["x1"] <= tap_x <= n["x2"] and n["y1"] <= tap_y <= n["y2"]
            ]
            if containing:
                best = min(
                    containing,
                    key=lambda n: max(1, (n["x2"] - n["x1"]) * (n["y2"] - n["y1"])),
                )
                return self._edittext_center(best)

            def dist2(n):
                cx, cy = self._edittext_center(n)
                return (cx - tap_x) ** 2 + (cy - tap_y) ** 2

            nearest = min(nodes, key=dist2)
            cx, cy = self._edittext_center(nearest)
            pad = 40
            near = (
                nearest["x1"] - pad <= tap_x <= nearest["x2"] + pad
                and nearest["y1"] - pad <= tap_y <= nearest["y2"] + pad
            ) or dist2(nearest) <= 120 * 120
            if near:
                return cx, cy

        focused = [n for n in nodes if n["focused"]]
        if focused:
            if prefer_package:
                pkg_focused = [n for n in focused if prefer_package in n["pkg"]]
                if pkg_focused:
                    return self._edittext_center(pkg_focused[0])
            return self._edittext_center(focused[0])

        return None

    def type_in_edittext(
        self,
        text: str,
        press_enter: bool = False,
        prefer_package: str = "",
        autofocus: bool = True,
        tap_x: Optional[int] = None,
        tap_y: Optional[int] = None,
    ) -> dict:
        """Inject into the EditText the user clicked (or the focused one)."""
        tapped = None
        if autofocus or tap_x is not None:
            pos = self.find_edittext_center(
                prefer_package=prefer_package,
                tap_x=tap_x,
                tap_y=tap_y,
            )
            if pos:
                self.tap(pos[0], pos[1])
                tapped = {"x": pos[0], "y": pos[1]}
                time.sleep(0.25)
        self.text(text, press_enter=press_enter)
        return {
            "tapped": tapped,
            "injected": text,
            "method": "adb_keyboard_b64",
            "dropped_chars": "",
            "used_tap": tap_x is not None and tap_y is not None,
        }

    def put_file(self, local_path: str, remote_path: str, progress_cb=None) -> None:
        """Upload a host file onto the target SSH machine (not the emulator)."""
        with self._lock:
            if not self._target:
                raise RuntimeError("not connected")
            sftp = self._target.open_sftp()
            try:
                remote_dir = os.path.dirname(remote_path.replace("\\", "/"))
                if remote_dir and remote_dir not in (".", "/"):
                    parts = remote_dir.strip("/").split("/")
                    cur = ""
                    for p in parts:
                        cur += "/" + p
                        try:
                            sftp.stat(cur)
                        except OSError:
                            try:
                                sftp.mkdir(cur)
                            except OSError:
                                pass

                def _cb(transferred, total):
                    if progress_cb:
                        try:
                            progress_cb(int(transferred), int(total or 0))
                        except Exception:
                            pass

                sftp.put(local_path, remote_path, callback=_cb if progress_cb else None)
            finally:
                sftp.close()

    def install_apk(
        self,
        local_path: str,
        timeout: float = 600.0,
        package_name: str = "",
        replace_incompatible: bool = True,
        progress_cb=None,
    ) -> str:
        """SFTP APK to target host, then mini_adb install -r into the emulator.

        progress_cb(phase, percent, message) optional:
          phase in ('upload', 'install', 'uninstall', 'done')
        """
        if not os.path.isfile(local_path):
            raise RuntimeError(f"apk not found: {local_path}")
        remote = f"{LAB}/apk/_webserver_install.apk"
        size = max(1, os.path.getsize(local_path))

        def on_sftp(transferred, total):
            if not progress_cb:
                return
            tot = total or size
            pct = min(70, int(transferred * 70 / tot)) if tot else 0
            mb = transferred / (1024 * 1024)
            tot_mb = tot / (1024 * 1024)
            progress_cb(
                "upload",
                pct,
                f"传输到虚拟机主机 {mb:.1f}/{tot_mb:.1f} MB ({pct}%)",
            )

        if progress_cb:
            progress_cb("upload", 0, "开始传输 APK 到虚拟机主机…")
        self.put_file(local_path, remote, progress_cb=on_sftp)

        if progress_cb:
            progress_cb("install", 75, "正在 adb install（可能需数分钟）…")
        out = self.adb("install", "-r", remote, timeout=timeout)
        if "Success" in (out or ""):
            if progress_cb:
                progress_cb("done", 100, "安装成功")
            return out
        text = (out or "").strip()
        if (
            replace_incompatible
            and package_name
            and "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in text
        ):
            # Different signing key than the already-installed app: uninstall then install.
            if progress_cb:
                progress_cb("uninstall", 80, "签名不兼容，正在卸载旧包…")
            try:
                self.adb("uninstall", package_name, timeout=120)
            except Exception as ex:
                raise RuntimeError(f"uninstall old package failed: {ex}; install said: {text}") from ex
            if progress_cb:
                progress_cb("install", 85, "正在重新安装…")
            out = self.adb("install", "-r", remote, timeout=timeout)
            if "Success" in (out or ""):
                if progress_cb:
                    progress_cb("done", 100, "安装成功")
                return out
            text = (out or "").strip()
        raise RuntimeError(text or "install failed")

    # ---- post-install permissions ----

    # Known accessibility service components (pkg -> component)
    A11Y_COMPONENTS = {
        "com.example.testapp": (
            "com.example.testapp/"
            "com.example.testapp.service.TestAccessibilityService"
        ),
        "com.example.devicesdkdemo": (
            "com.example.devicesdkdemo/"
            "com.huawei.aisfsdkv2.component.androidfunc.accessibility"
            ".impl.AccessibilityManagerImpl"
        ),
    }

    RUNTIME_PERMS = {
        "com.example.testapp": [
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
        ],
        "com.example.devicesdkdemo": [
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
        ],
        "com.unicom.cloudphone": [
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_PHONE_STATE",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
        ],
        "com.huawei.aisftvassistant": [
            "android.permission.RECORD_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
        ],
        "com.autonavi.minimap": [
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
        ],
        "com.youku.phone": [
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        ],
    }

    APPOPS_ALLOW = (
        "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS",
        "GET_USAGE_STATS",
        "PROJECT_MEDIA",
        "RUN_IN_BACKGROUND",
        "RUN_ANY_IN_BACKGROUND",
        "START_FOREGROUND",
        "MANAGE_EXTERNAL_STORAGE",
        "REQUEST_INSTALL_PACKAGES",
    )

    LOCATION_RUNTIME_PERMS = (
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.ACCESS_MEDIA_LOCATION",
        "android.permission.ACTIVITY_RECOGNITION",
    )

    LOCATION_APPOPS = (
        "FINE_LOCATION",
        "COARSE_LOCATION",
        "MONITOR_LOCATION",
        "MONITOR_HIGH_POWER_LOCATION",
    )

    def _shell_ok(self, *parts: str, timeout: float = 20.0) -> str:
        """Run adb shell command; return stdout (empty on soft failure)."""
        try:
            return self.adb("shell", *parts, timeout=timeout) or ""
        except Exception:
            return ""

    def discover_accessibility_component(self, package_name: str) -> Optional[str]:
        """Find AccessibilityService component for package, if any."""
        if not package_name:
            return None
        known = self.A11Y_COMPONENTS.get(package_name)
        if known:
            return known
        dump = self._shell_ok("dumpsys", "package", package_name, timeout=40)
        for line in dump.splitlines():
            if "AccessibilityService" not in line and "accessibilityservice" not in line.lower():
                continue
            # e.g. "com.foo/com.foo.BarService filter ..."
            m = re.search(
                rf"({re.escape(package_name)}/[A-Za-z0-9_$.]+)",
                line,
            )
            if m:
                return m.group(1)
        return None

    def enable_accessibility_services(self, *components: str) -> str:
        """Merge and enable accessibility services (keep existing ones)."""
        cur = (self._shell_ok("settings", "get", "secure", "enabled_accessibility_services") or "").strip()
        services = set()
        if cur and cur != "null":
            for s in cur.split(":"):
                s = s.strip()
                if s:
                    services.add(s)
        for c in components:
            if c:
                services.add(c)
        if not services:
            return ""
        enabled = ":".join(sorted(services))
        self._shell_ok(
            "settings", "put", "secure", "enabled_accessibility_services", enabled
        )
        self._shell_ok("settings", "put", "secure", "accessibility_enabled", "1")
        # bounce to force rebind
        self._shell_ok("settings", "put", "secure", "accessibility_enabled", "0")
        self._shell_ok("settings", "put", "secure", "accessibility_enabled", "1")
        return enabled

    def _requested_permissions(self, package_name: str) -> list:
        """Parse dumpsys package for android.permission.* the app requested."""
        dump = self._shell_ok("dumpsys", "package", package_name, timeout=40)
        found = []
        seen = set()
        in_req = False
        for line in dump.splitlines():
            low = line.lower()
            if "requested permissions:" in low:
                in_req = True
                continue
            if in_req:
                if re.match(r"^\S", line) and "permission" not in low:
                    # left indented block
                    if not line.strip().startswith("android.permission"):
                        in_req = False
                if "install permissions:" in low or "runtime permissions:" in low:
                    in_req = False
                    continue
            if not in_req and "android.permission." not in line:
                continue
            for m in re.finditer(r"(android\.permission\.[A-Z0-9_]+)", line):
                p = m.group(1)
                if p not in seen:
                    seen.add(p)
                    found.append(p)
        return found

    def grant_package_permissions(self, package_name: str) -> dict:
        """Grant runtime + special permissions after APK install.

        Ignores perms the package did not declare. Always best-effort.
        """
        if not package_name:
            return {"package": "", "granted": [], "skipped": [], "a11y": ""}

        granted = []
        skipped = []
        perms = list(self.RUNTIME_PERMS.get(package_name) or [])
        # Discover what the APK actually requested
        for p in self._requested_permissions(package_name):
            if p not in perms:
                perms.append(p)
        # Always try common dangerous ones even for unknown packages
        for p in (
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_PHONE_STATE",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.NEARBY_WIFI_DEVICES",
        ):
            if p not in perms:
                perms.append(p)

        for perm in perms:
            short = perm.split(".")[-1]
            try:
                out = self.adb("shell", "pm", "grant", package_name, perm, timeout=20) or ""
                if "Exception" in out or "not requested" in out:
                    skipped.append(short)
                else:
                    granted.append(short)
            except Exception as ex:
                msg = str(ex)
                if "not requested" in msg or "SecurityException" in msg or "Exception" in msg:
                    skipped.append(short)
                else:
                    skipped.append(short)

        for op in self.APPOPS_ALLOW:
            self._shell_ok("appops", "set", package_name, op, "allow")
        self._shell_ok(
            "appops", "set", package_name, "AUTO_REVOKE_PERMISSIONS_IF_UNUSED", "ignore"
        )
        self._shell_ok("cmd", "deviceidle", "whitelist", f"+{package_name}")

        a11y = self.discover_accessibility_component(package_name)
        # Also keep peer services alive if those packages are present
        peers = []
        for pkg, comp in self.A11Y_COMPONENTS.items():
            if pkg == package_name:
                continue
            path_out = self._shell_ok("pm", "path", pkg)
            if path_out.strip().startswith("package:"):
                peers.append(comp)
        enabled = self.enable_accessibility_services(
            *([a11y] if a11y else []), *peers
        )

        return {
            "package": package_name,
            "granted": granted,
            "skipped": skipped,
            "a11y": a11y or "",
            "a11y_enabled": enabled,
        }

    def grant_location_permissions(self, package_name: str) -> dict:
        """Grant location-related runtime permissions + appops only."""
        if not package_name:
            return {"package": "", "granted": [], "skipped": [], "mode": "location"}
        granted = []
        skipped = []
        for perm in self.LOCATION_RUNTIME_PERMS:
            short = perm.split(".")[-1]
            try:
                out = self.adb("shell", "pm", "grant", package_name, perm, timeout=20) or ""
                if "Exception" in out or "not requested" in out:
                    skipped.append(short)
                else:
                    granted.append(short)
            except Exception:
                skipped.append(short)
        for op in self.LOCATION_APPOPS:
            self._shell_ok("appops", "set", package_name, op, "allow")
        # Ensure location providers are on + inject Xi'an mock GPS
        self._shell_ok("settings", "put", "secure", "location_mode", "3")
        self._shell_ok(
            "settings", "put", "secure", "location_providers_allowed", "+gps,+network"
        )
        geo = self.ensure_guest_geo()
        if not geo.get("ok"):
            skipped.append(f"geo_fix:{geo.get('error', 'failed')}")
        else:
            granted.append(f"geo_fix:{geo['lon']},{geo['lat']}")
        return {
            "package": package_name,
            "granted": granted,
            "skipped": skipped,
            "mode": "location",
        }

    def list_permission_targets(self) -> list:
        """Third-party (user-installed) packages on the emulator — not system apps."""
        pkgs = []
        seen = set()
        # -3 = third-party only
        for line in self._shell_ok("pm", "list", "packages", "-3", timeout=25).splitlines():
            line = line.strip()
            if not line.startswith("package:"):
                continue
            pkg = line.split(":", 1)[1].strip()
            if not pkg or pkg in seen:
                continue
            # skip keyboard helper / obvious non-target tools if desired — keep them
            if pkg.startswith("com.android.") and pkg not in self.RUNTIME_PERMS:
                # rare 3p with com.android prefix; still include
                pass
            seen.add(pkg)
            pkgs.append(pkg)
        # Also include known test packages if installed but somehow missing from -3
        for pkg in list(self.RUNTIME_PERMS.keys()) + list(self.A11Y_COMPONENTS.keys()):
            if pkg in seen:
                continue
            path_out = self._shell_ok("pm", "path", pkg, timeout=15)
            if path_out.strip().startswith("package:"):
                seen.add(pkg)
                pkgs.append(pkg)

        items = []
        for pkg in pkgs:
            items.append(
                {
                    "package": pkg,
                    "label": self._recent_label(pkg),
                    "installed": True,
                    "has_a11y": bool(
                        self.A11Y_COMPONENTS.get(pkg)
                        or pkg in self.A11Y_COMPONENTS
                    ),
                    "known": pkg in self.RUNTIME_PERMS or pkg in self.A11Y_COMPONENTS,
                    "perm_count": len(self.RUNTIME_PERMS.get(pkg) or []),
                }
            )
        items.sort(key=lambda x: (not x.get("known"), x["label"].lower(), x["package"]))
        return items

    def grant_known_permissions(self, packages=None, mode: str = "all") -> dict:
        """One-click grant for third-party packages (or an explicit package list).

        mode:
          - all: full runtime/appops/a11y grant
          - location: location permissions + location appops only
        """
        mode = (mode or "all").strip().lower()
        if mode not in ("all", "location"):
            mode = "all"
        targets = self.list_permission_targets()
        by_pkg = {t["package"]: t for t in targets}
        if packages is None:
            todo = [t["package"] for t in targets if t.get("installed")]
        else:
            todo = []
            for p in packages or []:
                p = (p or "").strip()
                if p:
                    todo.append(p)
        results = []
        skipped = []
        failed = []
        for pkg in todo:
            info = by_pkg.get(pkg)
            if info is None:
                path_out = self._shell_ok("pm", "path", pkg, timeout=15)
                if not path_out.strip().startswith("package:"):
                    skipped.append({"package": pkg, "reason": "not installed"})
                    continue
            try:
                if mode == "location":
                    results.append(self.grant_location_permissions(pkg))
                else:
                    results.append(self.grant_package_permissions(pkg))
            except Exception as ex:
                failed.append({"package": pkg, "error": str(ex)})
        return {
            "results": results,
            "skipped": skipped,
            "failed": failed,
            "count": len(results),
            "mode": mode,
        }

    # ---- recent tasks / multitask ----

    RECENT_LABELS = {
        "com.example.testapp": "TestApp",
        "com.example.devicesdkdemo": "DeviceSDK Demo",
        "com.autonavi.minimap": "高德地图",
        "com.youku.phone": "优酷",
        "com.qiyi.video": "爱奇艺",
        "com.unicom.cloudphone": "云智手机",
        "com.huawei.aisftvassistant": "AISF 语音",
        "com.android.launcher3": "桌面",
        "com.google.android.apps.nexuslauncher": "桌面",
        "com.android.settings": "设置",
        "com.android.systemui": "系统界面",
        "com.android.adbkeyboard": "ADB键盘",
        "com.android.camera2": "相机",
        "com.android.camera": "相机",
    }

    RECENT_SKIP_CLEAR_ALL = {
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.android.systemui",
    }

    def _recent_label(self, package: str) -> str:
        if not package:
            return ""
        if package in self.RECENT_LABELS:
            return self.RECENT_LABELS[package]
        return package.rsplit(".", 1)[-1]

    @staticmethod
    def _extract_pkg_from_component(text: str) -> Optional[str]:
        """Extract package from cmp=pkg/cls, I=pkg/cls, or realActivity={pkg/cls}."""
        if not text:
            return None
        for pat in (
            r"cmp=([a-zA-Z0-9_.]+)/",
            r"realActivity=\{([a-zA-Z0-9_.]+)/",
            r"\bI=([a-zA-Z0-9_.]+)/",
            r"ComponentInfo\{([a-zA-Z0-9_.]+)/",
        ):
            m = re.search(pat, text)
            if m:
                return m.group(1)
        return None

    def list_recent_tasks(self) -> list:
        """List recent-task apps from dumpsys activity recents.

        Newer Android dumps put affinity (often an Activity class name) in A=,
        while the real package is on the intent line as cmp=package/activity.
        Prefer cmp=/realActivity; skip home tasks.
        """
        dump = self._shell_ok("dumpsys", "activity", "recents", timeout=25)
        if not dump.strip():
            dump = self._shell_ok("dumpsys", "activity", "activities", timeout=25)
        items = []
        seen_pkg = set()
        pkg_re = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")

        def add_item(idx, task_id, pkg, *, home=False):
            if home:
                return
            if not pkg or not pkg_re.match(pkg):
                return
            if pkg.startswith("android.") or pkg in ("null", "unknown"):
                return
            if pkg in seen_pkg:
                return
            seen_pkg.add(pkg)
            items.append(
                {
                    "index": int(idx) if str(idx).isdigit() else len(items),
                    "task_id": int(task_id) if str(task_id).isdigit() else None,
                    "package": pkg,
                    "label": self._recent_label(pkg),
                    "clearable": pkg not in self.RECENT_SKIP_CLEAR_ALL,
                }
            )

        # Primary: each "* Recent #N: Task{... #tid ...}" block + following lines
        block_re = re.compile(
            r"\*\s+Recent\s+#(\d+):\s+Task\{[^#\n]*#(\d+)\b([^}\n]*)\}(.*?)(?="
            r"\*\s+Recent\s+#|\*\s+RecentTaskInfo|\Z)",
            re.S,
        )
        for m in block_re.finditer(dump):
            idx, tid, meta, body = m.group(1), m.group(2), m.group(3), m.group(4)
            blob = meta + "\n" + body
            is_home = (
                "type=home" in meta
                or "activityType=2" in body
                or "category.HOME" in body
                or "FallbackHome" in blob
            )
            pkg = self._extract_pkg_from_component(blob)
            # Last resort: A=uid:something only if it looks like a real package id
            if not pkg:
                m_a = re.search(r"\bA=(?:\d+:)?([a-z][a-z0-9_.]+)", meta + body)
                if m_a and pkg_re.match(m_a.group(1)):
                    pkg = m_a.group(1)
            add_item(idx, tid, pkg, home=is_home)

        # Supplement from RecentTaskInfo (overview cards; may lack task id)
        info_re = re.compile(
            r"\*\s+RecentTaskInfo\s+#(\d+):(.*?)(?=\*\s+RecentTaskInfo\s+#|\Z)",
            re.S,
        )
        for m in info_re.finditer(dump):
            idx, body = m.group(1), m.group(2)
            if "category.HOME" in body:
                continue
            pkg = self._extract_pkg_from_component(body)
            if not pkg or pkg in seen_pkg:
                continue
            tid = None
            m_tid = re.search(r"(?:persistentId|taskId|id)=(\d+)", body)
            if m_tid:
                tid = m_tid.group(1)
            add_item(idx, tid, pkg)

        if not items:
            for line in dump.splitlines():
                m = re.search(
                    r"(?:mResumedActivity|topResumedActivity|Hist\s+#\d+:).*?"
                    r"([a-z][a-z0-9_.]+)/[a-zA-Z0-9_.]+",
                    line,
                )
                if m:
                    add_item(len(items), None, m.group(1))

        items.sort(key=lambda x: x.get("index", 0))
        # Keep only real installed packages (drops affinity parse leftovers)
        installed = set()
        for line in self._shell_ok("pm", "list", "packages", timeout=20).splitlines():
            line = line.strip()
            if line.startswith("package:"):
                installed.add(line.split(":", 1)[1].strip())
        if installed:
            items = [it for it in items if it["package"] in installed]
        return items

    def _try_remove_task(self, tid: int) -> dict:
        """Try several ways to remove a recent task by id; verify it is gone."""
        tid = int(tid)
        detail = {"task_id": tid, "ok": False, "method": None, "outs": []}

        def still_has_task() -> bool:
            dump = self._shell_ok("dumpsys", "activity", "recents", timeout=20)
            # Match "#87 " / "#87}" / "#87\n" style task ids in Recent lines
            return bool(re.search(rf"Recent\s+#\d+:\s+Task\{{[^#]*#{tid}\b", dump))

        commands = [
            # Android 13 (API 33) on this image: am stack remove <id> works
            ("am-stack-remove", ["am", "stack", "remove", str(tid)]),
            ("cmd-activity-stack-remove", ["cmd", "activity", "remove", str(tid)]),
            ("cmd-activity-remove-task", ["cmd", "activity", "remove-task", str(tid)]),
            ("cmd-activity_task-remove-task", ["cmd", "activity_task", "remove-task", str(tid)]),
            ("am-task-remove", ["am", "task", "remove", str(tid)]),
        ]
        for name, args in commands:
            try:
                out = (self.adb("shell", *args, timeout=15) or "").strip()
                detail["outs"].append({"method": name, "out": out[:200]})
                low = out.lower()
                if any(
                    k in low
                    for k in (
                        "exception",
                        "error:",
                        "unknown command",
                        "not found",
                        "no shell command",
                        "no task",
                        "cannot",
                        "denied",
                    )
                ):
                    continue
                time.sleep(0.2)
                if not still_has_task():
                    detail["ok"] = True
                    detail["method"] = name
                    return detail
            except Exception as ex:
                detail["outs"].append({"method": name, "out": str(ex)[:200]})

        # Binder service call fallbacks (transaction codes differ by Android version)
        for svc in ("activity", "activity_task"):
            for code in (12, 13, 22, 50, 81):
                name = f"service-call-{svc}-{code}"
                try:
                    out = (
                        self.adb(
                            "shell",
                            "service",
                            "call",
                            svc,
                            str(code),
                            "i32",
                            str(tid),
                            timeout=15,
                        )
                        or ""
                    ).strip()
                    detail["outs"].append({"method": name, "out": out[:200]})
                    time.sleep(0.15)
                    if not still_has_task():
                        detail["ok"] = True
                        detail["method"] = name
                        return detail
                except Exception as ex:
                    detail["outs"].append({"method": name, "out": str(ex)[:200]})
        return detail

    def clear_recent_app(self, package: str, task_id=None) -> dict:
        """Remove app from recents (remove-task) and force-stop the process."""
        package = (package or "").strip()
        if not package or "." not in package:
            raise RuntimeError("invalid package")
        detail = {
            "package": package,
            "force_stop": False,
            "remove_task": False,
            "removed": False,
        }

        # Resolve task id before stopping (force-stop can change task state)
        tid = task_id
        if tid is None:
            for item in self.list_recent_tasks():
                if item.get("package") == package and item.get("task_id") is not None:
                    tid = item["task_id"]
                    break
        try:
            tid = int(tid) if tid is not None else None
        except (TypeError, ValueError):
            tid = None

        if tid is not None:
            rm = self._try_remove_task(tid)
            detail["remove_task"] = bool(rm.get("ok"))
            detail["task_id"] = tid
            detail["remove_method"] = rm.get("method")
            if not rm.get("ok"):
                detail["remove_task_out"] = rm.get("outs", [])[-3:]

        try:
            self.adb("shell", "am", "force-stop", package, timeout=20)
            detail["force_stop"] = True
        except Exception as ex:
            detail["force_stop_error"] = str(ex)

        # Retry remove after force-stop with refreshed task id
        still = [it for it in self.list_recent_tasks() if it.get("package") == package]
        if still:
            tid2 = still[0].get("task_id")
            if tid2 is not None and tid2 != tid:
                rm2 = self._try_remove_task(int(tid2))
                detail["remove_task_retry"] = bool(rm2.get("ok"))
                detail["task_id"] = int(tid2)
                if rm2.get("ok"):
                    detail["remove_task"] = True
                    detail["remove_method"] = rm2.get("method")
            elif tid is not None and not detail.get("remove_task"):
                rm3 = self._try_remove_task(int(tid))
                detail["remove_task_retry"] = bool(rm3.get("ok"))
                if rm3.get("ok"):
                    detail["remove_task"] = True
                    detail["remove_method"] = rm3.get("method")

        still = [it for it in self.list_recent_tasks() if it.get("package") == package]
        detail["removed"] = len(still) == 0
        detail["still_present"] = [it.get("package") for it in still]
        if not detail["removed"]:
            raise RuntimeError(
                f"未能从多任务清除 {package}（force_stop={detail['force_stop']}, "
                f"remove_task={detail['remove_task']}, method={detail.get('remove_method')}）"
            )
        return detail

    def clear_all_recent_apps(self) -> dict:
        """Clear clearable recent apps (skip launcher/systemui)."""
        apps = self.list_recent_tasks()
        cleared = []
        skipped = []
        failed = []
        for item in apps:
            pkg = item.get("package") or ""
            if not item.get("clearable", True):
                skipped.append(pkg)
                continue
            try:
                cleared.append(
                    self.clear_recent_app(pkg, task_id=item.get("task_id"))
                )
            except Exception as ex:
                failed.append({"package": pkg, "error": str(ex)})
        return {
            "cleared": cleared,
            "skipped": skipped,
            "failed": failed,
            "count": len(cleared),
        }

    def clear_recent_apps(self, packages: list) -> dict:
        """Clear a list of packages from recents."""
        apps = {it.get("package"): it for it in self.list_recent_tasks()}
        cleared = []
        failed = []
        for pkg in packages or []:
            pkg = (pkg or "").strip()
            if not pkg:
                continue
            item = apps.get(pkg) or {}
            try:
                cleared.append(
                    self.clear_recent_app(pkg, task_id=item.get("task_id"))
                )
            except Exception as ex:
                failed.append({"package": pkg, "error": str(ex)})
        return {
            "cleared": cleared,
            "failed": failed,
            "count": len(cleared),
        }

    def get_emulator_info(self) -> dict:
        """Collect memory / storage / network / basic device info for the VM panel."""
        def _kb(line: str):
            m = re.search(r"(\d+)\s*kB", line or "", re.I)
            return int(m.group(1)) if m else 0

        def _parse_df_line(line: str):
            # Filesystem Size Used Avail Use% Mounted
            parts = line.split()
            if len(parts) < 6:
                return None
            return {
                "filesystem": parts[0],
                "size": parts[1],
                "used": parts[2],
                "avail": parts[3],
                "use_pct": parts[4].rstrip("%"),
                "mounted": parts[5] if len(parts) == 6 else " ".join(parts[5:]),
            }

        meminfo = self._shell_ok("cat", "/proc/meminfo", timeout=15)
        mem = {"total_kb": 0, "free_kb": 0, "available_kb": 0, "cached_kb": 0}
        for ln in meminfo.splitlines():
            if ln.startswith("MemTotal:"):
                mem["total_kb"] = _kb(ln)
            elif ln.startswith("MemFree:"):
                mem["free_kb"] = _kb(ln)
            elif ln.startswith("MemAvailable:"):
                mem["available_kb"] = _kb(ln)
            elif ln.startswith("Cached:"):
                mem["cached_kb"] = _kb(ln)
        total = mem["total_kb"] or 1
        used_kb = max(0, total - (mem["available_kb"] or mem["free_kb"]))
        mem["used_kb"] = used_kb
        mem["used_pct"] = round(100.0 * used_kb / total, 1)
        mem["available_pct"] = round(100.0 * (mem["available_kb"] or 0) / total, 1)
        mem["total_mb"] = round(total / 1024, 1)
        mem["used_mb"] = round(used_kb / 1024, 1)
        mem["available_mb"] = round((mem["available_kb"] or 0) / 1024, 1)

        storage = {"data": None, "root": None}
        for mount, key in (("/data", "data"), ("/", "root")):
            df = self._shell_ok("df", "-h", mount, timeout=15)
            for ln in df.splitlines()[1:]:
                parsed = _parse_df_line(ln)
                if parsed:
                    try:
                        parsed["use_pct_num"] = float(parsed["use_pct"])
                    except ValueError:
                        parsed["use_pct_num"] = 0.0
                    storage[key] = parsed
                    break

        # Network
        ip_addr = self._shell_ok("ip", "addr", "show", "wlan0", timeout=15)
        if not ip_addr.strip():
            ip_addr = self._shell_ok("ip", "addr", timeout=15)
        ipv4 = ""
        m = re.search(r"inet\s+(\d+\.\d+\.\d+\.\d+)/\d+", ip_addr)
        if m:
            ipv4 = m.group(1)
        wifi_on = (self._shell_ok("settings", "get", "global", "wifi_on") or "").strip()
        airplane = (self._shell_ok("settings", "get", "global", "airplane_mode_on") or "").strip()
        # quick gateway reachability (best-effort, short)
        gw_ok = False
        ping = self._shell_ok("ping", "-c", "1", "-W", "1", "10.0.2.2", timeout=8)
        if "1 received" in ping or "1 packets received" in ping:
            gw_ok = True

        def _ping_ms(text: str):
            mm = re.search(r"time[=<]([\d.]+)\s*ms", text or "", re.I)
            return float(mm.group(1)) if mm else None

        gw_latency_ms = _ping_ms(ping)
        net_ping = self._shell_ok("ping", "-c", "1", "-W", "2", "223.5.5.5", timeout=10)
        net_latency_ms = _ping_ms(net_ping)
        # Do not fall back to qemu gateway RTT for "网络延迟" (often <1ms → UI shows 0).
        latency_ms = net_latency_ms
        hub_latency_ms = None
        latency_src = "public" if net_latency_ms is not None else None

        props = {}
        for key in (
            "ro.product.model",
            "ro.build.version.release",
            "ro.build.version.sdk",
            "ro.product.cpu.abi",
            "ro.serialno",
            "ro.boot.qemu.avd_name",
            "persist.sys.locale",
            "persist.sys.timezone",
        ):
            props[key] = (self._shell_ok("getprop", key, timeout=10) or "").strip()

        # Merge QoS monitor (smoothness / jitter / rolling latency).
        smoothness_score = None
        jitter_ms = None
        qemu_cpu_pct = None
        guest_load1 = None
        qos_updated_at = None
        try:
            self.ensure_qos_monitor()
            qos = self.read_emu_qos()
            by_name = (qos.get("emulators") or {}) if isinstance(qos, dict) else {}
            avd_name = (props.get("ro.boot.qemu.avd_name") or self.active_avd or "").strip()
            q = by_name.get(avd_name) if avd_name else None
            if not isinstance(q, dict):
                for cand in (by_name or {}).values():
                    if isinstance(cand, dict) and cand.get("adb_port") == self.adb_port:
                        q = cand
                        break
            if isinstance(q, dict):
                if q.get("latency_ms") is not None:
                    latency_ms = q.get("latency_ms")
                if q.get("gw_latency_ms") is not None:
                    gw_latency_ms = q.get("gw_latency_ms")
                if q.get("net_latency_ms") is not None:
                    net_latency_ms = q.get("net_latency_ms")
                if q.get("hub_latency_ms") is not None:
                    hub_latency_ms = q.get("hub_latency_ms")
                if q.get("latency_src") is not None:
                    latency_src = q.get("latency_src")
                smoothness_score = q.get("smoothness_score")
                if smoothness_score is None and q.get("stutter_score") is not None:
                    try:
                        smoothness_score = max(0, min(100, 100 - int(q.get("stutter_score"))))
                    except Exception:
                        smoothness_score = None
                jitter_ms = q.get("jitter_ms")
                qemu_cpu_pct = q.get("qemu_cpu_pct")
                guest_load1 = q.get("guest_load1")
                qos_updated_at = q.get("ts") or qos.get("updated_at")
        except Exception:
            pass

        if latency_ms is None and hub_latency_ms is not None:
            latency_ms = hub_latency_ms
            latency_src = latency_src or "hub"

        # Fallback smoothness estimate if monitor has not produced a score yet.
        if smoothness_score is None:
            lat = float(latency_ms) if latency_ms is not None else 200.0
            lat_part = max(0.0, min(100.0, (lat - 40.0) / 4.0))
            smoothness_score = int(round(max(0.0, 100.0 - lat_part * 0.6)))

        pkgs = []
        for ln in self._shell_ok("pm", "list", "packages", "-3", timeout=20).splitlines():
            if ln.startswith("package:"):
                pkgs.append(ln.split(":", 1)[1].strip())

        return {
            "memory": mem,
            "storage": storage,
            "network": {
                "wifi_on": wifi_on in ("1", "true", "True"),
                "airplane": airplane in ("1", "true", "True"),
                "ipv4": ipv4,
                "gateway": "10.0.2.2",
                "gateway_ok": gw_ok,
                "iface": "wlan0" if "wlan0" in ip_addr else "",
                "latency_ms": latency_ms,
                "latency_src": latency_src,
                "gw_latency_ms": gw_latency_ms,
                "net_latency_ms": net_latency_ms,
                "hub_latency_ms": hub_latency_ms,
                "jitter_ms": jitter_ms,
            },
            "qos": {
                "latency_ms": latency_ms,
                "hub_latency_ms": hub_latency_ms,
                "latency_src": latency_src,
                "smoothness_score": smoothness_score,
                "jitter_ms": jitter_ms,
                "qemu_cpu_pct": qemu_cpu_pct,
                "guest_load1": guest_load1,
                "updated_at": qos_updated_at,
            },
            "device": {
                "model": props.get("ro.product.model", ""),
                "android": props.get("ro.build.version.release", ""),
                "sdk": props.get("ro.build.version.sdk", ""),
                "abi": props.get("ro.product.cpu.abi", ""),
                "serial": props.get("ro.serialno", ""),
                "avd": props.get("ro.boot.qemu.avd_name", ""),
                "locale": props.get("persist.sys.locale", ""),
                "timezone": props.get("persist.sys.timezone", ""),
                "width": self.width,
                "height": self.height,
            },
            "third_party_packages": pkgs,
            "third_party_count": len(pkgs),
        }


EMU_BRIDGE = TargetAdb()
