import { ensureDemoJob, listJobs } from "@/lib/jobs";
import { RunsListClient } from "@/components/runs-list-client";

export const dynamic = "force-dynamic";

export default function RunsPage() {
  ensureDemoJob();
  const jobs = listJobs().map((j) => ({
    id: j.id,
    datasetName: j.datasetName,
    backend: j.backend,
    status: j.status,
    demo: j.demo,
    passed: j.passed,
    failed: j.failed,
    errors: j.errors,
    mediaPurged: j.mediaPurged,
    mediaBytesFreed: j.mediaBytesFreed,
  }));
  return (
    <div className="mx-auto max-w-4xl animate-rise space-y-6">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold tracking-tight">任务管理</h1>
        <p className="text-sm text-muted-foreground">
          本机 artifacts/web/jobs 下的评测记录。可清理录屏等大文件以释放磁盘。
        </p>
      </div>
      <RunsListClient initialJobs={jobs} />
    </div>
  );
}
