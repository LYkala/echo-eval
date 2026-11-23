import fs from "fs";
import path from "path";

/** Repo root (parent of web/). Override with ECHO_EVAL_ROOT. */
export function repoRoot(): string {
  if (process.env.ECHO_EVAL_ROOT) {
    return path.resolve(process.env.ECHO_EVAL_ROOT);
  }
  // web/ is cwd when next runs from web/
  const candidates = [
    path.resolve(process.cwd(), ".."),
    path.resolve(process.cwd()),
    path.resolve(__dirname, "../../.."),
  ];
  for (const c of candidates) {
    if (fs.existsSync(path.join(c, "src", "echo_eval")) && fs.existsSync(path.join(c, "web"))) {
      return c;
    }
  }
  return path.resolve(process.cwd(), "..");
}

export function webDataRoot(): string {
  return path.join(repoRoot(), "artifacts", "web");
}

export function datasetsRoot(): string {
  return path.join(webDataRoot(), "datasets");
}

export function jobsRoot(): string {
  return path.join(webDataRoot(), "jobs");
}

export function ensureDir(dir: string): void {
  fs.mkdirSync(dir, { recursive: true });
}

export function safeId(raw: string): string {
  return raw.replace(/[^a-zA-Z0-9._-]+/g, "_").slice(0, 80) || "id";
}
