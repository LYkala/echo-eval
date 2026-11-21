import { NextResponse } from "next/server";
import { runEnvCheck } from "@/lib/env-check";
import { ensureDemoJob } from "@/lib/jobs";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const result = runEnvCheck();
    if (!result.ok) {
      ensureDemoJob();
    }
    return NextResponse.json(result);
  } catch (e) {
    return NextResponse.json(
      {
        ok: false,
        demoMode: true,
        items: [],
        checkedAt: new Date().toISOString(),
        error: String(e),
      },
      { status: 500 }
    );
  }
}
