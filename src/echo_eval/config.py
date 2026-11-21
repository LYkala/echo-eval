"""Load editable YAML config for device / capture."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG_PATH = REPO_ROOT / "configs" / "device.yaml"


@dataclass
class AdbConfig:
    host: str
    port: int
    serial: str | None
    adb_path: str
    connect_timeout_sec: float

    @property
    def resolved_serial(self) -> str:
        if self.serial and self.serial.strip():
            s = self.serial.strip()
            if ":" in s:
                return s
            return f"{s}:{self.port}"
        host = (self.host or "").strip()
        if not host:
            raise ValueError("configs/device.yaml 中 adb.host 或 adb.serial 必须填写")
        if ":" in host:
            return host
        return f"{host}:{self.port}"


@dataclass
class CaptureConfig:
    duration_sec: int
    remote_dir: str
    remote_filename: str
    local_dir: str
    fps: float
    ffmpeg_path: str

    @property
    def remote_path(self) -> str:
        d = self.remote_dir.rstrip("/")
        return f"{d}/{self.remote_filename.lstrip('/')}"

    def local_root(self, repo_root: Path | None = None) -> Path:
        root = repo_root or REPO_ROOT
        p = Path(self.local_dir)
        if p.is_absolute():
            return p
        return (root / p).resolve()


@dataclass
class AppConfig:
    adb: AdbConfig
    capture: CaptureConfig
    path: Path


def _as_dict(data: Any) -> dict[str, Any]:
    return data if isinstance(data, dict) else {}


def load_config(path: Path | str | None = None) -> AppConfig:
    cfg_path = Path(path) if path else DEFAULT_CONFIG_PATH
    if not cfg_path.is_file():
        example = cfg_path.with_name("device.yaml.example")
        hint = f" 可复制 {example} 为 {cfg_path.name}。" if example.is_file() else ""
        raise FileNotFoundError(f"未找到配置文件: {cfg_path}.{hint}")

    raw = yaml.safe_load(cfg_path.read_text(encoding="utf-8")) or {}
    adb_raw = _as_dict(raw.get("adb"))
    cap_raw = _as_dict(raw.get("capture"))

    adb = AdbConfig(
        host=str(adb_raw.get("host") or ""),
        port=int(adb_raw.get("port") or 5555),
        serial=(str(adb_raw["serial"]).strip() if adb_raw.get("serial") else None),
        adb_path=str(adb_raw.get("adb_path") or "").strip(),
        connect_timeout_sec=float(adb_raw.get("connect_timeout_sec") or 15),
    )
    capture = CaptureConfig(
        duration_sec=int(cap_raw.get("duration_sec") or 15),
        remote_dir=str(cap_raw.get("remote_dir") or "/sdcard/echo_eval"),
        remote_filename=str(cap_raw.get("remote_filename") or "capture.mp4"),
        local_dir=str(cap_raw.get("local_dir") or "artifacts"),
        fps=float(cap_raw.get("fps") if cap_raw.get("fps") is not None else 1),
        ffmpeg_path=str(cap_raw.get("ffmpeg_path") or "").strip(),
    )
    return AppConfig(adb=adb, capture=capture, path=cfg_path.resolve())
