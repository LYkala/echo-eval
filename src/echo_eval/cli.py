"""CLI for Phase 0–1: doctor / connect / record.

These commands are the device-capture foundation of automated evaluation.
Later `run --case` will call the same modules after audio stimulus + judge.
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

from echo_eval import __version__
from echo_eval.capture.frames import FrameExtractError, resolve_ffmpeg
from echo_eval.capture.screenrecord import record_session
from echo_eval.config import DEFAULT_CONFIG_PATH, load_config
from echo_eval.device.adb import AdbClient, AdbError


def _add_config_arg(p: argparse.ArgumentParser) -> None:
    p.add_argument(
        "-c",
        "--config",
        default=str(DEFAULT_CONFIG_PATH),
        help=f"设备配置文件路径（默认: {DEFAULT_CONFIG_PATH}）",
    )


def cmd_doctor(args: argparse.Namespace) -> int:
    print(f"echo-eval {__version__}")
    cfg_path = Path(args.config)
    print(f"config: {cfg_path} ({'OK' if cfg_path.is_file() else 'MISSING'})")

    problems: list[str] = []

    adb_bin = ""
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
            # serial not required for version
            serial = cfg.adb.resolved_serial if cfg else "emulator-5554"
            ver = AdbClient(serial=serial, adb_path=adb_bin).version()
            print(f"adb version: {ver}")
        except AdbError as e:
            problems.append(str(e))
    else:
        problems.append("未找到 adb（PATH 或 adb.adb_path）")

    ff = ""
    try:
        ff = resolve_ffmpeg(cfg.capture.ffmpeg_path if cfg else "")
        print(f"ffmpeg: {ff}")
    except FrameExtractError as e:
        problems.append(str(e))

    if cfg:
        try:
            print(f"serial: {cfg.adb.resolved_serial}")
            print(f"capture.duration_sec: {cfg.capture.duration_sec}")
            print(f"capture.remote_path: {cfg.capture.remote_path}")
            print(f"capture.local_dir: {cfg.capture.local_root()}")
            print(f"capture.fps: {cfg.capture.fps}")
        except ValueError as e:
            problems.append(str(e))

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


def cmd_record(args: argparse.Namespace) -> int:
    cfg = load_config(args.config)
    if args.seconds is not None:
        cfg.capture.duration_sec = int(args.seconds)
    if args.fps is not None:
        cfg.capture.fps = float(args.fps)

    extract = not args.no_frames
    print(
        f"record serial={cfg.adb.resolved_serial} "
        f"duration={cfg.capture.duration_sec}s fps={cfg.capture.fps} "
        f"extract_frames={extract}"
    )
    try:
        result = record_session(cfg, extract=extract, keep_remote=args.keep_remote)
    except (AdbError, FrameExtractError, FileNotFoundError, ValueError) as e:
        print(f"record: FAIL\n{e}", file=sys.stderr)
        return 1

    print(f"run_dir: {result.run_dir}")
    print(f"video:   {result.local_video}")
    if result.frame_paths:
        print(f"frames:  {len(result.frame_paths)} -> {result.frames_dir}")
    elif extract:
        print("frames:  (none)")
    else:
        print("frames:  skipped (--no-frames)")
    print("record: OK")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="echo-eval",
        description=(
            "语音助手无线 ADB 采集工具（Phase 0–1）。"
            "完整测评流水线将复用 connect/record，并在后续阶段加入播放与评判。"
        ),
    )
    parser.add_argument("--version", action="version", version=f"echo-eval {__version__}")
    sub = parser.add_subparsers(dest="command", required=True)

    p_doc = sub.add_parser("doctor", help="检查 adb / ffmpeg / 配置是否可用")
    _add_config_arg(p_doc)
    p_doc.set_defaults(func=cmd_doctor)

    p_conn = sub.add_parser("connect", help="按配置文件无线连接设备")
    _add_config_arg(p_conn)
    p_conn.set_defaults(func=cmd_connect)

    p_rec = sub.add_parser(
        "record",
        help="连接（如需要）→ screenrecord → pull → 抽帧；产物写入 artifacts/",
    )
    _add_config_arg(p_rec)
    p_rec.add_argument("--seconds", type=int, default=None, help="覆盖配置中的录制秒数")
    p_rec.add_argument("--fps", type=float, default=None, help="覆盖抽帧 fps；0 表示不抽帧")
    p_rec.add_argument(
        "--no-frames",
        action="store_true",
        help="只拉视频，不抽帧（无 ffmpeg 时可用）",
    )
    p_rec.add_argument(
        "--keep-remote",
        action="store_true",
        help="pull 后保留设备上的视频文件",
    )
    p_rec.set_defaults(func=cmd_record)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))
