#!/usr/bin/env bash
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh"

if avdmanager list avd | grep -q "Name: ${AISF_AVD_NAME}"; then
  echo "AVD already exists: ${AISF_AVD_NAME}"
  exit 0
fi

echo "Creating AVD: ${AISF_AVD_NAME}"
echo "System image: ${AISF_SYSTEM_IMAGE}"
yes | avdmanager create avd \
  -f \
  -n "${AISF_AVD_NAME}" \
  -k "${AISF_SYSTEM_IMAGE}" \
  -d "pixel_6" \
  --abi "arm64-v8a"

echo "AVD created: ${AISF_AVD_NAME}"
