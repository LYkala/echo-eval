"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";

export function RunRerunControls({
  jobId,
  caseId,
  disabled,
  label,
}: {
  jobId: string;
  caseId?: string;
  disabled?: boolean;
  label?: string;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");

  async function rerun() {
    if (busy || disabled) return;
    const mode = caseId ? "case" : "failed";
    const tip = caseId
      ? `重新执行用例 ${caseId}？将覆盖本任务中该条历史结果。`
      : "重新执行全部失败/错误用例？将覆盖本任务中对应历史结果。";
    if (!window.confirm(tip)) return;
    setBusy(true);
    setMsg("");
    try {
      const res = await fetch(`/api/runs/${encodeURIComponent(jobId)}/rerun`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ mode, caseId }),
      });
      const data = (await res.json()) as { error?: string };
      if (!res.ok) throw new Error(data.error || "重跑失败");
      setMsg("已开始重跑（覆盖历史）");
      router.refresh();
    } catch (e) {
      setMsg(String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="inline-flex flex-col items-end gap-1">
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={busy || disabled}
        onClick={() => void rerun()}
      >
        {busy ? "重跑中…" : label || (caseId ? "重跑本条" : "重跑全部失败")}
      </Button>
      {msg ? <span className="max-w-[14rem] text-right text-xs text-muted-foreground">{msg}</span> : null}
    </span>
  );
}
