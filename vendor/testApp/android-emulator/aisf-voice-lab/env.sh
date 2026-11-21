#!/usr/bin/env bash
# Source this file:  source ./env.sh
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EMULATOR_ROOT="$(cd "${LAB_ROOT}/.." && pwd)"

export AISF_LAB_ROOT="${LAB_ROOT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${LAB_ROOT}/sdk}"
export ANDROID_HOME="${ANDROID_SDK_ROOT}"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)" 2>/dev/null || echo /usr/bin/java)")")}"

export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/emulator:${PATH}"

export AISF_AVD_NAME="${AISF_AVD_NAME:-AISF_EulerOS_ARM64}"
export AISF_SYSTEM_IMAGE="${AISF_SYSTEM_IMAGE:-system-images;android-33;google_apis;arm64-v8a}"
export AISF_APK="${AISF_APK:-${EMULATOR_ROOT}/aisf_voice_assistant_tv-phoneRelease-20260715_103645.apk}"
export AISF_PACKAGE="${AISF_PACKAGE:-com.huawei.aisftvassistant}"
export AISF_ACTIVITY="${AISF_ACTIVITY:-com.huawei.aisftvassistant/.activity.PhoneLaunchActivity}"
export AISF_SERIAL="${AISF_SERIAL:-emulator-5554}"
export AISF_INPUT_WAV="${AISF_INPUT_WAV:-${LAB_ROOT}/input/weather_question.wav}"
export AISF_VIRTUAL_MIC="${AISF_VIRTUAL_MIC:-VirtualMic}"
# Emulator mock GPS (adb emu geo fix LON LAT). Default: Xi'an Bell Tower.
# Permission alone does not change coordinates — AVD default is often US West Coast.
export AISF_GEO_LON="${AISF_GEO_LON:-108.9398}"
export AISF_GEO_LAT="${AISF_GEO_LAT:-34.3416}"
export AISF_GEO_ALT="${AISF_GEO_ALT:-400}"
# Display (Amap/UI need more than 320x480; change requires emulator restart)
export AISF_LCD_WIDTH="${AISF_LCD_WIDTH:-720}"
export AISF_LCD_HEIGHT="${AISF_LCD_HEIGHT:-1280}"
export AISF_LCD_DENSITY="${AISF_LCD_DENSITY:-320}"

mkdir -p "${LAB_ROOT}/downloads" "${LAB_ROOT}/runs" "${LAB_ROOT}/dumps" "${ANDROID_SDK_ROOT}"
if [[ -f "${LAB_ROOT}/.java_env" ]]; then
  # shellcheck disable=SC1091
  source "${LAB_ROOT}/.java_env"
fi
export PATH="${LAB_ROOT}/tools/bin:${PATH}"
