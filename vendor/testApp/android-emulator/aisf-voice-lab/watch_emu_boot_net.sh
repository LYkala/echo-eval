#!/bin/bash
# Run on ARM TARGET. Watch emulator boot/restart and (re)apply DDS guest net.
# Detects: sys.boot_completed=1 and missing gw / iptables rules.
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
ADB="python3 ${ROOT}/mini_adb_auth.py"
APPLY="${ROOT}/start_emu_dds_guest_gw.sh"
LOG="${LOG:-/tmp/emu_dds_net_watch.log}"
INTERVAL="${INTERVAL:-20}"
STATE_FILE=/tmp/emu_dds_net_watch.state

mkdir -p "$(dirname "$LOG")"
echo "[$(date -Is)] watch start interval=${INTERVAL}s" >>"$LOG"

boot_ok() {
  local out
  out="$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | tail -1)"
  [[ "$out" == "1" ]]
}

gw_ok() {
  # Do NOT use "tail -1 of gw.log == READY": after traffic the last line is often
  # "CONNECT ok …", which falsely looks "down" and re-applies every INTERVAL —
  # killing guest_connect_gw mid-ASR and yielding empty asrText while the mic UI moves.
  local out
  out="$($ADB shell "su 0 sh -c 'ps -A 2>/dev/null | grep guest_connect_gw | grep -v grep | head -1'" 2>/dev/null || true)"
  echo "$out" | grep -q "guest_connect_gw"
}

ipt_ok() {
  local out
  out="$($ADB shell "su 0 iptables -t nat -S OUTPUT 2>/dev/null" 2>/dev/null || true)"
  echo "$out" | grep -q "dport 40098" || return 1
  echo "$out" | grep -q "dport 8735" || return 1
  # Must NOT blanket-redirect ARM VIP to guest_connect_gw (breaks CUSTOM ACM 40098).
  echo "$out" | grep "121.37.53.201" | grep -q "REDIRECT --to-ports 18744" && return 1
  return 0
}

apply_net() {
  echo "[$(date -Is)] applying DDS guest net..." >>"$LOG"
  if bash "$APPLY" >>"$LOG" 2>&1; then
    echo up >"$STATE_FILE"
    echo "[$(date -Is)] apply ok" >>"$LOG"
  else
    echo "[$(date -Is)] apply failed" >>"$LOG"
  fi
}

was_down=1
while true; do
  if boot_ok; then
    if [[ "$was_down" -eq 1 ]]; then
      echo "[$(date -Is)] emulator boot detected" >>"$LOG"
      apply_net
      was_down=0
    elif ! gw_ok || ! ipt_ok; then
      echo "[$(date -Is)] gw/iptables missing — re-apply" >>"$LOG"
      apply_net
    fi
  else
    if [[ "$was_down" -eq 0 ]]; then
      echo "[$(date -Is)] emulator down" >>"$LOG"
    fi
    was_down=1
    echo down >"$STATE_FILE"
  fi
  sleep "$INTERVAL"
done
