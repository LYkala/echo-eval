#!/usr/bin/env python3
"""Flask blueprint: /pan-search page + /api/netdisk/* APIs."""
from __future__ import annotations

import threading
import time
import uuid
from pathlib import Path

from flask import Blueprint, Flask, Response, jsonify, render_template, request, send_file

from .adb_util import (
    capture_screenshot,
    list_devices,
    probe_device_reachable,
    resolve_device_endpoint,
)
from .config import RESULTS, ROOT
from .dataset import e2e_path, es_path, read_cases, set_case_status
from .runner import RUNNER

bp = Blueprint(
    "netdisk_search",
    __name__,
    template_folder=str(Path(__file__).resolve().parent.parent / "templates"),
)


@bp.get("/pan-search")
def pan_search_page():
    return render_template("pan_search.html", page="pan-search")


@bp.get("/pan-search/app")
def pan_search_app():
    """Standalone console UI (iframe target)."""
    return render_template("pan_search_frame.html")


@bp.get("/api/netdisk/health")
def health():
    return jsonify({"ok": True, "root": str(ROOT)})


@bp.get("/api/netdisk/screenshot")
def screenshot():
    serial = (request.args.get("serial") or "").strip()
    if not serial:
        return jsonify({"ok": False, "error": "请先选择手机设备"}), 400
    ep = resolve_device_endpoint(serial)
    if not ep:
        return jsonify(
            {"ok": False, "error": f"设备不在线: {serial}（检查 USB 与本机助手桥接）"}
        ), 400
    try:
        png = capture_screenshot(ep)
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 502
    return Response(
        png,
        mimetype="image/png",
        headers={"Cache-Control": "no-store"},
    )


@bp.get("/api/netdisk/devices")
def devices():
    try:
        rows = list_devices()
        return jsonify({"ok": True, "devices": rows})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e), "devices": []}), 500


@bp.get("/api/netdisk/dataset")
def dataset():
    try:
        offset = int(request.args.get("offset", 0))
        limit = int(request.args.get("limit", 30))
        q = (request.args.get("q") or "").strip()
        sort_by = (request.args.get("sort_by") or "").strip()
        sort_dir = (request.args.get("sort_dir") or "asc").strip()
        count_min = request.args.get("count_min")
        count_max = request.args.get("count_max")
        try:
            count_min_i = int(count_min) if count_min not in (None, "") else None
        except ValueError:
            count_min_i = None
        try:
            count_max_i = int(count_max) if count_max not in (None, "") else None
        except ValueError:
            count_max_i = None
        status = (request.args.get("status") or "").strip()
        ids_only = (request.args.get("ids") or "").strip() in ("1", "true", "yes")
        which = (request.args.get("which") or "cases").strip()
        if which == "results":
            path = RESULTS / "latest.xlsx"
            if not path.exists():
                runs = sorted(RESULTS.glob("run_*.xlsx"), reverse=True)
                path = runs[0] if runs else e2e_path()
        else:
            latest = RESULTS / "latest.xlsx"
            path = latest if latest.exists() else e2e_path()
        data = read_cases(
            path,
            offset=offset,
            limit=limit,
            q=q,
            sort_by=sort_by,
            sort_dir=sort_dir,
            count_min=count_min_i,
            count_max=count_max_i,
            status=status,
            ids_only=ids_only,
        )
        data["ok"] = True
        data["which"] = which
        data["es"] = str(es_path())
        return jsonify(data)
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@bp.get("/api/netdisk/run/status")
def run_status():
    return jsonify({"ok": True, **RUNNER.status()})


@bp.post("/api/netdisk/case/status")
def case_status_edit():
    body = request.get_json(force=True, silent=True) or {}
    case_id = str(body.get("case_id") or "").strip()
    status = str(body.get("status") or "").strip()
    if not case_id:
        return jsonify({"ok": False, "error": "case_id required"}), 400
    latest = RESULTS / "latest.xlsx"
    targets = []
    if latest.exists():
        targets.append(latest)
    targets.append(e2e_path())
    seen = set()
    results = []
    for path in targets:
        key = str(path.resolve())
        if key in seen:
            continue
        seen.add(key)
        r = set_case_status(path, case_id, status)
        results.append(r)
        if not r.get("ok"):
            return jsonify({"ok": False, "error": r.get("error"), "results": results}), 400
    return jsonify({"ok": True, "case_id": case_id, "status": status, "results": results})


