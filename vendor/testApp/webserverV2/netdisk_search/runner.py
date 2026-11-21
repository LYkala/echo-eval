#!/usr/bin/env python3
"""Background runner for 网盘搜索过程 over E2E cases."""
from __future__ import annotations

import json
import shutil
import threading
import traceback
from datetime import datetime
from pathlib import Path
from typing import Any

from .adb_util import Adb, collect_search_order, init_back_and_clear, input_query_and_search, restore_ime
from .config import RESULTS
from .dataset import (
    STATUS_DONE,
    STATUS_FAILED,
    STATUS_PARTIAL,
    e2e_path,
    is_case_done,
    iter_case_rows,
    load_filename_to_fid,
    names_to_actual_fid,
    write_actual_results,
)

# collect_search_order statuses that mean the case finished successfully
_DONE_STATUSES = {"looped", "exhausted", "capped"}


class Runner:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._thread: threading.Thread | None = None
        self._stop = threading.Event()
        self._adb: Adb | None = None
        self._ime_prev: str | None = None
        self.state: dict[str, Any] = {
            "running": False,
            "run_id": None,
            "result_xlsx": None,
            "serial": None,
            "progress": 0,
            "total": 0,
            "current_case": None,
            "logs": [],
            "cases": [],
            "error": None,
            "started_at": None,
            "finished_at": None,
        }

    def _log(self, msg: str) -> None:
        ts = datetime.now().strftime("%H:%M:%S")
        line = f"[{ts}] {msg}"
        with self._lock:
            logs = self.state["logs"]
            logs.append(line)
            if len(logs) > 800:
                self.state["logs"] = logs[-800:]

    def status(self) -> dict[str, Any]:
        with self._lock:
            return json.loads(json.dumps(self.state, ensure_ascii=False))

    def _restore_user_ime(self) -> None:
        """Best-effort restore keyboard after stop / run end."""
        adb, prev = self._adb, self._ime_prev
        if adb is None or not prev or prev == "null":
            return
        try:
            restore_ime(adb, prev)
            self._log(f"restored ime -> {prev}")
        except Exception as e:
            self._log(f"ime restore warn: {e}")

    def stop(self) -> None:
        self._stop.set()
        self._log("stop requested")
        adb = self._adb
        if adb is not None:
            adb.halt()

    def start(
        self,
        serial: str,
        case_ids: list[str] | None = None,
        limit: int | None = None,
        max_images: int = 60,
        force: bool = False,
        adb_host: str | None = None,
        adb_port: int | None = None,
        channel_label: str = "",
    ) -> dict[str, Any]:
        with self._lock:
            if self.state["running"]:
                return {"ok": False, "error": "already running"}
            self._stop.clear()
            self.state["running"] = True
            self.state["error"] = None
            self.state["logs"] = []
            self.state["cases"] = []
            self.state["serial"] = serial
            self.state["started_at"] = datetime.now().isoformat(timespec="seconds")
            self.state["finished_at"] = None

        def target():
            try:
                self._run(
                    serial,
                    case_ids,
                    limit,
                    max_images,
                    force,
                    adb_host=adb_host,
                    adb_port=adb_port,
                    channel_label=channel_label,
                )
            except Exception as e:
                self._log(f"FATAL {e}")
                self._log(traceback.format_exc())
                with self._lock:
                    self.state["error"] = str(e)
            finally:
                if self._adb is not None:
                    self._adb.halted = False
                self._restore_user_ime()
                self._adb = None
                with self._lock:
                    self.state["running"] = False
                    self.state["finished_at"] = datetime.now().isoformat(timespec="seconds")

        self._thread = threading.Thread(target=target, daemon=True)
        self._thread.start()
        return {"ok": True}

    def _persist_case(
        self,
        dest: Path,
        excel_row: int,
        actual: str,
        names_joined: str,
        actual_count: int,
        latency_ms: int | None,
        status: str,
        overwrite_done: bool = False,
    ) -> None:
        """Write into run workbook, latest.xlsx, and master E2E workbook."""
        latest = RESULTS / "latest.xlsx"
        targets = [dest, latest, e2e_path()]
        seen: set[str] = set()
        for path in targets:
            key = str(path.resolve())
            if key in seen:
                continue
            seen.add(key)
            write_actual_results(
                path,
                excel_row,
                actual,
                names_joined,
                actual_count=actual_count,
                search_latency_ms=latency_ms,
                status=status,
                overwrite_done=overwrite_done,
            )

    def _run(
        self,
        serial: str,
        case_ids: list[str] | None,
        limit: int | None,
        max_images: int,
        force: bool,
        adb_host: str | None = None,
        adb_port: int | None = None,
        channel_label: str = "",
    ) -> None:
        RESULTS.mkdir(parents=True, exist_ok=True)
        run_id = datetime.now().strftime("run_%Y%m%d_%H%M%S")
        src = e2e_path()
        latest = RESULTS / "latest.xlsx"
        # Prefer latest so prior completions carry over
        base = latest if latest.exists() else src
        dest = RESULTS / f"{run_id}.xlsx"
        shutil.copy2(base, dest)
        shutil.copy2(dest, latest)

        with self._lock:
            self.state["run_id"] = run_id
            self.state["result_xlsx"] = str(dest)

        self._log(f"result workbook -> {dest} (base={base.name})")
        self._log("loading ES fileName→fid map…")
        mapping = load_filename_to_fid()
        self._log(f"ES map size={len(mapping)}")

        # Always skip 完成 unless force=True. Explicit case_ids still respect this.
        skip_done = not force
        all_rows = list(iter_case_rows(dest, case_ids=case_ids, limit=None, skip_done=False))
        done_ids = [str(c.get("用例编号") or "") for c in all_rows if is_case_done(c)]
        if skip_done and done_ids:
            self._log(
                f"skip already-done ({len(done_ids)}): {', '.join(done_ids[:12])}"
                + ("…" if len(done_ids) > 12 else "")
            )
        cases = list(
            iter_case_rows(dest, case_ids=case_ids, limit=limit, skip_done=skip_done)
        )
        if not cases:
            self._log("nothing to run (all selected cases already 完成, or empty)")
            return
        with self._lock:
            self.state["total"] = len(cases)
            self.state["progress"] = 0

        adb = Adb(serial, host=adb_host, port=adb_port)
        self._adb = adb
        ch = channel_label or (
            f"{adb_host}:{adb_port}" if adb_host else "本机ADB"
        )
        # Remember user IME before any AdbIME switch, restore on stop / run end
        try:
            prev = adb.run("shell", "settings", "get", "secure", "default_input_method")
            if prev and "adbkeyboard" in prev.lower():
                # Already stuck on AdbIME — pick another enabled IME if possible
                for line in adb.run("shell", "ime", "list", "-s").splitlines():
                    cand = line.strip()
                    if cand and "adbkeyboard" not in cand.lower():
                        prev = cand
                        break
            self._ime_prev = prev
            self._log(f"user ime={self._ime_prev}")
        except Exception as e:
            self._ime_prev = None
            self._log(f"ime capture warn: {e}")
        self._log(
            f"device={serial} channel={ch} cases={len(cases)} skip_done={skip_done} force={force}"
        )

        for idx, case in enumerate(cases, 1):
            if self._stop.is_set():
                self._log("aborted")
                break
            cid = str(case.get("用例编号") or "")
            query = str(case.get("原始输入") or "").strip()
            excel_row = int(case["_row"])
            with self._lock:
                self.state["progress"] = idx - 1
                self.state["current_case"] = {"id": cid, "query": query, "index": idx}

            self._log(f"==== [{idx}/{len(cases)}] {cid} query={query!r} ====")
            if not force and is_case_done(case):
                self._log(f"skip {cid}: already 完成")
                with self._lock:
                    self.state["progress"] = idx
                continue

            case_result: dict[str, Any] = {
                "id": cid,
                "query": query,
                "row": excel_row,
                "ok": False,
                "order": [],
                "actual_fid": "",
                "missing": [],
                "error": None,
                "status": "",
            }

            if not query:
                case_result["error"] = "empty query"
                case_result["status"] = STATUS_FAILED
                self._log("skip empty query")
                with self._lock:
                    self.state["cases"].append(case_result)
                    self.state["progress"] = idx
                continue

            try:
                search_res = input_query_and_search(
                    adb, query, self._log, stop_flag=self._stop.is_set
                )
                if self._stop.is_set():
                    raise RuntimeError("stopped")
                if not search_res.get("ok"):
                    init_back_and_clear(adb, self._log)
                    if self._stop.is_set():
                        raise RuntimeError("stopped")
                    search_res = input_query_and_search(
                        adb, query, self._log, stop_flag=self._stop.is_set
                    )
                if self._stop.is_set():
                    raise RuntimeError("stopped")
                if not search_res.get("ok"):
                    raise RuntimeError("search did not open SearchTimeLine")
                latency_ms = search_res.get("latency_ms")

                collected = collect_search_order(
                    adb,
                    self._log,
                    stop_flag=self._stop.is_set,
                    max_items=max_images,
                )
                order = collected.get("records") or []
                collect_status = collected.get("status") or "failed"
                case_result["order"] = order
                case_result["collect_status"] = collect_status
                names = [x["name"] for x in order]
                actual, missing = names_to_actual_fid(names, mapping)
                names_joined = ",".join(names)
                case_result["actual_fid"] = actual
                case_result["actual_names"] = names_joined
                case_result["search_latency_ms"] = latency_ms
                case_result["missing"] = missing

                if collect_status == "empty" or (collect_status in _DONE_STATUSES and not names):
                    excel_status = ""
                    case_result["ok"] = False
                    case_result["error"] = None
                elif collect_status in _DONE_STATUSES and names:
                    excel_status = STATUS_DONE
                    case_result["ok"] = True
                elif collect_status == "stopped":
                    excel_status = STATUS_PARTIAL
                    case_result["ok"] = False
                    case_result["error"] = "stopped"
                else:
                    excel_status = STATUS_FAILED if not names else STATUS_PARTIAL
                    case_result["ok"] = False
                    case_result["error"] = case_result["error"] or collect_status

                case_result["status"] = excel_status
                self._persist_case(
                    dest,
                    excel_row,
                    actual,
                    names_joined,
                    len(names),
                    latency_ms,
                    excel_status,
                    overwrite_done=force,
                )
                self._log(
                    f"done {cid}: status={excel_status or '空'} collect={collect_status} "
                    f"images={len(names)} fids={len(actual.split(',')) if actual else 0} "
                    f"missing={len(missing)} latency_ms={latency_ms}"
                )
            except Exception as e:
                stopped = str(e) == "stopped" or self._stop.is_set()
                case_result["error"] = "stopped" if stopped else str(e)
                case_result["status"] = STATUS_PARTIAL if stopped else STATUS_FAILED
                self._log("stopped" if stopped else f"case error: {e}")
                if not stopped:
                    self._log(traceback.format_exc()[-800:])
                try:
                    self._persist_case(
                        dest,
                        excel_row,
                        "",
                        "",
                        0,
                        None,
                        case_result["status"],
                        overwrite_done=force,
                    )
                except Exception as we:
                    self._log(f"persist fail status warn: {we}")
            finally:
                # 无论正常结束还是停止，都尽量回到搜索页；停止时先放开 halt 才能发返回
                if self._stop.is_set():
                    adb.halted = False
                    self._log("stopped, returning to search page…")
                try:
                    init_back_and_clear(adb, self._log)
                except Exception as e:
                    self._log(f"init warn: {e}")
                if self._stop.is_set():
                    adb.halt()

            with self._lock:
                self.state["cases"].append(case_result)
                self.state["progress"] = idx

            summary = RESULTS / f"{run_id}.json"
            with self._lock:
                summary.write_text(
                    json.dumps(self.state, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )

        self._log("all done")


RUNNER = Runner()
