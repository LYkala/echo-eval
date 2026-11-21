#!/usr/bin/env bash
# Create AVD config offline (no emulator package required for avdmanager).
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh"
[[ -f "${LAB_ROOT}/.java_env" ]] && source "${LAB_ROOT}/.java_env"

AVD_NAME="${AISF_AVD_NAME:-AISF_EulerOS_ARM64}"
AVD_HOME="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
AVD_DIR="${AVD_HOME}/${AVD_NAME}.avd"
INI="${AVD_HOME}/${AVD_NAME}.ini"
SYS_REL="system-images/android-33/google_apis/arm64-v8a"
SYS_ABS="${ANDROID_SDK_ROOT}/${SYS_REL}"

if [[ ! -f "${SYS_ABS}/system.img" ]]; then
  echo "system image missing: ${SYS_ABS}/system.img" >&2
  exit 1
fi

mkdir -p "${AVD_DIR}"
cat > "${INI}" <<EOF
avd.ini.displayname=${AVD_NAME}
avd.ini.encoding=UTF-8
path=${AVD_DIR}
path.rel=avd/${AVD_NAME}.avd
target=android-33
EOF

cat > "${AVD_DIR}/config.ini" <<EOF
AvdId=${AVD_NAME}
PlayStore.enabled=false
abi.type=arm64-v8a
avd.ini.displayname=${AVD_NAME}
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
hw.cpu.arch=arm64
hw.cpu.ncore=4
hw.ramSize=4096
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
image.sysdir.1=${SYS_REL}/
tag.display=Google APIs
tag.id=google_apis
vm.heapSize=256
EOF

echo "AVD created offline: ${AVD_NAME}"
echo "  ini: ${INI}"
echo "  dir: ${AVD_DIR}"
