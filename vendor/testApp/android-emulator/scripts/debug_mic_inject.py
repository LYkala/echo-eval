#!/usr/bin/env python3
"""Quick debug: VB-Cable inject -> emulator ASR text on screen."""

from __future__ import annotations

import re
import subprocess
import sys
import threading
import time
import wave
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import vbcable_play  # noqa: E402

ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL = "emulator-5554"
PACKAGE = "com.testappgui.voiceassistant"
ACTIVITY = f"{PACKAGE}/com.k2fsa.sherpa.onnx.MainActivity"
WAV = ROOT / "audio_samples" / "weather_question.wav"


def adb(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [str(ADB), "-s", SERIAL, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def find_text(xml: str, suffix: str) -> str:
    rid = re.escape(f"{PACKAGE}:id/{suffix}")
    for pat in (
        rf'resource-id="{rid}"[^>]*text="([^"]*)"',
        rf'text="([^"]*)"[^>]*resource-id="{rid}"',
    ):
        m = re.search(pat, xml)
        if m:
            return m.group(1).strip()
    return ""


def find_center(xml: str, suffix: str) -> tuple[int, int] | None:
    rid = re.escape(f"{PACKAGE}:id/{suffix}")
    m = re.search(rf'resource-id="{rid}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/debug_ui.xml")
    text = (adb("shell", "cat", "/sdcard/debug_ui.xml").stdout or "")
    if PACKAGE not in text:
        adb("shell", "am", "start", "-W", "-n", ACTIVITY, check=False)
        time.sleep(1)
        adb("shell", "uiautomator", "dump", "/sdcard/debug_ui.xml")
        text = (adb("shell", "cat", "/sdcard/debug_ui.xml").stdout or "")
    return text


def to_16k(src: Path, dest: Path) -> Path:
    with wave.open(str(src), "rb") as w_in:
        raw = w_in.readframes(w_in.getnframes())
        rate = w_in.getframerate()
    samples = list(struct.unpack("<" + "h" * (len(raw) // 2), raw))
    if rate != 16000:
        ratio = rate / 16000
        out_len = max(1, int(len(samples) / ratio))
        resampled = []
        for i in range(out_len):
            pos = i * ratio
            idx = int(pos)
            frac = pos - idx
            if idx + 1 < len(samples):
                resampled.append(int(samples[idx] * (1 - frac) + samples[idx + 1] * frac))
            else:
                resampled.append(samples[min(idx, len(samples) - 1)])
        samples = resampled
    dest.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(dest), "wb") as w_out:
        w_out.setnchannels(1)
        w_out.setsampwidth(2)
        w_out.setframerate(16000)
        w_out.writeframes(struct.pack("<" + "h" * len(samples), *samples))
    return dest


def main() -> int:
    print("VB-Cable:", vbcable_play.status())
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    for i in range(60):
        if "就绪" in find_text(ui(), "status_text"):
            print(f"ready at {i * 2}s")
            break
        time.sleep(2)
    else:
        print("model not ready")
        return 1

    wav16 = to_16k(WAV, ROOT / "app_apk_test" / "_debug_input_16k.wav")
    vbcable_play.configure_emulator_mic_inject()

    center = find_center(ui(), "record_button")
    print("tap record", center)
    adb("shell", "input", "tap", str(center[0]), str(center[1]))
    time.sleep(1.0)

    stop = threading.Event()

    def loop() -> None:
        while not stop.is_set():
            vbcable_play.play_to_cable(wav16)
            if stop.wait(0.2):
                break

    th = threading.Thread(target=loop, daemon=True)
    th.start()
    got = ""
    for i in range(30):
        asr = find_text(ui(), "asr_text")
        st = find_text(ui(), "status_text")
        print(f"{i:02d} status={st[:40]!r} asr={asr[:50]!r}")
        if asr and "说完" not in asr and "停止" not in asr[:6]:
            got = asr
            break
        time.sleep(0.5)
    stop.set()
    th.join(timeout=8)
    print("RESULT:", got or "(empty)")
    return 0 if got else 2


if __name__ == "__main__":
    raise SystemExit(main())
