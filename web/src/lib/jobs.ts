import { spawn, type ChildProcess } from "child_process";
import fs from "fs";
import path from "path";
import { getDataset } from "./datasets";
import { ensureDir, jobsRoot, repoRoot, safeId } from "./paths";
import {
  materializeJobConfig,
  sanitizeOverrides,
  type RunParamOverrides,
} from "./run-params";

export { aggregateJobMetrics } from "./metrics-aggregate";

export type JobStatus = "queued" | "running" | "passed" | "failed" | "error" | "cancelled";

/** Parallel metrics (ASR / latency / MOS) brief for list views. */
export type CaseMetricsBrief = {
  asr_accuracy?: number | null;
  asr_passed?: number;
  asr_failed?: number;
  asr_total?: number;
  latency_mean_sec?: number | null;
  latency_measured?: number;
  latency_no_action?: number;
  latency_total?: number;
  mos_mean?: number | null;
  mos_scored?: number;
  mos_na?: number;
  mos_total?: number;
};

export type JobCaseResult = {
  case_id: string;
  name: string;
  passed: boolean | null;
  run_dir: string;
  error?: string;
  report_path?: string;
  evaluation_path?: string;
  metrics?: CaseMetricsBrief;
};

export type JobProgressCase = {
  index: number;
  case_id: string;
  name: string;
  status: string;
  phase?: string;
  phase_label?: string;
  device_status?: string;
  device_label?: string;
  judge_status?: string;
  judge_label?: string;
  /** Populated once device capture finishes (for cancel/partial history). */
  run_dir?: string;
};

export type JobProgress = {
  total: number;
  done?: number;
  device_done?: number;
  judge_done?: number;
  current_index: number;
  current_case_id: string;
  current_name: string;
  phase: string;
  phase_label: string;
  device_phase_label?: string;
  judge_phase_label?: string;
  updated_at?: string;
  cases: JobProgressCase[];
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
  note?: string;
  progress?: JobProgress;
  /** Per-job overrides (not written to device.yaml). */
  params?: RunParamOverrides;
  /** Large media (mp4/frames/wav) removed; text results kept. */
  mediaPurged?: boolean;
  mediaPurgedAt?: string;
  mediaBytesFreed?: number;
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

function readProgress(id: string): JobProgress | undefined {
  const p = path.join(jobDir(id), "progress.json");
  if (!fs.existsSync(p)) return undefined;
  try {
    return JSON.parse(fs.readFileSync(p, "utf-8")) as JobProgress;
  } catch {
    return undefined;
  }
}

function readJsonFile(filePath: string): Record<string, unknown> | null {
  if (!fs.existsSync(filePath)) return null;
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf-8")) as Record<string, unknown>;
  } catch {
    return null;
  }
}

/** Load ASR / latency / MOS summary brief from a case run_dir. */
export function loadCaseMetricsBrief(runDir: string): CaseMetricsBrief | undefined {
  if (!runDir || !fs.existsSync(runDir)) return undefined;
  const asr = readJsonFile(path.join(runDir, "asr_summary.json"));
  const lat = readJsonFile(path.join(runDir, "latency_summary.json"));
  const mos = readJsonFile(path.join(runDir, "mos_summary.json"));
  const meta = readJsonFile(path.join(runDir, "run_meta.json"));
  const asrSrc = asr || (meta?.asr as Record<string, unknown> | undefined);
  const latSrc = lat || (meta?.latency as Record<string, unknown> | undefined);
  const mosSrc = mos || (meta?.mos as Record<string, unknown> | undefined);
  if (!asrSrc && !latSrc && !mosSrc) return undefined;
  const brief: CaseMetricsBrief = {};
  if (asrSrc) {
    brief.asr_total = Number(asrSrc.total ?? 0);
    brief.asr_passed = Number(asrSrc.passed ?? 0);
    brief.asr_failed = Number(asrSrc.failed ?? 0);
    brief.asr_accuracy =
      asrSrc.accuracy === null || asrSrc.accuracy === undefined
        ? null
        : Number(asrSrc.accuracy);
  }
  if (latSrc) {
    brief.latency_total = Number(latSrc.total ?? 0);
    brief.latency_measured = Number(latSrc.measured ?? 0);
    brief.latency_no_action = Number(latSrc.no_action ?? 0);
    // Only expose a mean when at least one utterance was measured.
    brief.latency_mean_sec =
      brief.latency_measured > 0 &&
      latSrc.mean_sec !== null &&
      latSrc.mean_sec !== undefined
        ? Number(latSrc.mean_sec)
        : null;
  }
  if (mosSrc) {
    brief.mos_total = Number(mosSrc.total ?? 0);
    brief.mos_scored = Number(mosSrc.scored ?? 0);
    brief.mos_na = Number(mosSrc.na ?? 0);
    // Only expose a mean when at least one utterance was scored.
    brief.mos_mean =
      brief.mos_scored > 0 &&
      mosSrc.mean_mos !== null &&
      mosSrc.mean_mos !== undefined
        ? Number(mosSrc.mean_mos)
        : null;
  }
  return brief;
}

