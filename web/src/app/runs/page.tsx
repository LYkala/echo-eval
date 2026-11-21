import Link from "next/link";
import { ensureDemoJob, listJobs } from "@/lib/jobs";
import { Badge } from "@/components/ui/badge";

export const dynamic = "force-dynamic";

export default function RunsPage() {
  ensureDemoJob();
  const jobs = listJobs();
  return (
    <div className="animate-rise space-y-6">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">历史任务</h1>
        <p className="mt-1 text-sm text-muted-foreground">本机 artifacts/web/jobs 下的评测记录</p>
      </div>
      <ul className="space-y-3">
        {jobs.map((j) => (
          <li key={j.id}>
            <Link
              href={`/runs/${j.id}`}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border/80 bg-card/80 px-4 py-3 transition hover:border-primary/35"
            >
              <div>
                <div className="font-medium">{j.id}</div>
                <div className="text-sm text-muted-foreground">
                  {j.datasetName} · {j.backend}
                  {j.demo ? " · 演示" : ""}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">
                  P{j.passed ?? 0}/F{j.failed ?? 0}/E{j.errors ?? 0}
                </span>
                {j.status === "passed" ? (
                  <Badge variant="pass">PASS</Badge>
                ) : j.status === "failed" ? (
                  <Badge variant="fail">FAIL</Badge>
                ) : j.status === "running" ? (
                  <Badge variant="running">running</Badge>
                ) : (
                  <Badge variant="secondary">{j.status}</Badge>
                )}
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
