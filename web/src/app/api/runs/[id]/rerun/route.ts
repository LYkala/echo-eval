import { NextResponse } from "next/server";
import { rerunJobOverwrite } from "@/lib/jobs";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(req: Request, ctx: { params: Promise<{ id: string }> }) {
  const { id } = await ctx.params;
  let body: { mode?: string; caseId?: string } = {};
  try {
    body = (await req.json()) as { mode?: string; caseId?: string };
  } catch {
    body = {};
  }
  const mode = body.mode === "case" ? "case" : "failed";
  try {
    const job = rerunJobOverwrite(id, {
      mode,
      caseId: body.caseId,
    });
    return NextResponse.json(job);
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 400 });
  }
}
