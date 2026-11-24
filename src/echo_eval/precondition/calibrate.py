"""Calibrate GUI precondition steps via VL (independent CLI entry)."""
from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Literal

from echo_eval.cases.excel_cases import load_cases
from echo_eval.config import load_config, load_object_packages, load_packages_map
from echo_eval.device.adb import AdbClient, AdbError
from echo_eval.judge.client import JudgeClientError
from echo_eval.judge.model_config import ModelConfigError, find_model_config_path, load_vl_eval_config
from echo_eval.precondition.cache import PreconditionCache, StepCacheEntry
from echo_eval.precondition.commands import (
    run_allowed_commands,
    run_cached_step_commands,
)
from echo_eval.precondition.coverage import build_coverage_report, print_coverage_report
from echo_eval.precondition.deterministic import plan_deterministic_step
from echo_eval.precondition.replay import resolve_opened_package
from echo_eval.precondition.steps import resolve_step_settle_sec, step_cache_key
from echo_eval.precondition.vl_act import (
    mock_propose_commands,
    mock_verify_step,
    vl_propose_commands,
    vl_verify_step,
)

Backend = Literal["auto", "vl", "mock"]


class CalibrateError(RuntimeError):
    pass


@dataclass
class StepCalibrateResult:
    case_id: str
    step_text: str
    key: str
    status: str
    attempts: int = 0
    error: str = ""
    commands: list[str] = field(default_factory=list)


@dataclass
class CalibrateSummary:
    excel: str
    backend: str
    results: list[StepCalibrateResult] = field(default_factory=list)
    passed_steps: int = 0
    failed_steps: int = 0
    skipped_steps: int = 0

    def recompute(self) -> None:
        self.passed_steps = sum(1 for r in self.results if r.status == "passed")
        self.failed_steps = sum(1 for r in self.results if r.status == "failed")
        self.skipped_steps = sum(1 for r in self.results if r.status == "skipped")


def _resolve_backend(requested: Backend) -> str:
    if requested == "vl":
        return "vl"
    if requested == "mock":
        return "mock"
    return "vl" if find_model_config_path() else "mock"


def _shot_dir(cache: PreconditionCache, key: str) -> Path:
    safe = "".join(ch if ch.isalnum() or ch in "-_" else "_" for ch in key)[:80]
    d = cache.root / "shots" / safe
    d.mkdir(parents=True, exist_ok=True)
    return d


def _clip(text: str, limit: int = 1200) -> str:
    s = (text or "").replace("\r\n", "\n").strip()
    if len(s) <= limit:
        return s
    return s[: limit - 20] + "\n…(truncated)…"


def _write_attempt_log(path: Path, payload: dict[str, Any]) -> None:
    try:
        path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    except OSError as e:
        print(f"[echo-eval][warn] write attempt log failed: {e}", flush=True)


def _exec_deterministic(
    adb: AdbClient,
    commands: list[str],
    *,
    allow_any: bool,
) -> None:
    last_err: Exception | None = None
    any_ok = False
    for cmd in commands:
        try:
            adb.shell(cmd, check=True, timeout=30)
            any_ok = True
            if allow_any:
                return
        except AdbError as e:
            last_err = e
            if not allow_any:
                continue
            continue
    if allow_any and not any_ok and last_err:
        raise last_err
    if not allow_any and not any_ok and last_err:
        raise last_err


