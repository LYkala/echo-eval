import { NextResponse } from "next/server";
import { spawnSync } from "child_process";
import { pythonChildEnv } from "@/lib/python-env";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

export async function GET(req: Request) {
  const url = new URL(req.url);
  const curated = url.searchParams.get("curated") !== "0";
  const locale = url.searchParams.get("locale") || "zh-CN";

  const code =
    "from echo_eval.stimulus.tts import list_edge_voices\n" +
    "import json, sys\n" +
    "locale = sys.argv[1]\n" +
    "curated = sys.argv[2] == '1'\n" +
    "print(json.dumps({'voices': list_edge_voices(locale_prefix=locale, curated_only=curated)}, ensure_ascii=False))";

  const r = spawnSync(pythonBin(), ["-c", code, locale, curated ? "1" : "0"], {
    env: pythonChildEnv(),
    encoding: "utf-8",
    windowsHide: true,
  });
  if (r.status !== 0) {
    return NextResponse.json(
      { error: (r.stderr || r.stdout || `python exit ${r.status}`).trim(), voices: [] },
      { status: 500 }
    );
  }
  try {
    const data = JSON.parse((r.stdout || "").trim() || "{}");
    return NextResponse.json(data);
  } catch (e) {
    return NextResponse.json({ error: String(e), voices: [] }, { status: 500 });
  }
}
