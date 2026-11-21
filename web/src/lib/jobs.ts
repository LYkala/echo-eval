import { spawn, type ChildProcess } from "child_process";
import fs from "fs";
import path from "path";
import { getDataset } from "./datasets";
import { ensureDir, jobsRoot, repoRoot, safeId } from "./paths";

export type JobStatus = "queued" | "running" | "passed" | "failed" | "error" | "cancelled";

export type JobCaseResult = {
  case_id: string;
  name: string;
  passed: boolean | null;
  run_dir: string;
  error?: string;
  report_path?: string;
  evaluation_path?: string;
};

export type JobMeta = {
  id: string;
  datasetId: string;
  datasetName: string;
  caseId: string | null;
  backend: "auto" | "vl" | "mock";
  status: JobStatus;
  createdAt: string;
  startedAt?: string;
  finishedAt?: string;
  pid?: number;
  summaryPath?: string;
  logPath?: string;
  error?: string;
  results: JobCaseResult[];
  passed?: number;
  failed?: number;
  errors?: number;
  demo?: boolean;
};

const live = new Map<string, ChildProcess>();

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

function jobDir(id: string): string {
  return path.join(jobsRoot(), safeId(id));
}

function writeJob(job: JobMeta): void {
  const dir = jobDir(job.id);
  ensureDir(dir);
  fs.writeFileSync(path.join(dir, "job.json"), JSON.stringify(job, null, 2));
}

export function readJob(id: string): JobMeta | null {
  const p = path.join(jobDir(id), "job.json");
  if (!fs.existsSync(p)) return null;
  return JSON.parse(fs.readFileSync(p, "utf-8")) as JobMeta;
}

export function listJobs(): JobMeta[] {
  const root = jobsRoot();
  if (!fs.existsSync(root)) return [];
  const out: JobMeta[] = [];
  for (const id of fs.readdirSync(root)) {
    const j = readJob(id);
    if (j) out.push(j);
  }
  return out.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

function newJobId(): string {
  const d = new Date();
  const stamp = d.toISOString().replace(/[:.]/g, "-");
  return `job_${stamp}`;
}

/** Seed a browsable demo job when no device (cloud / preview). */
export function ensureDemoJob(): JobMeta {
  const existing = listJobs().find((j) => j.demo);
  if (existing) return existing;
  const id = "job_demo_sample";
  const dir = jobDir(id);
  ensureDir(dir);
  const caseDir = path.join(dir, "demo_case");
  const framesDir = path.join(caseDir, "frames");
  const stepsDir = path.join(caseDir, "steps");
  ensureDir(framesDir);
  ensureDir(stepsDir);

  // Minimal 1x1 PNG
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    "base64"
  );
  fs.writeFileSync(path.join(framesDir, "frame_0001.png"), png);
  fs.writeFileSync(path.join(framesDir, "frame_0002.png"), png);
  fs.writeFileSync(
    path.join(caseDir, "case.json"),
    JSON.stringify(
      {
        case_id: "demo_weather_001",
        name: "询问天气（样例）",
        steps: [
          { text: "小艺小艺", wait_sec: 2 },
          { text: "今天西安天气怎么样", wait_sec: 3 },
        ],
        expected: "界面出现天气相关结果",
        exceptions: ["界面出现天气相关结果"],
      },
      null,
      2
    )
  );
  fs.writeFileSync(
    path.join(caseDir, "evaluation.json"),
    JSON.stringify(
      {
        passed: true,
        backend: "mock",
        result_parsed: [
          {
            expectation: "界面出现天气相关结果",
            result: "success",
            reason: "样例数据：截帧与预期均存在",
            final_result: "界面出现天气相关结果",
          },
        ],
      },
      null,
      2
    )
  );
  fs.writeFileSync(
    path.join(caseDir, "report.md"),
    "# 样例报告\n\n这是演示数据，真实评测请在 PC1 连接设备后运行。\n"
  );
  // empty wav placeholder note
  fs.writeFileSync(path.join(stepsDir, "step_01.txt"), "小艺小艺");

  const job: JobMeta = {
    id,
    datasetId: "demo",
    datasetName: "sample_weather.csv（演示）",
    caseId: "demo_weather_001",
    backend: "mock",
    status: "passed",
    createdAt: new Date().toISOString(),
    finishedAt: new Date().toISOString(),
    demo: true,
    results: [
      {
        case_id: "demo_weather_001",
        name: "询问天气（样例）",
        passed: true,
        run_dir: caseDir,
        report_path: path.join(caseDir, "report.md"),
        evaluation_path: path.join(caseDir, "evaluation.json"),
      },
    ],
    passed: 1,
    failed: 0,
    errors: 0,
  };
  writeJob(job);
  return job;
}

