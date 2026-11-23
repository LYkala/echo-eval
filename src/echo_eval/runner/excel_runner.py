"""Phase 4: Excel/CSV → TTS → record → judge → report."""
from __future__ import annotations

import json
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

from echo_eval.capture.frames import extract_frames
from echo_eval.cases.excel_cases import EvalCase, load_cases
from echo_eval.config import AppConfig, load_config
from echo_eval.device.adb import AdbClient, AdbError
from echo_eval.judge.service import JudgeError, judge_images
from echo_eval.runner.progress import finish_batch, init_progress, update_case
from echo_eval.stimulus.audio import play_audio_file
from echo_eval.stimulus.tts import TtsError, synthesize, wav_duration_sec

JudgeBackend = Literal["auto", "vl", "mock"]


@dataclass
class RunResult:
    case_id: str
    name: str
    passed: bool | None
    run_dir: str
    error: str = ""
    evaluation_path: str = ""
    report_path: str = ""


@dataclass
class BatchSummary:
    excel: str
    results: list[RunResult] = field(default_factory=list)
    started_at: str = ""
    finished_at: str = ""
    summary_path: str = ""

    @property
    def passed_count(self) -> int:
        return sum(1 for r in self.results if r.passed is True)

    @property
    def failed_count(self) -> int:
        return sum(1 for r in self.results if r.passed is False)

    @property
    def error_count(self) -> int:
        return sum(1 for r in self.results if r.passed is None or bool(r.error))


def _safe_name(s: str) -> str:
    out = []
    for ch in s:
        if ch.isalnum() or ch in ("-", "_", "."):
            out.append(ch)
        else:
            out.append("_")
    return "".join(out)[:80] or "case"


def _run_id() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")


def _device_posix(path: str) -> str:
    if not path.startswith("/"):
        path = "/" + path
    if not path.startswith("//"):
        path = "/" + path
    return path


