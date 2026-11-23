import { NextResponse } from "next/server";
import { repoRoot } from "@/lib/paths";
import fs from "fs";
import path from "path";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  const root = repoRoot();
  const script = path.join(root, "scripts", "env_doctor.py");
  const payload = {
    ok: true,
    time: new Date().toISOString(),
    cwd: process.cwd(),
    repoRoot: root,
    platform: process.platform,
    node: process.version,
    hasEnvDoctor: fs.existsSync(script),
    pythonHint: process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3"),
  };
  console.log(`[echo-eval][ping] ${JSON.stringify(payload)}`);
  return NextResponse.json(payload);
}
