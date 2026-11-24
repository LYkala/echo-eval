from pathlib import Path

from echo_eval.config import load_config


def test_load_device_yaml(tmp_path: Path):
    p = tmp_path / "device.yaml"
    p.write_text(
        """
adb:
  host: "10.0.0.2"
  port: 5555
capture:
  duration_sec: 8
  remote_dir: "/sdcard/echo_eval"
  remote_filename: "a.mp4"
  local_dir: "artifacts"
  fps: 1
stimulus:
  enabled: true
  path: assets/audio/beep_1s.wav
  start_delay_sec: 1.5
tts:
  backend: sapi
run:
  step_wait_sec: 4
  tail_sec: 6
  return_home_after_case: false
  return_home_wait_sec: 3.5
  judge_async: false
  judge_parallel: 2
""",
        encoding="utf-8",
    )
    cfg = load_config(p)
    assert cfg.adb.resolved_serial == "10.0.0.2:5555"
    assert cfg.capture.remote_path == "/sdcard/echo_eval/a.mp4"
    assert cfg.capture.duration_sec == 8
    assert cfg.stimulus.enabled is True
    assert cfg.stimulus.start_delay_sec == 1.5
    assert cfg.tts.backend == "sapi"
    assert cfg.run.step_wait_sec == 4
    assert cfg.run.tail_sec == 6
    assert cfg.run.return_home_after_case is False
    assert cfg.run.return_home_wait_sec == 3.5
    assert cfg.run.judge_async is False
    assert cfg.run.judge_parallel == 2
    assert cfg.precondition.enabled is True
    assert cfg.precondition.reuse_passed is True
    assert cfg.precondition.verify_after_replay is False


def test_serial_override(tmp_path: Path):
    p = tmp_path / "device.yaml"
    p.write_text(
        """
adb:
  host: "1.1.1.1"
  port: 5555
  serial: "192.168.43.244:37123"
capture: {}
""",
        encoding="utf-8",
    )
    cfg = load_config(p)
    assert cfg.adb.resolved_serial == "192.168.43.244:37123"
    assert cfg.tts.backend == "sapi"
    assert cfg.run.head_delay_sec == 1.0
    assert cfg.run.return_home_after_case is True
    assert cfg.run.return_home_wait_sec == 2.0
    assert cfg.run.judge_async is True
    assert cfg.run.judge_parallel == 3
    assert cfg.precondition.enabled is True
    assert cfg.precondition.coverage_check is True
    assert cfg.precondition.max_retries == 3


def test_precondition_switches(tmp_path: Path):
    p = tmp_path / "device.yaml"
    p.write_text(
        """
adb:
  host: "10.0.0.2"
capture: {}
precondition:
  enabled: false
  coverage_check: false
  reuse_passed: false
  verify_after_replay: true
  max_retries: 5
  step_settle_sec: 1.5
  step_settle_overrides:
    打开爱奇艺: 8
    打开设置: 2
""",
        encoding="utf-8",
    )
    cfg = load_config(p)
    assert cfg.precondition.enabled is False
    assert cfg.precondition.coverage_check is False
    assert cfg.precondition.reuse_passed is False
    assert cfg.precondition.verify_after_replay is True
    assert cfg.precondition.max_retries == 5
    assert cfg.precondition.step_settle_sec == 1.5
    assert cfg.precondition.step_settle_overrides["打开爱奇艺"] == 8.0
    assert cfg.precondition.step_settle_overrides["打开设置"] == 2.0
