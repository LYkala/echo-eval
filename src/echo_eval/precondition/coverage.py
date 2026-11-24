"""Batch-start precondition coverage check (report only + readiness)."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterable

from echo_eval.cases.excel_cases import EvalCase
from echo_eval.config import PreconditionConfig, load_packages_map
from echo_eval.precondition.cache import PreconditionCache, StepStatus
from echo_eval.precondition.deterministic import plan_deterministic_step
from echo_eval.precondition.steps import step_cache_key


@dataclass
class StepCoverage:
    case_id: str
    index: int
    text: str
    key: str
    status: StepStatus
    deterministic: bool
    ready: bool
    reason: str = ""


@dataclass
class CaseCoverage:
    case_id: str
    name: str
    steps: list[StepCoverage] = field(default_factory=list)
    skip: bool = False
    skip_reason: str = ""

    @property
    def ready(self) -> bool:
        return not self.skip and all(s.ready for s in self.steps)


@dataclass
class CoverageReport:
    cases: list[CaseCoverage] = field(default_factory=list)
    counts: dict[str, int] = field(default_factory=dict)

    @property
    def skip_case_ids(self) -> set[str]:
        return {c.case_id for c in self.cases if c.skip}


def _effective_status(
    cached: StepStatus,
    *,
    deterministic: bool,
    has_commands: bool = False,
) -> tuple[StepStatus, bool, str]:
    """Return (status, ready, reason)."""
    if cached == "failed":
        return "failed", False, "预置步骤已失败（校准用尽），跳过该用例"
    if cached == "stale":
        return "stale", False, "预置步骤已过期，请运行 precondition-calibrate 重新校准"
    if cached == "passed":
        # Run path only replays when adb_commands is non-empty; keep coverage aligned.
        if has_commands:
            return "passed", True, "已通过，将回放缓存 ADB"
        if deterministic:
            return "passed", True, "已通过（无命令，将走确定性 ADB）"
        return (
            "passed",
            False,
            "缓存 status=passed 但 adb_commands 为空，无法回放，请重新校准",
        )
    if deterministic:
        # System ADB steps can run without prior VL pass
        return "untested", True, "确定性系统 ADB，可直接执行"
    return (
        "untested",
        False,
        "未通过/无缓存，请运行: python -m echo_eval precondition-calibrate --excel <数据集>",
    )


def build_coverage_report(
    cases: Iterable[EvalCase],
    *,
    cfg: PreconditionConfig,
    cache: PreconditionCache | None = None,
    packages: dict[str, str] | None = None,
) -> CoverageReport:
    packages = packages if packages is not None else load_packages_map(cfg.resolved_packages_path())
    cache = cache or PreconditionCache(cfg.resolved_cache_dir())
    report = CoverageReport()
    counts: dict[str, int] = {
        "untested": 0,
        "passed": 0,
        "failed": 0,
        "stale": 0,
        "deterministic": 0,
        "skip_cases": 0,
    }

    for case in cases:
        cc = CaseCoverage(case_id=case.case_id, name=case.name)
        prefix: list[str] = []
        for i, text in enumerate(case.precondition_steps):
            key = step_cache_key(prefix, text)
            plan = plan_deterministic_step(text, packages=packages)
            deterministic = plan is not None
            entry = cache.get(key)
            cached_status = entry.status if entry else "untested"
            has_commands = bool(entry and entry.adb_commands)
            status, ready, reason = _effective_status(
                cached_status,
                deterministic=deterministic,
                has_commands=has_commands,
            )
            if deterministic:
                counts["deterministic"] += 1
            counts[status] = counts.get(status, 0) + 1
            cc.steps.append(
                StepCoverage(
                    case_id=case.case_id,
                    index=i + 1,
                    text=text,
                    key=key,
                    status=status,
                    deterministic=deterministic,
                    ready=ready,
                    reason=reason,
                )
            )
            if not ready and not cc.skip:
                cc.skip = True
                cc.skip_reason = f"步骤{i + 1}「{text}」: {reason}"
            prefix.append(text)
        if cc.skip:
            counts["skip_cases"] += 1
        report.cases.append(cc)

    report.counts = counts
    return report


def print_coverage_report(
    report: CoverageReport,
    *,
    cache_path: str | None = None,
) -> None:
    print("[echo-eval] ===== 预置条件覆盖检查 =====", flush=True)
    if cache_path:
        print(f"[echo-eval] cache: {cache_path}", flush=True)
    c = report.counts
    print(
        f"[echo-eval] 步骤统计: passed={c.get('passed', 0)} "
        f"untested={c.get('untested', 0)} failed={c.get('failed', 0)} "
        f"stale={c.get('stale', 0)} deterministic_capable={c.get('deterministic', 0)}",
        flush=True,
    )
    print(f"[echo-eval] 将跳过用例数: {c.get('skip_cases', 0)}", flush=True)
    for case in report.cases:
        mark = "SKIP" if case.skip else "OK"
        print(f"[echo-eval] [{mark}] {case.case_id} {case.name}", flush=True)
        for s in case.steps:
            kind = "sys" if s.deterministic else "gui"
            print(
                f"[echo-eval]   - #{s.index} [{s.status}/{kind}] {s.text} "
                f"→ {'ready' if s.ready else 'blocked'}: {s.reason}",
                flush=True,
            )
        if case.skip:
            print(f"[echo-eval]   !! 跳过原因: {case.skip_reason}", flush=True)
    print("[echo-eval] ===== 覆盖检查结束 =====", flush=True)
