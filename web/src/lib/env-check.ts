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
  error?: string;
};

export type EnvStepId =
  | "config"
  | "python"
  | "adb"
  | "device"
  | "ffmpeg"
  | "audio"
  | "tts"
  | "vl";

export const ENV_STEP_ORDER: { id: EnvStepId; label: string }[] = [
  { id: "config", label: "读取设备配置" },
  { id: "python", label: "检查 echo-eval 源码" },
  { id: "adb", label: "检查 ADB" },
  { id: "device", label: "连接设备（短超时）" },
  { id: "ffmpeg", label: "检查 FFmpeg" },
  { id: "audio", label: "检查音频播放" },
  { id: "tts", label: "检查 TTS" },
  { id: "vl", label: "检查 VL 配置" },
];

export type EnvProgressEvent =
  | { type: "start"; total: number }
  | { type: "step_start"; step: EnvStepId; label: string; index: number; total: number }
  | { type: "step_done"; step: EnvStepId; label: string; item: EnvCheckItem; index: number; total: number }
  | { type: "done"; result: EnvCheckResult }
  | { type: "error"; message: string };

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

function runDoctorStep(step: EnvStepId): { item?: EnvCheckItem; serial?: string; error?: string } {
  const root = repoRoot();
  const script = path.join(root, "scripts", "env_doctor.py");
  if (!fs.existsSync(script)) {
    return { error: `缺少脚本: ${script}` };
  }

  // device step can take a few seconds for connect; others should be fast
  const timeoutMs = step === "device" ? 12000 : 8000;
  const proc = spawnSync(pythonBin(), [script, "--quick", "--step", step], {
    encoding: "utf-8",
    cwd: root,
    env: { ...process.env, PYTHONPATH: path.join(root, "src"), PYTHONUNBUFFERED: "1" },
    timeout: timeoutMs,
    windowsHide: true,
  });

  if (proc.error) {
    const err = proc.error as NodeJS.ErrnoException;
    if (err.code === "ETIMEDOUT") {
      return {
        item: {
          key: step,
          label: step,
          ok: false,
          detail: `检查超时（>${timeoutMs / 1000}s）`,
          required: !["audio", "tts", "vl"].includes(step),
        },
      };
    }
    return { error: String(proc.error) };
  }

  const stdout = (proc.stdout || "").trim();
  const stderr = (proc.stderr || "").trim();
  if (!stdout) {
    return {
      error: stderr || `python 无输出 (exit=${proc.status})`,
    };
  }

  try {
    const line = stdout.split(/\r?\n/).filter(Boolean).pop() || "{}";
    const parsed = JSON.parse(line) as { items?: EnvCheckItem[]; serial?: string };
    const item = parsed.items?.[0];
    return { item, serial: parsed.serial };
  } catch (e) {
    return {
      error: `解析失败: ${String(e)}; stdout=${stdout.slice(0, 200)}; stderr=${stderr.slice(0, 200)}`,
    };
  }
}

export function* iterEnvCheck(): Generator<EnvProgressEvent, void, void> {
  const total = ENV_STEP_ORDER.length;
  yield { type: "start", total };
  const items: EnvCheckItem[] = [];
  let serial: string | undefined;
  let fatal: string | undefined;

  for (let i = 0; i < ENV_STEP_ORDER.length; i++) {
    const meta = ENV_STEP_ORDER[i];
    yield { type: "step_start", step: meta.id, label: meta.label, index: i + 1, total };
    const { item, serial: s, error } = runDoctorStep(meta.id);
    if (s) serial = s;
    if (error) {
      fatal = error;
      const failed: EnvCheckItem = {
        key: meta.id,
        label: meta.label,
        ok: false,
        detail: error,
        required: true,
      };
      items.push(failed);
      yield {
        type: "step_done",
        step: meta.id,
        label: meta.label,
        item: failed,
        index: i + 1,
        total,
      };
      break;
    }
    if (item) {
      // keep nicer labels from UI order when script label is short
      const normalized = { ...item, label: item.label || meta.label };
      items.push(normalized);
      yield {
        type: "step_done",
        step: meta.id,
        label: meta.label,
        item: normalized,
        index: i + 1,
        total,
      };
    }
  }

  const requiredFail = items.filter((it) => it.required && !it.ok);
  const result: EnvCheckResult = {
    ok: requiredFail.length === 0 && !fatal,
    demoMode: requiredFail.length > 0,
    items,
    serial,
    checkedAt: new Date().toISOString(),
    error: fatal,
  };
  yield { type: "done", result };
}

export function runEnvCheck(): EnvCheckResult {
  let result: EnvCheckResult | null = null;
  for (const ev of iterEnvCheck()) {
    if (ev.type === "done") result = ev.result;
    if (ev.type === "error") {
      return {
        ok: false,
        demoMode: true,
        items: [],
        checkedAt: new Date().toISOString(),
        error: ev.message,
      };
    }
  }
  return (
    result || {
      ok: false,
      demoMode: true,
      items: [],
      checkedAt: new Date().toISOString(),
      error: "环境检查未返回结果",
    }
  );
}
