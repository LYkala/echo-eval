"""Local case evaluation via Doubao (vl_eval): reply text + final screenshot."""
from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


def parse_evaluation_passed(eval_data: Optional[Dict[str, Any]]) -> Optional[bool]:
    if not eval_data:
        return None
    if eval_data.get("passed") is not None:
        return bool(eval_data.get("passed"))
    parsed = eval_data.get("result_parsed")
    if not isinstance(parsed, list):
        return None
    round_items = [x for x in parsed if x.get("round") is not None and x.get("result")]
    if round_items and any(x.get("result") != "success" for x in round_items):
        return False
    for item in parsed:
        if item.get("final_result") is not None:
            return item.get("result") == "success"
    step_items = [
        x
        for x in parsed
        if isinstance(x, dict) and x.get("result") and x.get("final_result") is None
    ]
    if step_items:
        return all(x.get("result") == "success" for x in step_items)
    return None


def extract_exceptions(testcase: Dict[str, Any]) -> List[str]:
    raw = testcase.get("exceptions")
    if raw is None or raw == []:
        raw = testcase.get("expections")
    if not raw:
        return []
    if isinstance(raw, str):
        return [ln.strip() for ln in raw.replace("\r", "").split("\n") if ln.strip()]
    if isinstance(raw, list):
        return [str(x).strip() for x in raw if str(x).strip()]
    return [str(raw).strip()]


def load_testcase_for_case(run_root: Path, case_id: str) -> Dict[str, Any]:
    for name in (f"{case_id}.json",):
        p = run_root / "runtime_json" / name
        if p.is_file():
            try:
                return json.loads(p.read_text(encoding="utf-8"))
            except Exception:
                pass
    # safe_name variants
    for p in (run_root / "runtime_json").glob("*.json") if (run_root / "runtime_json").is_dir() else []:
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            continue
        if (data.get("testId") or p.stem) == case_id:
            return data
    return {}


def pick_final_image(case_dir: Path) -> Optional[Path]:
    if not case_dir.is_dir():
        return None
    preferred = [
        "final_result.jpg",
        "final_result.png",
        "zz_after.jpg",
        "zz_after.png",
        "result_final.jpg",
        "result_final.png",
    ]
    for name in preferred:
        p = case_dir / name
        if p.is_file() and p.stat().st_size > 0:
            return p
    shots = [
        p
        for p in case_dir.iterdir()
        if p.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp") and p.stat().st_size > 0
    ]
    if not shots:
        return None
    # prefer last turn end / highest mtime
    ends = [p for p in shots if "_end" in p.name or "zz_" in p.name]
    pool = ends or shots
    return max(pool, key=lambda x: x.stat().st_mtime)


def replies_from_result(result: Dict[str, Any]) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    for i, turn in enumerate(result.get("turns") or [], 1):
        out.append(
            {
                "round": i,
                "user_message": turn.get("text") or "",
                "reply_text": turn.get("reply_text")
                or (turn.get("metrics") or {}).get("reply_text")
                or "",
            }
        )
    return out


def evaluate_case_dir(
    case_dir: Path,
    *,
    testcase: Optional[Dict[str, Any]] = None,
    result: Optional[Dict[str, Any]] = None,
    exceptions: Optional[List[str]] = None,
) -> Tuple[bool, str, Optional[Dict[str, Any]]]:
    """Run Doubao hybrid eval; write evaluation.json. Returns (ok, detail, eval_data)."""
    case_dir = Path(case_dir)
    if result is None:
        rj = case_dir / "result.json"
        result = json.loads(rj.read_text(encoding="utf-8")) if rj.is_file() else {}
    if exceptions is None:
        exceptions = extract_exceptions(testcase or {})
    if not exceptions:
        return False, "用例无 exceptions/预期结果，跳过评判", None

    image = pick_final_image(case_dir)
    if not image:
        return False, "无可用截图，跳过评判", None

    replies = replies_from_result(result or {})
    try:
        from llm_eval import evaluate_reply_and_image
    except Exception as e:
        return False, f"加载 llm_eval 失败: {e}", None

    try:
        eval_result = evaluate_reply_and_image(
            str(image),
            exceptions,
            replies=replies,
        )
    except Exception as e:
        return False, f"豆包评判调用失败: {e}", None

    eval_data: Dict[str, Any] = {
        "mode": "reply_and_image",
        "source": "doubao_vl_eval",
        "image": image.name,
        "exceptions": exceptions,
        "rounds": replies,
        "result_raw": (eval_result or {}).get("raw", ""),
        "result_parsed": (eval_result or {}).get("parsed"),
        "timestamp": time.time(),
    }
    passed = parse_evaluation_passed(eval_data)
    if passed is not None:
        eval_data["passed"] = passed
    eval_path = case_dir / "evaluation.json"
    eval_path.write_text(json.dumps(eval_data, ensure_ascii=False, indent=2), encoding="utf-8")
    return True, str(eval_path), eval_data


def load_evaluation(case_dir: Path) -> Optional[Dict[str, Any]]:
    path = Path(case_dir) / "evaluation.json"
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
