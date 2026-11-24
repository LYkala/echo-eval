"""Map common precondition steps to deterministic ADB (no UI tap)."""
from __future__ import annotations

import re
from dataclasses import dataclass


@dataclass
class DeterministicPlan:
    commands: list[str]
    kind: str  # home | launch | force_stop | volume | brightness | mute
    package: str = ""
    notes: str = ""
    # If True, stop after the first successful command (fallbacks).
    any_success: bool = False
    # For kind=launch: use AdbClient.launch_package instead of raw commands.
    prefer_settings_intent: bool = False


def _pct_to_media_index(pct: float, *, max_index: int = 15) -> int:
    pct = max(0.0, min(100.0, pct))
    return int(round(pct / 100.0 * max_index))


def _pct_to_brightness(pct: float) -> int:
    pct = max(0.0, min(100.0, pct))
    return int(round(pct / 100.0 * 255))


def resolve_package(step_text: str, packages: dict[str, str]) -> str | None:
    """Match '打开网易云音乐' / '打开【测试应用】' against packages map."""
    s = (step_text or "").strip()
    m = re.match(r"^打开\s*(.+)$", s)
    if not m:
        return None
    name = m.group(1).strip()
    name = name.strip("【】[]")
    if name in packages:
        return packages[name]
    # fuzzy: package key contained in name or vice versa
    for k, pkg in packages.items():
        if k in name or name in k:
            return pkg
    return None


def plan_deterministic_step(
    step_text: str,
    *,
    packages: dict[str, str],
) -> DeterministicPlan | None:
    """Return ADB shell command plan if this step can be done without taps."""
    s = (step_text or "").strip()
    if not s:
        return None

    if s in ("回桌面", "返回桌面", "回到桌面"):
        return DeterministicPlan(
            commands=["input keyevent 3"],
            kind="home",
            notes="KEYCODE_HOME",
        )

    if s in ("取消静音", "解除静音"):
        return DeterministicPlan(
            commands=[
                "cmd media_session volume --stream 3 --adj raise",
                "media volume --stream 3 --adj raise",
            ],
            kind="mute",
            notes="尝试取消静音（多命令择一成功即可）",
            any_success=True,
        )

    if s in ("静音", "开启静音"):
        return DeterministicPlan(
            commands=[
                "cmd media_session volume --stream 3 --set 0",
                "media volume --stream 3 --set 0",
            ],
            kind="mute",
            notes="媒体音量置 0",
            any_success=True,
        )

    m = re.match(r"^音量调到\s*(\d+)\s*%?\s*$", s)
    if m:
        pct = float(m.group(1))
        idx = _pct_to_media_index(pct)
        return DeterministicPlan(
            commands=[
                f"cmd media_session volume --stream 3 --set {idx}",
                f"media volume --stream 3 --set {idx}",
            ],
            kind="volume",
            notes=f"媒体音量约 {pct:g}% → index {idx}",
            any_success=True,
        )

    m = re.match(r"^亮度调到\s*(\d+)\s*%?\s*$", s)
    if m:
        pct = float(m.group(1))
        val = _pct_to_brightness(pct)
        return DeterministicPlan(
            commands=[
                "settings put system screen_brightness_mode 0",
                f"settings put system screen_brightness {val}",
            ],
            kind="brightness",
            notes=f"亮度约 {pct:g}% → {val}/255",
        )

    if s in ("关闭自动亮度", "关掉自动亮度"):
        return DeterministicPlan(
            commands=["settings put system screen_brightness_mode 0"],
            kind="brightness",
            notes="关闭自动亮度",
        )

    pkg = resolve_package(s, packages)
    if pkg:
        app_name = ""
        m_open = re.match(r"^打开\s*(.+)$", s)
        if m_open:
            app_name = m_open.group(1).strip().strip("【】[]")
        prefer_settings = app_name in ("设置", "系统设置") or pkg == "com.android.settings"
        # Placeholder command list for cache / logging; runtime uses launch_package.
        return DeterministicPlan(
            commands=[
                f"am start -a android.intent.action.MAIN "
                f"-c android.intent.category.LAUNCHER -p {pkg}"
            ],
            kind="launch",
            package=pkg,
            notes=f"启动 {pkg}（am start / resolve-activity / monkey 兜底）",
            any_success=True,
            prefer_settings_intent=prefer_settings,
        )

    return None


def is_gui_step(step_text: str, *, packages: dict[str, str]) -> bool:
    return plan_deterministic_step(step_text, packages=packages) is None
