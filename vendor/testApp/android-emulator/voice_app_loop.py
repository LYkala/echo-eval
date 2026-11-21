#!/usr/bin/env python3
"""Realistic emulator voice loop: inject wav -> App ASR -> Kimi LLM -> TTS output wav.

Simulates a user speaking into a voice app on the Android emulator:
  1. Launch app, grant mic, tap START
  2. Play input audio into emulator microphone (host playback -> virtual mic route)
  3. Read ASR result from app UI
  4. Kimi LLM reply (same API as markCustomerData/step3.py)
  5. Synthesize reply wav (Windows SAPI)

Standalone; does NOT involve TestApp WebServer.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import requests

ROOT = Path(__file__).resolve().parent

# 与 markCustomerData/step3.py 保持一致（可被环境变量覆盖）
KIMI_API_KEY = "sk-pPyuXbIpshuMg5m599PbGIssllrbvVFP7KIfhsxwjxqVQ2Sl"
KIMI_BASE_URL = "https://api.moonshot.cn/v1"
KIMI_MODEL = "kimi-k2.5"
KIMI_MAX_TOKENS = 4096
KIMI_REQ_TIMEOUT = 120

DEFAULT_SYSTEM_PROMPT = (
    "你是智能语音助手。用户可能询问天气、出行、日常问题等。"
    "请根据语音识别文本，用简洁、自然的中文回复。"
    "若用户问天气且你无法获取实时数据，可说明这一点并给出合理建议。"
    "直接输出回复内容，不要加前缀说明。"
)

DEMO_ASR_KNOWN: Dict[str, str] = {
    "weather_question.wav": "今天天气怎么样",
    "welcome.mp3": "欢迎使用阿里云。",
    "hello_world_female2.wav": "Hello world.",
}

from install_and_dump_ui import (  # noqa: E402
    adb_cmd,
    find_adb,
    list_devices,
    pick_device,
)


@dataclass(frozen=True)
class AppPreset:
    name: str
    package: str
    activity: str
    record_button_id: str
    result_text_id: str
    permissions: Tuple[str, ...]
    record_tap_xy: Tuple[int, int] = (540, 2221)
    permission_allow_xy: Tuple[int, int] = (540, 1157)


APP_PRESETS: Dict[str, AppPreset] = {
    "sherpa-asr": AppPreset(
        name="Sherpa ASR (Next-gen Kaldi)",
        package="com.k2fsa.sherpa.onnx",
        activity=".MainActivity",
        record_button_id="com.k2fsa.sherpa.onnx:id/record_button",
        result_text_id="com.k2fsa.sherpa.onnx:id/my_text",
        permissions=("android.permission.RECORD_AUDIO",),
    ),
}


def kimi_api_key_resolved() -> str:
    import os

    return (
        os.environ.get("LOGSCASE_KIMI_API_KEY", "").strip()
        or os.environ.get("KIMI_API_KEY", "").strip()
        or KIMI_API_KEY
    )


def kimi_base_url_resolved() -> str:
    import os

    return os.environ.get("LOGSCASE_KIMI_BASE_URL", KIMI_BASE_URL).strip() or KIMI_BASE_URL


def kimi_model_resolved() -> str:
    import os

    return os.environ.get("LOGSCASE_KIMI_MODEL", KIMI_MODEL).strip() or KIMI_MODEL


def moonshot_alternate_base(base_url: str) -> Optional[str]:
    base = base_url.rstrip("/")
    if base.endswith("api.moonshot.cn/v1") or "moonshot.cn" in base:
        return "https://api.moonshot.ai/v1"
    if base.endswith("api.moonshot.ai/v1") or "moonshot.ai" in base:
        return "https://api.moonshot.cn/v1"
    return None


def kimi_llm_reply(
    user_text: str,
    *,
    system_prompt: str = DEFAULT_SYSTEM_PROMPT,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    model: Optional[str] = None,
    timeout: int = KIMI_REQ_TIMEOUT,
) -> str:
    """调用 Kimi API，逻辑参考 markCustomerData/step3.py。"""
    api_key = api_key or kimi_api_key_resolved()
    base_url = (base_url or kimi_base_url_resolved()).rstrip("/")
    model = model or kimi_model_resolved()
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_text},
        ],
        "thinking": {"type": "disabled"},
        "max_tokens": KIMI_MAX_TOKENS,
    }
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    url = f"{base_url}/chat/completions"

    resp = requests.post(url, headers=headers, json=body, timeout=timeout)
    if resp.status_code == 401:
        alt = moonshot_alternate_base(base_url)
        if alt:
            resp = requests.post(
                f"{alt.rstrip('/')}/chat/completions",
                headers=headers,
                json=body,
                timeout=timeout,
            )
    if resp.status_code != 200:
        raise RuntimeError(f"Kimi 请求失败 ({resp.status_code}): {resp.text[:800]}")

    data = resp.json()
    choices = data.get("choices") or []
    if not choices:
        raise RuntimeError(f"Kimi 响应异常: {json.dumps(data, ensure_ascii=False)[:500]}")
    text = (choices[0].get("message") or {}).get("content") or ""
    text = str(text).strip()
    if not text:
        raise RuntimeError("Kimi 返回空文本")
    return text


def sapi_tts_synthesize(text: str, out_wav: Path) -> bytes:
    """Windows 内置 SAPI 离线合成。"""
    out_wav.parent.mkdir(parents=True, exist_ok=True)
    text_file = out_wav.with_suffix(".tts_input.txt")
    text_file.write_text(text, encoding="utf-8")

    ps_script = f"""
