#!/usr/bin/env python3
"""Pack portable agent zip with ASCII-only StartAgent.bat (cmd-safe on CN Windows)."""
from __future__ import annotations

import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
OUT = ROOT / "static" / "netdisk" / "NetdiskPrepareAgent.zip"
EXE = TOOLS / "dist" / "NetdiskPrepareAgent.exe"
CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")

# Pure ASCII + CRLF. Unblock MOTW then start.
START_BAT = (
    "@echo off\r\n"
    "cd /d \"%~dp0\"\r\n"
    "title Netdisk Prepare Agent\r\n"
    "echo.\r\n"
    "echo  Unblocking files downloaded from Internet...\r\n"
    "powershell -NoProfile -ExecutionPolicy Bypass -Command "
    "\"Get-ChildItem -LiteralPath '%~dp0' -Force | Unblock-File\" >nul 2>&1\r\n"
    "echo  Netdisk Prepare Agent\r\n"
    "echo  Keep this window open.\r\n"
    "echo  Browser: http://124.71.69.102:9526/pan-search\r\n"
    "echo.\r\n"
    "if not exist \"jump_password.txt\" (\r\n"
    "  echo ERROR: jump_password.txt missing\r\n"
    "  pause\r\n"
    "  exit /b 1\r\n"
    ")\r\n"
    "if not exist \"NetdiskPrepareAgent.exe\" (\r\n"
    "  echo ERROR: NetdiskPrepareAgent.exe missing\r\n"
    "  pause\r\n"
    "  exit /b 1\r\n"
    ")\r\n"
    "NetdiskPrepareAgent.exe\r\n"
    "if errorlevel 1 (\r\n"
    "  echo.\r\n"
    "  echo FAILED to start. If Windows Smart App Control blocked it:\r\n"
    "  echo   1. Right-click NetdiskPrepareAgent.exe - Properties - Unblock - OK\r\n"
    "  echo   2. Or Settings - Privacy - Windows Security - App and browser control\r\n"
    "  echo      - Smart App Control - Off\r\n"
    "  echo   3. Then run StartAgent.bat again\r\n"
    ")\r\n"
    "echo.\r\n"
    "echo Agent exited.\r\n"
    "pause\r\n"
)

README = """Netdisk Prepare Agent
=====================

1. Unzip
2. Double-click StartAgent.bat (keep window open)
3. Open http://124.71.69.102:9526/pan-search
4. Select YOUR computer in the dropdown, click One-click Prepare

If Windows blocks the file (Smart App Control / 智能应用控制):
  A. Right-click NetdiskPrepareAgent.exe -> Properties -> check Unblock -> OK
     Then run StartAgent.bat again
  B. Or: Settings -> Privacy & security -> Windows Security
     -> App & browser control -> Smart App Control -> Off
     (needs reboot), then run StartAgent.bat

StartAgent.bat already tries to Unblock-File automatically.
"""


def jump_password() -> str:
    for line in CREDS.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip().startswith("root") and "CLOUD" in line:
            return line.split()[-1]
    raise SystemExit(f"no password in {CREDS}")


def main() -> None:
    if not EXE.is_file():
        raise SystemExit(f"missing exe: {EXE}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("StartAgent.bat", START_BAT.encode("ascii"))
        zf.writestr("README.txt", README.encode("utf-8"))
        zf.writestr("jump_password.txt", (jump_password() + "\n").encode("ascii", "replace"))
        zf.write(EXE, "NetdiskPrepareAgent.exe")
    print("wrote", OUT, OUT.stat().st_size)


if __name__ == "__main__":
    main()
