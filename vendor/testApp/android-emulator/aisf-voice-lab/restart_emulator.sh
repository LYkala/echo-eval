#!/bin/bash
# Full restart of the ARM Android emulator on TARGET, then apply DDS guest net.
set -eu
LAB_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$LAB_ROOT"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh" 2>/dev/null || true

AVD_NAME="${AISF_AVD_NAME:-AISF_EulerOS_ARM64}"
SDK="${ANDROID_SDK_ROOT:-${LAB_ROOT}/sdk}"
QEMU="${SDK}/emulator/qemu/linux-aarch64/qemu-system-aarch64-headless"
EMU_BIN="${SDK}/emulator/emulator"
LOG="${LAB_ROOT}/runs/emulator.log"
ADB="python3 ${LAB_ROOT}/mini_adb_auth.py"
HTTP_PROXY_ARG="${EMU_HTTP_PROXY:-http://127.0.0.1:8888}"

mkdir -p "${LAB_ROOT}/runs"

# Resolve AVD home: managed AVDs live under /home/android-avds/avd (e.g. avd_2).
AVD_DIR=""
for home in "${ANDROID_AVD_HOME:-}" "/home/android-avds/avd" "${HOME}/.android/avd"; do
  [[ -z "${home}" ]] && continue
  if [[ -d "${home}/${AVD_NAME}.avd" ]]; then
    AVD_DIR="${home}/${AVD_NAME}.avd"
    export ANDROID_AVD_HOME="${home}"
    export ANDROID_SDK_HOME="$(dirname "${home}")"
    break
  fi
