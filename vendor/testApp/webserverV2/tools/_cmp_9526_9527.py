#!/usr/bin/env python3
"""Compare remote webserver 9526 vs 9527 (exclude netdisk/mcloud)."""
from __future__ import annotations

from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")

REMOTE_SCRIPT = r"""
set -e
A=/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9526
B=/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2

echo '=== ports/pids ==='
ss -lntp | grep -E ':9526|:9527' || true

echo
echo '=== systemd unit files ==='
for u in webserver-9526.service webserver-9527.service; do
  echo "-- $u --"
  systemctl is-active "$u" 2>/dev/null || echo "(inactive/missing)"
  systemctl cat "$u" 2>/dev/null | sed -n '1,35p' || true
done

echo
echo '=== WorkingDirectory / ExecStart ==='
ps -o pid,cmd -p $(ss -lntp | awk '/:9526/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1) 2>/dev/null || true
ps -o pid,cmd -p $(ss -lntp | awk '/:9527/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1) 2>/dev/null || true

echo
echo '=== top-level only-in ==='
echo '-- only 9526 --'
comm -23 <(ls -1 "$A" | sort) <(ls -1 "$B" | sort)
echo '-- only 9527 --'
comm -13 <(ls -1 "$A" | sort) <(ls -1 "$B" | sort)

echo
echo '=== shared key file md5 ==='
for f in \
  webserver_v2.py webtest_eval.py llm_eval.py emu_adb_bridge.py \
  templates/base.html templates/index.html templates/dispatch.html \
  templates/results.html templates/testcases.html templates/emulator.html \
  templates/emulator_manage.html templates/download.html \
  configs/model.yaml
do
  if [ -f "$A/$f" ] && [ -f "$B/$f" ]; then
    ha=$(md5sum "$A/$f" | awk '{print $1}')
    hb=$(md5sum "$B/$f" | awk '{print $1}')
    if [ "$ha" != "$hb" ]; then echo "DIFF $f"; else echo "SAME $f"; fi
  elif [ -f "$A/$f" ]; then echo "ONLY9526 $f"
  elif [ -f "$B/$f" ]; then echo "ONLY9527 $f"
  else echo "MISS both $f"; fi
done

# file lists excluding netdisk/mcloud/pycache/results/bak
list_files() {
  (cd "$1" && find . -type f \
    ! -path './netdisk_search/*' \
    ! -path './mcloud_search/*' \
    ! -path './static/netdisk/*' \
    ! -path './templates/pan_search*' \
    ! -path './templates/mcloud_search*' \
    ! -path '*/__pycache__/*' \
    ! -path './results/*' \
    ! -path './netdisk_search_results/*' \
    ! -path './mcloud_search_results/*' \
    ! -name '*.pyc' \
    ! -name '*.bak*' \
    ! -path './.git/*' \
    ! -path './excel_datasets/*' \
    ! -path './testfiles/*' \
    ! -path './tasks/*' \
    ! -path './uploads/*' \
    ! -path './logs/*' \
    ! -path './apks/*' \
    ! -path './tools/_cloud*' \
    ! -path './tools/__pycache__/*' \
    | sort)
}
list_files "$A" > /tmp/a9526.lst
list_files "$B" > /tmp/b9527.lst

echo
echo '=== only-in-9526 files (excl netdisk/mcloud/data noise) count ==='
comm -23 /tmp/a9526.lst /tmp/b9527.lst | tee /tmp/only9526.txt | wc -l
echo '--- sample ---'
head -60 /tmp/only9526.txt

echo
echo '=== only-in-9527 files count ==='
comm -13 /tmp/a9526.lst /tmp/b9527.lst | tee /tmp/only9527.txt | wc -l
echo '--- sample ---'
head -60 /tmp/only9527.txt

echo
echo '=== content-diff shared .py/.html/.js/.css (excl listed noise) ==='
comm -12 /tmp/a9526.lst /tmp/b9527.lst > /tmp/both.lst
n=0
while IFS= read -r f; do
  case "$f" in
    *.py|*.html|*.js|*.css|*.json|*.yaml|*.yml|*.md|*.sh|*.service) ;;
    *) continue ;;
  esac
  ha=$(md5sum "$A/$f" | awk '{print $1}')
  hb=$(md5sum "$B/$f" | awk '{print $1}')
  if [ "$ha" != "$hb" ]; then
    echo "DIFF $f"
    n=$((n+1))
  fi
done < /tmp/both.lst
echo "(diff_count=$n)"

echo
echo '=== webserver_v2.py: netdisk/mcloud register lines ==='
echo '--9526--'
grep -nE 'netdisk|mcloud|pan-search|9526|9527|PORT' "$A/webserver_v2.py" | head -40
echo '--9527--'
grep -nE 'netdisk|mcloud|pan-search|9526|9527|PORT' "$B/webserver_v2.py" | head -40

echo
echo '=== base.html nav: 网盘/模拟器 related ==='
echo '--9526--'
grep -nE 'pan-search|mcloud|网盘|模拟器' "$A/templates/base.html" | head -20
echo '--9527--'
grep -nE 'pan-search|mcloud|网盘|模拟器' "$B/templates/base.html" | head -20

echo
echo '=== apk packages API presence ==='
curl -sS -m 8 http://127.0.0.1:9526/api/download/packages 2>/dev/null | python3 -c 'import sys,json; d=json.load(sys.stdin); print("9526 pkgs", [x.get("id") for x in (d if isinstance(d,list) else d.get("packages",d.get("items",[]))) ][:20] if isinstance(d,(list,dict)) else d)' 2>/dev/null || echo '9526 packages fail'
curl -sS -m 8 http://127.0.0.1:9527/api/download/packages 2>/dev/null | python3 -c 'import sys,json; d=json.load(sys.stdin); print("9527 pkgs", [x.get("id") for x in (d if isinstance(d,list) else d.get("packages",d.get("items",[]))) ][:20] if isinstance(d,(list,dict)) else d)' 2>/dev/null || echo '9527 packages fail'

echo
echo '=== diffstat for DIFF key files (brief) ==='
for f in webserver_v2.py webtest_eval.py emu_adb_bridge.py templates/base.html templates/results.html templates/dispatch.html; do
  if [ -f "$A/$f" ] && [ -f "$B/$f" ]; then
    ha=$(md5sum "$A/$f" | awk '{print $1}')
    hb=$(md5sum "$B/$f" | awk '{print $1}')
    if [ "$ha" != "$hb" ]; then
      echo "---- $f ----"
      diff -u "$B/$f" "$A/$f" | head -80
    fi
  fi
done
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
    remote = "/tmp/_cmp_9526_9527.sh"
    with sftp.file(remote, "w") as f:
        f.write(REMOTE_SCRIPT)
    sftp.close()
    _, o, e = j.exec_command(f"bash {remote}", timeout=180)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    print(out)
    if err.strip():
        print("STDERR:", err[:3000])
    j.close()


if __name__ == "__main__":
    main()