def calibrate_gui_step(
    *,
    adb: AdbClient,
    cache: PreconditionCache,
    case_id: str,
    step_text: str,
    prefix: list[str],
    key: str,
    max_retries: int,
    settle_sec: float,
    backend: str,
    model_cfg: dict[str, Any] | None,
) -> StepCalibrateResult:
    propose = mock_propose_commands if backend == "mock" else vl_propose_commands
    verify = mock_verify_step if backend == "mock" else vl_verify_step
    shots = _shot_dir(cache, key)
    wm = ""
    try:
        wm = adb.wm_size()
    except AdbError:
        wm = ""

    feedback = ""
    prior_commands: list[str] = []
    prior_screenshot: Path | None = None
    last_cmds: list[str] = []
    last_err = ""
    for attempt in range(1, max_retries + 1):
        before = shots / f"try{attempt}_before.png"
        after = shots / f"try{attempt}_after.png"
        print(
            f"[echo-eval] calibrate {case_id} step={step_text!r} "
            f"attempt={attempt}/{max_retries} key={key} shots={shots}",
            flush=True,
        )
        if feedback or prior_commands:
            print(
                f"[echo-eval]   prior_cmds={prior_commands} "
                f"prior_shot={prior_screenshot} "
                f"prior_feedback: {_clip(feedback, 400)}",
                flush=True,
            )
        try:
            adb.screencap_png(before)
            print(f"[echo-eval]   screencap before: {before}", flush=True)
            act = propose(
                screenshot=before,
                step_text=step_text,
                prefix=prefix,
                wm_size=wm,
                feedback=feedback,
                prior_commands=prior_commands,
                prior_screenshot=prior_screenshot,
                model_cfg=model_cfg,
            )
            cmds = list(act.get("adb_commands") or [])
            rejected = list(act.get("rejected") or [])
            act_raw = str(act.get("raw") or "")
            act_reason = str(act.get("reason") or "")
            print(
                f"[echo-eval]   VL-Act reason={act_reason!r} "
                f"cmds={cmds} rejected={rejected}",
                flush=True,
            )
            print(f"[echo-eval]   VL-Act raw:\n{_clip(act_raw)}", flush=True)
            _write_attempt_log(
                shots / f"try{attempt}_act.json",
                {
                    "case_id": case_id,
                    "step_text": step_text,
                    "prefix": prefix,
                    "wm_size": wm,
                    "feedback_in": feedback,
                    "prior_commands_in": list(prior_commands),
                    "prior_screenshot_in": str(prior_screenshot) if prior_screenshot else "",
                    "adb_commands": cmds,
                    "rejected": rejected,
                    "reason": act_reason,
                    "parsed": act.get("parsed"),
                    "raw": act_raw,
                    "before": str(before),
                },
            )
            if rejected:
                feedback = f"模型给出非法命令已拒绝: {rejected[:3]}"
                last_err = feedback
                prior_commands = [str(c) for c in rejected[:6]]
                prior_screenshot = before if before.is_file() else prior_screenshot
                print(f"[echo-eval][warn] {feedback}", flush=True)
                continue
            if not cmds:
                feedback = "模型未给出任何 input 命令"
                last_err = feedback
                prior_screenshot = before if before.is_file() else prior_screenshot
                print(f"[echo-eval][warn] {feedback}", flush=True)
                continue
            last_cmds = cmds
            print(f"[echo-eval]   exec: {cmds}", flush=True)
            run_allowed_commands(adb, cmds)
            if settle_sec > 0:
                time.sleep(settle_sec)
            adb.screencap_png(after)
            print(f"[echo-eval]   screencap after: {after}", flush=True)
            verdict = verify(
                screenshot_after=after,
                step_text=step_text,
                prefix=prefix,
                screenshot_before=before,
                model_cfg=model_cfg,
            )
            achieved = bool(verdict.get("achieved"))
            v_reason = str(verdict.get("reason") or "")
            v_raw = str(verdict.get("raw") or "")
            print(
                f"[echo-eval]   VL-Verify achieved={achieved} reason={v_reason!r}",
                flush=True,
            )
            print(f"[echo-eval]   VL-Verify raw:\n{_clip(v_raw)}", flush=True)
            _write_attempt_log(
                shots / f"try{attempt}_verify.json",
                {
                    "case_id": case_id,
                    "step_text": step_text,
                    "prefix": prefix,
                    "achieved": achieved,
                    "reason": v_reason,
                    "parsed": verdict.get("parsed"),
                    "raw": v_raw,
                    "before": str(before),
                    "after": str(after),
                    "executed_commands": cmds,
                },
            )
            if achieved:
                entry = StepCacheEntry(
                    key=key,
                    step_text=step_text,
                    prefix=list(prefix),
                    status="passed",
                    adb_commands=cmds,
                    kind="vl",
                    judge_notes=str(v_reason or act_reason or ""),
                    screenshots=[str(before), str(after)],
                    device_wm_size=wm,
                    case_ids=[case_id],
                )
                cache.upsert(entry)
                cache.save()
                print(
                    f"[echo-eval]   PASS step={step_text!r} cmds={cmds}",
                    flush=True,
                )
                return StepCalibrateResult(
                    case_id=case_id,
                    step_text=step_text,
                    key=key,
                    status="passed",
                    attempts=attempt,
                    commands=cmds,
                )
            feedback = v_reason or "VL 判定未达成"
            last_err = feedback
            # Carry only the latest failed attempt: cmds + before shot (not full history).
            prior_commands = list(cmds)
            prior_screenshot = before
            print(f"[echo-eval][warn] verify not achieved: {feedback}", flush=True)
        except (
            AdbError,
            ModelConfigError,
            JudgeClientError,
            ValueError,
            OSError,
        ) as e:
            last_err = str(e)
            feedback = last_err
            if before.is_file():
                prior_screenshot = before
            if last_cmds:
                prior_commands = list(last_cmds)
            print(f"[echo-eval][warn] calibrate attempt failed: {e}", flush=True)
            _write_attempt_log(
                shots / f"try{attempt}_error.json",
                {
                    "case_id": case_id,
                    "step_text": step_text,
                    "prefix": prefix,
                    "error": last_err,
                    "before": str(before),
                    "after": str(after),
                },
            )

    entry = StepCacheEntry(
        key=key,
        step_text=step_text,
        prefix=list(prefix),
        status="failed",
        adb_commands=last_cmds,
        kind="vl",
        judge_notes=last_err,
        device_wm_size=wm,
        case_ids=[case_id],
    )
    cache.upsert(entry)
    cache.save()
    return StepCalibrateResult(
        case_id=case_id,
        step_text=step_text,
        key=key,
        status="failed",
        attempts=max_retries,
        error=last_err or "校准失败",
        commands=last_cmds,
    )