function safeCaseFolderSuffix(caseId: string): string {
  return (
    String(caseId || "")
      .split("")
      .map((ch) => (/[a-zA-Z0-9\-_.]/.test(ch) ? ch : "_"))
      .join("")
      .slice(0, 80) || "case"
  );
}

/** Locate capture/judge run_dir under artifacts/ for a case (cancel / partial). */
function findCaseRunDir(caseId: string, startedAt?: string): string {
  const root = path.join(repoRoot(), "artifacts");
  if (!fs.existsSync(root)) return "";
  const suffix = `_${safeCaseFolderSuffix(caseId)}`;
  const minMs = startedAt ? Date.parse(startedAt) - 60_000 : 0;
  let best = "";
  let bestMs = 0;
  let names: string[] = [];
  try {
    names = fs.readdirSync(root);
  } catch {
    return "";
  }
  for (const name of names) {
    if (!name.endsWith(suffix) && name !== safeCaseFolderSuffix(caseId)) continue;
    const dir = path.join(root, name);
    let st: fs.Stats;
    try {
      st = fs.statSync(dir);
    } catch {
      continue;
    }
    if (!st.isDirectory()) continue;
    if (minMs && st.mtimeMs < minMs) continue;
    const caseJson = readJsonFile(path.join(dir, "case.json"));
    if (caseJson && String(caseJson.case_id || "") && String(caseJson.case_id) !== caseId) {
      continue;
    }
    // Prefer dirs that look like a real capture.
    const hasCapture =
      fs.existsSync(path.join(dir, "capture.mp4")) ||
      fs.existsSync(path.join(dir, "timeline.json")) ||
      fs.existsSync(path.join(dir, "run_meta.json")) ||
      fs.existsSync(path.join(dir, "evaluation.json"));
    if (!hasCapture && !caseJson) continue;
    if (st.mtimeMs >= bestMs) {
      bestMs = st.mtimeMs;
      best = dir;
    }
  }
  return best;
}

function passedFromJudgeStatus(judge?: string): boolean | null {
  if (judge === "passed") return true;
  if (judge === "failed") return false;
  return null;
}

function recountJob(job: JobMeta, results: JobCaseResult[]): void {
  job.results = results;
  job.passed = results.filter((r) => r.passed === true).length;
  job.failed = results.filter((r) => r.passed === false).length;
  job.errors = results.filter(
    (r) => r.passed === null && (Boolean(r.error) || !r.run_dir)
  ).length;
}

/**
 * Build/merge case results from progress.json + artifacts so running/cancelled
 * jobs still show finished cases in history.
 */
export function harvestJobResults(job: JobMeta): JobCaseResult[] {
  const byId = new Map<string, JobCaseResult>();
  for (const r of job.results || []) {
    if (r?.case_id) byId.set(r.case_id, { ...r });
  }

  const progress = job.progress || readProgress(job.id);
  const cases = progress?.cases || [];
  for (const c of cases) {
    const device = c.device_status || "";
    const judge = c.judge_status || "";
    const started =
      device !== "pending" ||
      !["pending", ""].includes(judge) ||
      Boolean(c.run_dir);
    if (!started) continue;

    const existing = byId.get(c.case_id);
    let runDir = (c.run_dir || existing?.run_dir || "").trim();
    if (!runDir || !fs.existsSync(runDir)) {
      runDir = findCaseRunDir(c.case_id, job.startedAt || job.createdAt);
    }

    let passed = passedFromJudgeStatus(judge);
    if (passed === null && runDir) {
      const ev = readJsonFile(path.join(runDir, "evaluation.json"));
      if (ev && typeof ev.passed === "boolean") passed = ev.passed;
      else if (existing && typeof existing.passed === "boolean") passed = existing.passed;
    } else if (passed === null && existing && typeof existing.passed === "boolean") {
      passed = existing.passed;
    }

    const row: JobCaseResult = {
      case_id: c.case_id,
      name: c.name || existing?.name || c.case_id,
      passed,
      run_dir: runDir || existing?.run_dir || "",
      error: existing?.error,
      report_path:
        existing?.report_path ||
        (runDir && fs.existsSync(path.join(runDir, "report.md"))
          ? path.join(runDir, "report.md")
          : undefined),
      evaluation_path:
        existing?.evaluation_path ||
        (runDir && fs.existsSync(path.join(runDir, "evaluation.json"))
          ? path.join(runDir, "evaluation.json")
          : undefined),
    };
    byId.set(c.case_id, row);
  }

  // Prefer progress order, but always keep sibling results that a partial
  // rerun's progress.json no longer lists (single-case / failed-only overwrite).
  const progressIds = (progress?.cases || []).map((c) => c.case_id).filter(Boolean);
  const progressIdSet = new Set(progressIds);
  const resultIds = Array.from(byId.keys());
  const hasSiblingsOutsideProgress = resultIds.some((id) => !progressIdSet.has(id));

  let orderedIds: string[];
  if (hasSiblingsOutsideProgress && resultIds.length) {
    orderedIds = [...resultIds];
    for (const id of progressIds) {
      if (!orderedIds.includes(id)) orderedIds.push(id);
    }
  } else if (progressIds.length) {
    orderedIds = progressIds.filter((id) => byId.has(id));
  } else {
    orderedIds = resultIds;
  }

  const out = orderedIds
    .map((id) => byId.get(id))
    .filter((r): r is JobCaseResult => Boolean(r));

  return out.map((r) => {
    if (!r.run_dir) return r;
    const metrics = loadCaseMetricsBrief(r.run_dir);
    return metrics ? { ...r, metrics } : r;
  });
}

