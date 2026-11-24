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
    - kind=volume/mute: try fallbacks until one succeeds (same as calibrate)
    - other kinds (launch/brightness/...): run all commands in order

    Prefer listing steps as separate array items. A single string with
    ``;`` / ``&&`` is split and executed sequentially for adb compatibility.
    """
    kind_l = (kind or "").strip().lower()
    if kind_l == "vl":
        return run_allowed_commands(adb, commands)

    flat: list[str] = []
    for raw in commands:
        flat.extend(_split_shell_chain(str(raw)))
    if not flat:
        raise AdbError("缓存中没有可执行命令")

    # volume/mute cache stores alternative backends; one success is enough.
    allow_any = kind_l in ("volume", "mute")
    logs: list[dict] = []
    last_err: AdbError | None = None
    any_ok = False
    for cmd in flat:
        timeout = _shell_timeout(cmd)
        print(
            f"[echo-eval]   exec cache cmd ({timeout:g}s"
            f"{', allow_any' if allow_any else ''}): {cmd}",
            flush=True,
        )
        try:
            adb.shell(cmd, check=True, timeout=timeout)
            logs.append({"cmd": cmd, "ok": True})
            any_ok = True
            if allow_any:
                return logs
        except AdbError as e:
            last_err = e
            logs.append({"cmd": cmd, "ok": False, "error": str(e)})
            print(f"[echo-eval][warn] cache cmd failed: {e}", flush=True)
            if not allow_any:
                raise
            continue
    if allow_any and not any_ok and last_err:
        raise AdbError(f"音量/静音兜底命令均失败: {last_err}") from last_err
    if not any_ok and last_err:
        raise last_err
    return logs
