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


_SLEEP = re.compile(r"^sleep\s+(\d+(?:\.\d+)?)\s*$", re.I)
_SLEEP_ANY = re.compile(r"sleep\s+(\d+(?:\.\d+)?)", re.I)


def _split_shell_chain(cmd: str) -> list[str]:
    """Split a; b; c into separate adb shell invocations.

    adb.shell(one_string) often does not run through `sh -c`, so semicolons
    inside a single cache entry would not execute as a sequence.
    """
    s = (cmd or "").strip()
    if not s:
        return []
    if ";" in s:
        return [p.strip() for p in s.split(";") if p.strip()]
    if "&&" in s:
        return [p.strip() for p in s.split("&&") if p.strip()]
    return [s]


def _shell_timeout(cmd: str) -> float:
    m = _SLEEP.match(cmd.strip())
    if m:
        return float(m.group(1)) + 15.0
    timeout = 30.0
    for m in _SLEEP_ANY.finditer(cmd):
        timeout += float(m.group(1)) + 15.0
    return timeout


def run_cached_step_commands(
    adb: AdbClient,
    commands: list[str],
    *,
    kind: str = "",
) -> list[dict]:
    """Execute commands from a passed cache entry without rewriting them.

    - kind=vl: input whitelist only
    - other kinds (launch/volume/...): run shell commands as stored, so manual
      edits like am start / sleep / input tap are honored

    Prefer listing steps as separate array items. A single string with
    ``;`` / ``&&`` is split and executed sequentially for adb compatibility.
    """
    if (kind or "").strip().lower() == "vl":
        return run_allowed_commands(adb, commands)
    logs: list[dict] = []
    for raw in commands:
        for cmd in _split_shell_chain(str(raw)):
            timeout = _shell_timeout(cmd)
            print(
                f"[echo-eval]   exec cache cmd ({timeout:g}s): {cmd}",
                flush=True,
            )
            adb.shell(cmd, check=True, timeout=timeout)
            logs.append({"cmd": cmd, "ok": True})
    if not logs:
        raise AdbError("缓存中没有可执行命令")
    return logs
