"""Load TestApp-compatible models.vl_eval from model.yaml."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Any

from echo_eval.config import REPO_ROOT, _load_config_text

DEFAULT_MODEL_PATH = REPO_ROOT / "configs" / "model.yaml"


class ModelConfigError(RuntimeError):
    pass


def model_config_candidates() -> list[Path]:
    """Resolve order: explicit VL_EVAL_CONFIG → configs/model.yaml → vendor TestApp."""
    out: list[Path] = []
    env = (os.environ.get("VL_EVAL_CONFIG") or "").strip()
    if env:
        out.append(Path(env))
    # Default for echo-eval: in-repo configs/model.yaml (TestApp-compatible).
    out.append(DEFAULT_MODEL_PATH)
    out.append(REPO_ROOT / "vendor" / "testApp" / "configs" / "model.yaml")
    # dedupe
    seen: set[str] = set()
    ordered: list[Path] = []
    for p in out:
        key = str(p.resolve()) if p.exists() else str(p)
        if key not in seen:
            seen.add(key)
            ordered.append(p)
    return ordered


def find_model_config_path() -> Path | None:
    for p in model_config_candidates():
        if p.is_file():
            return p.resolve()
    return None


def load_vl_eval_config(path: Path | str | None = None) -> dict[str, Any]:
    if path:
        cfg_path = Path(path)
    else:
        found = find_model_config_path()
        if not found:
            looked = ", ".join(str(p) for p in model_config_candidates())
            raise ModelConfigError(
                "未找到 VL 模型配置。请编辑 configs/model.yaml（与 TestApp 同结构）填写 api_key，"
                f"或设置 VL_EVAL_CONFIG 覆盖路径。已查找: {looked}"
            )
        cfg_path = found

    if not cfg_path.is_file():
        raise ModelConfigError(f"模型配置不存在: {cfg_path}")

    raw = _load_config_text(cfg_path, cfg_path.read_text(encoding="utf-8")) or {}
    models = raw.get("models") if isinstance(raw, dict) else None
    if not isinstance(models, dict):
        raise ModelConfigError(f"{cfg_path} 缺少 models")
    entry = models.get("vl_eval")
    if not isinstance(entry, dict):
        raise ModelConfigError(f"{cfg_path} 缺少 models.vl_eval")
    entry = dict(entry)
    # Fallback: if anchors/merge failed or vl_eval only has overrides, fill from default/local
    if not str(entry.get("api_key") or "").strip():
        for fallback_name in ("default", "local"):
            fb = models.get(fallback_name)
            if isinstance(fb, dict) and str(fb.get("api_key") or "").strip():
                merged = dict(fb)
                merged.update({k: v for k, v in entry.items() if v not in (None, "")})
                entry = merged
                break
    for key in ("api_key", "base_url", "model_id"):
        val = str(entry.get(key) or "").strip()
        if not val or val.upper().startswith("YOUR_"):
            raise ModelConfigError(
                f"{cfg_path} 的 models.vl_eval.{key} 为空或仍是占位符。"
                "若使用 TestApp 的 &anchor / <<: *alias 语法，请 git pull 后重试"
                "（简易解析器已支持锚点合并）。"
            )
    entry["_config_path"] = str(cfg_path)
    return entry
