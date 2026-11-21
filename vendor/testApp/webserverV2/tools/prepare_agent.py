#!/usr/bin/env python3
"""本机准备助手：心跳拿端口、维持 SSH 桥、仅在桥通时上报设备。

关键：网页能看到的设备，跳板机必须能经桥真正 adb 到（否则会 Connection refused）。
"""
from __future__ import annotations

import json
import os
import socket
import subprocess
import sys
import threading
import time
import traceback
import urllib.request
import uuid
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
if getattr(sys, "frozen", False):
    TOOLS = Path(sys.executable).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

from setup_adb_bridge import (  # noqa: E402
    PrepareError,
    bridge_port,
    bridge_ready,
    ensure_adb,
    find_adb,
    run_prepare,
    start_bridge,
    stop_bridge,
)

JUMP = os.environ.get("ADB_JUMP_HOST", "124.71.69.102")
# 9527 远程 ADB：REMOTE_ADB_SERVER=http://host:9527
# 9526 网盘：NETDISK_SERVER=http://host:9526（默认）
SERVER = (
    os.environ.get("REMOTE_ADB_SERVER")
    or os.environ.get("NETDISK_SERVER")
    or f"http://{JUMP}:9526"
).rstrip("/")
if os.environ.get("PREPARE_API_PREFIX"):
    API_PREFIX = os.environ["PREPARE_API_PREFIX"].rstrip("/")
elif ":9527" in SERVER or SERVER.endswith("/9527"):
    API_PREFIX = "/api/remote-adb/prepare"
else:
    API_PREFIX = "/api/netdisk/prepare"
HOSTNAME = socket.gethostname()
AGENT_ID_FILE = TOOLS / "agent_id.txt"


def _stable_agent_id() -> str:
    env = (os.environ.get("NETDISK_AGENT_ID") or "").strip()
    if env:
        return env
    if AGENT_ID_FILE.is_file():
        s = AGENT_ID_FILE.read_text(encoding="utf-8", errors="replace").strip()
        if s:
            return s
    aid = f"{HOSTNAME}-{uuid.uuid4().hex[:6]}"
    try:
        AGENT_ID_FILE.write_text(aid + "\n", encoding="utf-8")
    except Exception:
        pass
    return aid


AGENT_ID = _stable_agent_id()
POLL_SEC = 1.0
_BRIDGE_PORT: int | None = None
_bridge_lock = threading.Lock()


