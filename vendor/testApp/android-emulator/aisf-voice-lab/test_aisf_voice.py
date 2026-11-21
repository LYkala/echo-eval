#!/usr/bin/env python3
"""AISF voice input/output test on Linux ARM emulator (EulerOS / openEuler)."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

import linux_audio

LAB_ROOT = Path(__file__).resolve().parent
EMULATOR_ROOT = LAB_ROOT.parent
DEFAULT_INPUT = LAB_ROOT / "input" / "weather_question.wav"
if not DEFAULT_INPUT.is_file():
    DEFAULT_INPUT = EMULATOR_ROOT / "audio_samples" / "weather_question.wav"
PACKAGE = os.environ.get("AISF_PACKAGE", "com.huawei.aisftvassistant")
ACTIVITY = os.environ.get(
    "AISF_ACTIVITY", "com.huawei.aisftvassistant/.activity.PhoneLaunchActivity"
)

VOICE_KEYWORDS = (
    "voice",
    "speech",
    "mic",
    "audio",
    "speak",
    "record",
    "asr",
    "tts",
    "listen",
    "talk",
    "语音",
    "说话",
    "麦克风",
    "录音",
    "按住",
    "聆听",
    "按住说",
)


def adb_bin() -> str:
    sdk = os.environ.get("ANDROID_SDK_ROOT", str(LAB_ROOT / "sdk"))
    candidate = Path(sdk) / "platform-tools" / "adb"
    if candidate.is_file():
        return str(candidate)
    return "adb"


def adb(serial: str, *args: str, check: bool = True, text: bool = True) -> subprocess.CompletedProcess:
    cmd = [adb_bin(), "-s", serial, *args]
    if text:
        return subprocess.run(cmd, capture_output=True, text=True, check=check, encoding="utf-8")
    return subprocess.run(cmd, capture_output=True, check=check)


def screenshot(serial: str, out_dir: Path, name: str) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    dest = out_dir / f"{name}.png"
    proc = subprocess.run(
        [adb_bin(), "-s", serial, "exec-out", "screencap", "-p"],
        capture_output=True,
        check=True,
    )
    dest.write_bytes(proc.stdout)
    print(f"  screenshot -> {dest.name}")
    return dest


def dump_ui(serial: str) -> str:
    remote = "/sdcard/aisf_ui_dump.xml"
    for _ in range(4):
        adb(serial, "shell", "uiautomator", "dump", remote, check=False)
        proc = adb(serial, "shell", "cat", remote, check=False, text=False)
        xml = (proc.stdout or b"").decode("utf-8", errors="replace")
        if PACKAGE in xml:
            return xml
        launch_app(serial)
        time.sleep(2)
    return xml


def launch_app(serial: str) -> None:
    adb(serial, "shell", "pm", "grant", PACKAGE, "android.permission.RECORD_AUDIO", check=False)
    adb(serial, "shell", "appops", "set", PACKAGE, "RECORD_AUDIO", "allow", check=False)
    adb(serial, "shell", "am", "force-stop", PACKAGE, check=False)
    adb(serial, "shell", "am", "start", "-W", "-n", ACTIVITY, check=False)
    time.sleep(2)


def parse_nodes(xml_text: str) -> list[dict]:
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return []
    nodes: list[dict] = []
    for elem in root.iter("node"):
        pkg = elem.attrib.get("package", "")
        if pkg and pkg != PACKAGE:
            continue
        node = {
            "resource_id": elem.attrib.get("resource-id", ""),
            "class_name": elem.attrib.get("class", ""),
            "text": elem.attrib.get("text", ""),
            "content_desc": elem.attrib.get("content-desc", ""),
            "clickable": elem.attrib.get("clickable", "false") == "true",
            "long_clickable": elem.attrib.get("long-clickable", "false") == "true",
            "enabled": elem.attrib.get("enabled", "true") == "true",
            "bounds": elem.attrib.get("bounds", ""),
        }
        blob = " ".join(
            part.lower()
            for part in (
                node["resource_id"],
                node["class_name"],
                node["text"],
                node["content_desc"],
            )
            if part
        )
        node["voice_related"] = any(k in blob for k in VOICE_KEYWORDS)
        nodes.append(node)
    return nodes


def bounds_center(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def pick_voice_button(nodes: list[dict]) -> dict | None:
    candidates = [
        n
        for n in nodes
        if n.get("enabled")
        and (n.get("clickable") or n.get("long_clickable"))
        and n.get("voice_related")
    ]
    if not candidates:
        candidates = [
            n
            for n in nodes
            if n.get("enabled") and (n.get("clickable") or n.get("long_clickable"))
        ]
    if not candidates:
        return None
    # Prefer long-click (hold to talk) or explicit mic/record ids.
    candidates.sort(
        key=lambda n: (
            0 if n.get("long_clickable") else 1,
            0 if any(k in (n.get("resource_id", "") + n.get("text", "")).lower() for k in ("mic", "record", "语音", "录音")) else 1,
        )
    )
    return candidates[0]


def tap(serial: str, x: int, y: int) -> None:
    adb(serial, "shell", "input", "tap", str(x), str(y))


def dismiss_anr(serial: str) -> None:
    xml = dump_ui(serial)
    if "isn't responding" not in xml and "aerr_wait" not in xml:
        return
    m = re.search(r'resource-id="android:id/aerr_wait"[^>]*bounds="([^"]+)"', xml)
    if m:
        center = bounds_center(m.group(1))
        if center:
            tap(serial, *center)
            time.sleep(2)


def collect_visible_texts(nodes: list[dict]) -> list[str]:
    texts: list[str] = []
    for node in nodes:
        for key in ("text", "content_desc"):
            value = (node.get(key) or "").strip()
            if value and value not in texts:
                texts.append(value)
    return texts


def inject_voice(serial: str, wav_path: Path, out_dir: Path, record_sec: float = 15.0) -> dict:
    launch_app(serial)
    dismiss_anr(serial)
    screenshot(serial, out_dir, "01_launch")

    if not linux_audio.is_ready():
        print("  virtual mic not ready; running ensure_virtual_mic...")
    mic = linux_audio.ensure_virtual_mic(serial=serial, force=False)
    print(
        f"  mic ensure: ready_before={mic.get('ready_before')} "
        f"ready_after={mic.get('ready_after')} fixed={mic.get('fixed')}"
    )
    if not mic.get("ready_after"):
        raise RuntimeError(
            "Virtual mic still not ready after ensure. See setup_virtual_mic.sh / PulseAudio."
        )

    xml = dump_ui(serial)
    (out_dir / "ui_before.xml").write_text(xml, encoding="utf-8")
    nodes = parse_nodes(xml)
    btn = pick_voice_button(nodes)
    if not btn:
        raise RuntimeError("No clickable voice button found. See ui_before.xml")

    center = bounds_center(btn.get("bounds", ""))
    if not center:
        raise RuntimeError(f"Voice button has no bounds: {btn}")

    print(f"  voice button: id={btn.get('resource_id')} text={btn.get('text')!r} bounds={btn.get('bounds')}")
    tap(serial, *center)
    time.sleep(1.0)
    screenshot(serial, out_dir, "02_recording")

    stop_play = threading.Event()
    player = linux_audio.play_loop_async(wav_path, stop_play)
    recorder: threading.Thread | None = None
    reply_wav = out_dir / "reply_recorded.wav"
    record_error = ""

    def _record() -> None:
        nonlocal record_error
        try:
            linux_audio.record_default_sink(reply_wav, record_sec)
        except Exception as exc:  # noqa: BLE001
            record_error = str(exc)

    recorder = threading.Thread(target=_record, daemon=True)
    recorder.start()

    before_texts = set(collect_visible_texts(nodes))
    seen_text = ""
    deadline = time.time() + record_sec
    try:
        while time.time() < deadline:
            dismiss_anr(serial)
            xml = dump_ui(serial)
            texts = collect_visible_texts(parse_nodes(xml))
            for text in texts:
                if text in before_texts:
                    continue
                if len(text) >= 4 and text not in seen_text:
                    seen_text = text
                    print(f"  ui text: {text[:100]}")
            time.sleep(0.5)
    finally:
        stop_play.set()
        player.join(timeout=10)
        if recorder:
            recorder.join(timeout=record_sec + 5)

    tap(serial, *center)
    time.sleep(2)
    screenshot(serial, out_dir, "03_after")
    xml_after = dump_ui(serial)
    (out_dir / "ui_after.xml").write_text(xml_after, encoding="utf-8")

    pulled = reply_wav.is_file() and reply_wav.stat().st_size > 1000
    if not pulled:
        print(f"  warn: reply wav not captured ({record_error or 'too small'})")

    return {
        "voice_button": btn,
        "seen_ui_text": seen_text,
        "reply_wav": str(reply_wav) if pulled else None,
        "reply_capture_error": record_error or None,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="AISF voice E2E on Linux ARM emulator")
    parser.add_argument("--serial", default=os.environ.get("AISF_SERIAL", "emulator-5554"))
    parser.add_argument("-i", "--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--record-sec", type=float, default=18.0)
    args = parser.parse_args()

    if not args.input.is_file():
        print(f"Input wav missing: {args.input}", file=sys.stderr)
        return 1

    args.output_dir.mkdir(parents=True, exist_ok=True)
    print(f"AISF voice test -> {args.output_dir}")
    print(f"  package: {PACKAGE}")
    print(f"  input  : {args.input}")

    try:
        result = inject_voice(args.serial, args.input, args.output_dir, args.record_sec)
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL: {exc}", file=sys.stderr)
        payload = {"ok": False, "error": str(exc), "at": datetime.now(timezone.utc).isoformat()}
        (args.output_dir / "result.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return 1

    payload = {
        "ok": True,
        "package": PACKAGE,
        "input_wav": str(args.input),
        "at": datetime.now(timezone.utc).isoformat(),
        **result,
    }
    (args.output_dir / "result.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("PASS (see result.json)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