Add-Type -AssemblyName System.Speech
$text = Get-Content -LiteralPath '{text_file}' -Raw -Encoding UTF8
$syn = New-Object System.Speech.Synthesis.SpeechSynthesizer
$syn.SetOutputToWaveFile('{out_wav}')
$syn.Speak($text.Trim())
$syn.Dispose()
"""
    proc = subprocess.run(
        ["powershell", "-NoProfile", "-Command", ps_script],
        capture_output=True,
        text=True,
    )
    text_file.unlink(missing_ok=True)
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        raise RuntimeError(f"Windows SAPI TTS 失败: {err}")
    if not out_wav.is_file():
        raise RuntimeError("Windows SAPI TTS 未生成 output.wav")
    return out_wav.read_bytes()


def parse_bounds(bounds: str) -> Optional[Tuple[int, int]]:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def adb_tap(adb: Path, serial: str, x: int, y: int) -> None:
    adb_cmd(adb, serial, "shell", "input", "tap", str(x), str(y))


def adb_key(adb: Path, serial: str, key: str) -> None:
    adb_cmd(adb, serial, "shell", "input", "keyevent", key)


def grant_permissions(adb: Path, serial: str, package: str, permissions: Tuple[str, ...]) -> None:
    for perm in permissions:
        adb_cmd(adb, serial, "shell", "pm", "grant", package, perm, check=False)


def launch_app(adb: Path, serial: str, package: str, activity: str) -> None:
    component = activity if "/" in activity else f"{package}/{activity}"
    adb_cmd(adb, serial, "shell", "am", "start", "-W", "-n", component, check=False)


def dismiss_shade(adb: Path, serial: str) -> None:
    adb_key(adb, serial, "KEYCODE_HOME")
    time.sleep(0.5)


def dump_ui_xml(adb: Path, serial: str) -> str:
    remote = "/sdcard/voice_app_ui.xml"
    adb_cmd(adb, serial, "shell", "uiautomator", "dump", remote, check=False)
    result = adb_cmd(adb, serial, "shell", "cat", remote, check=False)
    return result.stdout or ""


def find_node_text(xml_text: str, resource_id: str) -> str:
    if not xml_text.strip():
        return ""
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return ""
    for elem in root.iter("node"):
        if elem.attrib.get("resource-id") == resource_id:
            return (elem.attrib.get("text") or "").strip()
    return ""


def find_node_center(xml_text: str, resource_id: str) -> Optional[Tuple[int, int]]:
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return None
    for elem in root.iter("node"):
        if elem.attrib.get("resource-id") == resource_id:
            return parse_bounds(elem.attrib.get("bounds", ""))
    return None


def handle_permission_dialog(adb: Path, serial: str, preset: AppPreset) -> None:
    xml_text = dump_ui_xml(adb, serial)
    if "permissioncontroller" not in xml_text and "Allow" not in xml_text:
        return
    btn_id = "com.android.permissioncontroller:id/permission_allow_foreground_only_button"
    center = find_node_center(xml_text, btn_id)
    if center:
        adb_tap(adb, serial, center[0], center[1])
    else:
        adb_tap(adb, serial, preset.permission_allow_xy[0], preset.permission_allow_xy[1])
    time.sleep(1.0)


def play_audio_on_host(audio_path: Path) -> None:
    """Play into VB-Cable when available, else default speakers."""
    try:
        from vbcable_play import is_ready, play_to_cable

        if is_ready():
            play_to_cable(audio_path)
            return
    except Exception:
        pass

    path = audio_path.resolve()
    suffix = path.suffix.lower()
    if suffix == ".wav":
        ps = f"""
