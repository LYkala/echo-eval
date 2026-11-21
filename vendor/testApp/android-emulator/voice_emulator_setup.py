#!/usr/bin/env python3
"""Voice lab setup: start emulator, download/install x86 voice APK, push audio files, dump UI.

Standalone script for voice I/O experiments. Does NOT involve TestApp or WebServer.
"""

from __future__ import annotations

import argparse
import sys
import time
import urllib.request
from pathlib import Path
from typing import Dict, Optional

ROOT = Path(__file__).resolve().parent
APK_DIR = ROOT / "apks"
AUDIO_DIR = ROOT / "audio_samples"
DUMP_DIR = ROOT / "dumps"

# x86_64 APK presets (HuggingFace mirror, suitable for Windows x86 emulator)
VOICE_APK_PRESETS: Dict[str, Dict[str, str]] = {
    "sherpa-asr": {
        "label": "Sherpa-ONNX 流式语音识别 (中英双语)",
        "url": (
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-apk/resolve/main/asr/1.12.14/"
            "sherpa-onnx-1.12.14-x86_64-asr-bilingual_zh_en-zipformer.apk"
        ),
        "filename": "sherpa-onnx-1.12.14-x86_64-asr-bilingual_zh_en-zipformer.apk",
    },
    "sherpa-asr-zh": {
        "label": "Sherpa-ONNX 流式语音识别 (中文)",
        "url": (
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-apk/resolve/main/asr/1.12.14/"
            "sherpa-onnx-1.12.14-x86_64-asr-zh-zipformer2.apk"
        ),
        "filename": "sherpa-onnx-1.12.14-x86_64-asr-zh-zipformer2.apk",
    },
    "sherpa-tts-zh": {
        "label": "Sherpa-ONNX 语音合成 TTS Engine (中文，可与 ASR 共存)",
        "url": (
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-apk/resolve/main/tts-engine-new/1.12.14/"
            "sherpa-onnx-1.12.14-x86_64-zh-tts-engine-vits-piper-zh_CN-huayan-medium.apk"
        ),
        "filename": (
            "sherpa-onnx-1.12.14-x86_64-zh-tts-engine-vits-piper-zh_CN-huayan-medium.apk"
        ),
    },
}

from install_and_dump_ui import (  # noqa: E402
    dump_ui_xml,
    find_aapt,
    find_adb,
    install_apk,
    launch_app,
    list_devices,
    parse_apk_info,
    parse_ui_nodes,
    pick_device,
    resolve_launcher_activity,
    start_emulator_if_needed,
    wait_for_boot,
    write_reports,
)


def download_apk(url: str, dest: Path, force: bool = False) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.is_file() and not force:
        print(f"APK already exists: {dest}")
        return dest
    print(f"Downloading {url}")
    print(f"  -> {dest}")
    req = urllib.request.Request(url, headers={"User-Agent": "voice-emulator-setup/1.0"})
    with urllib.request.urlopen(req, timeout=600) as resp:
        data = resp.read()
    dest.write_bytes(data)
    print(f"Downloaded {len(data) / 1024 / 1024:.1f} MB")
    return dest