@bp.post("/api/netdisk/run/start")
def run_start():
    body = request.get_json(force=True, silent=True) or {}
    serial = (body.get("serial") or "").strip()
    if not serial:
        return jsonify({"ok": False, "error": "serial required"}), 400
    case_ids = body.get("case_ids") or None
    if case_ids is not None and not isinstance(case_ids, list):
        return jsonify({"ok": False, "error": "case_ids must be list"}), 400
    limit = body.get("limit")
    if limit is not None:
        try:
            limit = int(limit)
        except Exception:
            return jsonify({"ok": False, "error": "bad limit"}), 400
    max_images = int(body.get("max_images") or 60)
    force = bool(body.get("force") or False)

    ep = resolve_device_endpoint(serial)
    if not ep:
        return jsonify(
            {
                "ok": False,
                "error": f"设备不在 ADB 列表: {serial}（检查 USB 调试或本机助手桥接）",
            }
        ), 400

    ok_reach, reach_msg = probe_device_reachable(ep)
    if not ok_reach:
        return jsonify({"ok": False, "error": reach_msg}), 400

    r = RUNNER.start(
        serial=serial,
        case_ids=case_ids,
        limit=limit,
        max_images=max_images,
        force=force,
        adb_host=ep.get("adb_host"),
        adb_port=ep.get("adb_port"),
        channel_label=ep.get("channel_label") or "",
    )
    code = 200 if r.get("ok") else 409
    return jsonify(r), code


@bp.post("/api/netdisk/run/stop")
def run_stop():
    RUNNER.stop()
    return jsonify({"ok": True})


