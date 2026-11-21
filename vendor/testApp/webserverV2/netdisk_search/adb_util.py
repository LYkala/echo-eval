#!/usr/bin/env python3
from __future__ import annotations

import base64
import re
import subprocess
import tempfile
import threading
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from .config import (
    ADB_DEFAULT,
    ADB_USB_BRIDGE_HOST,
    ADB_USB_BRIDGE_PORT,
    IME_ADB,
    resolve_adb_bin,
)


class Adb:
    def __init__(
        self,
        serial: str,
        adb_path: str | Path | None = None,
        host: str | None = None,
        port: int | None = None,
    ):
        self.serial = serial
        self.adb_path = str(adb_path or resolve_adb_bin() or ADB_DEFAULT)
        self.host = host or None
        self.port = int(port) if port else None
        self.halted = False
        self._proc: subprocess.Popen | None = None
        self._proc_lock = threading.Lock()

    def halt(self) -> None:
        """Stop issuing commands and kill the one in flight."""
        self.halted = True
        with self._proc_lock:
            proc = self._proc
        if proc is not None and proc.poll() is None:
            try:
                proc.kill()
            except Exception:
                pass

    def _base(self) -> list[str]:
        cmd = [self.adb_path]
        if self.host:
            cmd.extend(["-H", str(self.host), "-P", str(self.port or ADB_USB_BRIDGE_PORT)])
        if self.serial:
            cmd.extend(["-s", self.serial])
        return cmd

    def run(self, *args: str, t: int = 60) -> str:
        if self.halted:
            return ""
        proc = subprocess.Popen(
            self._base() + list(args),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        with self._proc_lock:
            self._proc = proc
        try:
            out, err = proc.communicate(timeout=t)
        except subprocess.TimeoutExpired:
            proc.kill()
            out, err = proc.communicate()
        finally:
            with self._proc_lock:
                if self._proc is proc:
                    self._proc = None
        if self.halted:
            return ""
        text_out = (out or b"").decode("utf-8", "replace")
        text_err = (err or b"").decode("utf-8", "replace")
        return (text_out + text_err).strip()

    def dump(self, remote: str = "/sdcard/uidump_nd.xml", local: Path | None = None) -> ET.Element:
        local = local or Path(tempfile.gettempdir()) / f"uidump_nd_{self.serial or 'x'}.xml"
        try:
            if local.exists():
                local.unlink()
        except Exception:
            pass
        out_dump = self.run("shell", "uiautomator", "dump", remote, t=45)
        out_pull = self.run("pull", remote, str(local), t=45)
        if not local.is_file() or local.stat().st_size < 32:
            ch = f"{self.host}:{self.port}" if self.host else "local"
            raise RuntimeError(
                f"无法拉取 UI dump（桥接可能已断开 {ch}）。"
                f" dump=[{(out_dump or '')[:160]}] pull=[{(out_pull or '')[:160]}]。"
                f"请在手机电脑上保持 StartAgent.bat 运行，并重新点「一键准备」。"
            )
        return ET.fromstring(local.read_text(encoding="utf-8"))


def _adb_run(
    adb: str,
    args: list[str],
    host: str | None = None,
    port: int | None = None,
    t: int = 20,
) -> str:
    cmd = [adb]
    if host:
        cmd.extend(["-H", str(host), "-P", str(port or ADB_USB_BRIDGE_PORT)])
    cmd.extend(args)
    p = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=t,
    )
    return ((p.stdout or "") + (p.stderr or "")).strip()


