"""Per-case: force-stop previous → Home → replay precondition ADB."""
from __future__ import annotations

import time
from typing import Any

from echo_eval.cases.excel_cases import EvalCase
from echo_eval.config import AppConfig, load_object_packages, load_packages_map
from echo_eval.device.adb import AdbClient, AdbError
from echo_eval.precondition.cache import PreconditionCache, StepCacheEntry
from echo_eval.precondition.commands import run_cached_step_commands
from echo_eval.precondition.deterministic import plan_deterministic_step, resolve_package
from echo_eval.precondition.steps import resolve_step_settle_sec, step_cache_key


class PreconditionSkip(RuntimeError):
    """Case should be skipped due to precondition not ready."""


def resolve_opened_package(
    case: EvalCase,
    *,
    packages: dict[str, str],
    object_packages: dict[str, str] | None = None,
) -> str:
    """Infer package opened by this case (for next-case force-stop)."""
    object_packages = object_packages or {}
    for step in case.precondition_steps:
        pkg = resolve_package(step, packages)
        if pkg:
            return pkg
    obj = (case.object_name or "").strip()
    if obj and object_packages.get(obj):
        return object_packages[obj]
    return ""


def _run_shell_commands(
    adb: AdbClient,
    commands: list[str],
    *,
    allow_any_success: bool = False,
) -> list[dict[str, Any]]:
    logs: list[dict[str, Any]] = []
    last_err: Exception | None = None
    any_ok = False
    for cmd in commands:
        try:
            adb.shell(cmd, check=True, timeout=30)
            logs.append({"cmd": cmd, "ok": True})
            any_ok = True
            if allow_any_success:
                break
        except AdbError as e:
            logs.append({"cmd": cmd, "ok": False, "error": str(e)})
            last_err = e
            if not allow_any_success:
                # try next fallback command
                continue
    if allow_any_success and not any_ok and last_err:
        raise AdbError(f"预置命令均失败: {last_err}")
    if not allow_any_success and not any_ok and last_err:
        raise AdbError(str(last_err))
    return logs


