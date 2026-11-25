import { NextResponse } from "next/server";
import { spawnSync } from "child_process";
import {
  BUILTIN_DATASET_ID,
  BUILTIN_V012_ID,
  getDataset,
} from "@/lib/datasets";
import { pythonChildEnv } from "@/lib/python-env";
import { repoRoot } from "@/lib/paths";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

function runPythonJson(code: string, args: string[] = [], timeoutMs = 120_000): unknown {
  const r = spawnSync(pythonBin(), ["-c", code, ...args], {
    cwd: repoRoot(),
    env: pythonChildEnv(),
    encoding: "utf-8",
    windowsHide: true,
    timeout: timeoutMs,
  });
  if (r.error) throw r.error;
  if (r.status !== 0) {
    throw new Error((r.stderr || r.stdout || `python exit ${r.status}`).trim());
  }
  const text = (r.stdout || "").trim();
  if (!text) throw new Error("python returned empty output");
  // Last JSON line (synth may print [echo-eval] warnings to stdout)
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  const last = lines[lines.length - 1] || text;
  return JSON.parse(last);
}

function resolveExcelPath(datasetId: string): string {
  if (
    datasetId !== BUILTIN_DATASET_ID &&
    datasetId !== BUILTIN_V012_ID &&
    datasetId !== "__builtin_v011__" &&
    !getDataset(datasetId)
  ) {
    throw new Error("数据集不存在");
  }
  const meta = getDataset(datasetId);
  if (!meta?.storedPath) throw new Error("数据集路径缺失");
  return meta.storedPath;
}

export async function GET(req: Request) {
  const url = new URL(req.url);
  const libraryDir = (url.searchParams.get("library_dir") || "").trim() || "assets/tts_library";
  try {
    const data = runPythonJson(
      "from echo_eval.stimulus.tts_library import resolve_library_root, library_status\n" +
        "import json, sys\n" +
        "root = resolve_library_root(sys.argv[1])\n" +
        "print(json.dumps(library_status(root), ensure_ascii=False))",
      [libraryDir],
      30_000
    );
    return NextResponse.json(data);
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 500 });
  }
}

export async function POST(req: Request) {
  let body: {
    action?: string;
    datasetId?: string;
    text?: string;
    case_id?: string;
    step_index?: number;
    backend?: string;
    voice?: string;
    rate?: number;
    force?: boolean;
    library_dir?: string;
    wake_text?: string;
    wake_enabled?: boolean;
  } = {};
  try {
    body = (await req.json()) as typeof body;
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const action = String(body.action || "").trim().toLowerCase();
  const libraryDir = String(body.library_dir || "assets/tts_library").trim() || "assets/tts_library";
  const backend = String(body.backend || "edge").trim().toLowerCase() || "edge";
  const voice = String(body.voice || "zh-CN-XiaoxiaoNeural").trim();
  const rate = Number(body.rate ?? 0) || 0;
  const force = Boolean(body.force);

  try {
    if (action === "status") {
      const data = runPythonJson(
        "from echo_eval.stimulus.tts_library import resolve_library_root, library_status\n" +
          "import json, sys\n" +
          "root = resolve_library_root(sys.argv[1])\n" +
          "print(json.dumps(library_status(root), ensure_ascii=False))",
        [libraryDir],
        30_000
      );
      return NextResponse.json(data);
    }

    if (action === "prebuild") {
      const datasetId = String(body.datasetId || "").trim();
      if (!datasetId) {
        return NextResponse.json({ error: "缺少 datasetId" }, { status: 400 });
      }
      const excel = resolveExcelPath(datasetId);
      const wakeText =
        body.wake_enabled === false
          ? ""
          : String(body.wake_text || "小翼管家").trim() || "小翼管家";
      const data = runPythonJson(
        "from echo_eval.stimulus.tts_library import resolve_library_root, prebuild_from_excel\n" +
          "from echo_eval.config import load_config\n" +
          "import json, sys\n" +
          "excel, lib_dir, backend, voice, force, wake = sys.argv[1:7]\n" +
          "cfg = load_config()\n" +
          "root = resolve_library_root(lib_dir)\n" +
          "r = prebuild_from_excel(\n" +
          "  excel, root,\n" +
          "  backend=backend, voice=voice, rate=int(cfg.tts.rate),\n" +
          "  ffmpeg_path=cfg.capture.ffmpeg_path,\n" +
          "  fallback_sapi=bool(cfg.tts.fallback_sapi),\n" +
          "  wake_text=wake, force=force=='1',\n" +
          "  default_wait_sec=float(cfg.run.step_wait_sec),\n" +
          ")\n" +
          "print(json.dumps(r, ensure_ascii=False))",
        [excel, libraryDir, backend, voice, force ? "1" : "0", wakeText],
        600_000
      );
      return NextResponse.json(data);
    }

    if (action === "custom") {
      const text = String(body.text || "").trim();
      if (!text) {
        return NextResponse.json({ error: "缺少 text" }, { status: 400 });
      }
      if (text.length > 200) {
        return NextResponse.json({ error: "文本过长（最多 200 字）" }, { status: 400 });
      }
      const caseId = String(body.case_id || "").trim();
      const stepIndex = Math.max(0, Math.floor(Number(body.step_index ?? 0) || 0));
      const data = runPythonJson(
        "from echo_eval.stimulus.tts_library import resolve_library_root, add_utterance\n" +
          "from echo_eval.config import load_config\n" +
          "import json, sys\n" +
          "text, lib_dir, backend, voice, case_id, step, force = sys.argv[1:8]\n" +
          "cfg = load_config()\n" +
          "root = resolve_library_root(lib_dir)\n" +
          "r = add_utterance(\n" +
          "  root, text=text, case_id=case_id, step_index=int(step),\n" +
          "  backend=backend, voice=voice, rate=int(cfg.tts.rate),\n" +
          "  ffmpeg_path=cfg.capture.ffmpeg_path,\n" +
          "  fallback_sapi=bool(cfg.tts.fallback_sapi),\n" +
          "  source='custom', force=force=='1',\n" +
          ")\n" +
          "print(json.dumps(r, ensure_ascii=False))",
        [text, libraryDir, backend, voice, caseId, String(stepIndex), force ? "1" : "0"],
        120_000
      );
      return NextResponse.json(data);
    }

    return NextResponse.json(
      { error: "unknown action（支持 status / prebuild / custom）" },
      { status: 400 }
    );
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 500 });
  }
}
