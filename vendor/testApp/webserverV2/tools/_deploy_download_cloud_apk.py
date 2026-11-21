#!/usr/bin/env python3
"""Deploy software-download + latest devicesdk-*.apk to jump 9527."""
from __future__ import annotations

import time
from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")
LOCAL = Path(r"e:/TestAPPGUI/testApp/webserverV2")
REMOTE = "/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2"

TEXT_FILES = (
    "webserver_v2.py",
    "templates/results.html",
    "templates/index.html",
)


def pw() -> str:
    for line in CREDS.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip().startswith("root") and "CLOUD" in line:
            return line.split()[-1]
    return ""


def newest_devicesdk_apk() -> Path:
    cands: list[Path] = []
    for folder in (
        LOCAL / "apks" / "devicesdkdemo-cloud-v3",
        LOCAL / "tools" / "_cloud_v3_work" / "dist",
        LOCAL / "apks",
    ):
        if not folder.is_dir():
            continue
        cands.extend(folder.glob("devicesdk-*.apk"))
    if not cands:
        legacy = LOCAL / "apks" / "DeviceSDK-Demo-cloud-v3.apk"
        if legacy.is_file():
            return legacy
        raise SystemExit("no devicesdk-*.apk found; run rebuild.ps1 first")
    cands.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return cands[0]


def main() -> None:
    text = (LOCAL / "webserver_v2.py").read_text(encoding="utf-8")
    if "managed_glob" not in text or "devicesdk-*.apk" not in text:
        raise SystemExit("webserver devicesdk naming markers missing")
    if "devicesdkdemo_cloud_v3" not in text:
        raise SystemExit("cloud package missing")

    apk = newest_devicesdk_apk()
    print("local apk:", apk, apk.stat().st_size)

    j = paramiko.SSHClient()
    j.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    j.connect("124.71.69.102", 22, "root", pw(), timeout=30)

    def run(cmd: str, t: int = 60) -> str:
        _, o, e = j.exec_command(cmd, timeout=t)
        o.channel.settimeout(t)
        try:
            return (o.read() + e.read()).decode("utf-8", "replace")
        except Exception as ex:
            return f"(err {ex})"

    stamp = time.strftime("%Y%m%d_%H%M%S")
    sftp = j.open_sftp()
    for rel in TEXT_FILES:
        remote = f"{REMOTE}/{rel}"
        print(run(f"cp -a {remote} {remote}.bak_dl_{stamp}"))
        sftp.put(str(LOCAL / rel), remote)
        print("uploaded", remote)

    remote_dir = f"{REMOTE}/apks/devicesdkdemo-cloud-v3"
    print(run(f"mkdir -p {remote_dir}"))
    # 清掉旧包，只留本次 devicesdk-日期时间.apk
    print(run(f"rm -f {remote_dir}/devicesdk-*.apk {remote_dir}/devicesdkdemo.apk"))
    remote_apk = f"{remote_dir}/{apk.name}"
    sftp.put(str(apk), remote_apk)
    print("uploaded", remote_apk, apk.stat().st_size)
    # 兼容旧路径别名
    sftp.put(str(apk), f"{REMOTE}/apks/DeviceSDK-Demo-cloud-v3.apk")
    print("uploaded DeviceSDK-Demo-cloud-v3.apk alias")
    sftp.close()

    print(run(f"/opt/VoxTest/.venv/bin/python3.10 -m py_compile {REMOTE}/webserver_v2.py && echo SYNTAX_OK"))
    print(run("pkill -f 'python3.10 -u webserver_v2' || true"))
    print(run(
        f"cd {REMOTE} && nohup /opt/VoxTest/.venv/bin/python3.10 -u webserver_v2.py "
        f">> logs/webserver_stdout.log 2>&1 & echo start_pid=$!; sleep 3; "
        f"ss -lntp | grep 9527 || echo PORT_DOWN"
    ))
    print(run(
        "curl -sS --max-time 8 http://127.0.0.1:9527/api/download/packages "
        "| python3.10 -c \"import sys,json; d=json.load(sys.stdin); "
        "pkgs=d.get('packages') or []; "
        "p=next((x for x in pkgs if x.get('id')=='devicesdkdemo_cloud_v3'), None); "
        "print(p and {k:p.get(k) for k in ('id','filename','available','size')} )\""
    ))
    print(run(f"ls -la {remote_dir}"))
    j.close()
    print("OK")


if __name__ == "__main__":
    main()
