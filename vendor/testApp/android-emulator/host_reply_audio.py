#!/usr/bin/env python3
"""Host-side reply audio without App synthesizeToFile export.

Two alternate paths:
  1. sapi  - Re-synthesize LLM text with Windows SAPI (offline, no emulator audio)
  2. record - Record emulator TTS playback from host loopback while App speaks only
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
PACKAGE = "com.testappgui.voiceassistant"
ACTIVITY = "com.testappgui.voiceassistant/com.k2fsa.sherpa.onnx.MainActivity"
SERIAL = "emulator-5554"
SCRIPTS = ROOT / "scripts"
UTILS = SCRIPTS / "vbcable_utils.ps1"
APK = ROOT / "voice-assistant-app" / "VoiceAssistant-debug-x86_64.apk"

DEFAULT_LLM = (
    "抱歉，我无法获取实时天气数据。"
    "建议您查看手机天气应用或搜索当地气象信息，出门前记得关注温度和是否需要带伞。"
)


def adb(*args: str, check: bool = True, text: bool = True) -> subprocess.CompletedProcess:
    cmd = [str(ADB), "-s", SERIAL, *args]
    if text:
        return subprocess.run(cmd, capture_output=True, text=True, check=check, encoding="utf-8")
    return subprocess.run(cmd, capture_output=True, check=check)


def sapi_synthesize(text: str, out_wav: Path) -> Path:
    out_wav.parent.mkdir(parents=True, exist_ok=True)
    text_file = out_wav.with_suffix(".tts_input.txt")
    text_file.write_text(text, encoding="utf-8")
    ps = f"""
Add-Type -AssemblyName System.Speech
$text = Get-Content -LiteralPath '{text_file}' -Raw -Encoding UTF8
$syn = New-Object System.Speech.Synthesis.SpeechSynthesizer
$syn.SetOutputToWaveFile('{out_wav}')
$syn.Speak($text.Trim())
$syn.Dispose()
"""
    proc = subprocess.run(
        ["powershell", "-NoProfile", "-Command", ps],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    text_file.unlink(missing_ok=True)
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or proc.stdout or "SAPI failed").strip())
    if not out_wav.is_file() or out_wav.stat().st_size < 1000:
        raise RuntimeError(f"SAPI did not produce wav: {out_wav}")
    return out_wav


def find_ffmpeg() -> Path | None:
    ps = f"""
$ErrorActionPreference = 'SilentlyContinue'
. '{UTILS.as_posix().replace("'", "''")}'
(Find-Ffmpeg)
"""
    proc = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    line = (proc.stdout or "").strip().splitlines()
    if line and line[0] and Path(line[0]).is_file():
        return Path(line[0])
    return None


def pick_loopback_device(ffmpeg: Path) -> str | None:
    env = os.environ.copy()
    env["PATH"] = str(ffmpeg.parent) + os.pathsep + env.get("PATH", "")
    proc = subprocess.run(
        [str(ffmpeg), "-hide_banner", "-f", "dshow", "-list_devices", "true", "-i", "dummy"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
    )
    text = proc.stderr or proc.stdout or ""
    for line in text.splitlines():
        if "(audio)" not in line.lower():
            continue
        if "cable output" in line.lower():
            m = re.search(r'"([^"]+)"', line)
            if m:
                return f"audio={m.group(1)}"
    return None


def configure_playback_to_cable() -> None:
    ps = f"""
$ErrorActionPreference = 'Stop'
. '{UTILS.as_posix().replace("'", "''")}'
Ensure-AudioDeviceCmdlets
Save-DefaultAudioDevices
$dev = Get-CableDeviceNames
if (-not $dev.Playback) {{ throw 'CABLE Input not found' }}
$pb = Get-AudioDevice -List | Where-Object {{ $_.Type -eq 'Playback' -and $_.Name -eq $dev.Playback }} | Select-Object -First 1
if (-not $pb) {{ throw 'playback device missing' }}
Set-AudioDevice -Index $pb.Index | Out-Null
Write-Host "Default playback -> $($dev.Playback)"
"""
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps],
        check=True,
    )


def restore_default_devices() -> None:
    ps = f"""
