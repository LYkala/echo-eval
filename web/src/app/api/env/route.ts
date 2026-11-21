import { NextResponse } from "next/server";
import { runEnvCheck } from "@/lib/env-check";
import { ensureDemoJob } from "@/lib/jobs";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  const result = runEnvCheck();
  if (!result.ok) {
    ensureDemoJob();
  }
  return NextResponse.json(result);
}
