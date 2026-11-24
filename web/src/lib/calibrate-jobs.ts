import { spawn, spawnSync, type ChildProcess } from "child_process";
import fs from "fs";
import path from "path";
import { getDataset } from "./datasets";
import { hasActiveRunJob } from "./jobs";
import { ensureDir, jobsRoot, repoRoot, safeId } from "./paths";

export type CalibrateStatus =
  | "queued"
  | "running"
  | "passed"
  | "failed"
  | "error"
  | "cancelled";

export type CalibrateStepRow = {
  status: string;
  case_id: string;
  step_text: string;
  attempts?: number;
  error?: string;
};

export type CalibrateJobMeta = {
  id: string;
  kind: "precondition_calibrate";
  datasetId: string;
  datasetName: string;
  caseId: string | null;
  backend: "auto" | "vl" | "mock";
  retryFailed: boolean;
  force: boolean;
  status: CalibrateStatus;
  createdAt: string;
  startedAt?: string;
  finishedAt?: string;
  pid?: number;
  logPath?: string;
  error?: string;
  passed?: number;
  failed?: number;
  skipped?: number;
  steps: CalibrateStepRow[];
  logTail?: string;
};

const live = new Map<string, ChildProcess>();

function pythonBin(): string {
  return process.env.ECHO_EVAL_PYTHON || (process.platform === "win32" ? "python" : "python3");
}

function jobDir(id: string): string {
  return path.join(jobsRoot(), safeId(id));
}

function writeJob(job: CalibrateJobMeta): void {
  const dir = jobDir(job.id);
  ensureDir(dir);
  fs.writeFileSync(path.join(dir, "job.json"), JSON.stringify(job, null, 2));
}

function parseLogSummary(log: string): {
  passed?: number;
  failed?: number;
  skipped?: number;
  steps: CalibrateStepRow[];
} {
  const steps: CalibrateStepRow[] = [];
  for (const line of log.split(/\r?\n/)) {
    const m = line.match(
      /^\[(PASSED|FAILED|SKIPPED|ERROR)\]\s+(\S+)\s+step=(['"])(.*?)\3\s+attempts=(\d+)/i
    );
    if (m) {
      const errMatch = line.match(/\serr=(.*)$/);
      steps.push({
        status: m[1].toLowerCase(),
        case_id: m[2],
        step_text: m[4],
        attempts: Number(m[5]),
        error: errMatch ? errMatch[1].trim() : undefined,
      });
    }
  }
  const sm = log.match(
    /calibrate done:\s*passed=(\d+)\s+failed=(\d+)\s+skipped=(\d+)/i
  );
  const outSummary = log.match(
    /summary:\s*passed=(\d+)\s+failed=(\d+)\s+skipped=(\d+)/i
  );
  const src = outSummary || sm;
  return {
    passed: src ? Number(src[1]) : undefined,
    failed: src ? Number(src[2]) : undefined,
    skipped: src ? Number(src[3]) : undefined,
    steps,
  };
}

export function readCalibrateJob(id: string): CalibrateJobMeta | null {
  const p = path.join(jobDir(id), "job.json");
  if (!fs.existsSync(p)) return null;
  const job = JSON.parse(fs.readFileSync(p, "utf-8")) as CalibrateJobMeta;
  if (job.kind !== "precondition_calibrate") return null;
  if (job.logPath && fs.existsSync(job.logPath)) {
    try {
      const log = fs.readFileSync(job.logPath, "utf-8");
      job.logTail = log.slice(-12000);
      const parsed = parseLogSummary(log);
      if (parsed.steps.length) job.steps = parsed.steps;
      if (parsed.passed !== undefined) job.passed = parsed.passed;
      if (parsed.failed !== undefined) job.failed = parsed.failed;
      if (parsed.skipped !== undefined) job.skipped = parsed.skipped;
    } catch {
      /* ignore */
    }
  }
  return job;
}

export function listCalibrateJobs(): CalibrateJobMeta[] {
  const root = jobsRoot();
  if (!fs.existsSync(root)) return [];
  const out: CalibrateJobMeta[] = [];
  for (const id of fs.readdirSync(root)) {
    const j = readCalibrateJob(id);
    if (j) out.push(j);
  }
  return out.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export function hasActiveCalibrateJob(): boolean {
  return listCalibrateJobs().some((j) => j.status === "queued" || j.status === "running");
}

function newJobId(): string {
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  return `cal_${stamp}`;
}

export function createAndStartCalibrateJob(opts: {
  datasetId: string;
  caseId?: string | null;
  backend?: "auto" | "vl" | "mock";
  retryFailed?: boolean;
  force?: boolean;
}): CalibrateJobMeta {
  if (hasActiveRunJob()) {
    throw new Error("有测试任务正在运行，请先停止后再校准（设备互斥）");
  }
  if (hasActiveCalibrateJob()) {
    throw new Error("已有校准任务在运行");
  }

  const ds = getDataset(opts.datasetId);
  if (!ds) throw new Error(`数据集不存在: ${opts.datasetId}`);

  const id = newJobId();
  const dir = jobDir(id);
  ensureDir(dir);
  const logPath = path.join(dir, "calibrate.log");

  const job: CalibrateJobMeta = {
    id,
    kind: "precondition_calibrate",
    datasetId: ds.id,
    datasetName: ds.name,
    caseId: opts.caseId || null,
    backend: opts.backend || "auto",
    retryFailed: Boolean(opts.retryFailed),
    force: Boolean(opts.force),
    status: "queued",
    createdAt: new Date().toISOString(),
    logPath,
    steps: [],
  };
  writeJob(job);

  const root = repoRoot();
  const args = [
    "-m",
    "echo_eval",
    "precondition-calibrate",
    "--excel",
    ds.storedPath,
    "--backend",
    job.backend,
  ];
  if (job.caseId) args.push("--case-id", job.caseId);
  if (job.retryFailed) args.push("--retry-failed");
  if (job.force) args.push("--force");

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
      PYTHONIOENCODING: "utf-8",
      PYTHONUTF8: "1",
    },
    stdio: ["ignore", logFd, logFd],
    windowsHide: true,
  });
  live.set(id, child);
  job.pid = child.pid;
  writeJob(job);

  child.on("close", (code) => {
    live.delete(id);
    try {
      fs.closeSync(logFd);
    } catch {
      /* ignore */
    }
    const latest = readCalibrateJob(id) || job;
    latest.finishedAt = new Date().toISOString();
    if (latest.status === "cancelled") {
      writeJob(latest);
      return;
    }
    if (code === 0) latest.status = "passed";
    else if ((latest.failed || 0) > 0) latest.status = "failed";
    else {
      latest.status = "error";
      if (!latest.error) latest.error = `进程退出码 ${code}`;
    }
    writeJob(latest);
  });

  return job;
}

