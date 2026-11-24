from pathlib import Path

from echo_eval.precondition.commands import filter_allowed_commands, is_allowed_input_command
from echo_eval.precondition.steps import step_cache_key
from echo_eval.precondition.vl_act import extract_json_object
from echo_eval.precondition.cache import PreconditionCache, StepCacheEntry


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
