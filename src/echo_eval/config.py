"""Load editable YAML/JSON config for device / capture / stimulus.

Zero third-party dependency: uses a small YAML subset parser when PyYAML
is unavailable (common when pip/proxy is broken on Windows).
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
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
    # screenrecord = adb 无声录屏；scrcpy = 本机录屏+内录音频
    backend: str = "scrcpy"
    scrcpy_path: str = ""
    # Huawei Android 12: default opus fails; AAC works in puncture
    scrcpy_audio_codec: str = "aac"
    scrcpy_audio_encoder: str = "OMX.google.aac.encoder"
    scrcpy_max_size: int = 1280
    scrcpy_video_bit_rate: str = "8M"
    scrcpy_require_audio: bool = True
    scrcpy_extra_args: list[str] = field(default_factory=list)

    @property
    def remote_path(self) -> str:
        d = self.remote_dir.rstrip("/")
        return f"{d}/{self.remote_filename.lstrip('/')}"

    @property
    def use_scrcpy(self) -> bool:
        return str(self.backend or "").strip().lower() in ("scrcpy", "scrcpy_audio")

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
    # edge 连不上微软服务时，回退 Windows SAPI（仅 Windows）
    fallback_sapi: bool = True
    # 预合成语音库目录（相对仓库根或绝对路径）
    library_dir: str = "assets/tts_library"
    # 运行时优先用语音库；未命中则用本机 SAPI（不现场调 edge）
    prefer_library: bool = True


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
    # 正式步骤前播一句唤醒词（不参与 ASR/GUI/时延/MOS 评判）
    wake_enabled: bool = True
    wake_text: str = "小翼管家"
    wake_wait_sec: float = 3.0


@dataclass
class AsrConfig:
    """ASR 准确率：句后按间隔截屏，多帧 VL 对照播报文案（与 GUI 成功率互不影响）。"""

    enabled: bool = True
    # 截屏间隔（秒）
    interval_sec: float = 0.5
    # 最长采图窗口（秒）；默认 4s → 最多约 8 张
    window_sec: float = 4.0
    # 底部条相对首帧连续变化达到该帧数则提前停采（像素启发式，非 OCR）
    early_stop: bool = True
    early_stop_stable_frames: int = 2
    # 用于早停的底部区域高度占比；P2 时延「忽略底部文字」将复用该带
    bottom_band_ratio: float = 0.22
    # 底部固定提示（不算识别结果）
    ignore_texts: list[str] = field(
        default_factory=lambda: ["我还在听，说再见退出"]
    )


@dataclass
class LatencyConfig:
    """端到端时延（pull 后离线）：说完 → 画面首变（忽略底部识别条文字变化）。"""

    enabled: bool = True
    # 从 capture.mp4 抽帧帧率（需 ≥10 才能到约 0.1s）
    fps: float = 10.0
    # 说完后搜索窗口；超时记 status=no_action（无操控）
    window_sec: float = 8.0
    bottom_band_ratio: float = 0.22
    # 上半屏灰度指纹平均绝对差阈值
    change_threshold: float = 6.0


@dataclass
class MosConfig:
    """TTS MOS：启发式流畅度 1–5。

    source:
      - auto: scrcpy 且 mp4 有音轨 → 从视频抽；否则主机麦
      - video: 只从 capture.mp4 抽（无音轨则该句失败/N/A）
      - host_mic: 只主机麦（旧路径）
    """

    enabled: bool = True
    source: str = "auto"
    # 说完后录音/抽音时长
    window_sec: float = 5.0
    sample_rate: int = 16000
    # 有效语音低于该秒数 → N/A
    min_speech_sec: float = 0.3
    # ffmpeg -f / -i；留空则按平台默认（Windows 常需填写 dshow 设备名）
    input_format: str = ""
    input_device: str = ""


@dataclass
class PreconditionConfig:
    """预置条件：开跑前覆盖检查 + 每条用例前的 ADB 回放。"""

    enabled: bool = True
    coverage_check: bool = True
    # 回放后再 VL 验一次；false = 已通过不再重新测试
    verify_after_replay: bool = False
    # 已 pass 的步骤一律回放 cache ADB（校准/正式 run）；重训用 CLI --force
    max_retries: int = 3
    # 逐步回放后的默认等待（秒）；可被 step_settle_overrides 按步骤文案覆盖
    step_settle_sec: float = 1.0
    # 按预置步骤文案差异化等待，例如开 App 后等广告：{"打开爱奇艺": 8}
    step_settle_overrides: dict[str, float] = field(default_factory=dict)
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
    asr: AsrConfig
    latency: LatencyConfig
    mos: MosConfig
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
    asr_raw = _as_dict(raw.get("asr"))
    lat_raw = _as_dict(raw.get("latency"))
    mos_raw = _as_dict(raw.get("mos"))

    adb = AdbConfig(
        host=str(adb_raw.get("host") or ""),
        port=int(adb_raw.get("port") or 5555),
        serial=(str(adb_raw["serial"]).strip() if adb_raw.get("serial") else None),
        adb_path=str(adb_raw.get("adb_path") or "").strip(),
        connect_timeout_sec=float(adb_raw.get("connect_timeout_sec") or 15),
    )
    extra_raw = cap_raw.get("scrcpy_extra_args")
    extra_args: list[str] = []
    if isinstance(extra_raw, list):
        extra_args = [str(x).strip() for x in extra_raw if str(x).strip()]
    elif isinstance(extra_raw, str) and extra_raw.strip():
        extra_args = [extra_raw.strip()]
    capture = CaptureConfig(
        duration_sec=int(cap_raw.get("duration_sec") or 15),
        remote_dir=str(cap_raw.get("remote_dir") or "/sdcard/echo_eval"),
        remote_filename=str(cap_raw.get("remote_filename") or "capture.mp4"),
        local_dir=str(cap_raw.get("local_dir") or "artifacts"),
        fps=float(cap_raw.get("fps") if cap_raw.get("fps") is not None else 1),
        ffmpeg_path=str(cap_raw.get("ffmpeg_path") or "").strip(),
        backend=str(cap_raw.get("backend") or "scrcpy").strip().lower() or "scrcpy",
        scrcpy_path=str(cap_raw.get("scrcpy_path") or "").strip(),
        scrcpy_audio_codec=str(cap_raw.get("scrcpy_audio_codec") or "aac").strip()
        or "aac",
        scrcpy_audio_encoder=str(
            cap_raw.get("scrcpy_audio_encoder") or "OMX.google.aac.encoder"
        ).strip(),
        scrcpy_max_size=int(cap_raw.get("scrcpy_max_size") or 1280),
        scrcpy_video_bit_rate=str(cap_raw.get("scrcpy_video_bit_rate") or "8M").strip()
        or "8M",
        scrcpy_require_audio=bool(
            cap_raw["scrcpy_require_audio"]
            if "scrcpy_require_audio" in cap_raw
            else True
        ),
        scrcpy_extra_args=extra_args,
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
    if "fallback_sapi" in tts_raw:
        tts_fallback = bool(tts_raw.get("fallback_sapi"))
    else:
        tts_fallback = True
    if "prefer_library" in tts_raw:
        prefer_library = bool(tts_raw.get("prefer_library"))
    else:
        prefer_library = True
    tts = TtsConfig(
        backend=str(tts_raw.get("backend") or "edge").strip().lower(),
        voice=str(tts_raw.get("voice") or "zh-CN-XiaoxiaoNeural").strip(),
        rate=int(tts_raw.get("rate") or 0),
        lookup_dir=str(tts_raw.get("lookup_dir") or "").strip(),
        fallback_sapi=tts_fallback,
        library_dir=str(tts_raw.get("library_dir") or "assets/tts_library").strip()
        or "assets/tts_library",
        prefer_library=prefer_library,
    )
    if "return_home_after_case" in run_raw:
        return_home = bool(run_raw.get("return_home_after_case"))
    else:
        return_home = True
    if "judge_async" in run_raw:
        judge_async = bool(run_raw.get("judge_async"))
    else:
        judge_async = True
    if "wake_enabled" in run_raw:
        wake_enabled = bool(run_raw.get("wake_enabled"))
    else:
        wake_enabled = True
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
        wake_enabled=wake_enabled,
        wake_text=str(run_raw.get("wake_text") or "小翼管家").strip() or "小翼管家",
        wake_wait_sec=float(
            run_raw.get("wake_wait_sec")
            if run_raw.get("wake_wait_sec") is not None
            else 3.0
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
    if "verify_after_replay" in pre_raw:
        pre_verify = bool(pre_raw.get("verify_after_replay"))
    else:
        pre_verify = False
    precondition = PreconditionConfig(
        enabled=pre_enabled,
        coverage_check=pre_coverage,
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
        step_settle_overrides=_parse_settle_overrides(
            pre_raw.get("step_settle_overrides")
        ),
        cache_dir=str(pre_raw.get("cache_dir") or "artifacts/precondition_cache"),
        packages_path=str(pre_raw.get("packages_path") or "configs/packages.yaml"),
    )
    if "enabled" in asr_raw:
        asr_enabled = bool(asr_raw.get("enabled"))
    else:
        asr_enabled = True
    if "early_stop" in asr_raw:
        asr_early = bool(asr_raw.get("early_stop"))
    else:
        asr_early = True
    ignore_raw = asr_raw.get("ignore_texts")
    if isinstance(ignore_raw, list):
        ignore_texts = [str(x).strip() for x in ignore_raw if str(x).strip()]
    elif isinstance(ignore_raw, str) and ignore_raw.strip():
        # 支持 "a|b" 或单条字符串（兼容无列表语法的简易 YAML 解析器）
        parts = [p.strip() for p in ignore_raw.split("|")]
        ignore_texts = [p for p in parts if p]
    else:
        ignore_texts = ["我还在听，说再见退出"]
    asr = AsrConfig(
        enabled=asr_enabled,
        interval_sec=float(
            asr_raw.get("interval_sec")
            if asr_raw.get("interval_sec") is not None
            else 0.5
        ),
        window_sec=float(
            asr_raw.get("window_sec") if asr_raw.get("window_sec") is not None else 4.0
        ),
        early_stop=asr_early,
        early_stop_stable_frames=max(
            1,
            int(
                asr_raw.get("early_stop_stable_frames")
                if asr_raw.get("early_stop_stable_frames") is not None
                else 2
            ),
        ),
        bottom_band_ratio=min(
            0.5,
            max(
                0.05,
                float(
                    asr_raw.get("bottom_band_ratio")
                    if asr_raw.get("bottom_band_ratio") is not None
                    else 0.22
                ),
            ),
        ),
        ignore_texts=ignore_texts or ["我还在听，说再见退出"],
    )
    if "enabled" in lat_raw:
        lat_enabled = bool(lat_raw.get("enabled"))
    else:
        lat_enabled = True
    latency = LatencyConfig(
        enabled=lat_enabled,
        fps=float(lat_raw.get("fps") if lat_raw.get("fps") is not None else 10.0),
        window_sec=float(
            lat_raw.get("window_sec") if lat_raw.get("window_sec") is not None else 8.0
        ),
        bottom_band_ratio=min(
            0.5,
            max(
                0.05,
                float(
                    lat_raw.get("bottom_band_ratio")
                    if lat_raw.get("bottom_band_ratio") is not None
                    else asr.bottom_band_ratio
                ),
            ),
        ),
        change_threshold=float(
            lat_raw.get("change_threshold")
            if lat_raw.get("change_threshold") is not None
            else 6.0
        ),
    )
    if "enabled" in mos_raw:
        mos_enabled = bool(mos_raw.get("enabled"))
    else:
        mos_enabled = True
    mos_source = str(mos_raw.get("source") or "auto").strip().lower() or "auto"
    if mos_source not in ("auto", "video", "host_mic"):
        mos_source = "auto"
    mos = MosConfig(
        enabled=mos_enabled,
        source=mos_source,
        window_sec=float(
            mos_raw.get("window_sec") if mos_raw.get("window_sec") is not None else 5.0
        ),
        sample_rate=max(
            8000,
            int(
                mos_raw.get("sample_rate")
                if mos_raw.get("sample_rate") is not None
                else 16000
            ),
        ),
        min_speech_sec=float(
            mos_raw.get("min_speech_sec")
            if mos_raw.get("min_speech_sec") is not None
            else 0.3
        ),
        input_format=str(mos_raw.get("input_format") or "").strip(),
        input_device=str(mos_raw.get("input_device") or "").strip(),
    )
    return AppConfig(
        adb=adb,
        capture=capture,
        stimulus=stimulus,
        tts=tts,
        run=run,
        precondition=precondition,
        asr=asr,
        latency=latency,
        mos=mos,
        path=cfg_path.resolve(),
    )


def _parse_settle_overrides(raw: Any) -> dict[str, float]:
    """Parse precondition.step_settle_overrides map from YAML."""
    if not isinstance(raw, dict):
        return {}
    out: dict[str, float] = {}
    for k, v in raw.items():
        key = str(k).strip()
        if not key:
            continue
        try:
            out[key] = max(0.0, float(v))
        except (TypeError, ValueError):
            continue
    return out


def public_run_defaults(cfg: AppConfig | None = None) -> dict[str, Any]:
    """Frontend-facing defaults from device.yaml (or given AppConfig)."""
    c = cfg or load_config()
    return {
        "step_wait_sec": float(c.run.step_wait_sec),
        "head_delay_sec": float(c.run.head_delay_sec),
        "tail_sec": float(c.run.tail_sec),
        "fps": float(c.capture.fps),
        "max_retries": int(c.precondition.max_retries),
        "step_settle_sec": float(c.precondition.step_settle_sec),
        "asr_enabled": bool(c.asr.enabled),
        "asr_window_sec": float(c.asr.window_sec),
        "asr_interval_sec": float(c.asr.interval_sec),
        "latency_enabled": bool(c.latency.enabled),
        "latency_window_sec": float(c.latency.window_sec),
        "mos_enabled": bool(c.mos.enabled),
        "mos_window_sec": float(c.mos.window_sec),
        "mos_input_device": str(c.mos.input_device or ""),
        "wake_enabled": bool(c.run.wake_enabled),
        "wake_text": str(c.run.wake_text or "小翼管家"),
        "wake_wait_sec": float(c.run.wake_wait_sec),
        "tts_backend": str(c.tts.backend or "edge"),
        "tts_voice": str(c.tts.voice or "zh-CN-XiaoxiaoNeural"),
        "tts_prefer_library": bool(c.tts.prefer_library),
        "tts_library_dir": str(c.tts.library_dir or "assets/tts_library"),
    }


def apply_runtime_overrides(cfg: AppConfig, overrides: dict[str, Any] | None) -> AppConfig:
    """Mutate cfg from a flat dict of optional frontend/CLI overrides. Returns cfg."""
    if not overrides:
        return cfg
    if overrides.get("step_wait_sec") is not None:
        cfg.run.step_wait_sec = max(0.0, float(overrides["step_wait_sec"]))
    if overrides.get("head_delay_sec") is not None:
        cfg.run.head_delay_sec = max(0.0, float(overrides["head_delay_sec"]))
    if overrides.get("tail_sec") is not None:
        cfg.run.tail_sec = max(0.0, float(overrides["tail_sec"]))
    if overrides.get("fps") is not None:
        cfg.capture.fps = max(0.0, float(overrides["fps"]))
    if overrides.get("max_retries") is not None:
        cfg.precondition.max_retries = max(1, int(overrides["max_retries"]))
    if overrides.get("step_settle_sec") is not None:
        cfg.precondition.step_settle_sec = max(0.0, float(overrides["step_settle_sec"]))
    if overrides.get("asr_enabled") is not None:
        cfg.asr.enabled = bool(overrides["asr_enabled"])
    if overrides.get("asr_window_sec") is not None:
        cfg.asr.window_sec = max(0.5, float(overrides["asr_window_sec"]))
    if overrides.get("asr_interval_sec") is not None:
        cfg.asr.interval_sec = max(0.2, float(overrides["asr_interval_sec"]))
    if overrides.get("latency_enabled") is not None:
        cfg.latency.enabled = bool(overrides["latency_enabled"])
    if overrides.get("latency_window_sec") is not None:
        cfg.latency.window_sec = max(0.5, float(overrides["latency_window_sec"]))
    if overrides.get("mos_enabled") is not None:
        cfg.mos.enabled = bool(overrides["mos_enabled"])
    if overrides.get("mos_window_sec") is not None:
        cfg.mos.window_sec = max(0.5, float(overrides["mos_window_sec"]))
    if overrides.get("mos_source") is not None:
        src = str(overrides["mos_source"] or "auto").strip().lower() or "auto"
        if src in ("auto", "video", "host_mic"):
            cfg.mos.source = src
    if overrides.get("mos_input_device") is not None:
        cfg.mos.input_device = str(overrides["mos_input_device"] or "").strip()
        if cfg.mos.input_device and not (cfg.mos.input_format or "").strip():
            cfg.mos.input_format = "dshow"
    if overrides.get("capture_backend") is not None:
        backend = str(overrides["capture_backend"] or "scrcpy").strip().lower()
        if backend in ("scrcpy", "scrcpy_audio", "screenrecord"):
            cfg.capture.backend = "scrcpy" if backend == "scrcpy_audio" else backend
    if overrides.get("wake_enabled") is not None:
        cfg.run.wake_enabled = bool(overrides["wake_enabled"])
    if overrides.get("wake_text") is not None:
        text = str(overrides["wake_text"] or "").strip()
        if text:
            cfg.run.wake_text = text
    if overrides.get("wake_wait_sec") is not None:
        cfg.run.wake_wait_sec = max(0.0, float(overrides["wake_wait_sec"]))
    if overrides.get("tts_backend") is not None:
        backend = str(overrides["tts_backend"] or "").strip().lower()
        if backend in ("sapi", "edge", "file", "http"):
            cfg.tts.backend = backend
    if overrides.get("tts_voice") is not None:
        voice = str(overrides["tts_voice"] or "").strip()
        if voice:
            cfg.tts.voice = voice
    if overrides.get("tts_prefer_library") is not None:
        cfg.tts.prefer_library = bool(overrides["tts_prefer_library"])
    if overrides.get("tts_library_dir") is not None:
        d = str(overrides["tts_library_dir"] or "").strip()
        if d:
            cfg.tts.library_dir = d
    return cfg


def export_config_dict(cfg: AppConfig) -> dict[str, Any]:
    """Serialize AppConfig to a JSON-friendly mapping (for per-job --config)."""
    return {
        "adb": {
            "host": cfg.adb.host,
            "port": cfg.adb.port,
            "serial": cfg.adb.serial,
            "adb_path": cfg.adb.adb_path,
            "connect_timeout_sec": cfg.adb.connect_timeout_sec,
        },
        "capture": {
            "duration_sec": cfg.capture.duration_sec,
            "remote_dir": cfg.capture.remote_dir,
            "remote_filename": cfg.capture.remote_filename,
            "local_dir": cfg.capture.local_dir,
            "fps": cfg.capture.fps,
            "ffmpeg_path": cfg.capture.ffmpeg_path,
            "backend": cfg.capture.backend,
            "scrcpy_path": cfg.capture.scrcpy_path,
            "scrcpy_audio_codec": cfg.capture.scrcpy_audio_codec,
            "scrcpy_audio_encoder": cfg.capture.scrcpy_audio_encoder,
            "scrcpy_max_size": cfg.capture.scrcpy_max_size,
            "scrcpy_video_bit_rate": cfg.capture.scrcpy_video_bit_rate,
            "scrcpy_require_audio": cfg.capture.scrcpy_require_audio,
            "scrcpy_extra_args": list(cfg.capture.scrcpy_extra_args),
        },
        "stimulus": {
            "enabled": cfg.stimulus.enabled,
            "type": cfg.stimulus.type,
            "path": cfg.stimulus.path,
            "play_device": cfg.stimulus.play_device,
            "start_delay_sec": cfg.stimulus.start_delay_sec,
        },
        "tts": {
            "backend": cfg.tts.backend,
            "voice": cfg.tts.voice,
            "rate": cfg.tts.rate,
            "lookup_dir": cfg.tts.lookup_dir,
            "fallback_sapi": bool(cfg.tts.fallback_sapi),
            "library_dir": cfg.tts.library_dir,
            "prefer_library": bool(cfg.tts.prefer_library),
        },
        "run": {
            "step_wait_sec": cfg.run.step_wait_sec,
            "head_delay_sec": cfg.run.head_delay_sec,
            "tail_sec": cfg.run.tail_sec,
            "return_home_after_case": cfg.run.return_home_after_case,
            "return_home_wait_sec": cfg.run.return_home_wait_sec,
            "judge_async": cfg.run.judge_async,
            "judge_parallel": cfg.run.judge_parallel,
            "wake_enabled": cfg.run.wake_enabled,
            "wake_text": cfg.run.wake_text,
            "wake_wait_sec": cfg.run.wake_wait_sec,
        },
        "precondition": {
            "enabled": cfg.precondition.enabled,
            "coverage_check": cfg.precondition.coverage_check,
            "verify_after_replay": cfg.precondition.verify_after_replay,
            "max_retries": cfg.precondition.max_retries,
            "step_settle_sec": cfg.precondition.step_settle_sec,
            "step_settle_overrides": dict(cfg.precondition.step_settle_overrides),
            "cache_dir": cfg.precondition.cache_dir,
            "packages_path": cfg.precondition.packages_path,
        },
        "asr": {
            "enabled": cfg.asr.enabled,
            "interval_sec": cfg.asr.interval_sec,
            "window_sec": cfg.asr.window_sec,
            "early_stop": cfg.asr.early_stop,
            "early_stop_stable_frames": cfg.asr.early_stop_stable_frames,
            "bottom_band_ratio": cfg.asr.bottom_band_ratio,
            "ignore_texts": list(cfg.asr.ignore_texts),
        },
        "latency": {
            "enabled": cfg.latency.enabled,
            "fps": cfg.latency.fps,
            "window_sec": cfg.latency.window_sec,
            "bottom_band_ratio": cfg.latency.bottom_band_ratio,
            "change_threshold": cfg.latency.change_threshold,
        },
        "mos": {
            "enabled": cfg.mos.enabled,
            "source": cfg.mos.source,
            "window_sec": cfg.mos.window_sec,
            "sample_rate": cfg.mos.sample_rate,
            "min_speech_sec": cfg.mos.min_speech_sec,
            "input_format": cfg.mos.input_format,
            "input_device": cfg.mos.input_device,
        },
    }


def materialize_config(
    out_path: Path | str,
    overrides: dict[str, Any] | None = None,
    *,
    base_path: Path | str | None = None,
) -> Path:
    """Write a full JSON config (base + overrides) for a single job. Does not touch device.yaml."""
    cfg = load_config(base_path)
    apply_runtime_overrides(cfg, overrides)
    dest = Path(out_path)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(
        json.dumps(export_config_dict(cfg), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return dest.resolve()


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