done
if [[ -z "${AVD_DIR}" ]]; then
  for home in "/home/android-avds/avd" "${HOME}/.android/avd"; do
    mapfile -t found < <(find "${home}" -maxdepth 1 -type d -name '*.avd' 2>/dev/null | head -5)
    if [[ ${#found[@]} -eq 1 ]]; then
      AVD_DIR="${found[0]}"
      AVD_NAME="$(basename "${AVD_DIR}" .avd)"
      export ANDROID_AVD_HOME="${home}"
      export ANDROID_SDK_HOME="$(dirname "${home}")"
      echo "[restart] AVD fallback -> ${AVD_NAME} at ${AVD_DIR}"
      break
    fi
  done
fi
if [[ -z "${AVD_DIR}" ]]; then
  echo "[restart] AVD not found: ${AVD_NAME}" >&2
  exit 1
fi
echo "[restart] using AVD=${AVD_NAME} dir=${AVD_DIR}"

# Ensure AVD LCD size (default 720x1280) before start
if [[ -f "${AVD_DIR}/config.ini" ]]; then
  LCD_W="${AISF_LCD_WIDTH:-720}"
  LCD_H="${AISF_LCD_HEIGHT:-1280}"
  LCD_D="${AISF_LCD_DENSITY:-320}"
  python3 - "$AVD_DIR" "$LCD_W" "$LCD_H" "$LCD_D" <<'PY'
import sys
from pathlib import Path
avd, w, h, d = sys.argv[1:5]
p = Path(avd) / "config.ini"
text = p.read_text(encoding="utf-8", errors="replace")
drop = ("hw.lcd.width=", "hw.lcd.height=", "hw.lcd.density=", "hw.lcd.depth=", "skin.name=", "skin.path=")
lines = [ln for ln in text.splitlines() if not ln.startswith(drop)]
lines += [
    f"hw.lcd.width={w}",
    f"hw.lcd.height={h}",
    "hw.lcd.depth=16",
    f"hw.lcd.density={d}",
    f"skin.name={w}x{h}",
    "skin.path=_no_skin",
]
p.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"[restart] AVD LCD {w}x{h} dens={d} -> {p}")
PY
fi

echo "[restart] stopping qemu..."
pkill -f "qemu-system-aarch64" 2>/dev/null || true
# also stop wrapper if present
pkill -f "${SDK}/emulator/emulator" 2>/dev/null || true
sleep 3
pkill -9 -f "qemu-system-aarch64" 2>/dev/null || true
sleep 2

if [[ ! -x "$QEMU" && ! -x "$EMU_BIN" ]]; then
  echo "[restart] missing emulator binary under $SDK" >&2
  exit 1
fi

# Public names need local DoH (target has no direct DNS); proxy covers HTTPS CONNECT.
# Never `pkill -f doh_dns_proxy` in the same cmdline that starts it (self-kill).
if [[ -f "${LAB_ROOT}/doh_dns_proxy.py" ]]; then
  printf 'nameserver 127.0.0.1\noptions timeout:2 attempts:2\n' > /etc/resolv.conf
  if ! ss -ulnp 2>/dev/null | grep -q '127.0.0.1:53'; then
    nohup python3 "${LAB_ROOT}/doh_dns_proxy.py" >/tmp/doh_dns.log 2>&1 &
    echo $! > /tmp/doh_dns.pid
    sleep 1
  fi
  if [[ -x "${LAB_ROOT}/watch_doh_dns.sh" ]] && ! kill -0 "$(cat /tmp/doh_watch.pid 2>/dev/null)" 2>/dev/null; then
    nohup bash "${LAB_ROOT}/watch_doh_dns.sh" >/tmp/doh_watch.out 2>&1 &
    echo $! > /tmp/doh_watch.pid
  fi
fi
DNS_SERVER_ARG="${EMU_DNS_SERVER:-127.0.0.1}"

# Pulse VirtualMic must be up before qemu (-allow-host-audio / hostmicon inject).
export PULSE_RUNTIME_PATH="${PULSE_RUNTIME_PATH:-/tmp/pulse-root}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/pulse-root}"
if [[ -x "${LAB_ROOT}/setup_virtual_mic.sh" ]]; then
  echo "[restart] ensuring PulseAudio VirtualMic..."
  bash "${LAB_ROOT}/setup_virtual_mic.sh" || echo "[restart] warn: setup_virtual_mic failed"
else
  echo "[restart] warn: missing setup_virtual_mic.sh" >&2
fi

echo "[restart] starting ${AVD_NAME} (proxy=${HTTP_PROXY_ARG} dns=${DNS_SERVER_ARG})..."
# Force SDL/QEMU onto Pulse so VirtualMic.monitor can feed hostmicon.
AUDIO_ENV=(
  "PULSE_RUNTIME_PATH=${PULSE_RUNTIME_PATH}"
  "XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR}"
  "PULSE_SERVER=unix:${PULSE_RUNTIME_PATH}/native"
  "SDL_AUDIODRIVER=pulse"
  "QEMU_AUDIO_DRV=pa"
)
if [[ -x "$QEMU" ]]; then
  nohup env "${AUDIO_ENV[@]}" \
    "$QEMU" \
    -avd "$AVD_NAME" \
    -no-window \
    -no-snapshot \
    -no-snapshot-load \
    -no-snapshot-save \
    -gpu swiftshader_indirect \
    -no-boot-anim \
    -no-metrics \
    -feature -Vulkan \
    -audio sdl \
    -allow-host-audio \
    -http-proxy "$HTTP_PROXY_ARG" \
    -dns-server "$DNS_SERVER_ARG" \
    >"$LOG" 2>&1 &
else
  nohup env "${AUDIO_ENV[@]}" \
    "$EMU_BIN" \
    -avd "$AVD_NAME" \
    -no-window \
    -no-snapshot \
    -no-snapshot-load \
    -no-snapshot-save \
    -gpu swiftshader_indirect \
    -no-boot-anim \
    -http-proxy "$HTTP_PROXY_ARG" \
    -dns-server "$DNS_SERVER_ARG" \
    >"$LOG" 2>&1 &
fi
echo $! > /tmp/aisf_emulator.pid
echo "[restart] qemu pid=$(cat /tmp/aisf_emulator.pid)"

echo "[restart] waiting for boot..."
deadline=$((SECONDS + 420))
booted=0
while (( SECONDS < deadline )); do
  boot="$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | tail -1 || true)"
  if [[ "$boot" == "1" ]]; then
    booted=1
    break
  fi
  sleep 5
done

if [[ "$booted" -ne 1 ]]; then
  echo "[restart] boot timeout; see $LOG" >&2
  exit 1
fi
echo "[restart] BOOT_OK"

if [[ -x "${LAB_ROOT}/start_emu_dds_guest_gw.sh" ]]; then
  echo "[restart] applying DDS guest network..."
  bash "${LAB_ROOT}/start_emu_dds_guest_gw.sh" || echo "[restart] warn: DDS gw failed"
fi
if [[ -x "${LAB_ROOT}/install_emu_dds_net_watch.sh" ]]; then
  bash "${LAB_ROOT}/install_emu_dds_net_watch.sh" || true
fi

echo "[restart] guest public-net tweaks..."
$ADB shell settings put global private_dns_mode off || true
$ADB shell settings put global captive_portal_mode 0 || true
# Do NOT set Android http_proxy — conflicts with qemu -http-proxy.
$ADB shell settings put global http_proxy :0 || true
$ADB shell su 0 cmd wifi connect-network AndroidWifi open || true

# Mock GPS → Xi'an (or AISF_GEO_*). Location permission ≠ coordinates.
if [[ -x "${LAB_ROOT}/set_xian_geo.sh" ]]; then
  bash "${LAB_ROOT}/set_xian_geo.sh" || echo "[restart] warn: set_xian_geo failed"
else
  GEO_LON="${AISF_GEO_LON:-108.9398}"
  GEO_LAT="${AISF_GEO_LAT:-34.3416}"
  GEO_ALT="${AISF_GEO_ALT:-400}"
  echo "[restart] setting geo fix lon=${GEO_LON} lat=${GEO_LAT} alt=${GEO_ALT}..."
  $ADB shell settings put secure location_mode 3 || true
  $ADB emu geo fix "$GEO_LON" "$GEO_LAT" "$GEO_ALT" || echo "[restart] warn: geo fix failed"
fi
# Hide Accessibility floating button (Android robot); keep services on.
echo "[restart] hide accessibility floating button..."
$ADB shell settings delete secure accessibility_button_targets >/dev/null 2>&1 || true
$ADB shell settings put secure accessibility_button_mode 0 || true

# Route host mic into guest for voice-assistant inject (paplay → VirtualMic).
echo "[restart] enable emulator hostmicon..."
$ADB emu avd hostmicon || echo "[restart] warn: hostmicon failed"
echo "[restart] DONE"
