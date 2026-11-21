import { NextResponse } from "next/server";
import fs from "fs";
import path from "path";
import { getCaseDetail, readJob } from "@/lib/jobs";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const MIME: Record<string, string> = {
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".wav": "audio/wav",
  ".mp4": "video/mp4",
  ".json": "application/json",
  ".md": "text/markdown; charset=utf-8",
};

export async function GET(req: Request) {
  const url = new URL(req.url);
  const jobId = url.searchParams.get("job");
  const caseId = url.searchParams.get("case");
  const file = url.searchParams.get("file");
  if (!jobId || !caseId || !file) {
    return NextResponse.json({ error: "job, case, file required" }, { status: 400 });
  }
  if (file.includes("..") || path.isAbsolute(file)) {
    return NextResponse.json({ error: "invalid file" }, { status: 400 });
  }

  const job = readJob(jobId);
  if (!job) return NextResponse.json({ error: "job not found" }, { status: 404 });
  const detail = getCaseDetail(jobId, caseId);
  if (!detail?.result.run_dir) {
    return NextResponse.json({ error: "case not found" }, { status: 404 });
  }

  const abs = path.resolve(detail.result.run_dir, file);
  const root = path.resolve(detail.result.run_dir);
  if (!abs.startsWith(root + path.sep) && abs !== root) {
    return NextResponse.json({ error: "path denied" }, { status: 403 });
  }
  if (!fs.existsSync(abs) || !fs.statSync(abs).isFile()) {
    return NextResponse.json({ error: "file missing" }, { status: 404 });
  }

  const buf = fs.readFileSync(abs);
  const ext = path.extname(abs).toLowerCase();
  return new NextResponse(buf, {
    headers: {
      "Content-Type": MIME[ext] || "application/octet-stream",
      "Cache-Control": "private, max-age=60",
    },
  });
}
