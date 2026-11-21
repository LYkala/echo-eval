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

const INITIAL_STEPS: StepRow[] = [
  { id: "config", label: "读取设备配置", state: "pending" },
  { id: "python", label: "检查 echo-eval 源码", state: "pending" },
  { id: "adb", label: "检查 ADB", state: "pending" },
  { id: "device", label: "连接设备（短超时）", state: "pending" },
  { id: "ffmpeg", label: "检查 FFmpeg", state: "pending" },
  { id: "audio", label: "检查音频播放", state: "pending" },
  { id: "tts", label: "检查 TTS", state: "pending" },
  { id: "vl", label: "检查 VL 配置", state: "pending" },
];

export function ConsoleClient() {
  const [env, setEnv] = useState<EnvResult | null>(null);
  const [steps, setSteps] = useState<StepRow[]>(INITIAL_STEPS);
  const [envChecking, setEnvChecking] = useState(false);
  const [envProgress, setEnvProgress] = useState("尚未检查");
  const [envError, setEnvError] = useState("");
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [datasetId, setDatasetId] = useState("__builtin_sample__");
  const [caseId, setCaseId] = useState("demo_weather_001");
  const [backend, setBackend] = useState<"auto" | "vl" | "mock">("mock");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");
  const [active, setActive] = useState<Job | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const refreshDatasets = useCallback(async () => {
    try {
      const res = await fetch("/api/datasets", { cache: "no-store" });
      const data = (await res.json()) as { items: Dataset[] };
      setDatasets(data.items || []);
    } catch (e) {
      setMsg(`加载数据集失败: ${String(e)}`);
    }
  }, []);

  const refreshEnv = useCallback(async () => {
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;

    setEnvChecking(true);
    setEnvError("");
    setEnvProgress("开始环境检查…");
    setSteps(INITIAL_STEPS.map((s) => ({ ...s, state: "pending", detail: undefined })));

    const timeout = window.setTimeout(() => ac.abort(), 45000);

    try {
      const res = await fetch("/api/env/stream", {
        cache: "no-store",
        signal: ac.signal,
        headers: { Accept: "text/event-stream" },
      });
      if (!res.ok || !res.body) {
        throw new Error(`环境检查接口失败 HTTP ${res.status}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const chunks = buffer.split("\n\n");
        buffer = chunks.pop() || "";
        for (const chunk of chunks) {
          const line = chunk
            .split("\n")
            .map((l) => l.trim())
            .find((l) => l.startsWith("data:"));
          if (!line) continue;
          const payload = JSON.parse(line.slice(5).trim()) as {
            type: string;
            step?: string;
            label?: string;
            index?: number;
            total?: number;
            item?: EnvItem;
            result?: EnvResult;
            message?: string;
          };

          if (payload.type === "step_start") {
            setEnvProgress(`(${payload.index}/${payload.total}) ${payload.label}…`);
            setSteps((prev) =>
              prev.map((s) =>
                s.id === payload.step ? { ...s, state: "running", detail: "检查中…" } : s
              )
            );
          } else if (payload.type === "step_done" && payload.item) {
            setEnvProgress(
              `(${payload.index}/${payload.total}) ${payload.label}: ${payload.item.ok ? "OK" : "失败"}`
            );
            setSteps((prev) =>
              prev.map((s) =>
                s.id === payload.step
                  ? {
                      ...s,
                      state: payload.item!.ok ? "ok" : "fail",
                      detail: payload.item!.detail,
                    }
                  : s
              )
            );
          } else if (payload.type === "done" && payload.result) {
            setEnv(payload.result);
            setEnvProgress(
              payload.result.ok
                ? "环境检查完成：就绪"
                : "环境检查完成：未就绪（仍可打开演示任务）"
            );
            if (payload.result.error) setEnvError(payload.result.error);
          } else if (payload.type === "error") {
            throw new Error(payload.message || "环境检查出错");
          }
        }
      }
    } catch (e) {
      const aborted = ac.signal.aborted;
      const message = aborted
        ? "环境检查超时或已取消（45s）。请确认 python / adb 可用后重试。"
        : String(e);
      setEnvError(message);
      setEnvProgress("检查中断");
      setEnv((prev) =>
        prev || {
          ok: false,
          demoMode: true,
          items: [],
          checkedAt: new Date().toISOString(),
          error: message,
        }
      );
    } finally {
      window.clearTimeout(timeout);
      setEnvChecking(false);
    }
  }, []);

  useEffect(() => {
    void refreshEnv();
    void refreshDatasets();
    return () => abortRef.current?.abort();
  }, [refreshEnv, refreshDatasets]);

  useEffect(() => {
    if (!active || !["queued", "running"].includes(active.status)) return;
    const t = setInterval(async () => {
      const res = await fetch(`/api/runs/${active.id}`, { cache: "no-store" });
      if (!res.ok) return;
      const job = (await res.json()) as Job;
      setActive(job);
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

  const stepTone = (state: StepRow["state"]) => {
    if (state === "ok") return "text-emerald-700";
    if (state === "fail") return "text-rose-700";
    if (state === "running") return "text-amber-800";
    return "text-muted-foreground";
  };

  const stepMark = (state: StepRow["state"]) => {
    if (state === "ok") return "OK";
    if (state === "fail") return "失败";
    if (state === "running") return "…";
    return "等待";
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
      <section className="animate-rise space-y-6" style={{ animationDelay: "60ms" }}>
        <Card>
          <CardHeader>
            <CardTitle className="font-[family-name:var(--font-display)] text-2xl">环境检查</CardTitle>
            <CardDescription>
              分步检查会显示进度。设备连接使用短超时，避免一直卡住。
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap items-center gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => void refreshEnv()}
                disabled={envChecking}
              >
                {envChecking ? "检查中…" : "重新检查"}
              </Button>
              {envChecking ? (
                <Badge variant="running">进行中</Badge>
              ) : env ? (
                env.ok ? (
                  <Badge variant="pass">环境就绪</Badge>
                ) : (
                  <Badge variant="fail">未就绪</Badge>
                )
              ) : (
                <Badge variant="secondary">未开始</Badge>
              )}
              {env?.serial ? (
                <span className="text-xs text-muted-foreground">serial: {env.serial}</span>
              ) : null}
            </div>

            <p className="text-sm text-muted-foreground">
              <span className={envChecking ? "animate-pulse-dot" : ""}>{envProgress}</span>
            </p>
            {envError ? <p className="text-sm text-rose-700">{envError}</p> : null}

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
                  <span className={`shrink-0 font-medium ${stepTone(s.state)}`}>{stepMark(s.state)}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="font-[family-name:var(--font-display)] text-2xl">数据集与开测</CardTitle>
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
              <CardTitle className="font-[family-name:var(--font-display)] text-2xl">当前任务</CardTitle>
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
