#!/usr/bin/env bash
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh"

INPUT="${1:-${AISF_INPUT_WAV}}"
STAMP="$(date +%Y%m%d_%H%M%S)"
RUN_DIR="${LAB_ROOT}/runs/${STAMP}"
mkdir -p "${RUN_DIR}"

echo "=== AISF voice E2E ==="
echo "Run dir: ${RUN_DIR}"

python3 "${LAB_ROOT}/check_setup.py" --quick
bash "${LAB_ROOT}/setup_virtual_mic.sh"
bash "${LAB_ROOT}/start_emulator.sh"
bash "${LAB_ROOT}/install_aisf.sh"

python3 "${LAB_ROOT}/test_aisf_voice.py" \
  --serial "${AISF_SERIAL}" \
  --input "${INPUT}" \
  --output-dir "${RUN_DIR}"

echo "Done. Results in ${RUN_DIR}"
