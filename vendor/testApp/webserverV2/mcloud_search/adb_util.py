#!/usr/bin/env python3
"""移动云盘（com.chinamobile.mcloud）全局搜索链路。

复用 netdisk_search.adb_util 的通用设施（Adb / 设备端点 / 截图 / IME / 解析 / 点击），
只在这里实现移动云盘专属的 搜索 → 结果 → 大图 → 读文件名 → 翻页 流程。
"""
from __future__ import annotations

import base64
import re
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from netdisk_search.adb_util import (  # noqa: F401  (re-export for blueprint/runner)
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
            for extra in (("--compressed",), ()):
                self.run(
                    "shell",
                    f"rm -f {remote}; uiautomator dump {' '.join(extra)} {remote}".strip(),
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
            for extra in (("--compressed",), ()):
                cmd = (
                    f"rm -f {remote}; uiautomator dump {' '.join(extra)} {remote} "
                    f">/dev/null 2>&1; cat {remote}"
                ).strip()
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


def _result_visible(root: ET.Element) -> bool:
    """结果页判据：只认结果页专属节点（cl_result_tab / rv_images / tv_images_title）。"""
    for n in _nodes(root):
        rid = n.get("resource-id") or ""
        if (
            rid.endswith("/cl_result_tab")
            or rid.endswith("/rv_images")
            or rid.endswith("/tv_images_title")
        ):
            return True
    return False


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

def mcloud_input_query_and_search(adb: Adb, query: str, log, stop_flag=None) -> dict:
    """移动云盘全局搜索：进入搜索页 → 清空 → 输入 → 点搜索 → 等结果页出现。"""
    if not on_mcloud_search(adb):
        mcloud_go_home(adb, log)
        if stop_flag is not None and stop_flag() or adb.halted:
            return {"ok": False, "latency_ms": None, "stopped": True}
        mcloud_open_global_search(adb, log)

    if not on_mcloud_search(adb):
        log("not on mcloud search page -> abort")
        return {"ok": False, "latency_ms": None, "input_failed": True}

    prev = ensure_ime(adb)
    try:
        root = adb.dump()
        field = _xy_by_id(root, "/et_keyword")
        if field is None:
            log("et_keyword not found -> back home & reopen")
            mcloud_go_home(adb, log)
            mcloud_open_global_search(adb, log)
            root = adb.dump()
            field = _xy_by_id(root, "/et_keyword")
        if field is None:
            log("et_keyword still missing -> abort")
            return {"ok": False, "latency_ms": None, "input_failed": True}

        log(f"tap keyword field {field}")
        _tap_xy(adb, field)
        time.sleep(0.6)

        payload = base64.b64encode(query.encode("utf-8")).decode("ascii")
        ok = False
        latency_ms = None
        for attempt in range(3):
            if adb.halted or (stop_flag is not None and stop_flag()):
                return {"ok": False, "latency_ms": None, "stopped": True}
            adb.run("shell", "am", "broadcast", "-a", "ADB_CLEAR_TEXT")
            time.sleep(0.4)
            out = adb.run(
                "shell", "am", "broadcast",
                "-a", "ADB_INPUT_B64", "--es", "msg", payload,
            )
            if attempt == 0:
                log(f"input {(out or '')[:100]}")
            time.sleep(0.9)

            t0 = time.perf_counter()
            _xy = _xy_by_id(adb.dump(), "/tv_search")
            search_btn = _xy or None
            if search_btn is not None:
                log(f"tap search button {search_btn}")
                _tap_xy(adb, search_btn)
            else:
                # 无搜索按钮则用回车上屏
                adb.run("shell", "input", "keyevent", "KEYCODE_ENTER")

            while time.perf_counter() - t0 < 12.0:
                if adb.halted or (stop_flag is not None and stop_flag()):
                    return {
                        "ok": False,
                        "latency_ms": int(round((time.perf_counter() - t0) * 1000)),
                        "stopped": True,
                    }
                try:
                    root = adb.dump()
                except Exception as exc:
                    log(f"search poll dump fail (skip): {str(exc)[:80]}")
                    time.sleep(0.3)
                    continue
                if _result_visible(root):
                    ok = True
                    latency_ms = int(round((time.perf_counter() - t0) * 1000))
                    break
                time.sleep(0.3)
            if ok:
                if attempt:
                    log(f"search verified on retry #{attempt}")
                break
            log(f"search verify #{attempt}: result page not seen -> refocus & retry")
            tap2 = _xy_by_id(adb.dump(), "/et_keyword")
            if tap2 is not None:
                _tap_xy(adb, tap2)
            time.sleep(0.6)

        if not ok:
            log("search failed -> abort")
            return {"ok": False, "latency_ms": latency_ms, "input_failed": True}
        log(f"mcloud search latency_ms={latency_ms}")
        return {"ok": ok, "latency_ms": latency_ms}
    finally:
        restore_ime(adb, prev)


# ---- 结果页 → 大图 ----

def _image_cells(root: ET.Element) -> list[dict]:
    """结果页图片聚合区里可点开的图片单元（排除 +N「更多」格）。

    仅在 /rv_images 容器内查找；容器缺失时直接返回空，绝不退化扫全页——
    否则会把左上返回键等可点节点误判为图片格并点飞（会把结果页点没了）。
    """
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
SWIPE_REPEAT_LIMIT = 3  # 连续读到同一张这么多次才判定到底（避免单次滑动未生效被误判）


def _mcloud_next(adb: Adb, slow: bool = False) -> None:
    """关掉「更多」弹窗并左滑下一张。

    合并为一次 adb 往返（BACK; sleep; input swipe），省掉一次往返与两段 host 等待；
    swipe 按屏幕比例且避开左右边缘，防止触发系统侧滑返回。
    正常用 SWIPE_NEXT_MS 快速 flick；检测到未翻页时传 slow=True 用 SWIPE_RETRY_MS 放慢重试。
    """
    w, h = _screen_size(adb)
    x1 = int(w * 0.80)
    x2 = int(w * 0.20)
    y = int(h * 0.5)
    ms = SWIPE_RETRY_MS if slow else SWIPE_NEXT_MS
    adb.run(
        "shell",
        f"input keyevent KEYCODE_BACK; sleep 0.38; "
        f"input swipe {x1} {y} {x2} {y} {ms}",
    )
    time.sleep(0.5)


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
    more = (int(w * 0.9), int(h * 0.93))

    _tap_xy(adb, more)
    time.sleep(0.35)
    try:
        root = adb.dump_stdout()
    except Exception as exc:
        log(f"more popup dump fail: {str(exc)[:80]}")
        return None
    name = _read_popup_name(root)

    if not name:
        # 兜底：固定坐标没命中「更多」→ dump 定位真实按钮再点一次
        if not on_mcloud_preview(adb):
            # 不在大图页：直接放弃（由调用方决定后续导航）
            log("not on preview while reading name")
            return None
        btn = _find_more_btn(root) or more
        log(f"name not read -> retry 更多 at {btn}")
        _tap_xy(adb, btn)
        time.sleep(0.5)
        try:
            root = adb.dump_stdout()
            name = _read_popup_name(root)
        except Exception as exc:
            log(f"more popup dump fail#2: {str(exc)[:80]}")

    if not name:
        shown = " | ".join(
            (n.get("text") or "").strip()
            for n in _nodes(root)
            if (n.get("text") or "").strip()
        )
        log(f"no filename in more popup: {shown[:180]}")
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

def mcloud_collect_search_order(adb: Adb, log, stop_flag=None, max_items: int = 60) -> dict:
    """移动云盘全局搜索采集：结果页点首图 → 大图左滑读文件名。

    不点 +N、不进 RelateAlbum。

    Returns {"records": [...], "status": looped|exhausted|capped|empty|stopped|failed}
    """
    records: list[dict] = []
    status = "failed"

    def _stopped() -> bool:
        return bool(adb.halted or (stop_flag is not None and stop_flag()))

    seen: set[str] = set()
    stalls = 0
    repeat = 0
    try:
        opened = mcloud_open_first_image(adb, log)
    except Exception as e:
        log(f"FAIL {e}")
        return {"records": records, "status": status}
    if not opened:
        if _stopped():
            log("stop before preview")
            return {"records": records, "status": "stopped"}
        log("no image block on result page -> empty")
        return {"records": records, "status": "empty"}

    while True:
        if len(records) >= max_items:
            status = "capped"
            break
        if _stopped():
            status = "stopped"
            break
        name = _mcloud_read_name(adb, log)
        if not name:
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
            # 用放慢手势重试 SWIPE_REPEAT_LIMIT 次仍不前进才判定到底，避免单次未翻页被误判。
            repeat += 1
            if repeat >= SWIPE_REPEAT_LIMIT:
                log(f"looped at #{len(records) + 1} {name} (repeat x{repeat})")
                status = "exhausted" if records else "failed"
                break
            log(f"same image ({repeat}/{SWIPE_REPEAT_LIMIT}) {name} — retry swipe (slow)")
            _mcloud_next(adb, slow=True)
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
        _mcloud_next(adb)

    _leave_preview(adb)
    return {"records": records, "status": status}


def mcloud_init_back_and_clear(adb: Adb, log) -> None:
    """用例结束复位：离开大图/结果页，回到搜索页并清空关键词。"""
    for i in range(8):
        if adb.halted:
            break
        blob = focus_blob(adb)
        if "AlbumImagePreviewActivity" in blob or "RelateAlbumImagesActivity" in blob \
                or "CloudMediaPreviewActivity" in blob or "AlbumActivity" in blob:
            adb.run("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.9)
            log(f"mcloud back from preview#{i}")
            continue
        if not on_mcloud_search(adb):
            break
        try:
            root = adb.dump()
        except Exception:
            break
        if _result_visible(root):
            # 结果页与输入页是同一个 SearchGlobalActivity，用叉清空即可
            break
        break

    if on_mcloud_search(adb):
        try:
            root = adb.dump()
        except Exception:
            log("mcloud reset done (dump fail)")
            return
        xy = _xy_by_id(root, "/iv_clear")
        if xy:
            _tap_xy(adb, xy)
            time.sleep(0.3)
            log("mcloud cleared search bar")
            return
    log("mcloud reset done (no clear needed)")
