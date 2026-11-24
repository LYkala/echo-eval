"""Whitelist and execute safe precondition ADB shell commands."""
from __future__ import annotations

import re
from typing import Iterable

from echo_eval.device.adb import AdbClient, AdbError

_TAP = re.compile(r"^input\s+tap\s+-?\d+\s+-?\d+\s*$", re.I)
_SWIPE = re.compile(
    r"^input\s+swipe\s+-?\d+\s+-?\d+\s+-?\d+\s+-?\d+(?:\s+\d+)?\s*$",
    re.I,
)
_KEYEVENT = re.compile(
    r"^input\s+keyevent\s+(?:\d+|KEYCODE_[A-Z0-9_]+)\s*$",
    re.I,
)


def is_allowed_input_command(cmd: str) -> bool:
    s = (cmd or "").strip()
    if not s:
        return False
    # reject chaining / redirection
    if any(x in s for x in (";", "&&", "||", "|", ">", "<", "`", "$(")):
        return False
    return bool(_TAP.match(s) or _SWIPE.match(s) or _KEYEVENT.match(s))


def filter_allowed_commands(commands: Iterable[str]) -> tuple[list[str], list[str]]:
    """Return (allowed, rejected)."""
    ok: list[str] = []
    bad: list[str] = []
    for c in commands:
        s = str(c).strip()
        if is_allowed_input_command(s):
            ok.append(s)
        else:
            bad.append(s)
    return ok, bad


def run_allowed_commands(adb: AdbClient, commands: list[str]) -> list[dict]:
    allowed, rejected = filter_allowed_commands(commands)
    if rejected:
        raise AdbError(
            "拒绝执行非白名单命令（仅允许 input tap/swipe/keyevent）: "
            + "; ".join(rejected[:5])
        )
    if not allowed:
        raise AdbError("没有可执行的 input 命令")
    logs: list[dict] = []
    for cmd in allowed:
        adb.shell(cmd, check=True, timeout=30)
        logs.append({"cmd": cmd, "ok": True})
    return logs
