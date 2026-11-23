import path from "path";
import { repoRoot } from "./paths";

/** Env for spawning Python on Windows without GBK mojibake. */
export function pythonChildEnv(
  extra: Record<string, string | undefined> = {}
): NodeJS.ProcessEnv {
  const root = repoRoot();
  return {
    ...process.env,
    ...extra,
    PYTHONPATH: path.join(root, "src"),
    PYTHONUNBUFFERED: "1",
    PYTHONIOENCODING: "utf-8",
    PYTHONUTF8: "1",
  };
}
