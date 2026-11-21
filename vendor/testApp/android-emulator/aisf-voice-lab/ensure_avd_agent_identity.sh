#!/usr/bin/env bash
# Ensure per-AVD agent WS deviceId rewrite proxy + guest DNAT to it.
# Usage:
#   ADB_SERIAL=emulator-5554 AVD_NAME=avd_1 bash ensure_avd_agent_identity.sh
set -eu
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${LAB_ROOT}"

AVD_NAME="${AVD_NAME:?AVD_NAME required}"
ADB_SERIAL="${ADB_SERIAL:?ADB_SERIAL required}"
UPSTREAM="${AGENT_UPSTREAM:-127.0.0.1:8735}"
PROXY_PY="${LAB_ROOT}/emu_ws_deviceid_proxy.py"
ADB="python3 ${LAB_ROOT}/mini_adb_auth.py -s ${ADB_SERIAL}"

# Stable listen port: avd_1->28701 ... avd_5->28705
idx="${AVD_NAME##*_}"
if [[ ! "${idx}" =~ ^[0-9]+$ ]]; then
  echo "cannot derive index from AVD_NAME=${AVD_NAME}" >&2
  exit 1
fi
LISTEN_PORT=$((28700 + idx))

# Unique Hub deviceId from android_id
AID="$($ADB shell su 0 /system/bin/settings get secure android_id 2>/dev/null | tr -d '\r' | tail -1)"
AID="${AID:-$(echo -n "${AVD_NAME}" | md5sum | awk '{print substr($1,1,16)}')}"
DEVICE_ID="assistant-${AVD_NAME}-${AID}"

mkdir -p "${LAB_ROOT}/runs"
PIDF="${LAB_ROOT}/runs/ws_proxy_${AVD_NAME}.pid"
LOGF="${LAB_ROOT}/runs/ws_proxy_${AVD_NAME}.log"

# Stop old proxy for this AVD
if [[ -f "${PIDF}" ]]; then
  old="$(cat "${PIDF}" 2>/dev/null || true)"
  if [[ -n "${old}" ]] && kill -0 "${old}" 2>/dev/null; then
    kill "${old}" 2>/dev/null || true
    sleep 0.5
    kill -9 "${old}" 2>/dev/null || true
  fi
  rm -f "${PIDF}"
fi
# Also kill by listen port
ss -lntp 2>/dev/null | grep -q ":${LISTEN_PORT} " && \
  fuser -k "${LISTEN_PORT}/tcp" 2>/dev/null || true

nohup python3 "${PROXY_PY}" \
  --bind 127.0.0.1 \
  --listen "${LISTEN_PORT}" \
  --upstream "${UPSTREAM}" \
  --device-id "${DEVICE_ID}" \
  --avd "${AVD_NAME}" \
  >"${LOGF}" 2>&1 &
echo $! > "${PIDF}"
sleep 0.5
if ! kill -0 "$(cat "${PIDF}")" 2>/dev/null; then
  echo "proxy failed to start; see ${LOGF}" >&2
  tail -30 "${LOGF}" || true
  exit 1
fi

# DeviceSDK CUSTOM 40098 + Hub DNAT; no blanket 121.37.53.201 → 18744.
source "${LAB_ROOT}/guest_ipt_devicesdk.inc.sh"
write_guest_ipt_script_with_extras "${LISTEN_PORT}" "/tmp/ipt_dds_${AVD_NAME}.sh" \
  'setprop net.dns1 8.8.8.8' \
  'setprop net.dns2 1.1.1.1' \
  'settings put global http_proxy :0'

$ADB push /tmp/ipt_dds_${AVD_NAME}.sh /data/local/tmp/ipt_dds.sh >/dev/null
$ADB shell "su 0 sh /data/local/tmp/ipt_dds.sh" >/dev/null# Persist mapping for inject tools
echo "{\"avd\":\"${AVD_NAME}\",\"serial\":\"${ADB_SERIAL}\",\"listen\":${LISTEN_PORT},\"deviceId\":\"${DEVICE_ID}\",\"androidId\":\"${AID}\"}" \
  > "${LAB_ROOT}/runs/agent_identity_${AVD_NAME}.json"

# Also write per-AVD WS URL that still uses public Hub host (DNAT → local rewrite proxy).
# Direct 10.0.2.2 URLs break when qemu -http-proxy is enabled.
PREF_DIR="${LAB_ROOT}/runs"
cat > "${PREF_DIR}/voxtest_agent_${AVD_NAME}.xml" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="enabled" value="true" />
    <string name="ws_url">ws://124.71.69.102:8735/agent/ws</string>
    <string name="wsUrl">ws://124.71.69.102:8735/agent/ws</string>
</map>
EOF
$ADB push "${PREF_DIR}/voxtest_agent_${AVD_NAME}.xml" /data/local/tmp/voxtest_agent.xml >/dev/null || true
$ADB shell "su 0 sh -c 'APP=/data/data/com.huawei.aisftvassistant; mkdir -p \$APP/shared_prefs; cp /data/local/tmp/voxtest_agent.xml \$APP/shared_prefs/voxtest_agent.xml; cp /data/local/tmp/voxtest_agent.xml \$APP/shared_prefs/voxtest.xml; U=\$(stat -c %u \$APP); chown \$U:\$U \$APP/shared_prefs/voxtest*.xml; chmod 660 \$APP/shared_prefs/voxtest*.xml'" >/dev/null || true

echo "OK ${AVD_NAME} serial=${ADB_SERIAL} proxy=127.0.0.1:${LISTEN_PORT} deviceId=${DEVICE_ID}"
