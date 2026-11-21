"""Bridge: run DeviceSDK Excel tasks via ADB (like local_device_gui_runner), no TestApp."""
from __future__ import annotations

import json
import logging
import sys
import threading
import time
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

_PKG = Path(__file__).resolve().parent
if str(_PKG) not in sys.path:
    sys.path.insert(0, str(_PKG))

from device_app import AdbError, DeviceApp  # noqa: E402
from json_step_runner import load_testcase_json, run_testcase_steps  # noqa: E402
from paths import find_adb, load_config  # noqa: E402
from reply_metrics import assemble_reply_text, extract_events  # noqa: E402

logger = logging.getLogger(__name__)

DEVICESDK_PKG = "com.example.devicesdkdemo"

_cancel_flags: Dict[str, threading.Event] = {}
_cancel_lock = threading.Lock()


def is_devicesdk_package(package: str) -> bool:
    p = (package or "").strip()
    return p == DEVICESDK_PKG or p.endswith("devicesdkdemo")


def get_cancel_flag(task_id: str) -> threading.Event:
    with _cancel_lock:
        ev = _cancel_flags.get(task_id)
        if ev is None:
            ev = threading.Event()
            _cancel_flags[task_id] = ev
        return ev


def request_cancel(task_id: str) -> None:
    get_cancel_flag(task_id).set()


def clear_cancel(task_id: str) -> None:
    with _cancel_lock:
        _cancel_flags.pop(task_id, None)