$player = New-Object System.Media.SoundPlayer '{path}'
$player.PlaySync()
"""
    else:
        uri = str(path).replace("'", "''")
        ps = f"""
Add-Type -AssemblyName presentationCore
$p = New-Object System.Windows.Media.MediaPlayer
$p.Open([Uri]::new('file:///{uri.replace(chr(92), '/')}'))
$p.Play()
while ($p.NaturalDuration.HasTimeSpan -eq $false) {{ Start-Sleep -Milliseconds 100 }}
$sec = [Math]::Ceiling($p.NaturalDuration.TimeSpan.TotalSeconds) + 1
Start-Sleep -Seconds $sec
$p.Stop()
$p.Close()
"""
    proc = subprocess.run(
        ["powershell", "-NoProfile", "-Command", ps],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        raise RuntimeError(f"播放音频失败: {err}")


def play_audio_async(audio_path: Path) -> threading.Thread:
    thread = threading.Thread(target=play_audio_on_host, args=(audio_path,), daemon=True)
    thread.start()
    return thread


def push_input_audio(adb: Path, serial: str, audio_path: Path) -> str:
    remote_dir = "/sdcard/voice_lab"
    adb_cmd(adb, serial, "shell", "mkdir", "-p", remote_dir)
    remote = f"{remote_dir}/{audio_path.name}"
    adb_cmd(adb, serial, "push", str(audio_path.resolve()), remote)
    return remote


def infer_asr_fallback(audio_path: Path, asr_text: Optional[str]) -> Optional[str]:
    if asr_text:
        return asr_text.strip()
    sidecar = audio_path.with_suffix(audio_path.suffix + ".txt")
    if sidecar.is_file():
        text = sidecar.read_text(encoding="utf-8-sig").strip()
        if text:
            return text
    return DEMO_ASR_KNOWN.get(audio_path.name.lower())


def run_app_asr(
    adb: Path,
    serial: str,
    preset: AppPreset,
    audio_path: Path,
    *,
    mic_mode: str = "inject",
    record_sec: float = 8.0,
    asr_text_override: Optional[str] = None,
) -> Tuple[str, str]:
    """Return (asr_text, asr_source)."""
    dismiss_shade(adb, serial)
    grant_permissions(adb, serial, preset.package, preset.permissions)
    launch_app(adb, serial, preset.package, preset.activity)
    time.sleep(2.0)
    handle_permission_dialog(adb, serial, preset)

    xml_text = dump_ui_xml(adb, serial)
    baseline = find_node_text(xml_text, preset.result_text_id)

    center = find_node_center(xml_text, preset.record_button_id) or preset.record_tap_xy
    adb_tap(adb, serial, center[0], center[1])
    time.sleep(0.8)

    if mic_mode == "inject":
        try:
            from vbcable_play import configure_emulator_mic_inject, is_ready

            if is_ready():
                configure_emulator_mic_inject()
        except Exception:
            pass
        play_thread = play_audio_async(audio_path)
        deadline = time.time() + record_sec
        while time.time() < deadline:
            time.sleep(0.5)
            xml_text = dump_ui_xml(adb, serial)
            current = find_node_text(xml_text, preset.result_text_id)
            if current and current != baseline and len(current) > 3:
                play_thread.join(timeout=1)
                adb_tap(adb, serial, center[0], center[1])
                return current, "app-ui-asr"
        play_thread.join(timeout=5)

    adb_tap(adb, serial, center[0], center[1])
    time.sleep(1.0)
    xml_text = dump_ui_xml(adb, serial)
    current = find_node_text(xml_text, preset.result_text_id)
    if current and current != baseline and len(current) > 3:
        return current, "app-ui-asr"

    fallback = infer_asr_fallback(audio_path, asr_text_override)
    if fallback:
        return fallback, "fallback-text"
    raise RuntimeError(
        "App 未识别到语音。请安装虚拟声卡（VB-Cable）并把 CABLE Output 设为默认麦克风，"
        "或使用 --asr-text \"你说的内容\"。"
    )


def run_loop(
    input_audio: Path,
    output_dir: Path,
    *,
    preset_name: str = "sherpa-asr",
    serial: Optional[str] = None,
    mic_mode: str = "inject",
    asr_text: Optional[str] = None,
    system_prompt: str = DEFAULT_SYSTEM_PROMPT,
    record_sec: float = 8.0,
) -> Dict[str, Any]:
    if not input_audio.is_file():
        raise FileNotFoundError(f"输入音频不存在: {input_audio}")
    preset = APP_PRESETS.get(preset_name)
    if not preset:
        raise ValueError(f"未知 preset: {preset_name}，可选: {', '.join(APP_PRESETS)}")

    adb = find_adb()
    device = pick_device(adb, serial)
    output_dir.mkdir(parents=True, exist_ok=True)

    started = time.time()
    result: Dict[str, Any] = {
        "mode": "emulator-app-loop",
        "preset": preset_name,
        "device": device,
        "input": str(input_audio.resolve()),
        "output_dir": str(output_dir.resolve()),
        "started_at": datetime.now(timezone.utc).isoformat(),
        "steps": {},
    }

    print(f"Device: {device} | App: {preset.name}")
    remote = push_input_audio(adb, device, input_audio)
    result["input_remote"] = remote

    print(f"[1/4] Emulator ASR: inject {input_audio.name} -> {preset.package}")
    t0 = time.time()
    asr_result, asr_source = run_app_asr(
        adb,
        device,
        preset,
        input_audio,
        mic_mode=mic_mode,
        record_sec=record_sec,
        asr_text_override=asr_text,
    )
    asr_path = output_dir / "asr.txt"
    asr_path.write_text(asr_result, encoding="utf-8")
    result["steps"]["asr"] = {
        "text": asr_result,
        "source": asr_source,
        "file": str(asr_path),
        "elapsed_s": round(time.time() - t0, 2),
    }
    print(f"  -> [{asr_source}] {asr_result!r}")

    print("[2/4] LLM: kimi/kimi-k2.5")
    t0 = time.time()
    llm_text = kimi_llm_reply(asr_result, system_prompt=system_prompt)
    llm_path = output_dir / "llm.txt"
    llm_path.write_text(llm_text, encoding="utf-8")
    result["steps"]["llm"] = {
        "text": llm_text,
        "file": str(llm_path),
        "elapsed_s": round(time.time() - t0, 2),
    }
    print(f"  -> {llm_text!r}")

    print("[3/4] TTS: windows-sapi -> output.wav")
    t0 = time.time()
    out_wav = output_dir / "output.wav"
    audio_bytes = sapi_tts_synthesize(llm_text, out_wav)
    result["steps"]["tts"] = {
        "file": str(out_wav),
        "bytes": len(audio_bytes),
        "elapsed_s": round(time.time() - t0, 2),
    }
    print(f"  -> {out_wav} ({len(audio_bytes) / 1024:.1f} KB)")

    print("[4/4] Push reply wav to emulator /sdcard/voice_lab/")
    t0 = time.time()
    reply_remote = push_input_audio(adb, device, out_wav)
    result["steps"]["reply_push"] = {
        "remote": reply_remote,
        "elapsed_s": round(time.time() - t0, 2),
    }
    print(f"  -> {reply_remote}")

    result["elapsed_s"] = round(time.time() - started, 2)
    result_path = output_dir / "result.json"
    result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Done in {result['elapsed_s']}s. Results: {output_dir}")
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="逼真模拟: 模拟器 App 语音输入 -> ASR -> Kimi -> 回复 wav",
    )
    parser.add_argument("--input", "-i", required=True, help="输入音频 (.wav/.mp3)")
    parser.add_argument("--output-dir", "-o", default="", help="输出目录")
    parser.add_argument("--preset", default="sherpa-asr", choices=sorted(APP_PRESETS))
    parser.add_argument("--serial", default="", help="adb 设备 serial")
    parser.add_argument(
        "--mic-mode",
        choices=("inject", "fallback"),
        default="inject",
        help="inject=播放音频进模拟器麦克风; fallback=跳过灌麦，用 --asr-text 或 sidecar",
    )
    parser.add_argument("--asr-text", default="", help="ASR 兜底文本")
    parser.add_argument("--record-sec", type=float, default=8.0, help="录音窗口秒数")
    parser.add_argument("--system-prompt", default=DEFAULT_SYSTEM_PROMPT)
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    input_path = Path(args.input).resolve()
    if args.output_dir:
        out_dir = Path(args.output_dir).resolve()
    else:
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        out_dir = ROOT / "app_loop_output" / f"{input_path.stem}_{stamp}"

    try:
        run_loop(
            input_path,
            out_dir,
            preset_name=args.preset,
            serial=args.serial or None,
            mic_mode=args.mic_mode,
            asr_text=args.asr_text or None,
            system_prompt=args.system_prompt,
            record_sec=args.record_sec,
        )
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
