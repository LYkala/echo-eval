from pathlib import Path

from echo_eval.cases.excel_cases import load_cases
from echo_eval.config import load_config, load_packages_map
from echo_eval.precondition.coverage import build_coverage_report
from echo_eval.precondition.deterministic import plan_deterministic_step
from echo_eval.precondition.steps import step_cache_key


def test_load_v011_sample_csv():
    path = Path("cases/examples/sample_v011_android_iqiyi_10.csv")
    cases = load_cases(path, default_wait_sec=3)
    assert len(cases) == 10
    c0 = cases[0]
    assert c0.case_id == "SYS2-001"
    assert c0.object_name == "Android 系统"
    assert c0.precondition_steps[0] == "打开网易云音乐"
    assert "音量调到50%" in c0.precondition_steps
    assert len(c0.steps) == 1
    assert c0.steps[0].text == "增大音量"
    iq = next(c for c in cases if c.case_id == "IQ-SCR-071")
    assert iq.precondition_steps == ["打开爱奇艺", "点击筛选", "点击热播榜"]
    assert iq.steps[0].text == "点击电影"


def test_utterance_arrow_split(tmp_path: Path):
    p = tmp_path / "a.csv"
    p.write_text(
        "用例 ID,用例名称,预置条件,文本语料,预期结果\n"
        'X1,双句,回桌面,"声音大一点 → 声音大一点",累计两步\n',
        encoding="utf-8",
    )
    cases = load_cases(p)
    assert len(cases) == 1
    assert [s.text for s in cases[0].steps] == ["声音大一点", "声音大一点"]
    assert cases[0].precondition_steps == ["回桌面"]


def test_step_cache_key_prefix_matters():
    k1 = step_cache_key(["打开爱奇艺"], "点击筛选")
    k2 = step_cache_key(["打开设置"], "点击显示")
    k3 = step_cache_key(["打开爱奇艺"], "点击筛选")
    assert k1 != k2
    assert k1 == k3


def test_deterministic_open_and_volume():
    packages = load_packages_map("configs/packages.yaml")
    plan = plan_deterministic_step("打开爱奇艺", packages=packages)
    assert plan is not None
    assert plan.kind == "launch"
    assert "com.qiyi.video" in plan.commands[0]
    vol = plan_deterministic_step("音量调到50%", packages=packages)
    assert vol is not None and vol.kind == "volume"
    home = plan_deterministic_step("回桌面", packages=packages)
    assert home is not None and home.kind == "home"
    click = plan_deterministic_step("点击筛选", packages=packages)
    assert click is None


def test_coverage_skips_gui_without_cache():
    path = Path("cases/examples/sample_v011_android_iqiyi_10.csv")
    cases = load_cases(path)
    cfg = load_config()
    report = build_coverage_report(cases, cfg=cfg.precondition)
    # Cases with only deterministic steps can be ready (e.g. 回桌面 / 仅打开爱奇艺)
    ready_ids = {c.case_id for c in report.cases if c.ready}
    skip_ids = report.skip_case_ids
    assert "SYS2-058-02" in ready_ids  # 回桌面 only
    assert "IQ-SCR-001" in ready_ids  # 打开爱奇艺 only
    assert "IQ-SCR-026" in skip_ids  # needs 点击筛选
    assert "SYS2-001" in skip_ids  # needs 点击歌单/歌曲
