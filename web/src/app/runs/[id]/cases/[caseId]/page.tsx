import Link from "next/link";
import { notFound } from "next/navigation";
import { getCaseDetail } from "@/lib/jobs";
import { Badge } from "@/components/ui/badge";
import { CaseMedia } from "@/components/case-media";

export const dynamic = "force-dynamic";

export default async function CaseDetailPage({
  params,
}: {
  params: Promise<{ id: string; caseId: string }>;
}) {
  const { id, caseId } = await params;
  const detail = getCaseDetail(id, decodeURIComponent(caseId));
  if (!detail) notFound();

  const steps = (detail.caseJson as { steps?: { text: string; wait_sec: number }[] } | null)?.steps || [];
  const expected =
    (detail.caseJson as { expected?: string } | null)?.expected ||
    ((detail.evaluation as { result_parsed?: unknown }) ? "" : "");
  const parsed =
    (detail.evaluation as { result_parsed?: { expectation?: string; result?: string; reason?: string }[] } | null)
      ?.result_parsed || [];

  return (
    <div className="animate-rise space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link href={`/runs/${id}`} className="text-sm text-muted-foreground hover:text-foreground">
            ← 返回任务
          </Link>
          <h1 className="mt-2 font-display text-3xl font-semibold">
            {detail.result.case_id}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">{detail.result.name}</p>
        </div>
        {detail.result.passed === true ? (
          <Badge variant="pass">PASS</Badge>
        ) : detail.result.passed === false ? (
          <Badge variant="fail">FAIL</Badge>
        ) : (
          <Badge variant="secondary">—</Badge>
        )}
      </div>

      <section className="space-y-3">
        <h2 className="font-display text-xl font-semibold">测试步骤 / 音频</h2>
        <ol className="space-y-2 text-sm">
          {steps.map((s, i) => (
            <li key={i} className="rounded-md border bg-card/70 px-3 py-2">
              <span className="font-medium">{i + 1}. {s.text}</span>
              <span className="ml-2 text-muted-foreground">wait {s.wait_sec}s</span>
            </li>
          ))}
        </ol>
        <CaseMedia audios={detail.audios} frames={detail.frames} />
      </section>

      <section className="space-y-3">
        <h2 className="font-display text-xl font-semibold">预期结果</h2>
        <pre className="whitespace-pre-wrap rounded-md border bg-card/70 p-3 text-sm">
          {expected || (detail.caseJson as { exceptions?: string[] } | null)?.exceptions?.join("\n") || "—"}
        </pre>
      </section>

      <section className="space-y-3">
        <h2 className="font-display text-xl font-semibold">评判</h2>
        <ul className="space-y-2">
          {parsed.length ? (
            parsed.map((p, i) => (
              <li key={i} className="rounded-md border bg-card/70 px-3 py-2 text-sm">
                <div className="font-medium">
                  {p.expectation || "expectation"} · {p.result}
                </div>
                <div className="text-muted-foreground">{p.reason}</div>
              </li>
            ))
          ) : (
            <li className="text-sm text-muted-foreground">无解析结果</li>
          )}
        </ul>
      </section>

      {detail.report ? (
        <section className="space-y-3">
          <h2 className="font-display text-xl font-semibold">报告</h2>
          <pre className="whitespace-pre-wrap rounded-md border bg-card/70 p-3 text-sm">{detail.report}</pre>
        </section>
      ) : null}
    </div>
  );
}
