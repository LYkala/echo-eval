from pathlib import Path

from echo_eval.cases.excel_cases import load_cases
from echo_eval.config import load_config, load_packages_map
from echo_eval.precondition.coverage import build_coverage_report
from echo_eval.precondition.deterministic import plan_deterministic_step
from echo_eval.precondition.steps import step_cache_key


SAMPLE = Path("cases/examples/sample_v012_system_iqiyi_10.csv")


def test_load_v012_sample_csv():
    cases = load_cases(SAMPLE, default_wait_sec=3)
    assert len(cases) == 10
    c0 = cases[0]
    assert c0.case_id == "SYS2-001"
    assert c0.object_name == "系统操控"
    assert c0.precondition_steps == ["音量调到50%"]
    assert len(c0.steps) == 1
    assert c0.steps[0].text == "增大音量"
    iq = next(c for c in cases if c.case_id == "IQ-SCR-071")
    assert iq.precondition_steps == ["打开爱奇艺", "点击筛选", "点击热播榜"]
    assert iq.steps[0].text == "点击电影"
    ids = [c.case_id for c in cases]
    assert ids == [
        "SYS2-001",
        "SYS2-032",
        "SYS2-009",
        "SYS2-054",
        "SYS2-058-02",
        "IQ-SCR-001",
        "IQ-SCR-026",
        "IQ-SCR-071",
        "IQ-SCR-093",
        "IQ-SCR-111",
    ]


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
    assert plan.package == "com.qiyi.video"
    assert plan.any_success is True
    assert "am start" in plan.commands[0]
    settings = plan_deterministic_step("打开设置", packages=packages)
    assert settings is not None and settings.prefer_settings_intent is True
    vol = plan_deterministic_step("音量调到50%", packages=packages)
    assert vol is not None and vol.kind == "volume"
    home = plan_deterministic_step("回桌面", packages=packages)
    assert home is not None and home.kind == "home"
    click = plan_deterministic_step("点击筛选", packages=packages)
    assert click is None


def test_coverage_skips_gui_without_cache(tmp_path: Path):
    cases = load_cases(SAMPLE)
    cfg_path = tmp_path / "device.yaml"
    cfg_path.write_text(
        """
adb:
  host: "127.0.0.1"
capture: {}
precondition:
  enabled: true
  coverage_check: true
  reuse_passed: true
  verify_after_replay: false
  cache_dir: artifacts/precondition_cache
  packages_path: configs/packages.yaml
""",
        encoding="utf-8",
    )
    cfg = load_config(cfg_path)
    report = build_coverage_report(cases, cfg=cfg.precondition)
    ready_ids = {c.case_id for c in report.cases if c.ready}
    skip_ids = report.skip_case_ids
    # v0.12: many system steps are deterministic-only
    assert "SYS2-001" in ready_ids  # 音量调到50%
    assert "SYS2-032" in ready_ids  # 亮度调到50%
    assert "SYS2-009" in ready_ids  # 静音
    assert "SYS2-058-02" in ready_ids  # 回桌面
    assert "IQ-SCR-001" in ready_ids  # 打开爱奇艺
    assert "SYS2-054" in skip_ids  # 打开设置 → 点击显示
    assert "IQ-SCR-026" in skip_ids  # 点击筛选
