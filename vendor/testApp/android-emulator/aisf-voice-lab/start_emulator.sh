#!/usr/bin/env bash
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh"

if adb devices | awk 'NR>1 && $2=="device"{print $1}' | grep -q .; then
  echo "Device already online:"
  adb devices -l
  exit 0
fi

if [[ ! -d "/dev/kvm" && ! -e "/dev/kvm" ]]; then
  echo "[warn] /dev/kvm not found. Emulator will be slow (software mode)."
  EXTRA_QEMU=(-accel off)
else
  EXTRA_QEMU=()
fi

echo "Starting ${AISF_AVD_NAME} (headless) ..."
nohup "${ANDROID_SDK_ROOT}/emulator/emulator" \
  -avd "${AISF_AVD_NAME}" \
  -no-window \
  -no-snapshot-save \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  -netdelay none \
  -netspeed full \
  "${EXTRA_QEMU[@]}" \
  > "${LAB_ROOT}/runs/emulator.log" 2>&1 &

echo "Waiting for boot ..."
adb wait-for-device
deadline=$((SECONDS + 300))
while (( SECONDS < deadline )); do
  boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "${boot}" == "1" ]]; then
    adb devices -l
    echo "BOOT_OK"
    # DDS cloud + Hub DNAT: guest CONNECT gw (best-effort)
    if [[ -x "${LAB_ROOT}/start_emu_dds_guest_gw.sh" ]]; then
      echo "Applying DDS guest network bridge..."
      bash "${LAB_ROOT}/start_emu_dds_guest_gw.sh" || echo "[warn] DDS guest gw apply failed"
    fi
    # Keep watcher so later emulator restarts also get the bridge
    if [[ -x "${LAB_ROOT}/install_emu_dds_net_watch.sh" ]]; then
      bash "${LAB_ROOT}/install_emu_dds_net_watch.sh" || true
    fi
    GEO_LON="${AISF_GEO_LON:-108.9398}"
    GEO_LAT="${AISF_GEO_LAT:-34.3416}"
    GEO_ALT="${AISF_GEO_ALT:-400}"
    echo "Setting geo fix lon=${GEO_LON} lat=${GEO_LAT}..."
    adb shell settings put secure location_mode 3 || true
    adb emu geo fix "$GEO_LON" "$GEO_LAT" "$GEO_ALT" || echo "[warn] geo fix failed"
    exit 0
  fi
  sleep 3
done
echo "Boot timeout. See ${LAB_ROOT}/runs/emulator.log" >&2
exit 1
