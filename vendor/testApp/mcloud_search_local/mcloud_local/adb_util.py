#!/usr/bin/env python3
"""移动云盘（com.chinamobile.mcloud）搜索链路。

搜索入口是当前页的灵犀输入框。查询格式为「查找 + 用例名 + 的照片」，
发出后点结果里的第一张，再沿用大图左滑读文件名。
"""
from __future__ import annotations

import base64
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
import zlib
import struct
from pathlib import Path

from .adb_base import (  # noqa: F401  (re-export for runner/routes)
    Adb as _BaseAdb,
    capture_screenshot,
    center,
    ensure_ime,
    find_edit,
    focus_blob,
    list_devices,
    parse_bounds,
    probe_device_reachable,
    resolve_device_endpoint,
    restore_ime,
    _tap_xy,
)

from .config import MCLOUD_PKG


# 华为 EMUI 12 自带的 uiautomator 在 WebView 空子节点上会空指针，文件停在 0 字节。
# 这份 jar 只替换 AccessibilityNodeInfoDumper，跳过空子节点。
_SAFE_DUMP_JAR = Path(__file__).resolve().parent / "bin" / "uiautomator-safe.jar"
_SAFE_DUMP_REMOTE = "/data/local/tmp/uiautomator-safe.jar"
_SAFE_DUMP_SERIALS: set[str] = set()
_SAFE_PUSHED: set[str] = set()
# 灵犀画在 WebView 里的手机：回答文字不在控件树，清空输入框也点不到。
_WEBVIEW_SERIALS: set[str] = set()
_SETCLIP_PUSHED: set[str] = set()


def _parse_dump_text(text: str) -> ET.Element | None:
    raw = (text or "").replace("\r\n", "\n").replace("\r", "").strip()
    start = raw.find("<")
    if start < 0 or len(raw) - start < 32:
        return None
    try:
        return ET.fromstring(raw[start:])
    except Exception:
        return None


class Adb(_BaseAdb):
    """移动云盘专用 Adb。

    先用系统 uiautomator dump。华为机写不出新文件时，改用会跳过空子节点的
    dumper，并在之后的读取里直接走这条路径。
    """

    def _load_pulled(self, remote: str, local: Path) -> ET.Element | None:
        try:
            if local.exists():
                local.unlink()
        except Exception:
            pass
        self.run("pull", remote, str(local), t=45)
        if not local.is_file() or local.stat().st_size < 32:
            return None
        try:
            return ET.fromstring(local.read_text(encoding="utf-8"))
        except Exception:
            return None

    def _run_safe_dump(self, remote: str, cat: bool) -> str:
        if not _SAFE_DUMP_JAR.is_file():
            return ""
        key = self.serial or ""
        if key not in _SAFE_PUSHED:
            self.run("push", str(_SAFE_DUMP_JAR), _SAFE_DUMP_REMOTE, t=30)
            _SAFE_PUSHED.add(key)
        tail = f"; cat {remote}" if cat else ""
        cmd = (
            f"rm -f {remote}; "
            "mkdir -p /data/local/tmp/dalvik-cache; "
            "export ANDROID_DATA=/data/local/tmp; "
            f"export CLASSPATH={_SAFE_DUMP_REMOTE}:/system/framework/android.test.runner.jar:/system/framework/uiautomator.jar; "
            "app_process /system/bin com.android.commands.uiautomator.Launcher "
            f"dump {remote} >/dev/null 2>&1{tail}"
        )
        return self.run("shell", cmd, t=45) or ""

    def dump(
        self,
        remote: str = "/sdcard/uidump_mcloud.xml",
        local: Path | None = None,
    ) -> ET.Element:
        local = local or (
            Path(tempfile.gettempdir()) / f"uidump_mcloud_{self.serial or 'x'}.xml"
        )
        key = self.serial or ""
        if key not in _SAFE_DUMP_SERIALS:
            self.run(
                "shell",
                f"rm -f {remote}; uiautomator dump --compressed {remote}",
                t=45,
            )
            tree = self._load_pulled(remote, local)
            if tree is not None:
                return tree
        self._run_safe_dump(remote, cat=False)
        tree = self._load_pulled(remote, local)
        if tree is not None:
            _SAFE_DUMP_SERIALS.add(key)
            return tree
        raise RuntimeError("mcloud UI dump failed (no fresh xml)")

    def dump_stdout(
        self, remote: str = "/sdcard/uidump_mcloud_so.xml"
    ) -> ET.Element:
        """一次 adb 往返拿 dump：设备端 dump 后直接 `cat` 回 stdout（省掉 pull 往返）。

        只用于采集热路径（读「更多」弹窗）。系统 dump 失败时改用空节点安全版。
        adb shell 会把换行转成 \\r\\n，解析前统一归一化。
        """
        last = ""
        key = self.serial or ""
        if key not in _SAFE_DUMP_SERIALS:
            cmd = (
                f"rm -f {remote}; uiautomator dump --compressed {remote} "
                f">/dev/null 2>&1; cat {remote}"
            )
            out = self.run("shell", cmd, t=45) or ""
            tree = _parse_dump_text(out)
            if tree is not None:
                return tree
            last = out.replace("\r", "")[:80]
        out = self._run_safe_dump(remote, cat=True)
        tree = _parse_dump_text(out)
        if tree is not None:
            _SAFE_DUMP_SERIALS.add(key)
            return tree
        last = (out or last).replace("\r", "")[:80]
        raise RuntimeError(f"mcloud UI dump(stdout) failed: {last}")


# 采集状态语义与 netdisk 保持一致：looped/exhausted/capped 算完成
_DONE_STATUSES = {"looped", "exhausted", "capped"}

_FILENAME_RE = re.compile(r".+\.(jpe?g|png|heic|webp|gif|bmp)$", re.I)


# ---- 页面判定 ----

def on_mcloud_home(adb: Adb) -> bool:
    return "MenuActivity" in focus_blob(adb)


def on_mcloud_search(adb: Adb) -> bool:
    return "SearchGlobalActivity" in focus_blob(adb)


def on_mcloud_preview(adb: Adb) -> bool:
    """搜索结果大图 / 相关图片相册大图 均视为预览页。"""
    blob = focus_blob(adb)
    return (
        "AlbumImagePreviewActivity" in blob
        or "CloudMediaPreviewActivity" in blob
    )


def _page_kind(blob: str) -> str:
    """用当前 Activity 判断页面，不再为了确认相册去整页 dump。"""
    if "AiSearchImageActivity" in blob or "RelateAlbumImagesActivity" in blob:
        return "album"
    if "AlbumImagePreviewActivity" in blob or "CloudMediaPreviewActivity" in blob:
        return "preview"
    if "MenuActivity" in blob:
        return "lingxi"
    return "other"


def _wait_page(
    adb: Adb, seconds: float = 3.0, accept: tuple[str, ...] = ("album", "preview")
) -> str:
    """等到指定页面。点缩略图后只接受大图，不能把「还在相册」当成已经打开。"""
    deadline = time.perf_counter() + seconds
    seen = "other"
    while time.perf_counter() < deadline:
        if adb.halted:
            return "halted"
        seen = _page_kind(focus_blob(adb))
        if seen in accept:
            return seen
        time.sleep(0.2)
    return seen


def on_mcloud_relate_album(adb: Adb) -> bool:
    return "RelateAlbumImagesActivity" in focus_blob(adb)


# ---- 节点工具 ----

def _nodes(root: ET.Element):
    return root.iter("node")


def _xy_by_id(root: ET.Element, suffix: str) -> tuple[int, int] | None:
    for n in _nodes(root):
        rid = n.get("resource-id") or ""
        if rid.endswith(suffix) and n.get("bounds"):
            return center(n.get("bounds") or "")
    return None


def _node_by_id(root: ET.Element, suffix: str) -> ET.Element | None:
    for n in _nodes(root):
        rid = n.get("resource-id") or ""
        if rid.endswith(suffix):
            return n
    return None


def _text_by_id(root: ET.Element, suffix: str) -> str | None:
    n = _node_by_id(root, suffix)
    if n is None:
        return None
    t = (n.get("text") or "").strip()
    return t or None


_SCREEN_CACHE: dict[str, tuple[int, int]] = {}
# 灵犀页上点成功过的固定位置：input / send / keyboard / more / album_first。
# 加号跟着结果滚动，不放进来。
_LINGXI_XY: dict[str, dict[str, tuple[int, int]]] = {}


def _lx_cache(adb: Adb) -> dict[str, tuple[int, int]]:
    return _LINGXI_XY.setdefault(adb.serial or "x", {})


def _lx_remember(adb: Adb, key: str, xy: tuple[int, int], log) -> None:
    cache = _lx_cache(adb)
    if cache.get(key) == xy:
        return
    cache[key] = xy
    log(f"cache {key} {xy}")


def _lx_drop(adb: Adb, key: str, log) -> None:
    cache = _lx_cache(adb)
    if key in cache:
        log(f"drop cache {key} {cache.pop(key)}")


def _screen_size(adb: Adb) -> tuple[int, int]:
    """当前屏幕分辨率（按设备号缓存：一次运行内分辨率不变，省去每步 wm size 往返）。

    `wm size` 会同时打印 `Physical size:` 与 `Override size:`（当有覆盖时）。
    必须取**最后一行**（Override → 实际可用分辨率），否则会按物理分辨率算坐标，
    导致滑动起点落在屏幕真实边缘、触发系统侧滑返回手势。
    """
    key = adb.serial or "x"
    cached = _SCREEN_CACHE.get(key)
    if cached:
        return cached
    out = adb.run("shell", "wm", "size") or ""
    matches = re.findall(r"(\d+)\s*x\s*(\d+)", out)
    if matches:
        w, h = (int(matches[-1][0]), int(matches[-1][1]))
    else:
        w, h = 1080, 2400
    _SCREEN_CACHE[key] = (w, h)
    return w, h


_LINGXI_PLACEHOLDER = "有问题尽管问我~"
_LINGXI_FOUND = "为您找到云盘内的图片"


def _bounds_of(node: ET.Element) -> tuple[int, int, int, int]:
    return parse_bounds(node.get("bounds") or "")


