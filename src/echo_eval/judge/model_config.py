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
    out: list[Path] = []
    env = (os.environ.get("VL_EVAL_CONFIG") or "").strip()
    if env:
        out.append(Path(env))
    out.append(DEFAULT_MODEL_PATH)
    # Optional: sibling TestApp layout if vendored configs appear later
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
                "未找到 VL 模型配置。请设置 VL_EVAL_CONFIG 指向 TestApp 的 model.yaml，"
                f"或复制 configs/model.yaml.example 为 configs/model.yaml。已查找: {looked}"
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
    for key in ("api_key", "base_url", "model_id"):
        if not str(entry.get(key) or "").strip():
            raise ModelConfigError(f"{cfg_path} 的 models.vl_eval.{key} 为空")
    entry = dict(entry)
    entry["_config_path"] = str(cfg_path)
    return entry
