"""Mock judge M2: structured evaluation without calling a VL gateway."""
from __future__ import annotations

import os
from typing import Any


def evaluate_gui_poll_images_mock(
    image_paths: list[str],
    exceptions: list[str],
) -> dict[str, Any]:
    paths = [p for p in (image_paths or []) if p and os.path.isfile(p)]
    names = [os.path.basename(p) for p in paths]
    expects = [str(x).strip() for x in (exceptions or []) if str(x).strip()]

    if not paths:
        parsed = [
            {
                "final_result": "整体",
                "think": "无截图",
                "reason": "缺少截图文件",
                "result": "failed",
                "evidence": [],
            }
        ]
        return {
            "raw": "mock: no images",
            "parsed": parsed,
            "images": [],
            "image_count_total": 0,
            "image_count_used": 0,
        }

    if not expects:
        parsed = [
            {
                "final_result": "整体",
                "think": "无期望",
                "reason": "缺少 exceptions/预期结果",
                "result": "failed",
                "evidence": [],
            }
        ]
        return {
            "raw": "mock: no exceptions",
            "parsed": parsed,
            "images": names,
            "image_count_total": len(paths),
            "image_count_used": len(paths),
        }

    step_items = [
        {
            "expectation": exp,
            "think": "mock 有图有期望",
            "reason": "MockJudge M2：存在截图且期望非空，按通过处理",
            "result": "success",
        }
        for exp in expects
    ]
    evidence = [
        {
            "image": names[0],
            "reason": "Mock：首帧存在，作为占位证据",
            "elements": ["mock"],
        }
    ]
    if len(names) > 1:
        evidence.append(
            {
                "image": names[-1],
                "reason": "Mock：末帧存在，作为占位证据",
                "elements": ["mock"],
            }
        )
    step_items.append(
        {
            "final_result": "整体",
            "think": "mock pass",
            "reason": "MockJudge M2：帧与期望均具备",
            "result": "success",
            "evidence": evidence,
        }
    )
    return {
        "raw": "mock: pass",
        "parsed": step_items,
        "images": names,
        "image_count_total": len(paths),
        "image_count_used": len(paths),
    }
