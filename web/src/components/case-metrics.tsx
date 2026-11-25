"use client";

import { Badge } from "@/components/ui/badge";

type AsrUtterance = {
  index?: number;
  spoken_text?: string;
  recognized_text?: string;
  passed?: boolean;
  reason?: string;
};

type LatencyUtterance = {
  index?: number;
  status?: string;
  latency_sec?: number | null;
  action_frame?: string;
};

type MosUtterance = {
  index?: number;
  status?: string;
  mos?: number | null;
  reason?: string;
};

type AsrSummary = {
  enabled?: boolean;
  total?: number;
  passed?: number;
  failed?: number;
  accuracy?: number | null;
  utterances?: AsrUtterance[];
};

type LatencySummary = {
  enabled?: boolean;
  total?: number;
  measured?: number;
  no_action?: number;
  mean_sec?: number | null;
  utterances?: LatencyUtterance[];
};

type MosSummary = {
  enabled?: boolean;
  total?: number;
  scored?: number;
  na?: number;
  mean_mos?: number | null;
  utterances?: MosUtterance[];
};

function pct(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(Number(n))) return "—";
  return `${(Number(n) * 100).toFixed(1)}%`;
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-md border border-border/70 bg-secondary/40 px-3 py-2 text-center">
      <div className="text-[11px] text-muted-foreground">{label}</div>
      <div className="text-lg font-semibold tabular-nums">{value}</div>
    </div>
  );
}

