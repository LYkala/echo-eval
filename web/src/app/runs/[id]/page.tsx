import Link from "next/link";
import { notFound } from "next/navigation";
import { readJob } from "@/lib/jobs";
import { Badge } from "@/components/ui/badge";

export const dynamic = "force-dynamic";

export default async function RunDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const job = readJob(id);
  if (!job) notFound();

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
          <div className="text-xs text-muted-foreground">通过</div>
          <div className="text-2xl font-semibold">{job.passed ?? 0}</div>
        </div>
        <div className="rounded-lg border bg-card/80 px-3 py-4">
          <div className="text-xs text-muted-foreground">失败</div>
          <div className="text-2xl font-semibold">{job.failed ?? 0}</div>
        </div>
        <div className="rounded-lg border bg-card/80 px-3 py-4">
          <div className="text-xs text-muted-foreground">错误</div>
          <div className="text-2xl font-semibold">{job.errors ?? 0}</div>
        </div>
      </div>

      {job.error ? <p className="text-sm text-rose-700">{job.error}</p> : null}

      <ul className="space-y-2">
        {job.results.map((r) => (
          <li key={r.case_id}>
            <Link
              href={`/runs/${job.id}/cases/${encodeURIComponent(r.case_id)}`}
              className="flex items-center justify-between rounded-lg border bg-card/80 px-4 py-3 text-sm hover:border-primary/40"
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
    </div>
  );
}
