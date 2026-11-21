"""Shared Excel → Device App runner (CLI + Web)."""
from __future__ import annotations

import json
import re
import shutil
import threading
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

from device_app import AdbError, DeviceApp
from excel_cases import GuiCase, ChatTurn, parse_excel, resolve_dataset_xlsx
from excel_convert import (
    EXCEL_DEFAULT_CHAT_WAIT_MS,
    EXCEL_DEFAULT_TIMEOUT_MS,
    convert_xlsx_to_testset,
    load_cases_from_runtime_json,
)
from json_step_runner import load_testcase_json, run_testcase_steps
from paths import datasets_dir, find_adb, load_config, runs_dir

LogFn = Callable[[str], None]


def _safe_name(s: str) -> str:
    return re.sub(r"[^\w\-.]+", "_", s)[:80]


def list_datasets(cfg: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
    cfg = cfg or load_config()
    root = datasets_dir(cfg)
    items: List[Dict[str, Any]] = []
    if not root.is_dir():
        return items
    for child in sorted(root.iterdir()):
        if not child.is_dir():
            continue
        xlsx = None
        preferred = child / "dataset.xlsx"
        if preferred.is_file():
            xlsx = preferred
        else:
            found = sorted(child.glob("*.xlsx")) + sorted(child.glob("*.xlsm"))
            if found:
                xlsx = found[0]
        if not xlsx:
            continue
        try:
            cases = parse_excel(
                xlsx,
                default_package=cfg.get("package") or "com.example.devicesdkdemo",
                default_wait_sec=float(cfg.get("default_wait_sec") or 120),
            )
            n = len(cases)
        except Exception:
            n = -1
        items.append(
            {
                "name": child.name,
                "path": str(xlsx),
                "case_count": n,
                "size": xlsx.stat().st_size,
                "mtime": xlsx.stat().st_mtime,
            }
        )
    return items


def load_cases(
    dataset: str = "",
    xlsx: str = "",
    cfg: Optional[Dict[str, Any]] = None,
    *,
    runtime_dir: Optional[Path] = None,
    timeout_ms: Optional[int] = None,
    wait_ms: Optional[int] = None,
    package_name: Optional[str] = None,
) -> tuple[Path, List[GuiCase]]:
    """Load cases. If runtime_dir given (or convertible), use webserver Excel→JSON path."""
    cfg = cfg or load_config()
    package = package_name or cfg.get("package") or "com.example.devicesdkdemo"
    if xlsx:
        path = Path(xlsx).resolve()
    else:
        name = dataset or cfg.get("dataset") or ""
        if not name:
            raise ValueError("未指定数据集")
        path = resolve_dataset_xlsx(datasets_dir(cfg), name)

    to_ms = int(timeout_ms if timeout_ms is not None else (cfg.get("excel_timeout_ms") or EXCEL_DEFAULT_TIMEOUT_MS))
    w_ms = int(wait_ms if wait_ms is not None else (cfg.get("excel_wait_ms") or EXCEL_DEFAULT_CHAT_WAIT_MS))

    if runtime_dir is not None:
        runtime_dir = Path(runtime_dir)
        runtime_dir.mkdir(parents=True, exist_ok=True)
        result = convert_xlsx_to_testset(
            path,
            runtime_dir,
            package_name=package,
            timeout_ms=to_ms,
            wait_ms=w_ms,
        )
        if not result.get("success"):
            raise ValueError(result.get("message") or "Excel→JSON 失败")
        # 按 Excel 行序加载，禁止按文件名排序（否则 AMAP 会排到 YOUKU 前）
        order = result.get("created") or []
        (runtime_dir / "_order.json").write_text(
            json.dumps(order, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        raw = load_cases_from_runtime_json(runtime_dir, order=order)
        cases = [
            GuiCase(
                case_id=c["case_id"],
                name=c["name"],
                description=c.get("description") or "",
                case_type=c.get("case_type") or "GUI",
                package=c.get("package") or package,
                timeout_sec=float(c.get("timeout_sec") or to_ms / 1000.0),
                turns=[
                    ChatTurn(text=t["text"], wait_sec=float(t["wait_sec"]))
                    for t in (c.get("turns") or [])
                ],
                json_path=c.get("json_path") or "",
            )
            for c in raw
        ]
        return path, cases

    # fallback: legacy direct Excel parse
    default_wait = w_ms / 1000.0
    cases = parse_excel(path, default_package=package, default_wait_sec=default_wait)
    return path, cases


def make_device(cfg: Optional[Dict[str, Any]] = None, serial: str = "") -> DeviceApp:
    cfg = cfg or load_config()
    package = cfg.get("package") or "com.example.devicesdkdemo"
    return DeviceApp(
        adb=find_adb(cfg),
        serial=serial or (cfg.get("serial") or ""),
        package=package,
        main_activity=cfg.get("main_activity") or f"{package}/.ConfigActivity",
        qa_activity=cfg.get("qa_activity") or f"{package}/.QAActivity",
        ui_ids=cfg.get("ui_ids") or {},
        screenshot_max_width=int(cfg.get("screenshot_max_width") or 1080),
        screenshot_jpeg_quality=int(cfg.get("screenshot_jpeg_quality") or 80),
        adb_host=str(cfg.get("adb_host") or ""),
        adb_port=int(cfg.get("adb_port") or 0),
    )


def wait_with_screenshots(
    device: DeviceApp,
    case_dir: Path,
    turn_idx: int,
    wait_sec: float,
    interval: float,
    log: LogFn,
    stop_flag: Optional[threading.Event] = None,
    on_shot: Optional[Callable[[str], None]] = None,
    wait_reply: bool = True,
    poll_sec: float = 0.4,
) -> tuple:
    """等待期间截图；若 wait_reply 则高频轮询 DemoReplyMetrics REPLY（与 webserver 事件驱动一致）。"""
    from reply_metrics import assemble_reply_text, extract_events

    shots: List[str] = []
    metrics: Dict[str, Any] = {}
    deadline = time.time() + max(0.0, wait_sec)
    n = 0
    reply_ev = None
    next_shot_at = time.time()
    poll_sec = max(0.2, float(poll_sec))
    interval = max(poll_sec, float(interval))

    def _cap(suffix: str = "") -> None:
        nonlocal n
        n += 1
        # 实际可能落盘为 .jpg（压缩后）；先按 png 请求，screencap 返回最终路径
        name = f"turn{turn_idx:02d}_shot{n:03d}{suffix}.png"
        final = device.screencap(case_dir / name)
        shots.append(final.name)
        if on_shot:
            on_shot(str(final))

    _cap()
    next_shot_at = time.time() + interval
    while time.time() < deadline:
        if stop_flag and stop_flag.is_set():
            log("  [stop] 用户停止等待")
            break
        if wait_reply:
            try:
                events = extract_events(device.logcat_metrics(timeout=8))
                for ev in events:
                    if ev.kind == "FIRST_TOKEN" and "ttft_ms" not in metrics:
                        metrics["ttft_ms"] = ev.ttft_ms
                        metrics["first_token_kind"] = ev.fields.get("kind")
                        log(f"      FIRST_TOKEN ttft_ms={ev.ttft_ms}")
                    if ev.kind == "REPLY":
                        reply_ev = ev
                        metrics["ttft_ms"] = ev.ttft_ms
                        metrics["latency_ms"] = ev.latency_ms
                        metrics["reason"] = ev.reason
                        # REPLY 不再带全文：用 CHUNK 拼接
                        metrics["reply_text"] = assemble_reply_text(events)
                        preview = (metrics["reply_text"] or "")[:120]
                        log(
                            f"      REPLY ttft_ms={ev.ttft_ms} "
                            f"latency_ms={ev.latency_ms} reason={ev.reason} "
                            f"text={preview!r}"
                        )
                        break
            except Exception as e:
                log(f"  [warn] metrics: {e}")
            if reply_ev is not None:
                break
        now = time.time()
        if now >= next_shot_at:
            try:
                _cap()
            except AdbError as e:
                log(f"  [warn] screencap: {e}")
            next_shot_at = now + interval
        remain = deadline - time.time()
        if remain <= 0:
            break
        time.sleep(min(poll_sec, remain))
    try:
        _cap("_end")
    except AdbError:
        pass
    if wait_reply and reply_ev is None:
        log("  [warn] 等待超时，未收到 DemoReplyMetrics REPLY")
        # 超时也尽量保留已收到的 CHUNK，避免页面全空
        try:
            metrics["reply_text"] = assemble_reply_text(
                extract_events(device.logcat_metrics(timeout=8))
            )
        except Exception:
            pass
    return shots, metrics


def run_case(
    device: DeviceApp,
    case: GuiCase,
    out_root: Path,
    screenshot_interval: float,
    log: LogFn,
    stop_flag: Optional[threading.Event] = None,
    on_shot: Optional[Callable[[str], None]] = None,
    skip_pre_steps: bool = False,
) -> Dict[str, Any]:
    case_dir = out_root / _safe_name(case.case_id)
    # 重跑/重启：先清空该用例旧截图、result、evaluation，避免遗留
    if case_dir.is_dir():
        shutil.rmtree(case_dir, ignore_errors=True)
    case_dir.mkdir(parents=True, exist_ok=True)
    result: Dict[str, Any] = {
        "case_id": case.case_id,
        "name": case.name,
        "ok": True,
        "error": "",
        "turns": [],
        "started_at": datetime.now().isoformat(timespec="seconds"),
    }
    log(f"=== {case.case_id} | {case.name} ===")
    try:
        before = device.screencap(case_dir / "00_before.png")
        if on_shot:
            on_shot(str(before))
        # webserver 同款：有 Excel JSON 就按 steps 控端（含 launch/login/QA/对话）
        if case.json_path and Path(case.json_path).is_file():
            tc = load_testcase_json(Path(case.json_path))
            if skip_pre_steps:
                log(f"  按 JSON 执行(跳过 login/QA 前置，已在聊天页): {case.json_path}")
            else:
                log(f"  按 JSON 执行: {case.json_path}")
            out = run_testcase_steps(
                device,
                tc,
                case_dir=case_dir,
                screenshot_interval=screenshot_interval,
                log=log,
                wait_with_screenshots=wait_with_screenshots,
                stop_flag=stop_flag,
                on_shot=on_shot,
                skip_pre_steps=skip_pre_steps,
            )
            result["turns"] = out.get("turns") or []
            result["driven_by"] = "excel_json"
            result["skip_pre_steps"] = skip_pre_steps
        else:
            for i, turn in enumerate(case.turns, 1):
                if stop_flag and stop_flag.is_set():
                    raise AdbError("用户停止")
                log(f"  [{i}/{len(case.turns)}] send: {turn.text[:80]}")
                try:
                    device.logcat_clear()
                except Exception:
                    pass
                device.send_chat(turn.text)
                wait_sec = float(turn.wait_sec)
                if case.timeout_sec > 0:
                    wait_sec = min(wait_sec, case.timeout_sec)
                log(
                    f"      wait up to {wait_sec:.0f}s for REPLY, "
                    f"shot every {screenshot_interval:.0f}s"
                )
                shots, metrics = wait_with_screenshots(
                    device,
                    case_dir,
                    i,
                    wait_sec,
                    screenshot_interval,
                    log,
                    stop_flag=stop_flag,
                    on_shot=on_shot,
                    wait_reply=True,
                )
                result["turns"].append(
                    {
                        "index": i,
                        "text": turn.text,
                        "wait_sec": wait_sec,
                        "screenshots": shots,
                        "ttft_ms": metrics.get("ttft_ms"),
                        "latency_ms": metrics.get("latency_ms"),
                        "reply_reason": metrics.get("reason"),
                        "reply_text": metrics.get("reply_text"),
                        "metrics": metrics,
                    }
                )
        after = device.screencap(case_dir / "zz_after.png")
        if on_shot:
            on_shot(str(after))
    except Exception as e:
        result["ok"] = False
        result["error"] = str(e)
        log(f"  [FAIL] {e}")
        try:
            errp = device.screencap(case_dir / "zz_error.png")
            if on_shot:
                on_shot(str(errp))
        except Exception:
            pass
    result["finished_at"] = datetime.now().isoformat(timespec="seconds")
    (case_dir / "result.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return result


def execute_run(
    *,
    dataset: str = "",
    xlsx: str = "",
    serial: str = "",
    max_cases: int = 0,
    wait_cap_sec: float = 0,
    skip_prepare: bool = False,
    prepare_login: bool = True,
    case_ids: Optional[List[str]] = None,
    cfg: Optional[Dict[str, Any]] = None,
    log: Optional[LogFn] = None,
    stop_flag: Optional[threading.Event] = None,
    on_shot: Optional[Callable[[str], None]] = None,
    on_case_start: Optional[Callable[[GuiCase], None]] = None,
    on_case_status: Optional[Callable[[str, str], None]] = None,
    on_case_done: Optional[Callable[[Dict[str, Any]], None]] = None,
    out_root: Optional[Path] = None,
    timeout_ms: Optional[int] = None,
    wait_ms: Optional[int] = None,
    package_name: Optional[str] = None,
    use_excel_json: bool = True,
) -> Dict[str, Any]:
    cfg = cfg or load_config()
    log = log or (lambda m: print(m, flush=True))
    shot_iv = float(cfg.get("screenshot_interval_sec") or 5)
    pkg = package_name or cfg.get("package") or "com.example.devicesdkdemo"
    to_ms = int(
        timeout_ms
        if timeout_ms is not None
        else (cfg.get("excel_timeout_ms") or EXCEL_DEFAULT_TIMEOUT_MS)
    )
    w_ms = int(
        wait_ms
        if wait_ms is not None
        else (cfg.get("excel_wait_ms") or EXCEL_DEFAULT_CHAT_WAIT_MS)
    )

    # Resolve xlsx early so out_root can be created before convert
    if xlsx:
        xlsx_path = Path(xlsx).resolve()
    else:
        name = dataset or cfg.get("dataset") or ""
        if not name:
            raise ValueError("未指定数据集")
        xlsx_path = resolve_dataset_xlsx(datasets_dir(cfg), name)

    if out_root is None:
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        ds_name = dataset or xlsx_path.parent.name or "run"
        out_root = runs_dir(cfg) / f"{_safe_name(ds_name)}_{stamp}"
    out_root = Path(out_root)
    out_root.mkdir(parents=True, exist_ok=True)

    runtime_dir = out_root / "runtime_json"
    if use_excel_json:
        log(
            f"Excel→JSON (webserver算法): timeout默认={to_ms}ms "
            f"wait_duration默认={w_ms}ms → {runtime_dir}"
        )
        xlsx_path, cases = load_cases(
            dataset=dataset,
            xlsx=str(xlsx_path),
            cfg=cfg,
            runtime_dir=runtime_dir,
            timeout_ms=to_ms,
            wait_ms=w_ms,
            package_name=pkg,
        )
        log(f"已生成 {len(cases)} 个 JSON 用例（事件驱动等待，回复即结束）")
    else:
        xlsx_path, cases = load_cases(dataset=dataset, xlsx=str(xlsx_path), cfg=cfg)

    if case_ids:
        wanted = set(case_ids)
        cases = [c for c in cases if c.case_id in wanted]
    if max_cases > 0:
        cases = cases[:max_cases]
    # 仅当显式传入 wait_cap_sec 时才二次封顶（调试用）；默认信任 JSON 事件驱动 safety
    if wait_cap_sec and wait_cap_sec > 0 and not use_excel_json:
        for c in cases:
            for t in c.turns:
                t.wait_sec = min(t.wait_sec, wait_cap_sec)

    device = make_device(cfg, serial=serial)
    sid = device.pick_serial_if_needed()
    log(f"Device: {sid}")
    device.ensure_package()

    # Excel JSON 路径：登录/QA 在每条用例 steps 里（与 webserver 一致），不再提前 prepare_qa
    if use_excel_json and not skip_prepare:
        log("Excel JSON 驱动：用例 steps 含 launch/login/QA，跳过提前 prepare")
    elif not skip_prepare:
        log("Preparing Device App → QA ...")
        device.prepare_qa(do_login=prepare_login, stop_flag=stop_flag)
        log("QA ready.")
    else:
        log("skip-prepare: assume already on QA")

    log(f"Output: {out_root}")

    summary: Dict[str, Any] = {
        "xlsx": str(xlsx_path),
        "dataset": dataset or xlsx_path.parent.name,
        "device": device.serial,
        "started_at": datetime.now().isoformat(timespec="seconds"),
        "results": [],
        "output_dir": str(out_root),
        "runtime_json": str(runtime_dir) if use_excel_json else "",
        "excel_timeout_ms": to_ms,
        "excel_wait_ms": w_ms,
    }
    for idx, case in enumerate(cases):
        if stop_flag and stop_flag.is_set():
            log("[stop] 停止后续用例")
            break
        if on_case_start:
            try:
                on_case_start(case)
            except Exception:
                pass
        # 第 1 案走完整前置；之后仅当已在聊天页才跳过 login/QA
        skip_pre = use_excel_json and idx > 0
        if skip_pre:
            on_chat = bool(device.find_center_by_rid(device.id_input))
            if not on_chat:
                log("  未在聊天页，本案改为完整 launch/login/QA 前置")
                skip_pre = False
        result = run_case(
            device,
            case,
            out_root,
            shot_iv,
            log,
            stop_flag=stop_flag,
            on_shot=on_shot,
            skip_pre_steps=skip_pre,
        )
        # 豆包：结合模型回复 + 最终截图 + Excel 预期结果
        if cfg.get("auto_eval", True) and (stop_flag is None or not stop_flag.is_set()):
            if on_case_status:
                try:
                    on_case_status(case.case_id, "analyzing")
                except Exception:
                    pass
            log("  评判中（豆包 VL）…")
            try:
                from case_eval import (
                    evaluate_case_dir,
                    load_testcase_for_case,
                    parse_evaluation_passed,
                )

                case_dir = out_root / _safe_name(case.case_id)
                tc = load_testcase_for_case(out_root, case.case_id)
                ok_eval, detail, eval_data = evaluate_case_dir(
                    case_dir, testcase=tc, result=result
                )
                if ok_eval:
                    passed = parse_evaluation_passed(eval_data)
                    result["evaluation_passed"] = passed
                    result["evaluation"] = {
                        "passed": passed,
                        "image": (eval_data or {}).get("image"),
                        "mode": (eval_data or {}).get("mode"),
                    }
                    log(f"  评判完成: passed={passed} → {detail}")
                else:
                    log(f"  评判跳过/失败: {detail}")
            except Exception as e:
                log(f"  [warn] 评判异常: {e}")

        # 先结束用例状态（避免 UI 已显示评判通过却仍停在「分析中」）
        summary["results"].append(result)
        if on_case_done:
            try:
                on_case_done(result)
            except Exception:
                pass

        # 再拉回 Device 聊天页，供下一用例跳过 login/QA（耗时，不占用 analyzing）
        if stop_flag is None or not stop_flag.is_set():
            if on_case_status:
                try:
                    on_case_status(case.case_id, "finishing")
                except Exception:
                    pass
            try:
                log("  拉回 DeviceSDK 聊天页")
                device.return_after_case(case.case_type)
            except Exception as e:
                log(f"  [warn] 拉回聊天页失败: {e}")
            # 拉回结束后保持 completed/failed（勿留在 finishing）
            if on_case_done:
                try:
                    on_case_done(result)
                except Exception:
                    pass

        # webserver CASE_BATCH_GAP：给 finish/拉回一点时间再开下一用例
        if stop_flag is None or not stop_flag.is_set():
            time.sleep(1.5)

    summary["finished_at"] = datetime.now().isoformat(timespec="seconds")
    ok_n = sum(1 for r in summary["results"] if r.get("ok"))
    summary["passed"] = ok_n
    summary["failed"] = len(summary["results"]) - ok_n
    (out_root / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    log(f"Done. passed={ok_n} failed={summary['failed']}")
    return summary
