import { NextResponse } from "next/server";
import { spawnSync } from "child_process";
import fs from "fs";
import os from "os";
import path from "path";
import { pythonChildEnv } from "@/lib/python-env";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

export async function POST(req: Request) {
  let body: { text?: string; voice?: string; backend?: string } = {};
  try {
    body = (await req.json()) as typeof body;
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const text = String(body.text || "小翼管家").trim().slice(0, 80) || "小翼管家";
  const voice = String(body.voice || "zh-CN-XiaoxiaoNeural").trim();
  const backend = String(body.backend || "edge").trim().toLowerCase() || "edge";
  if (backend !== "edge") {
    return NextResponse.json({ error: "preview 目前仅支持 edge" }, { status: 400 });
  }

  const tmp = path.join(os.tmpdir(), `echo_eval_tts_preview_${Date.now()}.wav`);
  const code =
    "from echo_eval.stimulus.tts import synthesize_edge\n" +
    "import json, sys\n" +
    "text, voice, out = sys.argv[1], sys.argv[2], sys.argv[3]\n" +
    "r = synthesize_edge(text, out, voice=voice)\n" +
    "print(json.dumps({'path': r.path, 'duration_sec': r.duration_sec, 'voice': r.voice}, ensure_ascii=False))";

  const r = spawnSync(pythonBin(), ["-c", code, text, voice, tmp], {
    env: pythonChildEnv(),
    encoding: "utf-8",
    windowsHide: true,
    timeout: 60_000,
  });
  if (r.status !== 0) {
    try {
      fs.unlinkSync(tmp);
    } catch {
      /* ignore */
    }
    return NextResponse.json(
      { error: (r.stderr || r.stdout || `python exit ${r.status}`).trim() },
      { status: 500 }
    );
  }
  if (!fs.existsSync(tmp)) {
    return NextResponse.json({ error: "preview wav missing" }, { status: 500 });
  }
  const buf = fs.readFileSync(tmp);
  try {
    fs.unlinkSync(tmp);
  } catch {
    /* ignore */
  }
  return new NextResponse(buf, {
    headers: {
      "Content-Type": "audio/wav",
      "Cache-Control": "no-store",
      "X-Echo-Eval-Voice": encodeURIComponent(voice),
    },
  });
}
