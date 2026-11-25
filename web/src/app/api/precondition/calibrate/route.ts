import { NextResponse } from "next/server";
import {
  createAndStartCalibrateJob,
  listCalibrateJobs,
  type CalibrateScope,
} from "@/lib/calibrate-jobs";
import { runEnvCheck } from "@/lib/env-check";
import { sanitizeOverrides, type RunParamOverrides } from "@/lib/run-params";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({ items: listCalibrateJobs() });
}

export async function POST(req: Request) {
  const body = (await req.json()) as {
    datasetId?: string;
    caseId?: string | null;
    backend?: "auto" | "vl" | "mock";
    scope?: CalibrateScope;
    /** @deprecated use scope */
    retryFailed?: boolean;
    /** @deprecated use scope */
    force?: boolean;
    params?: RunParamOverrides;
  };
  if (!body.datasetId) {
    return NextResponse.json({ error: "缺少 datasetId" }, { status: 400 });
  }

  const env = runEnvCheck();
  const backend = body.backend || "auto";
  if (!env.ok && backend !== "mock") {
    return NextResponse.json(
      {
        error: "环境检查未通过，无法启动真机校准。可先修复 ADB/设备，或改用 mock 联调。",
        env,
      },
      { status: 409 }
    );
  }

  const scope: CalibrateScope =
    body.scope === "all" || body.force ? "all" : "pending";

  try {
    const job = createAndStartCalibrateJob({
      datasetId: body.datasetId,
      caseId: body.caseId || null,
      backend,
      scope,
      params: sanitizeOverrides(body.params),
    });
    return NextResponse.json(job);
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 409 });
  }
}
