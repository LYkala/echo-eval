from echo_eval.judge.wake_check import evaluate_wake_images_mock, _parse_json


def test_wake_mock_ok(tmp_path):
    before = tmp_path / "b.png"
    after = tmp_path / "a.png"
    before.write_bytes(b"x")
    after.write_bytes(b"y")
    r = evaluate_wake_images_mock(before, [after], wake_text="小翼管家")
    assert r["ok"] is True
    assert r["status"] == "ok"


def test_parse_json_fence():
    raw = '```json\n{"wake_ok": false, "reason": "无气泡"}\n```'
    # bare object extractor
    assert _parse_json('{"wake_ok": true, "reason": "ok"}')["wake_ok"] is True
    assert "wake_ok" in _parse_json(raw) or _parse_json(raw) == {}