def _result_visible(root: ET.Element) -> bool:
    """结果出现：灵犀回答「为您找到云盘内的图片」，或旧搜索页的结果容器。"""
    for n in _nodes(root):
        rid = n.get("resource-id") or ""
        text = (n.get("text") or "").strip()
        if text == _LINGXI_FOUND:
            return True
        if (
            rid.endswith("/cl_result_tab")
            or rid.endswith("/rv_images")
            or rid.endswith("/tv_images_title")
        ):
            return True
    return False


def _lingxi_edit(root: ET.Element) -> ET.Element | None:
    """当前页底部的灵犀输入框。取最靠下的 EditText。"""
    best = None
    best_y = -1
    for n in _nodes(root):
        if not (n.get("class") or "").endswith("EditText"):
            continue
        x1, y1, x2, y2 = _bounds_of(n)
        if y2 - y1 < 36 or x2 - x1 < 200:
            continue
        if y1 >= best_y:
            best, best_y = n, y1
    return best


def _node_text(node: ET.Element) -> str:
    return (node.get("text") or "").strip()


def _text_count(root: ET.Element, text: str) -> int:
    want = (text or "").strip()
    if not want:
        return 0
    count = 0
    for n in _nodes(root):
        if (n.get("class") or "").endswith("EditText"):
            continue
        x1, y1, x2, y2 = _bounds_of(n)
        if x2 <= x1 or y2 <= y1:
            continue
        if _node_text(n) == want:
            count += 1
    return count


def _latest_text_y(root: ET.Element, text: str) -> int | None:
    best = None
    for n in _nodes(root):
        if (n.get("class") or "").endswith("EditText"):
            continue
        if _node_text(n) != text:
            continue
        _x1, y1, x2, y2 = _bounds_of(n)
        if x2 <= _x1 or y2 <= y1 or y1 < 80 or y1 > 2200:
            continue
        if best is None or y1 > best:
            best = y1
    return best


def _lingxi_loading(root: ET.Element) -> bool:
    for n in _nodes(root):
        text = _node_text(n)
        if not text:
            continue
        if "不要离开" in text or "精彩马上呈现" in text or "正在查找" in text or "正在生成" in text:
            return True
    return False


def _overlaps_plus(cell: dict, plus_boxes: list[tuple[int, int, int, int]]) -> bool:
    cx, cy = cell["cx"], cell["cy"]
    for x1, y1, x2, y2 in plus_boxes:
        if x1 <= cx <= x2 and y1 <= cy <= y2:
            return True
    return False


def _lingxi_image_cells(root: ET.Element) -> list[dict]:
    """最新一条「为您找到云盘内的图片」下面的缩略图，排除 +N。"""
    title_y = _latest_text_y(root, _LINGXI_FOUND)
    plus_boxes = [
        _bounds_of(n)
        for n in _nodes(root)
        if re.fullmatch(r"\+\d+", _node_text(n))
    ]
    cells: list[dict] = []
    for n in _nodes(root):
        if n.get("clickable") != "true":
            continue
        if not (n.get("class") or "").endswith("ImageButton"):
            continue
        x1, y1, x2, y2 = _bounds_of(n)
        width, height = x2 - x1, y2 - y1
        if width < 140 or height < 140 or width > 260 or height > 260:
            continue
        if abs(width - height) > 48:
            continue
        if title_y is not None and (y1 < title_y or y1 > title_y + 560):
            continue
        cell = {
            "cx": (x1 + x2) // 2,
            "cy": (y1 + y2) // 2,
            "y1": y1,
            "x1": x1,
            "x2": x2,
            "y2": y2,
        }
        if _overlaps_plus(cell, plus_boxes):
            continue
        more = False
        for child in n.iter("node"):
            if re.fullmatch(r"\+\d+", _node_text(child)):
                more = True
                break
        if more:
            continue
        cells.append(cell)
    cells.sort(key=lambda c: (c["y1"], c["x1"]))
    return cells


def _hide_keyboard(adb: Adb) -> None:
    shown = adb.run("shell", "dumpsys", "input_method") or ""
    if "mInputShown=true" in shown:
        adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.4)


# ---- 导航 ----

def mcloud_go_home(adb: Adb, log, n: int = 8) -> None:
    """回到移动云盘首页 MenuActivity。"""
    for i in range(n):
        if adb.halted:
            return
        if on_mcloud_home(adb):
            return
        if MCLOUD_PKG not in focus_blob(adb):
            log("left mcloud app while backing -> relaunch")
            break
        log(f"back to mcloud home #{i}")
        adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.8)
    if not on_mcloud_home(adb):
        log("relaunch mcloud home")
        adb.run(
            "shell", "monkey", "-p", MCLOUD_PKG,
            "-c", "android.intent.category.LAUNCHER", "1",
        )
        for _ in range(20):
            time.sleep(1.0)
            if adb.halted:
                return
            if on_mcloud_home(adb):
                return
        log("WARN: mcloud home not reached after relaunch")


def _home_search_entry(root: ET.Element) -> tuple[int, int] | None:
    xy = _xy_by_id(root, "/ll_search")
    if xy:
        return xy
    # 兜底：顶部（y1<420）里 rc/desc 带 search 的可点节点
    best = None
    best_y = 10 ** 9
    for n in _nodes(root):
        if n.get("clickable") != "true":
            continue
        blob = f"{n.get('resource-id') or ''} {n.get('content-desc') or ''}".lower()
        if "search" not in blob:
            continue
        b = n.get("bounds") or ""
        x1, y1, x2, y2 = parse_bounds(b)
        if x2 <= x1 or y2 <= y1 or y1 > 420:
            continue
        if y1 < best_y:
            best, best_y = center(b), y1
    return best


def mcloud_open_global_search(adb: Adb, log) -> None:
    """首页 → 点搜索入口 → SearchGlobalActivity。"""
    xy = None
    for attempt in range(6):
        if adb.halted:
            raise RuntimeError("halted")
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"home dump fail (retry #{attempt}): {str(exc)[:80]}")
            time.sleep(1.0)
            continue
        xy = _home_search_entry(root)
        if xy is not None:
            break
        log(f"search entry not found (retry #{attempt})")
        if attempt == 2:
            mcloud_go_home(adb, log)
        time.sleep(1.0)
    if xy is None:
        raise RuntimeError("mcloud home search entry not found")
    log(f"tap mcloud search entry {xy}")
    _tap_xy(adb, xy)
    time.sleep(1.5)
    if on_mcloud_search(adb):
        log("opened mcloud global search page")
    else:
        log("WARN: not on mcloud search page after tap")


# ---- 搜索输入 ----

def _stopped(adb: Adb, stop_flag) -> bool:
    return bool(adb.halted or (stop_flag is not None and stop_flag()))


