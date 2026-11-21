from echo_eval.config import _load_simple_yaml
from echo_eval.judge.model_config import load_vl_eval_config


def test_testapp_style_anchor_merge(tmp_path):
    text = """
models:
  default: &doubao
    api_format: "openai"
    base_url: "https://example.com/v1"
    api_key: "secret-key"
    model_id: "doubao-seed-2.1-pro"
    temperature: 1
    timeout: 120

  local:
    <<: *doubao

  vl_eval:
    <<: *doubao
"""
    data = _load_simple_yaml(text)
    vl = data["models"]["vl_eval"]
    assert vl["api_key"] == "secret-key"
    assert vl["base_url"] == "https://example.com/v1"
    assert vl["model_id"] == "doubao-seed-2.1-pro"
    assert data["models"]["local"]["api_key"] == "secret-key"


def test_load_vl_eval_resolves_anchors(tmp_path):
    p = tmp_path / "model.yaml"
    p.write_text(
        """
models:
  default: &doubao
    base_url: "https://example.com/v1"
    api_key: "k"
    model_id: "m"
  vl_eval:
    <<: *doubao
""",
        encoding="utf-8",
    )
    cfg = load_vl_eval_config(p)
    assert cfg["api_key"] == "k"
    assert cfg["model_id"] == "m"
