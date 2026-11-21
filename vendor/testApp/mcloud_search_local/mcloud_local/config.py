#!/usr/bin/env python3
"""本地独立项目：移动网盘搜索（USB 直连本机 ADB，无 9526 桥）。"""
from __future__ import annotations

import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG = Path(__file__).resolve().parent
DATA = ROOT / "data"
RESULTS = ROOT / "results"

E2E_XLSX_NAME = "E2E_1000+cases_1w_data_v4.xlsx"
ES_GLOB = "ES*.xlsx"

IME_ADB = "com.android.adbkeyboard/.AdbIME"
MCLOUD_PKG = "com.chinamobile.mcloud"

# 本地默认不用桥；若显式设置则仍可用远程 adb server
ADB_USB_BRIDGE_HOST = os.environ.get("ADB_USB_BRIDGE_HOST") or None
_port_raw = os.environ.get("ADB_USB_BRIDGE_PORT", "").strip()
ADB_USB_BRIDGE_PORT = int(_port_raw) if _port_raw else None

# 本机 USB：默认连本机 adb daemon（5037），不经 -H/-P
LOCAL_USB_ONLY = os.environ.get("MCLOUD_LOCAL_USB", "1").strip() not in (
    "0",
    "false",
    "no",
)


def resolve_adb_bin() -> str:
    candidates = [
        os.environ.get("ADB_BIN") or "",
        os.environ.get("ADB") or "",
        shutil.which("adb") or "",
        str(ROOT / "tools" / "platform-tools" / "adb.exe"),
        str(ROOT / "tools" / "platform-tools" / "adb"),
    ]
    for c in candidates:
        if not c:
            continue
        p = Path(c)
        if p.is_file():
            return str(p)
    return "adb"


ADB_DEFAULT = resolve_adb_bin()
