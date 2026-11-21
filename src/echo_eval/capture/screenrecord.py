"""Record device screen via adb screenrecord, pull, optional frame extract."""
from __future__ import annotations

import json
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path

from echo_eval.config import AppConfig
from echo_eval.capture.frames import extract_frames
from echo_eval.device.adb import AdbClient, AdbError


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

    def write_meta(self, path: Path) -> None:
        path.write_text(
            json.dumps(asdict(self), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


def _run_id() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")


def _device_posix(path: str) -> str:
    """Normalize to a path that survives MSYS conversion when embedded in adb args."""
    if not path.startswith("/"):
        path = "/" + path
    # Double-slash form: Git Bash won't rewrite; Android treats //sdcard as /sdcard.
    if not path.startswith("//"):
        path = "/" + path
    return path


def record_session(
    cfg: AppConfig,
    *,
    client: AdbClient | None = None,
    extract: bool = True,
    keep_remote: bool = False,
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

    remote_dir = cfg.capture.remote_dir.rstrip("/")
    remote_file = f"{remote_dir}/{cfg.capture.remote_filename.lstrip('/')}"
    # Inside `adb shell "..."` the command runs on device — use single-slash POSIX.
    remote_on_device = remote_file if remote_file.startswith("/") else f"/{remote_file}"

    run_dir = cfg.capture.local_root() / f"capture_{_run_id()}"
    run_dir.mkdir(parents=True, exist_ok=True)
    local_video = run_dir / "capture.mp4"
    frames_dir = run_dir / "frames"

    adb.ensure_remote_dir(remote_dir)
    adb.rm_remote(remote_on_device)

    started = datetime.now(timezone.utc).isoformat()
    t0 = time.time()
    adb.screenrecord(remote_on_device, cfg.capture.duration_sec)
    elapsed = time.time() - t0
    finished = datetime.now(timezone.utc).isoformat()

    # pull: use // form for the adb.exe argument
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
    )
    # attach timing hint for debugging wireless lag
    meta_extra = run_dir / "capture_meta.json"
    payload = asdict(result)
    payload["elapsed_wall_sec"] = round(elapsed, 2)
    meta_extra.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return result
