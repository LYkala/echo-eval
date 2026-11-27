import Link from "next/link";
import { notFound } from "next/navigation";
import { readJob } from "@/lib/jobs";
import { aggregateJobMetrics } from "@/lib/metrics-aggregate";
import { Badge } from "@/components/ui/badge";
import { MetricsChips } from "@/components/case-metrics";
import { RunRerunControls } from "@/components/run-rerun-controls";

export const dynamic = "force-dynamic";

export default async function RunDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const job = readJob(id);
  if (!job) notFound();

  const agg = aggregateJobMetrics(job.results || []);
  const progressCases = job.progress?.cases || [];
  const resultById = new Map((job.results || []).map((r) => [r.case_id, r]));
  const progressById = new Map(progressCases.map((c) => [c.case_id, c]));
  const running = ["queued", "running"].includes(job.status);
  // Finished or cancelled (not demo): allow per-case rerun regardless of pass/fail.
  const canRerunJob = !job.demo && !running;
  const failedCount = (job.results || []).filter(
    (r) => r.passed === false || (r.passed === null && Boolean(r.error))
  ).length;

  // Union progress + results so a single-case rerun (progress subset) does not
  // hide sibling history. Prefer results order when it already has siblings.
  const progressIdSet = new Set(progressCases.map((c) => c.case_id));
  const resultIds = (job.results || []).map((r) => r.case_id);
  const hasSiblingResults = resultIds.some((id) => !progressIdSet.has(id));
  const orderedIds: string[] = [];
  const seenIds = new Set<string>();
  const pushId = (id: string) => {
    if (!id || seenIds.has(id)) return;
    orderedIds.push(id);
    seenIds.add(id);
  };
  if (hasSiblingResults && resultIds.length) {
    for (const id of resultIds) pushId(id);
    for (const c of progressCases) pushId(c.case_id);
  } else if (progressCases.length) {
    for (const c of progressCases) pushId(c.case_id);
    for (const id of resultIds) pushId(id);
  } else {
    for (const id of resultIds) pushId(id);
  }

  const rows = orderedIds
    .map((caseId) => {
      const r = resultById.get(caseId);
      const c = progressById.get(caseId);
      const started =
        Boolean(r?.run_dir) ||
        Boolean(c?.run_dir) ||
        (c?.device_status && c.device_status !== "pending") ||
        ["passed", "failed", "error", "skipped"].includes(
          c?.judge_status || c?.status || ""
        ) ||
        typeof r?.passed === "boolean" ||
        Boolean(r?.error);
      if (!started && job.status === "cancelled") return null;
      return {
        case_id: caseId,
        name: c?.name || r?.name || caseId,
        passed: r?.passed ?? null,
        error: r?.error || "",
        run_dir: r?.run_dir || c?.run_dir || "",
        metrics: r?.metrics,
        device_label: c?.device_label || c?.device_status || "",
        judge_label: c?.judge_label || c?.judge_status || "",
        status:
          c?.status ||
          (r?.passed === true ? "passed" : r?.passed === false ? "failed" : "pending"),
      };
    })
    .filter((x): x is NonNullable<typeof x> => Boolean(x));

  return (
    <div className="animate-rise space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link href="/runs" className="text-sm text-muted-foreground hover:text-foreground">
            ← 历史任务
          </Link>
          <h1 className="mt-2 font-display text-3xl font-semibold">{job.id}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {job.datasetName} · backend={job.backend}
            {job.caseId ? ` · case=${job.caseId}` : " · 全表"}
            {job.status === "cancelled" ? " · 已取消（保留已完成用例）" : ""}
          </p>
        </div>
        <div className="flex flex-col items-end gap-2">
          {job.status === "passed" ? (
            <Badge variant="pass">PASS</Badge>
          ) : job.status === "failed" ? (
            <Badge variant="fail">FAIL</Badge>
          ) : job.status === "cancelled" ? (
            <Badge variant="secondary">已取消</Badge>
          ) : (
            <Badge variant="secondary">{job.status}</Badge>
          )}
          {canRerunJob ? (
            <RunRerunControls
              jobId={job.id}
              disabled={failedCount === 0}
              label="重跑全部失败"
            />
          ) : null}
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3 text-center">
        <div className="rounded-lg border bg-card/80 px-3 py-4">
          <div className="text-xs text-muted-foreground">GUI 通过</div>
          <div className="text-2xl font-semibold">{job.passed ?? 0}</div>
        </div>
        <div className="rounded-lg border bg-card/80 px-3 py-4">
          <div className="text-xs text-muted-foreground">GUI 失败</div>
          <div className="text-2xl font-semibold">{job.failed ?? 0}</div>
        </div>
        <div className="rounded-lg border bg-card/80 px-3 py-4">
          <div className="text-xs text-muted-foreground">错误</div>
          <div className="text-2xl font-semibold">{job.errors ?? 0}</div>
        </div>
      </div>

      {agg.asr_n || agg.latency_n || agg.mos_n ? (
        <div className="grid gap-3 text-center sm:grid-cols-3">
          <div className="rounded-lg border bg-card/80 px-3 py-4">
            <div className="text-xs text-muted-foreground">
              ASR 准确率（均）{agg.asr_n ? ` · n=${agg.asr_n}` : ""}
            </div>
            <div className="text-2xl font-semibold">
              {agg.asr_mean != null ? `${(agg.asr_mean * 100).toFixed(0)}%` : "—"}
            </div>
          </div>
          <div className="rounded-lg border bg-card/80 px-3 py-4">
            <div className="text-xs text-muted-foreground">
              时延均值{agg.latency_n ? ` · n=${agg.latency_n}` : ""}
            </div>
            <div className="text-2xl font-semibold">
              {agg.latency_mean_sec != null ? `${agg.latency_mean_sec.toFixed(1)}s` : "—"}
            </div>
          </div>
          <div className="rounded-lg border bg-card/80 px-3 py-4">
            <div className="text-xs text-muted-foreground">
              MOS 均值{agg.mos_n ? ` · n=${agg.mos_n}` : ""}
            </div>
            <div className="text-2xl font-semibold">
              {agg.mos_mean != null ? agg.mos_mean.toFixed(1) : "—"}
            </div>
          </div>
        </div>
      ) : null}

      {job.error ? <p className="text-sm text-rose-700">{job.error}</p> : null}

      {!rows.length ? (
        <p className="text-sm text-muted-foreground">暂无已执行用例记录。</p>
      ) : (
        <ul className="space-y-2">
          {rows.map((r) => {
            const body = (
              <>
                <span className="min-w-0">
                  <span className="font-medium">{r.case_id}</span>
                  <span className="ml-2 text-muted-foreground">{r.name}</span>
                  {(r.device_label || r.judge_label) && (
                    <span className="mt-0.5 block text-xs text-muted-foreground">
                      设备：{r.device_label || "—"}
                      <span className="mx-1.5 opacity-40">|</span>
                      GUI：{r.judge_label || "—"}
                    </span>
                  )}
                  <span className="mt-0.5 block">
                    <MetricsChips metrics={r.metrics} />
                  </span>
                </span>
                {r.passed === true ? (
                  <Badge variant="pass">GUI PASS</Badge>
                ) : r.passed === false ? (
                  <Badge variant="fail">GUI FAIL</Badge>
                ) : (
                  <Badge variant="secondary">{r.status || "—"}</Badge>
                )}
              </>
            );
            return (
              <li key={r.case_id} className="rounded-lg border bg-card/80 px-4 py-3">
                <div className="flex items-center justify-between gap-3 text-sm">
                  {r.run_dir ? (
                    <Link
                      href={`/runs/${job.id}/cases/${encodeURIComponent(r.case_id)}`}
                      className="flex min-w-0 flex-1 items-center justify-between gap-3 hover:opacity-90"
                    >
                      {body}
                    </Link>
                  ) : (
                    <div className="flex min-w-0 flex-1 items-center justify-between gap-3">{body}</div>
                  )}
                  {canRerunJob ? (
                    <RunRerunControls jobId={job.id} caseId={r.case_id} label="重跑本条" />
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