def _composer_send(root: ET.Element) -> tuple[int, int] | None:
    """输入框右侧的发送键。空输入时这一格是语音，所以只在已经写入文字后点。"""
    for n in _nodes(root):
        blob = f"{_node_text(n)} {n.get('content-desc') or ''}"
        if "发送" in blob and n.get("clickable") == "true" and n.get("bounds"):
            return center(n.get("bounds") or "")
    slots: list[tuple[int, int, int]] = []
    for n in _nodes(root):
        if n.get("clickable") != "true":
            continue
        if (n.get("content-desc") or "") == "ai_camera":
            continue
        x1, y1, x2, y2 = _bounds_of(n)
        width, height = x2 - x1, y2 - y1
        if y1 < 2050 or width < 60 or width > 140 or height < 60 or height > 140:
            continue
        slots.append(((x1 + x2) // 2, (y1 + y2) // 2, x1))
    if not slots:
        return None
    slots.sort(key=lambda item: item[2])
    xy = slots[-1]
    return xy[0], xy[1]


def _field_has_query(root: ET.Element, query: str) -> bool:
    edit = _lingxi_edit(root)
    if edit is None:
        return False
    text = _node_text(edit)
    if not text or text == _LINGXI_PLACEHOLDER:
        return False
    return "".join(query.split()) in "".join(text.split())


def _answer_ready(root: ET.Element, query: str, before: int) -> bool:
    """新的提问气泡已经出现，且这条下面的找图回答不再转圈。"""
    if _text_count(root, query) <= before:
        return False
    bubble_y = _latest_text_y(root, query)
    if bubble_y is None:
        return False
    title_y = _latest_text_y(root, _LINGXI_FOUND)
    if title_y is not None and title_y >= bubble_y - 30:
        return True
    if _lingxi_loading(root):
        return False
    for n in _nodes(root):
        text = _node_text(n)
        if not text:
            continue
        if not any(phrase in text for phrase in ("没有找到", "未找到", "没有相关", "暂无")):
            continue
        _x1, y1, _x2, y2 = _bounds_of(n)
        if y2 > y1 and y1 >= bubble_y - 30:
            return True
    return False


def _focus_lingxi_input(adb: Adb, log) -> tuple[int, int] | None:
    """点灵犀输入框。已有成功坐标就直接点，点不进再重新找。"""
    cached = _lx_cache(adb).get("input")
    if cached is not None:
        log(f"use cached input {cached}")
        _tap_xy(adb, cached)
        time.sleep(0.4)
        return cached
    try:
        root = adb.dump()
    except Exception as exc:
        log(f"lingxi dump fail: {str(exc)[:80]}")
        return None
    edit = _lingxi_edit(root)
    if (edit is None or not edit.get("bounds")) and _voice_composer(root):
        keyboard_at = _switch_voice_to_text(adb, root, log)
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"lingxi dump fail: {str(exc)[:80]}")
            return None
        edit = _lingxi_edit(root)
        if edit is not None and edit.get("bounds"):
            _lx_remember(adb, "keyboard", keyboard_at, log)
        else:
            _lx_drop(adb, "keyboard", log)
    if edit is None or not edit.get("bounds"):
        log("lingxi input not on current page -> abort")
        return None
    field = center(edit.get("bounds") or "")
    log(f"tap lingxi input {field}")
    _tap_xy(adb, field)
    time.sleep(0.4)
    return field


_SETCLIP_JAR = Path(__file__).resolve().parent / "bin" / "setclip.jar"


def _screencap(adb: Adb) -> bytes:
    last = "screencap failed"
    for _attempt in range(2):
        try:
            proc = subprocess.run(
                adb._base() + ["exec-out", "screencap", "-p"],
                capture_output=True,
                timeout=40,
                check=False,
            )
        except subprocess.TimeoutExpired:
            _kill_stuck_screencap(adb)
            last = "screencap timed out"
            continue
        data = proc.stdout or b""
        at = data.find(b"\x89PNG\r\n\x1a\n")
        if at > 0:
            data = data[at:]
        if data.startswith(b"\x89PNG"):
            return data
        last = "screencap failed"
    raise RuntimeError(last)


def _rows_from_raw(data: bytes) -> tuple[int, int, list[bytearray]] | None:
    """screencap 不带 -p 时是宽、高、格式，后面直接是像素，不用解 PNG。"""
    if len(data) < 16:
        return None
    w, h, fmt = struct.unpack_from("<III", data, 0)
    if not (200 <= w <= 5000 and 200 <= h <= 5000 and fmt in (1, 2, 3, 4, 5)):
        return None
    need = w * h * 4
    header = 0
    for cand in (12, 16):
        if abs(len(data) - cand - need) < 64 and len(data) >= cand + need:
            header = cand
            break
    if header == 0:
        return None
    pix = data[header : header + need]
    stride = w * 4
    return w, h, [bytearray(pix[y * stride : (y + 1) * stride]) for y in range(h)]


def _kill_stuck_screencap(adb: Adb) -> None:
    """超时后手机上的 screencap 还占着，下一张会继续卡住。先把它清掉。"""
    adb.run("shell", "toybox killall screencap", t=8)


def _screen_rows(adb: Adb) -> tuple[int, int, list[bytearray]]:
    """走 PNG。原始像素大约 10MB，过 USB 桥要二十多秒，会顶满超时。"""
    w, h, rows = _png_rows(_screencap(adb))
    if rows and len(rows[0]) == w * 3:
        expanded: list[bytearray] = []
        for row in rows:
            out = bytearray(w * 4)
            for x in range(w):
                s = x * 3
                d = x * 4
                out[d] = row[s]
                out[d + 1] = row[s + 1]
                out[d + 2] = row[s + 2]
                out[d + 3] = 255
            expanded.append(out)
        rows = expanded
    return w, h, rows


def _png_rows(data: bytes) -> tuple[int, int, list[bytearray]]:
    pos = 8
    idat = b""
    w = h = 0
    ctype = 2
    while pos + 8 <= len(data):
        ln = struct.unpack(">I", data[pos : pos + 4])[0]
        typ = data[pos + 4 : pos + 8]
        chunk = data[pos + 8 : pos + 8 + ln]
        if typ == b"IHDR":
            w, h = struct.unpack(">II", chunk[:8])
            ctype = chunk[9]
        elif typ == b"IDAT":
            idat += chunk
        elif typ == b"IEND":
            break
        pos += 12 + ln
    raw = zlib.decompress(idat)
    bpp = 4 if ctype == 6 else 3
    stride = w * bpp
    i = 0
    prev = bytearray(stride)
    rows: list[bytearray] = []

    def paeth(a: int, b: int, c: int) -> int:
        p = a + b - c
        pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
        if pa <= pb and pa <= pc:
            return a
        return b if pb <= pc else c

    for _y in range(h):
        filt = raw[i]
        i += 1
        row = bytearray(raw[i : i + stride])
        i += stride
        if filt == 1:
            for x in range(stride):
                row[x] = (row[x] + (row[x - bpp] if x >= bpp else 0)) & 255
        elif filt == 2:
            for x in range(stride):
                row[x] = (row[x] + prev[x]) & 255
        elif filt == 3:
            for x in range(stride):
                left = row[x - bpp] if x >= bpp else 0
                row[x] = (row[x] + ((left + prev[x]) // 2)) & 255
        elif filt == 4:
            for x in range(stride):
                a = row[x - bpp] if x >= bpp else 0
                c = prev[x - bpp] if x >= bpp else 0
                row[x] = (row[x] + paeth(a, prev[x], c)) & 255
        rows.append(row)
        prev = row
    return w, h, rows


def _px(row: bytearray, x: int) -> tuple[int, int, int]:
    i = x * 4
    return row[i], row[i + 1], row[i + 2]


def _is_purple(r: int, g: int, b: int) -> bool:
    return b > 180 and 70 < r < 220 and g < 140 and b > r + 20


def _placeholder_xy(rows: list[bytearray], w: int, h: int) -> tuple[int, int] | None:
    """灵犀 WebView 输入框里的灰色占位文案。必须铺在白底上，避开键盘按键。"""
    best = None
    x1 = int(w * 0.62)
    for y in range(int(h * 0.40), int(h * 0.96), 2):
        xs = []
        dark = 0
        row = rows[y]
        above = rows[max(0, y - 14)]
        for x in range(40, x1):
            i = x * 4
            r, g, b = row[i], row[i + 1], row[i + 2]
            if r < 90 and g < 90 and b < 90:
                dark += 1
            ar, ag, ab = above[i], above[i + 1], above[i + 2]
            if ar <= 245 or ag <= 245 or ab <= 245:
                continue
            if 150 < r < 195 and abs(r - g) < 12 and abs(g - b) < 16:
                xs.append(x)
        if dark > 6:
            continue
        if len(xs) > 40 and (best is None or len(xs) > best[0]):
            best = (len(xs), (xs[0] + xs[-1]) // 2, y)
    if best is None:
        return None
    return best[1], best[2]


def _send_circle(rows: list[bytearray], w: int, h: int) -> tuple[int, int] | None:
    """键盘打开后右侧的紫色圆形发送键。只扫右缘一条紫色色块，不逐窗统计。"""
    best = None
    x0 = int(w * 0.82)
    # 只扫键盘高度。再往上是提问气泡的紫边，不能当成发送键。
    for y in range(int(h * 0.55), int(h * 0.70), 3):
        row = rows[y]
        run = 0
        start = 0
        last = x0
        for x in range(x0, w - 4):
            i = x * 4
            r, g, b = row[i], row[i + 1], row[i + 2]
            if b > 180 and 70 < r < 220 and g < 100 and b > r + 20:
                if run == 0:
                    start = x
                run += 1
                last = x
            else:
                if 32 <= run <= 140 and (best is None or run > best[0]):
                    best = (run, (start + last) // 2, y)
                run = 0
        if 32 <= run <= 140 and (best is None or run > best[0]):
            best = (run, (start + last) // 2, y)
    if best is None:
        return None
    return best[1], best[2]


def _query_bubble_y(rows: list[bytearray], w: int, h: int) -> int | None:
    """发出去之后右侧的紫色提问气泡。返回气泡中线 y。"""
    best = None
    x0 = int(w * 0.40)
    for y in range(int(h * 0.22), int(h * 0.82), 2):
        run = 0
        row = rows[y]
        for x in range(x0, w - 8):
            i = x * 4
            r, g, b = row[i], row[i + 1], row[i + 2]
            if b > 180 and 70 < r < 220 and g < 140 and b > r + 20:
                run += 1
            else:
                if run >= 140 and (best is None or run > best[0]):
                    best = (run, y)
                run = 0
        if run >= 140 and (best is None or run > best[0]):
            best = (run, y)
    if best is None:
        return None
    return best[1]


def _latest_bubble_y(rows: list[bytearray], w: int, h: int) -> int | None:
    """当前这条提问气泡。取输入框上方最靠下的紫色条，避免点到更早的 +N。"""
    ph = _placeholder_xy(rows, w, h)
    # 输入框紫边贴在占位文案上面，不能当成提问气泡。
    limit = ph[1] - 220 if ph is not None else int(h * 0.72)
    best = None
    thick = 0
    y_end = min(int(limit), int(h * 0.78))
    x0 = int(w * 0.45)
    for y in range(int(h * 0.18), max(int(h * 0.18), y_end), 2):
        run = 0
        row = rows[y]
        hit = False
        for x in range(x0, w - 8):
            i = x * 4
            r, g, b = row[i], row[i + 1], row[i + 2]
            if b > 180 and 70 < r < 220 and g < 140 and b > r + 20:
                run += 1
            else:
                if run >= 120:
                    hit = True
                run = 0
        if run >= 120:
            hit = True
        if hit:
            thick += 1
            if thick >= 4:
                best = y
        else:
            thick = 0
    return best


def _bubble_marks(rows: list[bytearray], w: int, h: int) -> list[tuple[int, tuple[int, ...]]]:
    """每条提问气泡的中线，以及气泡里白字的纹理。用来认出还没发出去之前的旧气泡。"""
    ph = _placeholder_xy(rows, w, h)
    limit = ph[1] - 180 if ph is not None else int(h * 0.78)
    y_end = min(int(limit), int(h * 0.82))
    x_from = int(w * 0.42)
    bands: list[tuple[int, int]] = []
    run_y = 0
    start = 0
    last_y = int(h * 0.16)
    for y in range(int(h * 0.16), max(int(h * 0.16) + 1, y_end), 2):
        last_y = y
        row = rows[y]
        wide = 0
        span = 0
        for x in range(x_from, w - 8):
            i = x * 4
            r, g, b = row[i], row[i + 1], row[i + 2]
            if b > 180 and 70 < r < 220 and g < 140 and b > r + 20:
                span += 1
            else:
                if span > wide:
                    wide = span
                span = 0
        if span > wide:
            wide = span
        if wide >= 100:
            if run_y == 0:
                start = y
            run_y += 1
        elif run_y >= 3:
            bands.append((start, y))
            run_y = 0
        else:
            run_y = 0
    if run_y >= 3:
        bands.append((start, last_y))
    marks: list[tuple[int, tuple[int, ...]]] = []
    for y0, y1 in bands:
        best_y = None
        best_white = -1
        for y in range(y0, y1 + 1, 2):
            row = rows[y]
            whites = 0
            for x in range(x_from, w - 8, 4):
                i = x * 4
                r, g, b = row[i], row[i + 1], row[i + 2]
                if r + g + b > 600 and b > 180 and r > 180:
                    whites += 1
            if whites > best_white:
                best_white = whites
                best_y = y
        if best_y is None or best_white < 4:
            continue
        row = rows[best_y]
        left = None
        right = None
        for x in range(x_from, w - 8):
            i = x * 4
            r, g, b = row[i], row[i + 1], row[i + 2]
            if b > 180 and 70 < r < 220 and g < 140 and b > r + 20:
                if left is None:
                    left = x
                right = x
        if left is None or right is None or right - left < 80:
            continue
        bits = []
        for x in range(left, right + 1, 4):
            i = x * 4
            r, g, b = row[i], row[i + 1], row[i + 2]
            bits.append(1 if r + g + b > 600 else 0)
        marks.append((best_y, tuple(bits)))
    return marks


def _same_bubble(new_sig: tuple[int, ...], old_sig: tuple[int, ...]) -> bool:
    n = min(len(new_sig), len(old_sig))
    if n < 24:
        return False
    # 气泡靠右。紫底几乎一样，只比白字，避免把新句子认成旧气泡。
    left_new = new_sig[-n:]
    left_old = old_sig[-n:]
    text_at = [i for i in range(n) if left_new[i] or left_old[i]]
    if len(text_at) < 8:
        return False
    diff = sum(1 for i in text_at if left_new[i] != left_old[i])
    return diff / len(text_at) <= 0.22


def _photo_tiles(rows: list[bytearray], w: int, h: int, below_y: int) -> list[tuple[int, int]]:
    """气泡下面的彩色缩略图，从左到右只取最上面一行的中心。白字和紫气泡不算。"""
    y0 = below_y + 50
    y1 = min(int(h * 0.78), below_y + 700)
    if y1 <= y0 + 100:
        return []

    def _photo(r: int, g: int, b: int) -> bool:
        if r > 242 and g > 242 and b > 242:
            return False
        if _is_purple(r, g, b):
            return False
        return max(r, g, b) - min(r, g, b) > 28 and max(r, g, b) > 60

    col = [0] * w
    for y in range(y0, y1, 3):
        row = rows[y]
        for x in range(12, w - 12):
            r, g, b = _px(row, x)
            if _photo(r, g, b):
                col[x] += 1
    spans: list[tuple[int, int]] = []
    x = 12
    while x < w - 12:
        if col[x] < 18:
            x += 1
            continue
        start = x
        while x < w - 12 and col[x] >= 18:
            x += 1
        if x - start >= 70:
            spans.append((start, x))
        x += 1
    tiles: list[tuple[int, int]] = []
    for x1, x2 in spans:
        top = None
        bot = None
        for y in range(y0, y1, 2):
            row = rows[y]
            hit = 0
            for px in range(x1, x2, 3):
                r, g, b = _px(row, px)
                if _photo(r, g, b):
                    hit += 1
            if hit >= max(4, (x2 - x1) // 18):
                if top is None:
                    top = y
                bot = y
            elif top is not None and bot is not None and y > bot + 16:
                break
        if top is None or bot is None or bot - top < 70:
            continue
        tiles.append(((x1 + x2) // 2, (top + bot) // 2))
    return tiles[:6]


def _answer_grid_corners(
    rows: list[bytearray], w: int, h: int, below_y: int
) -> tuple[tuple[int, int], tuple[int, int]] | None:
    """当前回答缩略图格子的右下角和左上角。

    右下角用最右一列的 x、最下一行的 y。那里是 +N 时点开会进相册，
    是照片时点开会进大图。
    """
    y0 = below_y + 30
    y1 = min(int(h * 0.80), below_y + 820)
    if y1 <= y0 + 80:
        return None

    def _ink(r: int, g: int, b: int) -> bool:
        # 白底不算。斑马和灰色 +N 色彩很低，也要算进格子。
        if r > 236 and g > 236 and b > 236:
            return False
        if _is_purple(r, g, b):
            return False
        return True

    col = [0] * w
    for y in range(y0, y1, 3):
        row = rows[y]
        for x in range(12, w - 12):
            r, g, b = _px(row, x)
            if _ink(r, g, b):
                col[x] += 1
    spans: list[tuple[int, int]] = []
    x = 12
    while x < w - 12:
        if col[x] < 12:
            x += 1
            continue
        start = x
        while x < w - 12 and col[x] >= 12:
            x += 1
        if x - start >= 50:
            spans.append((start, x))
        x += 1
    cells: list[tuple[int, int]] = []
    for x1, x2 in spans:
        top = None
        last = None
        for y in range(y0, y1):
            row = rows[y]
            hit = 0
            for px in range(x1, x2, 4):
                r, g, b = _px(row, px)
                if _ink(r, g, b):
                    hit += 1
            if hit >= max(3, (x2 - x1) // 24):
                if top is None:
                    top = y
                last = y
            elif top is not None and last is not None and y > last + 12:
                if last - top >= 60:
                    cells.append(((x1 + x2) // 2, (top + last) // 2))
                top = None
                last = None
        if top is not None and last is not None and last - top >= 60:
            cells.append(((x1 + x2) // 2, (top + last) // 2))
    if not cells:
        return None
    cols: list[int] = []
    for cx, _cy in sorted(cells):
        if not cols or cx - cols[-1] > 50:
            cols.append(cx)
    row_ys: list[int] = []
    for _cx, cy in sorted(cells, key=lambda p: p[1]):
        if not row_ys or cy - row_ys[-1] > 50:
            row_ys.append(cy)
    top_left = (cols[0], row_ys[0])
    bottom_right = (cols[-1], row_ys[-1])
    return bottom_right, top_left


def _lingxi_title_visible(root: ET.Element) -> bool:
    for n in _nodes(root):
        if _node_text(n) != "灵犀":
            continue
        x1, y1, x2, y2 = _bounds_of(n)
        if x2 > x1 and y2 > y1 and 0 <= y1 < 400:
            return True
    return False


def _ime_shown(adb: Adb) -> bool:
    out = adb.run("shell", "dumpsys", "input_method") or ""
    return "mInputShown=true" in out


def _visible_ime(adb: Adb, log) -> None:
    """WebView 不绑定 ADB Keyboard。改用系统里能弹出的输入法，再粘贴文字。"""
    cur = (adb.run("shell", "settings", "get", "secure", "default_input_method") or "").strip()
    if "adbkeyboard" not in cur.lower():
        return
    listed = adb.run("shell", "ime", "list", "-s") or ""
    for line in listed.splitlines():
        ime = line.strip()
        if ime and "adbkeyboard" not in ime.lower():
            adb.run("shell", "ime", "set", ime)
            log(f"webview ime {ime}")
            time.sleep(0.3)
            return


def _paste_text(adb: Adb, text: str) -> None:
    local = Path(tempfile.gettempdir()) / f"mcloud_clip_{adb.serial or 'x'}.txt"
    local.write_text(text, encoding="utf-8")
    remote = "/data/local/tmp/mcloud_query.txt"
    adb.run("push", str(local), remote, t=20)
    key = adb.serial or ""
    if key not in _SETCLIP_PUSHED:
        adb.run("push", str(_SETCLIP_JAR), "/data/local/tmp/setclip.jar", t=20)
        _SETCLIP_PUSHED.add(key)
    adb.run(
        "shell",
        "mkdir -p /data/local/tmp/dalvik-cache; "
        "ANDROID_DATA=/data/local/tmp "
        "CLASSPATH=/data/local/tmp/setclip.jar "
        "app_process /system/bin SetClip /data/local/tmp/mcloud_query.txt",
        t=30,
    )
    adb.run("shell", "input", "keyevent", "279")


def _tap_composer(adb: Adb, log) -> tuple[int, int] | None:
    """点输入框。有上次成功的坐标就直接点，点不进再按截图找占位文案。"""
    cached = _lx_cache(adb).get("input")
    if cached is not None:
        log(f"use cached input {cached}")
        _tap_xy(adb, cached)
        time.sleep(0.45)
        if _ime_shown(adb):
            return cached
        log("cached input missed, relocate")
        _lx_drop(adb, "input", log)
    w, h, rows = _screen_rows(adb)
    field = _placeholder_xy(rows, w, h)
    if field is None:
        field = (int(w * 0.22), int(h * 0.86))
        log(f"placeholder not seen, tap fallback {field}")
    else:
        log(f"tap webview composer {field}")
    _tap_xy(adb, field)
    time.sleep(0.45)
    if not _ime_shown(adb):
        log("webview composer did not focus")
        return None
    return field


def _clear_composer_text(adb: Adb) -> None:
    """光标移到末尾再删除。上一句没发出去时，不能把下一句接在后面。"""
    adb.run("shell", "input keyevent 123; input keyevent " + " ".join(["67"] * 120))


def _webview_lingxi_search(adb: Adb, query: str, log, stop_flag=None) -> dict:
    """Mate 40 灵犀画在 WebView 里，没有原生输入框。点占位文案，粘贴，再点紫色发送。"""
    _WEBVIEW_SERIALS.add(adb.serial or "")
    _visible_ime(adb, log)
    try:
        ow, oh, orows = _screen_rows(adb)
        old_marks = _bubble_marks(orows, ow, oh)
    except Exception:
        old_marks = []
    field = _tap_composer(adb, log)
    if field is None:
        return {"ok": False, "latency_ms": None, "input_failed": True}
    try:
        w, h, rows = _screen_rows(adb)
        empty = _placeholder_xy(rows, w, h) is not None
    except Exception as exc:
        log(f"composer shot fail: {str(exc)[:60]}")
        empty = False
    if not empty:
        log("composer not empty, clear before paste")
        _clear_composer_text(adb)
        time.sleep(0.2)
    _paste_text(adb, query)
    time.sleep(0.3)
    w, h, rows = _screen_rows(adb)
    if _placeholder_xy(rows, w, h) is not None:
        log("paste missed, retry once")
        _tap_xy(adb, field)
        time.sleep(0.3)
        _paste_text(adb, query)
        time.sleep(0.3)
        w, h, rows = _screen_rows(adb)
        if _placeholder_xy(rows, w, h) is not None:
            log("query not in webview field")
            return {"ok": False, "latency_ms": None, "input_failed": True}
    send_at = _lx_cache(adb).get("send")
    if send_at is not None:
        log(f"use cached send {send_at}")
    else:
        send_at = _send_circle(rows, w, h)
        if send_at is None:
            log("webview send button not found")
            return {"ok": False, "latency_ms": None, "input_failed": True}
        log(f"tap webview send {send_at}")
    t0 = time.perf_counter()
    _tap_xy(adb, send_at)
    ready = False
    bubble_at = None
    latched_y = None
    while time.perf_counter() - t0 < 50.0:
        if _stopped(adb, stop_flag):
            return {
                "ok": False,
                "latency_ms": int(round((time.perf_counter() - t0) * 1000)),
                "stopped": True,
            }
        try:
            w, h, rows = _screen_rows(adb)
        except Exception as exc:
            log(f"shot fail: {str(exc)[:60]}")
            time.sleep(0.35)
            continue
        # 只用这次新发出的气泡。同一句再发一次时，位置会更靠下，也算新的。
        fresh = []
        for y, sig in _bubble_marks(rows, w, h):
            matched = False
            for old_y, old in old_marks:
                if _same_bubble(sig, old) and y <= old_y + 80:
                    matched = True
                    break
            if not matched:
                fresh.append(y)
        if fresh:
            latched_y = fresh[-1]
            if bubble_at is None:
                bubble_at = time.perf_counter()
                log("webview query bubble shown")
        # 认中一次就继续看这条气泡下面。后一帧纹理对上旧气泡时，不能把已经出来的图丢掉。
        # 键盘还开着时，输入框紫边也会被看成气泡。占位文案回来才说明已经发出。
        if latched_y is not None and _placeholder_xy(rows, w, h) is not None:
            look_y = fresh[-1] if fresh else latched_y
            if _photo_tiles(rows, w, h, look_y):
                ready = True
                log("webview photo tiles shown")
                break
            if bubble_at is not None and time.perf_counter() - bubble_at > 12.0:
                ready = True
                log("webview bubble held, no tiles yet")
                break
        time.sleep(0.35)
    if not ready:
        _lx_drop(adb, "send", log)
        log("webview answer not shown")
        return {"ok": False, "latency_ms": int(round((time.perf_counter() - t0) * 1000)), "input_failed": True}
    latency_ms = int(round((time.perf_counter() - t0) * 1000))
    _lx_remember(adb, "input", field, log)
    _lx_remember(adb, "send", send_at, log)
    log(f"mcloud search latency_ms={latency_ms}")
    return {"ok": True, "latency_ms": latency_ms}


def mcloud_input_query_and_search(adb: Adb, query: str, log, stop_flag=None) -> dict:
    """在当前灵犀页输入「查找…的照片」并发送，等到这条回答出现。"""
    key = adb.serial or ""
    if key in _WEBVIEW_SERIALS:
        return _webview_lingxi_search(adb, query, log, stop_flag)
    try:
        root = adb.dump()
    except Exception as exc:
        log(f"lingxi dump fail: {str(exc)[:80]}")
        root = None
    # 欢迎页的「灵犀」画在 WebView 里。dump 失败或树里没有输入框时，仍走粘贴。
    on_menu = "MenuActivity" in focus_blob(adb)
    has_edit = root is not None and _lingxi_edit(root) is not None
    has_title = root is not None and _lingxi_title_visible(root)
    if (on_menu or has_title) and not has_edit:
        _WEBVIEW_SERIALS.add(key)
        return _webview_lingxi_search(adb, query, log, stop_flag)
    prev = ensure_ime(adb)
    try:
        field = _focus_lingxi_input(adb, log)
        if field is None:
            return {"ok": False, "latency_ms": None, "input_failed": True}
        try:
            before_root = adb.dump()
        except Exception as exc:
            log(f"lingxi dump fail: {str(exc)[:80]}")
            return {"ok": False, "latency_ms": None, "input_failed": True}
        before = _text_count(before_root, query)

        payload = base64.b64encode(query.encode("utf-8")).decode("ascii")
        ok = False
        latency_ms = None
        send_at: tuple[int, int] | None = None
        for attempt in range(3):
            if _stopped(adb, stop_flag):
                return {"ok": False, "latency_ms": None, "stopped": True}
            adb.run("shell", "am", "broadcast", "-a", "ADB_CLEAR_TEXT")
            time.sleep(0.35)
            out = adb.run(
                "shell", "am", "broadcast",
                "-a", "ADB_INPUT_B64", "--es", "msg", payload,
            )
            if attempt == 0:
                log(f"input {(out or '')[:100]}")
            time.sleep(0.8)
            try:
                typed = adb.dump()
            except Exception as exc:
                log(f"input dump fail: {str(exc)[:80]}")
                continue
            if not _field_has_query(typed, query):
                log(f"query not in lingxi field, retry #{attempt}")
                _lx_drop(adb, "input", log)
                edit = _lingxi_edit(typed)
                if edit is not None and edit.get("bounds"):
                    field = center(edit.get("bounds") or "")
                    _tap_xy(adb, field)
                time.sleep(0.4)
                continue

            t0 = time.perf_counter()
            send_at = _lx_cache(adb).get("send")
            if send_at is not None:
                log(f"use cached send {send_at}")
            else:
                send_at = _composer_send(typed)
                if send_at is not None:
                    log(f"tap lingxi send {send_at}")
            if send_at is not None:
                _tap_xy(adb, send_at)
            else:
                log("no send button, press enter")
                adb.run("shell", "input", "keyevent", "KEYCODE_ENTER")

            while time.perf_counter() - t0 < 50.0:
                if _stopped(adb, stop_flag):
                    return {
                        "ok": False,
                        "latency_ms": int(round((time.perf_counter() - t0) * 1000)),
                        "stopped": True,
                    }
                try:
                    root = adb.dump()
                except Exception as exc:
                    log(f"search poll dump fail (skip): {str(exc)[:80]}")
                    time.sleep(0.4)
                    continue
                if _answer_ready(root, query, before):
                    ok = True
                    latency_ms = int(round((time.perf_counter() - t0) * 1000))
                    _hide_keyboard(adb)
                    break
                time.sleep(0.6)
            if ok:
                _lx_remember(adb, "input", field, log)
                if send_at is not None:
                    _lx_remember(adb, "send", send_at, log)
                if attempt:
                    log(f"lingxi answer ready on retry #{attempt}")
                break
            log(f"lingxi answer not ready #{attempt}")
            if _lx_cache(adb).get("send") is not None:
                _lx_drop(adb, "send", log)

        if not ok:
            log("lingxi search failed -> abort")
            return {"ok": False, "latency_ms": latency_ms, "input_failed": True}
        log(f"mcloud search latency_ms={latency_ms}")
        return {"ok": True, "latency_ms": latency_ms}
    finally:
        restore_ime(adb, prev)


# ---- 结果页 → 大图 ----

def _image_cells(root: ET.Element) -> list[dict]:
    """结果里可点开的图片。灵犀页只取最新一条找图回答下面的格子。"""
    if _latest_text_y(root, _LINGXI_FOUND) is not None:
        return _lingxi_image_cells(root)
    container = _node_by_id(root, "/rv_images")
    if container is None:
        return []
    scope = list(container.iter("node"))
    cells: list[dict] = []
    for n in scope:
        if n.get("clickable") != "true":
            continue
        b = n.get("bounds") or ""
        x1, y1, x2, y2 = parse_bounds(b)
        if (x2 - x1) < 120 or (y2 - y1) < 120:
            continue
        # 排除含「+N」/ tv_more 的更多格
        more = False
        for d in n.iter("node"):
            rid = d.get("resource-id") or ""
            txt = (d.get("text") or "").strip()
            if rid.endswith("/tv_more") or re.fullmatch(r"\+\d+", txt):
                more = True
                break
        if more:
            continue
        cells.append({"cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2, "y1": y1, "x1": x1})
    cells.sort(key=lambda c: (c["y1"], c["x1"]))
    return cells


def _loading_placeholder(root: ET.Element) -> bool:
    """结果页是否仍处于「正在加载更多精彩~」占位态。"""
    for n in _nodes(root):
        t = (n.get("text") or "").strip()
        if t and ("正在加载" in t or "加载更多" in t):
            return True
    return False


def _wait_images_ready(
    adb: Adb, log, timeout: float = 15.0
) -> tuple[bool, ET.Element | None]:
    """采集前等图片网格真正渲染出来，避免抢跑点到返回键。

    判据：出现 /rv_images 或 /tv_images_title。
    若结果页已就绪（有 cl_result_tab）且不再显示「正在加载」占位、连续两轮都
    看不到图片容器，则判为真·无图并提前返回，避免空等满超时。
    返回 (是否就绪, 最后一次 dump 的 root)。
    """
    t0 = time.perf_counter()
    root: ET.Element | None = None
    settled = 0
    while time.perf_counter() - t0 < timeout:
        if adb.halted:
            return False, root
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"image-ready dump fail: {str(exc)[:80]}")
            time.sleep(0.5)
            continue
        if _lingxi_image_cells(root):
            log(f"image grid ready in {time.perf_counter() - t0:.1f}s")
            return True, root
        if (
            _latest_text_y(root, _LINGXI_FOUND) is not None
            and not _lingxi_loading(root)
        ):
            settled += 1
            if settled >= 2:
                log("lingxi answer settled with no image grid -> empty")
                return False, root
            time.sleep(0.6)
            continue
        if (
            _node_by_id(root, "/rv_images") is not None
            or _node_by_id(root, "/tv_images_title") is not None
        ):
            log(f"image grid ready in {time.perf_counter() - t0:.1f}s")
            return True, root
        if _node_by_id(root, "/cl_result_tab") is not None and not _loading_placeholder(root):
            settled += 1
            if settled >= 2:
                log("result page settled with no image grid -> empty")
                return False, root
        else:
            settled = 0
        time.sleep(0.6)
    log(f"image grid not ready within {timeout:.0f}s -> empty")
    return False, root


def mcloud_open_first_image(adb: Adb, log) -> bool:
    """结果页点第一张普通图片单元 → AlbumImagePreviewActivity。"""
    ready, _ = _wait_images_ready(adb, log)
    if not ready:
        return False
    for i in range(6):
        if adb.halted:
            return False
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"dump fail: {str(exc)[:80]}")
            time.sleep(0.5)
            continue
        cells = _image_cells(root)
        if cells:
            xy = (cells[0]["cx"], cells[0]["cy"])
            log(f"tap first image cell {xy} (cells={len(cells)})")
            _tap_xy(adb, xy)
            time.sleep(1.4)
            if on_mcloud_preview(adb):
                return True
            time.sleep(0.8)
            if on_mcloud_preview(adb):
                return True
            log("preview not opened after tap; retry")
            continue
        if _result_visible(root):
            log("result page has no image cells -> empty")
            return False
        log(f"scroll for image block #{i}")
        adb.run("shell", "input", "swipe", "540", "1600", "540", "700", "300")
        time.sleep(1.0)
    return False


def _leave_preview(adb: Adb) -> None:
    for _ in range(4):
        if not on_mcloud_preview(adb):
            return
        adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.7)


SWIPE_NEXT_MS = 80  # 正常翻页：快速 flick，贴合列表翻页手感
SWIPE_RETRY_MS = 250  # 未翻页重试用的放慢手势（降回弹）
SWIPE_REPEAT_LIMIT = 2  # 连续读到同一张这么多次就判定到底；中间只再慢滑一次


def _mcloud_next(adb: Adb, slow: bool = False, known_preview: bool = False) -> None:
    """关掉「更多」弹窗并左滑下一张。

    合并为一次 adb 往返（BACK; sleep; input swipe），省掉一次往返与两段 host 等待；
    swipe 按屏幕比例且避开左右边缘，防止触发系统侧滑返回。
    正常用 SWIPE_NEXT_MS 快速 flick；检测到未翻页时传 slow=True 用 SWIPE_RETRY_MS 放慢重试。
    已经不在大图页时不再按返回，避免把灵犀结果页退回云盘首页。
    known_preview 为真时不再查一次焦点：调用方刚从这张大图读到了文件名。
    """
    if not known_preview and not on_mcloud_preview(adb):
        return
    w, h = _screen_size(adb)
    x1 = int(w * 0.80)
    x2 = int(w * 0.20)
    y = int(h * 0.5)
    ms = SWIPE_RETRY_MS if slow else SWIPE_NEXT_MS
    adb.run(
        "shell",
        f"input keyevent KEYCODE_BACK; sleep 0.15; "
        f"input swipe {x1} {y} {x2} {y} {ms}",
    )
    time.sleep(0.2)


def _read_popup_name(root: ET.Element) -> str | None:
    """从「更多」弹窗 dump 里取文件名：text_file 完整路径优先，text_name 兜底。"""
    full = _text_by_id(root, "/text_file")
    if full:
        n = full.strip().replace("\\", "/").rsplit("/", 1)[-1].strip()
        if n:
            return n
    nm = _text_by_id(root, "/text_name")
    if nm and _FILENAME_RE.match(nm):
        return nm.strip()
    return None


def _find_more_btn(root: ET.Element) -> tuple[int, int] | None:
    """在预览页 dump 里定位底部「更多」按钮的坐标。"""
    for n in _nodes(root):
        if (n.get("text") or "").strip() != "更多":
            continue
        b = n.get("bounds") or ""
        x1, y1, x2, y2 = parse_bounds(b)
        if (x2 - x1) >= 40 and y1 > 1200:
            return center(b)
    return None


def _mcloud_read_name(adb: Adb, log) -> str | None:
    """大图页读文件名：点「更多」→ text_file 完整路径取 basename（text_name 兜底）。

    移动云盘「更多」弹窗的 text_name 会中间省略号截断，而 linear_file→text_file
    是完整路径（含完整文件名），故以 text_file 为准。
    提速：直接按固定坐标点「更多」，省掉每张一次「先 dump 找按钮」的定位 dump；
    弹窗 dump 走 stdout（省一次 pull 往返）；只有读不到名字时才回退到
    「dump 定位按钮再点一次」的兜底路径。关弹窗/翻页由调用方 _mcloud_next 负责。
    返回 None 表示读不到（不负责离开弹窗/大图页）。
    """
    w, h = _screen_size(adb)
    cached_more = _lx_cache(adb).get("more")
    more = cached_more or (int(w * 0.9), int(h * 0.93))

    _tap_xy(adb, more)
    time.sleep(0.2)
    try:
        root = adb.dump_stdout()
    except Exception as exc:
        log(f"more popup dump fail: {str(exc)[:80]}")
        return None
    name = _read_popup_name(root)

    if not name:
        # 兜底：缓存或比例坐标没命中「更多」→ dump 定位真实按钮再点一次
        if cached_more is not None:
            _lx_drop(adb, "more", log)
        if not on_mcloud_preview(adb):
            # 不在大图页：直接放弃（由调用方决定后续导航）
            log("not on preview while reading name")
            return None
        btn = _find_more_btn(root) or (int(w * 0.9), int(h * 0.93))
        log(f"name not read -> retry 更多 at {btn}")
        _tap_xy(adb, btn)
        time.sleep(0.5)
        try:
            root = adb.dump_stdout()
            name = _read_popup_name(root)
        except Exception as exc:
            log(f"more popup dump fail#2: {str(exc)[:80]}")
        if name:
            more = btn

    if not name:
        shown = " | ".join(
            (n.get("text") or "").strip()
            for n in _nodes(root)
            if (n.get("text") or "").strip()
        )
        log(f"no filename in more popup: {shown[:180]}")
        return None
    _lx_remember(adb, "more", more, log)
    return name


def _find_plus_more_xy(root: ET.Element) -> tuple[int, int] | None:
    """结果页 +N（tv_more）可点格子中心；无则 None。"""
    container = _node_by_id(root, "/rv_images")
    if container is None:
        return None
    for n in container.iter("node"):
        if n.get("clickable") != "true":
            continue
        more = False
        for d in n.iter("node"):
            rid = d.get("resource-id") or ""
            txt = (d.get("text") or "").strip()
            if rid.endswith("/tv_more") or re.fullmatch(r"\+\d+", txt):
                more = True
                break
        if not more:
            continue
        b = n.get("bounds") or ""
        x1, y1, x2, y2 = parse_bounds(b)
        if (x2 - x1) < 80 or (y2 - y1) < 80:
            continue
        return center(b)
    return None


def _parse_relate_total(root: ET.Element) -> int | None:
    """从「相关图片(61)」/「相关图片（61）」一类标题解析总数。"""
    for n in _nodes(root):
        t = (n.get("text") or "").strip()
        m = re.search(r"相关图片\s*[(（]\s*(\d+)\s*[)）]", t)
        if m:
            try:
                return int(m.group(1))
            except ValueError:
                return None
    return None


def _parse_result_image_total(root: ET.Element) -> int | None:
    """结果页「共61个与xxx相关的图片」."""
    for n in _nodes(root):
        t = (n.get("text") or "").strip()
        m = re.search(r"共\s*(\d+)\s*个", t)
        if m:
            try:
                return int(m.group(1))
            except ValueError:
                return None
    return None


def _relate_album_thumbs(adb: Adb, root: ET.Element) -> list[dict]:
    """相关图片页**屏幕内**可见缩略图（按行优先；排除屏外节点以免点飞）。"""
    _sw, sh = _screen_size(adb)
    top_cut = 280
    bottom_cut = max(sh - 40, top_cut + 200)
    cells: list[dict] = []
    for n in _nodes(root):
        if n.get("clickable") != "true":
            continue
        b = n.get("bounds") or ""
        x1, y1, x2, y2 = parse_bounds(b)
        w, h = x2 - x1, y2 - y1
        if w < 140 or h < 140 or w > 900:
            continue
        if y1 < top_cut or y2 > bottom_cut:
            continue
        cells.append(
            {
                "cx": (x1 + x2) // 2,
                "cy": (y1 + y2) // 2,
                "y1": y1,
                "x1": x1,
                "key": f"{x1},{y1},{x2},{y2}",
            }
        )
    cells.sort(key=lambda c: (c["y1"], c["x1"]))
    return cells


def mcloud_open_relate_album(adb: Adb, log) -> bool:
    """结果页点 +N → RelateAlbumImagesActivity。"""
    if on_mcloud_relate_album(adb):
        return True
    if on_mcloud_preview(adb):
        _leave_preview(adb)
        time.sleep(0.8)
    ready, root = _wait_images_ready(adb, log)
    if not ready:
        return False
    try:
        root = root or adb.dump()
    except Exception as exc:
        log(f"relate dump fail: {str(exc)[:80]}")
        return False
    plus = _find_plus_more_xy(root)
    if not plus:
        log("no +N tile on result page")
        return False
    log(f"tap +N more tile {plus}")
    _tap_xy(adb, plus)
    time.sleep(2.0)
    for _ in range(8):
        if on_mcloud_relate_album(adb):
            log("opened RelateAlbumImagesActivity")
            return True
        time.sleep(0.4)
    log(f"tap +N but not RelateAlbum: {focus_blob(adb)[:120]}")
    return False


def mcloud_collect_relate_album(
    adb: Adb,
    log,
    stop_flag=None,
    max_items: int = 60,
    target_total: int | None = None,
) -> dict:
    """相关图片页：滚动列表 + 逐张点开预览读文件名（不依赖左滑全量）。

    实测：相册大图左滑只会遍历已加载的一小段（约十几张）就循环；
    逐缩略图点开才能接近「相关图片(N)」标题中的全量。
    """
    records: list[dict] = []
    seen: set[str] = set()
    tried_keys: set[str] = set()
    status = "failed"

    def _stopped() -> bool:
        return bool(adb.halted or (stop_flag is not None and stop_flag()))

    if not on_mcloud_relate_album(adb):
        if not mcloud_open_relate_album(adb, log):
            return {"records": records, "status": "empty"}

    try:
        root0 = adb.dump()
        title_n = _parse_relate_total(root0)
        if title_n:
            target_total = title_n
    except Exception as exc:
        log(f"relate album dump fail: {str(exc)[:80]}")
        if target_total is None:
            return {"records": records, "status": "failed"}
    # 标题/结果页全量优先于调用方 max_items（默认 60）
    limit = max(int(max_items or 0), int(target_total or 0))
    limit = min(max(limit, 1), 200)
    log(f"relate album target_total={target_total} collect_limit={limit}")

    stagnant_pages = 0
    for page in range(50):
        if _stopped():
            status = "stopped"
            break
        if len(records) >= limit:
            status = (
                "capped"
                if (not target_total or len(records) < target_total)
                else "exhausted"
            )
            break
        if target_total and len(records) >= target_total:
            status = "exhausted"
            log(f"reached title total {target_total}")
            break
        if not on_mcloud_relate_album(adb):
            if on_mcloud_preview(adb):
                _leave_preview(adb)
                time.sleep(0.6)
            # 误点离开时尝试 BACK 回相册
            if not on_mcloud_relate_album(adb):
                adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(0.8)
            if not on_mcloud_relate_album(adb):
                log(f"left relate album at page#{page}")
                status = "exhausted" if records else "failed"
                break
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"relate dump fail#{page}: {str(exc)[:80]}")
            stagnant_pages += 1
            if stagnant_pages >= 3:
                status = "exhausted" if records else "failed"
                break
            continue
        cells = _relate_album_thumbs(adb, root)
        log(f"relate page#{page} thumbs={len(cells)} collected={len(records)}")
        new_this_page = 0
        for cell in cells:
            if _stopped():
                status = "stopped"
                break
            if len(records) >= limit:
                status = (
                    "capped"
                    if (not target_total or len(records) < target_total)
                    else "exhausted"
                )
                break
            if target_total and len(records) >= target_total:
                status = "exhausted"
                break
            key = cell["key"]
            if key in tried_keys:
                continue
            tried_keys.add(key)
            _tap_xy(adb, (cell["cx"], cell["cy"]))
            time.sleep(1.15)
            if not on_mcloud_preview(adb):
                time.sleep(0.7)
            if not on_mcloud_preview(adb):
                log(f"thumb tap no preview @ {cell['cx']},{cell['cy']}")
                # 若点飞到别的页面，BACK 回相册
                if not on_mcloud_relate_album(adb):
                    adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
                    time.sleep(0.7)
                continue
            name = _mcloud_read_name(adb, log)
            _leave_preview(adb)
            time.sleep(0.45)
            if not on_mcloud_relate_album(adb):
                adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(0.6)
            if not name:
                log("thumb unread name")
                continue
            if name in seen:
                continue
            seen.add(name)
            records.append({"pos": len(records) + 1, "name": name})
            new_this_page += 1
            log(f"saved #{len(records)} {name}")
        if status in ("stopped", "capped", "exhausted"):
            break
        if new_this_page == 0:
            stagnant_pages += 1
            if stagnant_pages >= 3:
                log("no new names after 3 scrolls -> done")
                status = "exhausted" if records else "failed"
                break
        else:
            stagnant_pages = 0
        # 向下翻一屏加载更多缩略图
        adb.run("shell", "input", "swipe", "540", "1650", "540", "700", "380")
        time.sleep(0.9)

    if status == "failed" and records:
        status = "exhausted"
    if on_mcloud_preview(adb):
        _leave_preview(adb)
    return {"records": records, "status": status}

def _voice_composer(root: ET.Element) -> bool:
    for n in _nodes(root):
        rid = n.get("resource-id") or ""
        text = _node_text(n)
        if rid == "recorder-input" or text == "按住 说话":
            x1, y1, x2, y2 = _bounds_of(n)
            if x2 > x1 and y2 > y1:
                return True
    return False


def _switch_voice_to_text(adb: Adb, root: ET.Element, log) -> tuple[int, int]:
    """语音条时点右侧键盘图标，回到文字输入。"""
    cached = _lx_cache(adb).get("keyboard")
    if cached is not None:
        log(f"use cached keyboard {cached}")
        _tap_xy(adb, cached)
        time.sleep(0.8)
        return cached
    target = None
    for n in _nodes(root):
        if not (n.get("class") or "").endswith("ImageButton"):
            continue
        if n.get("clickable") != "true":
            continue
        blob = n.get("text") or ""
        if blob == "ai_camera" or "5b2i54q257uT5ZCI" in blob:
            continue
        xy = center(n.get("bounds") or "")
        if xy[1] < 2000 or xy[0] < 880:
            continue
        target = xy
    if target is None:
        target = (955, 2190)
    log(f"voice mode, tap keyboard {target}")
    _tap_xy(adb, target)
    time.sleep(0.8)
    return target


def _plus_for_query(root: ET.Element, query: str | None) -> tuple[int, int] | None:
    """只点当前这条回答下面的 +N，不点更早的结果。"""
    bubble_y = _latest_text_y(root, query) if query else None
    title_y = None
    for n in _nodes(root):
        if _node_text(n) != _LINGXI_FOUND:
            continue
        x1, y1, x2, y2 = _bounds_of(n)
        if x2 <= x1 or y2 <= y1:
            continue
        if bubble_y is not None and y1 < bubble_y - 30:
            continue
        if title_y is None or y1 > title_y:
            title_y = y1
    if title_y is None and bubble_y is not None:
        return None
    best_y = -1
    best: tuple[int, int] | None = None
    for n in _nodes(root):
        if not re.fullmatch(r"\+\d+", _node_text(n)):
            continue
        x1, y1, x2, y2 = _bounds_of(n)
        if x2 <= x1 or y2 <= y1 or y1 < 200:
            continue
        if title_y is not None and not (title_y < y1 < title_y + 560):
            continue
        if y1 >= best_y:
            best_y = y1
            best = center(n.get("bounds") or "")
    return best


def _on_plus_album(adb: Adb) -> bool:
    blob = focus_blob(adb)
    if on_mcloud_relate_album(adb) or "AiSearchImageActivity" in blob:
        return True
    try:
        return _parse_relate_total(adb.dump()) is not None
    except Exception:
        return False


def _album_grid_thumbs(adb: Adb, root: ET.Element) -> list[dict]:
    """相关图片缩略图。第一行大约 y=240，不能按 280 裁掉。"""
    _sw, sh = _screen_size(adb)
    cells: list[dict] = []
    for n in _nodes(root):
        if n.get("clickable") != "true":
            continue
        x1, y1, x2, y2 = _bounds_of(n)
        width, height = x2 - x1, y2 - y1
        if width < 140 or height < 140 or width > 900:
            continue
        if y1 < 180 or y2 > sh - 40:
            continue
        cells.append({"cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2, "y1": y1, "x1": x1})
    cells.sort(key=lambda c: (c["y1"], c["x1"]))
    return cells


def _open_webview_answer(adb: Adb, log) -> tuple[str, int | None]:
    """网页灵犀：截图点当前格子的右下角。相册或大图都只看 Activity，不整页 dump。"""
    corner = None
    try:
        w, h, rows = _screen_rows(adb)
        bubble_y = _latest_bubble_y(rows, w, h) or _query_bubble_y(rows, w, h)
        found = _answer_grid_corners(
            rows, w, h, bubble_y if bubble_y is not None else int(h * 0.35)
        )
        if found is not None:
            corner, _first = found
    except Exception as exc:
        log(f"grid shot fail: {str(exc)[:60]}")
    if corner is not None:
        log(f"tap webview grid bottom-right {corner}")
        _tap_xy(adb, corner)
        page = _wait_page(adb, 3.5)
        if page == "halted":
            return "failed", None
        if page == "album":
            log("bottom-right opened the album")
            try:
                root = adb.dump()
            except Exception as exc:
                log(f"album dump fail: {str(exc)[:80]}")
                return "failed", None
            thumbs = _album_grid_thumbs(adb, root)
            total = _parse_relate_total(root)
            log(f"album total={total} thumbs={len(thumbs)}")
            if not thumbs:
                return "empty", total
            cached = _lx_cache(adb).get("album_first")
            xy = cached or (thumbs[0]["cx"], thumbs[0]["cy"])
            log(f"{'use cached album first' if cached else 'tap first album image'} {xy}")
            _tap_xy(adb, xy)
            if _wait_page(adb, 2.5, accept=("preview",)) == "preview":
                _lx_remember(adb, "album_first", xy, log)
                return "opened", total
            if cached is not None:
                _lx_drop(adb, "album_first", log)
                xy = (thumbs[0]["cx"], thumbs[0]["cy"])
                log(f"tap first album image {xy}")
                _tap_xy(adb, xy)
                if _wait_page(adb, 2.5, accept=("preview",)) == "preview":
                    _lx_remember(adb, "album_first", xy, log)
                    return "opened", total
            log("first album image did not open")
            return "failed", total
        if page == "preview":
            log("bottom-right opened a photo, no plus")
            adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.6)
        else:
            log(f"bottom-right stayed on {page}")
    tiles: list[tuple[int, int]] = []
    try:
        w, h, rows = _screen_rows(adb)
        bubble_y = _latest_bubble_y(rows, w, h) or _query_bubble_y(rows, w, h)
        tiles = _photo_tiles(rows, w, h, bubble_y if bubble_y is not None else int(h * 0.45))
    except Exception as exc:
        log(f"tile shot fail: {str(exc)[:60]}")
    if tiles:
        xy = tiles[0]
        log(f"tap webview first image {xy}")
        _tap_xy(adb, xy)
        if _wait_page(adb, 2.5, accept=("preview",)) == "preview":
            return "opened", None
        log("webview image did not open")
        return "failed", None
    log("no plus and no image cells -> empty")
    return "empty", None


def mcloud_open_answer_preview(adb: Adb, log, query: str | None = None) -> tuple[str, int | None]:
    """打开当前回答的大图。返回 (opened|empty|failed, 相册标题总数)。"""
    if (adb.serial or "") in _WEBVIEW_SERIALS:
        return _open_webview_answer(adb, log)
    plus = None
    cells: list[dict] = []
    root: ET.Element | None = None
    for _ in range(8):
        if adb.halted:
            return "failed", None
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"answer dump fail: {str(exc)[:80]}")
            time.sleep(0.5)
            continue
        plus_xy = _plus_for_query(root, query)
        bubble_y = _latest_text_y(root, query) if query else None
        cells = _lingxi_image_cells(root)
        if bubble_y is not None:
            cells = [c for c in cells if c["y1"] >= bubble_y - 30]
        if plus_xy is not None or cells:
            plus = plus_xy
            break
        time.sleep(0.6)
    if plus is not None:
        log(f"tap plus {plus}")
        _tap_xy(adb, plus)
        time.sleep(1.6)
        opened = False
        for _ in range(8):
            if adb.halted:
                return "failed", None
            if _on_plus_album(adb):
                opened = True
                break
            time.sleep(0.4)
        if not opened:
            log(f"plus did not open album: {focus_blob(adb)[:120]}")
            return "failed", None
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"album dump fail: {str(exc)[:80]}")
            return "failed", None
        thumbs = _album_grid_thumbs(adb, root)
        total = _parse_relate_total(root)
        log(f"album total={total} thumbs={len(thumbs)}")
        if not thumbs:
            return "empty", total
        xy = _lx_cache(adb).get("album_first") or (thumbs[0]["cx"], thumbs[0]["cy"])
        if _lx_cache(adb).get("album_first"):
            log(f"use cached album first {xy}")
        else:
            log(f"tap first album image {xy}")
        _tap_xy(adb, xy)
        time.sleep(1.4)
        if on_mcloud_preview(adb) or _wait_preview(adb):
            _lx_remember(adb, "album_first", xy, log)
            return "opened", total
        if _lx_cache(adb).get("album_first"):
            _lx_drop(adb, "album_first", log)
            xy = (thumbs[0]["cx"], thumbs[0]["cy"])
            log(f"tap first album image {xy}")
            _tap_xy(adb, xy)
            time.sleep(1.4)
            if on_mcloud_preview(adb) or _wait_preview(adb):
                _lx_remember(adb, "album_first", xy, log)
                return "opened", total
        log("first album image did not open")
        return "failed", total
    if plus is None and not cells:
        corner = None
        first = None
        try:
            w, h, rows = _screen_rows(adb)
            bubble_y = _latest_bubble_y(rows, w, h) or _query_bubble_y(rows, w, h)
            found = _answer_grid_corners(
                rows, w, h, bubble_y if bubble_y is not None else int(h * 0.35)
            )
            if found is not None:
                corner, first = found
        except Exception as exc:
            log(f"grid shot fail: {str(exc)[:60]}")
        if corner is not None:
            log(f"tap webview grid bottom-right {corner}")
            _tap_xy(adb, corner)
            time.sleep(1.6)
            opened = False
            for _ in range(8):
                if adb.halted:
                    return "failed", None
                if _on_plus_album(adb):
                    opened = True
                    break
                if on_mcloud_preview(adb):
                    log("bottom-right opened a photo, no plus")
                    adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
                    time.sleep(0.9)
                    break
                time.sleep(0.4)
            if opened:
                log("bottom-right opened the album")
                try:
                    root = adb.dump()
                except Exception as exc:
                    log(f"album dump fail: {str(exc)[:80]}")
                    return "failed", None
                thumbs = _album_grid_thumbs(adb, root)
                total = _parse_relate_total(root)
                log(f"album total={total} thumbs={len(thumbs)}")
                if not thumbs:
                    return "empty", total
                xy = (thumbs[0]["cx"], thumbs[0]["cy"])
                log(f"tap first album image {xy}")
                _tap_xy(adb, xy)
                time.sleep(1.4)
                if on_mcloud_preview(adb) or _wait_preview(adb):
                    _lx_remember(adb, "album_first", xy, log)
                    return "opened", total
                log("first album image did not open")
                return "failed", total
            if first is not None:
                log(f"no album, use first thumbnail {first}")
    if not cells:
        tiles: list[tuple[int, int]] = []
        try:
            w, h, rows = _screen_rows(adb)
            bubble_y = _latest_bubble_y(rows, w, h) or _query_bubble_y(rows, w, h)
            tiles = _photo_tiles(rows, w, h, bubble_y if bubble_y is not None else int(h * 0.45))
        except Exception as exc:
            log(f"tile shot fail: {str(exc)[:60]}")
        if tiles:
            xy = tiles[0]
            log(f"tap webview first image {xy}")
            _tap_xy(adb, xy)
            time.sleep(1.4)
            if on_mcloud_preview(adb) or _wait_preview(adb):
                return "opened", None
            log("webview image did not open")
            return "failed", None
        log("no plus and no image cells -> empty")
        return "empty", None
    log("no plus, open first result image")
    xy = (cells[0]["cx"], cells[0]["cy"])
    log(f"tap first result image {xy}")
    _tap_xy(adb, xy)
    time.sleep(1.4)
    if on_mcloud_preview(adb) or _wait_preview(adb):
        return "opened", None
    log("first result image did not open")
    return "failed", None


def _wait_preview(adb: Adb) -> bool:
    return _wait_page(adb, 2.0) == "preview"


def mcloud_collect_search_order(
    adb: Adb, log, stop_flag=None, max_items: int = 60, query: str | None = None
) -> dict:
    """搜索采集：当前回答有 +N 就点进相册并从第一张左滑；没有就点第一张。

    没有图片时返回 empty，不乱点。
    Returns {"records": [...], "status": looped|exhausted|capped|empty|stopped|failed}
    """
    records: list[dict] = []
    status = "failed"

    def _stopped() -> bool:
        return bool(adb.halted or (stop_flag is not None and stop_flag()))

    seen: set[str] = set()
    stalls = 0
    repeat = 0
    album_total = None
    try:
        opened, album_total = mcloud_open_answer_preview(adb, log, query)
    except Exception as e:
        log(f"FAIL {e}")
        return {"records": records, "status": status}
    if opened != "opened":
        if _stopped():
            log("stop before preview")
            return {"records": records, "status": "stopped"}
        return {"records": records, "status": opened if opened in ("empty", "failed") else "failed"}
    if album_total:
        # 调用方给出的采集数量是上限。相册更大时不再把上限抬到标题总数。
        requested = int(max_items or 0)
        max_items = min(200, int(album_total))
        if requested:
            max_items = min(max_items, requested)

    while True:
        if len(records) >= max_items:
            status = "capped"
            break
        if _stopped():
            status = "stopped"
            break
        name = _mcloud_read_name(adb, log)
        if not name:
            if not on_mcloud_preview(adb):
                log("left preview, stop without extra back")
                status = "exhausted" if records else "failed"
                break
            log("skip unread image")
            stalls += 1
            if stalls >= 3:
                status = "exhausted" if records else "failed"
                break
            _mcloud_next(adb)
            continue
        stalls = 0
        if name in seen:
            # 读到已采过的同一张：可能这次滑动没生效（画面回弹），也可能真的到末尾。
            # 同一张再出现就慢滑一次；第二次仍是这张，判定到底。
            repeat += 1
            if repeat >= SWIPE_REPEAT_LIMIT:
                log(f"looped at #{len(records) + 1} {name} (repeat x{repeat})")
                status = "exhausted" if records else "failed"
                break
            log(f"same image ({repeat}/{SWIPE_REPEAT_LIMIT}) {name} — retry swipe (slow)")
            _mcloud_next(adb, slow=True, known_preview=True)
            continue
        repeat = 0
        seen.add(name)
        records.append({"pos": len(records) + 1, "name": name})
        log(f"saved #{len(records)} {name}")
        if len(records) >= max_items:
            status = "capped"
            break
        if _stopped():
            status = "stopped"
            break
        _mcloud_next(adb, known_preview=True)

    _leave_preview(adb)
    return {"records": records, "status": status}


def mcloud_clear_composer(adb: Adb, log) -> bool:
    """清空灵犀搜索栏。语音模式先切回文字。已经是占位文案则不动。"""
    prev = ensure_ime(adb)
    try:
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"clear dump fail: {str(exc)[:80]}")
            return False
        if _voice_composer(root):
            keyboard_at = _switch_voice_to_text(adb, root, log)
            try:
                root = adb.dump()
            except Exception as exc:
                log(f"clear dump fail: {str(exc)[:80]}")
                return False
            if _lingxi_edit(root) is not None:
                _lx_remember(adb, "keyboard", keyboard_at, log)
            else:
                _lx_drop(adb, "keyboard", log)
        edit = _lingxi_edit(root)
        if edit is None:
            log("clear skip: lingxi input not visible")
            return False
        field = center(edit.get("bounds") or "")
        _lx_remember(adb, "input", field, log)
        text = _node_text(edit)
        if not text or text == _LINGXI_PLACEHOLDER:
            log("search bar already empty")
            return True
        _tap_xy(adb, field)
        time.sleep(0.3)
        adb.run("shell", "am", "broadcast", "-a", "ADB_CLEAR_TEXT")
        time.sleep(0.5)
        try:
            root = adb.dump()
        except Exception as exc:
            log(f"clear verify dump fail: {str(exc)[:80]}")
            return False
        edit = _lingxi_edit(root)
        text = "" if edit is None else _node_text(edit)
        ok = edit is not None and (not text or text == _LINGXI_PLACEHOLDER)
        log(f"cleared search bar ok={ok} field={text!r}")
        return ok
    finally:
        restore_ime(adb, prev)


def mcloud_init_back_and_clear(adb: Adb, log) -> None:
    """用例结束：离开大图和相关图片，回到灵犀，并清空搜索栏。"""
    away = (
        "AlbumImagePreviewActivity",
        "RelateAlbumImagesActivity",
        "CloudMediaPreviewActivity",
        "AiSearchImageActivity",
        "AlbumActivity",
        "SearchGlobalActivity",
    )
    for i in range(8):
        if adb.halted:
            break
        blob = focus_blob(adb)
        if any(name in blob for name in away):
            adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.9)
            log(f"mcloud back #{i}")
            continue
        break
    key = adb.serial or ""
    if key in _WEBVIEW_SERIALS:
        log("webview reset, skip clear")
        return
    on_menu = "MenuActivity" in focus_blob(adb)
    try:
        root = adb.dump()
    except Exception:
        if on_menu:
            _WEBVIEW_SERIALS.add(key)
            log("on lingxi webview, skip clear")
            return
        log("mcloud reset done (dump fail)")
        return
    on_lingxi = _lingxi_edit(root) is not None or any(
        _node_text(n) == "灵犀" and _bounds_of(n)[1] < 400 and _bounds_of(n)[3] > _bounds_of(n)[1]
        for n in _nodes(root)
    )
    if on_menu and _lingxi_edit(root) is None:
        _WEBVIEW_SERIALS.add(key)
        log("on lingxi webview, skip clear")
        return
    if not on_lingxi:
        log("mcloud reset done (lingxi input not visible)")
        return
    if _lingxi_edit(root) is None and _lingxi_title_visible(root):
        _WEBVIEW_SERIALS.add(key)
        log("on lingxi webview, skip clear")
        return
    log("on lingxi")
    mcloud_clear_composer(adb, log)
