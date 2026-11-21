import { spawnSync } from "child_process";
import fs from "fs";
import path from "path";
import { repoRoot } from "./paths";

export type EnvCheckItem = {
  key: string;
  label: string;
  ok: boolean;
  detail: string;
  required: boolean;
};

export type EnvCheckResult = {
  ok: boolean;
  demoMode: boolean;
  items: EnvCheckItem[];
  serial?: string;
  checkedAt: string;
};

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

export function runEnvCheck(): EnvCheckResult {
  const root = repoRoot();
  const demoMode = process.env.ECHO_EVAL_DEMO === "1" || !fs.existsSync(path.join(root, "src", "echo_eval"));
  const items: EnvCheckItem[] = [];

  const cfgPath = path.join(root, "configs", "device.yaml");
  items.push({
    key: "config",
    label: "设备配置 device.yaml",
    ok: fs.existsSync(cfgPath),
    detail: fs.existsSync(cfgPath) ? cfgPath : "缺失 configs/device.yaml",
    required: true,
  });

  const srcOk = fs.existsSync(path.join(root, "src", "echo_eval"));
  items.push({
    key: "python_pkg",
    label: "echo-eval 源码",
    ok: srcOk,
    detail: srcOk ? path.join(root, "src") : "未找到 src/echo_eval",
    required: true,
  });

  // Structured doctor via python one-liner
  const script = `
import json, shutil, sys
from pathlib import Path
sys.path.insert(0, r${JSON.stringify(path.join(root, "src"))})
out = {"ok": True, "items": [], "serial": None}
try:
    from echo_eval.config import load_config, DEFAULT_CONFIG_PATH
    from echo_eval.device.adb import AdbClient, AdbError
    from echo_eval.capture.frames import resolve_ffmpeg, FrameExtractError
    from echo_eval.stimulus.audio import _resolve_ffplay
    from echo_eval.judge.model_config import find_model_config_path
    cfg_path = Path(r${JSON.stringify(cfgPath)})
    cfg = load_config(cfg_path) if cfg_path.is_file() else None
    adb_bin = (cfg.adb.adb_path if cfg and cfg.adb.adb_path else "") or (shutil.which("adb") or "")
    if adb_bin:
        try:
            serial = cfg.adb.resolved_serial if cfg else ""
            out["serial"] = serial
            ver = AdbClient(serial=serial or "x", adb_path=adb_bin).version()
            out["items"].append({"key":"adb","label":"ADB","ok":True,"detail":f"{adb_bin} ({ver})", "required":True})
            if serial:
                try:
                    st = AdbClient(serial=serial, adb_path=adb_bin).device_state()
                    ok = bool(st and st.ok)
                    if not ok:
                        AdbClient(serial=serial, adb_path=adb_bin).connect(timeout_sec=8)
                        st = AdbClient(serial=serial, adb_path=adb_bin).device_state()
                        ok = bool(st and st.ok)
                    out["items"].append({"key":"device","label":"设备在线","ok":ok,"detail":f"{serial} state={(st.state if st else 'none')}", "required":True})
                except Exception as e:
                    out["items"].append({"key":"device","label":"设备在线","ok":False,"detail":str(e), "required":True})
        except Exception as e:
            out["items"].append({"key":"adb","label":"ADB","ok":False,"detail":str(e), "required":True})
    else:
        out["items"].append({"key":"adb","label":"ADB","ok":False,"detail":"未找到 adb","required":True})
    try:
        ff = resolve_ffmpeg(cfg.capture.ffmpeg_path if cfg else "")
        out["items"].append({"key":"ffmpeg","label":"FFmpeg","ok":True,"detail":ff,"required":True})
    except Exception as e:
        out["items"].append({"key":"ffmpeg","label":"FFmpeg","ok":False,"detail":str(e),"required":True})
    ffplay = _resolve_ffplay(cfg.capture.ffmpeg_path if cfg else "")
    out["items"].append({"key":"ffplay","label":"音频播放(ffplay/winsound)","ok":True,"detail":ffplay or "将回退 winsound (Windows)","required":False})
    import sys as _sys
    out["items"].append({"key":"tts","label":"TTS (SAPI)","ok":_sys.platform.startswith("win"),"detail":"Windows SAPI" if _sys.platform.startswith("win") else "非 Windows：可用 mock/beep 演示","required":False})
    mp = find_model_config_path()
    out["items"].append({"key":"vl","label":"VL 网关 model.yaml","ok":bool(mp),"detail":str(mp) if mp else "未设置 VL_EVAL_CONFIG；可用 mock","required":False})
except Exception as e:
    out["ok"] = False
    out["items"].append({"key":"doctor","label":"doctor","ok":False,"detail":str(e),"required":True})
print(json.dumps(out, ensure_ascii=False))
`;

  if (!demoMode) {
    const proc = spawnSync(pythonBin(), ["-c", script], {
      encoding: "utf-8",
      env: { ...process.env, PYTHONPATH: path.join(root, "src") },
      timeout: 25000,
    });
    if (proc.status === 0 && proc.stdout) {
      try {
        const parsed = JSON.parse(proc.stdout.trim().split("\n").pop() || "{}") as {
          items?: EnvCheckItem[];
          serial?: string;
        };
        if (parsed.items) items.push(...parsed.items);
        const requiredFail = items.filter((i) => i.required && !i.ok);
        return {
          ok: requiredFail.length === 0,
          demoMode: false,
          items,
          serial: parsed.serial,
          checkedAt: new Date().toISOString(),
        };
      } catch {
        items.push({
          key: "doctor_parse",
          label: "环境探测",
          ok: false,
          detail: proc.stderr || "无法解析 doctor 输出",
          required: true,
        });
      }
    } else {
      items.push({
        key: "doctor",
        label: "环境探测",
        ok: false,
        detail: (proc.stderr || proc.stdout || `python exit ${proc.status}`).slice(0, 500),
        required: true,
      });
    }
  } else {
    items.push(
      {
        key: "adb",
        label: "ADB",
        ok: false,
        detail: "演示模式：本机未接设备",
        required: true,
      },
      {
        key: "device",
        label: "设备在线",
        ok: false,
        detail: "演示模式",
        required: true,
      }
    );
  }

  const requiredFail = items.filter((i) => i.required && !i.ok);
  return {
    ok: requiredFail.length === 0,
    demoMode: demoMode || requiredFail.length > 0,
    items,
    checkedAt: new Date().toISOString(),
  };
}
