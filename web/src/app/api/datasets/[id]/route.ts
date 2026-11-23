import { NextResponse } from "next/server";
import fs from "fs";
import path from "path";
import { deleteDataset, resolveDownload } from "@/lib/datasets";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ id: string }> };

function contentTypeFor(filename: string): string {
  const ext = path.extname(filename).toLowerCase();
  if (ext === ".csv" || ext === ".txt") return "text/csv; charset=utf-8";
  if (ext === ".xlsx") {
    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  }
  if (ext === ".xlsm") return "application/vnd.ms-excel.sheet.macroEnabled.12";
  return "application/octet-stream";
}

export async function GET(_req: Request, ctx: Ctx) {
  const { id } = await ctx.params;
  try {
    const { meta, absolutePath } = resolveDownload(id);
    const buf = fs.readFileSync(absolutePath);
    const downloadName = meta.name.replace(/[^\w.\u4e00-\u9fff-]+/g, "_") || meta.filename;
    return new NextResponse(buf, {
      status: 200,
      headers: {
        "Content-Type": contentTypeFor(meta.filename),
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(downloadName)}`,
        "Content-Length": String(buf.length),
        "Cache-Control": "no-store",
      },
    });
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 404 });
  }
}

export async function DELETE(_req: Request, ctx: Ctx) {
  const { id } = await ctx.params;
  try {
    deleteDataset(id);
    return NextResponse.json({ ok: true, id });
  } catch (e) {
    const message = String(e);
    const status = message.includes("不可删除") ? 400 : 404;
    return NextResponse.json({ error: message }, { status });
  }
}
