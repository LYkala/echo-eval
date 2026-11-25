import { NextResponse } from "next/server";
import { BUILTIN_DATASET_ID, BUILTIN_V012_ID, getDataset } from "@/lib/datasets";
import { createAndStartJob, ensureDemoJob, listJobs, readJob } from "@/lib/jobs";
import { runEnvCheck } from "@/lib/env-check";
import { sanitizeOverrides, type RunParamOverrides } from "@/lib/run-params";

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
    params?: RunParamOverrides;
  };

  if (body.demo) {
    // Always re-copy fixtures so「打开演示任务」不会卡在旧的单用例缓存上。
    const job = ensureDemoJob({ force: true });
    return NextResponse.json(job);
  }

  const datasetId = body.datasetId;
  if (!datasetId) {
    return NextResponse.json({ error: "缺少 datasetId" }, { status: 400 });
  }

  if (
    datasetId !== BUILTIN_DATASET_ID &&
    datasetId !== BUILTIN_V012_ID &&
    datasetId !== "__builtin_v011__" &&
    !getDataset(datasetId)
  ) {
    return NextResponse.json({ error: "数据集不存在" }, { status: 404 });
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
      datasetId,
      caseId: body.caseId || null,
      backend,
      params: sanitizeOverrides(body.params),
    });
    return NextResponse.json(job);
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 409 });
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
