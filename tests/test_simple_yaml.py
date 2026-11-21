from pathlib import Path

from echo_eval.config import _load_simple_yaml, load_config


def test_simple_yaml_parses_device_file():
    text = Path("configs/device.yaml").read_text(encoding="utf-8")
    data = _load_simple_yaml(text)
    assert data["adb"]["host"] == "192.168.43.244"
    assert data["adb"]["port"] == 5555
    assert data["capture"]["duration_sec"] == 15
    assert data["stimulus"]["enabled"] is False
    assert data["stimulus"]["start_delay_sec"] == 1.0


def test_load_config_via_simple_parser(monkeypatch):
    monkeypatch.setattr(
        "echo_eval.config._load_config_text",
        lambda _path, text: _load_simple_yaml(text),
    )
    loaded = load_config("configs/device.yaml")
    assert loaded.adb.resolved_serial == "192.168.43.244:5555"
    assert loaded.capture.fps == 1.0
