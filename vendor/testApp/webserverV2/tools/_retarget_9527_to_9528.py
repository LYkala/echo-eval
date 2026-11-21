#!/usr/bin/env python3
"""Move frozen webserver HTTP listen from 9527 -> 9528. Do not touch 9526."""
from __future__ import annotations

from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")

REMOTE_SH = r"""
set -euo pipefail
DIR=/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2
UNIT7=/etc/systemd/system/webserver-9527.service
UNIT8=/etc/systemd/system/webserver-9528.service
PY=/opt/VoxTest/.venv/bin/python3.10

echo '=== before ==='
ss -lntp | grep -E ':9526|:9527|:9528|:9529' || true

P6_BEFORE=$(ss -lntp | awk '/:9526/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "9526_pid_before=$P6_BEFORE"

if ss -lntp | grep -qE '[:.]9528\b'; then
  echo 'FAIL: 9528 already in use'
  ss -lntp | grep -E '[:.]9528\b'
  exit 1
fi

OLD=$(ss -lntp | awk '/:9527/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "old_9527_pid=${OLD:-none}"
if [ -n "${OLD:-}" ]; then
  CWD=$(readlink /proc/$OLD/cwd || true)
  echo "old_cwd=$CWD"
  if [ "$CWD" != "$DIR" ]; then
    echo "REFUSE: 9527 cwd is not frozen dir ($CWD)"
    exit 2
  fi
  tr '\0' '\n' < /proc/$OLD/environ > /tmp/ws9528_env_from_old.txt
fi

mkdir -p "$DIR/logs"

{
  echo '[Unit]'
  echo 'Description=TestAppGUI WebServer V2 on port 9528 (was 9527 production)'
  echo 'After=network.target'
  echo
  echo '[Service]'
  echo 'Type=simple'
  echo "WorkingDirectory=$DIR"
  echo 'Environment=GUI_EVAL_HOST=0.0.0.0'
  echo 'Environment=GUI_EVAL_PORT=9528'
  echo 'Environment=WEBSERVER_PUBLIC_URL=http://124.71.69.102:9528'
  if [ -f /tmp/ws9528_env_from_old.txt ]; then
    while IFS= read -r line; do
      case "$line" in
        GUI_EVAL_*|WEBSERVER_PUBLIC_URL=*|WEB_DEVICE_PORT=*) continue ;;
        EMU_*=*|ADB_*=*)
          key=${line%%=*}
          val=${line#*=}
          val_esc=$(printf '%s' "$val" | sed 's/\\/\\\\/g; s/"/\\"/g')
          echo "Environment=\"${key}=${val_esc}\""
          ;;
      esac
    done < /tmp/ws9528_env_from_old.txt
  fi
  echo 'Environment=WEB_DEVICE_PORT=9529'
  echo "ExecStart=$PY -u webserver_v2.py"
  echo 'Restart=on-failure'
  echo 'RestartSec=3'
  echo "StandardOutput=append:$DIR/logs/webserver_9528_stdout.log"
  echo "StandardError=append:$DIR/logs/webserver_9528_stdout.log"
  echo
  echo '[Install]'
  echo 'WantedBy=multi-user.target'
} > "$UNIT8"

echo '=== unit 9528 written ==='
sed -E 's/(PASS|PASSWORD|TOKEN|SECRET)=[^"]*/\1=***/g' "$UNIT8"

systemctl daemon-reload

# stop 9527 unit / process
if systemctl list-unit-files | grep -q '^webserver-9527.service'; then
  systemctl stop webserver-9527.service || true
  systemctl disable webserver-9527.service || true
fi
if [ -n "${OLD:-}" ] && kill -0 "$OLD" 2>/dev/null; then
  kill "$OLD" || true
  for i in $(seq 1 20); do
    kill -0 "$OLD" 2>/dev/null || break
    sleep 0.4
  done
  kill -9 "$OLD" 2>/dev/null || true
fi
sleep 0.5
ss -lntp | grep -E '[:.]9527\b' && echo 'WARN: 9527 still up' || echo '9527 freed'

systemctl enable webserver-9528.service
systemctl restart webserver-9528.service
sleep 2.5
echo -n 'unit: '; systemctl is-active webserver-9528.service
systemctl status webserver-9528.service --no-pager -l | head -25

echo '=== after ports ==='
ss -lntp | grep -E ':9526|:9527|:9528|:9529' || true

P6_AFTER=$(ss -lntp | awk '/:9526/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "9526_pid_after=$P6_AFTER"
[ -n "$P6_AFTER" ] && readlink /proc/$P6_AFTER/cwd

echo '=== http checks ==='
echo -n '9526: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9526/api/download/packages || true
echo -n '9527: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 4 http://127.0.0.1:9527/api/download/packages || echo 'unreachable'
echo -n '9528: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9528/api/download/packages || true
curl -sS --max-time 8 http://127.0.0.1:9528/api/download/packages \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print("9528 ids", [x.get("id") for x in d.get("packages",[])])'

P8=$(ss -lntp | awk '/:9528/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "9528_pid=$P8"
if [ -n "$P8" ]; then
  tr '\0' '\n' < /proc/$P8/environ | grep -E 'GUI_EVAL_PORT|WEBSERVER_PUBLIC|WEB_DEVICE_PORT|EMU_ON_JUMP|EMU_TARGET_PORT' || true
fi

if [ -n "$P6_BEFORE" ] && [ -n "$P6_AFTER" ] && [ "$P6_BEFORE" != "$P6_AFTER" ]; then
  echo "WARN: 9526 pid changed ($P6_BEFORE -> $P6_AFTER)"
else
  echo 'OK: 9526 untouched'
fi
"""


def pw() -> str:
    for line in CREDS.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip().startswith("root") and "CLOUD" in line:
            return line.split()[-1]
    return ""


def main() -> None:
    j = paramiko.SSHClient()
    j.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    j.connect("124.71.69.102", 22, "root", pw(), timeout=30)
    sftp = j.open_sftp()
    with sftp.file("/tmp/_retarget_9527_to_9528.sh", "w") as f:
        f.write(REMOTE_SH)
    sftp.close()
    _, o, e = j.exec_command("bash /tmp/_retarget_9527_to_9528.sh", timeout=120)
    print(o.read().decode("utf-8", "replace"))
    err = e.read().decode("utf-8", "replace")
    if err.strip():
        print("STDERR:", err[:3000])
    j.close()


if __name__ == "__main__":
    main()
