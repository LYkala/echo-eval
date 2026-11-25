"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { MetricsChips } from "@/components/case-metrics";
import { aggregateJobMetrics } from "@/lib/metrics-aggregate";

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
  run_dir?: string;
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

type CaseMetrics = {
  asr_accuracy?: number | null;
  asr_passed?: number;
  asr_total?: number;
  latency_mean_sec?: number | null;
  latency_measured?: number;
  latency_no_action?: number;
  mos_mean?: number | null;
  mos_scored?: number;
  mos_na?: number;
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
  results: {
    case_id: string;
    name: string;
    passed: boolean | null;
    run_dir: string;
    metrics?: CaseMetrics;
  }[];
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
  asr_enabled: boolean;
  asr_window_sec: number;
  asr_interval_sec: number;
  latency_enabled: boolean;
  latency_window_sec: number;
  mos_enabled: boolean;
  mos_window_sec: number;
  mos_input_device: string;
  wake_enabled: boolean;
  wake_text: string;
  wake_wait_sec: number;
  tts_backend: string;
  tts_voice: string;
  tts_prefer_library: boolean;
  tts_library_dir: string;
};

type TtsVoiceOption = { id: string; label: string; gender?: string; locale?: string };

type LibraryVoice = {
  profile: string;
  backend: string;
  voice: string;
  label: string;
  count: number;
  sample_rel_path?: string;
};

type TtsLibraryStatus = {
  library_root?: string;
  exists?: boolean;
  entry_count?: number;
  missing_files?: number;
  updated_at?: string;
  voices?: LibraryVoice[];
  entries?: Array<{
    case_id?: string;
    step_index?: number;
    text?: string;
    voice_profile?: string;
    source?: string;
  }>;
  error?: string;
};

const PARAM_FALLBACK: RunParams = {
  step_wait_sec: 3,
  head_delay_sec: 1,
  tail_sec: 5,
  fps: 1,
  max_retries: 3,
  step_settle_sec: 1,
  asr_enabled: true,
  asr_window_sec: 4,
  asr_interval_sec: 0.5,
  latency_enabled: true,
  latency_window_sec: 8,
  mos_enabled: true,
  mos_window_sec: 5,
  mos_input_device: "",
  wake_enabled: true,
  wake_text: "小翼管家",
  wake_wait_sec: 3,
  tts_backend: "edge",
  tts_voice: "zh-CN-XiaoxiaoNeural",
  tts_prefer_library: true,
  tts_library_dir: "assets/tts_library",
};

const TTS_VOICE_FALLBACK: TtsVoiceOption[] = [
  { id: "zh-CN-XiaoxiaoNeural", label: "晓晓（女·温暖）", gender: "Female" },
  { id: "zh-CN-XiaoyiNeural", label: "晓伊（女·活泼）", gender: "Female" },
  { id: "zh-CN-YunxiNeural", label: "云希（男·阳光）", gender: "Male" },
  { id: "zh-CN-YunyangNeural", label: "云扬（男·新闻）", gender: "Male" },
  { id: "zh-CN-YunjianNeural", label: "云健（男·热情）", gender: "Male" },
  { id: "zh-CN-YunxiaNeural", label: "云夏（男·可爱）", gender: "Male" },
];

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
  { id: "tts", label: "检查 TTS / edge-tts" },
  { id: "vl", label: "检查 VL 配置" },
  { id: "mos", label: "MOS 拾音（可选）" },
];

function blankSteps(): StepRow[] {
  return STEP_DEFS.map((s) => ({ id: s.id, label: s.label, state: "pending" }));
}

export type ConsoleSection = "env" | "datasets" | "tts" | "calibrate" | "run";

const DATASET_ID_KEY = "echo_eval_dataset_id";