$ErrorActionPreference = 'Stop'
. '{UTILS.as_posix().replace("'", "''")}'
Restore-DefaultAudioDevices
"""
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps],
        check=False,
    )


def wav_peak(path: Path) -> int:
    import struct
    import wave

    with wave.open(str(path), "rb") as w:
        raw = w.readframes(w.getnframes())
    if w.getsampwidth() != 2:
        return 0
    samples = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    return max(abs(s) for s in samples) if samples else 0


def wav_loud_ratio(path: Path, threshold: int = 800) -> float:
    import struct
    import wave

    with wave.open(str(path), "rb") as w:
        raw = w.readframes(w.getnframes())
    if w.getsampwidth() != 2 or not raw:
        return 0.0
    samples = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    loud = sum(1 for s in samples if abs(s) > threshold)
    return loud / len(samples)


def ensure_app_installed() -> None:
    proc = adb("shell", "pm", "path", PACKAGE, check=False)
    if PACKAGE in (proc.stdout or ""):
        return
    if not APK.is_file():
        raise RuntimeError(
            f"Voice assistant APK missing: {APK}. "
            "Run .\\scripts\\build_voice_assistant_app.ps1 first."
        )
    print(f"  installing {APK.name} ...")
    proc = adb("install", "-r", str(APK), check=False)
    out = (proc.stdout or "") + (proc.stderr or "")
    if proc.returncode != 0 or "Failure" in out:
        raise RuntimeError(f"adb install failed: {out.strip()}")


def dump_ui_xml() -> str:
    remote = "/sdcard/host_reply_ui.xml"
    xml = ""
    for _ in range(4):
        adb("shell", "uiautomator", "dump", remote, check=False)
        proc = adb("shell", "cat", remote, check=False, text=False)
        xml = (proc.stdout or b"").decode("utf-8", errors="replace")
        if PACKAGE in xml:
            return xml
        adb("shell", "am", "start", "-W", "-n", ACTIVITY, check=False)
        time.sleep(1.5)
    return xml


def dump_ui_text(resource_suffix: str) -> str:
    rid = f"{PACKAGE}:id/{resource_suffix}"
    escaped = re.escape(rid)
    xml = dump_ui_xml()
    for pattern in (
        rf'resource-id="{escaped}"[^>]*text="([^"]*)"',
        rf'text="([^"]*)"[^>]*resource-id="{escaped}"',
    ):
        m = re.search(pattern, xml)
        if m:
            return m.group(1).strip()
    return ""


def find_center(xml: str, resource_id: str) -> tuple[int, int] | None:
    escaped = re.escape(resource_id)
    bounds = ""
    for pattern in (
        rf'resource-id="{escaped}"[^>]*bounds="([^"]*)"',
        rf'bounds="([^"]*)"[^>]*resource-id="{escaped}"',
    ):
        m = re.search(pattern, xml)
        if m:
            bounds = m.group(1)
            break
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def dismiss_blocking_dialogs() -> None:
    for _ in range(4):
        xml = dump_ui_xml()
        if "System UI isn't responding" in xml or "android:id/aerr_wait" in xml:
            wait_btn = find_center(xml, "android:id/aerr_wait")
            if wait_btn:
                adb("shell", "input", "tap", str(wait_btn[0]), str(wait_btn[1]), check=False)
            else:
                adb("shell", "input", "keyevent", "4", check=False)
            time.sleep(2)
            continue
        if PACKAGE in xml:
            return
        time.sleep(1)


def prepare_voice_app() -> None:
    adb("shell", "pm", "disable-user", "--user", "0", "com.k2fsa.sherpa.onnx", check=False)
    adb("shell", "pm", "grant", PACKAGE, "android.permission.RECORD_AUDIO", check=False)
    adb("shell", "appops", "set", PACKAGE, "RECORD_AUDIO", "allow", check=False)
    adb("shell", "am", "force-stop", PACKAGE, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, check=False)
    dismiss_blocking_dialogs()
    for _ in range(30):
        dismiss_blocking_dialogs()
        status = dump_ui_text("status_text")
        if status and "初始化" not in status:
            return
        time.sleep(2)


def tap_demo_and_wait_for_speak() -> str:
    prepare_voice_app()
    demo_id = f"{PACKAGE}:id/demo_button"
    tapped = False
    deadline = time.time() + 150
    llm_text = ""
    while time.time() < deadline:
        dismiss_blocking_dialogs()
        xml = dump_ui_xml()
        if not tapped:
            center = find_center(xml, demo_id)
            if center:
                adb("shell", "input", "tap", str(center[0]), str(center[1]), check=False)
                tapped = True
                time.sleep(2)
        status = dump_ui_text("status_text")
        llm_text = dump_ui_text("llm_text")
        if "files/voice_lab/output.wav" in status or "voice_lab/output.wav" in status:
            return llm_text
        if "TTS 保存失败，正在播报" in status:
            return llm_text
        time.sleep(1)
    raise RuntimeError(
        f"Timed out waiting for TTS (Kimi/synth). last_status={dump_ui_text('status_text')!r}"
    )


def pull_app_output_wav(dest: Path) -> bool:
    proc = adb(
        "exec-out",
        "run-as",
        PACKAGE,
        "cat",
        "files/voice_lab/output.wav",
        check=False,
        text=False,
    )
    data = proc.stdout or b""
    if len(data) < 1000:
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    return True


def play_wav_to_cable(wav: Path) -> None:
    ps = f"""
