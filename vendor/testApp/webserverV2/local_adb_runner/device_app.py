"""ADB helpers: drive Device SDK Demo + screencap."""
from __future__ import annotations

import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List, Optional, Tuple


class AdbError(RuntimeError):
    pass


class DeviceApp:
    def __init__(
        self,
        adb: str,
        serial: str = "",
        package: str = "com.example.devicesdkdemo",
        main_activity: str = "com.example.devicesdkdemo/.ConfigActivity",
        qa_activity: str = "com.example.devicesdkdemo/.QAActivity",
        ui_ids: Optional[dict] = None,
        screenshot_max_width: int = 1080,
        screenshot_jpeg_quality: int = 80,
        adb_host: str = "",
        adb_port: int = 0,
    ):
        self.adb = adb
        self.serial = serial
        self.package = package
        self.adb_host = (adb_host or "").strip()
        self.adb_port = int(adb_port or 0)
        # ConfigActivity 才是 exported LAUNCHER；Main/QA 不可被 adb 直接 start
        self.main_activity = main_activity
        self.qa_activity = qa_activity
        ids = ui_ids or {}
        self.id_confirm = ids.get("confirm", f"{package}:id/btn_confirm")
        self.id_login = ids.get("login", f"{package}:id/btn_login")
        self.id_qa = ids.get("qa", f"{package}:id/btn_qa")
        self.id_input = ids.get("input", f"{package}:id/et_input")
        self.id_send = ids.get("send", f"{package}:id/btn_send")
        self.screenshot_max_width = int(screenshot_max_width or 0)
        self.screenshot_jpeg_quality = int(screenshot_jpeg_quality or 0)

    def _base(self) -> List[str]:
        cmd = [self.adb]
        if self.adb_host:
            cmd.extend(["-H", self.adb_host])
        if self.adb_port:
            cmd.extend(["-P", str(self.adb_port)])
        if self.serial:
            cmd.extend(["-s", self.serial])
        return cmd

    def run(self, *args: str, timeout: float = 60, check: bool = True) -> str:
        cmd = self._base() + list(args)
        try:
            p = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=timeout,
            )
        except FileNotFoundError as e:
            raise AdbError(f"找不到 adb: {self.adb}") from e
        except subprocess.TimeoutExpired as e:
            raise AdbError(f"adb 超时: {' '.join(cmd)}") from e
        out = (p.stdout or "") + (p.stderr or "")
        if check and p.returncode != 0:
            raise AdbError(f"adb failed ({p.returncode}): {' '.join(cmd)}\n{out}")
        return out

    def shell(self, cmdline: str, timeout: float = 60, check: bool = True) -> str:
        return self.run("shell", cmdline, timeout=timeout, check=check)

    def pick_serial_if_needed(self) -> str:
        if self.serial:
            return self.serial
        out = self.run("devices", check=True)
        lines = [ln.strip() for ln in out.splitlines()[1:] if ln.strip()]
        devices = []
        for ln in lines:
            parts = ln.split()
            if len(parts) >= 2 and parts[1] == "device":
                devices.append(parts[0])
        if not devices:
            raise AdbError("没有 online 真机，请 USB 连接并开启调试")
        self.serial = devices[0]
        return self.serial

    def ensure_package(self) -> None:
        out = self.shell(f"pm path {self.package}", check=False)
        if "package:" not in out:
            raise AdbError(f"未安装 {self.package}")

    def start_main(self) -> None:
        """打开 Demo（LAUNCHER=ConfigActivity）。"""
        self.shell(
            f"am start -a android.intent.action.MAIN "
            f"-c android.intent.category.LAUNCHER "
            f"-n {self.main_activity}",
            check=False,
        )
        # 兜底：monkey 拉起包
        out = self.shell(
            f"dumpsys activity activities | grep topResumedActivity",
            check=False,
        )
        if self.package not in out:
            self.shell(
                f"monkey -p {self.package} -c android.intent.category.LAUNCHER 1",
                check=False,
            )

    def start_qa(self) -> None:
        """QA 未 exported，只能从 UI 点进；这里仅尝试（多数机型会 Permission Denial）。"""
        self.shell(
            f"am start -n {self.qa_activity}",
            check=False,
        )

    def a11y_services_value(self) -> str:
        menu = (
            "com.android.settings/com.android.settings.accessibility."
            "accessibilitymenu.AccessibilityMenuService"
        )
        svc = (
            f"{self.package}/com.huawei.aisfsdkv2.component.androidfunc."
            "accessibility.impl.AccessibilityManagerImpl"
        )
        return f"{menu}:{svc}"

    def return_after_case(self, case_type: str = "GUI") -> None:
        """跨 App 后拉回 Demo QA。"""
        # NEW_TASK|CLEAR_TASK：把 Demo 直接拉到前台
        self.shell(f"am start -f 0x10008000 -n {self.main_activity}", check=False)
        time.sleep(0.55)
        if self.package not in (self.current_focus() or ""):
            self.shell(
                f"am start -a android.intent.action.MAIN "
                f"-c android.intent.category.LAUNCHER -n {self.main_activity}",
                check=False,
            )
            time.sleep(0.45)

        if self.find_center_by_rid(self.id_input):
            return

        # Config 页确认按钮在屏幕下方，先上滑再点
        w, h = self.screen_size()
        confirm_xy = (w // 2, int(h * 0.908))
        for _ in range(5):
            self.swipe_up()
            time.sleep(0.08)
        self.tap(*confirm_xy)
        time.sleep(0.2)
        self.tap(*confirm_xy)
        time.sleep(0.35)
        self.dismiss_dialogs()
        if self._center_in_root(self.dump_ui(), rid=self.id_confirm) is not None:
            self.tap_id(self.id_confirm, retries=2, delay=0.12)
            time.sleep(0.35)
            self.dismiss_dialogs()

        if self.find_center_by_rid(self.id_input):
            return
        if self.tap_id(self.id_login, retries=2, delay=0.15):
            time.sleep(0.7)
            self.dismiss_dialogs()
        if self.tap_id(self.id_qa, retries=3, delay=0.15):
            time.sleep(0.5)
        if not self.find_center_by_rid(self.id_input):
            raise AdbError("拉回聊天页失败：et_input 不可见")

    def force_stop(self) -> None:
        self.shell(f"am force-stop {self.package}", check=False)

    def dump_ui(self) -> Optional[ET.Element]:
        """一次 shell 完成 dump+读回，避免 adb pull 往返。"""
        remote = "/sdcard/Download/_gui_runner_ui.xml"
        out = self.shell(
            f"uiautomator dump {remote} >/dev/null 2>&1; cat {remote}",
            check=False,
            timeout=12,
        )
        # uiautomator 常在 stderr/stdout 塞一句 UI hierchary dumped...
        start = out.find("<?xml")
        if start < 0:
            return None
        xml_text = out[start:]
        end = xml_text.rfind("</hierarchy>")
        if end >= 0:
            xml_text = xml_text[: end + len("</hierarchy>")]
        try:
            return ET.fromstring(xml_text)
        except ET.ParseError:
            return None

    @staticmethod
    def _bounds_center(bounds: str) -> Optional[Tuple[int, int]]:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
        if not m:
            return None
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2

    def _center_in_root(
        self, root: Optional[ET.Element], *, rid: str = "", text: str = ""
    ) -> Optional[Tuple[int, int]]:
        if root is None:
            return None
        for node in root.iter("node"):
            if rid and node.attrib.get("resource-id") == rid:
                return self._bounds_center(node.attrib.get("bounds", ""))
            if text and (
                node.attrib.get("text") == text or node.attrib.get("content-desc") == text
            ):
                return self._bounds_center(node.attrib.get("bounds", ""))
        return None

    def find_center_by_rid(self, resource_id: str) -> Optional[Tuple[int, int]]:
        return self._center_in_root(self.dump_ui(), rid=resource_id)

    def find_center_by_text(self, text: str) -> Optional[Tuple[int, int]]:
        return self._center_in_root(self.dump_ui(), text=text)

    def tap(self, x: int, y: int) -> None:
        self.shell(f"input tap {x} {y}", check=False)

    def tap_id(self, resource_id: str, retries: int = 2, delay: float = 0.25) -> bool:
        for _ in range(retries):
            c = self.find_center_by_rid(resource_id)
            if c:
                self.tap(*c)
                return True
            time.sleep(delay)
        return False

    def tap_text(self, text: str, retries: int = 1, delay: float = 0.2) -> bool:
        for _ in range(retries):
            c = self.find_center_by_text(text)
            if c:
                self.tap(*c)
                return True
            time.sleep(delay)
        return False

    def swipe_up(self) -> None:
        # 配置页往上滚，露出「进入测试」（短滑、快）
        w, h = self.screen_size()
        self.shell(
            f"input swipe {w // 2} {int(h * 0.72)} {w // 2} {int(h * 0.28)} 160",
            check=False,
        )

    def screen_size(self) -> Tuple[int, int]:
        out = self.shell("wm size", check=False)
        m = re.search(r"(\d+)x(\d+)", out or "")
        if m:
            return int(m.group(1)), int(m.group(2))
        return 1080, 2400

    def current_focus(self) -> str:
        out = self.shell(
            "dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 2",
            check=False,
            timeout=5,
        )
        return out or ""

    def dismiss_dialogs(self, root: Optional[ET.Element] = None) -> None:
        """同一份 dump 里找弹窗按钮，最多点一次。"""
        root = root if root is not None else self.dump_ui()
        if root is None:
            return
        for label in ("知道了", "我知道了", "确定", "取消"):
            c = self._center_in_root(root, text=label)
            if c:
                self.tap(*c)
                time.sleep(0.12)
                return

    def prepare_qa(
        self,
        do_login: bool = True,
        max_scrolls: int = 4,
        stop_flag: Optional[threading.Event] = None,
    ) -> None:
        """Config -> confirm -> login -> QA. Only tap button resource-ids. Never open a11y settings."""

        def _check_stop() -> None:
            if stop_flag is not None and stop_flag.is_set():
                raise AdbError("用户停止")

        def _wait_focus(substr: str, timeout: float = 2.5) -> bool:
            deadline = time.time() + timeout
            while time.time() < deadline:
                _check_stop()
                if substr in self.current_focus():
                    return True
                time.sleep(0.12)
            return False

        def _back_if_settings() -> None:
            focus = self.current_focus()
            if "settings" in focus.lower() or "SubSettings" in focus:
                self.shell("input keyevent 4", check=False)
                time.sleep(0.25)

        def _tap_rid(root: Optional[ET.Element], rid: str) -> bool:
            c = self._center_in_root(root, rid=rid)
            if not c:
                return False
            x, y = c
            _, hh = self.screen_size()
            if y > int(hh * 0.92):
                y = int(hh * 0.908)
            self.tap(x, y)
            return True

        w, h = self.screen_size()
        confirm_xy = (w // 2, int(h * 0.908))

        self.ensure_package()
        _check_stop()
        _back_if_settings()
        if "QAActivity" in self.current_focus():
            return

        self.start_main()
        time.sleep(0.35)
        _check_stop()
        _back_if_settings()
        if "QAActivity" in self.current_focus():
            return

        if "MainActivity" not in self.current_focus():
            for _ in range(max_scrolls):
                _check_stop()
                self.swipe_up()
                time.sleep(0.08)
            time.sleep(0.12)
            self.tap(*confirm_xy)
            time.sleep(0.12)
            self.tap(*confirm_xy)
            if not _wait_focus("MainActivity", timeout=1.2):
                root = self.dump_ui()
                self.dismiss_dialogs(root)
                if not _tap_rid(root, self.id_confirm):
                    raise AdbError("配置页找不到确认按钮")
                if not _wait_focus("MainActivity", timeout=2.0):
                    raise AdbError("点击确认后未能进入主测页")

        _check_stop()
        _back_if_settings()
        if "QAActivity" in self.current_focus():
            return

        root = self.dump_ui()
        self.dismiss_dialogs(root)

        # 只点登录 + QA，点完即结束（不校验 QA 页 / 输入框 / 无障碍）
        if do_login and self._center_in_root(root, rid=self.id_login):
            _tap_rid(root, self.id_login)
            time.sleep(0.5)

        _check_stop()
        root = self.dump_ui()
        self.dismiss_dialogs(root)
        if not _tap_rid(root, self.id_qa):
            raise AdbError("主测页找不到 QA 按钮")
        time.sleep(0.3)

    def send_chat(self, text: str) -> None:
        """填入语料并点击发送（优先点 et_input 中心，避免 a11y 在 0,0 误触）。"""
        if not self.find_center_by_rid(self.id_input):
            self.tap_id(self.id_qa, retries=2)
            time.sleep(1.0)
        box = self.find_center_by_rid(self.id_input)
        if not box:
            raise AdbError("发送前找不到 et_input，请先进入 QA 页")

        # 聚焦输入框（用真实坐标，避免 TextInputReceiver 内部 inputText(0,0) 误点左上角）
        self.tap(*box)
        time.sleep(0.35)

        safe = text.replace("'", "'\\''")
        # 只用 INPUT，发送交给下面点「发送」按钮（比 IME Enter 更稳）
        self.shell(
            "am broadcast "
            f"-a {self.package}.ACTION_INPUT_TEXT "
            f"-n {self.package}/.TextInputReceiver "
            f"--es text '{safe}'",
            check=False,
        )
        time.sleep(0.9)

        # 若仍在 QA，点发送；若被误带离 QA，报错
        if not self.find_center_by_rid(self.id_input):
            raise AdbError("填字后离开了 QA 页，未发送")
        if not self.tap_id(self.id_send, retries=3):
            raise AdbError("找不到发送按钮 btn_send")
        time.sleep(0.5)

    def set_case_context(
        self,
        task_id: str = "",
        case_id: str = "",
        device_id: str = "",
        base_url: str = "",
        clear: bool = False,
    ) -> None:
        """告诉 Demo WebServerReporter 当前 task/case（直报 case_reply）。"""
        if clear:
            self.shell(
                f"am broadcast -a {self.package}.ACTION_CLEAR_CASE_CONTEXT "
                f"-n {self.package}/.CaseContextReceiver",
                check=False,
            )
            return
        parts = [
            f"am broadcast -a {self.package}.ACTION_SET_CASE_CONTEXT",
            f"-n {self.package}/.CaseContextReceiver",
        ]
        if task_id:
            parts.append(f"--es taskId '{task_id}'")
        if case_id:
            parts.append(f"--es caseId '{case_id}'")
        did = device_id or self.serial
        if did:
            parts.append(f"--es deviceId '{did}'")
        if base_url:
            parts.append(f"--es baseUrl '{base_url}'")
        self.shell(" ".join(parts), check=False)

    def screencap(self, local_path: Path) -> Path:
        """全屏截图；可选等比缩放到 max_width 并转 JPEG（对齐 TestApp ScreenshotUtil）。"""
        local_path = Path(local_path)
        local_path.parent.mkdir(parents=True, exist_ok=True)
        remote = "/sdcard/Download/_gui_runner_shot.png"
        raw = local_path.with_suffix(".png") if local_path.suffix.lower() != ".png" else local_path
        self.shell(f"screencap -p {remote}", check=True)
        self.run("pull", remote, str(raw), check=True)
        self.shell(f"rm {remote}", check=False)
        return self._maybe_compress_shot(raw)

    def _maybe_compress_shot(self, source: Path) -> Path:
        max_w = self.screenshot_max_width
        quality = self.screenshot_jpeg_quality
        if max_w <= 0 and quality <= 0:
            return source
        if max_w <= 0:
            max_w = 1080
        if quality <= 0 or quality > 100:
            quality = 80
        try:
            from PIL import Image

            with Image.open(source) as im:
                im = im.convert("RGB")
                w, h = im.size
                if w > max_w:
                    nh = max(1, int(h * (max_w / float(w))))
                    im = im.resize((max_w, nh), Image.Resampling.LANCZOS)
                out = source.with_suffix(".jpg")
                im.save(out, format="JPEG", quality=quality, optimize=True)
            if out != source and source.exists():
                try:
                    source.unlink()
                except OSError:
                    pass
            return out
        except Exception:
            return source

    def logcat_clear(self) -> None:
        self.run("logcat", "-c", check=False)

    def logcat_metrics(self, timeout: float = 15) -> str:
        """Dump DemoReplyMetrics lines from logcat buffer."""
        return self.run(
            "logcat",
            "-d",
            "-s",
            "DemoReplyMetrics:I",
            timeout=timeout,
            check=False,
        )

    def wait_reply_metrics(
        self,
        timeout_sec: float,
        poll_sec: float = 1.0,
        stop_flag=None,
    ):
        """Poll until REPLY event or timeout. Returns (event|None, all_events)."""
        from reply_metrics import assemble_reply_text, extract_events

        deadline = time.time() + max(0.0, timeout_sec)
        seen_reply = None
        all_events = []
        while time.time() < deadline:
            if stop_flag is not None and stop_flag.is_set():
                break
            blob = self.logcat_metrics()
            events = extract_events(blob)
            all_events = events
            for ev in events:
                if ev.kind == "REPLY":
                    seen_reply = ev
                    # 挂上拼接正文，调用方可读 event 旁的 all_events / assemble
                    ev.fields["text"] = assemble_reply_text(events) or ev.text
                    return seen_reply, all_events
            remain = deadline - time.time()
            if remain <= 0:
                break
            time.sleep(min(poll_sec, remain))
        return seen_reply, all_events

    def install_apk(self, apk_path: str) -> str:
        return self.run("install", "-r", "-d", apk_path, timeout=180, check=False)