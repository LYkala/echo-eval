#!/usr/bin/env python3
"""Finer code-only diff summary for remote 9526 vs 9527."""
from __future__ import annotations

from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")

REMOTE_SCRIPT = r"""
set -e
A=/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9526
B=/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2

echo '=== env / port / bridge ==='
echo '--9526 env_9526.sh--'
cat "$A/env_9526.sh" 2>/dev/null || true
echo '--9526 process env snippet--'
tr '\0' '\n' < /proc/1489803/environ 2>/dev/null | grep -E 'GUI_EVAL|WEBSERVER|ADB_|PORT' || true
echo '--9527 process env snippet--'
tr '\0' '\n' < /proc/1721353/environ 2>/dev/null | grep -E 'GUI_EVAL|WEBSERVER|ADB_|PORT' || true
echo '--how 9527 started--'
ps -fp 1721353 -ww
ls -l /proc/1721353/cwd 2>/dev/null
cat /proc/1721353/cmdline | tr '\0' ' '; echo

echo
echo '=== code-only DIFF list (py/html/js/css, excl netdisk/mcloud/testresults/tools bak) ==='
(cd "$A" && find . -type f \( -name '*.py' -o -name '*.html' -o -name '*.js' -o -name '*.css' \) \
  ! -path './netdisk_search/*' ! -path './mcloud_search/*' ! -path './static/netdisk/*' \
  ! -path './templates/pan_search*' ! -path './templates/mcloud_search*' \
  ! -path '*/__pycache__/*' ! -path './testresults/*' ! -path './tools/*' \
  ! -name '*.bak*' | sort) > /tmp/acode.lst
(cd "$B" && find . -type f \( -name '*.py' -o -name '*.html' -o -name '*.js' -o -name '*.css' \) \
  ! -path './netdisk_search/*' ! -path './mcloud_search/*' ! -path './static/netdisk/*' \
  ! -path './templates/pan_search*' ! -path './templates/mcloud_search*' \
  ! -path '*/__pycache__/*' ! -path './testresults/*' ! -path './tools/*' \
  ! -name '*.bak*' | sort) > /tmp/bcode.lst

echo '-- only 9526 code --'
comm -23 /tmp/acode.lst /tmp/bcode.lst
echo '-- only 9527 code --'
comm -13 /tmp/acode.lst /tmp/bcode.lst
echo '-- content DIFF --'
while IFS= read -r f; do
  [ -f "$A/$f" ] && [ -f "$B/$f" ] || continue
  ha=$(md5sum "$A/$f" | awk '{print $1}')
  hb=$(md5sum "$B/$f" | awk '{print $1}')
  if [ "$ha" != "$hb" ]; then
    # line counts
    la=$(wc -l < "$A/$f")
    lb=$(wc -l < "$B/$f")
    echo "DIFF $f  lines 9526=$la 9527=$lb"
  fi
done < <(comm -12 /tmp/acode.lst /tmp/bcode.lst)

echo
echo '=== llm_eval.py diffstat / key symbols ==='
diff -u "$B/llm_eval.py" "$A/llm_eval.py" | diffstat 2>/dev/null || diff -u "$B/llm_eval.py" "$A/llm_eval.py" | wc -l
echo '--9526 unique defs--'
grep -nE '^def |^class ' "$A/llm_eval.py" | head -40
echo '--symbol grep gui_step / evidence / text_passed--'
echo 9526:
grep -nE 'gui_step_latency|text_passed|screenshot_passed|evidence|RESULTS_UI_VERSION|ingest_gui' "$A/llm_eval.py" | head -30
echo 9527:
grep -nE 'gui_step_latency|text_passed|screenshot_passed|evidence|RESULTS_UI_VERSION|ingest_gui' "$B/llm_eval.py" | head -30

echo
echo '=== webserver_v2.py key feature grep ==='
for label in 9526 9527; do
  P=$A; [ "$label" = 9527 ] && P=$B
  echo "--$label--"
  grep -nE 'gui_step_latency|ingest_gui_step|RESULTS_UI_VERSION|register_netdisk|register_mcloud|stop_test|devicesdk|ADB_USB_BRIDGE|WEBSERVER_PUBLIC|step_latency' "$P/webserver_v2.py" | head -50
done

echo
echo '=== packages API raw ==='
echo 9526:
curl -sS -m 8 http://127.0.0.1:9526/api/download/packages | python3 -m json.tool 2>/dev/null | head -80
echo 9527:
curl -sS -m 8 http://127.0.0.1:9527/api/download/packages | python3 -m json.tool 2>/dev/null | head -80

echo
echo '=== apk dir listing ==='
echo 9526:
ls -la "$A/apks" 2>/dev/null | head -30
find "$A/apks" -maxdepth 2 -type f -name '*.apk' 2>/dev/null | head -20
echo 9527:
ls -la "$B/apks" 2>/dev/null | head -30
find "$B/apks" -maxdepth 2 -type f -name '*.apk' 2>/dev/null | head -20

echo
echo '=== static only-in-9526 (non-netdisk) ==='
if [ -d "$A/static" ]; then
  (cd "$A/static" && find . -type f ! -path './netdisk/*' | sort)
fi
if [ -d "$B/static" ]; then
  echo '9527 static exists'
  (cd "$B/static" && find . -type f | sort | head -40)
else
  echo '9527 has no static/ dir'
fi

echo
echo '=== tools only on 9526 (names) ==='
ls -1 "$A/tools" 2>/dev/null | head -40

echo
echo '=== brief feature diff: results.html markers ==='
echo 9526:
grep -cE 'evalEvidence|step_latency|gui_step|text_passed|screenshot_passed|case-count-link' "$A/templates/results.html" || true
echo 9527:
grep -cE 'evalEvidence|step_latency|gui_step|text_passed|screenshot_passed|case-count-link' "$B/templates/results.html" || true
echo 9526 dispatch:
grep -cE 'isDeviceSdk|step_latency_sec|formatReportStepLatency|auto_wake' "$A/templates/dispatch.html" || true
echo 9527 dispatch:
grep -cE 'isDeviceSdk|step_latency_sec|formatReportStepLatency|auto_wake' "$B/templates/dispatch.html" || true
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
    remote = "/tmp/_cmp_9526_9527_code.sh"
    with sftp.file(remote, "w") as f:
        f.write(REMOTE_SCRIPT)
    sftp.close()
    _, o, e = j.exec_command(f"bash {remote}", timeout=180)
    print(o.read().decode("utf-8", "replace"))
    err = e.read().decode("utf-8", "replace")
    if err.strip():
        print("STDERR:", err[:2500])
    j.close()


if __name__ == "__main__":
    main()
