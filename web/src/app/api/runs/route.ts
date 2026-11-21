import { NextResponse } from "next/server";
import fs from "fs";
import path from "path";
import { datasetsRoot, ensureDir, repoRoot, safeId } from "@/lib/paths";
import { createAndStartJob, ensureDemoJob, listJobs, readJob } from "@/lib/jobs";
import { runEnvCheck } from "@/lib/env-check";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  ensureDemoJob();
  return NextResponse.json({ items: listJobs() });
}

export async function POST(req: Request) {
  const body = (await req.json()) as {
    datasetId?: string;
    caseId?: string | null;
    backend?: "auto" | "vl" | "mock";
    demo?: boolean;
  };

  if (body.demo) {
    const job = ensureDemoJob();
    return NextResponse.json(job);
  }

  let datasetId = body.datasetId;
  if (!datasetId) {
    return NextResponse.json({ error: "缺少 datasetId" }, { status: 400 });
  }

  // Materialize builtin sample into datasets store if needed
  if (datasetId === "__builtin_sample__") {
    const sample = path.join(repoRoot(), "cases", "examples", "sample_weather.csv");
    const id = "builtin_sample_weather";
    const dir = path.join(datasetsRoot(), id);
    ensureDir(dir);
    const dest = path.join(dir, "dataset.csv");
    if (!fs.existsSync(dest)) {
      fs.copyFileSync(sample, dest);
      fs.writeFileSync(
        path.join(dir, "meta.json"),
        JSON.stringify(
          {
            id,
            name: "sample_weather.csv",
            filename: "dataset.csv",
            storedPath: dest,
            uploadedAt: new Date().toISOString(),
            size: fs.statSync(dest).size,
          },
          null,
          2
        )
      );
    }
    datasetId = id;
  }

  const env = runEnvCheck();
  const backend = body.backend || "auto";

  // If environment not ready, only allow mock demo
  if (!env.ok) {
    if (backend !== "mock" && !body.demo) {
      return NextResponse.json(
        {
          error: "环境检查未通过，无法启动真机测试。可先用演示任务预览界面，或修复 ADB/设备后重试。",
          env,
        },
        { status: 409 }
      );
    }
    // mock without device still needs adb for record — force demo job
    const job = ensureDemoJob();
    return NextResponse.json({ ...job, note: "环境未就绪，已返回演示任务" });
  }

  try {
    const job = createAndStartJob({
      datasetId: safeId(datasetId),
      caseId: body.caseId || null,
      backend,
    });
    return NextResponse.json(job);
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 500 });
  }
}

export async function PATCH(req: Request) {
  const body = (await req.json()) as { id?: string; action?: string };
  if (!body.id) return NextResponse.json({ error: "缺少 id" }, { status: 400 });
  if (body.action === "refresh") {
    const job = readJob(body.id);
    if (!job) return NextResponse.json({ error: "not found" }, { status: 404 });
    return NextResponse.json(job);
  }
  return NextResponse.json({ error: "unknown action" }, { status: 400 });
}
