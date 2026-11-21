#!/usr/bin/env python3
"""End-to-end test: voice assistant APK on emulator with wav inject + screenshots."""

from __future__ import annotations

import argparse
import json
import re
import struct
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
from pathlib import Path

try:
    import vbcable_play
except ImportError:
    vbcable_play = None  # type: ignore

ROOT = Path(__file__).resolve().parent
AUDIO = ROOT / "audio_samples" / "weather_question.wav"
PACKAGE = "com.testappgui.voiceassistant"
ACTIVITY = "com.testappgui.voiceassistant/com.k2fsa.sherpa.onnx.MainActivity"
ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL = "emulator-5554"


def adb(*args: str, check: bool = True, text: bool = True) -> subprocess.CompletedProcess:
    cmd = [str(ADB), "-s", SERIAL, *args]
    if text:
        return subprocess.run(cmd, capture_output=True, text=True, check=check, encoding="utf-8")
    return subprocess.run(cmd, capture_output=True, check=check)


def screenshot(out_dir: Path, name: str) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    dest = out_dir / f"{name}.png"
    proc = subprocess.run(
        [str(ADB), "-s", SERIAL, "exec-out", "screencap", "-p"],
        capture_output=True,
        check=True,
    )
    dest.write_bytes(proc.stdout)
    print(f"  screenshot -> {dest.name}")
    return dest


def dump_ui() -> str:
    remote = "/sdcard/voice_assistant_ui.xml"
    xml = ""
    for _ in range(4):
        adb("shell", "uiautomator", "dump", remote, check=False)
        proc = adb("shell", "cat", remote, check=False, text=False)
        data = proc.stdout or b""
        xml = data.decode("utf-8", errors="replace")
        if PACKAGE in xml:
            return xml
        ensure_voice_app_foreground()
        time.sleep(2)
    return xml


def dismiss_blocking_dialogs() -> None:
    for _ in range(5):
        xml = dump_ui()
        if "isn't responding" in xml or "android:id/aerr_wait" in xml:
            wait_btn = find_center(xml, "android:id/aerr_wait")
            if wait_btn:
                tap(*wait_btn)
            else:
                adb("shell", "input", "keyevent", "4", check=False)
            time.sleep(2)
            continue
        if PACKAGE in xml:
            return
        time.sleep(1)


def launch_voice_app() -> None:
    adb("shell", "pm", "grant", PACKAGE, "android.permission.RECORD_AUDIO", check=False)
    adb("shell", "appops", "set", PACKAGE, "RECORD_AUDIO", "allow", check=False)
    adb("shell", "am", "force-stop", PACKAGE, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, check=False)
    dismiss_blocking_dialogs()
    time.sleep(2)


def ensure_voice_app_foreground() -> None:
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, check=False)
    time.sleep(1)


def find_node_attr(xml_text: str, resource_id: str, attr: str) -> str:
    rid = re.escape(resource_id)
    for pattern in (
        rf'resource-id="{rid}"[^>]*{attr}="([^"]*)"',
        rf'{attr}="([^"]*)"[^>]*resource-id="{rid}"',
    ):
        m = re.search(pattern, xml_text)
        if m:
            return m.group(1).strip()
    return ""


def find_center(xml_text: str, resource_id: str) -> tuple[int, int] | None:
    bounds = find_node_attr(xml_text, resource_id, "bounds")
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def find_text(xml_text: str, resource_id: str) -> str:
    return find_node_attr(xml_text, resource_id, "text")


ASR_PLACEHOLDER = "说完后点「停止」"
LLM_PLACEHOLDER = "等待大模型回复"


def is_real_asr(text: str) -> bool:
    text = text.strip()
    return bool(text) and text != ASR_PLACEHOLDER and "停止" not in text[:8]


def is_real_llm(text: str) -> bool:
    text = text.strip()
    return bool(text) and LLM_PLACEHOLDER not in text and len(text) > 12


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))


def play_wav(path: Path, *, use_vbcable: bool = True) -> None:
    if use_vbcable and vbcable_play and vbcable_play.is_ready():
        vbcable_play.play_to_cable(path)
        return
    ps = f"""
$path = '{path}'
$player = New-Object System.Media.SoundPlayer $path
$player.PlaySync()
"""
    subprocess.run(["powershell", "-NoProfile", "-Command", ps], check=True)