def _write_report(path: Path, case: EvalCase, payload: dict[str, Any]) -> None:
    lines = [
        f"# 用例报告：{case.case_id} — {case.name}",
        "",
        f"- passed: `{payload.get('passed')}`",
        f"- backend: `{payload.get('judge_backend')}`",
        f"- run_dir: `{payload.get('run_dir')}`",
        "",
        "## 测试步骤",
        "",
    ]
    for i, step in enumerate(case.steps, 1):
        lines.append(f"{i}. {step.text}（wait={step.wait_sec}s）")
    lines.extend(["", "## 预期结果", "", case.expected or "(空)", "", "## 评判摘要", ""])
    parsed = (payload.get("evaluation") or {}).get("result_parsed")
    if isinstance(parsed, list):
        for item in parsed:
            if not isinstance(item, dict):
                continue
            label = item.get("final_result") or item.get("expectation") or "?"
            lines.append(f"- **{label}**: {item.get('result')} — {item.get('reason') or item.get('think') or ''}")
    else:
        lines.append("(无解析结果)")
    if payload.get("error"):
        lines.extend(["", "## 错误", "", str(payload["error"])])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def run_one_case(
    case: EvalCase,
    cfg: AppConfig,
    *,
    judge_backend: JudgeBackend = "auto",
    extract_frames_flag: bool = True,
    case_index: int = 1,
    case_total: int = 1,
) -> RunResult:
    def _prog(status: str, phase: str, phase_label: str) -> None:
        update_case(
            index=case_index,
            case_id=case.case_id,
            name=case.name,
            status=status,
            phase=phase,
            phase_label=phase_label,
        )

    _prog("running", "prepare", f"准备中（{case_index}/{case_total}）")
    serial = cfg.adb.resolved_serial
    adb = AdbClient(serial=serial, adb_path=cfg.adb.adb_path)
    state = adb.device_state()
    if not state or not state.ok:
        adb.connect(timeout_sec=cfg.adb.connect_timeout_sec)
    if not adb.has_screenrecord():
        raise AdbError("设备无 screenrecord")

    run_dir = (
        cfg.capture.local_root()
        / f"{_run_id()}_{_safe_name(case.case_id)}"
    )
    run_dir.mkdir(parents=True, exist_ok=True)
    steps_dir = run_dir / "steps"
    steps_dir.mkdir(parents=True, exist_ok=True)

    case_payload = {
        "case_id": case.case_id,
        "name": case.name,
        "description": case.description,
        "case_type": case.case_type,
        "steps": [{"text": s.text, "wait_sec": s.wait_sec} for s in case.steps],
        "expected": case.expected,
        "exceptions": case.exceptions(),
    }
    (run_dir / "case.json").write_text(
        json.dumps(case_payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    # 1) TTS all steps first (know durations)
    _prog("running", "tts", f"合成语音（{case_index}/{case_total}）")
    tts_meta = []
    total_play = 0.0
    for i, step in enumerate(case.steps, 1):
        wav_path = steps_dir / f"step_{i:02d}.wav"
        try:
            tts = synthesize(
                step.text,
                wav_path,
                backend=cfg.tts.backend,
                voice=cfg.tts.voice,
                rate=cfg.tts.rate,
                lookup_dir=Path(cfg.tts.lookup_dir) if cfg.tts.lookup_dir else None,
                case_id=case.case_id,
                step_index=i,
            )
        except TtsError:
            raise
        dur = tts.duration_sec or wav_duration_sec(Path(tts.path))
        total_play += dur + float(step.wait_sec)
        tts_meta.append(
            {
                "index": i,
                "text": step.text,
                "wait_sec": step.wait_sec,
                "wav": tts.path,
                "backend": tts.backend,
                "duration_sec": dur,
            }
        )

    head = float(cfg.run.head_delay_sec)
    tail = float(cfg.run.tail_sec)
    buffer_sec = 2.0
    duration_sec = max(5, int(math_ceil(head + total_play + tail + buffer_sec)))

    remote_dir = cfg.capture.remote_dir.rstrip("/")
    remote_file = f"{remote_dir}/{cfg.capture.remote_filename.lstrip('/')}"
    remote_on_device = remote_file if remote_file.startswith("/") else f"/{remote_file}"
    local_video = run_dir / "capture.mp4"
    frames_dir = run_dir / "frames"

    adb.ensure_remote_dir(remote_dir)
    adb.rm_remote(remote_on_device)

    timeline: dict[str, Any] = {
        "record_started_at": None,
        "head_delay_sec": head,
        "tail_sec": tail,
        "planned_duration_sec": duration_sec,
        "steps": [],
    }

    started = datetime.now(timezone.utc).isoformat()
    timeline["record_started_at"] = started
    t0 = time.time()
    _prog("running", "record", f"录屏+播报（{case_index}/{case_total}）")
    proc = adb.screenrecord_start(remote_on_device, duration_sec)
    if head > 0:
        time.sleep(head)

    try:
        for step_i, item in enumerate(tts_meta, 1):
            _prog(
                "running",
                "play",
                f"播放第 {step_i}/{len(tts_meta)} 句（用例 {case_index}/{case_total}）",
            )
            play_started = datetime.now(timezone.utc).isoformat()
            play_offset = round(time.time() - t0, 3)
            play_audio_file(item["wav"], ffmpeg_path=cfg.capture.ffmpeg_path)
            wait_sec = float(item["wait_sec"])
            if wait_sec > 0:
                time.sleep(wait_sec)
            timeline["steps"].append(
                {
                    **item,
                    "play_started_at": play_started,
                    "play_offset_sec": play_offset,
                }
            )
        if tail > 0:
            # remaining time until screenrecord ends; sleep up to tail
            elapsed = time.time() - t0
            remain = duration_sec - elapsed
            time.sleep(max(0.0, min(tail, remain)))
        adb.screenrecord_wait(proc, duration_sec=duration_sec)
    except Exception:
        try:
            proc.kill()
        except Exception:
            pass
        raise

    _prog("running", "pull", f"拉取录屏（{case_index}/{case_total}）")
    adb.pull(_device_posix(remote_on_device), local_video)
    adb.rm_remote(remote_on_device)
    # Stopped + pulled: leave result UI before local judge work.
    _prog("running", "home", f"回桌面（{case_index}/{case_total}）")
    home_after_pull = _return_to_system_home(
        cfg, adb=adb, case_id=case.case_id, phase="after_pull", wait=False
    )
    timeline["return_home_after_pull"] = home_after_pull

    frame_paths: list[str] = []
    if extract_frames_flag and cfg.capture.fps > 0:
        _prog("running", "frames", f"抽帧（{case_index}/{case_total}）")
        frames = extract_frames(
            local_video,
            frames_dir,
            fps=cfg.capture.fps,
            ffmpeg_path=cfg.capture.ffmpeg_path,
        )
        frame_paths = [str(p) for p in frames]

    (run_dir / "timeline.json").write_text(
        json.dumps(timeline, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    expects = case.exceptions()
    if not expects:
        raise JudgeError(f"用例 {case.case_id} 无预期结果，无法评判")

    _prog("running", "judge", f"评判中（{case_index}/{case_total}）")
    evaluation = judge_images(
        exceptions=expects,
        run_dir=run_dir,
        backend=judge_backend,
        write=True,
    )
    passed = evaluation.get("passed")
    # After judge: home again + wait so the next case starts from launcher
    # (assistant UI may reappear during judge).
    _prog("running", "home", f"回桌面等待下一条（{case_index}/{case_total}）")
    home_after_case = _return_to_system_home(
        cfg, adb=adb, case_id=case.case_id, phase="after_case", wait=True
    )
    report_path = run_dir / "report.md"
    payload = {
        "passed": passed,
        "judge_backend": evaluation.get("backend"),
        "run_dir": str(run_dir),
        "evaluation": evaluation,
        "tts": tts_meta,
        "frame_count": len(frame_paths),
        "return_home_after_pull": home_after_pull,
        "return_home_after_case": home_after_case,
    }
    _write_report(report_path, case, payload)
    (run_dir / "run_meta.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (run_dir / "timeline.json").write_text(
        json.dumps({**timeline, "return_home_after_case": home_after_case}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    final_status = (
        "passed"
        if passed is True
        else ("failed" if passed is False else "error")
    )
    _prog(final_status, "done", f"本条完成（{case_index}/{case_total}）")
    return RunResult(
        case_id=case.case_id,
        name=case.name,
        passed=passed if isinstance(passed, bool) else None,
        run_dir=str(run_dir),
        evaluation_path=str(evaluation.get("evaluation_path") or ""),
        report_path=str(report_path),
    )


def math_ceil(x: float) -> int:
    import math

    return int(math.ceil(x))


def _return_to_system_home(
    cfg: AppConfig,
    *,
    adb: AdbClient | None = None,
    case_id: str = "",
    phase: str = "home",
    wait: bool = True,
) -> dict[str, Any]:
    """Press Home once. Failures only warn. Returns a small status dict for logs."""
    info: dict[str, Any] = {
        "enabled": bool(cfg.run.return_home_after_case),
        "phase": phase,
        "case_id": case_id,
        "ok": False,
        "error": "",
        "wait_sec": 0.0,
    }
    if not cfg.run.return_home_after_case:
        print(
            f"[echo-eval] return home skipped (disabled) case={case_id or '-'} phase={phase}",
            flush=True,
        )
        return info
    wait_sec = max(0.0, float(cfg.run.return_home_wait_sec)) if wait else 0.0
    info["wait_sec"] = wait_sec
    serial = cfg.adb.resolved_serial
    try:
        client = adb or AdbClient(serial=serial, adb_path=cfg.adb.adb_path)
        print(
            f"[echo-eval] return home… case={case_id or '-'} phase={phase} "
            f"serial={serial} adb={client.adb_bin}",
            flush=True,
        )
        client.press_home()
        info["ok"] = True
        print(
            f"[echo-eval] return home OK case={case_id or '-'} phase={phase}; "
            f"wait {wait_sec:g}s",
            flush=True,
        )
    except Exception as e:
        info["error"] = str(e)
        print(
            f"[echo-eval][warn] return home failed case={case_id or '-'} "
            f"phase={phase}: {e}",
            flush=True,
        )
    if wait_sec > 0:
        time.sleep(wait_sec)
    return info


def run_excel(
    excel_path: Path | str,
    *,
    config_path: Path | str | None = None,
    case_id: str | None = None,
    judge_backend: JudgeBackend = "auto",
    fail_fast: bool = False,
    extract_frames_flag: bool = True,
) -> BatchSummary:
    cfg = load_config(config_path) if config_path else load_config()
    print(
        f"[echo-eval] run_excel return_home_after_case={cfg.run.return_home_after_case} "
        f"return_home_wait_sec={cfg.run.return_home_wait_sec} "
        f"serial={cfg.adb.resolved_serial}",
        flush=True,
    )
    cases = load_cases(
        excel_path,
        case_id=case_id,
        default_wait_sec=float(cfg.run.step_wait_sec),
    )
    if not cases:
        raise ValueError(f"未解析到任何用例: {excel_path}")

    init_progress([(c.case_id, c.name) for c in cases])
    summary = BatchSummary(
        excel=str(Path(excel_path).resolve()),
        started_at=datetime.now(timezone.utc).isoformat(),
    )
    total = len(cases)
    for idx, case in enumerate(cases, 1):
        try:
            result = run_one_case(
                case,
                cfg,
                judge_backend=judge_backend,
                extract_frames_flag=extract_frames_flag,
                case_index=idx,
                case_total=total,
            )
        except Exception as e:
            result = RunResult(
                case_id=case.case_id,
                name=case.name,
                passed=None,
                run_dir="",
                error=str(e),
            )
            update_case(
                index=idx,
                case_id=case.case_id,
                name=case.name,
                status="error",
                phase="error",
                phase_label=f"错误（{idx}/{total}）",
            )
            # Best-effort home if the case failed before post-case reset.
            _return_to_system_home(
                cfg, case_id=case.case_id, phase="after_error", wait=True
            )
        summary.results.append(result)
        if fail_fast and (result.passed is False or result.error):
            break

    finish_batch()
    summary.finished_at = datetime.now(timezone.utc).isoformat()
    out_dir = cfg.capture.local_root() / f"batch_{_run_id()}"
    out_dir.mkdir(parents=True, exist_ok=True)
    summary_path = out_dir / "summary.json"
    summary.summary_path = str(summary_path)
    summary_path.write_text(
        json.dumps(
            {
                "excel": summary.excel,
                "started_at": summary.started_at,
                "finished_at": summary.finished_at,
                "passed": summary.passed_count,
                "failed": summary.failed_count,
                "errors": summary.error_count,
                "results": [asdict(r) for r in summary.results],
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return summary
