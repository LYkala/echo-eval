import { NextResponse } from "next/server";
import fs from "fs";
import path from "path";
import { listDatasets, saveUpload } from "@/lib/datasets";
import { repoRoot } from "@/lib/paths";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  const items = listDatasets();
  const sample = path.join(repoRoot(), "cases", "examples", "sample_weather.csv");
  const builtin = fs.existsSync(sample)
    ? [
        {
          id: "__builtin_sample__",
          name: "sample_weather.csv（内置样例）",
          filename: "sample_weather.csv",
          storedPath: sample,
          uploadedAt: "",
          size: fs.statSync(sample).size,
          builtin: true,
        },
      ]
    : [];
  return NextResponse.json({ items: [...builtin, ...items] });
}

export async function POST(req: Request) {
  const form = await req.formData();
  const file = form.get("file");
  if (!(file instanceof File)) {
    return NextResponse.json({ error: "缺少 file" }, { status: 400 });
  }
  try {
    const meta = await saveUpload(file);
    return NextResponse.json(meta);
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 400 });
  }
}
