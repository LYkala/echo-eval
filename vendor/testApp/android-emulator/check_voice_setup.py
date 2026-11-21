#!/usr/bin/env python3
"""Check emulator voice app loop prerequisites."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent
AUDIO_DIR = ROOT / "audio_samples"
KIMI_MODELS_URL = "https://api.moonshot.cn/v1/models"

SAMPLE_URLS = {
    "welcome.mp3": "https://dashscope.oss-cn-beijing.aliyuncs.com/audios/welcome.mp3",
    "hello_world_female2.wav": (
        "https://dashscope.oss-cn-beijing.aliyuncs.com/samples/audio/paraformer/"
        "hello_world_female2.wav"
    ),
}


def download_samples(force: bool = False) -> int:
    AUDIO_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for name, url in SAMPLE_URLS.items():
        dest = AUDIO_DIR / name
        if dest.is_file() and not force:
            print(f"[skip] {name} 已存在 ({dest.stat().st_size} bytes)")
            continue
        print(f"[download] {name}")
        resp = requests.get(url, timeout=60)
        resp.raise_for_status()
        dest.write_bytes(resp.content)
        print(f"  -> {dest} ({len(resp.content)} bytes)")
        count += 1
    return count


def list_samples() -> list[Path]:
    if not AUDIO_DIR.is_dir():
        return []
    patterns = ("*.wav", "*.mp3", "*.m4a", "*.ogg")
    files: list[Path] = []
    for pattern in patterns:
        files.extend(AUDIO_DIR.glob(pattern))
    return sorted(set(files))


def check_emulator() -> tuple[bool, str]:
    try:
        from install_and_dump_ui import find_adb, list_devices

        adb = find_adb()
        devices = list_devices(adb)
        if not devices:
            return False, "无在线设备，请先运行 .\\scripts\\start-emulator.ps1"
        return True, f"在线设备: {', '.join(devices)}"
    except Exception as exc:
        return False, str(exc)


def check_vbcable() -> tuple[bool, str]:
    try:
        from vbcable_play import status

        info = status()
        if info.get("installed"):
            dev = info.get("devices") or {}
            cap = dev.get("Capture") or "CABLE Output"
            return True, f"VB-Cable 已安装，麦克风端: {cap}"
        return False, "VB-Cable 未安装（运行 .\\scripts\\setup_virtual_mic.ps1）"
    except Exception as exc:
        return False, f"VB-Cable 检查失败: {exc}"


def check_kimi() -> tuple[bool, str]:
    from voice_app_loop import kimi_api_key_resolved

    key = kimi_api_key_resolved()
    try:
        resp = requests.get(
            KIMI_MODELS_URL,
            headers={"Authorization": f"Bearer {key}"},
            timeout=15,
        )
    except requests.RequestException as exc:
        return False, f"网络错误: {exc}"
    if resp.status_code == 200:
        return True, "Kimi API Key 有效"
    if resp.status_code == 401:
        return False, "Kimi API Key 无效（401）"
    return False, f"HTTP {resp.status_code}"


def main() -> int:
    parser = argparse.ArgumentParser(description="检查模拟器语音环回环境")
    parser.add_argument("--download-samples", action="store_true")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    print("\n=== 1. 示例语音 ===")
    if args.download_samples:
        download_samples(force=args.force)
    samples = list_samples()
    if samples:
        for f in samples:
            print(f"  [ok] {f}")
    else:
        print("  [missing] python check_voice_setup.py --download-samples")

    print("\n=== 2. Android 模拟器 ===")
    emu_ok, emu_msg = check_emulator()
    print(f"  {'[ok]' if emu_ok else '[fail]'} {emu_msg}")

    print("\n=== 3. Kimi 大模型 ===")
    kimi_ok, kimi_msg = check_kimi()
    print(f"  {'[ok]' if kimi_ok else '[fail]'} {kimi_msg}")

    print("\n=== 4. VB-Cable 灌麦 ===")
    vbc_ok, vbc_msg = check_vbcable()
    print(f"  {'[ok]' if vbc_ok else '[warn]'} {vbc_msg}")
    if vbc_ok:
        print("  .\\scripts\\configure_vbcable_routing.ps1")
        print("  python test_voice_assistant_apk.py --real-mic")
    else:
        print("  .\\scripts\\setup_virtual_mic.ps1")

    print("\n=== 5. 运行（默认示例：问天气） ===")
    if samples and emu_ok:
        weather = AUDIO_DIR / "weather_question.wav"
        demo = weather if weather.is_file() else samples[0]
        print(f'  python voice_app_loop.py -i "{demo}"')
        print(f'  python voice_app_loop.py -i "{demo}" --mic-mode fallback')
        print("  python test_voice_assistant_apk.py --real-mic --allow-demo-fallback")
        print("  .\\scripts\\setup_virtual_mic.ps1   # 逼真灌麦（VB-Cable）")

    return 0 if (samples and emu_ok and kimi_ok) else 1


if __name__ == "__main__":
    raise SystemExit(main())