@bp.get("/api/netdisk/results/download")
def results_download():
    path = RESULTS / "latest.xlsx"
    if not path.exists():
        runs = sorted(RESULTS.glob("run_*.xlsx"), reverse=True)
        if not runs:
            return jsonify({"ok": False, "error": "no results"}), 404
        path = runs[0]
    return send_file(
        path,
        as_attachment=True,
        download_name=path.name,
        mimetype="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )


# ---- 一键准备：经 9526 中转（按助手/电脑隔离）----

from . import agent_registry as _areg  # noqa: E402

_PREPARE_LOCK = threading.Lock()
_PREPARE_JOBS: dict[str, dict] = {}  # agent_id -> job


def _job_public(job: dict | None, agent_id: str = "") -> dict:
    agents = _areg.online_agents()
    online_ids = {a["agent_id"] for a in agents}
    agent_online = (agent_id in online_ids) if agent_id else bool(agents)
    if not job:
        return {
            "ok": True,
            "job_id": None,
            "status": "idle",
            "pct": 0,
            "message": "空闲",
            "done_ok": None,
            "error": None,
            "serials": [],
            "agent_id": agent_id or None,
            "agent_online": agent_online,
            "agents": len(agents),
        }
    return {
        "ok": True,
        "job_id": job.get("id"),
        "status": job.get("status"),
        "pct": int(job.get("pct") or 0),
        "message": job.get("message") or "",
        "done_ok": job.get("done_ok"),
        "error": job.get("error"),
        "serials": list(job.get("serials") or []),
        "agent_id": job.get("agent_id") or agent_id or None,
        "agent_online": agent_online,
        "agents": len(agents),
        "bridge_port": job.get("bridge_port"),
        "updated_at": job.get("updated_at"),
    }


@bp.get("/api/netdisk/prepare/agents")
def prepare_agents():
    """在线本机助手列表（每人选自己的电脑）。"""
    rows = _areg.online_agents()
    return jsonify({"ok": True, "agents": rows})


@bp.post("/api/netdisk/prepare/hello")
def prepare_hello():
    """本机助手心跳；分配专属桥接端口。"""
    body = request.get_json(force=True, silent=True) or {}
    agent_id = str(body.get("agent_id") or "default").strip() or "default"
    hostname = str(body.get("hostname") or "").strip()
    prefer = body.get("bridge_port")
    try:
        prefer_i = int(prefer) if prefer not in (None, "") else None
    except Exception:
        prefer_i = None
    try:
        info = _areg.hello(agent_id, hostname=hostname, prefer_port=prefer_i)
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 503
    return jsonify(info)


@bp.post("/api/netdisk/prepare/devices")
def prepare_devices_publish():
    """助手上报本机 USB 设备（只属于该助手）。"""
    body = request.get_json(force=True, silent=True) or {}
    agent_id = str(body.get("agent_id") or "default").strip() or "default"
    hostname = str(body.get("hostname") or "").strip()
    devices = body.get("devices") or []
    try:
        port = int(body.get("bridge_port") or 0) or None
    except Exception:
        port = None
    bridge_ok = bool(body.get("bridge_ok", True))
    if not isinstance(devices, list):
        return jsonify({"ok": False, "error": "devices must be list"}), 400
    r = _areg.publish_devices(
        agent_id,
        devices,
        bridge_port=port,
        hostname=hostname,
        bridge_ok=bridge_ok,
    )
    return jsonify(r)


@bp.post("/api/netdisk/prepare/request")
def prepare_request():
    """网页发起一键准备（必须指定 agent_id，避免领到别人的助手）。"""
    body = request.get_json(force=True, silent=True) or {}
    agent_id = str(body.get("agent_id") or "").strip()
    now = time.time()
    agents = _areg.online_agents()
    if not agent_id:
        if len(agents) == 1:
            agent_id = agents[0]["agent_id"]
        else:
            return jsonify(
                {
                    "ok": False,
                    "error": "请选择本机助手（电脑）后再一键准备，避免连到别人的手机",
                    "agents": agents,
                }
            ), 400
    if not _areg.agent_online(agent_id):
        return jsonify(
            {
                "ok": False,
                "error": "所选助手不在线，请先在本机运行 StartAgent.bat",
                "agents": agents,
            }
        ), 400

    job = {
        "id": uuid.uuid4().hex[:12],
        "agent_id": agent_id,
        "status": "queued",
        "pct": 0,
        "message": "已排队，等待该电脑助手领取…",
        "done_ok": None,
        "error": None,
        "serials": [],
        "created_at": now,
        "updated_at": now,
        "bridge_port": next(
            (a.get("bridge_port") for a in agents if a["agent_id"] == agent_id), None
        ),
    }
    with _PREPARE_LOCK:
        _PREPARE_JOBS[agent_id] = job
        pub = _job_public(job, agent_id)
    pub["started"] = True
    pub["ok"] = True
    return jsonify(pub)


@bp.get("/api/netdisk/prepare/status")
def prepare_status():
    """网页轮询进度。"""
    job_id = (request.args.get("job_id") or "").strip()
    agent_id = (request.args.get("agent_id") or "").strip()
    with _PREPARE_LOCK:
        job = None
        if job_id:
            for j in _PREPARE_JOBS.values():
                if j.get("id") == job_id:
                    job = j
                    break
        elif agent_id:
            job = _PREPARE_JOBS.get(agent_id)
        return jsonify(_job_public(job, agent_id or (job or {}).get("agent_id") or ""))


@bp.get("/api/netdisk/prepare/poll")
def prepare_poll():
    """本机助手领取「指定给自己」的排队任务。"""
    agent_id = (request.args.get("agent_id") or "default").strip() or "default"
    _areg.touch(agent_id)
    with _PREPARE_LOCK:
        job = _PREPARE_JOBS.get(agent_id)
        if not job or job.get("status") != "queued":
            return jsonify({"ok": True, "job": None})
        if job.get("agent_id") and job.get("agent_id") != agent_id:
            return jsonify({"ok": True, "job": None})
        job["status"] = "running"
        job["message"] = "本机助手已领取，开始准备…"
        job["pct"] = 1
        job["updated_at"] = time.time()
        # 带上专属端口
        ags = _areg.online_agents()
        port = next((a.get("bridge_port") for a in ags if a["agent_id"] == agent_id), None)
        job["bridge_port"] = port
        return jsonify(
            {
                "ok": True,
                "job": {
                    "id": job["id"],
                    "status": job["status"],
                    "bridge_port": port,
                },
            }
        )


@bp.post("/api/netdisk/prepare/report")
def prepare_report():
    """本机助手上报进度。"""
    body = request.get_json(force=True, silent=True) or {}
    job_id = str(body.get("job_id") or "").strip()
    agent_id = str(body.get("agent_id") or "default").strip() or "default"
    _areg.touch(agent_id)
    with _PREPARE_LOCK:
        job = _PREPARE_JOBS.get(agent_id)
        if job_id and (not job or job.get("id") != job_id):
            # 容错：按 job_id 找
            job = next((j for j in _PREPARE_JOBS.values() if j.get("id") == job_id), None)
        if not job:
            return jsonify({"ok": False, "error": "no matching job"}), 404
        if "pct" in body:
            try:
                job["pct"] = max(0, min(100, int(body.get("pct") or 0)))
            except Exception:
                pass
        if body.get("message") is not None:
            job["message"] = str(body.get("message") or "")
        if "done_ok" in body:
            job["done_ok"] = body.get("done_ok")
            if body.get("done_ok") is True:
                job["status"] = "done"
                job["pct"] = 100
            elif body.get("done_ok") is False:
                job["status"] = "error"
        if body.get("error") is not None:
            job["error"] = str(body.get("error") or "")
        if body.get("serials") is not None:
            job["serials"] = list(body.get("serials") or [])
        if body.get("bridge_port") is not None:
            try:
                job["bridge_port"] = int(body.get("bridge_port"))
            except Exception:
                pass
        job["updated_at"] = time.time()
        if job.get("status") == "queued":
            job["status"] = "running"
        return jsonify(_job_public(job, agent_id))


def register_netdisk(app: Flask) -> None:
    RESULTS.mkdir(parents=True, exist_ok=True)
    app.register_blueprint(bp)
