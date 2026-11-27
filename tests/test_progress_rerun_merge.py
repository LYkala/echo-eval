"""Partial rerun must keep sibling cases in progress.json."""
from __future__ import annotations

import json
from pathlib import Path

from echo_eval.runner import progress as progress_mod


def test_init_progress_keeps_siblings_on_partial_rerun(tmp_path: Path, monkeypatch):
    path = tmp_path / "progress.json"
    monkeypatch.setenv("ECHO_EVAL_PROGRESS_FILE", str(path))

    progress_mod.init_progress(
        [("case_a", "A"), ("case_b", "B"), ("case_c", "C")]
    )
    progress_mod.update_case(
        index=1,
        case_id="case_a",
        name="A",
        status="passed",
        device_status="done",
        judge_status="passed",
        run_dir=str(tmp_path / "a"),
    )
    progress_mod.update_case(
        index=2,
        case_id="case_b",
        name="B",
        status="failed",
        device_status="done",
        judge_status="failed",
        run_dir=str(tmp_path / "b"),
    )
    progress_mod.update_case(
        index=3,
        case_id="case_c",
        name="C",
        status="passed",
        device_status="done",
        judge_status="passed",
        run_dir=str(tmp_path / "c"),
    )

    # Single-case overwrite rerun of B only.
    progress_mod.init_progress([("case_b", "B")])
    data = json.loads(path.read_text(encoding="utf-8"))
    ids = [c["case_id"] for c in data["cases"]]
    assert ids == ["case_a", "case_b", "case_c"]
    by_id = {c["case_id"]: c for c in data["cases"]}
    assert by_id["case_a"]["status"] == "passed"
    assert by_id["case_a"]["run_dir"].endswith("a")
    assert by_id["case_c"]["status"] == "passed"
    assert by_id["case_b"]["status"] == "pending"
    assert by_id["case_b"]["device_status"] == "pending"
    assert "run_dir" not in by_id["case_b"]
    assert data["total"] == 3