def calibrate_dataset(
    excel_path: Path | str,
    *,
    config_path: Path | str | None = None,
    case_id: str | None = None,
    backend: Backend = "auto",
    retry_failed: bool = False,
) -> CalibrateSummary:
    cfg = load_config(config_path) if config_path else load_config()
    cases = load_cases(
        excel_path,
        case_id=case_id,
        default_wait_sec=float(cfg.run.step_wait_sec),
    )
    if not cases:
        raise CalibrateError(f"未解析到任何用例: {excel_path}")

    resolved = _resolve_backend(backend)
    summary = CalibrateSummary(excel=str(Path(excel_path).resolve()), backend=resolved)
    pre = cfg.precondition
    cache = PreconditionCache(pre.resolved_cache_dir())
    packages = load_packages_map(pre.resolved_packages_path())
    object_packages = load_object_packages(pre.resolved_packages_path())

    print(
        f"[echo-eval] precondition-calibrate backend={resolved} "
        f"(requested={backend}) retry_failed={retry_failed} "
        f"reuse_passed={pre.reuse_passed} max_retries={pre.max_retries}",
        flush=True,
    )
    report = build_coverage_report(cases, cfg=pre, cache=cache, packages=packages)
    print_coverage_report(report)

    model_cfg = None
    if resolved == "vl":
        try:
            model_cfg = load_vl_eval_config()
        except ModelConfigError as e:
            raise CalibrateError(str(e)) from e

    serial = cfg.adb.resolved_serial
    adb = AdbClient(serial=serial, adb_path=cfg.adb.adb_path)
    state = adb.device_state()
    if not state or not state.ok:
        adb.connect(timeout_sec=cfg.adb.connect_timeout_sec)

    previous_package = ""
    home_settle = max(0.0, float(pre.step_settle_sec))
    max_retries = max(1, int(pre.max_retries))

    for case in cases:
        print(f"[echo-eval] === calibrate case {case.case_id} {case.name} ===", flush=True)
        if previous_package:
            try:
                adb.force_stop(previous_package)
            except AdbError as e:
                print(f"[echo-eval][warn] force-stop {previous_package}: {e}", flush=True)
        try:
            adb.press_home()
            if home_settle:
                time.sleep(home_settle)
        except AdbError as e:
            print(f"[echo-eval][warn] home: {e}", flush=True)

        prefix: list[str] = []
        abort_case = False
        steps = list(case.precondition_steps)
        if not steps:
            print(
                f"[echo-eval]   case {case.case_id}: 无预置步骤，跳过",
                flush=True,
            )
        else:
            print(
                f"[echo-eval]   case {case.case_id}: "
                f"{len(steps)} 个预置步骤 → {' → '.join(steps)}",
                flush=True,
            )
        for step_i, text in enumerate(steps, 1):
            if abort_case:
                break
            key = step_cache_key(prefix, text)
            entry = cache.get(key)
            status = entry.status if entry else "untested"
            plan = plan_deterministic_step(text, packages=packages)
            settle = resolve_step_settle_sec(
                text,
                default_sec=pre.step_settle_sec,
                overrides=pre.step_settle_overrides,
            )
            print(
                f"[echo-eval]   step {step_i}/{len(steps)} text={text!r} "
                f"status={status} kind={'deterministic:' + plan.kind if plan else 'gui'}",
                flush=True,
            )

            # Prefer passed cache (incl. manually edited launch commands) over
            # re-deriving via launch_package / deterministic plans.
            if status == "passed" and pre.reuse_passed and entry and entry.adb_commands:
                try:
                    print(
                        f"[echo-eval]   reuse cache kind={entry.kind} "
                        f"cmds={list(entry.adb_commands)}",
                        flush=True,
                    )
                    run_cached_step_commands(
                        adb, list(entry.adb_commands), kind=str(entry.kind or "")
                    )
                    summary.results.append(
                        StepCalibrateResult(
                            case_id=case.case_id,
                            step_text=text,
                            key=key,
                            status="passed",
                            attempts=0,
                            commands=list(entry.adb_commands),
                        )
                    )
                except AdbError as e:
                    entry.status = "stale"
                    cache.upsert(entry)
                    cache.save()
                    print(
                        f"[echo-eval][warn] cache replay failed, mark stale "
                        f"and fall through: {e}",
                        flush=True,
                    )
                    status = "stale"
                else:
                    if settle:
                        print(
                            f"[echo-eval]   settle {settle:g}s after step={text!r}",
                            flush=True,
                        )
                        time.sleep(settle)
                    prefix.append(text)
                    continue

            # Deterministic system commands (only when no reusable cache)
            if plan is not None:
                try:
                    used_cmds = list(plan.commands)
                    if plan.kind == "launch" and plan.package:
                        used = adb.launch_package(
                            plan.package,
                            prefer_settings_intent=bool(plan.prefer_settings_intent),
                        )
                        used_cmds = [used]
                    else:
                        _exec_deterministic(
                            adb,
                            plan.commands,
                            allow_any=bool(plan.any_success)
                            or plan.kind in ("volume", "mute"),
                        )
                    cache.upsert(
                        StepCacheEntry(
                            key=key,
                            step_text=text,
                            prefix=list(prefix),
                            status="passed",
                            adb_commands=used_cmds,
                            kind=plan.kind,
                            package=plan.package,
                            judge_notes=plan.notes,
                            case_ids=[case.case_id],
                        )
                    )
                    cache.save()
                    summary.results.append(
                        StepCalibrateResult(
                            case_id=case.case_id,
                            step_text=text,
                            key=key,
                            status="passed",
                            attempts=0,
                            commands=used_cmds,
                        )
                    )
                except AdbError as e:
                    summary.results.append(
                        StepCalibrateResult(
                            case_id=case.case_id,
                            step_text=text,
                            key=key,
                            status="failed",
                            error=str(e),
                        )
                    )
                    abort_case = True
                    break
                if settle:
                    print(
                        f"[echo-eval]   settle {settle:g}s after step={text!r}",
                        flush=True,
                    )
                    time.sleep(settle)
                prefix.append(text)
                continue

            if status == "failed" and not retry_failed:
                msg = (
                    f"状态=failed，未加 --retry-failed；本条后续预置步骤中止。"
                    f"需要重试请加: --retry-failed"
                )
                print(
                    f"[echo-eval][warn] skip step={text!r} key={key}: {msg}",
                    flush=True,
                )
                summary.results.append(
                    StepCalibrateResult(
                        case_id=case.case_id,
                        step_text=text,
                        key=key,
                        status="skipped",
                        error=msg,
                    )
                )
                abort_case = True
                break

            # GUI VL calibrate: settle runs once inside calibrate_gui_step
            # (exec → wait → after shot → verify). Do not sleep again here.
            result = calibrate_gui_step(
                adb=adb,
                cache=cache,
                case_id=case.case_id,
                step_text=text,
                prefix=list(prefix),
                key=key,
                max_retries=max_retries,
                settle_sec=settle,
                backend=resolved,
                model_cfg=model_cfg,
            )
            summary.results.append(result)
            if result.status != "passed":
                print(
                    f"[echo-eval][warn] VL calibrate failed for step={text!r}; "
                    f"abort remaining steps of case={case.case_id}",
                    flush=True,
                )
                abort_case = True
                break
            prefix.append(text)

        previous_package = resolve_opened_package(
            case, packages=packages, object_packages=object_packages
        ) or previous_package

    summary.recompute()
    print(
        f"[echo-eval] calibrate done: passed={summary.passed_steps} "
        f"failed={summary.failed_steps} skipped={summary.skipped_steps}",
        flush=True,
    )
    return summary


def calibrate_excel(
    excel_path: Path | str,
    **kwargs: Any,
) -> CalibrateSummary:
    return calibrate_dataset(excel_path, **kwargs)
