import { NextResponse } from "next/server";
import { repoRoot } from "@/lib/paths";
import fs from "fs";
import os from "os";
import path from "path";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  const root = repoRoot();
  const script = path.join(root, "scripts", "env_doctor.py");
  // Cursor Cloud agent workspaces live under /agent/… — not your Windows PC1.
  const cloudLike =
    root.includes("/agent/repos/") ||
    root.includes("\\agent\\repos\\") ||
    process.env.CURSOR_AGENT === "1" ||
    process.env.CURSOR_CLOUD_AGENT === "1";
  const payload = {
    ok: true,
    time: new Date().toISOString(),
    cwd: process.cwd(),
    repoRoot: root,
    hostname: os.hostname(),
    platform: process.platform,
    node: process.version,
    portHint: Number(process.env.PORT || process.env.ECHO_EVAL_WEB_PORT || 47391),
    hostRole: cloudLike ? "cloud_agent" : "local_pc",
    hostLabel: cloudLike
      ? "云端 Agent（不是你的 Windows 本机）"
      : "本机 PC1",
    hasEnvDoctor: fs.existsSync(script),
    pythonHint: process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3"),
  };
  console.log(`[echo-eval][ping] ${JSON.stringify(payload)}`);
  return NextResponse.json(payload);
}
