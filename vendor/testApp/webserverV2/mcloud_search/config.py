#!/usr/bin/env python3
"""Paths and constants for 移动网盘搜索 (embedded in webserverV2).

与 netdisk_search（网盘搜索）完全独立：数据目录、结果目录都是本模块自己的，
不会与「网盘搜索」互相污染。
"""
from __future__ import annotations

import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
RESULTS = ROOT / "results"

E2E_XLSX_NAME = "E2E_1000+cases_1w_data_v4.xlsx"
ES_GLOB = "ES*.xlsx"

IME_ADB = "com.android.adbkeyboard/.AdbIME"
# 移动云盘（中国移动云盘）
MCLOUD_PKG = "com.chinamobile.mcloud"

# USB 桥：本机 USB → SSH 反向到跳板机（9526 默认 :15036，见 ADB_USB_BRIDGE_PORT）
ADB_USB_BRIDGE_HOST = os.environ.get("ADB_USB_BRIDGE_HOST", "127.0.0.1")
ADB_USB_BRIDGE_PORT = int(os.environ.get("ADB_USB_BRIDGE_PORT", "15036") or "15036")


def resolve_adb_bin() -> str:
    candidates = [
        os.environ.get("ADB_BIN") or "",
        os.environ.get("ADB") or "",
        "/opt/android-sdk/platform-tools/adb",
        str(ROOT.parent / "tools" / "platform-tools" / "adb"),
        str(ROOT.parent / "tools" / "platform-tools" / "adb.exe"),
        shutil.which("adb") or "",
    ]
    for c in candidates:
        if not c:
            continue
        p = Path(c)
        if p.is_file():
            return str(p)
    return "adb"


ADB_DEFAULT = resolve_adb_bin()
