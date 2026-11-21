"""Resolve paths for webserver-embedded local_adb_runner (no required config.yaml)."""
from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import Any, Dict, Optional

ROOT = Path(__file__).resolve().parent
WEBSERVER_ROOT = ROOT.parent


def load_config(path: Optional[Path] = None) -> Dict[str, Any]:
    cfg_path = path or (ROOT / "config.yaml")
    if not cfg_path.is_file():
        # Defaults for jump host / local USB bridge
        return {
            "package": "com.example.devicesdkdemo",
            "adb": "",
            "adb_host": "",
            "adb_port": 0,
            "serial": "",
            "screenshot_max_width": 1080,
            "screenshot_jpeg_quality": 80,
            "excel_datasets_dir": str(WEBSERVER_ROOT / "excel_datasets"),
            "runs_dir": str(WEBSERVER_ROOT / "testresults"),
        }
    try:
        import yaml
        with cfg_path.open(encoding="utf-8") as f:
            cfg = yaml.safe_load(f) or {}
        return cfg
    except Exception:
        return {}


def resolve_path(value: str | Path | None, default_rel: str) -> Path:
    if value is None or str(value).strip() == "":
        p = ROOT / default_rel
    else:
        p = Path(str(value))
        if not p.is_absolute():
            p = ROOT / p
    return p.resolve()


def find_adb(cfg: Dict[str, Any] | None = None) -> str:
    cfg = cfg or load_config()
    custom = (cfg.get("adb") or "").strip()
    if custom:
        p = Path(custom)
        if not p.is_absolute():
            p = ROOT / p
        if p.is_file():
            return str(p.resolve())
    # Prefer platform-tools next to local runner, then webserver, then PATH
    candidates = []
    if sys.platform.startswith("win"):
        candidates.extend(
            [
                WEBSERVER_ROOT.parent / "local_device_gui_runner" / "tools" / "platform-tools" / "adb.exe",
                ROOT / "tools" / "platform-tools" / "adb.exe",
                Path(r"C:\Android\platform-tools\adb.exe"),
                Path(r"E:\Android\Sdk\platform-tools\adb.exe"),
            ]
        )
    else:
        candidates.extend(
            [
                Path("/opt/android-sdk/platform-tools/adb"),
                Path("/usr/local/bin/adb"),
                ROOT / "tools" / "platform-tools" / "adb",
            ]
        )
    for bundled in candidates:
        if bundled.is_file():
            return str(bundled.resolve())
    return "adb"


def datasets_dir(cfg: Dict[str, Any] | None = None) -> Path:
    cfg = cfg or load_config()
    return resolve_path(cfg.get("excel_datasets_dir"), str(WEBSERVER_ROOT / "excel_datasets"))


def runs_dir(cfg: Dict[str, Any] | None = None) -> Path:
    cfg = cfg or load_config()
    d = resolve_path(cfg.get("runs_dir"), str(WEBSERVER_ROOT / "testresults"))
    d.mkdir(parents=True, exist_ok=True)
    return d
