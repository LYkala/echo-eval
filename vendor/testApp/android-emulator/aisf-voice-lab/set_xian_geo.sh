#!/bin/bash
# Inject Xi'an mock GPS into the running emulator (Gaode/AISF device location).
# Note: `adb emu geo fix` alone often does NOT update fused/gps last-location on this
# headless AVD — override providers via `cmd location` instead.
set -eu
LAB_ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh" 2>/dev/null || true

ADB_BIN="${ANDROID_SDK_ROOT:-${LAB_ROOT}/sdk}/platform-tools/adb"
if [[ -x "${LAB_ROOT}/mini_adb_auth.py" ]]; then
  ADB=(python3 "${LAB_ROOT}/mini_adb_auth.py")
elif [[ -x "$ADB_BIN" ]]; then
  ADB=("$ADB_BIN")
else
  echo "[geo] no adb" >&2
  exit 1
fi

LON="${AISF_GEO_LON:-108.9398}"
LAT="${AISF_GEO_LAT:-34.3416}"
echo "[geo] set mock location lat=${LAT} lon=${LON}"

"${ADB[@]}" shell appops set com.android.shell android:mock_location allow || true
"${ADB[@]}" shell appops set 2000 android:mock_location allow || true

for p in gps network fused mockgps; do
  "${ADB[@]}" shell cmd location providers remove-test-provider "$p" >/dev/null 2>&1 || true
done

"${ADB[@]}" shell cmd location providers add-test-provider gps \
  --requiresSatellite --supportsAltitude --supportsSpeed --supportsBearing
"${ADB[@]}" shell cmd location providers add-test-provider network \
  --requiresNetwork --supportsAltitude
"${ADB[@]}" shell cmd location providers add-test-provider fused \
  --supportsAltitude --supportsSpeed --supportsBearing

for p in gps network fused; do
  "${ADB[@]}" shell cmd location providers set-test-provider-enabled "$p" true
  "${ADB[@]}" shell cmd location providers set-test-provider-location "$p" \
    --location "${LAT},${LON}" --accuracy 5
done

"${ADB[@]}" shell settings put secure location_mode 3 || true
"${ADB[@]}" shell settings put secure location_providers_allowed +gps,+network || true
# Best-effort console inject (may not update fused on this AVD)
"${ADB[@]}" emu geo fix "$LON" "$LAT" "${AISF_GEO_ALT:-400}" >/dev/null 2>&1 || true

echo "[geo] verify:"
"${ADB[@]}" shell dumpsys location | grep -E 'last location=Location' | head -10
echo "[geo] DONE"
