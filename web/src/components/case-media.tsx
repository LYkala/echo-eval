"use client";

import { useState } from "react";

export function CaseMedia({
  audios,
  frames,
}: {
  audios: { name: string; url: string }[];
  frames: string[];
}) {
  const [idx, setIdx] = useState(0);
  const frame = frames[idx] || frames[0];

  return (
    <div className="space-y-4">
      {audios.length ? (
        <div className="space-y-2">
          {audios.map((a) => (
            <div key={a.name} className="rounded-md border bg-card/70 px-3 py-2">
              <div className="mb-1 text-xs text-muted-foreground">{a.name}</div>
              <audio controls preload="metadata" src={a.url} className="w-full" />
            </div>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">暂无步骤音频（演示任务可能仅有文本）</p>
      )}

      <div>
        <div className="mb-2 flex items-center justify-between text-sm">
          <span className="font-medium">截帧</span>
          {frames.length ? (
            <span className="text-muted-foreground">
              {Math.min(idx + 1, frames.length)} / {frames.length}
            </span>
          ) : null}
        </div>
        {frame ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={frame}
            alt={`frame ${idx + 1}`}
            className="max-h-[480px] w-full rounded-md border object-contain bg-black/5"
          />
        ) : (
          <p className="text-sm text-muted-foreground">无截帧</p>
        )}
        {frames.length > 1 ? (
          <div className="mt-2 flex gap-2">
            <button
              type="button"
              className="rounded-md border px-3 py-1 text-sm hover:bg-accent"
              onClick={() => setIdx((v) => Math.max(0, v - 1))}
            >
              上一帧
            </button>
            <button
              type="button"
              className="rounded-md border px-3 py-1 text-sm hover:bg-accent"
              onClick={() => setIdx((v) => Math.min(frames.length - 1, v + 1))}
            >
              下一帧
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
