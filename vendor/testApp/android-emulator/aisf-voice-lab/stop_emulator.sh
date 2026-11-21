#!/usr/bin/env bash
set -euo pipefail
adb devices | awk '/^emulator-/{print $1}' | while read -r serial; do
  adb -s "${serial}" emu kill || true
done
pkill -f "qemu-system-aarch64" 2>/dev/null || true
echo "Emulator stopped."
