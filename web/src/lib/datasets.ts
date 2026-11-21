import fs from "fs";
import path from "path";
import { datasetsRoot, ensureDir, safeId } from "./paths";

export type DatasetMeta = {
  id: string;
  name: string;
  filename: string;
  storedPath: string;
  uploadedAt: string;
  size: number;
};

export function listDatasets(): DatasetMeta[] {
  const root = datasetsRoot();
  if (!fs.existsSync(root)) return [];
  const out: DatasetMeta[] = [];
  for (const id of fs.readdirSync(root)) {
    const metaPath = path.join(root, id, "meta.json");
    if (!fs.existsSync(metaPath)) continue;
    try {
      out.push(JSON.parse(fs.readFileSync(metaPath, "utf-8")) as DatasetMeta);
    } catch {
      /* skip */
    }
  }
  return out.sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt));
}

export function getDataset(id: string): DatasetMeta | null {
  const metaPath = path.join(datasetsRoot(), safeId(id), "meta.json");
  if (!fs.existsSync(metaPath)) return null;
  return JSON.parse(fs.readFileSync(metaPath, "utf-8")) as DatasetMeta;
}

export async function saveUpload(file: File): Promise<DatasetMeta> {
  const original = file.name || "dataset.csv";
  const ext = path.extname(original).toLowerCase();
  if (![".csv", ".xlsx", ".xlsm", ".txt"].includes(ext)) {
    throw new Error("仅支持 .csv / .xlsx");
  }
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const id = safeId(`${stamp}_${path.basename(original, ext)}`);
  const dir = path.join(datasetsRoot(), id);
  ensureDir(dir);
  const storedName = `dataset${ext}`;
  const storedPath = path.join(dir, storedName);
  const buf = Buffer.from(await file.arrayBuffer());
  fs.writeFileSync(storedPath, buf);
  const meta: DatasetMeta = {
    id,
    name: original,
    filename: storedName,
    storedPath,
    uploadedAt: new Date().toISOString(),
    size: buf.length,
  };
  fs.writeFileSync(path.join(dir, "meta.json"), JSON.stringify(meta, null, 2));
  return meta;
}
