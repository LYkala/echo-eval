#!/bin/bash
# Run on ARM TARGET (emulator host). Starts in-guest CONNECT gateway + iptables.
# Guest listens 127.0.0.1:18744, CONNECTs via 10.0.2.2:8888 (host HTTP proxy).
# Hub :8735/:8731 DNAT + DDS auth/asr/push :443 REDIRECT to the guest gateway.
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

BIN_HOST="${ROOT}/guest_connect_gw"
BIN_GUEST=/data/local/tmp/guest_connect_gw
START_GUEST=/data/local/tmp/start_guest_gw.sh
IPT=/data/local/tmp/ipt_dds.sh
# Default serial for aisf voice TV emulator console/adb pair.
ADB_SERIAL="${ADB_SERIAL:-emulator-5556}"
ADB="python3 ${ROOT}/mini_adb_auth.py -s ${ADB_SERIAL}"

if [[ ! -f "$BIN_HOST" ]]; then
  echo "missing $BIN_HOST (android/linux arm64). Build on jump:"
  echo "  CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -o guest_connect_gw guest_connect_gw.go"
  exit 1
fi
chmod +x "$BIN_HOST" 2>/dev/null || true

cat > /tmp/start_guest_gw.sh <<'EOF'
#!/system/bin/sh
BIN=/data/local/tmp/guest_connect_gw
LOG=/data/local/tmp/gw.log
PIDF=/data/local/tmp/gw.pid
# Always kill stale listeners — pidfile alone is unreliable after reboot/crash.
if [ -f "$PIDF" ]; then
  kill $(cat "$PIDF") 2>/dev/null
fi
for p in $(pidof guest_connect_gw); do kill -9 "$p"; done
stop mdnsd 2>/dev/null
killall -9 mdnsd 2>/dev/null
sleep 1
chmod 755 "$BIN"
rm -f "$LOG" "$PIDF"
# setsid + background so adb shell exit (SIGHUP) does not kill the gw
setsid "$BIN" </dev/null >"$LOG" 2>&1 &
echo $! > "$PIDF"
sleep 1
echo STARTED:$(pidof guest_connect_gw)
cat "$LOG"
EOF

# Per-AVD Hub WS rewrite port (28701..28705); override via HUB_DNAT_PORT env.
source "${ROOT}/guest_ipt_devicesdk.inc.sh"
HUB_DNAT_PORT="$(hub_dnat_port_for_serial "${ADB_SERIAL}" "${HUB_DNAT_PORT:-8735}")"
write_guest_ipt_script "${HUB_DNAT_PORT}" /tmp/ipt_dds.sh

$ADB push "$BIN_HOST" "$BIN_GUEST"
$ADB push /tmp/start_guest_gw.sh "$START_GUEST"
$ADB push /tmp/ipt_dds.sh "$IPT"
$ADB shell "su 0 sh $START_GUEST"
$ADB shell "su 0 sh $IPT"
$ADB shell "su 0 sh -c 'toybox nc -w 1 127.0.0.1 18744; echo local:\$?; cat /data/local/tmp/gw.log'" || true
echo "DDS guest CONNECT gateway ready."
