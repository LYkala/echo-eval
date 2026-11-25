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
    brief.latency_mean_sec =
      latSrc.mean_sec === null || latSrc.mean_sec === undefined
        ? null
        : Number(latSrc.mean_sec);
  }
  if (mosSrc) {
    brief.mos_total = Number(mosSrc.total ?? 0);
    brief.mos_scored = Number(mosSrc.scored ?? 0);
    brief.mos_na = Number(mosSrc.na ?? 0);
    brief.mos_mean =
      mosSrc.mean_mos === null || mosSrc.mean_mos === undefined
        ? null
        : Number(mosSrc.mean_mos);
  }
  return brief;
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
  if (progress) {
    job.progress = progress;
    // While running, surface judged cases into results for the console list.
    if ((!job.results || job.results.length === 0) && Array.isArray(progress.cases)) {
      job.results = progress.cases
        .filter((c) =>
          ["passed", "failed", "error"].includes(c.judge_status || c.status)
        )
        .map((c) => {
          const st = c.judge_status || c.status;
          return {
            case_id: c.case_id,
            name: c.name,
            passed: st === "passed" ? true : st === "failed" ? false : null,
            run_dir: "",
          };
        });
    }
  }
  // Attach parallel metrics from each case run_dir when available.
  if (Array.isArray(job.results)) {
    job.results = job.results.map((r) => {
      if (!r.run_dir) return r;
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
const DEMO_FIXTURE_ID = "demo-two-cases-v1";

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

/** Seed a browsable multi-case demo job from packaged fixtures. MOS omitted. */
export function ensureDemoJob(): JobMeta {
  const id = "job_demo_sample";
  const dir = jobDir(id);
  ensureDir(dir);
  const markerPath = path.join(dir, "DEMO_JOB.txt");
  const specs = demoFixtureSpecs();
  const needSeed =
    !fs.existsSync(markerPath) ||
    fs.readFileSync(markerPath, "utf-8").trim() !== DEMO_FIXTURE_ID ||
    specs.some((s) => !fs.existsSync(path.join(dir, s.caseFolder, "case.json")));

  let results: JobCaseResult[];
  if (needSeed) {
    // Drop legacy single-case layout (demo_case/).
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
    note: "SYS2-009（取消静音 PASS）+ IQ-SCR-026（热播榜 FAIL）；ASR/时延仿真，MOS 跳过",
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
