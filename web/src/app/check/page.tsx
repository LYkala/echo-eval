import Link from "next/link";
import { spawnSync } from "child_process";
import fs from "fs";
import path from "path";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const STEPS = [
  "config",
  "python",
  "adb",
  "device",
  "ffmpeg",
  "audio",
  "tts",
  "vl",
] as const;

function repoRoot(): string {
  if (process.env.ECHO_EVAL_ROOT) return path.resolve(process.env.ECHO_EVAL_ROOT);
  const candidates = [path.resolve(process.cwd(), ".."), process.cwd()];
  for (const c of candidates) {
    if (fs.existsSync(path.join(c, "src", "echo_eval")) && fs.existsSync(path.join(c, "web"))) {
      return c;
    }
  }
  return path.resolve(process.cwd(), "..");
}

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

function runStep(step: string) {
  const root = repoRoot();
  const script = path.join(root, "scripts", "env_doctor.py");
  console.log(`[echo-eval][check-page] step=${step} root=${root}`);
  if (!fs.existsSync(script)) {
    return {
      ok: false,
      detail: `缺少脚本 ${script}`,
      raw: "",
    };
  }
  const proc = spawnSync(pythonBin(), [script, "--quick", "--step", step], {
    encoding: "utf-8",
    cwd: root,
    env: { ...process.env, PYTHONPATH: path.join(root, "src"), PYTHONUNBUFFERED: "1" },
    timeout: step === "device" ? 12000 : 10000,
    windowsHide: true,
  });
  const stdout = (proc.stdout || "").trim();
  const stderr = (proc.stderr || "").trim();
  console.log(`[echo-eval][check-page] status=${proc.status} stderr=${stderr.slice(0, 200)}`);
  if (proc.error || !stdout) {
    return {
      ok: false,
      detail: String(proc.error || stderr || `无输出 exit=${proc.status}`),
      raw: stdout,
    };
  }
  try {
    const line = stdout.split(/\r?\n/).filter(Boolean).pop() || "{}";
    const parsed = JSON.parse(line) as {
      items?: { ok: boolean; label: string; detail: string }[];
    };
    const item = parsed.items?.[0];
    return {
      ok: Boolean(item?.ok),
      detail: item ? `${item.label}: ${item.detail}` : "无 item",
      raw: line,
    };
  } catch (e) {
    return { ok: false, detail: `解析失败 ${String(e)}`, raw: stdout };
  }
}

export default async function CheckPage({
  searchParams,
}: {
  searchParams: Promise<{ run?: string }>;
}) {
  const sp = await searchParams;
  const shouldRun = sp.run === "1";
  const results = shouldRun
    ? STEPS.map((step) => {
        const r = runStep(step);
        return { step, ...r };
      })
    : [];

  const ping = {
    time: new Date().toISOString(),
    cwd: process.cwd(),
    root: repoRoot(),
    platform: process.platform,
    node: process.version,
    hasScript: fs.existsSync(path.join(repoRoot(), "scripts", "env_doctor.py")),
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="font-display text-3xl font-semibold">无脚本环境检查</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          此页不依赖浏览器 React。若首页一直“加载脚本”，用本页排查。完成后可回
          <Link className="mx-1 text-primary underline" href="/">
            工作台
          </Link>
          。
        </p>
      </div>

      <section className="rounded-lg border bg-card p-4 text-sm">
        <h2 className="font-medium">服务端状态</h2>
        <pre className="mt-2 overflow-auto rounded bg-black/90 p-3 text-xs text-green-100">
          {JSON.stringify(ping, null, 2)}
        </pre>
      </section>

      <form method="GET" action="/check">
        <input type="hidden" name="run" value="1" />
        <button
          type="submit"
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
        >
          开始检查（服务端执行）
        </button>
      </form>

      {shouldRun ? (
        <ul className="space-y-2">
          {results.map((r) => (
            <li key={r.step} className="rounded-md border bg-card px-3 py-2 text-sm">
              <div className="flex items-center justify-between gap-3">
                <span className="font-medium">{r.step}</span>
                <span style={{ color: r.ok ? "#047857" : "#be123c" }}>{r.ok ? "OK" : "失败"}</span>
              </div>
              <div className="mt-1 break-all text-xs text-muted-foreground">{r.detail}</div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">点击上方按钮后，结果会显示在这里；同时看 Bash 里 `[echo-eval][check-page]` 日志。</p>
      )}

      <section className="rounded-lg border bg-card p-4 text-sm text-muted-foreground">
        <p className="font-medium text-foreground">Bash 手动命令</p>
        <pre className="mt-2 overflow-auto rounded bg-black/90 p-3 text-xs text-green-100">{`curl http://127.0.0.1:3847/api/ping
curl "http://127.0.0.1:3847/api/env/step?step=config"
cd .. && python scripts/env_doctor.py --quick --step adb`}</pre>
      </section>
    </div>
  );
}
