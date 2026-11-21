#!/usr/bin/env python3
"""Apply DeviceSDK emulator prefs: device_type=PHY_PHONE (local a11y GUI).

Usage on jump host (with ARM tunnel / mini_adb_auth available):
  python3 setup_devicesdk_emu_prefs.py
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent
PREFS = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="ca_cert_enabled" value="false" />
    <string name="password">test_password_12345</string>
    <boolean name="auto_confirm_enabled" value="true" />
    <boolean name="device_cert_enabled" value="false" />
    <boolean name="focus_mode_enabled" value="true" />
    <string name="auth_mode">APIGW</string>
    <string name="device_type">PHY_PHONE</string>
    <boolean name="gui_confirm_enabled" value="false" />
    <string name="ticket_mode">NONE</string>
    <string name="server_env">CUSTOM_ENV</string>
    <string name="ws_url">wss://121.37.53.201:40098/ws/acm/v1/ai</string>
    <string name="auth_url">https://121.37.53.201:40098/ws/acm/v1/auth</string>
    <string name="username">test_user222</string>
    <boolean name="log_enable" value="true" />
</map>
"""
DS_A11Y = (
    "com.example.devicesdkdemo/"
    "com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl.AccessibilityManagerImpl"
)


def adb(*args: str) -> None:
    cmd = [sys.executable, str(BASE / "mini_adb_auth.py"), *args]
    print("+", " ".join(cmd))
    subprocess.check_call(cmd)


def main() -> int:
    prefs_path = Path("/tmp/sdk_config_phy.xml")
    prefs_path.write_text(PREFS, encoding="utf-8")
    adb("push", str(prefs_path), "/sdcard/sdk_config.xml")
    adb("shell", "am", "force-stop", "com.example.devicesdkdemo")
    # Resolve app UID after reinstall/wipe (was hardcoded u0_a169).
    dump = subprocess.check_output(
        [sys.executable, str(BASE / "mini_adb_auth.py"), "shell",
         "dumpsys package com.example.devicesdkdemo"],
        text=True,
        errors="replace",
    )
    import re
    m = re.search(r"userId=(\d+)", dump)
    uid = m.group(1) if m else "10164"
    adb(
        "shell",
        "su",
        "0",
        "sh",
        "-c",
        "mkdir -p /data/data/com.example.devicesdkdemo/shared_prefs && "
        "cp /sdcard/sdk_config.xml /data/data/com.example.devicesdkdemo/shared_prefs/sdk_config.xml && "
        f"chown {uid}:{uid} /data/data/com.example.devicesdkdemo/shared_prefs/sdk_config.xml && "
        "chmod 660 /data/data/com.example.devicesdkdemo/shared_prefs/sdk_config.xml",
    )
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", DS_A11Y)
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
    adb("shell", "appops", "set", "com.example.devicesdkdemo", "PROJECT_MEDIA", "allow")
    print("Done: PHY_PHONE prefs + DeviceSDK a11y + PROJECT_MEDIA allow")
    print("Next: open DeviceSDK -> 登录 -> QA, then try 打开爱奇艺…")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