def play_wav_loop(path: Path, stop_event: threading.Event, *, use_vbcable: bool = True) -> None:
    while not stop_event.is_set():
        play_wav(path, use_vbcable=use_vbcable)
        if stop_event.wait(0.25):
            break


def play_wav_loop_async(path: Path, stop_event: threading.Event, *, use_vbcable: bool = True) -> threading.Thread:
    t = threading.Thread(
        target=play_wav_loop,
        args=(path, stop_event),
        kwargs={"use_vbcable": use_vbcable},
        daemon=True,
    )
    t.start()
    return t


def record_button_enabled(xml: str, record_id: str) -> bool:
    rid = re.escape(record_id)
    m = re.search(rf'resource-id="{rid}"[^>]*enabled="(true|false)"', xml)
    if m:
        return m.group(1) == "true"
    m = re.search(rf'enabled="(true|false)"[^>]*resource-id="{rid}"', xml)
    return bool(m and m.group(1) == "true")


def inject_wav_while_recording(
    inject_audio: Path,
    record_id: str,
    *,
    record_sec: float = 12.0,
) -> str:
    """Tap record, play wav into VB-Cable mic, poll live ASR, then stop recording."""
    if not vbcable_play or not vbcable_play.is_ready():
        raise RuntimeError("VB-Cable not ready")

    if not wait_for_ready(timeout_s=30.0):
        raise RuntimeError("App not ready before mic inject")

    xml = dump_ui()
    center = find_center(xml, record_id)
    if not center:
        raise RuntimeError("record_button not found")
    if not record_button_enabled(xml, record_id):
        raise RuntimeError("record_button disabled (model not ready)")

    tap(*center)
    time.sleep(1.0)

    status = find_text(dump_ui(), f"{PACKAGE}:id/status_text")
    print(f"  recording status: {status[:60] if status else '(empty)'}")
    if "正在聆听" not in status and "聆听" not in status:
        print("  warn: record may not have started; retry tap")
        tap(*center)
        time.sleep(1.0)

    stop_play = threading.Event()
    player = play_wav_loop_async(inject_audio, stop_play, use_vbcable=True)

    live_asr = ""
    deadline = time.time() + record_sec
    try:
        while time.time() < deadline:
            dismiss_blocking_dialogs()
            xml = dump_ui()
            text = find_text(xml, f"{PACKAGE}:id/asr_text")
            if is_real_asr(text):
                live_asr = text
                print(f"  live ASR: {text[:80]}")
                break
            time.sleep(0.4)
    finally:
        stop_play.set()
        player.join(timeout=10)

    xml = dump_ui()
    stop_center = find_center(xml, record_id)
    if stop_center:
        tap(*stop_center)
    time.sleep(2.0)
    return live_asr


import wave


