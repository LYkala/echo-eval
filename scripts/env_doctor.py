#!/usr/bin/env python3
"""Emit one JSON object for web console env check. Usage: python scripts/env_doctor.py [--quick]"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--quick", action="store_true", help="短超时，避免 UI 卡住")
    ap.add_argument("--step", default="", help="只跑单步: config|python|adb|device|ffmpeg|audio|tts|vl")
    args = ap.parse_args()
    connect_timeout = 4.0 if args.quick else 12.0

    from echo_eval.capture.frames import resolve_ffmpeg
    from echo_eval.config import load_config
    from echo_eval.device.adb import AdbClient, AdbError
    from echo_eval.judge.model_config import find_model_config_path
    from echo_eval.stimulus.audio import _resolve_ffplay

    cfg_path = ROOT / "configs" / "device.yaml"
    items: list[dict] = []
    serial = None
    step = (args.step or "").strip().lower()

    def want(key: str) -> bool:
        return not step or step == key

    if want("config"):
        items.append(
            {
                "key": "config",
                "label": "设备配置 device.yaml",
                "ok": cfg_path.is_file(),
                "detail": str(cfg_path) if cfg_path.is_file() else "缺失 configs/device.yaml",
                "required": True,
            }
        )

    if want("python"):
        src = ROOT / "src" / "echo_eval"
        items.append(
            {
                "key": "python_pkg",
                "label": "echo-eval 源码",
                "ok": src.is_dir(),
                "detail": str(src) if src.is_dir() else "未找到 src/echo_eval",
                "required": True,
            }
        )

    cfg = None
    if cfg_path.is_file():
        try:
            cfg = load_config(cfg_path)
        except Exception as e:
            if want("config"):
                items[-1]["ok"] = False
                items[-1]["detail"] = f"配置无法加载: {e}"

    adb_bin = ""
    if cfg and cfg.adb.adb_path:
        adb_bin = cfg.adb.adb_path
    else:
        adb_bin = shutil.which("adb") or ""

    if want("adb"):
        if not adb_bin:
            items.append(
                {
                    "key": "adb",
                    "label": "ADB",
                    "ok": False,
                    "detail": "未找到 adb（PATH 或 configs/device.yaml 的 adb.adb_path）",
                    "required": True,
                }
            )
        else:
            try:
                serial = cfg.adb.resolved_serial if cfg else ""
                ver = AdbClient(serial=serial or "x", adb_path=adb_bin).version()
                items.append(
                    {
                        "key": "adb",
                        "label": "ADB",
                        "ok": True,
                        "detail": f"{adb_bin} ({ver})",
                        "required": True,
                    }
                )
            except Exception as e:
                items.append(
                    {
                        "key": "adb",
                        "label": "ADB",
                        "ok": False,
                        "detail": str(e),
                        "required": True,
                    }
                )

    if want("device"):
        if not adb_bin:
            items.append(
                {
                    "key": "device",
                    "label": "设备在线",
                    "ok": False,
                    "detail": "无 adb，跳过连接",
                    "required": True,
                }
            )
        else:
            try:
                serial = cfg.adb.resolved_serial if cfg else ""
                client = AdbClient(serial=serial, adb_path=adb_bin)
                st = client.device_state()
                ok = bool(st and st.ok)
                detail = f"{serial} state={(st.state if st else 'none')}"
                if not ok and serial:
                    try:
                        st = client.connect(timeout_sec=connect_timeout)
                        ok = bool(st and st.ok)
                        detail = f"{serial} state={(st.state if st else 'none')} (after connect)"
                    except AdbError as e:
                        ok = False
                        detail = f"{serial} 连接失败: {e}"
                items.append(
                    {
                        "key": "device",
                        "label": "设备在线",
                        "ok": ok,
                        "detail": detail,
                        "required": True,
                    }
                )
            except Exception as e:
                items.append(
                    {
                        "key": "device",
                        "label": "设备在线",
                        "ok": False,
                        "detail": str(e),
                        "required": True,
                    }
                )

    if want("ffmpeg"):
        try:
            ff = resolve_ffmpeg(cfg.capture.ffmpeg_path if cfg else "")
            items.append(
                {
                    "key": "ffmpeg",
                    "label": "FFmpeg",
                    "ok": True,
                    "detail": ff,
                    "required": True,
                }
            )
        except Exception as e:
            items.append(
                {
                    "key": "ffmpeg",
                    "label": "FFmpeg",
                    "ok": False,
                    "detail": str(e),
                    "required": True,
                }
            )

    if want("audio"):
        ffplay = _resolve_ffplay(cfg.capture.ffmpeg_path if cfg else "")
        items.append(
            {
                "key": "ffplay",
                "label": "音频播放(ffplay/winsound)",
                "ok": True,
                "detail": ffplay or "将回退 Windows winsound",
                "required": False,
            }
        )

    if want("tts"):
        items.append(
            {
                "key": "tts",
                "label": "TTS (SAPI)",
                "ok": sys.platform.startswith("win"),
                "detail": "Windows SAPI" if sys.platform.startswith("win") else "非 Windows：真机 TTS 需在 PC1 Windows 上跑",
                "required": False,
            }
        )

    if want("vl"):
        mp = find_model_config_path()
        items.append(
            {
                "key": "vl",
                "label": "VL 网关 model.yaml",
                "ok": bool(mp),
                "detail": str(mp) if mp else "未设置 VL_EVAL_CONFIG；可用 mock",
                "required": False,
            }
        )

    required_fail = [i for i in items if i.get("required") and not i.get("ok")]
    out = {
        "ok": len(required_fail) == 0,
        "serial": serial,
        "items": items,
        "step": step or "all",
    }
    print(json.dumps(out, ensure_ascii=False))
    return 0 if out["ok"] or step else (0 if step else 1)


if __name__ == "__main__":
    raise SystemExit(main())
