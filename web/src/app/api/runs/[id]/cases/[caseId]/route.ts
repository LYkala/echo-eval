import { NextResponse } from "next/server";
import { getCaseDetail } from "@/lib/jobs";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(
  _: Request,
  ctx: { params: Promise<{ id: string; caseId: string }> }
) {
  const { id, caseId } = await ctx.params;
  const detail = getCaseDetail(id, caseId);
  if (!detail) return NextResponse.json({ error: "not found" }, { status: 404 });
  return NextResponse.json(detail);
}
