"""Execute webserver Excel→JSON steps over ADB (DeviceSDK Demo path).

Mirrors TestApp step semantics for the actions we need locally:
launchApp / scrollUntilClick / click / wait / input / screenshot.
wait-response-* ends early on DemoReplyMetrics REPLY (event-driven).
"""
from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

from device_app import AdbError, DeviceApp

LogFn = Callable[[str], None]


def load_testcase_json(path: Path) -> Dict[str, Any]:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def _target_value(step: Dict[str, Any]) -> tuple[str, str]:
    target = step.get("target") or {}
    strategy = (target.get("strategy") or "id").lower()
    value = target.get("value") or ""
    return strategy, value


def _click_target(device: DeviceApp, step: Dict[str, Any], *, required: bool) -> bool:
    strategy, value = _target_value(step)
    if not value:
        if required:
            raise AdbError(f"步骤缺 target: {step.get('stepId')}")
        return False
    if strategy == "id":
        ok = device.tap_id(value, retries=3)
    elif strategy == "text":
        ok = device.tap_text(value, retries=2)
    else:
        raise AdbError(f"不支持的 click strategy={strategy} step={step.get('stepId')}")
    if required and not ok:
        raise AdbError(f"点击失败 step={step.get('stepId')} {strategy}={value}")
    return ok


def _scroll_until_click(device: DeviceApp, step: Dict[str, Any]) -> None:
    strategy, value = _target_value(step)
    params = step.get("params") or {}
    max_scrolls = int(params.get("maxScrolls") or 6)
    pause_ms = int(params.get("duration") or 600)
    if strategy != "id" or not value:
        raise AdbError(f"scrollUntilClick 仅支持 id: {step.get('stepId')}")
    for i in range(max_scrolls + 1):
        if device.tap_id(value, retries=1, delay=0.1):
            return
        if i < max_scrolls:
            device.swipe_up()
            time.sleep(max(0.15, pause_ms / 1000.0))
    raise AdbError(f"scrollUntilClick 未找到 {value}")


def _input_text(device: DeviceApp, step: Dict[str, Any]) -> None:
    text = ((step.get("params") or {}).get("text") or "").strip()
    if not text:
        return
    strategy, value = _target_value(step)
    if strategy == "id" and value:
        device.tap_id(value, retries=2)
        time.sleep(0.25)
    else:
        box = device.find_center_by_rid(device.id_input)
        if box:
            device.tap(*box)
            time.sleep(0.25)
    safe = text.replace("'", "'\\''")
    device.shell(
        "am broadcast "
        f"-a {device.package}.ACTION_INPUT_TEXT "
        f"-n {device.package}/.TextInputReceiver "
        f"--es text '{safe}'",
        check=False,
    )
    time.sleep(0.6)


def _send_broadcast(device: DeviceApp, step: Dict[str, Any]) -> None:
    params = step.get("params") or {}
    action_name = (params.get("action") or "").strip()
    text = (params.get("text") or "").strip()
    component = (params.get("component") or "").strip()
    if not action_name or not text:
        raise AdbError(f"broadcast 缺少 action/text: {step.get('stepId')}")
    safe = text.replace("'", "'\\''")
    cmd = f"am broadcast -a {action_name} --es text '{safe}'"
    if component:
        cmd += f" -n {component}"
    device.shell(cmd, check=False)


def _is_soft_click(step: Dict[str, Any]) -> bool:
    sid = str(step.get("stepId") or "")
    return sid.startswith("dismiss-")


def _is_wait_response(step: Dict[str, Any]) -> bool:
    sid = str(step.get("stepId") or "")
    return sid.startswith("wait-response")


# 第 2 案起：案间已回 QA 聊天页，跳过 launch/新会话前置
_DEVICESDK_PRE_STEP_IDS = {
    "wait-home",
    "launch",
    "click-session-manage",
    "click-new-session",
    "wait-new-session",
    # 旧登录前置（兼容历史 JSON）
    "dismiss-session-dialog",
    "scroll-to-enter-test",
    "wait-test-home",
    "dismiss-need-login",
    "click-login",
    "wait-login",
    "dismiss-after-login",
    "click-qa",
    "wait-qa-page",
}


def _is_pre_step(step: Dict[str, Any]) -> bool:
    sid = str(step.get("stepId") or "")
    action = (step.get("action") or "").strip()
    if sid in _DEVICESDK_PRE_STEP_IDS:
        return True
    if action == "launchApp":
        return True
    return False


