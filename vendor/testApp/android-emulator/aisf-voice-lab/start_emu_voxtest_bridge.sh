#!/bin/bash
# On JUMP host: keep reverse tunnel to ARM target for VoxTest agent WS/HTTP.
# Emulator reaches Hub via DNAT; DDS cloud via guest CONNECT gw on TARGET.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
set -a
# shellcheck disable=SC1091
source /root/testApp/env_web.sh
set +a

pkill -f '/root/aisf-voice-lab/emu_agent_tunnel.py' 2>/dev/null || true
sleep 1
mkdir -p /root/aisf-voice-lab
cp -f "$ROOT/emu_agent_tunnel.py" /root/aisf-voice-lab/emu_agent_tunnel.py 2>/dev/null || true
nohup /usr/local/bin/python3.10 /root/aisf-voice-lab/emu_agent_tunnel.py \
  >> /tmp/emu_agent_tunnel.log 2>&1 &
echo $! > /tmp/emu_agent_tunnel.pid
sleep 3
ps -p "$(cat /tmp/emu_agent_tunnel.pid)" -o pid,cmd
tail -15 /tmp/emu_agent_tunnel.log

# Trigger DDS guest net + boot watcher on TARGET (best-effort).
/usr/local/bin/python3.10 - <<'PY' || echo "[warn] remote DDS guest net trigger failed"
import os, paramiko
host = os.environ.get("EMU_TARGET_HOST", "121.37.53.201")
port = int(os.environ.get("EMU_TARGET_PORT", "40204"))
user = os.environ.get("EMU_TARGET_USER", "root")
password = os.environ.get("EMU_TARGET_PASS", "")
if not password:
    raise SystemExit("no EMU_TARGET_PASS")
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(host, port, username=user, password=password, timeout=60)
cmd = (
    "bash /root/aisf-voice-lab/start_emu_dds_guest_gw.sh; "
    "bash /root/aisf-voice-lab/install_emu_dds_net_watch.sh"
)
_, o, e = c.exec_command(cmd, timeout=120)
print((o.read() + e.read()).decode("utf-8", "replace"))
c.close()
PY

# Also apply Hub DNAT via local mini_adb if available (legacy path).
if [[ -f /root/aisf-voice-lab/mini_adb_auth.py ]]; then
  cd /root/aisf-voice-lab
  python3 mini_adb_auth.py shell "su 0 iptables -t nat -C OUTPUT -d 124.71.69.102 -p tcp --dport 8735 -j DNAT --to-destination 10.0.2.2:8735" \
    2>/dev/null || python3 mini_adb_auth.py shell "su 0 iptables -t nat -A OUTPUT -d 124.71.69.102 -p tcp --dport 8735 -j DNAT --to-destination 10.0.2.2:8735" || true
  python3 mini_adb_auth.py shell "su 0 iptables -t nat -C OUTPUT -d 124.71.69.102 -p tcp --dport 8731 -j DNAT --to-destination 10.0.2.2:8731" \
    2>/dev/null || python3 mini_adb_auth.py shell "su 0 iptables -t nat -A OUTPUT -d 124.71.69.102 -p tcp --dport 8731 -j DNAT --to-destination 10.0.2.2:8731" || true
  python3 mini_adb_auth.py shell "am force-stop com.huawei.aisftvassistant" || true
  sleep 1
  python3 mini_adb_auth.py shell "am start -n com.huawei.aisftvassistant/.activity.PhoneLaunchActivity" || true
  sleep 8
fi
curl -sS http://127.0.0.1:8736/internal/devices || true
echo
