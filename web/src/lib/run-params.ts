import { spawnSync } from "child_process";
import path from "path";
import { pythonChildEnv } from "./python-env";
import { repoRoot } from "./paths";

/** Flat overrides the console can pass for a single job (not written to device.yaml). */
export type RunParamOverrides = {
  step_wait_sec?: number;
  head_delay_sec?: number;
  tail_sec?: number;
  fps?: number;
  max_retries?: number;
  step_settle_sec?: number;
  asr_enabled?: boolean;
  asr_window_sec?: number;
  asr_interval_sec?: number;
  latency_enabled?: boolean;
  latency_window_sec?: number;
  mos_enabled?: boolean;
  mos_window_sec?: number;
  mos_input_device?: string;
  wake_enabled?: boolean;
  wake_text?: string;
  wake_wait_sec?: number;
};

export type RunParamDefaults = {
  step_wait_sec: number;
  head_delay_sec: number;
  tail_sec: number;
  fps: number;
  max_retries: number;
  step_settle_sec: number;
  asr_enabled: boolean;
  asr_window_sec: number;
  asr_interval_sec: number;
  latency_enabled: boolean;
  latency_window_sec: number;
  mos_enabled: boolean;
  mos_window_sec: number;
  mos_input_device: string;
  wake_enabled: boolean;
  wake_text: string;
  wake_wait_sec: number;
};

const FALLBACK_DEFAULTS: RunParamDefaults = {
  step_wait_sec: 3,
  head_delay_sec: 1,
  tail_sec: 5,
  fps: 1,
  max_retries: 3,
  step_settle_sec: 1,
  asr_enabled: true,
  asr_window_sec: 4,
  asr_interval_sec: 0.5,
  latency_enabled: true,
  latency_window_sec: 8,
  mos_enabled: true,
  mos_window_sec: 5,
  mos_input_device: "",
  wake_enabled: true,
  wake_text: "小翼管家",
  wake_wait_sec: 3,
};

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

function runPythonJson(code: string, args: string[] = []): unknown {
  const r = spawnSync(pythonBin(), ["-c", code, ...args], {
    cwd: repoRoot(),
    env: pythonChildEnv(),
    encoding: "utf-8",
    windowsHide: true,
  });
  if (r.error) throw r.error;
  if (r.status !== 0) {
    throw new Error((r.stderr || r.stdout || `python exit ${r.status}`).trim());
  }
  const text = (r.stdout || "").trim();
  if (!text) throw new Error("python returned empty output");
  return JSON.parse(text);
}

/** Read current defaults from configs/device.yaml via echo_eval. */
export function readRunParamDefaults(): RunParamDefaults {
  try {
    const raw = runPythonJson(
      "from echo_eval.config import public_run_defaults\n" +
        "import json\n" +
        "print(json.dumps(public_run_defaults(), ensure_ascii=False))"
    ) as Partial<RunParamDefaults>;
    return {
      step_wait_sec: Number(raw.step_wait_sec ?? FALLBACK_DEFAULTS.step_wait_sec),
      head_delay_sec: Number(raw.head_delay_sec ?? FALLBACK_DEFAULTS.head_delay_sec),
      tail_sec: Number(raw.tail_sec ?? FALLBACK_DEFAULTS.tail_sec),
      fps: Number(raw.fps ?? FALLBACK_DEFAULTS.fps),
      max_retries: Math.max(1, Math.floor(Number(raw.max_retries ?? FALLBACK_DEFAULTS.max_retries))),
      step_settle_sec: Number(raw.step_settle_sec ?? FALLBACK_DEFAULTS.step_settle_sec),
      asr_enabled: raw.asr_enabled !== undefined ? Boolean(raw.asr_enabled) : FALLBACK_DEFAULTS.asr_enabled,
      asr_window_sec: Number(raw.asr_window_sec ?? FALLBACK_DEFAULTS.asr_window_sec),
      asr_interval_sec: Number(raw.asr_interval_sec ?? FALLBACK_DEFAULTS.asr_interval_sec),
      latency_enabled:
        raw.latency_enabled !== undefined ? Boolean(raw.latency_enabled) : FALLBACK_DEFAULTS.latency_enabled,
      latency_window_sec: Number(raw.latency_window_sec ?? FALLBACK_DEFAULTS.latency_window_sec),
      mos_enabled: raw.mos_enabled !== undefined ? Boolean(raw.mos_enabled) : FALLBACK_DEFAULTS.mos_enabled,
      mos_window_sec: Number(raw.mos_window_sec ?? FALLBACK_DEFAULTS.mos_window_sec),
      mos_input_device: String(raw.mos_input_device ?? FALLBACK_DEFAULTS.mos_input_device),
      wake_enabled: raw.wake_enabled !== undefined ? Boolean(raw.wake_enabled) : FALLBACK_DEFAULTS.wake_enabled,
      wake_text: String(raw.wake_text ?? FALLBACK_DEFAULTS.wake_text) || FALLBACK_DEFAULTS.wake_text,
      wake_wait_sec: Number(raw.wake_wait_sec ?? FALLBACK_DEFAULTS.wake_wait_sec),
    };
  } catch {
    return { ...FALLBACK_DEFAULTS };
  }
}

/** Keep only known overrides; drop NaN / empty numbers. */
export function sanitizeOverrides(input: RunParamOverrides | null | undefined): RunParamOverrides {
  if (!input) return {};
  const out: RunParamOverrides = {};
  const numKeys = [
    "step_wait_sec",
    "head_delay_sec",
    "tail_sec",
    "fps",
    "max_retries",
    "step_settle_sec",
    "asr_window_sec",
    "asr_interval_sec",
    "latency_window_sec",
    "mos_window_sec",
    "wake_wait_sec",
  ] as const;
  for (const k of numKeys) {
    const v = input[k];
    if (v === undefined || v === null || Number.isNaN(Number(v))) continue;
    const n = Number(v);
    out[k] = k === "max_retries" ? Math.max(1, Math.floor(n)) : Math.max(0, n);
  }
  const boolKeys = ["asr_enabled", "latency_enabled", "mos_enabled", "wake_enabled"] as const;
  for (const k of boolKeys) {
    if (input[k] === undefined || input[k] === null) continue;
    out[k] = Boolean(input[k]);
  }
  if (input.mos_input_device !== undefined && input.mos_input_device !== null) {
    out.mos_input_device = String(input.mos_input_device);
  }
  if (input.wake_text !== undefined && input.wake_text !== null) {
    const t = String(input.wake_text).trim();
    if (t) out.wake_text = t;
  }
  return out;
}

/**
 * Write a full JSON config for one job (base device.yaml + overrides).
 * Returns absolute path suitable for `echo_eval --config`.
 */
export function materializeJobConfig(
  outPath: string,
  overrides: RunParamOverrides | null | undefined
): string {
  const clean = sanitizeOverrides(overrides);
  const abs = path.resolve(outPath);
  runPythonJson(
    "from echo_eval.config import materialize_config\n" +
      "import json, sys\n" +
      "overrides = json.loads(sys.argv[1])\n" +
      "path = materialize_config(sys.argv[2], overrides or None)\n" +
      "print(json.dumps({\"path\": str(path)}))",
    [JSON.stringify(clean), abs]
  );
  return abs;
}
