#!/usr/bin/env bash
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh"

APK="${LAB_ROOT}/apk/aisf_voice_assistant.apk"
if [[ ! -f "${APK}" ]]; then
  APK="${AISF_APK}"
fi
if [[ ! -f "${APK}" ]]; then
  echo "AISF apk missing. Run: bash download_deps.sh" >&2
  exit 1
fi

adb wait-for-device
serial="${AISF_SERIAL}"
if ! adb devices | grep -q "${serial}[[:space:]]*device"; then
  serial="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi

echo "Installing ${APK} on ${serial} ..."
adb -s "${serial}" install -r "${APK}"

adb -s "${serial}" shell pm grant "${AISF_PACKAGE}" android.permission.RECORD_AUDIO || true
adb -s "${serial}" shell appops set "${AISF_PACKAGE}" RECORD_AUDIO allow || true

echo "Launching AISF ..."
adb -s "${serial}" shell am start -W -n "${AISF_ACTIVITY}"
echo "Installed and launched."