function writeProgress(id: string, progress: JobProgress): void {
  const p = path.join(jobDir(id), "progress.json");
  ensureDir(jobDir(id));
  fs.writeFileSync(p, JSON.stringify(progress, null, 2));
}

/** Merge a partial-rerun progress snapshot back into the previous full list. */
export function mergeProgressForRerun(
  previous: JobProgress | undefined,
  incoming: JobProgress | undefined,
  updatedCaseIds: string[]
): JobProgress | undefined {
  if (!incoming) return previous;
  if (!previous?.cases?.length) return incoming;
  const updated = new Set(updatedCaseIds.filter(Boolean));
  if (!updated.size) return incoming;

  const byId = new Map(previous.cases.map((c) => [c.case_id, { ...c }]));
  for (const c of incoming.cases || []) {
    if (!c?.case_id || !updated.has(c.case_id)) continue;
    const old = byId.get(c.case_id);
    byId.set(c.case_id, old ? { ...old, ...c, index: old.index } : { ...c });
  }

  const cases: JobProgressCase[] = [];
  const seen = new Set<string>();
  for (const c of previous.cases) {
    const row = byId.get(c.case_id);
    if (!row || seen.has(c.case_id)) continue;
    cases.push(row);
    seen.add(c.case_id);
  }
  for (const c of incoming.cases || []) {
    if (!c?.case_id || seen.has(c.case_id)) continue;
    cases.push({ ...c, index: cases.length + 1 });
    seen.add(c.case_id);
  }
  cases.forEach((c, i) => {
    c.index = i + 1;
  });

  const deviceDone = cases.filter((c) =>
    ["done", "error"].includes(c.device_status || "")
  ).length;
  const judgeDone = cases.filter((c) =>
    ["passed", "failed", "error", "skipped"].includes(c.judge_status || "")
  ).length;
  const done = cases.filter(
    (c) =>
      ["passed", "failed", "error"].includes(c.status) &&
      ["done", "error"].includes(c.device_status || "") &&
      ["passed", "failed", "error", "skipped"].includes(c.judge_status || "")
  ).length;

  return {
    ...previous,
    ...incoming,
    total: cases.length,
    device_done: deviceDone,
    judge_done: judgeDone,
    done,
    cases,
  };
}

function statusFromResults(job: JobMeta, exitCode: number | null): JobStatus {
  const results = job.results || [];
  const failed = results.filter((r) => r.passed === false).length;
  const errors = results.filter(
    (r) => r.passed === null && (Boolean(r.error) || !r.run_dir)
  ).length;
  if (errors > 0) return "error";
  if (failed > 0) return "failed";
  if (results.length > 0 && results.every((r) => r.passed === true)) return "passed";
  if (exitCode === 0) return "passed";
  if (exitCode && exitCode !== 0) return "error";
  return "failed";
}

function artifactUrl(jobId: string, caseId: string, file: string): string {
  return `/api/artifacts?job=${encodeURIComponent(jobId)}&case=${encodeURIComponent(caseId)}&file=${encodeURIComponent(file)}`;
}

function listMediaUrls(
  runDir: string,
  jobId: string,
  caseId: string,
  subdir: string,
  re: RegExp
): { name: string; url: string }[] {
  const dir = path.join(runDir, subdir);
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((f) => re.test(f))
    .sort()
    .map((f) => ({
      name: f,
      url: artifactUrl(jobId, caseId, `${subdir}/${f}`),
    }));
}