export function cancelCalibrateJob(id: string): CalibrateJobMeta | null {
  const job = readCalibrateJob(id);
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

export function runCoverageCheck(opts: {
  datasetId: string;
  caseId?: string | null;
}): Record<string, unknown> {
  const ds = getDataset(opts.datasetId);
  if (!ds) throw new Error(`数据集不存在: ${opts.datasetId}`);
  const root = repoRoot();
  const script = `
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(${JSON.stringify(path.join(root, "src"))})))
from echo_eval.cases.excel_cases import load_cases
from echo_eval.config import load_config
from echo_eval.precondition.coverage import build_coverage_report
cfg = load_config()
cases = load_cases(${JSON.stringify(ds.storedPath)}, case_id=${opts.caseId ? JSON.stringify(opts.caseId) : "None"}, default_wait_sec=float(cfg.run.step_wait_sec))
report = build_coverage_report(cases, cfg=cfg.precondition)
out = {
  "counts": report.counts,
  "skip_case_ids": sorted(report.skip_case_ids),
  "cases": [
    {
      "case_id": c.case_id,
      "name": c.name,
      "skip": c.skip,
      "skip_reason": c.skip_reason,
      "ready": c.ready,
      "steps": [
        {
          "index": s.index,
          "text": s.text,
          "status": s.status,
          "deterministic": s.deterministic,
          "ready": s.ready,
          "reason": s.reason,
        }
        for s in c.steps
      ],
    }
    for c in report.cases
  ],
}
print(json.dumps(out, ensure_ascii=False))
`;
  const r = spawnSync(pythonBin(), ["-c", script], {
    cwd: root,
    encoding: "utf-8",
    env: {
      ...process.env,
      PYTHONPATH: path.join(root, "src"),
      PYTHONIOENCODING: "utf-8",
      PYTHONUTF8: "1",
    },
    timeout: 60000,
  });
  if (r.status !== 0) {
    throw new Error((r.stderr || r.stdout || "coverage check failed").toString().slice(0, 800));
  }
  const text = (r.stdout || "").trim();
  const line = text.split(/\r?\n/).filter(Boolean).pop() || "{}";
  return JSON.parse(line) as Record<string, unknown>;
}
