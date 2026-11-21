import { NextResponse } from "next/server";
import { ENV_STEP_ORDER, type EnvStepId } from "@/lib/env-check";
import { spawnSync } from "child_process";
import fs from "fs";
import path from "path";
import { repoRoot } from "@/lib/paths";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

export async function GET(req: Request) {
  const url = new URL(req.url);
  const step = (url.searchParams.get("step") || "") as EnvStepId;
  const known = ENV_STEP_ORDER.find((s) => s.id === step);
  if (!known) {
    return NextResponse.json(
      { error: `未知 step，可选: ${ENV_STEP_ORDER.map((s) => s.id).join(",")}` },
      { status: 400 }
    );
  }

  const root = repoRoot();
  const script = path.join(root, "scripts", "env_doctor.py");
  if (!fs.existsSync(script)) {
    return NextResponse.json(
      {
        step,
        label: known.label,
        item: {
          key: step,
          label: known.label,
          ok: false,
          detail: `缺少脚本: ${script}`,
          required: true,
        },
      },
      { status: 200 }
    );
  }

  const timeoutMs = step === "device" ? 12000 : 10000;
  const proc = spawnSync(pythonBin(), [script, "--quick", "--step", step], {
    encoding: "utf-8",
    cwd: root,
    env: { ...process.env, PYTHONPATH: path.join(root, "src"), PYTHONUNBUFFERED: "1" },
    timeout: timeoutMs,
    windowsHide: true,
  });

  if (proc.error) {
    const err = proc.error as NodeJS.ErrnoException;
    const detail =
      err.code === "ETIMEDOUT"
        ? `检查超时（>${timeoutMs / 1000}s）`
        : `${err.message}. 请确认已安装 Python，且可执行: ${pythonBin()} ${script}`;
    return NextResponse.json({
      step,
      label: known.label,
      item: {
        key: step,
        label: known.label,
        ok: false,
        detail,
        required: !["audio", "tts", "vl"].includes(step),
      },
    });
  }

  const stdout = (proc.stdout || "").trim();
  const stderr = (proc.stderr || "").trim();
  if (!stdout) {
    return NextResponse.json({
      step,
      label: known.label,
      item: {
        key: step,
        label: known.label,
        ok: false,
        detail: stderr || `python 无输出 (exit=${proc.status}). 命令: ${pythonBin()} ${script} --quick --step ${step}`,
        required: true,
      },
    });
  }

  try {
    const line = stdout.split(/\r?\n/).filter(Boolean).pop() || "{}";
    const parsed = JSON.parse(line) as {
      items?: { key: string; label: string; ok: boolean; detail: string; required: boolean }[];
      serial?: string;
    };
    const item = parsed.items?.[0] || {
      key: step,
      label: known.label,
      ok: false,
      detail: "无检查结果",
      required: true,
    };
    return NextResponse.json({
      step,
      label: known.label,
      serial: parsed.serial,
      item,
    });
  } catch (e) {
    return NextResponse.json({
      step,
      label: known.label,
      item: {
        key: step,
        label: known.label,
        ok: false,
        detail: `解析失败: ${String(e)}; out=${stdout.slice(0, 180)}`,
        required: true,
      },
    });
  }
}
