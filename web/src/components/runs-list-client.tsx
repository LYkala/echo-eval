"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

export type RunsListJob = {
  id: string;
  datasetName: string;
  backend: string;
  status: string;
  demo?: boolean;
  passed?: number;
  failed?: number;
  errors?: number;
  mediaPurged?: boolean;
  mediaBytesFreed?: number;
};

function statusBadge(status: string) {
  if (status === "passed") return <Badge variant="pass">PASS</Badge>;
  if (status === "failed") return <Badge variant="fail">FAIL</Badge>;
  if (status === "running" || status === "queued") {
    return <Badge variant="running">{status}</Badge>;
  }
  return <Badge variant="secondary">{status}</Badge>;
}

export function RunsListClient({ initialJobs }: { initialJobs: RunsListJob[] }) {
  const [jobs, setJobs] = useState(initialJobs);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [batchBusy, setBatchBusy] = useState(false);
  const [msg, setMsg] = useState("");

  const refresh = useCallback(async () => {
    const res = await fetch("/api/runs", { cache: "no-store" });
    const data = (await res.json()) as { items?: RunsListJob[] };
    setJobs(data.items || []);
  }, []);

  async function purgeOne(jobId: string) {
    if (
      !window.confirm(
        `清理任务「${jobId}」的录屏/截帧/步骤音频？\n保留评判结果与任务记录。进行中的任务不可清理。`
      )
    ) {
      return;
    }
    setBusyId(jobId);
    setMsg("");
    try {
      const res = await fetch("/api/runs/cleanup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ mode: "purge_media", jobId }),
      });
      const data = (await res.json()) as {
        error?: string;
        reason?: string;
        bytesFreedLabel?: string;
        skipped?: boolean;
      };
      if (!res.ok) throw new Error(data.error || data.reason || `HTTP ${res.status}`);
      setMsg(
        data.skipped
          ? data.reason || "已跳过"
          : `已清理 ${jobId}，释放约 ${data.bytesFreedLabel || "0 B"}`
      );
      await refresh();
    } catch (e) {
      setMsg(String(e));
    } finally {
      setBusyId(null);
    }
  }

  async function purgeAll() {
    if (
      !window.confirm(
        "清理全部已结束任务的录屏/截帧/步骤音频？\n保留任务列表与评判摘要；不删除预置缓存。运行中任务会跳过。"
      )
    ) {
      return;
    }
    setBatchBusy(true);
    setMsg("");
    try {
      const res = await fetch("/api/runs/cleanup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ mode: "purge_media" }),
      });
      const data = (await res.json()) as {
        error?: string;
        bytesFreedLabel?: string;
        purged?: number;
        skipped?: number;
      };
      if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
      setMsg(
        `批量瘦身完成：清理 ${data.purged ?? 0} 个，跳过 ${data.skipped ?? 0} 个，释放约 ${
          data.bytesFreedLabel || "0 B"
        }`
      );
      await refresh();
    } catch (e) {
      setMsg(String(e));
    } finally {
      setBatchBusy(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant="secondary"
          disabled={batchBusy || !!busyId}
          onClick={() => void purgeAll()}
        >
          {batchBusy ? "清理中…" : "清理全部历史媒体"}
        </Button>
        <p className="text-xs text-muted-foreground">
          默认瘦身：删除 mp4 / 截帧 / wav，保留任务与评判结果；不触及 precondition_cache。
        </p>
      </div>
      {msg ? <p className="text-sm text-muted-foreground">{msg}</p> : null}
      <ul className="space-y-3">
        {jobs.map((j) => {
          const active = j.status === "running" || j.status === "queued";
          return (
            <li
              key={j.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border/80 bg-card px-4 py-3"
            >
              <Link href={`/runs/${j.id}`} className="min-w-0 flex-1 hover:opacity-90">
                <div className="font-medium">{j.id}</div>
                <div className="text-sm text-muted-foreground">
                  {j.datasetName} · {j.backend}
                  {j.demo ? " · 演示" : ""}
                  {j.mediaPurged ? " · 媒体已清理" : ""}
                </div>
              </Link>
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs text-muted-foreground">
                  P{j.passed ?? 0}/F{j.failed ?? 0}/E{j.errors ?? 0}
                </span>
                {statusBadge(j.status)}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={active || !!j.mediaPurged || batchBusy || busyId === j.id}
                  title={
                    active
                      ? "进行中不可清理"
                      : j.mediaPurged
                        ? "已清理"
                        : "删除录屏等大文件"
                  }
                  onClick={() => void purgeOne(j.id)}
                >
                  {busyId === j.id ? "清理中…" : j.mediaPurged ? "已瘦身" : "清理媒体"}
                </Button>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
