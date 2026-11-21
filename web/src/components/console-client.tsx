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
  const [env, setEnv] = useState<EnvResult | null>(null);
  const [steps, setSteps] = useState<StepRow[]>(blankSteps);
  const [envChecking, setEnvChecking] = useState(false);
  const [envProgress, setEnvProgress] = useState("点击「重新检查」开始");
  const [envError, setEnvError] = useState("");
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [datasetId, setDatasetId] = useState("__builtin_sample__");
  const [caseId, setCaseId] = useState("demo_weather_001");
  const [backend, setBackend] = useState<"auto" | "vl" | "mock">("mock");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");
  const [active, setActive] = useState<Job | null>(null);
  const runIdRef = useRef(0);

  const refreshDatasets = useCallback(async () => {
    try {
      const res = await fetch("/api/datasets", { cache: "no-store" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = (await res.json()) as { items: Dataset[] };
      setDatasets(data.items || []);
    } catch (e) {
      setMsg(`加载数据集失败: ${String(e)}`);
    }
  }, []);

  const refreshEnv = useCallback(async () => {
    const runId = ++runIdRef.current;
    setEnvChecking(true);
    setEnvError("");
    setEnv(null);
    setSteps(blankSteps());
    setEnvProgress("已开始：准备逐项检查…");

    const items: EnvItem[] = [];
    let serial: string | undefined;
    let stopped = false;

    try {
      for (let i = 0; i < STEP_DEFS.length; i++) {
        if (runId !== runIdRef.current) return; // superseded
        const def = STEP_DEFS[i];
        setEnvProgress(`(${i + 1}/${STEP_DEFS.length}) 正在检查：${def.label}`);
        setSteps((prev) =>
          prev.map((s) => (s.id === def.id ? { ...s, state: "running", detail: "检查中…" } : s))
        );

        const ac = new AbortController();
        const timer = window.setTimeout(() => ac.abort(), 20000);
        let item: EnvItem;
        try {
          const res = await fetch(`/api/env/step?step=${encodeURIComponent(def.id)}`, {
            cache: "no-store",
            signal: ac.signal,
          });
          const data = (await res.json()) as {
            item?: EnvItem;
            serial?: string;
            error?: string;
          };
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
              ? "本步超时（20s）。请检查本机 python / adb 是否卡住。"
              : `请求失败: ${String(e)}`,
            required: !["audio", "tts", "vl"].includes(def.id),
          };
          if (aborted || String(e).includes("Failed to fetch")) {
            stopped = true;
          }
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

        if (stopped) {
          setEnvError("后续步骤已跳过：网络或服务异常。请确认 npm run dev 仍在运行。");
          break;
        }
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
    } catch (e) {
      if (runId !== runIdRef.current) return;
      const message = String(e);
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
  }, []);

  useEffect(() => {
    void refreshEnv();
    void refreshDatasets();
  }, [refreshEnv, refreshDatasets]);

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
              点击后会立刻显示进度；每一步单独请求，避免整页卡住。
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap items-center gap-3">
              <Button type="button" variant="outline" onClick={() => void refreshEnv()} disabled={envChecking}>
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
                <Badge variant="secondary">等待开始</Badge>
              )}
              {env?.serial ? (
                <span className="text-xs text-muted-foreground">serial: {env.serial}</span>
              ) : null}
            </div>

            <div
              className="rounded-md border px-3 py-2 text-sm"
              style={{ background: "hsl(40 18% 94%)", borderColor: "hsl(40 14% 80%)" }}
            >
              <div className="font-medium">当前进度</div>
              <div className={envChecking ? "animate-pulse-dot mt-1" : "mt-1"}>{envProgress}</div>
              {envError ? <div className="mt-1 text-rose-700">{envError}</div> : null}
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