def resample_for_mic(src: Path, out_dir: Path) -> Path:
    """16 kHz mono PCM wav matches the app's AudioRecord settings."""
    dest = out_dir / "input_16k.wav"
    with wave.open(str(src), "rb") as w_in:
        in_rate = w_in.getframerate()
        channels = w_in.getnchannels()
        width = w_in.getsampwidth()
        raw = w_in.readframes(w_in.getnframes())
    if width != 2:
        return src
    samples = list(struct.unpack("<" + "h" * (len(raw) // 2), raw))
    if channels == 2:
        samples = [(samples[i] + samples[i + 1]) // 2 for i in range(0, len(samples), 2)]
    elif channels != 1:
        return src
    if in_rate != 16000:
        ratio = in_rate / 16000
        out_len = max(1, int(len(samples) / ratio))
        resampled: list[int] = []
        for i in range(out_len):
            pos = i * ratio
            idx = int(pos)
            frac = pos - idx
            if idx + 1 < len(samples):
                value = int(samples[idx] * (1 - frac) + samples[idx + 1] * frac)
            else:
                value = samples[min(idx, len(samples) - 1)]
            resampled.append(max(-32768, min(32767, value)))
        samples = resampled
    with wave.open(str(dest), "wb") as w_out:
        w_out.setnchannels(1)
        w_out.setsampwidth(2)
        w_out.setframerate(16000)
        w_out.writeframes(struct.pack("<" + "h" * len(samples), *samples))
    return dest


def clear_device_output_wav() -> None:
    adb(
        "shell",
        "run-as",
        PACKAGE,
        "rm",
        "-f",
        "files/voice_lab/output.wav",
        check=False,
    )


def pull_reply_wav(out_dir: Path, timeout_s: float = 90.0) -> bool:
    remotes = (
        "/sdcard/voice_lab/output.wav",
        f"/sdcard/Android/data/{PACKAGE}/files/voice_lab/output.wav",
    )
    dest = out_dir / "output.wav"
    run_as_path = "files/voice_lab/output.wav"
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        proc = adb(
            "exec-out",
            "run-as",
            PACKAGE,
            "cat",
            run_as_path,
            check=False,
            text=False,
        )
        data = proc.stdout or b""
        if len(data) > 1000:
            dest.write_bytes(data)
            print(f"  pulled run-as:{run_as_path} -> output.wav ({len(data)} bytes)")
            return True
        for remote in remotes:
            adb("pull", remote, str(dest), check=False)
            if dest.is_file() and dest.stat().st_size > 1000:
                print(f"  pulled {remote} -> output.wav ({dest.stat().st_size} bytes)")
                return True
        time.sleep(2)
    return False


def wait_for_ready(timeout_s: float = 180.0) -> bool:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        dismiss_blocking_dialogs()
        xml = dump_ui()
        if PACKAGE not in xml:
            ensure_voice_app_foreground()
            time.sleep(2)
            continue
        status = find_text(xml, f"{PACKAGE}:id/status_text")
        if status:
            print(f"  status: {status[:60]}")
        if "就绪" in status:
            return True
        if "失败" in status:
            return False
        time.sleep(2)
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description="Test voice assistant APK on emulator")
    parser.add_argument(
        "--real-mic",
        action="store_true",
        help="Use VB-Cable + hostmicon for real wav ASR (requires setup_virtual_mic.ps1)",
    )
    parser.add_argument(
        "--allow-demo-fallback",
        action="store_true",
        help="If mic ASR fails, tap weather demo button",
    )
    parser.add_argument(
        "--skip-install",
        action="store_true",
        help="Skip APK reinstall (keep current app session)",
    )
    parser.add_argument("-i", "--input", type=Path, default=AUDIO, help="Input wav path")
    args = parser.parse_args()
    input_audio = args.input.resolve()

    if not ADB.is_file():
        print(f"adb not found: {ADB}")
        return 1
    if not input_audio.is_file():
        print(f"missing input wav: {input_audio}")
        return 1

    use_vbcable = bool(args.real_mic and vbcable_play and vbcable_play.is_ready())
    if args.real_mic and not use_vbcable:
        print("  VB-Cable not ready. Run: .\\scripts\\setup_virtual_mic.ps1 -ConfigureOnly")

    ts = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    out_dir = ROOT / "app_apk_test" / f"weather_{ts}"
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Output: {out_dir}")

    print("\n[0] Disable legacy Sherpa demo app (avoid stealing focus)")
    adb("shell", "pm", "disable-user", "--user", "0", "com.k2fsa.sherpa.onnx", check=False)

    print("\n[1] Push input wav")
    adb("shell", "mkdir", "-p", "/sdcard/voice_lab")
    adb("push", str(input_audio), "/sdcard/voice_lab/weather_question.wav")
    (out_dir / "input_pushed.txt").write_text(
        f"remote=/sdcard/voice_lab/weather_question.wav\nlocal={input_audio}\n"
        f"vbcable={'yes' if use_vbcable else 'no'}\n",
        encoding="utf-8",
    )

    print("\n[2] Launch voice assistant app")
    clear_device_output_wav()
    launch_voice_app()
    screenshot(out_dir, "01_launch")

    if use_vbcable:
        print("\n[2b] Configure VB-Cable + hostmicon (before model wait)")
        vbcable_play.configure_default_microphone()
        vbcable_play.ensure_hostmicon_enabled()

    print("\n[3] Wait for Sherpa model ready")
    if not wait_for_ready(timeout_s=180.0):
        screenshot(out_dir, "02_model_timeout")
        print("Model did not become ready in time")
        return 1
    screenshot(out_dir, "02_ready")

    xml = dump_ui()
    record_id = f"{PACKAGE}:id/record_button"
    center = find_center(xml, record_id)
    if not center:
        print("record_button not found")
        return 1

    print("\n[4] Start recording + play input wav into mic")
    live_asr = ""
    if use_vbcable:
        print("  VB-Cable: CABLE Output default mic + hostmicon")
        inject_audio = resample_for_mic(input_audio, out_dir)
        print(f"  inject wav (16 kHz): {inject_audio.name}")
        screenshot(out_dir, "03_recording_start")
        try:
            live_asr = inject_wav_while_recording(inject_audio, record_id, record_sec=12.0)
        except Exception as ex:
            print(f"  inject failed: {ex}")
        screenshot(out_dir, "03_recording")
    else:
        xml = dump_ui()
        center = find_center(xml, record_id)
        if not center:
            print("record_button not found")
            return 1
        tap(*center)
        time.sleep(0.5)
        screenshot(out_dir, "03_recording")

    print("\n[5] Kimi -> TTS")
    xml = dump_ui()
    asr_text = find_text(xml, f"{PACKAGE}:id/asr_text")
    if not is_real_asr(asr_text):
        asr_text = live_asr
    if not is_real_asr(asr_text) and args.allow_demo_fallback:
        print("  mic ASR empty, fallback to weather demo button")
        xml = dump_ui()
        if "正在聆听" in find_text(xml, f"{PACKAGE}:id/status_text"):
            stop_center = find_center(xml, record_id)
            if stop_center:
                tap(*stop_center)
                time.sleep(2)
                xml = dump_ui()
        demo_center = find_center(xml, f"{PACKAGE}:id/demo_button")
        if demo_center:
            tap(*demo_center)
            time.sleep(1)
            screenshot(out_dir, "03b_demo_fallback")
            asr_text = "今天天气怎么样"
    elif not is_real_asr(asr_text):
        print("  mic ASR empty (use --allow-demo-fallback or fix VB-Cable route)")

    deadline = time.time() + 120
    llm_text = ""
    while time.time() < deadline:
        dismiss_blocking_dialogs()
        xml = dump_ui()
        asr_text = find_text(xml, f"{PACKAGE}:id/asr_text")
        llm_text = find_text(xml, f"{PACKAGE}:id/llm_text")
        status = find_text(xml, f"{PACKAGE}:id/status_text")
        if is_real_llm(llm_text) and ("voice_lab" in status or "files/voice_lab" in status):
            break
        time.sleep(2)

    screenshot(out_dir, "04_result")
    (out_dir / "asr.txt").write_text(asr_text or "(empty)", encoding="utf-8")
    (out_dir / "llm.txt").write_text(llm_text or "(empty)", encoding="utf-8")
    print(f"  ASR: {asr_text[:80] if asr_text else '(empty)'}")
    print(f"  LLM: {llm_text[:80] if llm_text else '(empty)'}")

    print("\n[6] Pull reply wav")
    pulled = pull_reply_wav(out_dir, timeout_s=90.0)
    if not pulled:
        print("  output.wav not found on device (TTS save may have failed)")

    if use_vbcable and vbcable_play:
        vbcable_play.restore_default_devices()

    result = {
        "input": str(input_audio),
        "input_remote": "/sdcard/voice_lab/weather_question.wav",
        "asr": asr_text,
        "llm": llm_text,
        "output_wav": str(out_dir / "output.wav") if pulled else None,
        "screenshots": sorted(p.name for p in out_dir.glob("*.png")),
    }
    import json

    (out_dir / "result.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    ok = is_real_asr(asr_text) and is_real_llm(llm_text) and pulled
    print(f"\n{'PASS' if ok else 'PARTIAL'} -> {out_dir}")
    return 0 if ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