def resolve_adb_endpoint(device_id: str, adb_meta: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """Return {serial, host, port} for a device id."""
    meta = adb_meta or {}
    serial = meta.get("device_id") or device_id
    host = meta.get("adb_host") or ""
    port = int(meta.get("adb_port") or 0)
    channel = meta.get("channel") or ""
    kind = meta.get("kind") or ""
    if not host and (kind == "adb_usb_bridge" or "15037" in str(channel)):
        host = "127.0.0.1"
        port = port or 15037
    if not host and channel and ":" in str(channel) and kind == "adb_usb_bridge":
        h, _, p = str(channel).partition(":")
        host = h or "127.0.0.1"
        try:
            port = int(p) if p else 15037
        except ValueError:
            port = 15037
    return {"serial": serial, "host": host, "port": port}


def make_device_for_endpoint(
    serial: str,
    host: str = "",
    port: int = 0,
    package: str = DEVICESDK_PKG,
) -> DeviceApp:
    cfg = load_config()
    return DeviceApp(
        adb=find_adb(cfg),
        serial=serial,
        package=package,
        adb_host=host,
        adb_port=port,
        screenshot_max_width=int(cfg.get("screenshot_max_width") or 1080),
        screenshot_jpeg_quality=int(cfg.get("screenshot_jpeg_quality") or 80),
    )


def restore_demo_accessibility(device: DeviceApp, package: str = DEVICESDK_PKG) -> None:
    demo_svc = (
        f"{package}/com.huawei.aisfsdkv2.component.androidfunc."
        "accessibility.impl.AccessibilityManagerImpl"
    )
    try:
        cur = (
            device.shell("settings get secure enabled_accessibility_services", check=False)
            or ""
        ).strip()
    except Exception:
        cur = ""
    parts = [p for p in cur.split(":") if p] if cur and cur != "null" else []
    if demo_svc not in parts:
        parts.append(demo_svc)
    without = [p for p in parts if p != demo_svc]
    try:
        if without:
            device.shell(
                "settings put secure enabled_accessibility_services " + ":".join(without),
                check=False,
            )
        else:
            device.shell("settings delete secure enabled_accessibility_services", check=False)
        device.shell("settings put secure accessibility_enabled 1", check=False)
        time.sleep(0.3)
        device.shell(
            "settings put secure enabled_accessibility_services " + ":".join(parts),
            check=False,
        )
        device.shell("settings put secure accessibility_enabled 1", check=False)
    except Exception as e:
        logger.warning("restore accessibility failed: %s", e)


def force_stop_demo(device: DeviceApp, package: str = DEVICESDK_PKG) -> None:
    try:
        device.shell("input keyevent KEYCODE_HOME", check=False)
        device.shell(f"am force-stop {package}", check=False)
        device.shell("am force-stop com.example.testapp", check=False)
        restore_demo_accessibility(device, package)
    except Exception as e:
        logger.warning("force_stop_demo: %s", e)


def _replies_ready(case_dir: Path, min_rounds: int = 1) -> bool:
    path = case_dir / "replies.json"
    if not path.is_file():
        return False
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        replies = data.get("replies") or []
        finals = [
            r
            for r in replies
            if r.get("is_final") or r.get("reply_text") or r.get("task_completed")
        ]
        return len(finals) >= min_rounds
    except Exception:
        return False


def run_json_case_adb(
    *,
    task_id: str,
    case_id: str,
    json_path: Path,
    case_dir: Path,
    device: DeviceApp,
    base_url: str = "",
    stop_flag: Optional[threading.Event] = None,
    log: Optional[Callable[[str], None]] = None,
    wait_http_replies: bool = True,
    screenshot_interval: float = 3.0,
) -> Dict[str, Any]:
    """Execute one Excel→JSON case over ADB; set Demo case context for HTTP replies."""
    log = log or (lambda m: logger.info("[%s/%s] %s", task_id, case_id, m))
    case_dir = Path(case_dir)
    case_dir.mkdir(parents=True, exist_ok=True)
    stop_flag = stop_flag or get_cancel_flag(task_id)

    device.set_case_context(
        task_id=task_id,
        case_id=case_id,
        device_id=device.serial,
        base_url=base_url,
    )

    def _on_shot(p: str) -> None:
        log(f"shot {Path(p).name}")

    def wait_with_http(
        device_arg,
        case_dir_arg,
        turn_idx,
        wait_sec,
        interval,
        log_fn,
        stop_flag=None,
        on_shot=None,
        wait_reply=True,
        poll_sec=0.4,
    ):
        shots: List[str] = []
        metrics: Dict[str, Any] = {}
        deadline = time.time() + max(0.0, float(wait_sec))
        n = 0
        reply_ev = None
        poll_sec = max(0.2, float(poll_sec))
        interval = max(poll_sec, float(interval))
        sf = stop_flag

        def _cap(suffix: str = "") -> None:
            nonlocal n
            n += 1
            name = f"turn{turn_idx:02d}_shot{n:03d}{suffix}.png"
            final = device_arg.screencap(Path(case_dir_arg) / name)
            shots.append(final.name)
            if on_shot:
                on_shot(str(final))

        _cap()
        next_shot_at = time.time() + interval
        while time.time() < deadline:
            if sf and sf.is_set():
                log_fn("  [stop] user cancelled wait")
                break
            if wait_http_replies and _replies_ready(Path(case_dir_arg), 1):
                log_fn("  [http] replies.json received; end wait early")
                metrics["reason"] = "http_reply"
                break
            if wait_reply:
                try:
                    events = extract_events(device_arg.logcat_metrics(timeout=8))
                    for ev in events:
                        if ev.kind == "FIRST_TOKEN" and "ttft_ms" not in metrics:
                            metrics["ttft_ms"] = ev.ttft_ms
                            metrics["first_token_kind"] = ev.fields.get("kind")
                        if ev.kind == "REPLY":
                            reply_ev = ev
                            metrics["ttft_ms"] = ev.ttft_ms
                            metrics["latency_ms"] = ev.latency_ms
                            metrics["reason"] = ev.reason
                            metrics["reply_text"] = assemble_reply_text(events)
                            break
                except Exception as e:
                    log_fn(f"  [warn] metrics: {e}")
                if reply_ev is not None:
                    break
            now = time.time()
            if now >= next_shot_at:
                try:
                    _cap()
                except AdbError as e:
                    log_fn(f"  [warn] screencap: {e}")
                next_shot_at = now + interval
            remain = deadline - time.time()
            if remain <= 0:
                break
            time.sleep(min(poll_sec, remain))
        try:
            _cap("_end")
        except AdbError:
            pass
        return shots, metrics

    try:
        tc = load_testcase_json(Path(json_path))
        result = run_testcase_steps(
            device,
            tc,
            case_dir=case_dir,
            screenshot_interval=screenshot_interval,
            log=log,
            wait_with_screenshots=wait_with_http,
            stop_flag=stop_flag,
            on_shot=_on_shot,
        )
        if isinstance(result, dict):
            result.setdefault("ok", True)
            return result
        return {"ok": True}
    except Exception as e:
        log(f"case error: {e}")
        return {"ok": False, "error": str(e)}
    finally:
        try:
            device.set_case_context(clear=True)
        except Exception:
            pass


def execute_devicesdk_task_adb(
    *,
    task_id: str,
    files: List[str],
    testset_dir: Path | str,
    results_root: Path | str,
    device_id: str,
    adb_meta: Optional[Dict[str, Any]] = None,
    target_package: str = DEVICESDK_PKG,
    base_url: str = "",
    is_cancelled: Optional[Callable[[], bool]] = None,
    update_case_progress: Optional[Callable[[str, str], None]] = None,
    on_case_done: Optional[Callable[[str, Dict[str, Any]], None]] = None,
) -> None:
    """Run all case JSON files for a DeviceSDK task via ADB."""
    clear_cancel(task_id)
    stop_flag = get_cancel_flag(task_id)
    ep = resolve_adb_endpoint(device_id, adb_meta)
    device = make_device_for_endpoint(
        ep["serial"], ep["host"], ep["port"], package=target_package
    )
    device.pick_serial_if_needed()
    device.ensure_package()
    log = lambda m: logger.info("[adb-task %s] %s", task_id, m)
    testset_dir = Path(testset_dir)
    results_root = Path(results_root)

    for filename in files:
        if stop_flag.is_set() or (is_cancelled and is_cancelled()):
            log("cancelled before next case")
            break
        case_id = Path(filename).stem
        json_path = testset_dir / filename
        if not json_path.is_file():
            log(f"missing {json_path}")
            if update_case_progress:
                update_case_progress(filename, "failed")
            continue
        case_dir = results_root / task_id / case_id
        case_dir.mkdir(parents=True, exist_ok=True)
        if update_case_progress:
            update_case_progress(filename, "running")
        log(f"start case {case_id} on {device.serial} host={ep['host']}:{ep['port']}")
        try:
            result = run_json_case_adb(
                task_id=task_id,
                case_id=case_id,
                json_path=json_path,
                case_dir=case_dir,
                device=device,
                base_url=base_url,
                stop_flag=stop_flag,
                log=log,
            )
            if isinstance(result, dict):
                (case_dir / "result.json").write_text(
                    json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
                )
            status = "completed"
            if stop_flag.is_set():
                status = "cancelled"
            elif isinstance(result, dict) and result.get("ok") is False:
                status = "failed"
            if update_case_progress:
                update_case_progress(filename, status)
            if on_case_done:
                on_case_done(filename, result if isinstance(result, dict) else {})
        except Exception as e:
            logger.exception("case %s failed: %s", case_id, e)
            if update_case_progress:
                update_case_progress(filename, "failed")
            if on_case_done:
                on_case_done(filename, {"ok": False, "error": str(e)})
        if stop_flag.is_set() or (is_cancelled and is_cancelled()):
            force_stop_demo(device, target_package)
            break
    clear_cancel(task_id)