export function readJob(id: string): JobMeta | null {
  const p = path.join(jobDir(id), "job.json");
  if (!fs.existsSync(p)) return null;
  const raw = JSON.parse(fs.readFileSync(p, "utf-8")) as JobMeta & { kind?: string };
  if (raw.kind === "precondition_calibrate") return null;
  const job = raw as JobMeta;
  const progress = readProgress(id);
  if (progress) job.progress = progress;

  // Running / cancelled / incomplete: merge progress + artifacts so history
  // shows cases that already finished device/judge even without summary.json.
  const needsHarvest =
    job.status === "running" ||
    job.status === "queued" ||
    job.status === "cancelled" ||
    !job.results?.length ||
    job.results.some((r) => !r.run_dir);
  if (needsHarvest) {
    const harvested = harvestJobResults(job);
    if (harvested.length) {
      recountJob(job, harvested);
    }
  }

  // Attach parallel metrics from each case run_dir when available.
  if (Array.isArray(job.results)) {
    job.results = job.results.map((r) => {
      if (!r.run_dir) return r;
      if (r.metrics) return r;
      const metrics = loadCaseMetricsBrief(r.run_dir);
      return metrics ? { ...r, metrics } : r;
    });
  }
  return job;
}

export function listJobs(): JobMeta[] {
  const root = jobsRoot();
  if (!fs.existsSync(root)) return [];
  const out: JobMeta[] = [];
  for (const id of fs.readdirSync(root)) {
    const j = readJob(id);
    // Skip calibrate jobs stored alongside run jobs.
    if (j && (j as { kind?: string }).kind === "precondition_calibrate") continue;
    if (j) out.push(j);
  }
  return out.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export function hasActiveRunJob(): boolean {
  return listJobs().some((j) => j.status === "queued" || j.status === "running");
}

function newJobId(): string {
  const d = new Date();
  const stamp = d.toISOString().replace(/[:.]/g, "-");
  return `job_${stamp}`;
}

/** Bump when demo fixture set changes so local caches re-seed. */
const DEMO_FIXTURE_ID = "demo-two-cases-v3";

type DemoFixtureSpec = {
  fixtureDir: string;
  caseFolder: string;
};

/** Real packaged runs under web/fixtures/ (SYS2-009 pass + IQ-SCR-026 fail). */
function demoFixtureSpecs(): DemoFixtureSpec[] {
  const fixtures = path.join(repoRoot(), "web", "fixtures");
  return [
    { fixtureDir: path.join(fixtures, "demo_sys2_009"), caseFolder: "SYS2-009" },
    { fixtureDir: path.join(fixtures, "demo_iq_scr_026"), caseFolder: "IQ-SCR-026" },
  ];
}

function copyDirRecursive(src: string, dest: string): void {
  ensureDir(dest);
  for (const name of fs.readdirSync(src)) {
    const from = path.join(src, name);
    const to = path.join(dest, name);
    const st = fs.statSync(from);
    if (st.isDirectory()) copyDirRecursive(from, to);
    else fs.copyFileSync(from, to);
  }
}

function seedDemoCase(jobRoot: string, spec: DemoFixtureSpec): JobCaseResult {
  if (!fs.existsSync(spec.fixtureDir)) {
    throw new Error(`演示素材缺失: ${spec.fixtureDir}`);
  }
  const caseDir = path.join(jobRoot, spec.caseFolder);
  fs.rmSync(caseDir, { recursive: true, force: true });
  copyDirRecursive(spec.fixtureDir, caseDir);

  const caseJson = readJsonFile(path.join(caseDir, "case.json")) || {};
  const caseId = String(caseJson.case_id || spec.caseFolder);
  const caseName = String(caseJson.name || caseId);
  const evalJson = readJsonFile(path.join(caseDir, "evaluation.json"));
  const guiPassed = evalJson?.passed === true;

  return {
    case_id: caseId,
    name: caseName,
    passed: guiPassed,
    run_dir: caseDir,
    report_path: path.join(caseDir, "report.md"),
    evaluation_path: path.join(caseDir, "evaluation.json"),
    metrics: loadCaseMetricsBrief(caseDir),
  };
}

function isLegacyDemoLayout(dir: string, specs: DemoFixtureSpec[]): boolean {
  if (fs.existsSync(path.join(dir, "demo_case"))) return true;
  const existing = readJob("job_demo_sample");
  if (!existing?.results?.length) return true;
  if (existing.results.length !== specs.length) return true;
  if (existing.results.some((r) => r.case_id === "demo_weather_001")) return true;
  const want = new Set(specs.map((s) => s.caseFolder));
  return existing.results.some((r) => !want.has(r.case_id));
}

/**
 * Seed a browsable multi-case demo job from packaged fixtures. MOS omitted.
 * Pass `{ force: true }` from「打开演示任务」so stale local caches are always replaced.
 */
export function ensureDemoJob(opts?: { force?: boolean }): JobMeta {
  const id = "job_demo_sample";
  const dir = jobDir(id);
  ensureDir(dir);
  const markerPath = path.join(dir, "DEMO_JOB.txt");
  const specs = demoFixtureSpecs();
  for (const s of specs) {
    if (!fs.existsSync(s.fixtureDir)) {
      throw new Error(
        `演示素材缺失: ${s.fixtureDir}。请确认已 git pull 且存在 web/fixtures/demo_sys2_009 与 demo_iq_scr_026。`
      );
    }
  }

  const markerOk =
    fs.existsSync(markerPath) &&
    fs.readFileSync(markerPath, "utf-8").trim() === DEMO_FIXTURE_ID;
  const casesPresent = specs.every((s) =>
    fs.existsSync(path.join(dir, s.caseFolder, "case.json"))
  );
  const needSeed =
    !!opts?.force ||
    !markerOk ||
    !casesPresent ||
    isLegacyDemoLayout(dir, specs);

  let results: JobCaseResult[];
  if (needSeed) {
    // Drop legacy single-case layout (demo_case/ / weather demo).
    const legacy = path.join(dir, "demo_case");
    if (fs.existsSync(legacy)) fs.rmSync(legacy, { recursive: true, force: true });
    results = specs.map((s) => seedDemoCase(dir, s));
    fs.writeFileSync(markerPath, `${DEMO_FIXTURE_ID}\n`, "utf-8");
  } else {
    results = specs.map((s) => {
      const caseDir = path.join(dir, s.caseFolder);
      const caseJson = readJsonFile(path.join(caseDir, "case.json")) || {};
      const caseId = String(caseJson.case_id || s.caseFolder);
      const caseName = String(caseJson.name || caseId);
      const evalJson = readJsonFile(path.join(caseDir, "evaluation.json"));
      return {
        case_id: caseId,
        name: caseName,
        passed: evalJson?.passed === true,
        run_dir: caseDir,
        report_path: path.join(caseDir, "report.md"),
        evaluation_path: path.join(caseDir, "evaluation.json"),
        metrics: loadCaseMetricsBrief(caseDir),
      };
    });
  }

  const passed = results.filter((r) => r.passed === true).length;
  const failed = results.filter((r) => r.passed === false).length;

  const job: JobMeta = {
    id,
    datasetId: "demo_field_samples",
    datasetName: "现场样例演示（SYS2-009 + IQ-SCR-026）",
    caseId: null,
    backend: "vl",
    status: failed > 0 ? "failed" : "passed",
    createdAt: "2026-11-24T12:36:22.000Z",
    finishedAt: "2026-11-24T12:40:05.000Z",
    demo: true,
    note: "SYS2-009（GUI PASS，时延≈1.2s 按截帧估算）+ IQ-SCR-026（GUI FAIL，ASR 热锅榜≠热播榜）；MOS 跳过",
    results,
    passed,
    failed,
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
  params?: RunParamOverrides;
}): JobMeta {
  if (opts.forceDemo || process.env.ECHO_EVAL_DEMO === "1") {
    return ensureDemoJob();
  }

  // Lazy import to avoid circular init with calibrate-jobs.
  // eslint-disable-next-line @typescript-eslint/no-require-imports -- break jobs↔calibrate-jobs cycle
  const { hasActiveCalibrateJob } = require("./calibrate-jobs") as typeof import("./calibrate-jobs");
  if (hasActiveCalibrateJob()) {
    throw new Error("有预置校准任务正在运行，请先停止后再开测（设备互斥）");
  }
  if (hasActiveRunJob()) {
    throw new Error("已有测试任务在运行");
  }

  const ds = getDataset(opts.datasetId);
  if (!ds) throw new Error(`数据集不存在: ${opts.datasetId}`);

  const id = newJobId();
  const dir = jobDir(id);
  ensureDir(dir);
  const logPath = path.join(dir, "run.log");
  const params = sanitizeOverrides(opts.params);

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
    params,
  };
  writeJob(job);

  const configPath = materializeJobConfig(path.join(dir, "device.override.json"), params);

  const root = repoRoot();
  const args = [
    "-m",
    "echo_eval",
    "run",
    "--config",
    configPath,
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

  const progressPath = path.join(dir, "progress.json");
  const logFd = fs.openSync(logPath, "a");
  const child = spawn(pythonBin(), args, {
    cwd: root,
    env: {
      ...process.env,
      PYTHONPATH: path.join(root, "src"),
      PYTHONUNBUFFERED: "1",
      PYTHONIOENCODING: "utf-8",
      PYTHONUTF8: "1",
      ECHO_EVAL_PROGRESS_FILE: progressPath,
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
    // Read raw job.json (avoid harvest side-effects before merging summary).
    const rawPath = path.join(jobDir(id), "job.json");
    let latest: JobMeta = job;
    try {
      latest = JSON.parse(fs.readFileSync(rawPath, "utf-8")) as JobMeta;
    } catch {
      latest = { ...job };
    }
    latest.finishedAt = new Date().toISOString();
    latest.progress = readProgress(id);

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

    // Always harvest partial cases (cancel mid-run, missing summary, empty run_dir).
    const harvested = harvestJobResults(latest);
    if (harvested.length) {
      // Prefer summary rows when present; fill gaps from harvest.
      if (latest.results?.length) {
        const byId = new Map(latest.results.map((r) => [r.case_id, r]));
        for (const h of harvested) {
          const cur = byId.get(h.case_id);
          if (!cur) byId.set(h.case_id, h);
          else if (!cur.run_dir && h.run_dir) byId.set(h.case_id, { ...cur, ...h, passed: cur.passed ?? h.passed });
        }
        recountJob(latest, Array.from(byId.values()));
      } else {
        recountJob(latest, harvested);
      }
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

/**
 * Re-run selected cases inside an existing job and **overwrite** those rows
 * in job.results (no child job).
 */
export function rerunJobOverwrite(
  jobId: string,
  opts: { mode: "case" | "failed"; caseId?: string }
): JobMeta {
  if (hasActiveRunJob()) {
    throw new Error("已有测试任务在运行，请先停止");
  }
  // eslint-disable-next-line @typescript-eslint/no-require-imports -- break jobs↔calibrate-jobs cycle
  const { hasActiveCalibrateJob } = require("./calibrate-jobs") as typeof import("./calibrate-jobs");
  if (hasActiveCalibrateJob()) {
    throw new Error("有预置校准任务正在运行，请先停止");
  }

  const job = readJob(jobId);
  if (!job) throw new Error("任务不存在");
  if (job.demo) throw new Error("演示任务不可重跑");
  if (["queued", "running"].includes(job.status)) {
    throw new Error("任务仍在运行");
  }

  const results = [...(job.results || [])];
  const previousProgress = readProgress(job.id) || job.progress;
  const originalCaseId = job.caseId;
  let caseIds: string[] = [];
  if (opts.mode === "case") {
    const cid = (opts.caseId || "").trim();
    if (!cid) throw new Error("缺少 caseId");
    caseIds = [cid];
  } else {
    caseIds = results
      .filter((r) => r.passed === false || (r.passed === null && Boolean(r.error)))
      .map((r) => r.case_id)
      .filter(Boolean);
    if (!caseIds.length) throw new Error("没有失败/错误用例可重跑");
  }

  const ds = getDataset(job.datasetId);
  if (!ds) throw new Error(`数据集不存在: ${job.datasetId}`);

  const dir = jobDir(job.id);
  ensureDir(dir);
  const logPath = job.logPath || path.join(dir, "run.log");
  const params = sanitizeOverrides(job.params);
  const configPath = materializeJobConfig(path.join(dir, "device.override.json"), params);
  const progressPath = path.join(dir, "progress.json");

  // Keep full history on disk; do not narrow job.caseId to the rerun subset.
  job.status = "running";
  job.startedAt = new Date().toISOString();
  job.finishedAt = undefined;
  job.error = undefined;
  job.results = results;
  job.progress = previousProgress;
  writeJob(job);

  fs.appendFileSync(
    logPath,
    `\n[echo-eval] rerun overwrite mode=${opts.mode} cases=${caseIds.join(",")}\n`,
    "utf-8"
  );

  const root = repoRoot();
  const args = [
    "-m",
    "echo_eval",
    "run",
    "--config",
    configPath,
    "--excel",
    ds.storedPath,
    "--backend",
    job.backend,
    "--case-id",
    caseIds.join(","),
  ];

  const logFd = fs.openSync(logPath, "a");
  const child = spawn(pythonBin(), args, {
    cwd: root,
    env: {
      ...process.env,
      PYTHONPATH: path.join(root, "src"),
      PYTHONUNBUFFERED: "1",
      PYTHONIOENCODING: "utf-8",
      PYTHONUTF8: "1",
      ECHO_EVAL_PROGRESS_FILE: progressPath,
    },
    stdio: ["ignore", logFd, logFd],
    windowsHide: true,
  });
  live.set(job.id, child);
  job.pid = child.pid;
  writeJob(job);

  child.on("close", (code) => {
    live.delete(job.id);
    fs.closeSync(logFd);
    const rawPath = path.join(jobDir(job.id), "job.json");
    let latest: JobMeta = job;
    try {
      latest = JSON.parse(fs.readFileSync(rawPath, "utf-8")) as JobMeta;
    } catch {
      latest = { ...job };
    }
    latest.finishedAt = new Date().toISOString();
    latest.caseId = originalCaseId;

    const incomingProgress = readProgress(job.id);
    const mergedProgress = mergeProgressForRerun(
      previousProgress,
      incomingProgress,
      caseIds
    );
    if (mergedProgress) {
      writeProgress(job.id, mergedProgress);
      latest.progress = mergedProgress;
    } else {
      latest.progress = incomingProgress;
    }

    let newRows: JobCaseResult[] = [];
    try {
      const log = fs.readFileSync(logPath, "utf-8");
      const matches = [...log.matchAll(/summary_path:\s*(.+)/g)];
      const lastSummary = matches.length ? matches[matches.length - 1][1].trim() : "";
      if (lastSummary && fs.existsSync(lastSummary)) {
        latest.summaryPath = lastSummary;
        const summary = JSON.parse(fs.readFileSync(lastSummary, "utf-8")) as {
          results?: JobCaseResult[];
        };
        newRows = summary.results || [];
      }
    } catch (e) {
      latest.error = String(e);
    }

    // Always start from the pre-rerun full result list, then overwrite only
    // the rerun case ids (summary may contain only the subset).
    const byId = new Map(results.map((r) => [r.case_id, r]));
    for (const r of latest.results || []) {
      if (r?.case_id && !byId.has(r.case_id)) byId.set(r.case_id, r);
    }
    for (const row of newRows) {
      if (row?.case_id && caseIds.includes(row.case_id)) byId.set(row.case_id, row);
    }
    const harvested = harvestJobResults({
      ...latest,
      results: Array.from(byId.values()),
      progress: latest.progress,
    });
    for (const h of harvested) {
      if (caseIds.includes(h.case_id)) byId.set(h.case_id, h);
    }
    // Preserve original order when possible.
    const ordered: JobCaseResult[] = [];
    const seen = new Set<string>();
    for (const r of results) {
      const row = byId.get(r.case_id);
      if (row) {
        ordered.push(row);
        seen.add(r.case_id);
      }
    }
    for (const [id, row] of byId) {
      if (!seen.has(id)) ordered.push(row);
    }
    recountJob(latest, ordered);

    latest.status = statusFromResults(latest, code);
    if (code && code !== 0 && (latest.errors || 0) > 0 && !latest.error) {
      latest.error = `重跑退出码 ${code}`;
    }
    writeJob(latest);
  });

  return job;
}

/** Subpaths under a case run_dir that dominate disk (safe to drop after judge). */
const PURGE_MEDIA_FILES = ["capture.mp4"] as const;
const PURGE_MEDIA_DIRS = [
  "frames",
  "asr_frames",
  "post_speech_frames",
  "steps",
  "mos_audio",
] as const;

function artifactsRoot(): string {
  return path.resolve(repoRoot(), "artifacts");
}

function isSafeArtifactsPath(abs: string): boolean {
  const root = artifactsRoot();
  const resolved = path.resolve(abs);
  if (resolved !== root && !resolved.startsWith(root + path.sep)) return false;
  const rel = path.relative(root, resolved).replace(/\\/g, "/");
  if (!rel || rel.startsWith("..")) return false;
  if (rel === "precondition_cache" || rel.startsWith("precondition_cache/")) return false;
  if (rel === "web/datasets" || rel.startsWith("web/datasets/")) return false;
  return true;
}

function rmPathBytes(target: string): number {
  if (!fs.existsSync(target)) return 0;
  let freed = 0;
  const st = fs.statSync(target);
  if (st.isFile()) {
    freed = st.size;
    fs.unlinkSync(target);
    return freed;
  }
  if (st.isDirectory()) {
    for (const name of fs.readdirSync(target)) {
      freed += rmPathBytes(path.join(target, name));
    }
    try {
      fs.rmdirSync(target);
    } catch {
      /* non-empty or race */
    }
  }
  return freed;
}

function collectJobRunDirs(job: JobMeta): string[] {
  const dirs = new Set<string>();
  for (const r of job.results || []) {
    const d = (r.run_dir || "").trim();
    if (d) dirs.add(path.resolve(d));
  }
  for (const c of job.progress?.cases || []) {
    const d = (c.run_dir || "").trim();
    if (d) dirs.add(path.resolve(d));
  }
  // Also rediscover under artifacts/ for partial jobs
  for (const r of harvestJobResults(job)) {
    const d = (r.run_dir || "").trim();
    if (d) dirs.add(path.resolve(d));
  }
  return [...dirs].filter((d) => isSafeArtifactsPath(d) && fs.existsSync(d));
}

export type PurgeMediaResult = {
  jobId: string;
  ok: boolean;
  skipped?: boolean;
  reason?: string;
  bytesFreed: number;
  pathsRemoved: number;
  job?: JobMeta;
};

/** Delete heavy media for one finished job; keep job.json + evaluation text. */
export function purgeJobMedia(id: string): PurgeMediaResult {
  const job = readJob(id);
  if (!job) {
    return { jobId: id, ok: false, reason: "not found", bytesFreed: 0, pathsRemoved: 0 };
  }
  if (job.status === "queued" || job.status === "running") {
    return {
      jobId: id,
      ok: false,
      skipped: true,
      reason: "任务进行中，请先停止后再清理",
      bytesFreed: 0,
      pathsRemoved: 0,
      job,
    };
  }
  if (job.mediaPurged) {
    return {
      jobId: id,
      ok: true,
      skipped: true,
      reason: "媒体已清理过",
      bytesFreed: 0,
      pathsRemoved: 0,
      job,
    };
  }

  let bytesFreed = 0;
  let pathsRemoved = 0;
  for (const runDir of collectJobRunDirs(job)) {
    for (const name of PURGE_MEDIA_FILES) {
      const p = path.join(runDir, name);
      if (!isSafeArtifactsPath(p)) continue;
      const before = fs.existsSync(p);
      const n = rmPathBytes(p);
      if (n > 0 || before) {
        bytesFreed += n;
        pathsRemoved += 1;
      }
    }
    for (const name of PURGE_MEDIA_DIRS) {
      const p = path.join(runDir, name);
      if (!isSafeArtifactsPath(p)) continue;
      if (!fs.existsSync(p)) continue;
      bytesFreed += rmPathBytes(p);
      pathsRemoved += 1;
    }
  }

  job.mediaPurged = true;
  job.mediaPurgedAt = new Date().toISOString();
  job.mediaBytesFreed = (job.mediaBytesFreed || 0) + bytesFreed;
  writeJob(job);
  return { jobId: id, ok: true, bytesFreed, pathsRemoved, job };
}

/** Purge media for all finished jobs (not running/queued). */
export function purgeAllFinishedJobMedia(): {
  results: PurgeMediaResult[];
  bytesFreed: number;
  purged: number;
  skipped: number;
} {
  const results: PurgeMediaResult[] = [];
  let bytesFreed = 0;
  let purged = 0;
  let skipped = 0;
  for (const job of listJobs()) {
    const r = purgeJobMedia(job.id);
    results.push(r);
    bytesFreed += r.bytesFreed;
    if (r.skipped || !r.ok) skipped += 1;
    else purged += 1;
  }
  return { results, bytesFreed, purged, skipped };
}

export function formatBytes(n: number): string {
  if (!n || n < 0) return "0 B";
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  return `${(n / (1024 * 1024 * 1024)).toFixed(2)} GB`;
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
  job.progress = readProgress(id) || job.progress;
  const harvested = harvestJobResults(job);
  if (harvested.length) recountJob(job, harvested);
  writeJob(job);
  live.delete(id);
  return job;
}

export function getCaseDetail(jobId: string, caseId: string) {
  const job = readJob(jobId);
  if (!job) return null;
  let row = job.results.find((r) => r.case_id === caseId);
  let runDir = (row?.run_dir || "").trim();
  if (!runDir || !fs.existsSync(runDir)) {
    const fromProgress = job.progress?.cases?.find((c) => c.case_id === caseId)?.run_dir;
    runDir = (fromProgress || findCaseRunDir(caseId, job.startedAt || job.createdAt)).trim();
  }
  if (!runDir || !fs.existsSync(runDir)) return null;
  if (!row) {
    row = {
      case_id: caseId,
      name: caseId,
      passed: null,
      run_dir: runDir,
    };
  } else if (!row.run_dir) {
    row = { ...row, run_dir: runDir };
  }
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

  const asrSummary = readJson("asr_summary.json") || (readJson("run_meta.json") as { asr?: unknown } | null)?.asr || null;
  const latencySummary =
    readJson("latency_summary.json") ||
    (readJson("run_meta.json") as { latency?: unknown } | null)?.latency ||
    null;
  const mosSummary =
    readJson("mos_summary.json") ||
    (readJson("run_meta.json") as { mos?: unknown } | null)?.mos ||
    null;

  const asrFrames = listMediaUrls(runDir, jobId, caseId, "asr_frames", /\.(png|jpg|jpeg|webp)$/i);
  const postSpeechFrames = listMediaUrls(
    runDir,
    jobId,
    caseId,
    "post_speech_frames",
    /\.(png|jpg|jpeg|webp)$/i
  );
  const mosAudios = listMediaUrls(runDir, jobId, caseId, "mos_audio", /\.wav$/i);

  const capturePath = path.join(runDir, "capture.mp4");
  const video = fs.existsSync(capturePath)
    ? {
        name: "capture.mp4",
        url: `/api/artifacts?job=${encodeURIComponent(jobId)}&case=${encodeURIComponent(caseId)}&file=capture.mp4`,
      }
    : null;

  return {
    job,
    result: { ...row, metrics: loadCaseMetricsBrief(runDir) || row.metrics },
    caseJson: readJson("case.json"),
    evaluation: readJson("evaluation.json"),
    timeline: readJson("timeline.json"),
    runMeta: readJson("run_meta.json"),
    asr: asrSummary,
    latency: latencySummary,
    mos: mosSummary,
    asrFrames: asrFrames.length ? asrFrames : postSpeechFrames,
    mosAudios,
    frames,
    audios,
    video,
    report,
  };
}