def prepare_case_precondition(
    case: EvalCase,
    cfg: AppConfig,
    *,
    adb: AdbClient,
    previous_package: str = "",
    cache: PreconditionCache | None = None,
) -> dict[str, Any]:
    """
    Before utterance/record:
      1) force-stop previous case package
      2) Home
      3) replay this case's precondition steps (cache and/or deterministic)
    """
    pre = cfg.precondition
    info: dict[str, Any] = {
        "enabled": bool(pre.enabled),
        "case_id": case.case_id,
        "previous_package": previous_package,
        "force_stop": {},
        "home": False,
        "steps": [],
        "opened_package": "",
    }
    if not pre.enabled:
        print(
            f"[echo-eval] precondition disabled; skip setup case={case.case_id}",
            flush=True,
        )
        return info

    packages = load_packages_map(pre.resolved_packages_path())
    object_packages = load_object_packages(pre.resolved_packages_path())
    cache = cache or PreconditionCache(pre.resolved_cache_dir())

    # 1) force-stop previous
    if previous_package:
        try:
            print(
                f"[echo-eval] force-stop previous package={previous_package} "
                f"before case={case.case_id}",
                flush=True,
            )
            adb.force_stop(previous_package)
            info["force_stop"] = {"package": previous_package, "ok": True}
        except AdbError as e:
            info["force_stop"] = {
                "package": previous_package,
                "ok": False,
                "error": str(e),
            }
            print(
                f"[echo-eval][warn] force-stop failed package={previous_package}: {e}",
                flush=True,
            )

    # 2) Home
    try:
        adb.press_home()
        info["home"] = True
        settle = max(0.0, float(pre.step_settle_sec))
        if settle:
            time.sleep(settle)
    except AdbError as e:
        print(f"[echo-eval][warn] home before precondition failed: {e}", flush=True)

    # 3) replay steps
    prefix: list[str] = []
    opened = ""
    for i, text in enumerate(case.precondition_steps, 1):
        key = step_cache_key(prefix, text)
        entry = cache.get(key)
        plan = plan_deterministic_step(text, packages=packages)
        step_log: dict[str, Any] = {
            "index": i,
            "text": text,
            "key": key,
            "status": entry.status if entry else "untested",
            "source": "",
            "commands": [],
            "ok": False,
        }

        commands: list[str] = []
        source = ""
        allow_any = False
        package = ""

        if entry and entry.status == "passed" and entry.adb_commands:
            commands = list(entry.adb_commands)
            source = "cache"
            package = entry.package or ""
            allow_any = entry.kind in ("volume", "mute")
        elif plan is not None:
            commands = list(plan.commands)
            source = f"deterministic:{plan.kind}"
            package = plan.package
            allow_any = bool(plan.any_success) or plan.kind in ("volume", "mute")
            # Persist deterministic success as passed for coverage reuse
            cache.upsert(
                StepCacheEntry(
                    key=key,
                    step_text=text,
                    prefix=list(prefix),
                    status="passed",
                    adb_commands=commands,
                    kind=plan.kind,
                    package=package,
                    judge_notes=plan.notes,
                    case_ids=[case.case_id],
                )
            )
            cache.save()
        elif entry and entry.status == "failed":
            raise PreconditionSkip(
                f"用例 {case.case_id} 跳过：预置步骤{i}「{text}」状态=failed（{key}）"
            )
        else:
            raise PreconditionSkip(
                f"用例 {case.case_id} 跳过：预置步骤{i}「{text}」未通过测试/"
                f"无可用 ADB 缓存（点击类需 VL 校准模块）。key={key}"
            )

        step_log["source"] = source
        step_log["commands"] = commands
        print(
            f"[echo-eval] precondition replay case={case.case_id} "
            f"step={i}/{len(case.precondition_steps)} source={source} text={text}",
            flush=True,
        )
        try:
            if source == "cache" and entry is not None:
                # Honor cached commands as-is (manual launch edits included).
                # Do not call launch_package or upsert — that would overwrite.
                logs = run_cached_step_commands(
                    adb, commands, kind=str(entry.kind or "")
                )
                step_log["commands"] = list(commands)
            elif plan is not None and plan.kind == "launch" and plan.package:
                used = adb.launch_package(
                    plan.package,
                    prefer_settings_intent=bool(plan.prefer_settings_intent),
                )
                logs = [{"cmd": used, "ok": True}]
                step_log["commands"] = [used]
                cache.upsert(
                    StepCacheEntry(
                        key=key,
                        step_text=text,
                        prefix=list(prefix),
                        status="passed",
                        adb_commands=[used],
                        kind="launch",
                        package=plan.package,
                        judge_notes=plan.notes,
                        case_ids=[case.case_id],
                    )
                )
                cache.save()
            else:
                logs = _run_shell_commands(adb, commands, allow_any_success=allow_any)
            step_log["exec"] = logs
            step_log["ok"] = True
        except AdbError as e:
            step_log["error"] = str(e)
            raise PreconditionSkip(
                f"用例 {case.case_id} 跳过：预置步骤{i}「{text}」执行失败: {e}"
            ) from e

        if package:
            opened = package
        settle = resolve_step_settle_sec(
            text,
            default_sec=pre.step_settle_sec,
            overrides=pre.step_settle_overrides,
        )
        step_log["settle_sec"] = settle
        if settle:
            print(
                f"[echo-eval]   settle {settle:g}s after step={text!r}",
                flush=True,
            )
            time.sleep(settle)

        # Optional VL verify after replay — deferred to module ②
        if pre.verify_after_replay and source == "cache":
            print(
                f"[echo-eval] verify_after_replay=true but VL verify not implemented yet; "
                f"skip verify for step={text}",
                flush=True,
            )

        info["steps"].append(step_log)
        prefix.append(text)

    if not opened:
        opened = resolve_opened_package(
            case, packages=packages, object_packages=object_packages
        )
    info["opened_package"] = opened
    return info
