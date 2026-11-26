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

function parseRange(
  header: string | null,
  size: number
): { start: number; end: number } | null {
  if (!header || !header.startsWith("bytes=") || size <= 0) return null;
  const spec = header.slice(6).trim();
  // Only support a single range
  const part = spec.split(",")[0]?.trim() || "";
  const m = /^(\d*)-(\d*)$/.exec(part);
  if (!m) return null;
  let start: number;
  let end: number;
  if (m[1] === "" && m[2] === "") return null;
  if (m[1] === "") {
    // suffix: bytes=-N
    const suffix = Number(m[2]);
    if (!Number.isFinite(suffix) || suffix <= 0) return null;
    start = Math.max(0, size - suffix);
    end = size - 1;
  } else {
    start = Number(m[1]);
    end = m[2] === "" ? size - 1 : Number(m[2]);
  }
  if (!Number.isFinite(start) || !Number.isFinite(end) || start < 0 || start >= size) {
    return null;
  }
  end = Math.min(end, size - 1);
  if (end < start) return null;
  return { start, end };
}

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

  const st = fs.statSync(abs);
  const size = st.size;
  const ext = path.extname(abs).toLowerCase();
  const contentType = MIME[ext] || "application/octet-stream";
  const rangeHeader = req.headers.get("range") || req.headers.get("Range");
  const range = parseRange(rangeHeader, size);

  if (rangeHeader && !range) {
    return new NextResponse(null, {
      status: 416,
      headers: {
        "Content-Range": `bytes */${size}`,
        "Accept-Ranges": "bytes",
      },
    });
  }

  if (range) {
    const { start, end } = range;
    const chunkSize = end - start + 1;
    const fd = fs.openSync(abs, "r");
    try {
      const buf = Buffer.alloc(chunkSize);
      fs.readSync(fd, buf, 0, chunkSize, start);
      return new NextResponse(buf, {
        status: 206,
        headers: {
          "Content-Type": contentType,
          "Content-Length": String(chunkSize),
          "Content-Range": `bytes ${start}-${end}/${size}`,
          "Accept-Ranges": "bytes",
          "Cache-Control": "private, max-age=60",
        },
      });
    } finally {
      fs.closeSync(fd);
    }
  }

  // Full body (still advertise Accept-Ranges so browsers enable seeking)
  const buf = fs.readFileSync(abs);
  return new NextResponse(buf, {
    status: 200,
    headers: {
      "Content-Type": contentType,
      "Content-Length": String(size),
      "Accept-Ranges": "bytes",
      "Cache-Control": "private, max-age=60",
    },
  });
}
