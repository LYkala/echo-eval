#!/usr/bin/env python3
"""Multi-user ADB agent registry: each PC gets its own bridge port + device list."""
from __future__ import annotations

import threading
import time

_LOCK = threading.Lock()
_AGENT_TTL = 45.0
_PORT_BASE = 15036
_PORT_END = 15060  # inclusive

# agent_id -> {hostname, bridge_port, last_seen, devices: [dict], label}
_AGENTS: dict[str, dict] = {}


def _prune_locked(now: float | None = None) -> None:
    now = now if now is not None else time.time()
    dead = [aid for aid, a in _AGENTS.items() if now - float(a.get("last_seen") or 0) > _AGENT_TTL]
    for aid in dead:
        _AGENTS.pop(aid, None)


def _used_ports_locked() -> set[int]:
    return {int(a["bridge_port"]) for a in _AGENTS.values() if a.get("bridge_port")}


def _alloc_port_locked(prefer: int | None = None) -> int:
    used = _used_ports_locked()
    if prefer and _PORT_BASE <= prefer <= _PORT_END and prefer not in used:
        return prefer
    for p in range(_PORT_BASE, _PORT_END + 1):
        if p not in used:
            return p
    raise RuntimeError(f"无空闲桥接端口 {_PORT_BASE}-{_PORT_END}")


def hello(agent_id: str, hostname: str = "", prefer_port: int | None = None) -> dict:
    agent_id = (agent_id or "").strip() or "default"
    hostname = (hostname or "").strip() or agent_id
    now = time.time()
    with _LOCK:
        _prune_locked(now)
        prev = _AGENTS.get(agent_id)
        if prev and prev.get("bridge_port"):
            port = int(prev["bridge_port"])
        else:
            port = _alloc_port_locked(prefer_port)
        _AGENTS[agent_id] = {
            "agent_id": agent_id,
            "hostname": hostname,
            "bridge_port": port,
            "last_seen": now,
            "devices": list(prev.get("devices") or []) if prev else [],
            "label": hostname,
        }
        return {
            "ok": True,
            "agent_id": agent_id,
            "hostname": hostname,
            "bridge_port": port,
            "agents_online": len(_AGENTS),
        }


def publish_devices(
    agent_id: str,
    devices: list[dict],
    bridge_port: int | None = None,
    hostname: str = "",
    bridge_ok: bool = True,
) -> dict:
    agent_id = (agent_id or "").strip() or "default"
    now = time.time()
    with _LOCK:
        _prune_locked(now)
        row = _AGENTS.get(agent_id)
        if not row:
            port = _alloc_port_locked(bridge_port)
            row = {
                "agent_id": agent_id,
                "hostname": hostname or agent_id,
                "bridge_port": port,
                "last_seen": now,
                "devices": [],
                "label": hostname or agent_id,
                "bridge_ok": False,
            }
            _AGENTS[agent_id] = row
        if hostname:
            row["hostname"] = hostname
            row["label"] = hostname
        port = int(row["bridge_port"])
        row["bridge_ok"] = bool(bridge_ok)
        cleaned = []
        # 桥不通时清空设备，避免网页「看得到却控不了」
        if bridge_ok:
            for d in devices or []:
                if not isinstance(d, dict):
                    continue
                serial = str(d.get("serial") or "").strip()
                if not serial:
                    continue
                item = dict(d)
                item["serial"] = serial
                item["state"] = item.get("state") or "device"
                item["adb_host"] = "127.0.0.1"
                item["adb_port"] = port
                item["channel"] = f"127.0.0.1:{port}"
                host_label = row.get("hostname") or agent_id
                item["channel_label"] = f"{host_label} · 桥:{port}"
                item["agent_id"] = agent_id
                item["hostname"] = host_label
                item["bridge_ok"] = True
                label = item.get("label") or item.get("marketname") or item.get("model") or serial
                item["label"] = f"{label}"
                cleaned.append(item)
        row["devices"] = cleaned
        row["last_seen"] = now
        return {
            "ok": True,
            "count": len(cleaned),
            "bridge_port": port,
            "bridge_ok": bool(bridge_ok),
        }


def online_agents() -> list[dict]:
    now = time.time()
    with _LOCK:
        _prune_locked(now)
        out = []
        for a in _AGENTS.values():
            out.append(
                {
                    "agent_id": a["agent_id"],
                    "hostname": a.get("hostname") or a["agent_id"],
                    "bridge_port": a.get("bridge_port"),
                    "device_count": len(a.get("devices") or []),
                    "devices": [
                        {
                            "serial": d.get("serial"),
                            "label": d.get("label"),
                            "state": d.get("state"),
                        }
                        for d in (a.get("devices") or [])
                    ],
                    "last_seen": a.get("last_seen"),
                }
            )
        out.sort(key=lambda x: str(x.get("hostname") or ""))
        return out


def registered_devices() -> list[dict]:
    """Devices published by online agents (each PC's own phones)."""
    now = time.time()
    with _LOCK:
        _prune_locked(now)
        rows = []
        for a in _AGENTS.values():
            for d in a.get("devices") or []:
                rows.append(dict(d))
        return rows


def agent_online(agent_id: str) -> bool:
    agent_id = (agent_id or "").strip()
    now = time.time()
    with _LOCK:
        _prune_locked(now)
        a = _AGENTS.get(agent_id)
        return bool(a)


def touch(agent_id: str) -> None:
    agent_id = (agent_id or "").strip()
    if not agent_id:
        return
    with _LOCK:
        a = _AGENTS.get(agent_id)
        if a:
            a["last_seen"] = time.time()
