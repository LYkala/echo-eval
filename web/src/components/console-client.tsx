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
type Dataset = { id: string; name: string; size: number; builtin?: boolean };
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
};

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
  const [datasetId, setDatasetId] = useState("__builtin_sample__");
  const [caseId, setCaseId] = useState("demo_weather_001");
  const [backend, setBackend] = useState<"auto" | "vl" | "mock">("mock");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");
  const [active, setActive] = useState<Job | null>(null);
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
        pushLog(`ping HTTP ${pingRes.status}: ${pingText.slice(0, 180)}`);
        if (!pingRes.ok) throw new Error(`ping 失败 HTTP ${pingRes.status}`);
      } catch (e) {
        const message = `无法连接本机 API: ${String(e)}。请确认 Git Bash 里 npm run dev 仍在运行，并用 http://127.0.0.1:3847 打开（不要用 file://）。`;
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
  }, [mounted, refreshEnv, refreshDatasets]);

  useEffect(() => {
    if (!active || !["queued", "running"].includes(active.status)) return;
    const t = setInterval(async () => {
      const res = await fetch(`/api/runs/${active.id}`, { cache: "no-store" });
      if (!res.ok) return;
      setActive(await res.json());
    }, 2000);
    return () => clearInterval(t);
  }, [active]);

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

  async function startRun(demo = false) {
    setBusy(true);
    setMsg("");
    try {
      const res = await fetch("/api/runs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(demo ? { demo: true } : { datasetId, caseId: caseId || null, backend }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "启动失败");
      setActive(data);
      setMsg(data.note || `任务已启动：${data.id}`);
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
      setActive(await res.json());
    } finally {
      setBusy(false);
    }
  }

  const statusBadge = (s: string) => {
    if (s === "passed") return <Badge variant="pass">PASS</Badge>;
    if (s === "failed") return <Badge variant="fail">FAIL</Badge>;
    if (s === "running" || s === "queued") return <Badge variant="running">{s}</Badge>;
    return <Badge variant="secondary">{s}</Badge>;
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
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
              <Label htmlFor="dataset">选用数据集</Label>
              <select
                id="dataset"
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={datasetId}
                onChange={(e) => setDatasetId(e.target.value)}
              >
                {datasets.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="case">用例编号（可空=全部）</Label>
                <Input
                  id="case"
                  value={caseId}
                  onChange={(e) => setCaseId(e.target.value)}
                  placeholder="demo_weather_001"
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
            <div className="flex flex-wrap gap-3">
              <Button type="button" disabled={busy} onClick={() => void startRun(false)}>
                {busy ? "处理中…" : "开始测试"}
              </Button>
              <Button type="button" variant="secondary" disabled={busy} onClick={() => void startRun(true)}>
                打开演示任务
              </Button>
              {active && ["running", "queued"].includes(active.status) ? (
                <Button type="button" variant="destructive" disabled={busy} onClick={() => void stopRun()}>
                  停止
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
                <ul className="space-y-2">
                  {(active.results || []).map((r) => (
                    <li key={r.case_id}>
                      <Link
                        href={`/runs/${active.id}/cases/${encodeURIComponent(r.case_id)}`}
                        className="flex items-center justify-between rounded-md border border-border/80 bg-card/60 px-3 py-2 text-sm transition hover:border-primary/40 hover:bg-accent/40"
                      >
                        <span>
                          <span className="font-medium">{r.case_id}</span>
                          <span className="ml-2 text-muted-foreground">{r.name}</span>
                        </span>
                        {r.passed === true ? (
                          <Badge variant="pass">PASS</Badge>
                        ) : r.passed === false ? (
                          <Badge variant="fail">FAIL</Badge>
                        ) : (
                          <Badge variant="secondary">—</Badge>
                        )}
                      </Link>
                    </li>
                  ))}
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
