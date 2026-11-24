import { NextResponse } from "next/server";
import { runCoverageCheck } from "@/lib/calibrate-jobs";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(req: Request) {
  const body = (await req.json()) as {
    datasetId?: string;
    caseId?: string | null;
  };
  if (!body.datasetId) {
    return NextResponse.json({ error: "缺少 datasetId" }, { status: 400 });
  }
  try {
    const report = runCoverageCheck({
      datasetId: body.datasetId,
      caseId: body.caseId || null,
    });
    return NextResponse.json(report);
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 500 });
  }
}
