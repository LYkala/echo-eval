"""v0.29 same-ID multi-row merge (方案 A)."""
from __future__ import annotations

from pathlib import Path

from echo_eval.cases.excel_cases import load_cases

SAMPLE = Path("cases/examples/sample_v029_10cases.csv")


def test_sample_v029_ten_case_ids():
    cases = load_cases(SAMPLE, default_wait_sec=3)
    ids = [c.case_id for c in cases]
    assert len(ids) == 10
    assert len(set(ids)) == 10
    assert ids == [
        "DEMO-001",
        "DEMO-002",
        "DEMO-003",
        "DEMO-010",
        "DEMO-011",
        "DEMO-020",
        "DEMO-021",
        "DEMO-030",
        "DEMO-031",
        "DEMO-040",
    ]


def test_multi_round_merge_precondition_once_and_ordered_steps():
    cases = load_cases(SAMPLE)
    c = next(x for x in cases if x.case_id == "DEMO-020")
    assert c.round_count == 3
    assert len(c.rounds) == 3
    assert c.precondition_steps == ["打开爱奇艺"]
    assert [s.text for s in c.steps] == [
        "点击播放第一个视频",
        "点击全屏",
        "退出全屏",
    ]
    assert [s.round_index for s in c.steps] == [1, 2, 3]
    ex = c.exceptions()
    assert len(ex) == 3
    assert "播放" in ex[0]
    assert "全屏" in ex[1]
    assert "退出全屏" in ex[2] or "勿回系统桌面" in ex[2]


def test_two_round_wifi_empty_later_precondition():
    cases = load_cases(SAMPLE)
    c = next(x for x in cases if x.case_id == "DEMO-010")
    assert c.round_count == 2
    assert c.precondition_steps == ["回到系统桌面"]
    assert [s.text for s in c.steps] == ["打开无线局域网", "关闭无线局域网"]
    assert c.rounds[1].precondition_raw == ""


def test_single_round_unchanged():
    cases = load_cases(SAMPLE)
    c = next(x for x in cases if x.case_id == "DEMO-001")
    assert c.round_count == 1
    assert len(c.steps) == 1
    assert c.steps[0].text == "把音量调高一点"
    assert c.precondition_steps == ["回到系统桌面"]


def test_filter_case_id_returns_merged_group(tmp_path: Path):
    cases = load_cases(SAMPLE, case_id="DEMO-020")
    assert len(cases) == 1
    assert cases[0].round_count == 3


def test_merge_noncontiguous_same_id(tmp_path: Path):
    p = tmp_path / "gap.csv"
    p.write_text(
        "用例 ID,用例名称,预置条件,文本语料,预期结果,轮次\n"
        "A1,连续,回桌面,第一句,预期一,1\n"
        "B1,中间,回桌面,中间句,预期中,1\n"
        "A1,连续,,第二句,预期二,2\n",
        encoding="utf-8",
    )
    cases = load_cases(p)
    assert [c.case_id for c in cases] == ["A1", "B1"]
    a1 = cases[0]
    assert [s.text for s in a1.steps] == ["第一句", "第二句"]
    assert a1.precondition_steps == ["回桌面"]
    assert a1.round_count == 2