def push_audio_files(adb, serial: str, source: Path) -> int:
    if not source.exists():
        raise FileNotFoundError(f"Audio path not found: {source}")
    remote_dir = "/sdcard/voice_lab"
    adb_cmd_list = [str(adb), "-s", serial, "shell", "mkdir", "-p", remote_dir]
    import subprocess

    subprocess.run(adb_cmd_list, check=True)
    count = 0
    patterns = ("*.wav", "*.mp3", "*.m4a", "*.ogg")
    files = []
    if source.is_file():
        files = [source]
    else:
        for pattern in patterns:
            files.extend(source.glob(pattern))
    files = sorted(set(files))
    if not files:
        print(f"No audio files found under {source}")
        return 0
    for item in files:
        remote = f"{remote_dir}/{item.name}"
        print(f"Pushing {item.name} -> {remote}")
        subprocess.run([str(adb), "-s", serial, "push", str(item), remote], check=True)
        count += 1
    print(f"Pushed {count} audio file(s) to {remote_dir}")
    return count


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Voice lab: emulator + x86 voice APK install (no TestApp).",
    )
    parser.add_argument(
        "--preset",
        choices=sorted(VOICE_APK_PRESETS.keys()),
        default="sherpa-asr",
        help="Built-in x86 voice APK preset (default: sherpa-asr)",
    )
    parser.add_argument("--apk", help="Custom APK path (overrides --preset)")
    parser.add_argument(
        "--download-only",
        action="store_true",
        help="Only download APK to android-emulator/apks/",
    )
    parser.add_argument(
        "--skip-download",
        action="store_true",
        help="Skip download; use existing APK",
    )
    parser.add_argument(
        "--no-start-emulator",
        action="store_true",
        help="Do not auto-start emulator",
    )
    parser.add_argument(
        "--avd",
        default="TestAppGUI_API34",
        help="AVD name when auto-starting emulator (default: TestAppGUI_API34)",
    )
    parser.add_argument("--serial", help="Device serial")
    parser.add_argument("--reinstall", action="store_true", help="adb install -r")
    parser.add_argument("--skip-install", action="store_true", help="Skip APK install")
    parser.add_argument("--skip-launch", action="store_true", help="Skip launching app")
    parser.add_argument("--skip-dump", action="store_true", help="Skip UI dump")
    parser.add_argument(
        "--push-audio",
        metavar="PATH",
        help="Push wav/mp3/m4a/ogg files or folder to /sdcard/voice_lab/",
    )
    parser.add_argument(
        "--wait-sec",
        type=float,
        default=6.0,
        help="Seconds to wait after launch before UI dump",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    adb = find_adb()
    aapt = find_aapt()

    if args.apk:
        apk_path = Path(args.apk).resolve()
        if not apk_path.is_file():
            raise FileNotFoundError(f"APK not found: {apk_path}")
    else:
        preset = VOICE_APK_PRESETS[args.preset]
        print(f"Preset: {preset['label']}")
        apk_path = APK_DIR / preset["filename"]
        if not args.skip_download:
            download_apk(preset["url"], apk_path)
        elif not apk_path.is_file():
            raise FileNotFoundError(
                f"APK not found: {apk_path}. Run without --skip-download first."
            )

    if args.download_only:
        print(f"APK ready: {apk_path}")
        return 0

    if not args.no_start_emulator:
        import os

        os.environ["ANDROID_AVD_NAME"] = args.avd
        start_emulator_if_needed(adb)

    if not list_devices(adb):
        raise RuntimeError("No online device. Start emulator first.")

    package_name, activity, apk_abis = parse_apk_info(apk_path, aapt)
    serial = pick_device(adb, args.serial, apk_abis)
    print(f"Device  : {serial}")
    print(f"Package : {package_name or '(parse after install)'}")
    wait_for_boot(adb, serial)

    if not args.skip_install:
        install_apk(adb, serial, apk_path, reinstall=args.reinstall, apk_abis=apk_abis)
        package_name, activity, _ = parse_apk_info(apk_path, aapt)

    if args.push_audio:
        push_audio_files(adb, serial, Path(args.push_audio).resolve())

    if not args.skip_launch and package_name:
        if not activity:
            activity = resolve_launcher_activity(adb, serial, package_name)
        launch_app(adb, serial, package_name, activity)
        if args.wait_sec > 0:
            print(f"Waiting {args.wait_sec}s for UI ...")
            time.sleep(args.wait_sec)

    if not args.skip_dump and package_name:
        xml_text = dump_ui_xml(adb, serial, remote_path="/sdcard/voice_lab_ui_dump.xml")
        nodes = parse_ui_nodes(xml_text, package_filter=package_name)
        json_path, txt_path = write_reports(DUMP_DIR, package_name, apk_path, xml_text, nodes)
        print("")
        print(f"UI nodes: {len(nodes)}")
        print(f"Report  : {txt_path}")
        print(f"JSON    : {json_path}")

    print("")
    print("Voice lab ready.")
    print(f"  APK   : {apk_path}")
    print(f"  Audio : push with --push-audio (device path /sdcard/voice_lab/)")
    print("  Next  : open the app on emulator, use mic or inject audio via Extended Controls")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