def _parse_devices_l(out: str) -> list[dict]:
    valid_states = {
        "device",
        "offline",
        "unauthorized",
        "recovery",
        "sideload",
        "rescue",
        "connecting",
        "authorizing",
    }
    rows: list[dict] = []
    for ln in (out or "").splitlines():
        ln = ln.strip()
        if not ln or ln.startswith("List of") or ln.startswith("*") or ln.lower().startswith("adb:"):
            continue
        parts = ln.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        if state not in valid_states:
            continue
        if ":" in serial and serial.count(":") >= 1 and not serial.startswith("emulator-"):
            # allow host:port serials; reject timestamp-like junk already filtered by state
            pass
        model = product = device = ""
        for p2 in parts[2:]:
            if p2.startswith("model:"):
                model = p2.split(":", 1)[1].replace("_", " ")
            elif p2.startswith("product:"):
                product = p2.split(":", 1)[1]
            elif p2.startswith("device:"):
                device = p2.split(":", 1)[1]
        rows.append(
            {
                "serial": serial,
                "state": state,
                "model": model,
                "product": product,
                "device": device,
                "raw": ln,
            }
        )
    return rows


def _enrich(adb: str, row: dict, host: str | None = None, port: int | None = None) -> dict:
    serial = row["serial"]
    state = row["state"]
    model = row.get("model") or ""
    brand = ""
    market = ""
    if state == "device":
        try:
            brand = _adb_run(adb, ["-s", serial, "shell", "getprop", "ro.product.brand"], host, port, t=8)
            market = _adb_run(
                adb, ["-s", serial, "shell", "getprop", "ro.product.marketname"], host, port, t=8
            )
            prop_model = _adb_run(
                adb, ["-s", serial, "shell", "getprop", "ro.product.model"], host, port, t=8
            )
            if prop_model:
                model = prop_model
        except Exception:
            pass
    label_parts = []
    if brand:
        label_parts.append(brand)
    if market and market not in label_parts:
        label_parts.append(market)
    elif model and model not in label_parts:
        label_parts.append(model)
    label = " ".join(label_parts) if label_parts else (model or serial)
    return {
        **row,
        "model": model,
        "brand": brand,
        "marketname": market,
        "label": label,
    }


def list_devices(adb_path: str | Path | None = None) -> list[dict]:
    """List ADB devices: 各助手上报的设备（按电脑隔离）+ 本机/默认桥兜底。"""
    adb = str(adb_path or resolve_adb_bin() or ADB_DEFAULT)
    seen: dict[str, dict] = {}

    # 1) 各电脑助手上报（多人场景主路径）
    try:
        from .agent_registry import registered_devices

        for info in registered_devices():
            serial = info.get("serial")
            if not serial:
                continue
            seen[serial] = info
    except Exception:
        pass

    # 2) 跳板机本机 adb
    try:
        local_out = _adb_run(adb, ["devices", "-l"], t=12)
        for row in _parse_devices_l(local_out):
            if row["serial"] in seen:
                continue
            info = _enrich(adb, row)
            info["channel"] = "local"
            info["channel_label"] = "跳板机本机ADB"
            info["adb_host"] = None
            info["adb_port"] = None
            seen[row["serial"]] = info
    except Exception:
        pass

    # 3) 扫一遍已注册助手的桥端口（补全未及时上报时）
    try:
        from .agent_registry import online_agents

        for ag in online_agents():
            port = int(ag.get("bridge_port") or 0)
            if port <= 0:
                continue
            host = ADB_USB_BRIDGE_HOST
            try:
                bridge_out = _adb_run(adb, ["devices", "-l"], host=host, port=port, t=5)
            except Exception:
                continue
            host_label = ag.get("hostname") or ag.get("agent_id") or str(port)
            for row in _parse_devices_l(bridge_out):
                info = _enrich(adb, row, host=host, port=port)
                info["channel"] = f"{host}:{port}"
                info["channel_label"] = f"{host_label} · 桥:{port}"
                info["adb_host"] = host
                info["adb_port"] = port
                info["agent_id"] = ag.get("agent_id")
                info["hostname"] = host_label
                # 助手上报优先；扫描结果可覆盖同 serial 的空标签
                prev = seen.get(row["serial"])
                if not prev or prev.get("agent_id") == ag.get("agent_id"):
                    seen[row["serial"]] = info
    except Exception:
        pass

    return list(seen.values())


def resolve_device_endpoint(serial: str) -> dict | None:
    serial = (serial or "").strip()
    if not serial:
        return None
    for d in list_devices():
        if d.get("serial") == serial and d.get("state") == "device":
            return d
    return None


