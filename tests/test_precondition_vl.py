from pathlib import Path
from unittest.mock import patch

from echo_eval.precondition.commands import filter_allowed_commands, is_allowed_input_command
from echo_eval.precondition.steps import resolve_step_settle_sec, step_cache_key
from echo_eval.precondition.vl_act import extract_json_object, vl_propose_commands
from echo_eval.precondition.cache import PreconditionCache, StepCacheEntry


def test_resolve_step_settle_overrides():
    overrides = {"打开爱奇艺": 8, "打开设置": 2.5, "打开": 1.5}
    assert resolve_step_settle_sec("点击筛选", default_sec=1.0, overrides=overrides) == 1.0
    assert resolve_step_settle_sec("打开爱奇艺", default_sec=1.0, overrides=overrides) == 8.0
    assert resolve_step_settle_sec("打开 爱奇艺", default_sec=1.0, overrides=overrides) == 8.0
    assert resolve_step_settle_sec("打开设置", default_sec=1.0, overrides=overrides) == 2.5
    # longest substring key wins over shorter "打开"
    assert resolve_step_settle_sec("打开爱奇艺APP", default_sec=1.0, overrides=overrides) == 8.0
    assert resolve_step_settle_sec("打开某某", default_sec=1.0, overrides=overrides) == 1.5


def test_whitelist_commands():
    assert is_allowed_input_command("input tap 10 20")
    assert is_allowed_input_command("input swipe 1 2 3 4 300")
    assert is_allowed_input_command("input keyevent 4")
    assert is_allowed_input_command("input keyevent KEYCODE_BACK")
    assert not is_allowed_input_command("am force-stop com.x")
    assert not is_allowed_input_command("input tap 1 2; rm -rf /")
    ok, bad = filter_allowed_commands(
        ["input tap 1 2", "settings put system x 1", "input swipe 0 1 0 2"]
    )
    assert ok == ["input tap 1 2", "input swipe 0 1 0 2"]
    assert bad == ["settings put system x 1"]


def test_extract_json_object_fence():
    text = '好的\n```json\n{"achieved": true, "reason": "ok"}\n```\n'
    obj = extract_json_object(text)
    assert obj["achieved"] is True


def test_cache_retry_failed_semantics(tmp_path: Path):
    cache = PreconditionCache(tmp_path / "cache")
    key = step_cache_key(["打开爱奇艺"], "点击筛选")
    cache.upsert(
        StepCacheEntry(
            key=key,
            step_text="点击筛选",
            prefix=["打开爱奇艺"],
            status="failed",
            adb_commands=["input tap 1 2"],
            kind="vl",
        )
    )
    cache.save()
    assert cache.status_for(key) == "failed"
    # simulate --retry-failed success
    e = cache.get(key)
    assert e is not None
    e.status = "passed"
    cache.upsert(e)
    cache.save()
    assert cache.status_for(key) == "passed"


def test_vl_propose_includes_prior_context(tmp_path: Path):
    current = tmp_path / "try2_before.png"
    prior = tmp_path / "try1_before.png"
    current.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\x00" * 16)
    prior.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\x01" * 16)
    captured: dict = {}

    def fake_chat(_cfg, messages, **_kwargs):
        captured["messages"] = messages
        return '{"adb_commands": ["input tap 100 200"], "reason": "retry"}'

    with patch("echo_eval.precondition.vl_act.chat_vision", side_effect=fake_chat), patch(
        "echo_eval.precondition.vl_act.load_vl_eval_config",
        return_value={"timeout": 30},
    ):
        out = vl_propose_commands(
            screenshot=current,
            step_text="点击筛选",
            prefix=["打开爱奇艺"],
            wm_size="1080x2340",
            feedback="未出现筛选面板",
            prior_commands=["input tap 50 60"],
            prior_screenshot=prior,
        )

    assert out["adb_commands"] == ["input tap 100 200"]
    content = captured["messages"][1]["content"]
    texts = [p.get("text", "") for p in content if p.get("type") == "text"]
    images = [p for p in content if p.get("type") == "image_url"]
    joined = "\n".join(texts)
    assert "上一轮执行命令: input tap 50 60" in joined
    assert "未出现筛选面板" in joined
    assert "上一轮操作时画面" in joined
    assert "当前画面" in joined
    assert len(images) == 2


def test_vl_propose_first_attempt_single_image(tmp_path: Path):
    current = tmp_path / "try1_before.png"
    current.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\x00" * 16)
    captured: dict = {}

    def fake_chat(_cfg, messages, **_kwargs):
        captured["messages"] = messages
        return '{"adb_commands": ["input tap 10 20"], "reason": "first"}'

    with patch("echo_eval.precondition.vl_act.chat_vision", side_effect=fake_chat), patch(
        "echo_eval.precondition.vl_act.load_vl_eval_config",
        return_value={"timeout": 30},
    ):
        vl_propose_commands(
            screenshot=current,
            step_text="点击筛选",
            prefix=[],
            wm_size="1080x2340",
        )

    content = captured["messages"][1]["content"]
    images = [p for p in content if p.get("type") == "image_url"]
    assert len(images) == 1