$ErrorActionPreference = 'Stop'
. '{UTILS.as_posix().replace("'", "''")}'
Play-WavToCableInput -AudioPath '{wav.as_posix()}'
"""
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps],
        check=True,
    )


def record_loopback_wav(
    ffmpeg: Path, device: str, out_wav: Path, duration_s: float, play_wav: Path | None = None
) -> tuple[int, float]:
    out_wav.parent.mkdir(parents=True, exist_ok=True)
    proc = record_playback_async(ffmpeg, device, out_wav, duration_s)
    if play_wav:
        time.sleep(1)
        play_wav_to_cable(play_wav)
    rc = proc.wait(timeout=duration_s + 20)
    stderr = (proc.stderr.read() if proc.stderr else b"").decode("utf-8", errors="replace")
    if rc != 0:
        raise RuntimeError(stderr.strip() or f"ffmpeg record failed ({rc})")
    if not out_wav.is_file() or out_wav.stat().st_size < 1000:
        raise RuntimeError("Recorded wav empty")
    return wav_peak(out_wav), wav_loud_ratio(out_wav)


def record_playback_async(
    ffmpeg: Path, device: str, out_wav: Path, duration_s: float
) -> subprocess.Popen:
    out_wav.parent.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env["PATH"] = str(ffmpeg.parent) + os.pathsep + env.get("PATH", "")
    cmd = [
        str(ffmpeg),
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-f",
        "dshow",
        "-i",
        device,
        "-t",
        str(duration_s),
        str(out_wav),
    ]
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)


def run_sapi(text: str, out_dir: Path) -> dict:
    out_wav = out_dir / "output_host_sapi.wav"
    t0 = time.time()
    sapi_synthesize(text, out_wav)
    return {
        "mode": "host-sapi-resynth",
        "text_source": "argument",
        "output_wav": str(out_wav),
        "bytes": out_wav.stat().st_size,
        "elapsed_s": round(time.time() - t0, 2),
    }


def wav_duration_seconds(path: Path) -> float:
    import wave

    with wave.open(str(path), "rb") as w:
        if w.getframerate() <= 0:
            return 15.0
        return w.getnframes() / w.getframerate()


def run_record(out_dir: Path, duration_s: float) -> dict:
    ffmpeg = find_ffmpeg()
    if not ffmpeg:
        raise RuntimeError("ffmpeg not found; run .\\scripts\\install_ffmpeg.ps1")
    device = pick_loopback_device(ffmpeg)
    if not device:
        raise RuntimeError("No dshow capture device found (need VB-Cable)")
    out_wav = out_dir / "output_host_record.wav"

    configure_playback_to_cable()
    ensure_app_installed()

    print("  launch app + demo, wait for App TTS wav ...")
    t0 = time.time()
    llm_text = tap_demo_and_wait_for_speak()

    pulled = out_dir / "_app_tts_src.wav"
    pull_deadline = time.time() + 45
    while time.time() < pull_deadline:
        if pull_app_output_wav(pulled):
            break
        time.sleep(2)
    else:
        restore_default_devices()
        raise RuntimeError("App TTS output.wav not available via run-as")

    play_sec = max(duration_s, wav_duration_seconds(pulled) + 2.0)
    print(f"  record VB-Cable loopback for {play_sec:.0f}s while playing App TTS ...")
    peak, loud_ratio = record_loopback_wav(ffmpeg, device, out_wav, play_sec, play_wav=pulled)
    restore_default_devices()

    if peak < 3000 or loud_ratio < 0.02:
        raise RuntimeError(
            f"Recorded audio lacks TTS content (peak={peak}, loud_ratio={loud_ratio:.4f}). "
            "Check VB-Cable loopback (scripts/test_vbcable_loopback.py)."
        )

    return {
        "mode": "host-loopback-record",
        "capture_device": device,
        "route": "App TTS wav -> CABLE Input playback -> CABLE Output dshow record",
        "llm_on_screen": llm_text,
        "wav_peak": peak,
        "wav_loud_ratio": round(loud_ratio, 4),
        "record_sec": play_sec,
        "output_wav": str(out_wav),
        "bytes": out_wav.stat().st_size,
        "elapsed_s": round(time.time() - t0, 2),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Host-side reply audio alternatives")
    parser.add_argument(
        "--mode",
        choices=("sapi", "record", "both"),
        default="both",
        help="sapi=re-synthesize on host; record=capture emulator playback",
    )
    parser.add_argument("--text", default=DEFAULT_LLM, help="Text for SAPI mode")
    parser.add_argument("--record-sec", type=float, default=25.0, help="Loopback record seconds")
    parser.add_argument("-o", "--output-dir", type=Path, default=None)
    args = parser.parse_args()

    if not ADB.is_file():
        print(f"adb not found: {ADB}", file=sys.stderr)
        return 1

    ts = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    out_dir = args.output_dir or (ROOT / "app_apk_test" / f"host_alt_{ts}")
    out_dir.mkdir(parents=True, exist_ok=True)
    print(f"Output: {out_dir}")

    results: dict = {"output_dir": str(out_dir), "steps": {}}

    if args.mode in ("sapi", "both"):
        print("\n[1] Host SAPI re-synthesis (no App file export)")
        try:
            results["steps"]["sapi"] = run_sapi(args.text, out_dir)
            print(f"  -> {results['steps']['sapi']['output_wav']} ({results['steps']['sapi']['bytes']} bytes)")
        except Exception as ex:
            results["steps"]["sapi"] = {"error": str(ex)}
            print(f"  FAIL: {ex}")

    if args.mode in ("record", "both"):
        print("\n[2] Host loopback record while App TTS speaks")
        try:
            results["steps"]["record"] = run_record(out_dir, args.record_sec)
            print(
                f"  -> {results['steps']['record']['output_wav']} "
                f"({results['steps']['record']['bytes']} bytes, "
                f"peak={results['steps']['record'].get('wav_peak', '?')})"
            )
        except Exception as ex:
            results["steps"]["record"] = {"error": str(ex)}
            print(f"  FAIL: {ex}")

    (out_dir / "result.json").write_text(
        json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    ok = any("output_wav" in step for step in results["steps"].values())
    print(f"\n{'OK' if ok else 'FAIL'} -> {out_dir}")
    return 0 if ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