def capture_screenshot(ep: dict, timeout: int = 25) -> bytes:
    """One-shot PNG of the phone screen via adb exec-out screencap."""
    if not ep:
        raise RuntimeError("无设备端点")
    adb = resolve_adb_bin() or str(ADB_DEFAULT)
    serial = ep.get("serial") or ""
    host = ep.get("adb_host") or None
    port = ep.get("adb_port") or None
    cmd = [adb]
    if host:
        cmd.extend(["-H", str(host), "-P", str(port or ADB_USB_BRIDGE_PORT)])
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(["exec-out", "screencap", "-p"])
    try:
        proc = subprocess.run(cmd, capture_output=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired as e:
        ch = f"{host}:{port}" if host else "local"
        raise RuntimeError(f"截图超时（桥 {ch} 可能已断开）") from e
    data = proc.stdout or b""
    # adb 偶发把日志混进 stdout；找到真正的 PNG 头
    png_at = data.find(b"\x89PNG\r\n\x1a\n")
    if png_at > 0:
        data = data[png_at:]
    err = (proc.stderr or b"").decode("utf-8", "replace").strip()
    blob = ((proc.stdout or b"")[:240] + (proc.stderr or b"")[:240]).decode(
        "utf-8", "replace"
    )
    if "Connection refused" in err or "Connection refused" in blob or "cannot connect to daemon" in blob:
        ch = f"{host}:{port}" if host else "local"
        raise RuntimeError(
            f"ADB 桥已断开（{ch} Connection refused）。"
            f"请在手机电脑上保持 StartAgent.bat 运行，并重新点「一键准备」。"
        )
    if proc.returncode != 0 or len(data) < 100 or not data.startswith(b"\x89PNG"):
        if not err and data:
            err = data[:180].decode("utf-8", "replace").strip()
        ch = f"{host}:{port}" if host else "local"
        raise RuntimeError(
            err
            or f"截图失败（桥 {ch}，rc={proc.returncode}）。请保持 StartAgent.bat 运行并重新一键准备。"
        )
    return data


def probe_device_reachable(ep: dict, timeout: int = 8) -> tuple[bool, str]:
    """Verify server can adb through the device's bridge before starting a run."""
    if not ep:
        return False, "无设备端点"
    adb = resolve_adb_bin() or str(ADB_DEFAULT)
    serial = ep.get("serial") or ""
    host = ep.get("adb_host") or None
    port = ep.get("adb_port") or None
    try:
        out = _adb_run(
            adb,
            ["-s", serial, "get-state"] if serial else ["get-state"],
            host=host,
            port=int(port) if port else None,
            t=timeout,
        )
    except Exception as e:
        return False, f"adb 探测异常: {e}"
    if "device" in (out or "").lower():
        return True, out
    ch = f"{host}:{port}" if host else "local"
    return False, (
        f"跳板机无法经 {ch} 控制该手机（Connection refused / 桥已断）。"
        f"adb输出: {(out or '')[:200]}。"
        f"请让该电脑保持 StartAgent.bat 运行并重新「一键准备」。"
    )


def parse_bounds(bounds: str) -> tuple[int, int, int, int]:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return 0, 0, 0, 0
    return tuple(map(int, m.groups()))  # type: ignore[return-value]


def center(bounds: str) -> tuple[int, int]:
    x1, y1, x2, y2 = parse_bounds(bounds)
    return (x1 + x2) // 2, (y1 + y2) // 2


def focus_blob(adb: Adb) -> str:
    parts = []
    for ln in adb.run("shell", "dumpsys", "window").splitlines():
        if "mCurrentFocus" in ln or "mFocusedApp" in ln:
            parts.append(ln.strip())
    return "\n".join(parts)


def on_timeline(adb: Adb) -> bool:
    return "SearchTimeLineActivity" in focus_blob(adb)


def on_preview(adb: Adb) -> bool:
    return "ImagePagerActivity" in focus_blob(adb)


def on_flutter_search(adb: Adb) -> bool:
    return "FlutterBusinessActivity" in focus_blob(adb)


def find_edit(root: ET.Element) -> ET.Element | None:
    edit = None
    for n in root.iter("node"):
        if "EditText" in (n.get("class") or "") and n.get("bounds"):
            edit = n
    return edit


def find_by_text(root: ET.Element, label: str) -> ET.Element | None:
    for n in root.iter("node"):
        t = (n.get("text") or "").strip()
        d = (n.get("content-desc") or "").strip()
        if t == label or d == label:
            return n
    return None


def find_text_by_id(root: ET.Element, suffix: str) -> str | None:
    for n in root.iter("node"):
        rid = n.get("resource-id") or ""
        if rid.endswith(suffix):
            t = (n.get("text") or "").strip()
            if t:
                return t
    return None


def list_thumbs(root: ET.Element) -> list[dict]:
    thumbs = []
    for n in root.iter("node"):
        rid = n.get("resource-id") or ""
        if not rid.endswith("/thumbnail"):
            continue
        b = n.get("bounds") or ""
        x1, y1, x2, y2 = parse_bounds(b)
        if x2 - x1 < 80 or y2 - y1 < 80:
            continue
        thumbs.append(
            {
                "cx": (x1 + x2) // 2,
                "cy": (y1 + y2) // 2,
                "y1": y1,
                "x1": x1,
                "key": f"{x1},{y1}",
            }
        )
    thumbs.sort(key=lambda t: (t["y1"], t["x1"]))
    return thumbs


def ensure_ime(adb: Adb) -> str:
    prev = adb.run("shell", "settings", "get", "secure", "default_input_method")
    # 已是 AdbIME 时直接跳过，避免重复切换造成输入法状态抖动
    if (prev or "").strip() == IME_ADB:
        return prev
    en = adb.run("shell", "ime", "enable", IME_ADB)
    # 华为/荣耀常拒绝 ime enable，需先写入 enabled_input_methods
    if "cannot be enabled" in en or "Unknown input method" in en:
        cur = (adb.run("shell", "settings", "get", "secure", "enabled_input_methods") or "").strip()
        if "adbkeyboard" not in cur.lower():
            nxt = IME_ADB if not cur or cur == "null" else f"{cur}:{IME_ADB}"
            adb.run("shell", "settings", "put", "secure", "enabled_input_methods", nxt)
        adb.run("shell", "ime", "enable", IME_ADB)
    adb.run("shell", "ime", "set", IME_ADB)
    time.sleep(0.35)
    cur = (adb.run("shell", "settings", "get", "secure", "default_input_method") or "").strip()
    if "adbkeyboard" not in cur.lower():
        adb.run("shell", "settings", "put", "secure", "default_input_method", IME_ADB)
        adb.run("shell", "ime", "set", IME_ADB)
        time.sleep(0.35)
    return prev


def restore_ime(adb: Adb, prev: str) -> None:
    if prev and prev != "null":
        adb.run("shell", "ime", "set", prev)


def _node_text(n: ET.Element) -> str:
    return (n.get("text") or "").strip()


def _is_placeholder(text: str) -> bool:
    return text in {"", "搜索", "搜索图片", "请输入", "搜索文件", "搜索照片"}


def find_clear_x(root: ET.Element, edit: ET.Element) -> tuple[int, int] | None:
    """搜索栏右侧的叉。优先按文案/id，否则取输入框右缘内侧。"""
    ex1, ey1, ex2, ey2 = parse_bounds(edit.get("bounds") or "")
    if ex2 <= ex1:
        return None
    ecy = (ey1 + ey2) // 2
    search = find_by_text(root, "搜索")
    search_left = 10**9
    if search is not None:
        search_left, _, _, _ = parse_bounds(search.get("bounds") or "")

    for n in root.iter("node"):
        rid = (n.get("resource-id") or "").lower()
        text = _node_text(n)
        desc = (n.get("content-desc") or "").strip()
        blob = f"{text} {desc} {rid}"
        if text == "搜索":
            continue
        if any(k in blob for k in ("清除", "清空", "clear", "delete", "clean")):
            b = n.get("bounds") or ""
            if b:
                return center(b)

    best: tuple[int, int] | None = None
    best_x = -1
    for n in root.iter("node"):
        cls = n.get("class") or ""
        if "EditText" in cls:
            continue
        if _node_text(n) == "搜索":
            continue
        b = n.get("bounds") or ""
        if not b:
            continue
        x1, y1, x2, y2 = parse_bounds(b)
        w, h = x2 - x1, y2 - y1
        if w <= 8 or h <= 8 or w > 140 or h > 140:
            continue
        cy = (y1 + y2) // 2
        if abs(cy - ecy) > 70:
            continue
        if x1 < ex1 or x1 >= search_left - 4:
            continue
        cx = (x1 + x2) // 2
        # 左边那个图标一直在，叉只出现在输入框右半边
        if cx <= (ex1 + ex2) // 2:
            continue
        if cx > best_x:
            best_x = cx
            best = (cx, cy)
    if best:
        return best
    if not _is_placeholder(_node_text(edit)):
        return max(ex1 + 8, ex2 - 36), ecy
    return None


# 同一台手机上搜索栏位置不变：输入框、叉、搜索按钮只找一次
_BAR_CACHE: dict[str, dict[str, tuple[int, int]]] = {}


def _bar_key(adb: Adb) -> str:
    return f"{adb.host or ''}:{adb.port or ''}:{adb.serial}"


def bar_cache(adb: Adb) -> dict[str, tuple[int, int]]:
    return _BAR_CACHE.setdefault(_bar_key(adb), {})


def learn_search_bar(adb: Adb, root: ET.Element) -> None:
    cache = bar_cache(adb)
    edit = find_edit(root)
    if edit is not None and edit.get("bounds"):
        cache["edit"] = center(edit.get("bounds") or "")
        xy = find_clear_x(root, edit)
        if xy:
            cache["clear"] = xy
    search = find_by_text(root, "搜索")
    if search is not None and search.get("bounds"):
        cache["search"] = center(search.get("bounds") or "")


def _tap_xy(adb: Adb, xy: tuple[int, int]) -> None:
    adb.run("shell", "input", "tap", str(xy[0]), str(xy[1]))


def commit_query(adb: Adb, query: str) -> str:
    """把搜索词交给 ADB Keyboard。中文走 base64，避免 shell 把字弄丢。"""
    payload = base64.b64encode(query.encode("utf-8")).decode("ascii")
    return adb.run(
        "shell", "am", "broadcast", "-a", "ADB_INPUT_B64", "--es", "msg", payload
    )


def field_has_query(root: ET.Element, query: str) -> bool:
    edit = find_edit(root)
    if edit is None:
        return False
    text = _node_text(edit)
    if _is_placeholder(text):
        return False
    compact = "".join(text.split())
    want = "".join((query or "").split())
    return bool(want) and want in compact


def _focus_type(adb: Adb, cache: dict, query: str, log) -> None:
    """点输入框、清掉旧词、再点回输入框，然后写入。"""
    _tap_xy(adb, cache["edit"])
    time.sleep(0.3)
    if cache.get("clear"):
        _tap_xy(adb, cache["clear"])
        time.sleep(0.25)
        _tap_xy(adb, cache["edit"])
        time.sleep(0.3)
    out = commit_query(adb, query)
    log(f"input broadcast {(out or '')[:140]}")
    time.sleep(0.35)


def clear_edit(adb: Adb, root: ET.Element | None = None) -> None:
    """点搜索栏右侧的叉。有缓存就直接点，不再拉界面。"""
    cache = bar_cache(adb)
    if root is None and cache.get("clear"):
        _tap_xy(adb, cache["clear"])
        time.sleep(0.2)
        return
    if root is None:
        root = adb.dump()
    learn_search_bar(adb, root)
    xy = bar_cache(adb).get("clear")
    if not xy:
        return
    _tap_xy(adb, xy)
    time.sleep(0.2)


def input_query_and_search(adb: Adb, query: str, log, stop_flag=None) -> dict:
    """
    Input query and tap 搜索.
    Returns {"ok": bool, "latency_ms": int|None} where latency is
    from search tap to SearchTimeLineActivity appearing.
    """
    prev = ensure_ime(adb)
    try:
        cache = bar_cache(adb)
        if not cache.get("edit") or not cache.get("search"):
            root = adb.dump()
            learn_search_bar(adb, root)
            cache = bar_cache(adb)
            if not cache.get("edit") or not cache.get("search"):
                log(
                    f"missing edit/search edit={cache.get('edit')} search={cache.get('search')}"
                )
                return {"ok": False, "latency_ms": None}
            log(
                f"cached search bar edit={cache.get('edit')} "
                f"search={cache.get('search')} clear={cache.get('clear')}"
            )
        else:
            log(
                f"use cached search bar edit={cache.get('edit')} "
                f"search={cache.get('search')} clear={cache.get('clear')}"
            )
        _focus_type(adb, cache, query, log)
        root = adb.dump()
        if not field_has_query(root, query):
            log("query not in field, refocus and resend")
            _tap_xy(adb, cache["edit"])
            time.sleep(0.3)
            out = commit_query(adb, query)
            log(f"input resend {(out or '')[:140]}")
            time.sleep(0.35)
            root = adb.dump()
            if not field_has_query(root, query):
                shown = ""
                edit = find_edit(root)
                if edit is not None:
                    shown = _node_text(edit)
                log(f"input miss field={shown!r}")
                return {"ok": False, "latency_ms": None}
        t0 = time.perf_counter()
        _tap_xy(adb, cache["search"])
        # poll until timeline or timeout (~12s)
        ok = False
        latency_ms = None
        deadline = t0 + 12.0
        while time.perf_counter() < deadline:
            if stop_flag is not None and stop_flag():
                log("search aborted")
                return {"ok": False, "latency_ms": None, "stopped": True}
            if adb.halted:
                log("search aborted")
                return {"ok": False, "latency_ms": None, "stopped": True}
            if on_timeline(adb):
                latency_ms = int(round((time.perf_counter() - t0) * 1000))
                ok = True
                break
            time.sleep(0.15)
        if not ok:
            latency_ms = int(round((time.perf_counter() - t0) * 1000))
            log(f"search timeout latency_ms={latency_ms}")
        else:
            log(f"search latency_ms={latency_ms}")
        return {"ok": ok, "latency_ms": latency_ms}
    finally:
        restore_ime(adb, prev)


def ensure_list(adb: Adb, log, n: int = 6) -> bool:
    for i in range(1, n + 1):
        if on_timeline(adb):
            return True
        adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.8)
        log(f"back#{i} timeline={on_timeline(adb)}")
    return on_timeline(adb)


