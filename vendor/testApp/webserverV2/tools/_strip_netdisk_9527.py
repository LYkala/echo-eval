#!/usr/bin/env python3
"""Remove netdisk + mcloud search from remote webserverV2_9527 only."""
from __future__ import annotations

from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")

REMOTE_SH = r"""
set -euo pipefail
DST=/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9527
PY=/opt/VoxTest/.venv/bin/python3.10

P6b=$(ss -lntp | awk '/:9526/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
P8b=$(ss -lntp | awk '/:9528/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "before 9526=$P6b 9528=$P8b"

# --- strip register blocks from webserver_v2.py ---
python3 - <<'PY'
from pathlib import Path
p = Path('/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9527/webserver_v2.py')
text = p.read_text(encoding='utf-8')
old = text
# Remove netdisk + mcloud register try/except blocks
import re
pat = re.compile(
    r"\n# 网盘搜索过程.*?_logging\.getLogger\(__name__\)\.warning\('netdisk_search register failed: %s', _netdisk_ex\)\n"
    r"\n# 移动网盘搜索过程.*?_logging\.getLogger\(__name__\)\.warning\('mcloud_search register failed: %s', _mcloud_ex\)\n",
    re.S,
)
text2, n = pat.subn('\n', text, count=1)
if n != 1:
    # fallback: line-based removal around register_netdisk / register_mcloud
    lines = text.splitlines(True)
    out = []
    i = 0
    while i < len(lines):
        chunk = ''.join(lines[i:i+8])
        if 'register_netdisk' in chunk or '网盘搜索过程' in lines[i] or 'register_mcloud' in chunk or '移动网盘搜索过程' in lines[i]:
            # skip until after the except warning line of this block
            while i < len(lines):
                out_line = lines[i]
                i += 1
                if "register failed" in out_line and ('_netdisk_ex' in out_line or '_mcloud_ex' in out_line):
                    break
            continue
        out.append(lines[i])
        i += 1
    text2 = ''.join(out)
    if 'register_netdisk' in text2 or 'register_mcloud' in text2:
        raise SystemExit('failed to strip register_* from webserver_v2.py')
p.write_text(text2, encoding='utf-8')
print('webserver_v2.py stripped ok; changed=', text2 != old)
PY

# --- strip nav items from base.html ---
python3 - <<'PY'
from pathlib import Path
import re
p = Path('/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9527/templates/base.html')
text = p.read_text(encoding='utf-8')
# remove the two <li> blocks for pan-search and mcloud-search
pat = re.compile(
    r"\s*<li>\s*<a class=\"\{% if page == 'pan-search' %\}active\{% endif %\}\".*?</li>\s*"
    r"<li>\s*<a class=\"\{% if page == 'mcloud-search' %\}active\{% endif %\}\".*?</li>",
    re.S,
)
text2, n = pat.subn('\n', text, count=1)
if n != 1:
    # fallback remove individually
    for page in ('pan-search', 'mcloud-search'):
        pat2 = re.compile(
            rf"\s*<li>\s*<a class=\"\{{% if page == '{page}' %\}}active\{{% endif %}}\"[\s\S]*?</li>",
            re.S,
        )
        text2, n2 = pat2.subn('\n', text2 if n else text, count=1)
        text = text2
        print(f'removed {page}: {n2}')
else:
    print('removed both nav li via combined pattern')
if "pan-search" in text2 or "mcloud-search" in text2:
    # allow remaining mentions only if not nav - check
    if "href=\"{{ gui_base_path }}/pan-search\"" in text2 or "href=\"{{ gui_base_path }}/mcloud-search\"" in text2:
        raise SystemExit('nav links still present in base.html')
p.write_text(text2, encoding='utf-8')
print('base.html stripped ok')
PY

# optional: remove packages so they cannot be re-imported accidentally
rm -rf "$DST/netdisk_search" "$DST/mcloud_search"
rm -f "$DST/templates/pan_search.html" "$DST/templates/pan_search_frame.html"
rm -f "$DST/templates/mcloud_search.html" "$DST/templates/mcloud_search_frame.html"
rm -rf "$DST/static/netdisk"

# syntax check
$PY -m py_compile "$DST/webserver_v2.py"

systemctl restart webserver-9527.service
sleep 3
systemctl is-active webserver-9527.service

echo '=== ports ==='
ss -lntp | grep -E ':9526|:9527|:9528' || true
P6a=$(ss -lntp | awk '/:9526/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
P8a=$(ss -lntp | awk '/:9528/{print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
echo "after 9526=$P6a 9528=$P8a"

echo '=== verify 9527 no pan-search ==='
echo -n 'pan-search: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9527/pan-search || true
echo -n 'pan-search/app: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9527/pan-search/app || true
echo -n 'mcloud-search: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9527/mcloud-search || true
echo -n 'api/netdisk: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9527/api/netdisk/run/status || true
echo -n 'packages: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9527/api/download/packages || true

# 9526 still has netdisk
echo -n '9526 pan-search/app: '; curl -sS -o /dev/null -w '%{http_code}\n' --max-time 8 http://127.0.0.1:9526/pan-search/app || true

# greps
echo '--- 9527 register remnants ---'
grep -nE 'register_netdisk|register_mcloud|pan-search|mcloud-search' "$DST/webserver_v2.py" "$DST/templates/base.html" 2>/dev/null | head -20 || echo '(none)'

if [ "$P6b" != "$P6a" ]; then echo "WARN: 9526 pid changed"; else echo 'OK: 9526 untouched'; fi
if [ "$P8b" != "$P8a" ]; then echo "WARN: 9528 pid changed"; else echo 'OK: 9528 untouched'; fi
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
    with sftp.file("/tmp/_strip_netdisk_9527.sh", "w") as f:
        f.write(REMOTE_SH)
    sftp.close()
    _, o, e = j.exec_command("bash /tmp/_strip_netdisk_9527.sh", timeout=120)
    print(o.read().decode("utf-8", "replace"))
    err = e.read().decode("utf-8", "replace")
    if err.strip():
        print("STDERR:", err[:3000])
    j.close()


if __name__ == "__main__":
    main()
