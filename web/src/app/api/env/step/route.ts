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
  const t0 = Date.now();
  console.log(`[echo-eval][env/step] >>> start step=${step}`);

  const known = ENV_STEP_ORDER.find((s) => s.id === step);
  if (!known) {
    console.log(`[echo-eval][env/step] <<< unknown step=${step}`);
    return NextResponse.json(
      { error: `未知 step，可选: ${ENV_STEP_ORDER.map((s) => s.id).join(",")}` },
      { status: 400 }
    );
  }

  const root = repoRoot();
  const script = path.join(root, "scripts", "env_doctor.py");
  console.log(`[echo-eval][env/step] repoRoot=${root}`);
  console.log(`[echo-eval][env/step] script=${script} exists=${fs.existsSync(script)}`);
  console.log(`[echo-eval][env/step] python=${pythonBin()} cwd=${process.cwd()}`);

  if (!fs.existsSync(script)) {
    console.log(`[echo-eval][env/step] <<< missing script`);
    return NextResponse.json({
      step,
      label: known.label,
      item: {
        key: step,
        label: known.label,
        ok: false,
        detail: `缺少脚本: ${script}`,
        required: true,
      },
    });
  }

  const timeoutMs = step === "device" ? 12000 : 10000;
  const args = [script, "--quick", "--step", step];
  console.log(`[echo-eval][env/step] spawn: ${pythonBin()} ${args.join(" ")} timeout=${timeoutMs}ms`);

  const proc = spawnSync(pythonBin(), args, {
    encoding: "utf-8",
    cwd: root,
    env: { ...process.env, PYTHONPATH: path.join(root, "src"), PYTHONUNBUFFERED: "1" },
    timeout: timeoutMs,
    windowsHide: true,
  });

  console.log(
    `[echo-eval][env/step] spawn done status=${proc.status} signal=${proc.signal} error=${proc.error || ""} elapsed=${Date.now() - t0}ms`
  );
  if (proc.stdout) console.log(`[echo-eval][env/step] stdout: ${proc.stdout.trim().slice(0, 500)}`);
  if (proc.stderr) console.log(`[echo-eval][env/step] stderr: ${proc.stderr.trim().slice(0, 500)}`);

  if (proc.error) {
    const err = proc.error as NodeJS.ErrnoException;
    const detail =
      err.code === "ETIMEDOUT"
        ? `检查超时（>${timeoutMs / 1000}s）`
        : `${err.message}. 请确认已安装 Python，且可执行: ${pythonBin()} ${script}`;
    console.log(`[echo-eval][env/step] <<< spawn error: ${detail}`);
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
    const detail =
      stderr ||
      `python 无输出 (exit=${proc.status}). 命令: ${pythonBin()} ${script} --quick --step ${step}`;
    console.log(`[echo-eval][env/step] <<< empty stdout: ${detail}`);
    return NextResponse.json({
      step,
      label: known.label,
      item: {
        key: step,
        label: known.label,
        ok: false,
        detail,
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
    console.log(
      `[echo-eval][env/step] <<< ok=${item.ok} key=${item.key} detail=${String(item.detail).slice(0, 120)}`
    );
    return NextResponse.json({
      step,
      label: known.label,
      serial: parsed.serial,
      item,
    });
  } catch (e) {
    console.log(`[echo-eval][env/step] <<< parse error: ${String(e)}`);
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
