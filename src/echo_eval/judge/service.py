"""Orchestrate multi-frame judge over a capture run directory or image list."""
from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any, Literal

from echo_eval.judge.gui_poll import evaluate_gui_poll_images
from echo_eval.judge.mock import evaluate_gui_poll_images_mock
from echo_eval.judge.model_config import ModelConfigError, find_model_config_path, load_vl_eval_config
from echo_eval.judge.schema import normalize_exceptions, parse_evaluation_passed

Backend = Literal["auto", "vl", "mock"]


class JudgeError(RuntimeError):
    pass


IMAGE_GLOBS = ("*.jpg", "*.jpeg", "*.png", "*.webp")


def collect_images(run_dir: Path | None = None, images: list[str] | None = None) -> list[str]:
    if images:
        out = []
        for item in images:
            p = Path(item)
            if p.is_file():
                out.append(str(p.resolve()))
        return sorted(out)

    if not run_dir:
        raise JudgeError("必须提供 --run-dir 或 --image")

    run_dir = Path(run_dir)
    if not run_dir.is_dir():
        raise JudgeError(f"run-dir 不存在: {run_dir}")

    frames = run_dir / "frames"
    search_roots = [frames] if frames.is_dir() else [run_dir]
    found: list[Path] = []
    for root in search_roots:
        for pattern in IMAGE_GLOBS:
            found.extend(root.glob(pattern))
    # Prefer frame_*.jpg ordering
    found = sorted({p.resolve() for p in found if p.is_file() and p.stat().st_size > 0})
    frame_named = [p for p in found if p.name.startswith("frame_")]
    pool = frame_named or found
    return [str(p) for p in pool]


def resolve_backend(backend: Backend) -> str:
    if backend == "vl":
        return "vl"
    if backend == "mock":
        return "mock"
    # auto
    return "vl" if find_model_config_path() else "mock"


def judge_images(
    *,
    exceptions: list[str] | str,
    run_dir: Path | str | None = None,
    images: list[str] | None = None,
    backend: Backend = "auto",
    write: bool = True,
) -> dict[str, Any]:
    expects = normalize_exceptions(exceptions)
    paths = collect_images(Path(run_dir) if run_dir else None, images)
    chosen = resolve_backend(backend)

    if chosen == "mock":
        result = evaluate_gui_poll_images_mock(paths, expects)
        source = "mock_m2"
    else:
        try:
            cfg = load_vl_eval_config()
        except ModelConfigError as e:
            raise JudgeError(str(e)) from e
        try:
            result = evaluate_gui_poll_images(paths, expects, model_cfg=cfg)
        except Exception as e:
            raise JudgeError(f"VL 评判失败: {e}") from e
        source = "vl_eval_e1"

    eval_data: dict[str, Any] = {
        "mode": "gui_poll_images",
        "source": source,
        "backend": chosen,
        "exceptions": expects,
        "images": result.get("images") or [],
        "image_count_total": result.get("image_count_total"),
        "image_count_used": result.get("image_count_used"),
        "result_raw": result.get("raw", ""),
        "result_parsed": result.get("parsed"),
        "timestamp": time.time(),
    }
    passed = parse_evaluation_passed(eval_data)
    if passed is not None:
        eval_data["passed"] = passed

    if write and run_dir:
        out = Path(run_dir) / "evaluation.json"
        out.write_text(json.dumps(eval_data, ensure_ascii=False, indent=2), encoding="utf-8")
        eval_data["evaluation_path"] = str(out.resolve())

    return eval_data
