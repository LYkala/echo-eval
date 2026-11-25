/** Pure dataset-level metric means (safe for client + server). */

export type MetricsLike = {
  asr_accuracy?: number | null;
  asr_total?: number;
  latency_mean_sec?: number | null;
  latency_measured?: number;
  mos_mean?: number | null;
  mos_scored?: number;
};

export type ResultLike = {
  metrics?: MetricsLike | null;
};

/**
 * Dataset-level means. MOS / latency only count when a value was actually
 * obtained (measured/scored); missing values do not affect the average.
 */
export function aggregateJobMetrics(results: ResultLike[]): {
  asr_mean: number | null;
  asr_n: number;
  latency_mean_sec: number | null;
  latency_n: number;
  mos_mean: number | null;
  mos_n: number;
} {
  const asr: number[] = [];
  const lat: number[] = [];
  const mos: number[] = [];
  for (const r of results) {
    const m = r.metrics;
    if (!m) continue;
    if (typeof m.asr_accuracy === "number" && (m.asr_total ?? 0) > 0) {
      asr.push(m.asr_accuracy);
    }
    if (typeof m.latency_mean_sec === "number" && (m.latency_measured ?? 0) > 0) {
      lat.push(m.latency_mean_sec);
    }
    if (typeof m.mos_mean === "number" && (m.mos_scored ?? 0) > 0) {
      mos.push(m.mos_mean);
    }
  }
  const mean = (xs: number[]) =>
    xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : null;
  return {
    asr_mean: mean(asr),
    asr_n: asr.length,
    latency_mean_sec: mean(lat),
    latency_n: lat.length,
    mos_mean: mean(mos),
    mos_n: mos.length,
  };
}
