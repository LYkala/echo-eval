"""Phase 4: Excel/CSV → TTS → record → (async) judge → report."""
from __future__ import annotations

import json
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

from echo_eval.capture.asr_poll import asr_poll_meta, poll_asr_screenshots
from echo_eval.capture.frames import extract_frames
from echo_eval.cases.excel_cases import EvalCase, load_cases
from echo_eval.config import (
    AppConfig,
    load_config,
    load_object_packages,
    load_packages_map,
)
from echo_eval.device.adb import AdbClient, AdbError
from echo_eval.judge.asr import judge_asr_utterance
from echo_eval.judge.service import JudgeError, judge_images
from echo_eval.precondition import (
    PreconditionSkip,
    build_coverage_report,
    prepare_case_precondition,
    print_coverage_report,
    resolve_opened_package,
)
from echo_eval.precondition.cache import PreconditionCache
from echo_eval.runner.progress import (
    finish_batch,
    init_progress,
    set_batch_phase,
    update_case,
)
from echo_eval.stimulus.audio import play_audio_file
from echo_eval.stimulus.tts import synthesize, wav_duration_sec

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


@dataclass
class _DeviceCapture:
    """Device-side outcome ready for background frame extract + judge."""

    case: EvalCase
    case_index: int
    case_total: int
    run_dir: Path
    local_video: Path
    timeline: dict[str, Any]
    tts_meta: list[dict[str, Any]]
    home_after_pull: dict[str, Any]
    extract_frames_flag: bool
    opened_package: str = ""
    precondition_info: dict[str, Any] = field(default_factory=dict)


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


def math_ceil(x: float) -> int:
    import math

    return int(math.ceil(x))


def _write_report(path: Path, case: EvalCase, payload: dict[str, Any]) -> None:
    lines = [
        f"# 用例报告：{case.case_id} — {case.name}",
        "",
        f"- passed (GUI成功率): `{payload.get('passed')}`",
        f"- backend: `{payload.get('judge_backend')}`",
        f"- run_dir: `{payload.get('run_dir')}`",
        "",
        "## 测试步骤",
        "",
    ]
    for i, step in enumerate(case.steps, 1):
        lines.append(f"{i}. {step.text}（wait={step.wait_sec}s）")
    lines.extend(["", "## 预期结果", "", case.expected or "(空)", "", "## GUI 评判摘要", ""])
    parsed = (payload.get("evaluation") or {}).get("result_parsed")
    if isinstance(parsed, list):
        for item in parsed:
            if not isinstance(item, dict):
                continue
            label = item.get("final_result") or item.get("expectation") or "?"
            lines.append(
                f"- **{label}**: {item.get('result')} — "
                f"{item.get('reason') or item.get('think') or ''}"
            )
    else:
        lines.append("(无解析结果)")

    asr = payload.get("asr") or {}
    if asr:
        lines.extend(
            [
                "",
                "## ASR 准确率（与 GUI 成功率互不影响）",
                "",
                f"- utterances: `{asr.get('total')}`",
                f"- passed: `{asr.get('passed')}`",
                f"- failed: `{asr.get('failed')}`",
                f"- accuracy: `{asr.get('accuracy')}`",
                "",
            ]
        )
        for row in asr.get("utterances") or []:
            if not isinstance(row, dict):
                continue
            ok = row.get("passed")
            mark = "PASS" if ok is True else ("FAIL" if ok is False else "—")
            lines.append(
                f"- [{mark}] step{row.get('index')}: spoken=`{row.get('spoken_text')}` "
                f"recognized=`{row.get('recognized_text')}` — {row.get('reason') or ''}"
            )

    if payload.get("error"):
        lines.extend(["", "## 错误", "", str(payload["error"])])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


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


def _prog_device(
    case: EvalCase,
    *,
    index: int,
    total: int,
    device_status: str,
    device_label: str,
    status: str | None = None,
) -> None:
    update_case(
        index=index,
        case_id=case.case_id,
        name=case.name,
        status=status or ("running" if device_status == "running" else None),
        device_status=device_status,
        device_label=device_label,
        phase="device",
        phase_label=f"设备:{device_label}",
        touch_device_current=True,
    )


