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
};

export type RunParamDefaults = {
  step_wait_sec: number;
  head_delay_sec: number;
  tail_sec: number;
  fps: number;
  max_retries: number;
  step_settle_sec: number;
};

const FALLBACK_DEFAULTS: RunParamDefaults = {
  step_wait_sec: 3,
  head_delay_sec: 1,
  tail_sec: 5,
  fps: 1,
  max_retries: 3,
  step_settle_sec: 1,
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
    };
  } catch {
    return { ...FALLBACK_DEFAULTS };
  }
}

/** Keep only known numeric overrides; drop NaN / empty. */
export function sanitizeOverrides(input: RunParamOverrides | null | undefined): RunParamOverrides {
  if (!input) return {};
  const out: RunParamOverrides = {};
  const keys: (keyof RunParamOverrides)[] = [
    "step_wait_sec",
    "head_delay_sec",
    "tail_sec",
    "fps",
    "max_retries",
    "step_settle_sec",
  ];
  for (const k of keys) {
    const v = input[k];
    if (v === undefined || v === null || Number.isNaN(Number(v))) continue;
    const n = Number(v);
    if (k === "max_retries") out[k] = Math.max(1, Math.floor(n));
    else out[k] = Math.max(0, n);
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
