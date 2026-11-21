#!/bin/bash
# Install/restart the emulator boot network watcher on TARGET.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
WATCH="${ROOT}/watch_emu_boot_net.sh"
PIDF=/tmp/emu_dds_net_watch.pid
LOG=/tmp/emu_dds_net_watch.log

chmod +x "$WATCH" "${ROOT}/start_emu_dds_guest_gw.sh" 2>/dev/null || true

if [[ -f "$PIDF" ]]; then
  old=$(cat "$PIDF" || true)
  if [[ -n "${old:-}" ]] && kill -0 "$old" 2>/dev/null; then
    kill "$old" 2>/dev/null || true
    sleep 1
  fi
fi
# avoid matching this installer argv
pkill -f "${ROOT}/watch_emu_boot_net.sh" 2>/dev/null || true
sleep 1

nohup bash "$WATCH" >>"$LOG" 2>&1 &
echo $! >"$PIDF"
sleep 1
echo "watch pid=$(cat "$PIDF")"
tail -5 "$LOG" || true
