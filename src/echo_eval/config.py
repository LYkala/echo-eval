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
class TtsConfig:
    backend: str  # sapi | file | edge | http
    voice: str
    rate: int
    lookup_dir: str


@dataclass
class RunConfig:
    step_wait_sec: float
    head_delay_sec: float
    tail_sec: float
    # After each case (post pull): press system Home once, then wait.
    return_home_after_case: bool = True
    return_home_wait_sec: float = 2.0
    # Device runs serially; frame extract + VL judge run in a background pool.
    judge_async: bool = True
    judge_parallel: int = 3


@dataclass
class PreconditionConfig:
    """预置条件：开跑前覆盖检查 + 每条用例前的 ADB 回放。"""

    enabled: bool = True
    coverage_check: bool = True
    # 已「通过」的步骤直接回放缓存，不再探索校准
    reuse_passed: bool = True
    # 回放后再 VL 验一次；false = 已通过不再重新测试
    verify_after_replay: bool = False
    max_retries: int = 3
    # 逐步回放后的短等待
    step_settle_sec: float = 1.0
    # 缓存目录（相对仓库根或绝对路径）
    cache_dir: str = "artifacts/precondition_cache"
    # 应用包名映射文件
    packages_path: str = "configs/packages.yaml"

    def resolved_cache_dir(self, repo_root: Path | None = None) -> Path:
        root = repo_root or REPO_ROOT
        p = Path(self.cache_dir)
        if p.is_absolute():
            return p
        return (root / p).resolve()

    def resolved_packages_path(self, repo_root: Path | None = None) -> Path:
        root = repo_root or REPO_ROOT
        p = Path(self.packages_path)
        if p.is_absolute():
            return p
        return (root / p).resolve()


@dataclass
class AppConfig:
    adb: AdbConfig
    capture: CaptureConfig
    stimulus: StimulusConfig
    tts: TtsConfig
    run: RunConfig
    precondition: PreconditionConfig
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
    tts_raw = _as_dict(raw.get("tts"))
    run_raw = _as_dict(raw.get("run"))
    pre_raw = _as_dict(raw.get("precondition"))

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
    tts = TtsConfig(
        backend=str(tts_raw.get("backend") or "sapi").strip().lower(),
        voice=str(tts_raw.get("voice") or "").strip(),
        rate=int(tts_raw.get("rate") or 0),
        lookup_dir=str(tts_raw.get("lookup_dir") or "").strip(),
    )
    if "return_home_after_case" in run_raw:
        return_home = bool(run_raw.get("return_home_after_case"))
    else:
        return_home = True
    if "judge_async" in run_raw:
        judge_async = bool(run_raw.get("judge_async"))
    else:
        judge_async = True
    run = RunConfig(
        step_wait_sec=float(
            run_raw.get("step_wait_sec") if run_raw.get("step_wait_sec") is not None else 3.0
        ),
        head_delay_sec=float(
            run_raw.get("head_delay_sec") if run_raw.get("head_delay_sec") is not None else 1.0
        ),
        tail_sec=float(run_raw.get("tail_sec") if run_raw.get("tail_sec") is not None else 5.0),
        return_home_after_case=return_home,
        return_home_wait_sec=float(
            run_raw.get("return_home_wait_sec")
            if run_raw.get("return_home_wait_sec") is not None
            else 2.0
        ),
        judge_async=judge_async,
        judge_parallel=max(
            1,
            int(
                run_raw.get("judge_parallel")
                if run_raw.get("judge_parallel") is not None
                else 3
            ),
        ),
    )
    if "enabled" in pre_raw:
        pre_enabled = bool(pre_raw.get("enabled"))
    else:
        pre_enabled = True
    if "coverage_check" in pre_raw:
        pre_coverage = bool(pre_raw.get("coverage_check"))
    else:
        pre_coverage = True
    if "reuse_passed" in pre_raw:
        pre_reuse = bool(pre_raw.get("reuse_passed"))
    else:
        pre_reuse = True
    if "verify_after_replay" in pre_raw:
        pre_verify = bool(pre_raw.get("verify_after_replay"))
    else:
        pre_verify = False
    precondition = PreconditionConfig(
        enabled=pre_enabled,
        coverage_check=pre_coverage,
        reuse_passed=pre_reuse,
        verify_after_replay=pre_verify,
        max_retries=max(
            1,
            int(
                pre_raw.get("max_retries")
                if pre_raw.get("max_retries") is not None
                else 3
            ),
        ),
        step_settle_sec=float(
            pre_raw.get("step_settle_sec")
            if pre_raw.get("step_settle_sec") is not None
            else 1.0
        ),
        cache_dir=str(pre_raw.get("cache_dir") or "artifacts/precondition_cache"),
        packages_path=str(pre_raw.get("packages_path") or "configs/packages.yaml"),
    )
    return AppConfig(
        adb=adb,
        capture=capture,
        stimulus=stimulus,
        tts=tts,
        run=run,
        precondition=precondition,
        path=cfg_path.resolve(),
    )


def load_packages_map(path: Path | str | None = None) -> dict[str, str]:
    """Load display-name → package map from configs/packages.yaml."""
    pkg_path = Path(path) if path else (REPO_ROOT / "configs" / "packages.yaml")
    if not pkg_path.is_file():
        return {}
    raw = _load_config_text(pkg_path, pkg_path.read_text(encoding="utf-8")) or {}
    packages = _as_dict(raw.get("packages"))
    out: dict[str, str] = {}
    for k, v in packages.items():
        name = str(k).strip()
        pkg = str(v or "").strip()
        if name and pkg:
            out[name] = pkg
    return out


def load_object_packages(path: Path | str | None = None) -> dict[str, str]:
    pkg_path = Path(path) if path else (REPO_ROOT / "configs" / "packages.yaml")
    if not pkg_path.is_file():
        return {}
    raw = _load_config_text(pkg_path, pkg_path.read_text(encoding="utf-8")) or {}
    objects = _as_dict(raw.get("objects"))
    out: dict[str, str] = {}
    for k, v in objects.items():
        name = str(k).strip()
        pkg = str(v or "").strip()
        if name:
            out[name] = pkg
    return out
