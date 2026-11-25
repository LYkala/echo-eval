"""progress.json should retain run_dir for cancel/partial history."""
from __future__ import annotations

import json
import os
from pathlib import Path

from echo_eval.runner.progress import init_progress, update_case


def test_progress_stores_run_dir(tmp_path: Path, monkeypatch):
    prog = tmp_path / "progress.json"
    monkeypatch.setenv("ECHO_EVAL_PROGRESS_FILE", str(prog))
    init_progress([("CASE-1", "示例")])
    update_case(
        index=1,
        case_id="CASE-1",
        name="示例",
        device_status="done",
        device_label="设备执行完成",
        judge_status="queued",
        run_dir=str(tmp_path / "20260101_CASE-1"),
    )
    data = json.loads(prog.read_text(encoding="utf-8"))
    assert data["cases"][0]["run_dir"].endswith("20260101_CASE-1")
    assert data["device_done"] == 1
