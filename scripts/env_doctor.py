#!/usr/bin/env python3
"""Emit one JSON object for web console env check. Usage: python scripts/env_doctor.py [--quick]"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

# Windows 控制台默认常为 GBK；强制 UTF-8，避免 Node/浏览器乱码
try:
    sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
    sys.stderr.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
except Exception:
    pass

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--quick", action="store_true", help="短超时，避免 UI 卡住")
    ap.add_argument(
        "--step",
        default="",
        help="只跑单步: config|python|adb|device|ffmpeg|audio|tts|vl|mos",
    )
    args = ap.parse_args()
    connect_timeout = 4.0 if args.quick else 12.0
    print(
        f"[echo-eval][env_doctor] start step={args.step or 'all'} quick={args.quick}",
        file=sys.stderr,
        flush=True,
    )

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
        tts_cfg = cfg.tts if cfg else None
        backend = (tts_cfg.backend if tts_cfg else "edge") or "edge"
        backend = str(backend).strip().lower()
        if backend == "edge":
            detail = ""
            ok = False
            try:
                from echo_eval.stimulus.tts import synthesize_edge

                probe = ROOT / "artifacts" / "_tts_edge_probe.wav"
                probe.parent.mkdir(parents=True, exist_ok=True)
                voice = (tts_cfg.voice if tts_cfg else "") or "zh-CN-XiaoxiaoNeural"
                result = synthesize_edge(
                    "小翼管家",
                    probe,
                    voice=voice,
                    rate=int(tts_cfg.rate if tts_cfg else 0),
                    ffmpeg_path=cfg.capture.ffmpeg_path if cfg else "",
                )
                ok = True
                detail = f"edge-tts OK · {result.voice} · {result.duration_sec}s"
                try:
                    if probe.is_file():
                        probe.unlink()
                except OSError:
                    pass
            except Exception as e:
                ok = False
                detail = f"edge-tts 失败（需出网）: {e}"
            items.append(
                {
                    "key": "tts",
                    "label": "TTS (edge-tts)",
                    "ok": ok,
                    "detail": detail,
                    "required": False,
                }
            )
        elif backend == "sapi":
            items.append(
                {
                    "key": "tts",
                    "label": "TTS (SAPI)",
                    "ok": sys.platform.startswith("win"),
                    "detail": (
                        "Windows SAPI"
                        if sys.platform.startswith("win")
                        else "非 Windows：SAPI 不可用，请改用 edge"
                    ),
                    "required": False,
                }
            )
        else:
            items.append(
                {
                    "key": "tts",
                    "label": f"TTS ({backend})",
                    "ok": True,
                    "detail": f"backend={backend}（未做深度探测）",
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
                "detail": str(mp) if mp else "缺少 configs/model.yaml（或 VL_EVAL_CONFIG）；可用 mock",
                "required": False,
            }
        )

    if want("mos"):
        # Optional: host-mic path for TTS MOS (does not block env.ok).
        from echo_eval.capture.host_mic import (
            HostMicError,
            finish_host_mic_record,
            normalize_dshow_audio_device,
            resolve_ffmpeg_bin,
            start_host_mic_record,
        )

        mos_cfg = cfg.mos if cfg else None
        if mos_cfg is None or not mos_cfg.enabled:
            items.append(
                {
                    "key": "mos_mic",
                    "label": "MOS 拾音（主机麦）",
                    "ok": True,
                    "detail": "mos.enabled=false，跳过拾音检查",
                    "required": False,
                }
            )
        else:
            detail = ""
            ok = False
            try:
                ff = resolve_ffmpeg_bin(cfg.capture.ffmpeg_path if cfg else "")
                fmt = (mos_cfg.input_format or "").strip() or (
                    "dshow" if sys.platform.startswith("win") else ""
                )
                dev = (mos_cfg.input_device or "").strip()
                if sys.platform.startswith("win"):
                    if not dev:
                        ok = False
                        detail = (
                            "未配置 mos.input_device。请用 "
                            "`ffmpeg -list_devices true -f dshow -i dummy` "
                            "查看 (audio) 设备，填 audio=名称 或纯名称"
                        )
                    else:
                        norm = normalize_dshow_audio_device(dev)
                        probe = ROOT / "artifacts" / "_mos_mic_probe.wav"
                        probe.parent.mkdir(parents=True, exist_ok=True)
                        proc, meta = start_host_mic_record(
                            probe,
                            duration_sec=0.8,
                            ffmpeg_path=ff,
                            input_format=fmt or "dshow",
                            input_device=norm,
                            sample_rate=int(mos_cfg.sample_rate or 16000),
                        )
                        meta = finish_host_mic_record(proc, meta, timeout_sec=8.0)
                        ok = bool(meta.ok)
                        detail = (
                            f"试录 OK · {norm}"
                            if ok
                            else f"试录失败 · {norm} · {meta.error or 'unknown'}"
                        )
                        try:
                            if probe.is_file():
                                probe.unlink()
                        except OSError:
                            pass
                else:
                    # Non-Windows: attempt default pulse/alsa briefly.
                    probe = ROOT / "artifacts" / "_mos_mic_probe.wav"
                    probe.parent.mkdir(parents=True, exist_ok=True)
                    proc, meta = start_host_mic_record(
                        probe,
                        duration_sec=0.8,
                        ffmpeg_path=ff,
                        input_format=fmt,
                        input_device=dev,
                        sample_rate=int(mos_cfg.sample_rate or 16000),
                    )
                    meta = finish_host_mic_record(proc, meta, timeout_sec=8.0)
                    ok = bool(meta.ok)
                    detail = (
                        f"试录 OK · {meta.backend}"
                        if ok
                        else f"试录失败 · {meta.error or 'unknown'}"
                    )
                    try:
                        if probe.is_file():
                            probe.unlink()
                    except OSError:
                        pass
            except HostMicError as e:
                ok = False
                detail = str(e)
            except Exception as e:
                ok = False
                detail = f"MOS 拾音检查异常: {e}"
            items.append(
                {
                    "key": "mos_mic",
                    "label": "MOS 拾音（主机麦）",
                    "ok": ok,
                    "detail": detail,
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
    print(
        f"[echo-eval][env_doctor] done step={out['step']} ok={out['ok']} items={len(items)}",
        file=sys.stderr,
        flush=True,
    )
    print(json.dumps(out, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
