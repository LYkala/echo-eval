#!/usr/bin/env python3
"""Clone remote webserverV2_9526 -> webserverV2_9527 and run it on port 9527.

Does not modify 9526 or the frozen webserverV2 (currently on 9528).
"""
from __future__ import annotations

from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")

REMOTE_SH = r"""
set -euo pipefail
SRC=/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9526
DST=/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9527
UNIT=/etc/systemd/system/webserver-9527.service
PY=/opt/VoxTest/.venv/bin/python3.10

echo '=== before ports ==='
ss -lntp | grep -E ':9526|:9527|:9528' || true

P6=$(ss -lntp | awk '/:9526/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
P8=$(ss -lntp | awk '/:9528/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "pid_9526_before=$P6"
echo "pid_9528_before=$P8"

if ss -lntp | grep -qE '[:.]9527\b'; then
  echo 'FAIL: 9527 already listening'
  ss -lntp | grep -E '[:.]9527\b'
  exit 1
fi

if [ ! -d "$SRC" ]; then
  echo "FAIL: missing source $SRC"
  exit 1
fi

echo '=== rsync clone (exclude runtime noise / bak) ==='
mkdir -p "$DST"
rsync -a --delete \
  --exclude '__pycache__/' \
  --exclude '*.pyc' \
  --exclude 'logs/' \
  --exclude 'results/' \
  --exclude 'netdisk_search/results/' \
  --exclude 'mcloud_search/results/' \
  --exclude 'testresults/' \
  --exclude '*.bak_*' \
  --exclude 'webserver_v2.py.bak*' \
  --exclude 'llm_eval.py.bak*' \
  "$SRC/" "$DST/"

mkdir -p "$DST/logs" "$DST/results" \
  "$DST/netdisk_search/results" "$DST/mcloud_search/results" \
  "$DST/testresults"

# env / start / stop for 9527
cat > "$DST/env_9527.sh" <<'EOF'
# sourced by start script / manual runs
export GUI_EVAL_HOST=0.0.0.0
export GUI_EVAL_PORT=9527
export WEBSERVER_PUBLIC_URL=http://124.71.69.102:9527
# keep USB bridge off 9526's 15036
export ADB_USB_BRIDGE_PORT=15037
EOF

cat > "$DST/start_9527.sh" <<EOF
#!/bin/bash
set -e
cd "$DST"
# shellcheck disable=SC1091
source ./env_9527.sh
exec $PY -u webserver_v2.py
EOF
chmod +x "$DST/start_9527.sh"

cat > "$DST/stop_9527.sh" <<'EOF'
#!/bin/bash
systemctl stop webserver-9527.service
EOF
chmod +x "$DST/stop_9527.sh"

# remove 9526-specific helper names if confusing (keep copies as reference)
# leave start_9526.sh as-is inside tree but prefer 9527 scripts above

cat > "$UNIT" <<EOF
[Unit]
Description=TestAppGUI WebServer V2 on port 9527 (clone of 9526)
After=network.target

[Service]
Type=simple
WorkingDirectory=$DST
Environment=GUI_EVAL_HOST=0.0.0.0
Environment=GUI_EVAL_PORT=9527
Environment=WEBSERVER_PUBLIC_URL=http://124.71.69.102:9527
Environment=ADB_USB_BRIDGE_PORT=15037
ExecStart=$PY -u webserver_v2.py
Restart=on-failure
RestartSec=3
StandardOutput=append:$DST/logs/webserver_stdout.log
StandardError=append:$DST/logs/webserver_stdout.log

[Install]
WantedBy=multi-user.target
EOF

echo '=== unit ==='
cat "$UNIT"

systemctl daemon-reload
systemctl enable webserver-9527.service
systemctl restart webserver-9527.service
sleep 3
echo -n 'unit: '; systemctl is-active webserver-9527.service
systemctl status webserver-9527.service --no-pager -l | head -25

echo '=== after ports ==='
ss -lntp | grep -E ':9526|:9527|:9528' || true

P6a=$(ss -lntp | awk '/:9526/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
P8a=$(ss -lntp | awk '/:9528/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "pid_9526_after=$P6a cwd=$(readlink /proc/$P6a/cwd 2>/dev/null || true)"
echo "pid_9528_after=$P8a cwd=$(readlink /proc/$P8a/cwd 2>/dev/null || true)"
P7=$(ss -lntp | awk '/:9527/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "pid_9527=$P7 cwd=$(readlink /proc/$P7/cwd 2>/dev/null || true)"
if [ -n "$P7" ]; then
  tr '\0' '\n' < /proc/$P7/environ | grep -E 'GUI_EVAL_PORT|WEBSERVER_PUBLIC|ADB_USB_BRIDGE' || true
fi

echo '=== http ==='
echo -n '9526: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9526/api/download/packages || true
echo -n '9527: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9527/api/download/packages || true
echo -n '9528: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9528/api/download/packages || true

curl -sS --max-time 8 http://127.0.0.1:9527/api/download/packages \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print("9527 ids", [x.get("id") for x in d.get("packages",[])])'
# netdisk presence
curl -sS -o /dev/null -w '9527 pan-search app: %{http_code}\n' --max-time 8 http://127.0.0.1:9527/pan-search/app || true

# size check
du -sh "$SRC" "$DST" || true

if [ "$P6" != "$P6a" ]; then echo "WARN: 9526 pid changed $P6 -> $P6a"; else echo 'OK: 9526 untouched'; fi
if [ "$P8" != "$P8a" ]; then echo "WARN: 9528 pid changed $P8 -> $P8a"; else echo 'OK: 9528 untouched'; fi
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
    with sftp.file("/tmp/_clone_9526_to_9527.sh", "w") as f:
        f.write(REMOTE_SH)
    sftp.close()
    _, o, e = j.exec_command("bash /tmp/_clone_9526_to_9527.sh", timeout=600)
    print(o.read().decode("utf-8", "replace"))
    err = e.read().decode("utf-8", "replace")
    if err.strip():
        print("STDERR:", err[:4000])
    j.close()


if __name__ == "__main__":
    main()
