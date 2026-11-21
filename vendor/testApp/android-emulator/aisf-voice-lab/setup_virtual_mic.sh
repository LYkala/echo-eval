#!/usr/bin/env bash
# PulseAudio virtual microphone for emulator hostmicon inject.
# Usage:
#   bash setup_virtual_mic.sh              # legacy: recreate default sink + set as default source
#   bash setup_virtual_mic.sh --ensure     # create AISF_VIRTUAL_MIC sink if missing (multi-AVD safe)
#   bash setup_virtual_mic.sh --restore    # unload modules recorded in .pulse_modules
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Preserve caller AISF_VIRTUAL_MIC before env.sh default (VirtualMic).
_CALLER_VMIC="${AISF_VIRTUAL_MIC:-}"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh"
if [[ -n "${_CALLER_VMIC}" ]]; then
  export AISF_VIRTUAL_MIC="${_CALLER_VMIC}"
fi

export PULSE_RUNTIME_PATH="${PULSE_RUNTIME_PATH:-/tmp/pulse-root}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/pulse-root}"
mkdir -p "${PULSE_RUNTIME_PATH}"
chmod 700 "${PULSE_RUNTIME_PATH}" || true

SINK="${AISF_VIRTUAL_MIC:-VirtualMic}"
STATE="${LAB_ROOT}/.pulse_modules"
MODE="${1:-}"

restore() {
  if [[ -f "${STATE}" ]]; then
    while read -r mod; do
      [[ -n "${mod}" ]] && pactl unload-module "${mod}" 2>/dev/null || true
    done < "${STATE}"
    rm -f "${STATE}"
  fi
  echo "PulseAudio modules restored."
}

ensure_pulse() {
  export PULSE_RUNTIME_PATH="${PULSE_RUNTIME_PATH:-/tmp/pulse-root}"
  export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/pulse-root}"
  mkdir -p "${PULSE_RUNTIME_PATH}"
  chmod 700 "${PULSE_RUNTIME_PATH}" || true
  if pactl info >/dev/null 2>&1; then
    return 0
  fi
  pulseaudio -k 2>/dev/null || true
  pkill -9 pulseaudio 2>/dev/null || true
  sleep 1
  # Headless / root: private runtime dir, never idle-exit.
  pulseaudio -D \
    --exit-idle-time=-1 \
    --log-target=file:/tmp/pulseaudio.log \
    --log-level=info \
    2>/dev/null \
    || pulseaudio --start --daemonize=true --exit-idle-time=-1 2>/dev/null \
    || true
  sleep 2
  # Retry once if cookie/auth race.
  if ! pactl info >/dev/null 2>&1; then
    sleep 2
    pulseaudio -D --exit-idle-time=-1 --log-target=file:/tmp/pulseaudio.log 2>/dev/null || true
    sleep 2
  fi
  if ! pactl info >/dev/null 2>&1; then
    echo "PulseAudio failed to start. See /tmp/pulseaudio.log" >&2
    tail -40 /tmp/pulseaudio.log 2>/dev/null || true
    return 1
  fi
}

ensure_sink() {
  local sink="$1"
  if pactl list short sinks 2>/dev/null | awk '{print $2}' | grep -qx "${sink}"; then
    echo "[ok] sink ${sink} already present"
    return 0
  fi
  local mod_sink
  mod_sink="$(pactl load-module module-null-sink sink_name="${sink}" sink_properties=device.description="${sink}")"
  echo "${mod_sink}" >> "${STATE}"
  echo "[ok] created sink ${sink} (module ${mod_sink})"
}

if [[ "${MODE}" == "--restore" ]]; then
  restore
  exit 0
fi

if ! command -v pactl >/dev/null 2>&1; then
  echo "Install pulseaudio first: sudo yum install -y pulseaudio pulseaudio-utils" >&2
  exit 1
fi

ensure_pulse
touch "${STATE}"

if [[ "${MODE}" == "--ensure" ]]; then
  # Multi-AVD safe: only ensure named sink exists; do not wipe other sinks / default source.
  ensure_sink "${SINK}"
  echo "[ok] ensure mic source = ${SINK}.monitor"
  pactl list short sinks | grep -E "${SINK}|VirtualMic" || true
  exit 0
fi

# Legacy single-mic path (destructive to recorded modules).
restore
: > "${STATE}"
ensure_sink "${SINK}"

# Route monitor to real speakers (optional, helps debugging).
mod_loop="$(pactl load-module module-loopback source="${SINK}.monitor" latency_msec=20 2>/dev/null || true)"
if [[ -n "${mod_loop}" ]]; then
  echo "${mod_loop}" >> "${STATE}"
fi

pactl set-default-source "${SINK}.monitor"
echo "[ok] default mic source = ${SINK}.monitor"
pactl info | head -20 || true
echo "Play test wav into sink:"
echo "  paplay --device=${SINK} ../audio_samples/weather_question.wav"
echo "Then enable emulator mic:"
echo "  adb -s ${AISF_SERIAL} emu avd hostmicon"
