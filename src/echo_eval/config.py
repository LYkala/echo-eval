"""Load editable YAML/JSON config for device / capture / stimulus.

Zero third-party dependency: uses a small YAML subset parser when PyYAML
is unavailable (common when pip/proxy is broken on Windows).
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG_PATH = REPO_ROOT / "configs" / "device.yaml"


def _parse_scalar(raw: str) -> Any:
    s = raw.strip()
    if not s:
        return ""
    if (s.startswith('"') and s.endswith('"')) or (s.startswith("'") and s.endswith("'")):
        return s[1:-1]
    low = s.lower()
    if low in ("true", "yes", "on"):
        return True
    if low in ("false", "no", "off"):
        return False
    if low in ("null", "~", "none"):
        return None
    if re.fullmatch(r"-?\d+", s):
        return int(s)
    if re.fullmatch(r"-?\d+\.\d+", s):
        return float(s)
    return s


def _deepcopy_mapping(value: Any) -> Any:
    if isinstance(value, dict):
        return {k: _deepcopy_mapping(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_deepcopy_mapping(v) for v in value]
    return value


def _load_simple_yaml(text: str) -> dict[str, Any]:
    """Parse nested mappings + TestApp-style anchors (&name / *name / <<: *name)."""
    root: dict[str, Any] = {}
    stack: list[tuple[int, dict[str, Any]]] = [(-1, root)]
    anchors: dict[str, Any] = {}

    for lineno, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        # strip inline comments when not inside quotes
        stripped = line.split("#", 1)[0].rstrip()
        if not stripped.strip():
            continue
        indent = len(stripped) - len(stripped.lstrip(" "))
        if "\t" in line[:indent]:
            raise ValueError(f"YAML 不支持 tab 缩进 (line {lineno})")
        content = stripped.lstrip(" ")
        if ":" not in content:
            raise ValueError(f"无法解析配置行 {lineno}: {line}")
        key, _, rest = content.partition(":")
        key = key.strip()
        if not key:
            raise ValueError(f"空键名 (line {lineno})")

        while stack and indent <= stack[-1][0]:
            stack.pop()
        if not stack:
            raise ValueError(f"缩进错误 (line {lineno})")
        parent = stack[-1][1]

        value_raw = rest.strip()

        # Merge key used by TestApp model.yaml: <<: *doubao
        if key == "<<":
            alias = value_raw[1:] if value_raw.startswith("*") else value_raw
            if alias not in anchors:
                raise ValueError(f"未知锚点 *{alias} (line {lineno})")
            src = anchors[alias]
            if not isinstance(src, dict):
                raise ValueError(f"锚点 *{alias} 不是 mapping，无法 merge (line {lineno})")
            for k, v in src.items():
                if k not in parent:
                    parent[k] = _deepcopy_mapping(v)
            continue

        anchor_name: str | None = None
        if value_raw.startswith("&"):
            # default: &doubao   OR   name: &a somevalue
            parts = value_raw.split(None, 1)
            anchor_name = parts[0][1:]
            value_raw = parts[1] if len(parts) > 1 else ""

        # Alias reference: key: *doubao
        if value_raw.startswith("*") and len(value_raw.split()) == 1:
            alias = value_raw[1:]
            if alias not in anchors:
                raise ValueError(f"未知锚点 *{alias} (line {lineno})")
            parent[key] = _deepcopy_mapping(anchors[alias])
            if anchor_name:
                anchors[anchor_name] = parent[key]
            continue

        if value_raw == "":
            child: dict[str, Any] = {}
            parent[key] = child
            if anchor_name:
                anchors[anchor_name] = child
            stack.append((indent, child))
        else:
            parent[key] = _parse_scalar(value_raw)
            if anchor_name:
                anchors[anchor_name] = parent[key]
    return root


def _load_config_text(path: Path, text: str) -> Any:
    if path.suffix.lower() == ".json":
        return json.loads(text)
    try:
        import yaml  # type: ignore

        return yaml.safe_load(text)
    except ImportError:
        return _load_simple_yaml(text)


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

    raw = _load_config_text(cfg_path, cfg_path.read_text(encoding="utf-8")) or {}
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