export function CaseMetricsPanels({
  asr,
  latency,
  mos,
  asrFrames,
  mosAudios,
}: {
  asr?: AsrSummary | null;
  latency?: LatencySummary | null;
  mos?: MosSummary | null;
  asrFrames?: { name: string; url: string }[];
  mosAudios?: { name: string; url: string }[];
}) {
  const showAsr = asr && (asr.enabled !== false || (asr.total ?? 0) > 0);
  const showLat = latency && (latency.enabled !== false || (latency.total ?? 0) > 0);
  const showMos = mos && (mos.enabled !== false || (mos.total ?? 0) > 0);
  if (!showAsr && !showLat && !showMos) {
    return (
      <p className="text-sm text-muted-foreground">
        暂无 ASR / 时延 / MOS 数据（可能未启用或用例尚未写完指标文件）。
      </p>
    );
  }

  return (
    <div className="space-y-8">
      {showAsr ? (
        <section className="space-y-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="font-display text-xl font-semibold">ASR 准确率</h2>
            <span className="text-xs text-muted-foreground">与 GUI 成功率互不影响</span>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <Stat label="准确率" value={pct(asr.accuracy)} />
            <Stat label="通过句" value={asr.passed ?? 0} />
            <Stat label="失败句" value={asr.failed ?? 0} />
            <Stat label="总句数" value={asr.total ?? 0} />
          </div>
          <ul className="space-y-2">
            {(asr.utterances || []).map((u, i) => (
              <li key={i} className="rounded-md border bg-card/70 px-3 py-2 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">第 {u.index ?? i + 1} 句</span>
                  {u.passed ? (
                    <Badge variant="pass">一致</Badge>
                  ) : (
                    <Badge variant="fail">不一致</Badge>
                  )}
                </div>
                <div className="mt-1 text-muted-foreground">
                  播报：{u.spoken_text || "—"}
                  <span className="mx-1.5 opacity-40">→</span>
                  识别：{u.recognized_text || "—"}
                </div>
                {u.reason ? <div className="mt-0.5 text-xs text-muted-foreground">{u.reason}</div> : null}
              </li>
            ))}
          </ul>
          {asrFrames && asrFrames.length > 0 ? (
            <div className="space-y-2">
              <p className="text-xs text-muted-foreground">ASR / 句后截屏（{asrFrames.length}）</p>
              <div className="flex gap-2 overflow-x-auto pb-1">
                {asrFrames.slice(0, 16).map((f) => (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    key={f.url}
                    src={f.url}
                    alt={f.name}
                    className="h-28 w-auto shrink-0 rounded border bg-black/5 object-contain"
                    title={f.name}
                  />
                ))}
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      {showLat ? (
        <section className="space-y-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="font-display text-xl font-semibold">端到端时延</h2>
            <span className="text-xs text-muted-foreground">说完 → 画面首变（忽略底部文字）</span>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <Stat
              label="平均时延"
              value={
                latency.mean_sec === null || latency.mean_sec === undefined
                  ? "—"
                  : `${Number(latency.mean_sec).toFixed(1)}s`
              }
            />
            <Stat label="已测到" value={latency.measured ?? 0} />
            <Stat label="无操控" value={latency.no_action ?? 0} />
            <Stat label="总句数" value={latency.total ?? 0} />
          </div>
          <ul className="space-y-2">
            {(latency.utterances || []).map((u, i) => (
              <li key={i} className="rounded-md border bg-card/70 px-3 py-2 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">第 {u.index ?? i + 1} 句</span>
                  {u.status === "measured" ? (
                    <Badge variant="pass">{u.latency_sec != null ? `${u.latency_sec}s` : "已测"}</Badge>
                  ) : u.status === "no_action" ? (
                    <Badge variant="secondary">无操控</Badge>
                  ) : (
                    <Badge variant="secondary">{u.status || "—"}</Badge>
                  )}
                </div>
                {u.action_frame ? (
                  <div className="mt-0.5 text-xs text-muted-foreground">首变帧：{u.action_frame}</div>
                ) : null}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {showMos ? (
        <section className="space-y-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="font-display text-xl font-semibold">TTS MOS</h2>
            <span className="text-xs text-muted-foreground">主机麦 · 无回复记 N/A</span>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <Stat
              label="平均 MOS"
              value={
                mos.mean_mos === null || mos.mean_mos === undefined
                  ? "—"
                  : Number(mos.mean_mos).toFixed(1)
              }
            />
            <Stat label="已评分" value={mos.scored ?? 0} />
            <Stat label="N/A" value={mos.na ?? 0} />
            <Stat label="总句数" value={mos.total ?? 0} />
          </div>
          <ul className="space-y-2">
            {(mos.utterances || []).map((u, i) => (
              <li key={i} className="rounded-md border bg-card/70 px-3 py-2 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">第 {u.index ?? i + 1} 句</span>
                  {u.status === "scored" ? (
                    <Badge variant="pass">MOS {u.mos ?? "—"}</Badge>
                  ) : u.status === "no_speech" ? (
                    <Badge variant="secondary">N/A</Badge>
                  ) : (
                    <Badge variant="secondary">{u.status || "—"}</Badge>
                  )}
                </div>
                {u.reason ? <div className="mt-0.5 text-xs text-muted-foreground">{u.reason}</div> : null}
              </li>
            ))}
          </ul>
          {mosAudios && mosAudios.length > 0 ? (
            <div className="space-y-2">
              <p className="text-xs text-muted-foreground">MOS 录音</p>
              <ul className="space-y-2">
                {mosAudios.map((a) => (
                  <li key={a.url} className="flex flex-wrap items-center gap-3 text-sm">
                    <span className="text-muted-foreground">{a.name}</span>
                    <audio controls preload="none" src={a.url} className="h-8 max-w-full" />
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

/** Compact one-line chips for case lists. */
export function MetricsChips({
  metrics,
}: {
  metrics?: {
    asr_accuracy?: number | null;
    asr_passed?: number;
    asr_total?: number;
    latency_mean_sec?: number | null;
    latency_no_action?: number;
    mos_mean?: number | null;
    mos_na?: number;
  } | null;
}) {
  if (!metrics) return null;
  const parts: string[] = [];
  if (metrics.asr_total != null && metrics.asr_total > 0) {
    parts.push(
      `ASR ${
        metrics.asr_accuracy != null
          ? `${(Number(metrics.asr_accuracy) * 100).toFixed(0)}%`
          : `${metrics.asr_passed ?? 0}/${metrics.asr_total}`
      }`
    );
  }
  if (metrics.latency_mean_sec != null) {
    parts.push(`时延 ${Number(metrics.latency_mean_sec).toFixed(1)}s`);
  } else if ((metrics.latency_no_action ?? 0) > 0) {
    parts.push("时延 无操控");
  }
  if (metrics.mos_mean != null) {
    parts.push(`MOS ${Number(metrics.mos_mean).toFixed(1)}`);
  } else if ((metrics.mos_na ?? 0) > 0) {
    parts.push("MOS N/A");
  }
  if (!parts.length) return null;
  return <span className="text-xs text-muted-foreground">{parts.join(" · ")}</span>;
}