export function createAndStartJob(opts: {
  datasetId: string;
  caseId?: string | null;
  backend?: "auto" | "vl" | "mock";
  forceDemo?: boolean;
}): JobMeta {
  if (opts.forceDemo || process.env.ECHO_EVAL_DEMO === "1") {
    return ensureDemoJob();
  }

  const ds = getDataset(opts.datasetId);
  if (!ds) throw new Error(`数据集不存在: ${opts.datasetId}`);

  const id = newJobId();
  const dir = jobDir(id);
  ensureDir(dir);
  const logPath = path.join(dir, "run.log");

  const job: JobMeta = {
    id,
    datasetId: ds.id,
    datasetName: ds.name,
    caseId: opts.caseId || null,
    backend: opts.backend || "auto",
    status: "queued",
    createdAt: new Date().toISOString(),
    logPath,
    results: [],
  };
  writeJob(job);

  const root = repoRoot();
  const args = [
    "-m",
    "echo_eval",
    "run",
    "--excel",
    ds.storedPath,
    "--backend",
    job.backend,
  ];
  if (job.caseId) {
    args.push("--case-id", job.caseId);
  }

  job.status = "running";
  job.startedAt = new Date().toISOString();
  writeJob(job);

  const logFd = fs.openSync(logPath, "a");
  const child = spawn(pythonBin(), args, {
    cwd: root,
    env: {
      ...process.env,
      PYTHONPATH: path.join(root, "src"),
      PYTHONUNBUFFERED: "1",
    },
    stdio: ["ignore", logFd, logFd],
    windowsHide: true,
  });
  live.set(id, child);
  job.pid = child.pid;
  writeJob(job);

  child.on("close", (code) => {
    live.delete(id);
    fs.closeSync(logFd);
    const latest = readJob(id) || job;
    latest.finishedAt = new Date().toISOString();

    // Parse summary from log or newest batch summary
    try {
      const log = fs.readFileSync(logPath, "utf-8");
      const m = log.match(/summary_path:\s*(.+)/);
      if (m) {
        latest.summaryPath = m[1].trim();
        if (fs.existsSync(latest.summaryPath)) {
          const summary = JSON.parse(fs.readFileSync(latest.summaryPath, "utf-8")) as {
            passed?: number;
            failed?: number;
            errors?: number;
            results?: JobCaseResult[];
          };
          latest.passed = summary.passed ?? 0;
          latest.failed = summary.failed ?? 0;
          latest.errors = summary.errors ?? 0;
          latest.results = summary.results || [];
        }
      }
    } catch (e) {
      latest.error = String(e);
    }

    if (latest.status === "cancelled") {
      writeJob(latest);
      return;
    }
    if (code === 0) latest.status = "passed";
    else if ((latest.failed || 0) > 0 && (latest.errors || 0) === 0) latest.status = "failed";
    else if (code && code !== 0) {
      latest.status = (latest.errors || 0) > 0 || !latest.results.length ? "error" : "failed";
      if (!latest.error) latest.error = `进程退出码 ${code}`;
    } else latest.status = "failed";
    writeJob(latest);
  });

  return job;
}

export function cancelJob(id: string): JobMeta | null {
  const job = readJob(id);
  if (!job) return null;
  const child = live.get(id);
  if (child && !child.killed) {
    child.kill("SIGTERM");
  } else if (job.pid) {
    try {
      process.kill(job.pid);
    } catch {
      /* already dead */
    }
  }
  job.status = "cancelled";
  job.finishedAt = new Date().toISOString();
  writeJob(job);
  live.delete(id);
  return job;
}

export function getCaseDetail(jobId: string, caseId: string) {
  const job = readJob(jobId);
  if (!job) return null;
  const row = job.results.find((r) => r.case_id === caseId);
  if (!row || !row.run_dir || !fs.existsSync(row.run_dir)) return null;

  const runDir = row.run_dir;
  const readJson = (name: string) => {
    const p = path.join(runDir, name);
    if (!fs.existsSync(p)) return null;
    try {
      return JSON.parse(fs.readFileSync(p, "utf-8"));
    } catch {
      return null;
    }
  };

  const framesDir = path.join(runDir, "frames");
  const frames = fs.existsSync(framesDir)
    ? fs
        .readdirSync(framesDir)
        .filter((f) => /\.(png|jpg|jpeg|webp)$/i.test(f))
        .sort()
        .map((f) => `/api/artifacts?job=${encodeURIComponent(jobId)}&case=${encodeURIComponent(caseId)}&file=frames/${encodeURIComponent(f)}`)
    : [];

  const stepsDir = path.join(runDir, "steps");
  const audios = fs.existsSync(stepsDir)
    ? fs
        .readdirSync(stepsDir)
        .filter((f) => /\.wav$/i.test(f))
        .sort()
        .map((f) => ({
          name: f,
          url: `/api/artifacts?job=${encodeURIComponent(jobId)}&case=${encodeURIComponent(caseId)}&file=steps/${encodeURIComponent(f)}`,
        }))
    : [];

  const reportPath = path.join(runDir, "report.md");
  const report = fs.existsSync(reportPath) ? fs.readFileSync(reportPath, "utf-8") : "";

  return {
    job,
    result: row,
    caseJson: readJson("case.json"),
    evaluation: readJson("evaluation.json"),
    timeline: readJson("timeline.json"),
    frames,
    audios,
    report,
  };
}