def _prog_judge(
    case: EvalCase,
    *,
    index: int,
    judge_status: str,
    judge_label: str,
    status: str | None = None,
) -> None:
    update_case(
        index=index,
        case_id=case.case_id,
        name=case.name,
        status=status,
        judge_status=judge_status,
        judge_label=judge_label,
        phase="judge",
        phase_label=f"评判:{judge_label}",
        touch_judge_current=True,
    )


def capture_one_case(
    case: EvalCase,
    cfg: AppConfig,
    *,
    case_index: int = 1,
    case_total: int = 1,
    extract_frames_flag: bool = True,
    previous_package: str = "",
    precondition_cache: PreconditionCache | None = None,
) -> _DeviceCapture:
    """Device path: precondition → TTS → record → pull → Home. No frames/judge."""
    _prog_device(
        case,
        index=case_index,
        total=case_total,
        device_status="running",
        device_label=f"准备中（{case_index}/{case_total}）",
        status="running",
    )
    serial = cfg.adb.resolved_serial
    adb = AdbClient(serial=serial, adb_path=cfg.adb.adb_path)
    state = adb.device_state()
    if not state or not state.ok:
        adb.connect(timeout_sec=cfg.adb.connect_timeout_sec)
    if not adb.has_screenrecord():
        raise AdbError("设备无 screenrecord")

    run_dir = cfg.capture.local_root() / f"{_run_id()}_{_safe_name(case.case_id)}"
    run_dir.mkdir(parents=True, exist_ok=True)
    steps_dir = run_dir / "steps"
    steps_dir.mkdir(parents=True, exist_ok=True)

    case_payload = {
        "case_id": case.case_id,
        "name": case.name,
        "description": case.description,
        "case_type": case.case_type,
        "object": case.object_name,
        "scene": case.scene,
        "priority": case.priority,
        "precondition_steps": list(case.precondition_steps),
        "steps": [{"text": s.text, "wait_sec": s.wait_sec} for s in case.steps],
        "expected": case.expected,
        "exceptions": case.exceptions(),
    }
    (run_dir / "case.json").write_text(
        json.dumps(case_payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    _prog_device(
        case,
        index=case_index,
        total=case_total,
        device_status="running",
        device_label=f"预置条件（{case_index}/{case_total}）",
        status="running",
    )
    pre_info = prepare_case_precondition(
        case,
        cfg,
        adb=adb,
        previous_package=previous_package,
        cache=precondition_cache,
    )
    (run_dir / "precondition.json").write_text(
        json.dumps(pre_info, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    opened_package = str(pre_info.get("opened_package") or "")

    _prog_device(
        case,
        index=case_index,
        total=case_total,
        device_status="running",
        device_label=f"合成语音（{case_index}/{case_total}）",
        status="running",
    )
    tts_meta: list[dict[str, Any]] = []
    total_play = 0.0
    for i, step in enumerate(case.steps, 1):
        wav_path = steps_dir / f"step_{i:02d}.wav"
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
        dur = tts.duration_sec or wav_duration_sec(Path(tts.path))
        # ASR 采图窗口与 step.wait 取较大值，避免录屏时长不够
        post = float(step.wait_sec)
        if cfg.asr.enabled:
            post = max(post, float(cfg.asr.window_sec))
        total_play += dur + post
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
    _prog_device(
        case,
        index=case_index,
        total=case_total,
        device_status="running",
        device_label=f"录屏+播报（{case_index}/{case_total}）",
        status="running",
    )
    proc = adb.screenrecord_start(remote_on_device, duration_sec)
    if head > 0:
        time.sleep(head)

    try:
        for step_i, item in enumerate(tts_meta, 1):
            _prog_device(
                case,
                index=case_index,
                total=case_total,
                device_status="running",
                device_label=f"播放第 {step_i}/{len(tts_meta)} 句（{case_index}/{case_total}）",
                status="running",
            )
            play_started = datetime.now(timezone.utc).isoformat()
            play_offset = round(time.time() - t0, 3)
            play_audio_file(item["wav"], ffmpeg_path=cfg.capture.ffmpeg_path)
            play_ended = datetime.now(timezone.utc).isoformat()
            play_end_offset = round(time.time() - t0, 3)
            asr_info: dict[str, Any] = {"enabled": bool(cfg.asr.enabled)}
            asr_elapsed = 0.0
            if cfg.asr.enabled:
                _prog_device(
                    case,
                    index=case_index,
                    total=case_total,
                    device_status="running",
                    device_label=(
                        f"ASR截屏 第 {step_i}/{len(tts_meta)} 句"
                        f"（{case_index}/{case_total}）"
                    ),
                    status="running",
                )
                asr_dir = run_dir / "asr_frames"
                poll = poll_asr_screenshots(
                    adb,
                    asr_dir,
                    asr=cfg.asr,
                    ffmpeg_path=cfg.capture.ffmpeg_path,
                    step_index=step_i,
                )
                asr_info.update(asr_poll_meta(poll))
                asr_elapsed = float(poll.elapsed_sec or 0.0)
            wait_sec = float(item["wait_sec"])
            remain_wait = max(0.0, wait_sec - asr_elapsed)
            if remain_wait > 0:
                time.sleep(remain_wait)
            timeline["steps"].append(
                {
                    **item,
                    "play_started_at": play_started,
                    "play_offset_sec": play_offset,
                    "play_ended_at": play_ended,
                    "play_end_offset_sec": play_end_offset,
                    "asr_poll": asr_info,
                }
            )
        if tail > 0:
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

    _prog_device(
        case,
        index=case_index,
        total=case_total,
        device_status="running",
        device_label=f"拉取录屏（{case_index}/{case_total}）",
        status="running",
    )
    adb.pull(_device_posix(remote_on_device), local_video)
    adb.rm_remote(remote_on_device)

    _prog_device(
        case,
        index=case_index,
        total=case_total,
        device_status="running",
        device_label=f"回桌面（{case_index}/{case_total}）",
        status="running",
    )
    home_after_pull = _return_to_system_home(
        cfg, adb=adb, case_id=case.case_id, phase="after_pull", wait=True
    )
    timeline["return_home_after_pull"] = home_after_pull
    (run_dir / "timeline.json").write_text(
        json.dumps(timeline, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    update_case(
        index=case_index,
        case_id=case.case_id,
        name=case.name,
        status="running",
        device_status="done",
        device_label="设备执行完成",
        judge_status="queued",
        judge_label="排队等待评判",
        phase="queued_judge",
        phase_label="设备完成 · 评判排队",
        touch_device_current=True,
    )

    return _DeviceCapture(
        case=case,
        case_index=case_index,
        case_total=case_total,
        run_dir=run_dir,
        local_video=local_video,
        timeline=timeline,
        tts_meta=tts_meta,
        home_after_pull=home_after_pull,
        extract_frames_flag=extract_frames_flag,
        opened_package=opened_package,
        precondition_info=pre_info,
    )


def judge_one_capture(
    cap: _DeviceCapture,
    cfg: AppConfig,
    *,
    judge_backend: JudgeBackend = "auto",
) -> RunResult:
    """Background: extract frames + VL/mock judge + report. Failures become error results."""
    case = cap.case
    idx = cap.case_index
    try:
        _prog_judge(
            case,
            index=idx,
            judge_status="running",
            judge_label="抽帧中",
            status="running",
        )
        frame_paths: list[str] = []
        frames_dir = cap.run_dir / "frames"
        if cap.extract_frames_flag and cfg.capture.fps > 0:
            frames = extract_frames(
                cap.local_video,
                frames_dir,
                fps=cfg.capture.fps,
                ffmpeg_path=cfg.capture.ffmpeg_path,
            )
            frame_paths = [str(p) for p in frames]

        expects = case.exceptions()
        if not expects:
            raise JudgeError(f"用例 {case.case_id} 无预期结果，无法评判")

        _prog_judge(
            case,
            index=idx,
            judge_status="running",
            judge_label="VL/mock 评判中",
            status="running",
        )
        evaluation = judge_images(
            exceptions=expects,
            run_dir=cap.run_dir,
            backend=judge_backend,
            write=True,
        )
        passed = evaluation.get("passed")

        asr_summary: dict[str, Any] = {
            "enabled": bool(cfg.asr.enabled),
            "total": 0,
            "passed": 0,
            "failed": 0,
            "accuracy": None,
            "utterances": [],
        }
        if cfg.asr.enabled:
            _prog_judge(
                case,
                index=idx,
                judge_status="running",
                judge_label="ASR 准确率评判中",
                status="running",
            )
            step_rows = (cap.timeline or {}).get("steps") or []
            asr_out_dir = cap.run_dir / "asr"
            asr_out_dir.mkdir(parents=True, exist_ok=True)
            for step in step_rows:
                if not isinstance(step, dict):
                    continue
                spoken = str(step.get("text") or "")
                poll = step.get("asr_poll") or {}
                paths = [str(p) for p in (poll.get("paths") or []) if p]
                try:
                    asr_eval = judge_asr_utterance(
                        spoken_text=spoken,
                        image_paths=paths,
                        backend=judge_backend,
                        ignore_texts=list(cfg.asr.ignore_texts),
                        write_path=asr_out_dir / f"step_{int(step.get('index') or 0):02d}.json",
                    )
                except Exception as e:
                    asr_eval = {
                        "passed": False,
                        "result_parsed": {
                            "match": False,
                            "recognized_text": "",
                            "spoken_text": spoken,
                            "reason": f"ASR 评判异常: {e}",
                        },
                        "backend": judge_backend,
                    }
                parsed = asr_eval.get("result_parsed") if isinstance(asr_eval, dict) else {}
                if not isinstance(parsed, dict):
                    parsed = {}
                ok = bool(asr_eval.get("passed")) if isinstance(asr_eval, dict) else False
                asr_summary["total"] += 1
                if ok:
                    asr_summary["passed"] += 1
                else:
                    asr_summary["failed"] += 1
                asr_summary["utterances"].append(
                    {
                        "index": step.get("index"),
                        "spoken_text": spoken,
                        "recognized_text": parsed.get("recognized_text") or "",
                        "passed": ok,
                        "reason": parsed.get("reason") or "",
                        "backend": asr_eval.get("backend") if isinstance(asr_eval, dict) else "",
                        "image_count": len(paths),
                        "early_stopped": bool(poll.get("early_stopped")),
                        "evaluation_path": (
                            asr_eval.get("evaluation_path") if isinstance(asr_eval, dict) else ""
                        ),
                    }
                )
            if asr_summary["total"] > 0:
                asr_summary["accuracy"] = round(
                    asr_summary["passed"] / float(asr_summary["total"]), 4
                )
            (cap.run_dir / "asr_summary.json").write_text(
                json.dumps(asr_summary, ensure_ascii=False, indent=2), encoding="utf-8"
            )

        report_path = cap.run_dir / "report.md"
        payload = {
            "passed": passed,
            "judge_backend": evaluation.get("backend"),
            "run_dir": str(cap.run_dir),
            "evaluation": evaluation,
            "asr": asr_summary,
            "tts": cap.tts_meta,
            "frame_count": len(frame_paths),
            "return_home_after_pull": cap.home_after_pull,
            "judge_async": bool(cfg.run.judge_async),
        }
        _write_report(report_path, case, payload)
        (cap.run_dir / "run_meta.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        (cap.run_dir / "timeline.json").write_text(
            json.dumps(cap.timeline, ensure_ascii=False, indent=2), encoding="utf-8"
        )

        final_status = (
            "passed" if passed is True else ("failed" if passed is False else "error")
        )
        _prog_judge(
            case,
            index=idx,
            judge_status=final_status,
            judge_label="评判完成",
            status=final_status,
        )
        return RunResult(
            case_id=case.case_id,
            name=case.name,
            passed=passed if isinstance(passed, bool) else None,
            run_dir=str(cap.run_dir),
            evaluation_path=str(evaluation.get("evaluation_path") or ""),
            report_path=str(report_path),
        )
    except Exception as e:
        err = str(e)
        print(f"[echo-eval][warn] judge failed case={case.case_id}: {err}", flush=True)
        _prog_judge(
            case,
            index=idx,
            judge_status="error",
            judge_label=f"评判失败: {err[:80]}",
            status="error",
        )
        report_path = cap.run_dir / "report.md"
        payload = {
            "passed": None,
            "run_dir": str(cap.run_dir),
            "error": err,
            "tts": cap.tts_meta,
        }
        try:
            _write_report(report_path, case, payload)
            (cap.run_dir / "run_meta.json").write_text(
                json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
            )
        except Exception:
            pass
        return RunResult(
            case_id=case.case_id,
            name=case.name,
            passed=None,
            run_dir=str(cap.run_dir),
            error=err,
            report_path=str(report_path) if report_path.exists() else "",
        )


def run_one_case(
    case: EvalCase,
    cfg: AppConfig,
    *,
    judge_backend: JudgeBackend = "auto",
    extract_frames_flag: bool = True,
    case_index: int = 1,
    case_total: int = 1,
    previous_package: str = "",
    precondition_cache: PreconditionCache | None = None,
) -> RunResult:
    """Synchronous path (device + judge). Used when judge_async is false."""
    cap = capture_one_case(
        case,
        cfg,
        case_index=case_index,
        case_total=case_total,
        extract_frames_flag=extract_frames_flag,
        previous_package=previous_package,
        precondition_cache=precondition_cache,
    )
    return judge_one_capture(cap, cfg, judge_backend=judge_backend)


def _mark_case_skipped(
    case: EvalCase,
    *,
    index: int,
    total: int,
    reason: str,
) -> RunResult:
    print(f"[echo-eval][skip] {case.case_id}: {reason}", flush=True)
    update_case(
        index=index,
        case_id=case.case_id,
        name=case.name,
        status="error",
        device_status="skipped",
        device_label=(reason[:80] if reason else "预置未就绪，已跳过"),
        judge_status="skipped",
        judge_label="跳过",
        phase="skipped",
        phase_label=f"跳过（{index}/{total}）",
        touch_device_current=True,
    )
    return RunResult(
        case_id=case.case_id,
        name=case.name,
        passed=None,
        run_dir="",
        error=reason,
    )


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
        f"judge_async={cfg.run.judge_async} judge_parallel={cfg.run.judge_parallel} "
        f"precondition.enabled={cfg.precondition.enabled} "
        f"verify_after_replay={cfg.precondition.verify_after_replay} "
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

    pre_cache = PreconditionCache(cfg.precondition.resolved_cache_dir())
    skip_ids: set[str] = set()
    skip_reasons: dict[str, str] = {}
    if cfg.precondition.enabled and cfg.precondition.coverage_check:
        report = build_coverage_report(
            cases, cfg=cfg.precondition, cache=pre_cache
        )
        print_coverage_report(
            report, cache_path=str(pre_cache.path.resolve())
        )
        skip_ids = report.skip_case_ids
        skip_reasons = {
            c.case_id: c.skip_reason for c in report.cases if c.skip
        }

    init_progress([(c.case_id, c.name) for c in cases])
    summary = BatchSummary(
        excel=str(Path(excel_path).resolve()),
        started_at=datetime.now(timezone.utc).isoformat(),
    )
    total = len(cases)
    async_mode = bool(cfg.run.judge_async)
    workers = max(1, int(cfg.run.judge_parallel))
    packages = load_packages_map(cfg.precondition.resolved_packages_path())
    object_packages = load_object_packages(cfg.precondition.resolved_packages_path())
    previous_package = ""

    if not async_mode:
        for idx, case in enumerate(cases, 1):
            if case.case_id in skip_ids:
                reason = skip_reasons.get(
                    case.case_id, f"预置条件未就绪，跳过用例 {case.case_id}"
                )
                summary.results.append(
                    _mark_case_skipped(case, index=idx, total=total, reason=reason)
                )
                continue
            try:
                result = run_one_case(
                    case,
                    cfg,
                    judge_backend=judge_backend,
                    extract_frames_flag=extract_frames_flag,
                    case_index=idx,
                    case_total=total,
                    previous_package=previous_package,
                    precondition_cache=pre_cache,
                )
                previous_package = resolve_opened_package(
                    case, packages=packages, object_packages=object_packages
                )
            except PreconditionSkip as e:
                result = _mark_case_skipped(
                    case, index=idx, total=total, reason=str(e)
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
                    device_status="error",
                    device_label="设备执行失败",
                    judge_status="skipped",
                    judge_label="跳过",
                    phase="error",
                    phase_label=f"错误（{idx}/{total}）",
                    touch_device_current=True,
                )
                _return_to_system_home(
                    cfg, case_id=case.case_id, phase="after_error", wait=True
                )
            summary.results.append(result)
            if fail_fast and result.error:
                break
        finish_batch()
    else:
        # Device serial + background judge pool (TestApp-style).
        futures = []
        results_by_index: dict[int, RunResult] = {}
        with ThreadPoolExecutor(max_workers=workers) as pool:
            for idx, case in enumerate(cases, 1):
                if case.case_id in skip_ids:
                    reason = skip_reasons.get(
                        case.case_id, f"预置条件未就绪，跳过用例 {case.case_id}"
                    )
                    results_by_index[idx] = _mark_case_skipped(
                        case, index=idx, total=total, reason=reason
                    )
                    continue
                try:
                    cap = capture_one_case(
                        case,
                        cfg,
                        case_index=idx,
                        case_total=total,
                        extract_frames_flag=extract_frames_flag,
                        previous_package=previous_package,
                        precondition_cache=pre_cache,
                    )
                    previous_package = cap.opened_package or resolve_opened_package(
                        case, packages=packages, object_packages=object_packages
                    )
                    fut = pool.submit(
                        judge_one_capture,
                        cap,
                        cfg,
                        judge_backend=judge_backend,
                    )
                    futures.append((idx, case, fut))
                    print(
                        f"[echo-eval] queued judge case={case.case_id} "
                        f"({idx}/{total}) workers={workers}",
                        flush=True,
                    )
                except PreconditionSkip as e:
                    results_by_index[idx] = _mark_case_skipped(
                        case, index=idx, total=total, reason=str(e)
                    )
                except Exception as e:
                    result = RunResult(
                        case_id=case.case_id,
                        name=case.name,
                        passed=None,
                        run_dir="",
                        error=str(e),
                    )
                    results_by_index[idx] = result
                    update_case(
                        index=idx,
                        case_id=case.case_id,
                        name=case.name,
                        status="error",
                        device_status="error",
                        device_label="设备执行失败",
                        judge_status="skipped",
                        judge_label="跳过",
                        phase="error",
                        phase_label=f"设备错误（{idx}/{total}）",
                        touch_device_current=True,
                    )
                    _return_to_system_home(
                        cfg, case_id=case.case_id, phase="after_error", wait=True
                    )
                    # B1: device fail_fast only stops further device runs;
                    # already-queued judges still finish.
                    if fail_fast:
                        print(
                            f"[echo-eval] fail_fast: stop further device cases "
                            f"after {case.case_id}",
                            flush=True,
                        )
                        break

            set_batch_phase(
                phase="judging",
                phase_label=f"设备侧已结束，等待评判完成（并行={workers}）",
            )
            for idx, case, fut in futures:
                try:
                    results_by_index[idx] = fut.result()
                except Exception as e:
                    results_by_index[idx] = RunResult(
                        case_id=case.case_id,
                        name=case.name,
                        passed=None,
                        run_dir="",
                        error=str(e),
                    )
                    _prog_judge(
                        case,
                        index=idx,
                        judge_status="error",
                        judge_label=str(e)[:80],
                        status="error",
                    )

        for idx in sorted(results_by_index):
            summary.results.append(results_by_index[idx])
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
                "judge_async": async_mode,
                "judge_parallel": workers if async_mode else 1,
                "precondition": {
                    "enabled": cfg.precondition.enabled,
                    "coverage_check": cfg.precondition.coverage_check,
                    "verify_after_replay": cfg.precondition.verify_after_replay,
                    "skipped_case_ids": sorted(skip_ids),
                },
                "results": [asdict(r) for r in summary.results],
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return summary
