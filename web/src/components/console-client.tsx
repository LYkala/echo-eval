"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type EnvItem = { key: string; label: string; ok: boolean; detail: string; required: boolean };
type EnvResult = {
  ok: boolean;
  demoMode: boolean;
  items: EnvItem[];
  serial?: string;
  checkedAt: string;
  error?: string;
};
type Dataset = {
  id: string;
  name: string;
  size: number;
  uploadedAt?: string;
  builtin?: boolean;
};

/** Display upload time in Asia/Shanghai for distinguishing same-named datasets. */
function formatUploadAt(iso?: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(d);
}

function formatSize(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}
type JobProgressCase = {
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

type JobProgress = {
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
  cases: JobProgressCase[];
};

type Job = {
  id: string;
  datasetName: string;
  status: string;
  backend: string;
  caseId: string | null;
  passed?: number;
  failed?: number;
  errors?: number;
  demo?: boolean;
  results: { case_id: string; name: string; passed: boolean | null; run_dir: string }[];
  error?: string;
  note?: string;
  progress?: JobProgress;
};

type CoverageStep = {
  index: number;
  text: string;
  status: string;
  deterministic: boolean;
  ready: boolean;
  reason: string;
};
type CoverageCase = {
  case_id: string;
  name: string;
  skip: boolean;
  skip_reason: string;
  ready: boolean;
  steps: CoverageStep[];
};
type CoverageReport = {
  counts?: Record<string, number>;
  skip_case_ids?: string[];
  cases?: CoverageCase[];
  error?: string;
};

type CalibrateJob = {
  id: string;
  kind?: string;
  datasetName: string;
  status: string;
  backend: string;
  scope?: "pending" | "all";
  retryFailed?: boolean;
  force?: boolean;
  passed?: number;
  failed?: number;
  skipped?: number;
  error?: string;
  logTail?: string;
  steps?: { status: string; case_id: string; step_text: string; attempts?: number; error?: string }[];
};

type RunParams = {
  step_wait_sec: number;
  head_delay_sec: number;
  tail_sec: number;
  fps: number;
  max_retries: number;
  step_settle_sec: number;
};

const PARAM_FALLBACK: RunParams = {
  step_wait_sec: 3,
  head_delay_sec: 1,
  tail_sec: 5,
  fps: 1,
  max_retries: 3,
  step_settle_sec: 1,
};

const ACTIVE_JOB_KEY = "echo_eval_active_job";
const ACTIVE_CAL_KEY = "echo_eval_active_calibrate";

type StepRow = {
  id: string;
  label: string;
  state: "pending" | "running" | "ok" | "fail";
  detail?: string;
};

const STEP_DEFS: { id: string; label: string }[] = [
  { id: "config", label: "读取设备配置" },
  { id: "python", label: "检查 echo-eval 源码" },
  { id: "adb", label: "检查 ADB" },
  { id: "device", label: "连接设备（短超时）" },
  { id: "ffmpeg", label: "检查 FFmpeg" },
  { id: "audio", label: "检查音频播放" },
  { id: "tts", label: "检查 TTS" },
  { id: "vl", label: "检查 VL 配置" },
];

function blankSteps(): StepRow[] {
  return STEP_DEFS.map((s) => ({ id: s.id, label: s.label, state: "pending" }));
}

export function ConsoleClient() {
  const [mounted, setMounted] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [env, setEnv] = useState<EnvResult | null>(null);
  const [steps, setSteps] = useState<StepRow[]>(blankSteps);
  const [envChecking, setEnvChecking] = useState(false);
  const [envProgress, setEnvProgress] = useState("脚本加载后将自动检查");
  const [envError, setEnvError] = useState("");
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [datasetId, setDatasetId] = useState("__builtin_v012__");
  const [caseId, setCaseId] = useState("");
  const [backend, setBackend] = useState<"auto" | "vl" | "mock">("mock");
  const [calBackend, setCalBackend] = useState<"auto" | "vl" | "mock">("vl");
  /** pending = 只校准未通过（默认）；all = 全部重新校准 */
  const [calScope, setCalScope] = useState<"pending" | "all">("pending");
  const [params, setParams] = useState<RunParams>(PARAM_FALLBACK);
  const [paramSource, setParamSource] = useState("configs/device.yaml");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");
  const [active, setActive] = useState<Job | null>(null);
  const [calJob, setCalJob] = useState<CalibrateJob | null>(null);
  const [coverage, setCoverage] = useState<CoverageReport | null>(null);
  const [coverageWarn, setCoverageWarn] = useState("");
  const [hostPing, setHostPing] = useState<{
    hostRole?: string;
    hostLabel?: string;
    platform?: string;
    hostname?: string;
    repoRoot?: string;
    portHint?: number;
  } | null>(null);
  const runIdRef = useRef(0);
  const autoStarted = useRef(false);

  const pushLog = useCallback((line: string) => {
    const stamp = new Date().toLocaleTimeString("zh-CN", { hour12: false });
    const full = `[${stamp}] ${line}`;
    console.log(`[echo-eval][ui] ${line}`);
    setLogs((prev) => [...prev.slice(-80), full]);
  }, []);

  const refreshDatasets = useCallback(async () => {
    try {
      pushLog("GET /api/datasets …");
      const res = await fetch("/api/datasets", { cache: "no-store" });
      const data = (await res.json()) as { items: Dataset[] };
      setDatasets(data.items || []);
      pushLog(`datasets OK count=${(data.items || []).length}`);
    } catch (e) {
      pushLog(`datasets FAIL ${String(e)}`);
      setMsg(`加载数据集失败: ${String(e)}`);
    }
  }, [pushLog]);

  const refreshParamDefaults = useCallback(async () => {
    try {
      pushLog("GET /api/config/defaults …");
      const res = await fetch("/api/config/defaults", { cache: "no-store" });
      const data = (await res.json()) as {
        defaults?: RunParams;
        source?: string;
        error?: string;
      };
      if (!res.ok) throw new Error(data.error || "读取配置默认值失败");
      if (data.defaults) setParams({ ...PARAM_FALLBACK, ...data.defaults });
      if (data.source) setParamSource(data.source);
      pushLog(`config defaults OK from ${data.source || "device.yaml"}`);
    } catch (e) {
      pushLog(`config defaults FAIL ${String(e)}`);
    }
  }, [pushLog]);

  function setParam<K extends keyof RunParams>(key: K, raw: string) {
    const n = Number(raw);
    if (Number.isNaN(n)) return;
    setParams((prev) => ({
      ...prev,
      [key]: key === "max_retries" ? Math.max(1, Math.floor(n)) : Math.max(0, n),
    }));
  }

  const refreshEnv = useCallback(async () => {
    const runId = ++runIdRef.current;
    pushLog(`环境检查开始 runId=${runId}`);
    setEnvChecking(true);
    setEnvError("");
    setEnv(null);
    setSteps(blankSteps());
    setEnvProgress("① 先探测 /api/ping …");

    const items: EnvItem[] = [];
    let serial: string | undefined;

    try {
      try {
        pushLog("GET /api/ping …");
        const pingRes = await fetch("/api/ping", { cache: "no-store" });
        const pingText = await pingRes.text();
        pushLog(`ping HTTP ${pingRes.status}: ${pingText.slice(0, 220)}`);
        if (!pingRes.ok) throw new Error(`ping 失败 HTTP ${pingRes.status}`);
        try {
          const pingJson = JSON.parse(pingText) as {
            hostRole?: string;
            hostLabel?: string;
            platform?: string;
            hostname?: string;
            repoRoot?: string;
            portHint?: number;
          };
          setHostPing(pingJson);
          if (pingJson.hostRole === "cloud_agent") {
            pushLog(
              `警告：当前打到的是云端 Agent（${pingJson.repoRoot}），不是 Windows 本机。请改用 http://127.0.0.1:47391`
            );
          }
        } catch {
          /* ignore parse */
        }
      } catch (e) {
        const message = `无法连接本机 API: ${String(e)}。请确认 Git Bash 里 npm run dev 仍在运行，并用 http://127.0.0.1:47391 打开（不要用 file://）。`;
        pushLog(message);
        setEnvError(message);
        setEnvProgress("API 不通，已停止");
        setEnvChecking(false);
        return;
      }

      if (runId !== runIdRef.current) return;

      for (let i = 0; i < STEP_DEFS.length; i++) {
        if (runId !== runIdRef.current) {
          pushLog(`runId=${runId} 被更新的检查取代，退出`);
          return;
        }
        const def = STEP_DEFS[i];
        setEnvProgress(`(${i + 1}/${STEP_DEFS.length}) 正在检查：${def.label}`);
        setSteps((prev) =>
          prev.map((s) => (s.id === def.id ? { ...s, state: "running", detail: "检查中…" } : s))
        );
        pushLog(`GET /api/env/step?step=${def.id} …`);

        const ac = new AbortController();
        const timer = window.setTimeout(() => ac.abort(), 20000);
        let item: EnvItem;
        try {
          const res = await fetch(`/api/env/step?step=${encodeURIComponent(def.id)}`, {
            cache: "no-store",
            signal: ac.signal,
          });
          const text = await res.text();
          pushLog(`step=${def.id} HTTP ${res.status}: ${text.slice(0, 220)}`);
          let data: { item?: EnvItem; serial?: string; error?: string } = {};
          try {
            data = JSON.parse(text) as typeof data;
          } catch {
            data = { error: `非 JSON 响应: ${text.slice(0, 120)}` };
          }
          if (!res.ok) {
            item = {
              key: def.id,
              label: def.label,
              ok: false,
              detail: data.error || `HTTP ${res.status}`,
              required: true,
            };
          } else {
            item = data.item || {
              key: def.id,
              label: def.label,
              ok: false,
              detail: "无返回",
              required: true,
            };
            if (data.serial) serial = data.serial;
          }
        } catch (e) {
          const aborted = (e as { name?: string }).name === "AbortError";
          item = {
            key: def.id,
            label: def.label,
            ok: false,
            detail: aborted
              ? "本步超时（20s）。请看 Git Bash 里 npm run dev 日志是否卡在 python/adb。"
              : `请求失败: ${String(e)}`,
            required: !["audio", "tts", "vl"].includes(def.id),
          };
          pushLog(`step=${def.id} EXCEPTION ${item.detail}`);
        } finally {
          window.clearTimeout(timer);
        }

        if (runId !== runIdRef.current) return;
        items.push(item);
        setSteps((prev) =>
          prev.map((s) =>
            s.id === def.id
              ? { ...s, state: item.ok ? "ok" : "fail", detail: item.detail }
              : s
          )
        );
        setEnvProgress(
          `(${i + 1}/${STEP_DEFS.length}) ${def.label}: ${item.ok ? "OK" : "失败"}`
        );
      }

      if (runId !== runIdRef.current) return;
      const requiredFail = items.filter((it) => it.required && !it.ok);
      const result: EnvResult = {
        ok: requiredFail.length === 0,
        demoMode: requiredFail.length > 0,
        items,
        serial,
        checkedAt: new Date().toISOString(),
      };
      setEnv(result);
      setEnvProgress(result.ok ? "全部完成：环境就绪" : "全部完成：环境未就绪（可用演示任务）");
      pushLog(`环境检查结束 ok=${result.ok}`);
    } catch (e) {
      if (runId !== runIdRef.current) return;
      const message = String(e);
      pushLog(`环境检查中断 ${message}`);
      setEnvError(message);
      setEnvProgress("检查中断");
      setEnv({
        ok: false,
        demoMode: true,
        items,
        serial,
        checkedAt: new Date().toISOString(),
        error: message,
      });
    } finally {
      if (runId === runIdRef.current) setEnvChecking(false);
    }
  }, [pushLog]);

  useEffect(() => {
    setMounted(true);
    pushLog("浏览器脚本已加载（hydration OK）");
  }, [pushLog]);

  useEffect(() => {
    if (!mounted || autoStarted.current) return;
    autoStarted.current = true;
    void refreshEnv();
    void refreshDatasets();
    void refreshParamDefaults();
    // Restore in-flight job after leaving/returning to this page.
    const saved = sessionStorage.getItem(ACTIVE_JOB_KEY);
    if (saved) {
      void (async () => {
        try {
          const res = await fetch(`/api/runs/${saved}`, { cache: "no-store" });
          if (!res.ok) {
            sessionStorage.removeItem(ACTIVE_JOB_KEY);
            return;
          }
          const job = (await res.json()) as Job;
          setActive(job);
          pushLog(`恢复任务视图 ${job.id} status=${job.status}`);
        } catch (e) {
          pushLog(`恢复任务失败 ${String(e)}`);
        }
      })();
    }
    const savedCal = sessionStorage.getItem(ACTIVE_CAL_KEY);
    if (savedCal) {
      void (async () => {
        try {
          const res = await fetch(`/api/precondition/calibrate/${savedCal}`, { cache: "no-store" });
          if (!res.ok) {
            sessionStorage.removeItem(ACTIVE_CAL_KEY);
            return;
          }
          const job = (await res.json()) as CalibrateJob;
          setCalJob(job);
          pushLog(`恢复校准任务 ${job.id} status=${job.status}`);
        } catch (e) {
          pushLog(`恢复校准失败 ${String(e)}`);
        }
      })();
    }
  }, [mounted, refreshEnv, refreshDatasets, refreshParamDefaults, pushLog]);

  useEffect(() => {
    if (!active || !["queued", "running"].includes(active.status)) return;
    const t = setInterval(async () => {
      const res = await fetch(`/api/runs/${active.id}`, { cache: "no-store" });
      if (!res.ok) return;
      const job = (await res.json()) as Job;
      setActive(job);
      if (!["queued", "running"].includes(job.status)) {
        sessionStorage.setItem(ACTIVE_JOB_KEY, job.id);
      }
    }, 1000);
    return () => clearInterval(t);
  }, [active]);

  useEffect(() => {
    if (!calJob || !["queued", "running"].includes(calJob.status)) return;
    const t = setInterval(async () => {
      const res = await fetch(`/api/precondition/calibrate/${calJob.id}`, { cache: "no-store" });
      if (!res.ok) return;
      const job = (await res.json()) as CalibrateJob;
      setCalJob(job);
      sessionStorage.setItem(ACTIVE_CAL_KEY, job.id);
    }, 1000);
    return () => clearInterval(t);
  }, [calJob]);

  async function onUpload(file: File | null) {
    if (!file) return;
    setBusy(true);
    setMsg("");
    try {
      const fd = new FormData();
      fd.append("file", file);
      const res = await fetch("/api/datasets", { method: "POST", body: fd });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "上传失败");
      setMsg(`已上传：${data.name}`);
      await refreshDatasets();
      setDatasetId(data.id);
    } catch (e) {
      setMsg(String(e));
    } finally {
      setBusy(false);
    }
  }

  function downloadDataset(d: Dataset) {
    pushLog(`下载数据集 ${d.id}`);
    window.open(`/api/datasets/${encodeURIComponent(d.id)}`, "_blank");
  }

  async function deleteDataset(d: Dataset) {
    if (d.builtin) {
      setMsg("内置样例不可删除");
      return;
    }
    if (!window.confirm(`确认删除数据集「${d.name}」？\n上传于 ${formatUploadAt(d.uploadedAt)}`)) {
      return;
    }
    setBusy(true);
    setMsg("");
    try {
      pushLog(`DELETE /api/datasets/${d.id}`);
      const res = await fetch(`/api/datasets/${encodeURIComponent(d.id)}`, { method: "DELETE" });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || "删除失败");
      setMsg(`已删除：${d.name}`);
      await refreshDatasets();
      if (datasetId === d.id) {
        setDatasetId("__builtin_v012__");
      }
    } catch (e) {
      setMsg(String(e));
      pushLog(`delete FAIL ${String(e)}`);
    } finally {
      setBusy(false);
    }
  }

  const calibrateRunning = !!(calJob && ["queued", "running"].includes(calJob.status));
  const runRunning = !!(active && ["queued", "running"].includes(active.status));

  async function checkCoverage() {
    setBusy(true);
    setMsg("");
    setCoverageWarn("");
    try {
      pushLog(`POST /api/precondition/coverage dataset=${datasetId}`);
      const res = await fetch("/api/precondition/coverage", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ datasetId, caseId: caseId || null }),
      });
      const data = (await res.json()) as CoverageReport & { error?: string };
      if (!res.ok) throw new Error(data.error || "覆盖检查失败");
      setCoverage(data);
      const skips = data.skip_case_ids || [];
      if (skips.length) {
        setCoverageWarn(
          `有 ${skips.length} 条用例预置未就绪（将在开测时跳过）。建议先「开始校准」。`
        );
      } else {
        setCoverageWarn("");
        setMsg("覆盖检查：全部预置步骤就绪");
      }
      pushLog(
        `coverage OK skip=${skips.length} counts=${JSON.stringify(data.counts || {})}`
      );
    } catch (e) {
      setMsg(String(e));
      pushLog(`coverage FAIL ${String(e)}`);
    } finally {
      setBusy(false);
    }
  }

  async function startCalibrate() {
    if (runRunning) {
      setMsg("有测试任务正在运行，请先停止后再校准");
      return;
    }
    setBusy(true);
    setMsg("");
    try {
      pushLog(
        `POST /api/precondition/calibrate backend=${calBackend} scope=${calScope}`
      );
      const res = await fetch("/api/precondition/calibrate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          datasetId,
          caseId: caseId || null,
          backend: calBackend,
          scope: calScope,
          params,
        }),
      });
      const data = (await res.json()) as CalibrateJob & { error?: string };
      if (!res.ok) throw new Error(data.error || "启动校准失败");
      setCalJob(data);
      sessionStorage.setItem(ACTIVE_CAL_KEY, data.id);
      setMsg(`预置校准已启动：${data.id}`);
    } catch (e) {
      setMsg(String(e));
      pushLog(`calibrate FAIL ${String(e)}`);
    } finally {
      setBusy(false);
    }
  }

  async function stopCalibrate() {
    if (!calJob) return;
    setBusy(true);
    try {
      await fetch(`/api/precondition/calibrate/${calJob.id}`, { method: "DELETE" });
      const res = await fetch(`/api/precondition/calibrate/${calJob.id}`);
      const job = (await res.json()) as CalibrateJob;
      setCalJob(job);
      sessionStorage.setItem(ACTIVE_CAL_KEY, job.id);
    } finally {
      setBusy(false);
    }
  }

  async function startRun(demo = false) {
    if (!demo && calibrateRunning) {
      setMsg("有预置校准正在运行，请先停止后再开测");
      return;
    }
    setBusy(true);
    setMsg("");
    try {
      if (!demo && coverage && (coverage.skip_case_ids || []).length) {
        setCoverageWarn(
          `提示：仍有 ${(coverage.skip_case_ids || []).length} 条用例预置未就绪，开测时会跳过这些用例。`
        );
      }
      const res = await fetch("/api/runs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(
          demo
            ? { demo: true }
            : { datasetId, caseId: caseId || null, backend, params }
        ),
      });
      const data = (await res.json()) as Job;
      if (!res.ok) throw new Error((data as { error?: string }).error || "启动失败");
      setActive(data);
      sessionStorage.setItem(ACTIVE_JOB_KEY, data.id);
      setMsg(
        data.note ||
          `任务已启动：${data.id}。语音由本机进程播放，切换页面不会中断；请留在本页或「任务」页查看进度。`
      );
    } catch (e) {
      setMsg(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function stopRun() {
    if (!active) return;
    setBusy(true);
    try {
      await fetch(`/api/runs/${active.id}`, { method: "DELETE" });
      const res = await fetch(`/api/runs/${active.id}`);
      const job = (await res.json()) as Job;
      setActive(job);
      sessionStorage.setItem(ACTIVE_JOB_KEY, job.id);
    } finally {
      setBusy(false);
    }
  }

  function progressBadge(status: string) {
    if (status === "passed") return <Badge variant="pass">PASS</Badge>;
    if (status === "failed") return <Badge variant="fail">FAIL</Badge>;
    if (status === "running") return <Badge variant="running">进行中</Badge>;
    if (status === "error") return <Badge variant="fail">ERROR</Badge>;
    return <Badge variant="outline">等待</Badge>;
  }

  const statusBadge = (s: string) => {
    if (s === "passed") return <Badge variant="pass">PASS</Badge>;
    if (s === "failed") return <Badge variant="fail">FAIL</Badge>;
    if (s === "running" || s === "queued") return <Badge variant="running">{s}</Badge>;
    return <Badge variant="secondary">{s}</Badge>;
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
      {hostPing ? (
        <div
          className={`lg:col-span-2 rounded-md border px-3 py-2 text-sm ${
            hostPing.hostRole === "cloud_agent"
              ? "border-rose-300 bg-rose-50 text-rose-950"
              : "border-emerald-300 bg-emerald-50 text-emerald-950"
          }`}
        >
          <strong>{hostPing.hostLabel || hostPing.hostRole}</strong>
          <span className="ml-2 text-xs opacity-90">
            {hostPing.platform}/{hostPing.hostname} · {hostPing.repoRoot}
          </span>
          {hostPing.hostRole === "cloud_agent" ? (
            <p className="mt-1 text-xs">
              你打开的不是 Windows 本机控制台。请在本机 Git Bash 执行{" "}
              <code>cd web &amp;&amp; npm run dev</code>，浏览器只用{" "}
              <code>http://127.0.0.1:47391</code>（不要点 Cursor 云端端口预览）。
            </p>
          ) : null}
        </div>
      ) : null}
      <section className="animate-rise space-y-6" style={{ animationDelay: "60ms" }}>
        <Card>
          <CardHeader>
            <CardTitle className="font-display text-2xl">环境检查</CardTitle>
            <CardDescription>
              点击后下方「调试日志」和 Git Bash 的 npm 窗口都会打印进度。请盯着 Bash 里以
              <code className="mx-1">[echo-eval]</code>
              开头的行。
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap items-center gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  pushLog("用户点击「重新检查」");
                  void refreshEnv();
                }}
                disabled={envChecking}
              >
                {envChecking ? "检查中…" : "重新检查"}
              </Button>
              <button
                type="button"
                className="h-10 rounded-md border px-3 text-sm"
                onClick={() => {
                  pushLog("用户点击「强制检查」(原生按钮)");
                  void refreshEnv();
                }}
              >
                强制检查
              </button>
              {envChecking ? (
                <Badge variant="running">进行中</Badge>
              ) : env ? (
                env.ok ? (
                  <Badge variant="pass">环境就绪</Badge>
                ) : (
                  <Badge variant="fail">未就绪</Badge>
                )
              ) : (
                <Badge variant="secondary">等待开始</Badge>
              )}
            </div>

            <div
              className="rounded-md border px-3 py-2 text-sm"
              style={{ background: "hsl(40 18% 94%)", borderColor: "hsl(40 14% 80%)" }}
            >
              <div className="font-medium">当前进度</div>
              <div className={envChecking ? "animate-pulse-dot mt-1" : "mt-1"}>{envProgress}</div>
              {envError ? <div className="mt-1 text-rose-700">{envError}</div> : null}
            </div>

            <div>
              <div className="mb-1 text-sm font-medium">调试日志（页面 + Bash 同步）</div>
              <pre
                className="max-h-48 overflow-auto rounded-md border p-2 text-xs leading-5"
                style={{ background: "#1e1e1e", color: "#d4d4d4" }}
              >
                {logs.length ? logs.join("\n") : "（暂无日志）"}
              </pre>
            </div>

            <ul className="space-y-2">
              {steps.map((s) => (
                <li
                  key={s.id}
                  className="flex items-start justify-between gap-3 border-b border-border/60 py-2 text-sm last:border-0"
                >
                  <div className="min-w-0">
                    <div className="font-medium">{s.label}</div>
                    {s.detail ? (
                      <div className="break-all text-xs text-muted-foreground">{s.detail}</div>
                    ) : null}
                  </div>
                  <span
                    className="shrink-0 font-medium"
                    style={{
                      color:
                        s.state === "ok"
                          ? "#047857"
                          : s.state === "fail"
                            ? "#be123c"
                            : s.state === "running"
                              ? "#92400e"
                              : "#6b7280",
                    }}
                  >
                    {s.state === "ok"
                      ? "OK"
                      : s.state === "fail"
                        ? "失败"
                        : s.state === "running"
                          ? "检查中"
                          : "等待"}
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="font-display text-2xl">数据集与开测</CardTitle>
            <CardDescription>上传 TestApp 风格 Excel/CSV，选择后端后开始。扬声器请朝向设备麦克风。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="file">上传数据集</Label>
              <Input
                id="file"
                type="file"
                accept=".csv,.xlsx,.xlsm"
                disabled={busy}
                onChange={(e) => void onUpload(e.target.files?.[0] || null)}
              />
            </div>
            <div className="space-y-2">
              <Label>选用数据集</Label>
              <ul className="max-h-64 divide-y overflow-y-auto rounded-md border border-input bg-background">
                {datasets.length === 0 ? (
                  <li className="px-3 py-4 text-sm text-muted-foreground">暂无数据集，请先上传</li>
                ) : (
                  datasets.map((d) => {
                    const selected = datasetId === d.id;
                    return (
                      <li
                        key={d.id}
                        className={`flex flex-wrap items-center gap-2 px-3 py-2.5 sm:flex-nowrap ${
                          selected ? "bg-secondary/80" : "hover:bg-secondary/40"
                        }`}
                      >
                        <button
                          type="button"
                          className="min-w-0 flex-1 text-left"
                          disabled={busy}
                          onClick={() => setDatasetId(d.id)}
                        >
                          <div className="truncate text-sm font-medium">
                            {selected ? "● " : "○ "}
                            {d.name}
                            {d.builtin ? (
                              <span className="ml-1 text-xs font-normal text-muted-foreground">内置</span>
                            ) : null}
                          </div>
                          <div className="mt-0.5 text-xs text-muted-foreground">
                            上传 {formatUploadAt(d.uploadedAt)}
                            {d.size ? ` · ${formatSize(d.size)}` : ""}
                          </div>
                        </button>
                        <div className="flex shrink-0 gap-1.5">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            disabled={busy}
                            onClick={() => downloadDataset(d)}
                          >
                            下载
                          </Button>
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            disabled={busy || !!d.builtin}
                            title={d.builtin ? "内置样例不可删除" : "删除"}
                            onClick={() => void deleteDataset(d)}
                          >
                            删除
                          </Button>
                        </div>
                      </li>
                    );
                  })
                )}
              </ul>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="case">用例编号（默认空=跑全部）</Label>
                <Input
                  id="case"
                  value={caseId}
                  onChange={(e) => setCaseId(e.target.value)}
                  placeholder="留空跑全部；也可填单个编号"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="backend">评判后端</Label>
                <select
                  id="backend"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={backend}
                  onChange={(e) => setBackend(e.target.value as "auto" | "vl" | "mock")}
                >
                  <option value="mock">mock</option>
                  <option value="vl">vl</option>
                  <option value="auto">auto</option>
                </select>
              </div>
            </div>

            <div className="space-y-3 rounded-lg border border-border/70 bg-secondary/30 px-3 py-3">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <p className="text-sm font-medium">本次节奏</p>
                <button
                  type="button"
                  className="text-xs text-muted-foreground underline-offset-2 hover:underline"
                  onClick={() => void refreshParamDefaults()}
                >
                  恢复配置默认
                </button>
              </div>
              <p className="text-xs text-muted-foreground">
                默认来自 {paramSource}，只影响本次任务，不会改写配置文件。
              </p>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <div className="space-y-1.5">
                  <Label htmlFor="p-wait" className="text-xs text-muted-foreground">
                    步间等待（秒）
                  </Label>
                  <Input
                    id="p-wait"
                    type="number"
                    min={0}
                    step={0.5}
                    value={params.step_wait_sec}
                    onChange={(e) => setParam("step_wait_sec", e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="p-head" className="text-xs text-muted-foreground">
                    开录延迟（秒）
                  </Label>
                  <Input
                    id="p-head"
                    type="number"
                    min={0}
                    step={0.5}
                    value={params.head_delay_sec}
                    onChange={(e) => setParam("head_delay_sec", e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="p-tail" className="text-xs text-muted-foreground">
                    尾录时长（秒）
                  </Label>
                  <Input
                    id="p-tail"
                    type="number"
                    min={0}
                    step={0.5}
                    value={params.tail_sec}
                    onChange={(e) => setParam("tail_sec", e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="p-fps" className="text-xs text-muted-foreground">
                    抽帧 fps
                  </Label>
                  <Input
                    id="p-fps"
                    type="number"
                    min={0}
                    step={0.5}
                    value={params.fps}
                    onChange={(e) => setParam("fps", e.target.value)}
                  />
                </div>
              </div>
            </div>

            {coverageWarn ? (
              <p className="rounded-md border border-amber-200/80 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                {coverageWarn}
              </p>
            ) : null}
            <div className="flex flex-wrap gap-3">
              <Button
                type="button"
                disabled={busy || calibrateRunning}
                title={calibrateRunning ? "请先停止预置校准" : undefined}
                onClick={() => void startRun(false)}
              >
                {busy ? "处理中…" : "开始测试"}
              </Button>
              <Button type="button" variant="secondary" disabled={busy} onClick={() => void startRun(true)}>
                打开演示任务
              </Button>
              {active && ["running", "queued"].includes(active.status) ? (
                <Button type="button" variant="destructive" disabled={busy} onClick={() => void stopRun()}>
                  停止测试
                </Button>
              ) : null}
            </div>
            {msg ? <p className="text-sm text-muted-foreground">{msg}</p> : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="font-display text-2xl">预置校准</CardTitle>
            <CardDescription>
              正式开测前独立校准预置条件（截屏+VL→ADB）。与「开始测试」互斥占用设备。
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="cal-backend">校准后端</Label>
                <select
                  id="cal-backend"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={calBackend}
                  onChange={(e) => setCalBackend(e.target.value as "auto" | "vl" | "mock")}
                >
                  <option value="vl">vl</option>
                  <option value="auto">auto</option>
                  <option value="mock">mock（仅联调）</option>
                </select>
              </div>
              <div className="space-y-2">
                <Label>校准范围</Label>
                <div
                  className="grid grid-cols-2 gap-1 rounded-lg border border-border/80 bg-background p-1"
                  role="radiogroup"
                  aria-label="校准范围"
                >
                  <button
                    type="button"
                    role="radio"
                    aria-checked={calScope === "pending"}
                    className={`rounded-md px-3 py-2 text-sm transition-colors ${
                      calScope === "pending"
                        ? "bg-foreground text-background shadow-sm"
                        : "text-muted-foreground hover:bg-secondary"
                    }`}
                    onClick={() => setCalScope("pending")}
                  >
                    只校准未通过
                  </button>
                  <button
                    type="button"
                    role="radio"
                    aria-checked={calScope === "all"}
                    className={`rounded-md px-3 py-2 text-sm transition-colors ${
                      calScope === "all"
                        ? "bg-foreground text-background shadow-sm"
                        : "text-muted-foreground hover:bg-secondary"
                    }`}
                    onClick={() => setCalScope("all")}
                  >
                    全部重新校准
                  </button>
                </div>
                <p className="text-xs text-muted-foreground">
                  {calScope === "pending"
                    ? "跳过预置已全部通过的用例；失败与未完成的会重训。已通过的步骤只回放 ADB。"
                    : "含已通过步骤一并重训，耗时更长。"}
                </p>
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="p-retries" className="text-xs text-muted-foreground">
                  单步最多重试次数
                </Label>
                <Input
                  id="p-retries"
                  type="number"
                  min={1}
                  step={1}
                  value={params.max_retries}
                  onChange={(e) => setParam("max_retries", e.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="p-settle" className="text-xs text-muted-foreground">
                  预置步默认等待（秒）
                </Label>
                <Input
                  id="p-settle"
                  type="number"
                  min={0}
                  step={0.5}
                  value={params.step_settle_sec}
                  onChange={(e) => setParam("step_settle_sec", e.target.value)}
                />
              </div>
            </div>

            <div className="flex flex-wrap gap-3">
              <Button type="button" variant="outline" disabled={busy} onClick={() => void checkCoverage()}>
                查看覆盖情况
              </Button>
              <Button
                type="button"
                disabled={busy || runRunning}
                title={runRunning ? "请先停止测试任务" : undefined}
                onClick={() => void startCalibrate()}
              >
                开始校准
              </Button>
              {calibrateRunning ? (
                <Button type="button" variant="destructive" disabled={busy} onClick={() => void stopCalibrate()}>
                  停止校准
                </Button>
              ) : null}
              {calJob ? statusBadge(calJob.status) : null}
            </div>

            {calJob ? (
              <div className="space-y-3 rounded-md border border-border/80 bg-secondary/40 px-3 py-3">
                <div className="text-sm font-medium">
                  {calJob.datasetName} · {calJob.backend}
                  {calJob.scope === "all" || calJob.force
                    ? " · 全部重新校准"
                    : " · 只校准未通过"}
                </div>
                <div className="grid grid-cols-3 gap-2 text-center text-sm">
                  <div className="rounded-md bg-background px-2 py-2">
                    <div className="text-xs text-muted-foreground">通过步</div>
                    <div className="text-lg font-semibold">{calJob.passed ?? "—"}</div>
                  </div>
                  <div className="rounded-md bg-background px-2 py-2">
                    <div className="text-xs text-muted-foreground">失败步</div>
                    <div className="text-lg font-semibold">{calJob.failed ?? "—"}</div>
                  </div>
                  <div className="rounded-md bg-background px-2 py-2">
                    <div className="text-xs text-muted-foreground">跳过步</div>
                    <div className="text-lg font-semibold">{calJob.skipped ?? "—"}</div>
                  </div>
                </div>
                {calJob.error ? <p className="text-sm text-rose-700">{calJob.error}</p> : null}
                <ul className="max-h-40 space-y-1 overflow-y-auto text-xs">
                  {(calJob.steps || []).slice(-40).map((s, i) => (
                    <li key={`${s.case_id}-${i}`} className="truncate">
                      <span className="font-medium uppercase">{s.status}</span> {s.case_id} · {s.step_text}
                      {s.error ? ` · ${s.error}` : ""}
                    </li>
                  ))}
                </ul>
                <div>
                  <div className="mb-1 text-xs font-medium text-muted-foreground">校准日志（尾部）</div>
                  <pre
                    className="max-h-48 overflow-auto rounded-md border p-2 text-xs leading-5"
                    style={{ background: "#1e1e1e", color: "#d4d4d4" }}
                  >
                    {calJob.logTail || "（等待日志…）"}
                  </pre>
                </div>
              </div>
            ) : null}

            {coverage?.cases?.length ? (
              <div className="space-y-2">
                <div className="text-sm font-medium">
                  覆盖摘要
                  {coverage.counts
                    ? ` · passed=${coverage.counts.passed ?? 0} untested=${coverage.counts.untested ?? 0} failed=${coverage.counts.failed ?? 0} skip_cases=${coverage.counts.skip_cases ?? 0}`
                    : ""}
                  {(coverage as { cache_path?: string }).cache_path
                    ? ` · cache=${(coverage as { cache_path?: string }).cache_path}`
                    : ""}
                </div>
                <ul className="max-h-48 space-y-1 overflow-y-auto text-xs">
                  {coverage.cases.map((c) => (
                    <li key={c.case_id} className={c.skip ? "text-amber-800" : "text-emerald-800"}>
                      {c.skip ? "SKIP" : "OK"} {c.case_id} {c.name}
                      {c.skip_reason ? ` — ${c.skip_reason}` : ""}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </CardContent>
        </Card>
      </section>

      <section className="animate-rise space-y-6" style={{ animationDelay: "140ms" }}>
        <Card className="overflow-hidden">
          <CardHeader>
            <div className="flex items-center justify-between gap-2">
              <CardTitle className="font-display text-2xl">当前任务</CardTitle>
              {active ? statusBadge(active.status) : <Badge variant="outline">空闲</Badge>}
            </div>
            <CardDescription>
              {active
                ? `${active.datasetName} · ${active.backend}${active.demo ? " · 演示数据" : ""}`
                : "启动后在此查看进度与用例入口"}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {!active ? (
              <p className="text-sm text-muted-foreground">尚无任务。环境未就绪时可先打开演示任务浏览界面。</p>
            ) : (
              <>
                {["running", "queued"].includes(active.status) ? (
                  <p className="rounded-md border border-amber-200/80 bg-amber-50 px-3 py-2 text-xs text-amber-950">
                    语音由本机 Python 进程播放，切换到「任务」等页面<strong>不会中断</strong>执行；回到工作台会自动恢复当前任务进度。请保持扬声器朝向设备。
                  </p>
                ) : null}
                {active.progress ? (
                  <div className="space-y-3 rounded-md border border-border/80 bg-secondary/40 px-3 py-3">
                    <div className="space-y-1.5">
                      <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                        <span className="font-medium">
                          设备 {active.progress.device_done ?? 0}/{active.progress.total}
                          {active.progress.current_case_id
                            ? ` · ${active.progress.current_case_id}`
                            : ""}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {active.progress.device_phase_label || "设备执行"}
                        </span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-background">
                        <div
                          className="h-full bg-emerald-700/80 transition-all duration-500"
                          style={{
                            width: `${
                              active.progress.total > 0
                                ? Math.min(
                                    100,
                                    Math.round(
                                      ((active.progress.device_done ?? 0) / active.progress.total) * 100
                                    )
                                  )
                                : 0
                            }%`,
                          }}
                        />
                      </div>
                    </div>
                    <div className="space-y-1.5">
                      <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                        <span className="font-medium">
                          评判 {active.progress.judge_done ?? 0}/{active.progress.total}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {active.progress.judge_phase_label ||
                            active.progress.phase_label ||
                            "评判"}
                        </span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-background">
                        <div
                          className="h-full bg-amber-700/70 transition-all duration-500"
                          style={{
                            width: `${
                              active.progress.total > 0
                                ? Math.min(
                                    100,
                                    Math.round(
                                      ((active.progress.judge_done ?? 0) / active.progress.total) * 100
                                    )
                                  )
                                : 0
                            }%`,
                          }}
                        />
                      </div>
                    </div>
                  </div>
                ) : ["running", "queued"].includes(active.status) ? (
                  <p className="text-sm text-muted-foreground">正在启动用例，稍后显示进度…</p>
                ) : null}
                <div className="grid grid-cols-3 gap-2 text-center text-sm">
                  <div className="rounded-md bg-secondary/70 px-2 py-3">
                    <div className="text-xs text-muted-foreground">通过</div>
                    <div className="text-lg font-semibold">{active.passed ?? "—"}</div>
                  </div>
                  <div className="rounded-md bg-secondary/70 px-2 py-3">
                    <div className="text-xs text-muted-foreground">失败</div>
                    <div className="text-lg font-semibold">{active.failed ?? "—"}</div>
                  </div>
                  <div className="rounded-md bg-secondary/70 px-2 py-3">
                    <div className="text-xs text-muted-foreground">错误</div>
                    <div className="text-lg font-semibold">{active.errors ?? "—"}</div>
                  </div>
                </div>
                {active.error ? <p className="text-sm text-rose-700">{active.error}</p> : null}
                <ul className="max-h-72 space-y-2 overflow-y-auto">
                  {(active.progress?.cases?.length
                    ? active.progress.cases.map((c) => ({
                        key: `${c.index}-${c.case_id}`,
                        case_id: c.case_id,
                        name: c.name,
                        status: c.status,
                        device_label: c.device_label || c.device_status || "",
                        judge_label: c.judge_label || c.judge_status || "",
                        linkable: ["passed", "failed"].includes(c.status),
                      }))
                    : (active.results || []).map((r) => ({
                        key: r.case_id,
                        case_id: r.case_id,
                        name: r.name,
                        status:
                          r.passed === true ? "passed" : r.passed === false ? "failed" : "pending",
                        device_label: "",
                        judge_label: "",
                        linkable: r.passed === true || r.passed === false,
                      }))
                  ).map((row) => {
                    const body = (
                      <>
                        <span className="min-w-0">
                          <span className="font-medium">{row.case_id}</span>
                          <span className="ml-2 text-muted-foreground">{row.name}</span>
                          <span className="mt-0.5 block text-xs text-muted-foreground">
                            设备：{row.device_label || "—"}
                            <span className="mx-1.5 opacity-40">|</span>
                            评判：{row.judge_label || "—"}
                          </span>
                        </span>
                        {progressBadge(row.status)}
                      </>
                    );
                    return (
                      <li key={row.key}>
                        {row.linkable ? (
                          <Link
                            href={`/runs/${active.id}/cases/${encodeURIComponent(row.case_id)}`}
                            className="flex items-center justify-between gap-2 rounded-md border border-border/80 bg-card/60 px-3 py-2 text-sm transition hover:border-primary/40 hover:bg-accent/40"
                          >
                            {body}
                          </Link>
                        ) : (
                          <div className="flex items-center justify-between gap-2 rounded-md border border-border/80 bg-card/60 px-3 py-2 text-sm">
                            {body}
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ul>
                <Link href={`/runs/${active.id}`} className="text-sm font-medium text-primary hover:underline">
                  查看任务详情 →
                </Link>
              </>
            )}
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
