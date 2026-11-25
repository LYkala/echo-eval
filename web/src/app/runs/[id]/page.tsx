import Link from "next/link";
import { notFound } from "next/navigation";
import { readJob } from "@/lib/jobs";
import { Badge } from "@/components/ui/badge";
import { MetricsChips } from "@/components/case-metrics";

export const dynamic = "force-dynamic";

export default async function RunDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const job = readJob(id);
  if (!job) notFound();

  const withMetrics = job.results.filter((r) => r.metrics);
  const asrAcc =
    withMetrics
      .map((r) => r.metrics?.asr_accuracy)
      .filter((x): x is number => typeof x === "number") || [];
  const latMeans =
    withMetrics
      .map((r) => r.metrics?.latency_mean_sec)
      .filter((x): x is number => typeof x === "number") || [];
  const mosMeans =
    withMetrics
      .map((r) => r.metrics?.mos_mean)
      .filter((x): x is number => typeof x === "number") || [];
  const avg = (xs: number[]) =>
    xs.length ? (xs.reduce((a, b) => a + b, 0) / xs.length) : null;

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
          </p>
        </div>
        {job.status === "passed" ? (
          <Badge variant="pass">PASS</Badge>
        ) : job.status === "failed" ? (
          <Badge variant="fail">FAIL</Badge>
        ) : (
          <Badge variant="secondary">{job.status}</Badge>
        )}
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

      {withMetrics.length ? (
        <div className="grid gap-3 text-center sm:grid-cols-3">
          <div className="rounded-lg border bg-card/80 px-3 py-4">
            <div className="text-xs text-muted-foreground">ASR 准确率（均）</div>
            <div className="text-2xl font-semibold">
              {avg(asrAcc) != null ? `${(avg(asrAcc)! * 100).toFixed(0)}%` : "—"}
            </div>
          </div>
          <div className="rounded-lg border bg-card/80 px-3 py-4">
            <div className="text-xs text-muted-foreground">时延均值</div>
            <div className="text-2xl font-semibold">
              {avg(latMeans) != null ? `${avg(latMeans)!.toFixed(1)}s` : "—"}
            </div>
          </div>
          <div className="rounded-lg border bg-card/80 px-3 py-4">
            <div className="text-xs text-muted-foreground">MOS 均值</div>
            <div className="text-2xl font-semibold">
              {avg(mosMeans) != null ? avg(mosMeans)!.toFixed(1) : "—"}
            </div>
          </div>
        </div>
      ) : null}

      {job.error ? <p className="text-sm text-rose-700">{job.error}</p> : null}

      <ul className="space-y-2">
        {job.results.map((r) => (
          <li key={r.case_id}>
            <Link
              href={`/runs/${job.id}/cases/${encodeURIComponent(r.case_id)}`}
              className="flex items-center justify-between gap-3 rounded-lg border bg-card/80 px-4 py-3 text-sm hover:border-primary/40"
            >
              <span className="min-w-0">
                <span className="font-medium">{r.case_id}</span>
                <span className="ml-2 text-muted-foreground">{r.name}</span>
                <span className="mt-0.5 block">
                  <MetricsChips metrics={r.metrics} />
                </span>
              </span>
              {r.passed === true ? (
                <Badge variant="pass">GUI PASS</Badge>
              ) : r.passed === false ? (
                <Badge variant="fail">GUI FAIL</Badge>
              ) : (
                <Badge variant="secondary">—</Badge>
              )}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