def run_testcase_steps(
    device: DeviceApp,
    testcase: Dict[str, Any],
    *,
    case_dir: Path,
    screenshot_interval: float,
    log: LogFn,
    wait_with_screenshots: Callable[..., tuple],
    stop_flag=None,
    on_shot: Optional[Callable[[str], None]] = None,
    skip_pre_steps: bool = False,
) -> Dict[str, Any]:
    """Run full JSON steps. Returns turn-level metrics compatible with run_case."""
    steps: List[Dict[str, Any]] = list(testcase.get("steps") or [])
    turns_out: List[Dict[str, Any]] = []
    turn_idx = 0
    pending_text = ""

    for step in steps:
        if stop_flag is not None and stop_flag.is_set():
            raise AdbError("用户停止")
        action = (step.get("action") or "").strip()
        sid = step.get("stepId") or action
        if skip_pre_steps and _is_pre_step(step):
            log(f"  skip pre {sid}")
            continue
        log(f"  step {sid}: {action}")

        if action == "launchApp":
            device.start_main()
            time.sleep(0.4)
            continue

        if action == "scrollUntilClick":
            _scroll_until_click(device, step)
            continue

        if action == "click":
            _click_target(device, step, required=not _is_soft_click(step))
            if _is_soft_click(step):
                device.dismiss_dialogs()
            time.sleep(0.2)
            continue

        if action == "input":
            pending_text = ((step.get("params") or {}).get("text") or "").strip()
            _input_text(device, step)
            continue

        if action == "wait":
            duration_ms = int((step.get("params") or {}).get("duration") or 0)
            if _is_wait_response(step):
                turn_idx += 1
                try:
                    device.logcat_clear()
                except Exception:
                    pass
                wait_sec = max(0.0, duration_ms / 1000.0)
                clarify = ((step.get("params") or {}).get("clarifyMessage") or "").strip()
                log(f"      wait-response up to {wait_sec:.0f}s (REPLY 事件结束)")
                if clarify:
                    log(f"      本轮澄清语料: {clarify[:60]}")
                # 有澄清：先等首轮 REPLY（澄清问句），再自动发出澄清，再等最终 REPLY
                phase1 = wait_sec
                if clarify:
                    phase1 = min(wait_sec, 120.0)
                shots, metrics = wait_with_screenshots(
                    device,
                    case_dir,
                    turn_idx,
                    phase1,
                    screenshot_interval,
                    log,
                    stop_flag=stop_flag,
                    on_shot=on_shot,
                    wait_reply=True,
                )
                reply_text = (metrics.get("reply_text") or "") if isinstance(metrics, dict) else ""
                if clarify and (metrics.get("reason") or metrics.get("ttft_ms") is not None or reply_text):
                    # 首轮已有回复迹象则发澄清并继续等
                    log(f"      自动发送澄清…")
                    try:
                        device.logcat_clear()
                    except Exception:
                        pass
                    device.send_chat(clarify)
                    remain = max(30.0, wait_sec - phase1)
                    shots2, metrics2 = wait_with_screenshots(
                        device,
                        case_dir,
                        turn_idx + 100,  # 澄清阶段截图命名 turn101_… 避免覆盖
                        remain,
                        screenshot_interval,
                        log,
                        stop_flag=stop_flag,
                        on_shot=on_shot,
                        wait_reply=True,
                    )
                    shots = list(shots or []) + list(shots2 or [])
                    # 以澄清后的最终 REPLY 为准
                    if metrics2:
                        metrics = dict(metrics or {})
                        metrics.update(
                            {
                                "ttft_ms": metrics2.get("ttft_ms") or metrics.get("ttft_ms"),
                                "latency_ms": metrics2.get("latency_ms"),
                                "reason": metrics2.get("reason") or metrics.get("reason"),
                                "reply_text": metrics2.get("reply_text") or metrics.get("reply_text"),
                                "clarify_sent": clarify,
                                "pre_clarify_reply": reply_text,
                            }
                        )
                turns_out.append(
                    {
                        "index": turn_idx,
                        "text": pending_text,
                        "wait_sec": wait_sec,
                        "clarify": clarify,
                        "screenshots": shots,
                        "ttft_ms": metrics.get("ttft_ms"),
                        "latency_ms": metrics.get("latency_ms"),
                        "reply_reason": metrics.get("reason"),
                        "reply_text": metrics.get("reply_text"),
                        "metrics": metrics,
                    }
                )
                pending_text = ""
            else:
                time.sleep(max(0.0, duration_ms / 1000.0))
            continue

        if action == "broadcast":
            _send_broadcast(device, step)
            pending_text = ((step.get("params") or {}).get("text") or "").strip() or pending_text
            continue

        if action == "screenshot":
            name = ((step.get("params") or {}).get("saveName") or sid) + ".png"
            path = device.screencap(case_dir / name)
            if on_shot:
                on_shot(str(path))
            continue

        log(f"  [skip] 未实现 action={action}")

    return {"turns": turns_out}
