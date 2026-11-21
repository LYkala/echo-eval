"""Record device screen via adb screenrecord, optional host audio, pull, frames."""
from __future__ import annotations

import json
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from echo_eval.capture.frames import extract_frames
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

    if not adb.has_screenrecord():
        raise AdbError(
            "设备上未找到 screenrecord。Phase 1 当前仅支持 screenrecord；"
            "请换机或后续启用 screencap 降级方案。"
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

    adb.ensure_remote_dir(remote_dir)
    adb.rm_remote(remote_on_device)

    timeline: dict[str, Any] = {
        "record_started_at": None,
        "play_started_at": None,
        "play_finished_at": None,
        "play_offset_sec": None,
        "audio_path": None,
        "audio_backend": None,
    }

    started = datetime.now(timezone.utc).isoformat()
    timeline["record_started_at"] = started
    t0 = time.time()

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

        proc = adb.screenrecord_start(remote_on_device, cfg.capture.duration_sec)
        delay = max(0.0, float(cfg.stimulus.start_delay_sec))
        if delay:
            time.sleep(delay)
        timeline["play_offset_sec"] = round(time.time() - t0, 3)
        try:
            play = play_audio_file(audio, ffmpeg_path=cfg.capture.ffmpeg_path)
        except AudioPlayError:
            # Still wait for recorder so device file is finalized.
            try:
                adb.screenrecord_wait(proc, duration_sec=cfg.capture.duration_sec)
            except AdbError:
                pass
            raise
        timeline["play_started_at"] = play.started_at
        timeline["play_finished_at"] = play.finished_at
        timeline["audio_path"] = play.path
        timeline["audio_backend"] = play.backend
        adb.screenrecord_wait(proc, duration_sec=cfg.capture.duration_sec)
    else:
        adb.screenrecord(remote_on_device, cfg.capture.duration_sec)

    elapsed = time.time() - t0
    finished = datetime.now(timezone.utc).isoformat()

    adb.pull(_device_posix(remote_on_device), local_video)

    if not keep_remote:
        adb.rm_remote(remote_on_device)

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
        remote_path=remote_on_device,
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
