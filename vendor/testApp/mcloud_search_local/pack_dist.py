#!/usr/bin/env python3
"""打可分发 zip：内含 platform-tools(adb) + ADBKeyboard.apk + 用例数据。"""
from __future__ import annotations

import shutil
import zipfile
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO_PT = ROOT.parent / "webserverV2" / "tools" / "platform-tools"
REPO_APK = ROOT.parent / "webserverV2" / "apks" / "adbkeyboard" / "ADBKeyboard.apk"
OUT_DIR = ROOT / "dist"
STAMP = datetime.now().strftime("%Y%m%d_%H%M")
NAME = f"mcloud_search_local_{STAMP}"
STAGE = OUT_DIR / NAME

INCLUDE_DIRS = ("mcloud_local", "static", "templates", "data")
INCLUDE_FILES = ("app.py", "requirements.txt", "start.bat", "README.md", "使用说明.txt")


def copy_platform_tools(dest: Path) -> None:
    """Copy real files (resolve junction)."""
    src = REPO_PT
    if not (src / "adb.exe").is_file():
        raise SystemExit(f"missing adb.exe under {src}")
    dest.mkdir(parents=True, exist_ok=True)
    for p in src.iterdir():
        if p.is_file():
            shutil.copy2(p, dest / p.name)
        elif p.is_dir():
            shutil.copytree(p, dest / p.name, dirs_exist_ok=True)


def main() -> None:
    if STAGE.exists():
        shutil.rmtree(STAGE)
    STAGE.mkdir(parents=True)

    for d in INCLUDE_DIRS:
        src = ROOT / d
        if not src.is_dir():
            raise SystemExit(f"missing {src}")
        shutil.copytree(
            src,
            STAGE / d,
            ignore=shutil.ignore_patterns("__pycache__", "*.pyc"),
        )

    for bak in (STAGE / "data").glob("*.bak*"):
        bak.unlink(missing_ok=True)

    (STAGE / "results").mkdir(exist_ok=True)
    latest = ROOT / "results" / "latest.xlsx"
    if latest.is_file():
        shutil.copy2(latest, STAGE / "results" / "latest.xlsx")

    # adb
    pt_dest = STAGE / "tools" / "platform-tools"
    copy_platform_tools(pt_dest)
    print("adb.exe", (pt_dest / "adb.exe").stat().st_size)

    # ADB Keyboard
    apk_src = ROOT / "tools" / "adbkeyboard" / "ADBKeyboard.apk"
    if not apk_src.is_file():
        if REPO_APK.is_file():
            apk_src.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(REPO_APK, apk_src)
        else:
            raise SystemExit("missing ADBKeyboard.apk")
    apk_dest = STAGE / "tools" / "adbkeyboard"
    apk_dest.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk_src, apk_dest / "ADBKeyboard.apk")
    print("ADBKeyboard.apk", (apk_dest / "ADBKeyboard.apk").stat().st_size)

    for f in INCLUDE_FILES:
        src = ROOT / f
        if src.is_file():
            shutil.copy2(src, STAGE / f)

    zip_path = OUT_DIR / f"{NAME}.zip"
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for p in STAGE.rglob("*"):
            if p.is_file():
                zf.write(p, p.relative_to(OUT_DIR).as_posix())

    print("zip", zip_path, zip_path.stat().st_size)
    print("DONE")


if __name__ == "__main__":
    main()