export function ConsoleClient({ section }: { section: ConsoleSection }) {
  const [mounted, setMounted] = useState(false);
  // Seed a line so SSR/HTML is not stuck on “（暂无日志）”; effects append more.
  const [logs, setLogs] = useState<string[]>([
    "[boot] ConsoleClient 已渲染。若本行之后没有新日志，说明浏览器 JS 未完成加载（请强刷或看 F12）。",
  ]);
  const [env, setEnv] = useState<EnvResult | null>(null);
  const [steps, setSteps] = useState<StepRow[]>(blankSteps);
  const [envChecking, setEnvChecking] = useState(false);
  const [envProgress, setEnvProgress] = useState("脚本加载后将自动检查");
  const [envError, setEnvError] = useState("");
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [datasetId, setDatasetId] = useState(() => {
    if (typeof window === "undefined") return "__builtin_v012__";
    return sessionStorage.getItem(DATASET_ID_KEY) || "__builtin_v012__";
  });
  const [caseId, setCaseId] = useState("");
  const [backend, setBackend] = useState<"auto" | "vl" | "mock">("mock");
  const [calBackend, setCalBackend] = useState<"auto" | "vl" | "mock">("vl");
  /** pending = 只校准未通过（默认）；all = 全部重新校准 */
  const [calScope, setCalScope] = useState<"pending" | "all">("pending");
  const [params, setParams] = useState<RunParams>(PARAM_FALLBACK);
  const [paramSource, setParamSource] = useState("configs/device.yaml");
  const [ttsVoices, setTtsVoices] = useState<TtsVoiceOption[]>(TTS_VOICE_FALLBACK);
  const [ttsPreviewBusy, setTtsPreviewBusy] = useState(false);
  const [ttsPreviewUrl, setTtsPreviewUrl] = useState<string | null>(null);
  const [ttsPreviewError, setTtsPreviewError] = useState("");
  const [ttsLibrary, setTtsLibrary] = useState<TtsLibraryStatus | null>(null);
  const [ttsLibraryBusy, setTtsLibraryBusy] = useState(false);
  const [ttsLibraryMsg, setTtsLibraryMsg] = useState("");
  const [ttsCustomText, setTtsCustomText] = useState("");
  /** Prebuild-only; independent from run/preview tts_* params. */
  const [libBackend, setLibBackend] = useState("edge");
  const [libVoice, setLibVoice] = useState("zh-CN-XiaoxiaoNeural");
  const [libPreviewBusy, setLibPreviewBusy] = useState(false);
  const [libPreviewUrl, setLibPreviewUrl] = useState<string | null>(null);
  const [libPreviewError, setLibPreviewError] = useState("");
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
    if (
      key === "mos_input_device" ||
      key === "wake_text" ||
      key === "tts_backend" ||
      key === "tts_voice" ||
      key === "tts_library_dir"
    ) {
      setParams((prev) => ({ ...prev, [key]: raw }));
      return;
    }
    if (
      key === "asr_enabled" ||
      key === "latency_enabled" ||
      key === "mos_enabled" ||
      key === "wake_enabled" ||
      key === "tts_prefer_library"
    ) {
      return;
    }
    const n = Number(raw);
    if (Number.isNaN(n)) return;
    setParams((prev) => ({
      ...prev,
      [key]: key === "max_retries" ? Math.max(1, Math.floor(n)) : Math.max(0, n),
    }));
  }

  const loadTtsVoices = useCallback(async () => {
    try {
      const res = await fetch("/api/tts/voices?curated=1&locale=zh-CN", { cache: "no-store" });
      const data = (await res.json()) as { voices?: TtsVoiceOption[]; error?: string };
      if (res.ok && Array.isArray(data.voices) && data.voices.length) {
        setTtsVoices(data.voices);
      }
    } catch {
      /* keep fallback list */
    }
  }, []);

  const refreshTtsLibrary = useCallback(async () => {
    try {
      const dir = encodeURIComponent(params.tts_library_dir || "assets/tts_library");
      const res = await fetch(`/api/tts/library?library_dir=${dir}`, { cache: "no-store" });
      const data = (await res.json()) as TtsLibraryStatus;
      if (!res.ok) throw new Error(data.error || `library HTTP ${res.status}`);
      setTtsLibrary(data);
    } catch (e) {
      setTtsLibrary({ error: String(e), entry_count: 0 });
    }
  }, [params.tts_library_dir]);

  async function previewTtsVoice(opts: {
    backend: string;
    voice: string;
    setBusy: (v: boolean) => void;
    setError: (v: string) => void;
    url: string | null;
    setUrl: (v: string | null) => void;
  }) {
    if (opts.backend !== "edge") {
      opts.setError("试听目前仅支持 edge-tts");
      return;
    }
    opts.setBusy(true);
    opts.setError("");
    try {
      if (opts.url) {
        URL.revokeObjectURL(opts.url);
        opts.setUrl(null);
      }
      const res = await fetch("/api/tts/preview", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          backend: "edge",
          voice: opts.voice,
          text: "小翼管家",
        }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error((err as { error?: string }).error || `preview HTTP ${res.status}`);
      }
      const blob = await res.blob();
      opts.setUrl(URL.createObjectURL(blob));
    } catch (e) {
      opts.setError(String(e));
    } finally {
      opts.setBusy(false);
    }
  }

  const libraryVoices = ttsLibrary?.voices || [];

  function selectLibraryVoiceForRun(profile: string) {
    const hit = libraryVoices.find((v) => v.profile === profile);
    if (!hit) return;
    setParams((prev) => ({
      ...prev,
      tts_backend: hit.backend || "edge",
      tts_voice: hit.voice || prev.tts_voice,
      tts_prefer_library: true,
    }));
  }

  async function previewLibraryProfile(
    profile: string,
    opts: {
      setBusy: (v: boolean) => void;
      setError: (v: string) => void;
      url: string | null;
      setUrl: (v: string | null) => void;
    }
  ) {
    opts.setBusy(true);
    opts.setError("");
    try {
      if (opts.url) {
        URL.revokeObjectURL(opts.url);
        opts.setUrl(null);
      }
      const dir = encodeURIComponent(params.tts_library_dir || "assets/tts_library");
      const res = await fetch(
        `/api/tts/library?library_dir=${dir}&sample_profile=${encodeURIComponent(profile)}`,
        { cache: "no-store" }
      );
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error((err as { error?: string }).error || `sample HTTP ${res.status}`);
      }
      const blob = await res.blob();
      opts.setUrl(URL.createObjectURL(blob));
    } catch (e) {
      opts.setError(String(e));
    } finally {
      opts.setBusy(false);
    }
  }

  async function previewTts() {
    const profile = `${params.tts_backend}:${params.tts_voice || "default"}`;
    const inLib = libraryVoices.some((v) => v.profile === profile);
    if (inLib) {
      await previewLibraryProfile(profile, {
        setBusy: setTtsPreviewBusy,
        setError: setTtsPreviewError,
        url: ttsPreviewUrl,
        setUrl: setTtsPreviewUrl,
      });
      return;
    }
    await previewTtsVoice({
      backend: params.tts_backend,
      voice: params.tts_voice,
      setBusy: setTtsPreviewBusy,
      setError: setTtsPreviewError,
      url: ttsPreviewUrl,
      setUrl: setTtsPreviewUrl,
    });
  }

  // Keep run voice selection aligned with library when voices load / change.
  useEffect(() => {
    if (section !== "run") return;
    if (!libraryVoices.length) return;
    const current = `${params.tts_backend}:${params.tts_voice || "default"}`;
    if (!libraryVoices.some((v) => v.profile === current)) {
      selectLibraryVoiceForRun(libraryVoices[0].profile);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only re-sync when library voice list changes
  }, [section, libraryVoices.map((v) => v.profile).join("|")]);

  async function previewLibTts() {
    await previewTtsVoice({
      backend: libBackend,
      voice: libVoice,
      setBusy: setLibPreviewBusy,
      setError: setLibPreviewError,
      url: libPreviewUrl,
      setUrl: setLibPreviewUrl,
    });
  }

  async function prebuildTtsLibrary(force = false) {
    setTtsLibraryBusy(true);
    setTtsLibraryMsg("");
    try {
      const res = await fetch("/api/tts/library", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          action: "prebuild",
          datasetId,
          backend: libBackend,
          voice: libVoice,
          library_dir: params.tts_library_dir,
          wake_enabled: params.wake_enabled,
          wake_text: params.wake_text,
          force,
        }),
      });
      const data = (await res.json()) as {
        error?: string;
        created?: number;
        exists?: number;
        total?: number;
        errors?: Array<{ case_id?: string; step?: string; error?: string }>;
        entry_count?: number;
      };
      if (!res.ok) throw new Error(data.error || `prebuild HTTP ${res.status}`);
      const errN = (data.errors || []).length;
      setTtsLibraryMsg(
        `预合成完成（${libBackend}/${libVoice}）：新建 ${data.created ?? 0} / 已有 ${data.exists ?? 0} / 合计 ${data.total ?? 0}` +
          (errN ? `，失败 ${errN}` : "") +
          `，库内 ${data.entry_count ?? "?"} 条`
      );
      await refreshTtsLibrary();
    } catch (e) {
      setTtsLibraryMsg(String(e));
    } finally {
      setTtsLibraryBusy(false);
    }
  }

  async function addCustomTts() {
    const text = ttsCustomText.trim();
    if (!text) {
      setTtsLibraryMsg("请输入要合成的文本");
      return;
    }
    setTtsLibraryBusy(true);
    setTtsLibraryMsg("");
    try {
      const res = await fetch("/api/tts/library", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          action: "custom",
          text,
          backend: libBackend,
          voice: libVoice,
          library_dir: params.tts_library_dir,
        }),
      });
      const data = (await res.json()) as {
        error?: string;
        status?: string;
        entry?: { text?: string; rel_path?: string };
      };
      if (!res.ok) throw new Error(data.error || `custom HTTP ${res.status}`);
      setTtsLibraryMsg(
        data.status === "exists"
          ? `已存在：${data.entry?.text || text}`
          : `已写入库（${libBackend}/${libVoice}）：${data.entry?.text || text}`
      );
      setTtsCustomText("");
      await refreshTtsLibrary();
    } catch (e) {
      setTtsLibraryMsg(String(e));
    } finally {
      setTtsLibraryBusy(false);
    }
  }

  function setBoolParam(
    key: "asr_enabled" | "latency_enabled" | "mos_enabled" | "wake_enabled" | "tts_prefer_library",
    value: boolean
  ) {
    setParams((prev) => ({ ...prev, [key]: value }));
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
              `警告：当前打到的是云端 Agent（${pingJson.repoRoot}），不是 Windows 本机。请改用 http://127.0.0.1:58261`
            );
          }
        } catch {
          /* ignore parse */
        }
      } catch (e) {
        const message = `无法连接本机 API: ${String(e)}。请确认 Git Bash 里 npm run dev 仍在运行，并用 http://127.0.0.1:58261 打开（不要用 file://）。`;
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
            required: !["audio", "tts", "vl", "mos"].includes(def.id),
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
    // Expose a manual hook for F12 debugging when clicks seem dead.
    try {
      (window as unknown as { __echoEvalRefreshEnv?: () => void }).__echoEvalRefreshEnv = () => {
        pushLog("F12 手动触发 refreshEnv");
        void refreshEnv();
      };
    } catch {
      /* ignore */
    }
  }, [pushLog, refreshEnv]);

  useEffect(() => {
    if (!mounted || autoStarted.current) return;
    autoStarted.current = true;
    if (section === "env") void refreshEnv();
    void refreshDatasets();
    if (section === "run" || section === "tts") void refreshParamDefaults();
    if (section === "tts") void loadTtsVoices();
    if (section === "tts" || section === "run") void refreshTtsLibrary();
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
  }, [mounted, section, refreshEnv, refreshDatasets, refreshParamDefaults, loadTtsVoices, refreshTtsLibrary, pushLog]);

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

  useEffect(() => {
    if (!mounted) return;
    sessionStorage.setItem(DATASET_ID_KEY, datasetId);
  }, [datasetId, mounted]);

  const calibrateRunning = !!(calJob && ["queued", "running"].includes(calJob.status));
  const runRunning = !!(active && ["queued", "running"].includes(active.status));

  const datasetPicker = (
    <div className="space-y-2">
      <Label>当前数据集</Label>
      <select
        className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
        value={datasetId}
        disabled={busy}
        onChange={(e) => setDatasetId(e.target.value)}
      >
        {datasets.map((d) => (
          <option key={d.id} value={d.id}>
            {d.name}
            {d.builtin ? "（内置）" : ""}
          </option>
        ))}
      </select>
    </div>
  );

  const pageTitle =
    section === "env"
      ? "环境检查"
      : section === "datasets"
        ? "数据集"
        : section === "tts"
          ? "预合成语音"
          : section === "calibrate"
            ? "预置校准"
            : "开始测试";

  const pageDesc =
    section === "env"
      ? "检查本机 ADB / 设备 / FFmpeg / TTS / VL 等依赖。"
      : section === "datasets"
        ? "上传、选用、下载或删除 Excel/CSV 用例集。"
        : section === "tts"
          ? "将数据集步骤预合成到语音库；可追加自定义文本。"
          : section === "calibrate"
            ? "正式开测前校准预置条件（截屏 + VL → ADB）。"
            : "配置本次节奏与评判项，启动或停止测试。";

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
    <div className={section === "run" ? "grid gap-6 lg:grid-cols-[1.1fr_0.9fr]" : "mx-auto max-w-4xl space-y-6"}>
      <div className={section === "run" ? "lg:col-span-2 space-y-1" : "space-y-1"}>
        <h1 className="text-xl font-semibold tracking-tight">{pageTitle}</h1>
        <p className="text-sm text-muted-foreground">{pageDesc}</p>
      </div>

      {hostPing && (section === "env" || hostPing.hostRole === "cloud_agent") ? (
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
              <code>http://127.0.0.1:58261</code>（不要点 Cursor 云端端口预览）。
            </p>
          ) : null}
        </div>
      ) : null}

      {section === "env" ? (
        <section className="animate-rise space-y-6">
<Card>
          <CardHeader>
            <CardTitle className="text-xl font-semibold">环境检查</CardTitle>
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
        </section>
      ) : null}

      {section === "datasets" ? (
        <section className="animate-rise space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-xl font-semibold">数据集</CardTitle>
            <CardDescription>上传 TestApp 风格 Excel/CSV，选用后供预合成、校准与开测共用。</CardDescription>
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
            {msg && section === "datasets" ? <p className="text-sm text-muted-foreground">{msg}</p> : null}
          </CardContent>
        </Card>
        </section>
      ) : null}

      {section === "tts" ? (
        <section className="animate-rise space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-xl font-semibold">预合成语音</CardTitle>
              <CardDescription>与开测音色独立；写入 assets/tts_library，开测时可优先读库。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {datasetPicker}
              <div className="space-y-2 border-t border-border/60 pt-3">
                <p className="text-sm font-medium">预合成音色（语音库）</p>
                <p className="text-xs text-muted-foreground">
                  与开测音色独立。预合成 / 自定义写入只看这里的后端与音色，不会改开测参数。
                </p>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground">预合成后端</Label>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={libBackend}
                      onChange={(e) => setLibBackend(e.target.value)}
                    >
                      <option value="edge">edge-tts（推荐）</option>
                      <option value="sapi">本机 SAPI</option>
                    </select>
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground">预合成音色</Label>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      disabled={libBackend !== "edge"}
                      value={libVoice}
                      onChange={(e) => setLibVoice(e.target.value)}
                    >
                      {ttsVoices.map((v) => (
                        <option key={`lib-${v.id}`} value={v.id}>
                          {v.label || v.id}
                        </option>
                      ))}
                      {!ttsVoices.some((v) => v.id === libVoice) && libVoice ? (
                        <option value={libVoice}>{libVoice}</option>
                      ) : null}
                    </select>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={libPreviewBusy || libBackend !== "edge"}
                    onClick={() => void previewLibTts()}
                  >
                    {libPreviewBusy ? "合成中…" : "试听预合成音色"}
                  </Button>
                  {libPreviewUrl ? (
                    <audio controls src={libPreviewUrl} className="h-8 max-w-full" />
                  ) : null}
                </div>
                {libPreviewError ? (
                  <p className="text-xs text-rose-700">{libPreviewError}</p>
                ) : null}
                <p className="text-xs text-muted-foreground">
                  当前写入音色：
                  <span className="font-medium text-foreground">
                    {" "}
                    {libBackend === "edge"
                      ? ttsVoices.find((v) => v.id === libVoice)?.label || libVoice
                      : "本机 SAPI"}
                  </span>
                  （{libBackend}:{libBackend === "edge" ? libVoice : "default"}）· 库内{" "}
                  {ttsLibrary?.entry_count ?? "—"} 条
                  {ttsLibrary?.missing_files
                    ? `（缺文件 ${ttsLibrary.missing_files}）`
                    : ""}
                </p>
                {libraryVoices.length ? (
                  <div className="overflow-hidden rounded-md border border-border">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-secondary/50 text-muted-foreground">
                        <tr>
                          <th className="px-3 py-2 font-medium">已入库音色</th>
                          <th className="px-3 py-2 font-medium">profile</th>
                          <th className="px-3 py-2 font-medium">条数</th>
                          <th className="px-3 py-2 font-medium">试听</th>
                        </tr>
                      </thead>
                      <tbody>
                        {libraryVoices.map((v) => (
                          <tr key={v.profile} className="border-t border-border/70">
                            <td className="px-3 py-2 font-medium">{v.label}</td>
                            <td className="px-3 py-2 text-muted-foreground">{v.profile}</td>
                            <td className="px-3 py-2">{v.count}</td>
                            <td className="px-3 py-2">
                              <button
                                type="button"
                                className="text-primary underline-offset-2 hover:underline"
                                disabled={libPreviewBusy}
                                onClick={() =>
                                  void previewLibraryProfile(v.profile, {
                                    setBusy: setLibPreviewBusy,
                                    setError: setLibPreviewError,
                                    url: libPreviewUrl,
                                    setUrl: setLibPreviewUrl,
                                  })
                                }
                              >
                                播放样例
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="rounded-md border border-dashed border-border px-3 py-2 text-xs text-muted-foreground">
                    库中尚无音色。选择上方后端/音色后点「预合成当前数据集」。
                  </p>
                )}
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={ttsLibraryBusy || !datasetId}
                    onClick={() => void prebuildTtsLibrary(false)}
                  >
                    {ttsLibraryBusy ? "处理中…" : `预合成（${libBackend === "edge" ? libVoice : "SAPI"}）`}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={ttsLibraryBusy || !datasetId}
                    onClick={() => void prebuildTtsLibrary(true)}
                  >
                    强制重合成
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    disabled={ttsLibraryBusy}
                    onClick={() => void refreshTtsLibrary()}
                  >
                    刷新状态
                  </Button>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
                  <div className="min-w-0 flex-1 space-y-1">
                    <Label className="text-xs text-muted-foreground">自定义文本（数据集外）</Label>
                    <Input
                      value={ttsCustomText}
                      placeholder="例如：打开设置"
                      maxLength={200}
                      onChange={(e) => setTtsCustomText(e.target.value)}
                    />
                  </div>
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={ttsLibraryBusy || !ttsCustomText.trim()}
                    onClick={() => void addCustomTts()}
                  >
                    写入语音库
                  </Button>
                </div>
                {ttsLibraryMsg ? (
                  <p className="text-xs text-muted-foreground whitespace-pre-wrap">{ttsLibraryMsg}</p>
                ) : null}
                {ttsLibrary?.error ? (
                  <p className="text-xs text-rose-700">{ttsLibrary.error}</p>
                ) : null}
              </div>
            </CardContent>
          </Card>
        </section>
      ) : null}

      {section === "calibrate" ? (
        <section className="animate-rise space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-xl font-semibold">预置校准</CardTitle>
              <CardDescription>
                正式开测前独立校准预置条件（截屏+VL→ADB）。与「开始测试」互斥占用设备。
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {datasetPicker}

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
      ) : null}

      {section === "run" ? (
        <>
        <section className="animate-rise space-y-6" style={{ animationDelay: "60ms" }}>
          <Card>
            <CardHeader>
              <CardTitle className="text-xl font-semibold">测试参数</CardTitle>
              <CardDescription>扬声器请朝向设备麦克风。参数只影响本次任务。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {datasetPicker}
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

            <div className="space-y-3 rounded-lg border border-border/70 bg-secondary/30 px-3 py-3">
              <p className="text-sm font-medium">评判项（本次任务）</p>
              <p className="text-xs text-muted-foreground">
                现场：唤醒 + 播报 + 短 ASR 采图 + MOS 麦。pull 后后台：GUI / ASR VL / 离线时延 / MOS 打分。唤醒词只播报不评判。
              </p>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={params.asr_enabled}
                    onChange={(e) => setBoolParam("asr_enabled", e.target.checked)}
                  />
                  ASR 准确率
                </label>
                <div className="grid grid-cols-2 gap-2">
                  <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground">ASR 窗口（秒）</Label>
                    <Input
                      type="number"
                      min={0.5}
                      step={0.5}
                      disabled={!params.asr_enabled}
                      value={params.asr_window_sec}
                      onChange={(e) => setParam("asr_window_sec", e.target.value)}
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground">ASR 间隔（秒）</Label>
                    <Input
                      type="number"
                      min={0.2}
                      step={0.1}
                      disabled={!params.asr_enabled}
                      value={params.asr_interval_sec}
                      onChange={(e) => setParam("asr_interval_sec", e.target.value)}
                    />
                  </div>
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={params.latency_enabled}
                    onChange={(e) => setBoolParam("latency_enabled", e.target.checked)}
                  />
                  端到端时延（pull 后离线）
                </label>
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">时延窗口（秒，离线抽帧）</Label>
                  <Input
                    type="number"
                    min={0.5}
                    step={0.5}
                    disabled={!params.latency_enabled}
                    value={params.latency_window_sec}
                    onChange={(e) => setParam("latency_window_sec", e.target.value)}
                  />
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={params.mos_enabled}
                    onChange={(e) => setBoolParam("mos_enabled", e.target.checked)}
                  />
                  TTS MOS
                </label>
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">MOS 录音（秒）</Label>
                  <Input
                    type="number"
                    min={0.5}
                    step={0.5}
                    disabled={!params.mos_enabled}
                    value={params.mos_window_sec}
                    onChange={(e) => setParam("mos_window_sec", e.target.value)}
                  />
                </div>
              </div>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">
                  MOS 麦克风（dshow，可写 audio=名称 或纯名称）
                </Label>
                <Input
                  disabled={!params.mos_enabled}
                  placeholder="例：麦克风阵列 (适用于数字麦克风的英特尔® 智音技术)"
                  value={params.mos_input_device}
                  onChange={(e) => setParam("mos_input_device", e.target.value)}
                />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={params.wake_enabled}
                    onChange={(e) => setBoolParam("wake_enabled", e.target.checked)}
                  />
                  用例前唤醒播报
                </label>
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">唤醒后等待（秒）</Label>
                  <Input
                    type="number"
                    min={0}
                    step={0.5}
                    disabled={!params.wake_enabled}
                    value={params.wake_wait_sec}
                    onChange={(e) => setParam("wake_wait_sec", e.target.value)}
                  />
                </div>
                <div className="space-y-1 sm:col-span-2">
                  <Label className="text-xs text-muted-foreground">唤醒词（不评判）</Label>
                  <Input
                    disabled={!params.wake_enabled}
                    value={params.wake_text}
                    onChange={(e) => setParam("wake_text", e.target.value)}
                  />
                </div>
              </div>
            </div>
              <div className="space-y-2 border-t border-border/60 pt-3">
                <p className="text-sm font-medium">开测音色（仅语音库）</p>
                <p className="text-xs text-muted-foreground">
                  只能选择已预合成入库的音色。未命中该音色条目时回退本机系统语音（Windows SAPI）。请先到「预合成语音」生成。
                </p>
                {libraryVoices.length ? (
                  <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground">已入库音色</Label>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={`${params.tts_backend}:${params.tts_voice || "default"}`}
                      onChange={(e) => selectLibraryVoiceForRun(e.target.value)}
                    >
                      {libraryVoices.map((v) => (
                        <option key={v.profile} value={v.profile}>
                          {v.label} · {v.count} 条 · {v.profile}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : (
                  <p className="rounded-md border border-amber-200/80 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                    语音库尚无可用音色。请先到侧栏「数据集管理 → 预合成语音」生成后再开测。
                  </p>
                )}
                <div className="flex flex-wrap items-center gap-3">
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={ttsPreviewBusy || !libraryVoices.length}
                    onClick={() => void previewTts()}
                  >
                    {ttsPreviewBusy ? "合成中…" : "试听库内样例"}
                  </Button>
                  {ttsPreviewUrl ? (
                    <audio controls src={ttsPreviewUrl} className="h-8 max-w-full" />
                  ) : null}
                  <label className="flex items-center gap-2 text-xs">
                    <input
                      type="checkbox"
                      checked={params.tts_prefer_library}
                      onChange={(e) => setBoolParam("tts_prefer_library", e.target.checked)}
                    />
                    运行时优先用语音库
                  </label>
                </div>
                {ttsPreviewError ? (
                  <p className="text-xs text-rose-700">{ttsPreviewError}</p>
                ) : null}
              </div>


            {coverageWarn ? (
              <p className="rounded-md border border-amber-200/80 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                {coverageWarn}
              </p>
            ) : null}
            <div className="flex flex-wrap gap-3">
              <Button
                type="button"
                disabled={busy || calibrateRunning || !libraryVoices.length}
                title={
                  !libraryVoices.length
                    ? "请先预合成语音库音色"
                    : calibrateRunning
                      ? "请先停止预置校准"
                      : undefined
                }
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
        </section>

        <section className="animate-rise space-y-6" style={{ animationDelay: "140ms" }}>
        <Card className="overflow-hidden">
          <CardHeader>
            <div className="flex items-center justify-between gap-2">
              <CardTitle className="text-xl font-semibold">当前任务</CardTitle>
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
                    <div className="text-xs text-muted-foreground">GUI 通过</div>
                    <div className="text-lg font-semibold">{active.passed ?? "—"}</div>
                  </div>
                  <div className="rounded-md bg-secondary/70 px-2 py-3">
                    <div className="text-xs text-muted-foreground">GUI 失败</div>
                    <div className="text-lg font-semibold">{active.failed ?? "—"}</div>
                  </div>
                  <div className="rounded-md bg-secondary/70 px-2 py-3">
                    <div className="text-xs text-muted-foreground">错误</div>
                    <div className="text-lg font-semibold">{active.errors ?? "—"}</div>
                  </div>
                </div>
                {(() => {
                  const agg = aggregateJobMetrics(active.results || []);
                  if (!agg.asr_n && !agg.latency_n && !agg.mos_n) return null;
                  return (
                    <div className="grid grid-cols-3 gap-2 text-center text-sm">
                      <div className="rounded-md border border-border/70 bg-card/50 px-2 py-2">
                        <div className="text-[11px] text-muted-foreground">
                          ASR 均{agg.asr_n ? ` · n=${agg.asr_n}` : ""}
                        </div>
                        <div className="font-semibold">
                          {agg.asr_mean != null ? `${(agg.asr_mean * 100).toFixed(0)}%` : "—"}
                        </div>
                      </div>
                      <div className="rounded-md border border-border/70 bg-card/50 px-2 py-2">
                        <div className="text-[11px] text-muted-foreground">
                          时延均{agg.latency_n ? ` · n=${agg.latency_n}` : ""}
                        </div>
                        <div className="font-semibold">
                          {agg.latency_mean_sec != null
                            ? `${agg.latency_mean_sec.toFixed(1)}s`
                            : "—"}
                        </div>
                      </div>
                      <div className="rounded-md border border-border/70 bg-card/50 px-2 py-2">
                        <div className="text-[11px] text-muted-foreground">
                          MOS 均{agg.mos_n ? ` · n=${agg.mos_n}` : ""}
                        </div>
                        <div className="font-semibold">
                          {agg.mos_mean != null ? agg.mos_mean.toFixed(1) : "—"}
                        </div>
                      </div>
                    </div>
                  );
                })()}
                {active.error ? <p className="text-sm text-rose-700">{active.error}</p> : null}
                <ul className="max-h-72 space-y-2 overflow-y-auto">
                  {(active.progress?.cases?.length
                    ? active.progress.cases.map((c) => {
                        const result = (active.results || []).find(
                          (r) => r.case_id === c.case_id
                        );
                        const hasRun = Boolean(result?.run_dir) || Boolean(c.run_dir);
                        const started =
                          hasRun ||
                          !["pending", ""].includes(c.device_status || "pending") ||
                          ["passed", "failed", "error", "skipped"].includes(
                            c.judge_status || c.status
                          );
                        return {
                          key: `${c.index}-${c.case_id}`,
                          case_id: c.case_id,
                          name: c.name,
                          status: c.status,
                          device_label: c.device_label || c.device_status || "",
                          judge_label: c.judge_label || c.judge_status || "",
                          linkable: hasRun || ["passed", "failed"].includes(c.status),
                          show:
                            started ||
                            active.status === "running" ||
                            active.status === "queued",
                          metrics: result?.metrics,
                        };
                      })
                    : (active.results || []).map((r) => ({
                        key: r.case_id,
                        case_id: r.case_id,
                        name: r.name,
                        status:
                          r.passed === true ? "passed" : r.passed === false ? "failed" : "pending",
                        device_label: "",
                        judge_label: "",
                        linkable: Boolean(r.run_dir) || r.passed === true || r.passed === false,
                        show: true,
                        metrics: r.metrics,
                      }))
                  )
                    .filter((row) => row.show)
                    .map((row) => {
                    const body = (
                      <>
                        <span className="min-w-0">
                          <span className="font-medium">{row.case_id}</span>
                          <span className="ml-2 text-muted-foreground">{row.name}</span>
                          <span className="mt-0.5 block text-xs text-muted-foreground">
                            设备：{row.device_label || "—"}
                            <span className="mx-1.5 opacity-40">|</span>
                            GUI：{row.judge_label || "—"}
                          </span>
                          <span className="mt-0.5 block">
                            <MetricsChips metrics={row.metrics} />
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
        </>
      ) : null}
    </div>
  );
}
