"""Load editable YAML config for device / capture / stimulus."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG_PATH = REPO_ROOT / "configs" / "device.yaml"


def _load_yaml(text: str) -> Any:
    try:
        import yaml
    except ImportError as e:
        raise ImportError(
            "缺少 PyYAML。代理/SSL 导致 pip 失败时，可改用国内镜像安装：\n"
            "  python -m pip install pyyaml -i https://pypi.tuna.tsinghua.edu.cn/simple\n"
            "或先执行: export PYTHONPATH=src"
        ) from e
    return yaml.safe_load(text)


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
class StimulusConfig:
    enabled: bool
    type: str
    path: str
    play_device: str
    start_delay_sec: float

    def resolved_path(self, repo_root: Path | None = None) -> Path:
        root = repo_root or REPO_ROOT
        p = Path(self.path)
        if not p.is_absolute():
            p = root / p
        return p.resolve()


@dataclass
class AppConfig:
    adb: AdbConfig
    capture: CaptureConfig
    stimulus: StimulusConfig
    path: Path


def _as_dict(data: Any) -> dict[str, Any]:
    return data if isinstance(data, dict) else {}


def load_config(path: Path | str | None = None) -> AppConfig:
    cfg_path = Path(path) if path else DEFAULT_CONFIG_PATH
    if not cfg_path.is_file():
        example = cfg_path.with_name("device.yaml.example")
        hint = f" 可复制 {example} 为 {cfg_path.name}。" if example.is_file() else ""
        raise FileNotFoundError(f"未找到配置文件: {cfg_path}.{hint}")

    raw = _load_yaml(cfg_path.read_text(encoding="utf-8")) or {}
    adb_raw = _as_dict(raw.get("adb"))
    cap_raw = _as_dict(raw.get("capture"))
    stim_raw = _as_dict(raw.get("stimulus"))

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
    stimulus = StimulusConfig(
        enabled=bool(stim_raw.get("enabled", False)),
        type=str(stim_raw.get("type") or "audio_file"),
        path=str(stim_raw.get("path") or "assets/audio/beep_1s.wav"),
        play_device=str(stim_raw.get("play_device") or "host"),
        start_delay_sec=float(
            stim_raw.get("start_delay_sec")
            if stim_raw.get("start_delay_sec") is not None
            else 1.0
        ),
    )
    return AppConfig(
        adb=adb, capture=capture, stimulus=stimulus, path=cfg_path.resolve()
    )
