#!/usr/bin/env bash
# Download Android SDK (linux aarch64) + helper tools for AISF voice lab on EulerOS/openEuler.
# Usage:  bash download_deps.sh
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh"

DOWN="${LAB_ROOT}/downloads"
SDK="${ANDROID_SDK_ROOT}"
mkdir -p "${DOWN}" "${SDK}/cmdline-tools" "${SDK}/platforms" "${SDK}/platform-tools" "${SDK}/emulator"

log() { printf '[download] %s\n' "$*"; }
need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

need_cmd java

unzip_q() {
  local zip="$1" dest="$2"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "${zip}" -d "${dest}"
  else
    python3 - <<PY
import zipfile, sys
from pathlib import Path
z = zipfile.ZipFile("${zip}")
z.extractall("${dest}")
PY
  fi
}

EMULATOR_ZIP="${DOWN}/emulator-linux_aarch64.zip"
# Also accept prepare_offline_bundle name.
if [[ ! -f "${EMULATOR_ZIP}" ]]; then
  shopt -s nullglob
  alts=("${DOWN}"/sdk-repo-linux_aarch64-emulator-*.zip)
  shopt -u nullglob
  if [[ ${#alts[@]} -gt 0 ]]; then
    EMULATOR_ZIP="${alts[0]}"
  fi
fi

PLATFORM_TOOLS_ZIP="${DOWN}/platform-tools-linux.zip"
# Google official zip is x86_64; community aarch64 static tools as fallback.
PLATFORM_TOOLS_AARCH64_URL="${PLATFORM_TOOLS_AARCH64_URL:-https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip}"

fetch() {
  local url="$1" dest="$2"
  if [[ -f "${dest}" ]]; then
    log "exists: ${dest} ($(du -h "${dest}" | awk '{print $1}'))"
    return 0
  fi
  if ! command -v curl >/dev/null 2>&1; then
    echo "Missing offline file: ${dest}" >&2
    return 1
  fi
  log "fetch ${url}"
  curl -fL --retry 3 --connect-timeout 30 -o "${dest}" "${url}" || return 1
}

CMDLINE_ZIP="${DOWN}/commandlinetools-linux-latest.zip"
CMDLINE_URL="${CMDLINE_URL:-https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip}"
EMULATOR_URL="${EMULATOR_URL:-https://ci.android.com/builds/submitted/12964465/emulator-linux_aarch64_gfxstream/latest/sdk-repo-linux_aarch64-emulator-12964465.zip}"

install_cmdline_tools() {
  if [[ -x "${SDK}/cmdline-tools/latest/bin/sdkmanager" ]]; then
    log "cmdline-tools already installed"
    return 0
  fi
  fetch "${CMDLINE_URL}" "${CMDLINE_ZIP}" || true
  if [[ ! -f "${CMDLINE_ZIP}" ]]; then
    log "cmdline-tools zip missing: ${CMDLINE_ZIP}"
    return 1
  fi
  tmp="${DOWN}/cmdline-tools-unpack"
  rm -rf "${tmp}"
  mkdir -p "${tmp}"
  unzip_q "${CMDLINE_ZIP}" "${tmp}"
  rm -rf "${SDK}/cmdline-tools/latest"
  mkdir -p "${SDK}/cmdline-tools"
  mv "${tmp}/cmdline-tools" "${SDK}/cmdline-tools/latest"
  rm -rf "${tmp}"
  chmod +x "${SDK}/cmdline-tools/latest/bin/"* 2>/dev/null || true
  yes | sdkmanager --sdk_root="${SDK}" --licenses >/dev/null || true
}

install_platform_tools() {
  if [[ -x "${SDK}/platform-tools/adb" ]]; then
    log "platform-tools already installed"
    return 0
  fi
  if command -v adb >/dev/null 2>&1; then
    log "system adb found: $(command -v adb)"
    return 0
  fi
  local zip="${DOWN}/android-sdk-tools-static-aarch64.zip"
  if [[ ! -f "${zip}" ]]; then
    zip="${PLATFORM_TOOLS_ZIP}"
    fetch "${PLATFORM_TOOLS_AARCH64_URL}" "${zip}" || true
  fi
  if [[ ! -f "${zip}" ]]; then
    log "platform-tools zip missing"
    return 1
  fi
  log "install platform-tools from ${zip}"
  tmp="${DOWN}/pt-unpack"
  rm -rf "${tmp}"
  mkdir -p "${tmp}"
  unzip_q "${zip}" "${tmp}"
  rm -rf "${SDK}/platform-tools"
  mkdir -p "${SDK}/platform-tools"
  if [[ -d "${tmp}/platform-tools" ]]; then
    cp -a "${tmp}/platform-tools/." "${SDK}/platform-tools/"
  else
    cp -a "${tmp}/." "${SDK}/platform-tools/"
  fi
  rm -rf "${tmp}"
  chmod +x "${SDK}/platform-tools"/adb "${SDK}/platform-tools"/* 2>/dev/null || true
  find "${SDK}/platform-tools" -type f -name adb -exec chmod +x {} \; 2>/dev/null || true
}

install_emulator() {
  if [[ -x "${SDK}/emulator/emulator" ]]; then
    log "emulator already installed"
    return 0
  fi
  if [[ ! -f "${EMULATOR_ZIP}" ]]; then
    log "emulator zip missing (manual download required)"
    return 1
  fi
  local size
  size="$(wc -c < "${EMULATOR_ZIP}" | tr -d ' ')"
  if [[ "${size}" -lt 10000000 ]]; then
    log "emulator zip too small (${size} bytes) — likely CI HTML placeholder, not a real zip"
    return 1
  fi
  if ! head -c 2 "${EMULATOR_ZIP}" | grep -q '^PK'; then
    log "emulator zip is not a valid zip (missing PK header)"
    return 1
  fi
  unzip_q "${EMULATOR_ZIP}" "${SDK}"
}

install_platform_offline() {
  local zip="${DOWN}/platform-33-ext3_r03.zip"
  if [[ ! -f "${zip}" ]]; then
    return 1
  fi
  local dest="${SDK}/platforms/android-33"
  if [[ -f "${dest}/android.jar" ]]; then
    log "platform android-33 already installed"
    return 0
  fi
  log "offline platform android-33"
  mkdir -p "${SDK}/platforms"
  tmp="${DOWN}/platform-unpack"
  rm -rf "${tmp}"
  mkdir -p "${tmp}"
  unzip_q "${zip}" "${tmp}"
  if [[ -d "${tmp}/android-33" ]]; then
    mv "${tmp}/android-33" "${dest}"
  else
    mkdir -p "${dest}"
    mv "${tmp}"/* "${dest}/"
  fi
  rm -rf "${tmp}"
  if [[ -d "${dest}/android-13" && ! -f "${dest}/android.jar" ]]; then
    cp -a "${dest}/android-13/." "${dest}/"
  fi
}

install_system_image_offline() {
  local zip="${DOWN}/arm64-v8a-33_r08.zip"
  if [[ ! -f "${zip}" ]]; then
    return 1
  fi
  local dest="${SDK}/system-images/android-33/google_apis/arm64-v8a"
  if [[ -f "${dest}/system.img" ]]; then
    log "system image already installed"
    return 0
  fi
  log "offline system image arm64-v8a android-33"
  mkdir -p "${dest}"
  unzip_q "${zip}" "${dest}"
  if [[ -d "${dest}/arm64-v8a" && -f "${dest}/arm64-v8a/system.img" ]]; then
    cp -a "${dest}/arm64-v8a/." "${dest}/"
    rm -rf "${dest}/arm64-v8a"
  fi
}

install_system_image() {
  install_platform_offline || true
  install_system_image_offline && return 0
  if [[ ! -x "${SDK}/cmdline-tools/latest/bin/sdkmanager" ]]; then
    log "sdkmanager missing; need offline zips in downloads/"
    return 1
  fi
  log "install system image via sdkmanager: ${AISF_SYSTEM_IMAGE}"
  yes | sdkmanager --sdk_root="${SDK}" \
    "platform-tools" \
    "platforms;android-33" \
    "emulator" \
    "${AISF_SYSTEM_IMAGE}" || {
      log "sdkmanager emulator/image failed; trying pinned emulator zip only"
      install_emulator
      yes | sdkmanager --sdk_root="${SDK}" "platforms;android-33" "${AISF_SYSTEM_IMAGE}" || true
    }
}

copy_aisf_apk() {
  local dest="${LAB_ROOT}/apk/aisf_voice_assistant.apk"
  mkdir -p "${LAB_ROOT}/apk"
  if [[ -f "${dest}" ]]; then
    log "AISF apk ready: ${dest}"
    return 0
  fi
  if [[ -f "${AISF_APK}" ]]; then
    cp -f "${AISF_APK}" "${dest}"
    log "copied AISF apk -> ${dest}"
    return 0
  fi
  echo "AISF APK not found. Copy your apk to:" >&2
  echo "  ${dest}" >&2
  echo "  or set AISF_APK=/path/to/aisf.apk" >&2
  exit 1
}

install_os_packages_hint() {
  cat <<'EOF'

[optional OS packages on EulerOS / openEuler]
  sudo yum install -y java-17-openjdk java-17-openjdk-devel
  sudo yum install -y pulseaudio pulseaudio-utils alsa-utils
  sudo yum install -y ffmpeg python3 python3-pip
  sudo yum install -y unzip curl wget libGL mesa-libGL

If yum has android-tools:
  sudo yum install -y android-tools

EOF
}

log "SDK root: ${SDK}"
install_cmdline_tools
install_platform_tools
install_system_image
install_emulator || log "emulator not installed yet (see downloads/emulator-linux_aarch64.zip)"
copy_aisf_apk
install_os_packages_hint
log "done. Next:  source ./env.sh && bash create_avd.sh"
