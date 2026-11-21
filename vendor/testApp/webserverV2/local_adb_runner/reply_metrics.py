"""Parse DemoReplyMetrics logcat lines from Device Demo."""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional


TAG = "DemoReplyMetrics"

_KV = re.compile(r"(\w+)=((?:(?!\s+\w+=).)+)")


@dataclass
class ReplyMetricsEvent:
    kind: str  # SEND | FIRST_TOKEN | CHUNK | REPLY
    raw: str
    fields: Dict[str, str] = field(default_factory=dict)

    @property
    def ttft_ms(self) -> Optional[int]:
        v = self.fields.get("ttft_ms")
        try:
            return int(v) if v is not None else None
        except ValueError:
            return None

    @property
    def latency_ms(self) -> Optional[int]:
        v = self.fields.get("latency_ms")
        try:
            return int(v) if v is not None else None
        except ValueError:
            return None

    @property
    def text(self) -> str:
        return self.fields.get("text") or self.fields.get("chunk") or ""

    @property
    def reason(self) -> str:
        return self.fields.get("reason") or ""

    @property
    def seq(self) -> Optional[int]:
        v = self.fields.get("seq")
        try:
            return int(v) if v is not None else None
        except ValueError:
            return None


def parse_metrics_line(line: str) -> Optional[ReplyMetricsEvent]:
    if TAG not in line:
        return None
    # ... I DemoReplyMetrics: REPLY session=...
    m = re.search(rf"{TAG}\s*:\s*(SEND|FIRST_TOKEN|CHUNK|REPLY)\s+(.*)$", line)
    if not m:
        return None
    kind = m.group(1)
    rest = m.group(2).strip()
    fields: Dict[str, str] = {}
    for km in _KV.finditer(rest):
        fields[km.group(1)] = km.group(2).strip()
    return ReplyMetricsEvent(kind=kind, raw=line.rstrip(), fields=fields)


def extract_events(log_text: str) -> List[ReplyMetricsEvent]:
    out: List[ReplyMetricsEvent] = []
    for line in log_text.splitlines():
        ev = parse_metrics_line(line)
        if ev:
            out.append(ev)
    return out


def assemble_reply_text(events: List[ReplyMetricsEvent]) -> str:
    """拼接 CHUNK 正文；REPLY 若自带 text 则优先用 REPLY。"""
    reply_text = ""
    chunks: List[str] = []
    seen_seq: set = set()
    for ev in events:
        if ev.kind == "CHUNK":
            seq = ev.seq
            key = (ev.fields.get("session"), ev.fields.get("round"), seq)
            if key in seen_seq:
                continue
            seen_seq.add(key)
            t = (ev.text or "").strip("\n")
            if t:
                chunks.append(t)
        elif ev.kind == "REPLY":
            reply_text = ev.text or ""
    if reply_text.strip():
        return reply_text
    return "".join(chunks)
