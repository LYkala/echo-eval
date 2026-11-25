import { NextResponse } from "next/server";
import { readRunParamDefaults } from "@/lib/run-params";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Current run/calibrate defaults from configs/device.yaml (for console form). */
export async function GET() {
  try {
    const defaults = readRunParamDefaults();
    return NextResponse.json({ defaults, source: "configs/device.yaml" });
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 500 });
  }
}