def _http_json(method: str, path: str, payload: dict | None = None, timeout: float = 12):
    url = SERVER + path
    data = None
    headers = {"Content-Type": "application/json", "User-Agent": "NetdiskPrepareAgent/3"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            return json.loads(raw) if raw else {}
    except Exception as e:
        return {"ok": False, "error": str(e)}


def _local_devices() -> list[dict]:
    adb = find_adb()
    if not adb:
        try:
            adb = ensure_adb(lambda _p, _m: None)
        except Exception:
            return []
    try:
        p = subprocess.run(
            [str(adb), "devices", "-l"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=15,
        )
        out = (p.stdout or "") + (p.stderr or "")
    except Exception:
        return []
    rows = []
    for ln in out.splitlines():
        ln = ln.strip()
        if not ln or ln.startswith("List of"):
            continue
        parts = ln.split()
        if len(parts) < 2 or parts[1] != "device":
            continue
        serial = parts[0]
        model = ""
        for t in parts[2:]:
            if t.startswith("model:"):
                model = t.split(":", 1)[1].replace("_", " ")
        rows.append(
            {
                "serial": serial,
                "state": "device",
                "model": model,
                "label": model or serial,
            }
        )
    return rows


def _hello() -> dict:
    global _BRIDGE_PORT
    r = _http_json(
        "POST",
        f"{API_PREFIX}/hello",
        {
            "agent_id": AGENT_ID,
            "hostname": HOSTNAME,
            "bridge_port": _BRIDGE_PORT,
        },
        timeout=8,
    )
    if r.get("bridge_port"):
        _BRIDGE_PORT = int(r["bridge_port"])
    return r


def _publish_devices() -> None:
    ok = bridge_ready()
    devs = _local_devices() if ok else []
    _http_json(
        "POST",
        f"{API_PREFIX}/devices",
        {
            "agent_id": AGENT_ID,
            "hostname": HOSTNAME,
            "bridge_port": _BRIDGE_PORT or bridge_port(),
            "devices": devs,
            "bridge_ok": ok,
        },
        timeout=8,
    )


def _ensure_bridge_alive() -> bool:
    """Keep SSH reverse tunnel up so 9526 can adb -H 127.0.0.1 -P <port>."""
    global _BRIDGE_PORT
    if bridge_ready():
        return True
    if not _BRIDGE_PORT:
        return False
    if not _bridge_lock.acquire(blocking=False):
        return False
    try:
        if bridge_ready():
            return True
        serials = [d["serial"] for d in _local_devices()]
        if not serials:
            print("[bridge] no local USB device", flush=True)
            return False

        def prog(pct: int, msg: str) -> None:
            if pct in (0, 50, 70, 100) or "FAIL" in msg or "无法" in msg:
                print(f"[bridge {pct}%] {msg}", flush=True)

        print(f"[bridge] connecting jump:{_BRIDGE_PORT} …", flush=True)
        start_bridge(
            serials,
            progress=prog,
            block=False,
            remote_port=int(_BRIDGE_PORT),
        )
        ok = bridge_ready()
        print(f"[bridge] ready={ok}", flush=True)
        return ok
    except Exception as e:
        print(f"[bridge] ensure failed: {e}", flush=True)
        return False
    finally:
        _bridge_lock.release()


def _poll_job() -> dict | None:
    r = _http_json(
        "GET",
        f"{API_PREFIX}/poll?agent_id={AGENT_ID}",
        timeout=8,
    )
    if not r.get("ok"):
        return None
    return r.get("job")


def _report(job_id: str, **fields) -> None:
    body = {
        "job_id": job_id,
        "agent_id": AGENT_ID,
        "bridge_port": _BRIDGE_PORT or bridge_port(),
        **fields,
    }
    _http_json("POST", f"{API_PREFIX}/report", body, timeout=8)


def _run_claimed(job: dict) -> None:
    job_id = str(job.get("id") or "")
    port = job.get("bridge_port") or _BRIDGE_PORT
    print(f"[agent] claimed job {job_id} port={port}", flush=True)

    def progress(pct: int, msg: str) -> None:
        print(f"[{pct}%] {msg}", flush=True)
        _report(job_id, pct=pct, message=msg, done_ok=None)

    try:
        result = run_prepare(
            progress=progress,
            keep_bridge=True,
            remote_port=int(port) if port else None,
        )
        _publish_devices()
        _report(
            job_id,
            pct=100,
            message=f"准备完成（本机 {HOSTNAME} · 桥 :{result.get('port')}）",
            done_ok=True,
            error=None,
            serials=result.get("serials") or [],
            bridge_ready=bridge_ready(),
            bridge_port=result.get("port"),
        )
        print("[agent] prepare ok", flush=True)
    except PrepareError as e:
        _report(job_id, pct=0, message=str(e), done_ok=False, error=str(e))
        print(f"[agent] prepare fail: {e}", flush=True)
    except Exception as e:
        _report(job_id, pct=0, message=str(e), done_ok=False, error=str(e))
        print(traceback.format_exc(), flush=True)


def main() -> None:
    for k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
        os.environ.pop(k, None)
    os.environ.setdefault("NO_PROXY", f"127.0.0.1,localhost,{JUMP}")

    print("Prepare Agent (per-PC)", flush=True)
    print(f"  server:   {SERVER}", flush=True)
    print(f"  api:      {API_PREFIX}", flush=True)
    print(f"  hostname: {HOSTNAME}", flush=True)
    print(f"  agent:    {AGENT_ID}", flush=True)
    print("Keep open. Bridge must stay up for the jump host to control your phone.", flush=True)

    busy = threading.Lock()
    printed_port = None
    tick = 0

    while True:
        try:
            hi = _hello()
            bp = hi.get("bridge_port")
            if bp and bp != printed_port:
                print(f"  bridge_port: {bp}", flush=True)
                printed_port = bp
            # keep tunnel alive (every ~5s attempt if down)
            if tick % 5 == 0:
                _ensure_bridge_alive()
            _publish_devices()
            if not busy.locked():
                job = _poll_job()
                if job and job.get("id"):

                    def runner(j=job):
                        with busy:
                            _run_claimed(j)

                    threading.Thread(target=runner, daemon=True).start()
        except Exception as e:
            print(f"[agent] loop warn: {e}", flush=True)
        tick += 1
        time.sleep(POLL_SEC)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nagent stopped", flush=True)
        stop_bridge()