def read_name_via_more(
    adb: Adb,
    more_xy: tuple[int, int] | None = None,
    wake_first: bool = False,
) -> dict:
    """
    Tap 更多 and read image_name.
    - wake_first=False (首张): 直接找/点更多；找不到再点屏幕后重试
    - wake_first=True (左滑后): 先点屏幕再点更多（用缓存坐标）
    """
    if wake_first:
        adb.run("shell", "input", "tap", "540", "1200")
        time.sleep(0.4)

    if more_xy is None:
        root = adb.dump()
        more = find_by_text(root, "更多")
        if more is None and not wake_first:
            # 首张：找不到再点屏幕唤出底栏
            adb.run("shell", "input", "tap", "540", "1200")
            time.sleep(0.4)
            root = adb.dump()
            more = find_by_text(root, "更多")
        if more is None:
            return {"ok": False, "error": "no_更多", "more_xy": None}
        more_xy = center(more.get("bounds") or "")

    x, y = more_xy
    adb.run("shell", "input", "tap", str(x), str(y))
    time.sleep(1.0)
    root2 = adb.dump()
    name = find_text_by_id(root2, "/image_name")
    if not name:
        return {"ok": False, "error": "no_name", "more_xy": None}
    return {"ok": True, "name": name, "more_xy": more_xy}


