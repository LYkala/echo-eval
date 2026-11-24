import fs from "fs";
import path from "path";
import { datasetsRoot, ensureDir, repoRoot, safeId } from "./paths";

export const BUILTIN_DATASET_ID = "__builtin_sample__";
export const BUILTIN_V011_ID = "__builtin_v011__";

export type DatasetMeta = {
  id: string;
  name: string;
  filename: string;
  storedPath: string;
  uploadedAt: string;
  size: number;
  builtin?: boolean;
};

function weatherSamplePath(): string {
  return path.join(repoRoot(), "cases", "examples", "sample_weather.csv");
}

function v011SamplePath(): string {
  return path.join(repoRoot(), "cases", "examples", "sample_v011_android_iqiyi_10.csv");
}

function builtinFromFile(id: string, name: string, filePath: string): DatasetMeta | null {
  if (!fs.existsSync(filePath)) return null;
  const st = fs.statSync(filePath);
  return {
    id,
    name,
    filename: path.basename(filePath),
    storedPath: filePath,
    uploadedAt: st.mtime.toISOString(),
    size: st.size,
    builtin: true,
  };
}

export function getBuiltinDataset(): DatasetMeta | null {
  return builtinFromFile(
    BUILTIN_DATASET_ID,
    "sample_weather.csv（内置样例）",
    weatherSamplePath()
  );
}

export function getBuiltinV011Dataset(): DatasetMeta | null {
  return builtinFromFile(
    BUILTIN_V011_ID,
    "sample_v011_android_iqiyi_10.csv（内置·预置校准）",
    v011SamplePath()
  );
}

export function listBuiltinDatasets(): DatasetMeta[] {
  return [getBuiltinV011Dataset(), getBuiltinDataset()].filter(Boolean) as DatasetMeta[];
}

export function listDatasets(): DatasetMeta[] {
  const root = datasetsRoot();
  if (!fs.existsSync(root)) return [];
  const out: DatasetMeta[] = [];
  for (const id of fs.readdirSync(root)) {
    const metaPath = path.join(root, id, "meta.json");
    if (!fs.existsSync(metaPath)) continue;
    try {
      const meta = JSON.parse(fs.readFileSync(metaPath, "utf-8")) as DatasetMeta;
      // Prefer on-disk path under datasets root (meta.storedPath may be machine-specific).
      const localPath = path.join(root, id, meta.filename || "dataset.csv");
      if (fs.existsSync(localPath)) {
        meta.storedPath = localPath;
      }
      out.push(meta);
    } catch {
      /* skip */
    }
  }
  return out.sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt));
}

export function getDataset(id: string): DatasetMeta | null {
  if (id === BUILTIN_DATASET_ID) return getBuiltinDataset();
  if (id === BUILTIN_V011_ID) return getBuiltinV011Dataset();
  const sid = safeId(id);
  const metaPath = path.join(datasetsRoot(), sid, "meta.json");
  if (!fs.existsSync(metaPath)) return null;
  const meta = JSON.parse(fs.readFileSync(metaPath, "utf-8")) as DatasetMeta;
  const localPath = path.join(datasetsRoot(), sid, meta.filename || "dataset.csv");
  if (fs.existsSync(localPath)) {
    meta.storedPath = localPath;
  }
  return meta;
}

export function deleteDataset(id: string): void {
  if (id === BUILTIN_DATASET_ID || id === BUILTIN_V011_ID) {
    throw new Error("内置样例不可删除");
  }
  const sid = safeId(id);
  const dir = path.join(datasetsRoot(), sid);
  if (!fs.existsSync(dir)) {
    throw new Error("数据集不存在");
  }
  fs.rmSync(dir, { recursive: true, force: true });
}

export function resolveDownload(id: string): { meta: DatasetMeta; absolutePath: string } {
  const meta = getDataset(id);
  if (!meta) throw new Error("数据集不存在");
  const absolutePath = path.resolve(meta.storedPath);
  if (!fs.existsSync(absolutePath)) {
    throw new Error("数据集文件缺失");
  }
  // Guard: only serve files under datasets root or builtin sample paths.
  const root = path.resolve(datasetsRoot());
  const allowedBuiltins = [weatherSamplePath(), v011SamplePath()].map((p) => path.resolve(p));
  const allowed =
    allowedBuiltins.includes(absolutePath) ||
    absolutePath === path.resolve(root) ||
    absolutePath.startsWith(root + path.sep);
  if (!allowed) {
    throw new Error("非法路径");
  }
  return { meta, absolutePath };
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
