"""CLI: doctor / connect / record / spike / judge.

Phase 0–3: capture foundation + multi-frame VL judge (TestApp E1 port).
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

from echo_eval import __version__
from echo_eval.capture.frames import FrameExtractError, resolve_ffmpeg
from echo_eval.capture.screenrecord import record_session
from echo_eval.config import DEFAULT_CONFIG_PATH, REPO_ROOT, load_config
from echo_eval.device.adb import AdbClient, AdbError
from echo_eval.judge.model_config import find_model_config_path
from echo_eval.judge.service import JudgeError, judge_images, resolve_backend
from echo_eval.runner.excel_runner import run_excel
from echo_eval.stimulus.audio import AudioPlayError, _resolve_ffplay
from echo_eval.stimulus.tts import TtsError


def _add_config_arg(p: argparse.ArgumentParser) -> None:
    p.add_argument(
        "-c",
        "--config",
        default=str(DEFAULT_CONFIG_PATH),
        help=f"设备配置文件路径（默认: {DEFAULT_CONFIG_PATH}）",
    )


def _print_capture_result(result, *, extract: bool) -> None:
    print(f"run_dir: {result.run_dir}")
    print(f"video:   {result.local_video}")
    if result.timeline.get("audio_path"):
        print(
            f"audio:   {result.timeline.get('audio_path')} "
            f"(offset={result.timeline.get('play_offset_sec')}s, "
            f"backend={result.timeline.get('audio_backend')})"
        )
    if result.frame_paths:
        print(f"frames:  {len(result.frame_paths)} -> {result.frames_dir}")
    elif extract:
        print("frames:  (none)")
    else:
        print("frames:  skipped (--no-frames)")


def cmd_doctor(args: argparse.Namespace) -> int:
    print(f"echo-eval {__version__}")
    cfg_path = Path(args.config)
    print(f"config: {cfg_path} ({'OK' if cfg_path.is_file() else 'MISSING'})")

    problems: list[str] = []

    try:
        cfg = load_config(cfg_path) if cfg_path.is_file() else None
    except Exception as e:
        cfg = None
        problems.append(f"配置无法加载: {e}")

    if cfg and cfg.adb.adb_path:
        adb_bin = cfg.adb.adb_path
    else:
        adb_bin = shutil.which("adb") or ""

    if adb_bin:
        print(f"adb: {adb_bin}")
        try:
            serial = cfg.adb.resolved_serial if cfg else "emulator-5554"
            ver = AdbClient(serial=serial, adb_path=adb_bin).version()
            print(f"adb version: {ver}")
        except AdbError as e:
            problems.append(str(e))
    else:
        problems.append("未找到 adb（PATH 或 adb.adb_path）")

    try:
        ff = resolve_ffmpeg(cfg.capture.ffmpeg_path if cfg else "")
        print(f"ffmpeg: {ff}")
    except FrameExtractError as e:
        problems.append(str(e))

    ffplay = _resolve_ffplay(cfg.capture.ffmpeg_path if cfg else "")
    if ffplay:
        print(f"ffplay: {ffplay}")
    else:
        print("ffplay: (missing — spike/host 播放可用 Windows winsound 播 wav，或安装 ffmpeg)")

    if cfg:
        try:
            print(f"serial: {cfg.adb.resolved_serial}")
            print(f"capture.duration_sec: {cfg.capture.duration_sec}")
            print(f"capture.remote_path: {cfg.capture.remote_path}")
            print(f"capture.local_dir: {cfg.capture.local_root()}")
            print(f"capture.fps: {cfg.capture.fps}")
            print(f"stimulus.enabled: {cfg.stimulus.enabled}")
            print(f"stimulus.path: {cfg.stimulus.resolved_path(REPO_ROOT)}")
        except ValueError as e:
            problems.append(str(e))

    model_path = find_model_config_path()
    if model_path:
        print(f"vl_eval config: {model_path}")
        print("judge backend auto: vl")
    else:
        print("vl_eval config: (missing — judge --backend auto 将使用 mock)")
        print("  提示: 编辑 configs/model.yaml 填写 api_key（结构同 TestApp）")

    if problems:
        print("doctor: FAIL")
        for p in problems:
            print(f"  - {p}")
        return 1
    print("doctor: OK")
    return 0


def cmd_connect(args: argparse.Namespace) -> int:
    cfg = load_config(args.config)
    serial = cfg.adb.resolved_serial
    print(f"connecting {serial} ...")
    client = AdbClient(serial=serial, adb_path=cfg.adb.adb_path)
    try:
        state = client.connect(timeout_sec=cfg.adb.connect_timeout_sec)
    except AdbError as e:
        print(f"connect: FAIL\n{e}", file=sys.stderr)
        return 1
    print(f"connect: {state.serial}\t{state.state}")
    if not client.shell_ok():
        print("connect: device 已显示 device，但 shell 探测失败", file=sys.stderr)
        return 1
    print("connect: OK")
    return 0


def _run_record(args: argparse.Namespace, *, force_play: bool | None) -> int:
    cfg = load_config(args.config)
    if args.seconds is not None:
        cfg.capture.duration_sec = int(args.seconds)
    if args.fps is not None:
        cfg.capture.fps = float(args.fps)

    extract = not args.no_frames
    audio_path = getattr(args, "audio", None)
    play_audio = force_play
    if play_audio is None and audio_path:
        play_audio = True

    print(
        f"record serial={cfg.adb.resolved_serial} "
        f"duration={cfg.capture.duration_sec}s fps={cfg.capture.fps} "
        f"extract_frames={extract} play_audio={play_audio if play_audio is not None else cfg.stimulus.enabled}"
    )
    try:
        result = record_session(
            cfg,
            extract=extract,
            keep_remote=args.keep_remote,
            play_audio=play_audio,
            audio_path=audio_path,
        )
    except (AdbError, FrameExtractError, AudioPlayError, FileNotFoundError, ValueError) as e:
        print(f"record: FAIL\n{e}", file=sys.stderr)
        return 1

    _print_capture_result(result, extract=extract)
    print("record: OK")
    return 0


def cmd_record(args: argparse.Namespace) -> int:
    return _run_record(args, force_play=None)


def cmd_spike(args: argparse.Namespace) -> int:
    """End-to-end puncture: connect + record with host audio playback."""
    return _run_record(args, force_play=True)


def cmd_run(args: argparse.Namespace) -> int:
    print(
        f"run excel={args.excel} case_id={args.case_id or '*'} "
        f"backend={args.backend} fail_fast={args.fail_fast}"
    )
    try:
        summary = run_excel(
            args.excel,
            config_path=args.config,
            case_id=args.case_id,
            judge_backend=args.backend,
            fail_fast=args.fail_fast,
            extract_frames_flag=not args.no_frames,
        )
    except (FileNotFoundError, ValueError, ImportError, AdbError, TtsError, JudgeError) as e:
        print(f"run: FAIL\n{e}", file=sys.stderr)
        return 2
    except Exception as e:
        print(f"run: FAIL\n{e}", file=sys.stderr)
        return 2

    for r in summary.results:
        status = (
            "PASS"
            if r.passed is True
            else ("FAIL" if r.passed is False else "ERROR")
        )
        extra = f" err={r.error}" if r.error else ""
        print(f"[{status}] {r.case_id} {r.name} dir={r.run_dir or '-'}{extra}")
    print(
        f"summary: passed={summary.passed_count} failed={summary.failed_count} "
        f"errors={summary.error_count}"
    )
    if summary.summary_path:
        print(f"summary_path: {summary.summary_path}")

    if summary.error_count:
        print("run: ERROR")
        return 2
    if summary.failed_count:
        print("run: FAIL")
        return 1
    print("run: OK")
    return 0


def cmd_judge(args: argparse.Namespace) -> int:
    expects: list[str] = []
    if args.expect:
        expects.extend(args.expect)
    if args.expect_file:
        text = Path(args.expect_file).read_text(encoding="utf-8")
        expects.extend([ln.strip() for ln in text.splitlines() if ln.strip()])
    if not expects:
        print("judge: FAIL\n请提供 --expect 或 --expect-file", file=sys.stderr)
        return 2

    backend = resolve_backend(args.backend)
    print(
        f"judge backend={backend} (requested={args.backend}) "
        f"expects={len(expects)} run_dir={args.run_dir or '-'} "
        f"images={len(args.image or [])}"
    )
    try:
        data = judge_images(
            exceptions=expects,
            run_dir=args.run_dir,
            images=args.image,
            backend=args.backend,
            write=not args.no_write,
        )
    except JudgeError as e:
        print(f"judge: FAIL\n{e}", file=sys.stderr)
        return 2

    passed = data.get("passed")
    print(f"passed: {passed}")
    print(f"images_used: {data.get('image_count_used')}/{data.get('image_count_total')}")
    if data.get("evaluation_path"):
        print(f"evaluation: {data['evaluation_path']}")
    parsed = data.get("result_parsed")
    if isinstance(parsed, list):
        for item in parsed:
            if not isinstance(item, dict):
                continue
            label = item.get("final_result") or item.get("expectation") or "?"
            print(f"  - {label}: {item.get('result')}")
    if passed is True:
        print("judge: OK")
        return 0
    if passed is False:
        print("judge: FAIL")
        return 1
    print("judge: UNKNOWN")
    return 2


def _add_record_args(p: argparse.ArgumentParser) -> None:
    _add_config_arg(p)
    p.add_argument("--seconds", type=int, default=None, help="覆盖配置中的录制秒数")
    p.add_argument("--fps", type=float, default=None, help="覆盖抽帧 fps；0 表示不抽帧")
    p.add_argument(
        "--no-frames",
        action="store_true",
        help="只拉视频，不抽帧（无 ffmpeg 时可用）",
    )
    p.add_argument(
        "--keep-remote",
        action="store_true",
        help="pull 后保留设备上的视频文件",
    )
    p.add_argument(
        "--audio",
        default=None,
        help="本机播放的音频路径（覆盖 stimulus.path；隐含开启播放）",
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="echo-eval",
        description=(
            "语音助手无线 ADB 采集与多帧 VL 评判（Phase 0–3）。"
            "评判逻辑对齐 TestApp evaluate_gui_poll_images（E1）。"
        ),
    )
    parser.add_argument("--version", action="version", version=f"echo-eval {__version__}")
    sub = parser.add_subparsers(dest="command", required=True)

    p_doc = sub.add_parser("doctor", help="检查 adb / ffmpeg / ffplay / 设备与模型配置")
    _add_config_arg(p_doc)
    p_doc.set_defaults(func=cmd_doctor)

    p_conn = sub.add_parser("connect", help="按配置文件无线连接设备")
    _add_config_arg(p_conn)
    p_conn.set_defaults(func=cmd_connect)

    p_rec = sub.add_parser(
        "record",
        help="录屏→pull→抽帧；若配置 stimulus.enabled 或传 --audio 则边录边播",
    )
    _add_record_args(p_rec)
    p_rec.set_defaults(func=cmd_record)

    p_spike = sub.add_parser(
        "spike",
        help="穿刺：强制本机播放刺激音频 + 录屏抽帧（测评采集链路联调）",
    )
    _add_record_args(p_spike)
    p_spike.set_defaults(func=cmd_spike)

    p_judge = sub.add_parser(
        "judge",
        help="多帧截图 + 预期结果 → evaluation.json（TestApp gui_poll E1 / Mock M2）",
    )
    p_judge.add_argument("--run-dir", default=None, help="含 frames/ 的采集目录")
    p_judge.add_argument(
        "--image",
        action="append",
        default=None,
        help="额外指定图片路径，可重复；不配 --run-dir 时必填",
    )
    p_judge.add_argument(
        "--expect",
        action="append",
        default=None,
        help="一条预期结果（exceptions），可重复",
    )
    p_judge.add_argument(
        "--expect-file",
        default=None,
        help="预期结果文本文件，一行一条",
    )
    p_judge.add_argument(
        "--backend",
        choices=["auto", "vl", "mock"],
        default="auto",
        help="auto=有 model.yaml 用 vl，否则 mock",
    )
    p_judge.add_argument(
        "--no-write",
        action="store_true",
        help="不写 evaluation.json（仅打印）",
    )
    p_judge.set_defaults(func=cmd_judge)

    p_run = sub.add_parser(
        "run",
        help="Phase4：Excel/CSV → TTS → 录屏 → 评判 → 报告（可单条或批量）",
    )
    _add_config_arg(p_run)
    p_run.add_argument(
        "--excel",
        required=True,
        help="TestApp 风格用例表（.xlsx/.csv；xlsx 需 openpyxl）",
    )
    p_run.add_argument("--case-id", default=None, help="只跑指定用例编号")
    p_run.add_argument(
        "--backend",
        choices=["auto", "vl", "mock"],
        default="auto",
        help="评判后端（默认 auto）",
    )
    p_run.add_argument(
        "--fail-fast",
        action="store_true",
        help="遇失败或错误即停止批量",
    )
    p_run.add_argument(
        "--no-frames",
        action="store_true",
        help="不抽帧（仍会录屏；评判需要帧时不要加）",
    )
    p_run.set_defaults(func=cmd_run)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))
