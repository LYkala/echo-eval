#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")

SCRIPT = r"""
set -e
echo '=== listen 9526/9527/9528 ==='
ss -lntp | grep -E ':9526|:9527|:9528' || true

echo
echo '=== 9527 process ==='
PID=$(ss -lntp | awk '/:9527/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "pid=$PID"
if [ -n "$PID" ]; then
  ps -fp "$PID" -ww
  ls -l /proc/$PID/cwd
  echo '--environ--'
  tr '\0' '\n' < /proc/$PID/environ | grep -E 'GUI_EVAL|WEBSERVER|ADB_|PORT|EMU_' || true
  echo '--cmdline--'
  tr '\0' ' ' < /proc/$PID/cmdline; echo
fi

echo
echo '=== 9528 process if any ==='
PID8=$(ss -lntp | awk '/:9528/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "pid8=$PID8"
if [ -n "$PID8" ]; then
  ps -fp "$PID8" -ww
  tr '\0' '\n' < /proc/$PID8/environ | grep -E 'GUI_EVAL|WEBSERVER|ADB_|PORT|EMU_' || true
fi

echo
echo '=== systemd units mentioning 9527/9528 ==='
ls /etc/systemd/system/*9527* /etc/systemd/system/*9528* 2>/dev/null || true
systemctl list-units --all 'webserver*' 2>/dev/null || true
grep -RIn '9527\|9528' /etc/systemd/system/ 2>/dev/null | head -40 || true

echo
echo '=== start scripts in webserverV2 ==='
ls -la /opt/TestAppGUI/TestAPPGUI/testApp/webserverV2/*.sh 2>/dev/null || true
grep -RIn '9527\|GUI_EVAL_PORT\|WEBSERVER_PUBLIC' /opt/TestAppGUI/TestAPPGUI/testApp/webserverV2/*.sh /opt/TestAppGUI/TestAPPGUI/testApp/webserverV2/env* 2>/dev/null | head -40 || true

echo
echo '=== parent / how started ==='
if [ -n "$PID" ]; then
  pstree -asp "$PID" 2>/dev/null || ps -o ppid= -p "$PID" | xargs -I{} ps -fp {}
fi

echo
echo '=== firewall / security group note (local iptables) ==='
iptables -L INPUT -n 2>/dev/null | grep -E '9527|9528' || true
firewall-cmd --list-ports 2>/dev/null || true
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
    with sftp.file("/tmp/_inspect_9527_port.sh", "w") as f:
        f.write(SCRIPT)
    sftp.close()
    _, o, e = j.exec_command("bash /tmp/_inspect_9527_port.sh", timeout=60)
    print(o.read().decode("utf-8", "replace"))
    err = e.read().decode("utf-8", "replace")
    if err.strip():
        print("STDERR:", err[:2000])
    j.close()


if __name__ == "__main__":
    main()
