#!/usr/bin/env python3
"""Flask blueprint: /mcloud-search page + /api/mcloud/* APIs.

与 netdisk_search（网盘搜索）完全独立：数据目录、结果目录、运行状态都是本模块自己的。
「一键准备」端点薄封装转发到 netdisk 的 prepare 视图，复用同一个助手 job store
（本机助手 StartAgent.bat 硬编码轮询 /api/netdisk/prepare/*）。
"""
from __future__ import annotations

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
    "mcloud_search",
    __name__,
    template_folder=str(Path(__file__).resolve().parent.parent / "templates"),
)


@bp.get("/mcloud-search")
def mcloud_search_page():
    return render_template("mcloud_search.html", page="mcloud-search")


@bp.get("/mcloud-search/app")
def mcloud_search_app():
    """Standalone console UI (iframe target)."""
    return render_template("mcloud_search_frame.html")


@bp.get("/api/mcloud/health")
def health():
    return jsonify({"ok": True, "root": str(ROOT)})


@bp.get("/api/mcloud/screenshot")
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


@bp.get("/api/mcloud/devices")
def devices():
    try:
        rows = list_devices()
        return jsonify({"ok": True, "devices": rows})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e), "devices": []}), 500


@bp.get("/api/mcloud/dataset")
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


@bp.get("/api/mcloud/run/status")
def run_status():
    return jsonify({"ok": True, **RUNNER.status()})


@bp.post("/api/mcloud/case/status")
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


@bp.post("/api/mcloud/run/start")
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


@bp.post("/api/mcloud/run/stop")
def run_stop():
    RUNNER.stop()
    return jsonify({"ok": True})


@bp.get("/api/mcloud/results/download")
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


# ---- 一键准备：转发 netdisk 的 prepare 视图（共享助手 job store）----

from netdisk_search.blueprint import (  # noqa: E402
    prepare_agents as _nd_prepare_agents,
    prepare_devices_publish as _nd_prepare_devices_publish,
    prepare_hello as _nd_prepare_hello,
    prepare_poll as _nd_prepare_poll,
    prepare_report as _nd_prepare_report,
    prepare_request as _nd_prepare_request,
    prepare_status as _nd_prepare_status,
)


@bp.get("/api/mcloud/prepare/agents")
def prepare_agents():
    return _nd_prepare_agents()


@bp.post("/api/mcloud/prepare/hello")
def prepare_hello():
    return _nd_prepare_hello()


@bp.post("/api/mcloud/prepare/devices")
def prepare_devices_publish():
    return _nd_prepare_devices_publish()


@bp.post("/api/mcloud/prepare/request")
def prepare_request():
    return _nd_prepare_request()


@bp.get("/api/mcloud/prepare/status")
def prepare_status():
    return _nd_prepare_status()


@bp.get("/api/mcloud/prepare/poll")
def prepare_poll():
    # 本机助手实际轮询的是 /api/netdisk/prepare/poll；这里保留一条等价入口
    return _nd_prepare_poll()


@bp.post("/api/mcloud/prepare/report")
def prepare_report():
    return _nd_prepare_report()


def register_mcloud(app: Flask) -> None:
    RESULTS.mkdir(parents=True, exist_ok=True)
    app.register_blueprint(bp)
