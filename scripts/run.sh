#!/usr/bin/env bash
# 免 pip install：用 PYTHONPATH 直接跑 CLI（适合代理导致 pip 失败的环境）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PYTHONPATH="$ROOT/src${PYTHONPATH:+:$PYTHONPATH}"
cd "$ROOT"
exec python -m echo_eval "$@"