def close_more_stay_preview(adb: Adb, log) -> bool:
    """Close 更多 sheet with one BACK; stay on ImagePager (no dumpsys)."""
    adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.75)
    log("close_more ok")
    return True


def pager_swipe_next(adb: Adb, log=None) -> None:
    """Swipe left inside ImagePager. Stuck detection is by filename on next read."""
    adb.run("shell", "input", "swipe", "920", "1200", "160", "1200", "280")
    time.sleep(0.95)
    if log:
        log("pager swipe")


def collect_search_order(adb: Adb, log, stop_flag=None, max_items: int = 60) -> dict:
    """
    Open first visible thumb, then keep reading inside ImagePager by swiping
    to the next image (no grid scroll / coordinate keys).

    Returns {"records": [...], "status": looped|exhausted|capped|stopped|failed}
    Only status=looped/exhausted/capped counts as a finished case.
    """
    records: list[dict] = []
    known_names: set[str] = set()
    first_name: str | None = None
    fail_streak = 0
    more_xy: tuple[int, int] | None = None
    wake_first = False  # 首张不先点屏幕；左滑后才先点屏幕
    status = "failed"

    if not ensure_list(adb, log):
        log("FAIL not on timeline")
        return {"records": records, "status": status}

    thumbs = list_thumbs(adb.dump())
    if not thumbs:
        log("no thumbs visible")
        return {"records": records, "status": "empty"}

    t0 = thumbs[0]
    log(f"enter pager via {t0['cx']},{t0['cy']}")
    adb.run("shell", "input", "tap", str(t0["cx"]), str(t0["cy"]))
    time.sleep(1.5)
    if not on_preview(adb):
        time.sleep(0.7)
    if not on_preview(adb):
        log("FAIL not in ImagePager")
        return {"records": records, "status": status}

    while len(records) < max_items:
        if stop_flag is not None and stop_flag():
            log("stopped by user")
            status = "stopped"
            break

        pos = len(records) + 1
        log(f"read pager #{pos}")
        info = read_name_via_more(adb, more_xy=more_xy, wake_first=wake_first)
        if "more_xy" in info:
            more_xy = info["more_xy"]

        if not info.get("ok"):
            fail_streak += 1
            log(f"read fail {info.get('error')} streak={fail_streak}")
            more_xy = None
            pager_swipe_next(adb, log)
            wake_first = True
            if fail_streak >= 3:
                log("stop: 3 consecutive read fails")
                status = "exhausted" if records else "failed"
                break
            continue

        close_more_stay_preview(adb, log)

        name = info["name"]
        if first_name is not None and name == first_name:
            log(f"loop stop: name==first ({first_name})")
            status = "looped"
            break

        # 文件名兜底：只判一次（左滑划不动 / 到头）
        if name in known_names:
            log(f"swipe stuck: name already seen ({name})")
            status = "exhausted"
            break

        fail_streak = 0
        if first_name is None:
            first_name = name
        known_names.add(name)
        records.append({"pos": pos, "name": name})
        log(f"saved #{pos} {name}")

        if len(records) >= max_items:
            status = "capped"
            break
        log("pager next")
        if stop_flag is not None and stop_flag():
            log("stopped by user")
            status = "stopped"
            break
        pager_swipe_next(adb, log)
        wake_first = True

    if not (stop_flag is not None and stop_flag()):
        ensure_list(adb, log)
    return {"records": records, "status": status}


def init_back_and_clear(adb: Adb, log) -> None:
    """Step3: back to search page + tap the clear (×) on the search bar."""
    for i in range(1, 5):
        if on_flutter_search(adb) and not on_timeline(adb):
            break
        adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.9)
        log(f"init back#{i}")
    clear_edit(adb)
    log("cleared search bar")
