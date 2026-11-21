#!/usr/bin/env python3
"""Deploy webserver_v2.py excel-template change to 9526 and 9527 only."""
from __future__ import annotations

import time
from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")
LOCAL = Path(r"e:/TestAPPGUI/testApp/webserverV2/webserver_v2.py")
TARGETS = (
    ("/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9526", "webserver-9526.service", 9526),
    ("/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9527", "webserver-9527.service", 9527),
)


def pw() -> str:
    for line in CREDS.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip().startswith("root") and "CLOUD" in line:
            return line.split()[-1]
    return ""


def main() -> None:
    if not LOCAL.is_file():
        raise SystemExit(f"missing {LOCAL}")
    j = paramiko.SSHClient()
    j.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    j.connect("124.71.69.102", 22, "root", pw(), timeout=30)

    def run(cmd: str, t: int = 90) -> str:
        _, o, e = j.exec_command(cmd, timeout=t)
        return (o.read() + e.read()).decode("utf-8", "replace")

    print("before:\n", run("ss -lntp | grep -E ':9526|:9527|:9528' || true"))
    p8 = run("ss -lntp | awk '/:9528/{print}' | sed -n 's/.*pid=\\([0-9]*\\).*/\\1/p' | head -1").strip()
    print("9528_pid_before=", p8)

    stamp = time.strftime("%Y%m%d_%H%M%S")
    sftp = j.open_sftp()
    for remote_dir, unit, port in TARGETS:
        remote = f"{remote_dir}/webserver_v2.py"
        run(f"cp -a {remote} {remote}.bak_{stamp} 2>/dev/null || true")
        sftp.put(str(LOCAL), remote)
        print("uploaded", remote)
        # drop stale on-disk template so generated danger0903 template is used
        run(f"rm -f {remote_dir}/用例-格式.xlsx")
        print(run(f"/opt/VoxTest/.venv/bin/python3.10 -m py_compile {remote} && echo SYNTAX_OK_{port}"))
        print(run(f"systemctl restart {unit} && sleep 2 && systemctl is-active {unit}"))
    sftp.close()

    time.sleep(1)
    print("after:\n", run("ss -lntp | grep -E ':9526|:9527|:9528' || true"))
    p8a = run("ss -lntp | awk '/:9528/{print}' | sed -n 's/.*pid=\\([0-9]*\\).*/\\1/p' | head -1").strip()
    print("9528_pid_after=", p8a)
    if p8 and p8a and p8 != p8a:
        print("WARN: 9528 pid changed")
    else:
        print("OK: 9528 untouched")

    for port in (9526, 9527):
        out = run(
            f"curl -sS --max-time 15 http://127.0.0.1:{port}/api/files/excel-template "
            f"-o /tmp/tpl_{port}.xlsx -w '%{{http_code}}'"
        )
        print(f"template {port} http={out.strip()}")
        print(
            run(
                f"/opt/VoxTest/.venv/bin/python3.10 - <<'PY'\n"
                f"from openpyxl import load_workbook\n"
                f"wb=load_workbook('/tmp/tpl_{port}.xlsx', read_only=True)\n"
                f"ws=wb.active\n"
                f"h=[c.value for c in next(ws.iter_rows(min_row=1,max_row=1))]\n"
                f"print('headers', h[:5], '...', 'count', len([x for x in h if x]))\n"
                f"print('has APP', 'APP' in h, 'has 用例编号', '用例编号' in h)\n"
                f"wb.close()\n"
                f"PY"
            )
        )
    j.close()


if __name__ == "__main__":
    main()
