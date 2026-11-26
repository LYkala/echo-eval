"""Record device screen via adb screenrecord or scrcpy; optional host audio; frames."""
from __future__ import annotations

import json
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from echo_eval.capture.frames import extract_frames
from echo_eval.capture.scrcpy_record import (
    ScrcpyError,
    probe_has_audio,
    start_scrcpy_record,
    stop_scrcpy_record,
    wait_scrcpy_record,
)
from echo_eval.config import AppConfig, REPO_ROOT
from echo_eval.device.adb import AdbClient, AdbError
from echo_eval.stimulus.audio import AudioPlayError, play_audio_file


@dataclass
class CaptureResult:
    serial: str
    duration_sec: int
    remote_path: str
    local_video: str
    frames_dir: str | None
    frame_paths: list[str] = field(default_factory=list)
    started_at: str = ""
    finished_at: str = ""
    run_dir: str = ""
    timeline: dict[str, Any] = field(default_factory=dict)

    def write_meta(self, path: Path) -> None:
        path.write_text(
            json.dumps(asdict(self), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


def _run_id() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")


def _device_posix(path: str) -> str:
    if not path.startswith("/"):
        path = "/" + path
    if not path.startswith("//"):
        path = "/" + path
    return path


def record_session(
    cfg: AppConfig,
    *,
    client: AdbClient | None = None,
    extract: bool = True,
    keep_remote: bool = False,
    play_audio: bool | None = None,
    audio_path: str | Path | None = None,
) -> CaptureResult:
    serial = cfg.adb.resolved_serial
    adb = client or AdbClient(serial=serial, adb_path=cfg.adb.adb_path)

    state = adb.device_state()
    if not state or not state.ok:
        adb.connect(timeout_sec=cfg.adb.connect_timeout_sec)

    use_scrcpy = bool(cfg.capture.use_scrcpy)
    if not use_scrcpy and not adb.has_screenrecord():
        raise AdbError(
            "设备上未找到 screenrecord。可改 capture.backend: scrcpy，"
            "或换机 / 启用 screencap 降级。"
        )

    do_play = cfg.stimulus.enabled if play_audio is None else bool(play_audio)
    if do_play and cfg.stimulus.play_device != "host":
        raise AdbError(
            f"暂不支持 stimulus.play_device={cfg.stimulus.play_device!r}，请使用 host"
        )

    remote_dir = cfg.capture.remote_dir.rstrip("/")
    remote_file = f"{remote_dir}/{cfg.capture.remote_filename.lstrip('/')}"
    remote_on_device = remote_file if remote_file.startswith("/") else f"/{remote_file}"

    run_dir = cfg.capture.local_root() / f"capture_{_run_id()}"
    run_dir.mkdir(parents=True, exist_ok=True)
    local_video = run_dir / "capture.mp4"
    frames_dir = run_dir / "frames"

    if not use_scrcpy:
        adb.ensure_remote_dir(remote_dir)
        adb.rm_remote(remote_on_device)

    timeline: dict[str, Any] = {
        "record_started_at": None,
        "play_started_at": None,
        "play_finished_at": None,
        "play_offset_sec": None,
        "audio_path": None,
        "audio_backend": None,
        "capture_backend": "scrcpy" if use_scrcpy else "screenrecord",
    }

    started = datetime.now(timezone.utc).isoformat()
    timeline["record_started_at"] = started
    t0 = time.time()

    scrcpy_proc = None
    scrcpy_meta = None

    def _start_recorder() -> Any:
        nonlocal scrcpy_proc, scrcpy_meta
        if use_scrcpy:
            scrcpy_proc, scrcpy_meta = start_scrcpy_record(
                local_video,
                serial=serial,
                duration_sec=cfg.capture.duration_sec,
                scrcpy_path=cfg.capture.scrcpy_path,
                adb_path=cfg.adb.adb_path,
                audio_codec=cfg.capture.scrcpy_audio_codec,
                audio_encoder=cfg.capture.scrcpy_audio_encoder,
                max_size=int(cfg.capture.scrcpy_max_size),
                video_bit_rate=str(cfg.capture.scrcpy_video_bit_rate),
                extra_args=list(cfg.capture.scrcpy_extra_args or []),
                require_audio=bool(cfg.capture.scrcpy_require_audio),
            )
            return scrcpy_proc
        return adb.screenrecord_start(remote_on_device, cfg.capture.duration_sec)

    def _wait_recorder(proc: Any) -> None:
        if use_scrcpy:
            assert scrcpy_meta is not None
            wait_scrcpy_record(proc, scrcpy_meta)
        else:
            adb.screenrecord_wait(proc, duration_sec=cfg.capture.duration_sec)

    try:
        if do_play:
            audio = (
                Path(audio_path)
                if audio_path
                else cfg.stimulus.resolved_path(REPO_ROOT)
            )
            if not audio.is_file():
                raise AudioPlayError(
                    f"刺激音频不存在: {audio}。请修改 configs/device.yaml 的 stimulus.path"
                )

            proc = _start_recorder()
            delay = max(0.0, float(cfg.stimulus.start_delay_sec))
            if delay:
                time.sleep(delay)
            timeline["play_offset_sec"] = round(time.time() - t0, 3)
            try:
                play = play_audio_file(audio, ffmpeg_path=cfg.capture.ffmpeg_path)
            except AudioPlayError:
                try:
                    _wait_recorder(proc)
                except (AdbError, ScrcpyError):
                    pass
                raise
            timeline["play_started_at"] = play.started_at
            timeline["play_finished_at"] = play.finished_at
            timeline["audio_path"] = play.path
            timeline["audio_backend"] = play.backend
            _wait_recorder(proc)
        elif use_scrcpy:
            proc = _start_recorder()
            _wait_recorder(proc)
        else:
            adb.screenrecord(remote_on_device, cfg.capture.duration_sec)
    except Exception:
        if use_scrcpy:
            stop_scrcpy_record(scrcpy_proc)
        raise

    elapsed = time.time() - t0
    finished = datetime.now(timezone.utc).isoformat()

    if use_scrcpy:
        if not local_video.is_file():
            raise ScrcpyError(f"scrcpy 未写出 {local_video}")
        timeline["capture_audio"] = probe_has_audio(
            local_video, ffmpeg_path=cfg.capture.ffmpeg_path
        )
        remote_label = "(scrcpy-local)"
    else:
        adb.pull(_device_posix(remote_on_device), local_video)
        if not keep_remote:
            adb.rm_remote(remote_on_device)
        timeline["capture_audio"] = False
        remote_label = remote_on_device

    frame_paths: list[Path] = []
    frames_dir_out: str | None = None
    if extract and cfg.capture.fps > 0:
        frame_paths = extract_frames(
            local_video,
            frames_dir,
            fps=cfg.capture.fps,
            ffmpeg_path=cfg.capture.ffmpeg_path,
        )
        frames_dir_out = str(frames_dir)

    result = CaptureResult(
        serial=serial,
        duration_sec=cfg.capture.duration_sec,
        remote_path=remote_label,
        local_video=str(local_video),
        frames_dir=frames_dir_out,
        frame_paths=[str(p) for p in frame_paths],
        started_at=started,
        finished_at=finished,
        run_dir=str(run_dir),
        timeline=timeline,
    )
    payload = asdict(result)
    payload["elapsed_wall_sec"] = round(elapsed, 2)
    (run_dir / "capture_meta.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (run_dir / "timeline.json").write_text(
        json.dumps(timeline, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return result
