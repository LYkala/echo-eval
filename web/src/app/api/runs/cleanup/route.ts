import { NextResponse } from "next/server";
import {
  formatBytes,
  purgeAllFinishedJobMedia,
  purgeJobMedia,
  readJob,
} from "@/lib/jobs";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Slim historical jobs: delete capture.mp4 / frames / steps wav / mos audio.
 * Keeps job.json, evaluation, reports. Never touches precondition_cache.
 */
export async function POST(req: Request) {
  let body: { mode?: string; jobId?: string } = {};
  try {
    body = (await req.json()) as typeof body;
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const mode = String(body.mode || "purge_media").trim();
  if (mode !== "purge_media") {
    return NextResponse.json(
      { error: "目前仅支持 mode=purge_media（瘦身）" },
      { status: 400 }
    );
  }

  const jobId = String(body.jobId || "").trim();
  if (jobId) {
    if (!readJob(jobId)) {
      return NextResponse.json({ error: "任务不存在" }, { status: 404 });
    }
    const result = purgeJobMedia(jobId);
    if (!result.ok && !result.skipped) {
      return NextResponse.json({ error: result.reason || "清理失败", ...result }, { status: 409 });
    }
    return NextResponse.json({
      ...result,
      bytesFreedLabel: formatBytes(result.bytesFreed),
    });
  }

  const batch = purgeAllFinishedJobMedia();
  return NextResponse.json({
    mode: "purge_media",
    allFinished: true,
    ...batch,
    bytesFreedLabel: formatBytes(batch.bytesFreed),
  });
}
