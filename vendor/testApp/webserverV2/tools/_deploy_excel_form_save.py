#!/usr/bin/env python3
"""Deploy excel FormData save fix to 9527."""
from __future__ import annotations

import time
from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")
LOCAL = Path(r"e:/TestAPPGUI/testApp/webserverV2")
REMOTE = "/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2"
FILES = ("webserver_v2.py", "templates/testcases.html")


def pw() -> str:
    for line in CREDS.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip().startswith("root") and "CLOUD" in line:
            return line.split()[-1]
    return ""


def main() -> None:
    py = (LOCAL / "webserver_v2.py").read_text(encoding="utf-8")
    html = (LOCAL / "templates/testcases.html").read_text(encoding="utf-8")
    if "DS1|" not in py or "_handle_dataset_save_via_notepad_content" not in py:
        raise SystemExit("server notepad-ds protocol missing")
    if "DS1|begin|" not in html:
        raise SystemExit("client notepad-ds save missing")
    if "EXCEL_PAGE_SIZE" not in html:
        raise SystemExit("client pagination missing")

    j = paramiko.SSHClient()
    j.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    j.connect("124.71.69.102", 22, "root", pw(), timeout=30)

    def run(cmd: str, t: int = 30) -> str:
        _, o, e = j.exec_command(cmd, timeout=t)
        o.channel.settimeout(t)
        try:
            return (o.read() + e.read()).decode("utf-8", "replace")
        except Exception as ex:
            return f"(err {ex})"

    stamp = time.strftime("%Y%m%d_%H%M%S")
    sftp = j.open_sftp()
    for rel in FILES:
        remote = f"{REMOTE}/{rel}"
        run(f"cp -a {remote} {remote}.bak_excel_form_{stamp}")
        sftp.put(str(LOCAL / rel), remote)
        print("uploaded", remote)
    sftp.close()

    print(run(f"/opt/VoxTest/.venv/bin/python3.10 -m py_compile {REMOTE}/webserver_v2.py && echo SYNTAX_OK"))
    run("pkill -f 'python3.10 -u webserver_v2' || true")
    print(run(
        f"cd {REMOTE} && nohup /opt/VoxTest/.venv/bin/python3.10 -u webserver_v2.py "
        f">> logs/webserver_stdout.log 2>&1 & echo start_pid=$!; sleep 2; "
        f"ss -lntp | grep 9527 || echo PORT_DOWN"
    ))
    print(run(
        "python3 - <<'PY'\n"
        "import json, urllib.request\n"
        "from urllib.request import Request\n"
        "url='http://127.0.0.1:9527/api/excel-datasets/12/content'\n"
        "with urllib.request.urlopen(url) as r: d=json.load(r)\n"
        "import base64, time, random\n"
        "def b64(o):\n"
        "  return base64.b64encode(json.dumps(o, ensure_ascii=False).encode()).decode()\n"
        "def post(content):\n"
        "  body=json.dumps({'content': content}, ensure_ascii=False).encode()\n"
        "  req=Request('http://127.0.0.1:9527/api/download/notepad', data=body, method='POST',\n"
        "              headers={'Content-Type':'application/json','Accept':'application/json'})\n"
        "  with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read().decode())\n"
        "sid=f't{int(time.time())}_{random.randint(1000,9999)}'\n"
        "st,j=post(f\"DS1|begin|{sid}|12|{len(d['rows'])}|{b64(d['headers'])}\")\n"
        "print('begin', st, j)\n"
        "rows=d['rows']; cs=10\n"
        "for i in range(0,len(rows),cs):\n"
        "  st,j=post(f\"DS1|chunk|{sid}|{i}|{b64(rows[i:i+cs])}\")\n"
        "  if not j.get('success'): print('chunk_fail', i, j); break\n"
        "else:\n"
        "  st,j=post(f'DS1|done|{sid}')\n"
        "  print('DONE', st, j)\n"
        "html=urllib.request.urlopen('http://127.0.0.1:9527/testcases').read().decode()\n"
        "print('page_ds1', 'DS1|begin|' in html)\n"
        "print('page_page', 'EXCEL_PAGE_SIZE' in html)\n"
        "PY"
    ))
    print("OK")
    j.close()


if __name__ == "__main__":
    main()
