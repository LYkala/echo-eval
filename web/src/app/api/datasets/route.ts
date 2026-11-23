import { NextResponse } from "next/server";
import { getBuiltinDataset, listDatasets, saveUpload } from "@/lib/datasets";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  const builtin = getBuiltinDataset();
  const items = listDatasets();
  return NextResponse.json({ items: builtin ? [builtin, ...items] : items });
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
