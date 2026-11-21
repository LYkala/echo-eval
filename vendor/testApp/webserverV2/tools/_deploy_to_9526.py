#!/usr/bin/env python3
"""Deploy local webserverV2 changes to remote 9526 only (never touch 9527)."""
from __future__ import annotations

import argparse
import time
from pathlib import Path

import paramiko

CREDS = Path(r"e:/TestAPPGUI/testApp/android-emulator/华为云服务器.txt")
LOCAL = Path(r"e:/TestAPPGUI/testApp/webserverV2")
REMOTE = "/opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9526"
PORT = 9526

DEFAULT_TEXT = (
    "webserver_v2.py",
    "llm_eval.py",
    "templates/results.html",
    "templates/dispatch.html",
    "templates/index.html",
    "templates/testcases.html",
)

NETDISK_FILES = (
    "webserver_v2.py",
    "templates/base.html",
    "templates/pan_search.html",
    "templates/pan_search_frame.html",
    "static/netdisk/app.js",
    "static/netdisk/app.css",
    "netdisk_search/__init__.py",
    "netdisk_search/config.py",
    "netdisk_search/blueprint.py",
    "netdisk_search/adb_util.py",
    "netdisk_search/dataset.py",
    "netdisk_search/runner.py",
)

MCLOUD_FILES = (
    "webserver_v2.py",
    "templates/base.html",
    "templates/mcloud_search.html",
    "templates/mcloud_search_frame.html",
    "static/netdisk/app.js",
    "static/netdisk/app.css",
    "mcloud_search/__init__.py",
    "mcloud_search/config.py",
    "mcloud_search/blueprint.py",
    "mcloud_search/adb_util.py",
    "mcloud_search/dataset.py",
    "mcloud_search/runner.py",
)


def pw() -> str:
    for line in CREDS.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip().startswith("root") and "CLOUD" in line:
            return line.split()[-1]
    return ""


def newest_devicesdk_apk() -> Path | None:
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
        return legacy if legacy.is_file() else None
    cands.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return cands[0]


def main() -> None:
    ap = argparse.ArgumentParser(description="Deploy to remote webserver 9526 only")
    ap.add_argument("--files", nargs="*", default=list(DEFAULT_TEXT), help="relative paths under webserverV2")
    ap.add_argument("--apk", action="store_true", help="also upload newest devicesdk-*.apk")
    ap.add_argument("--netdisk", action="store_true", help="deploy 网盘搜索 tab + package (+ data xlsx)")
    ap.add_argument("--mcloud", action="store_true", help="deploy 移动网盘搜索 tab + package (+ data xlsx)")
    ap.add_argument("--no-restart", action="store_true", help="upload only, do not restart 9526")
    args = ap.parse_args()

    files = list(args.files)
    if args.netdisk:
        if files == list(DEFAULT_TEXT):
            files = list(NETDISK_FILES)
        else:
            for f in NETDISK_FILES:
                if f not in files:
                    files.append(f)
    if args.mcloud:
        if files == list(DEFAULT_TEXT):
            files = list(MCLOUD_FILES)
        else:
            for f in MCLOUD_FILES:
                if f not in files:
                    files.append(f)

    j = paramiko.SSHClient()
    j.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    j.connect("124.71.69.102", 22, "root", pw(), timeout=30)

    def run(cmd: str, t: int = 90) -> str:
        _, o, e = j.exec_command(cmd, timeout=t)
        o.channel.settimeout(t)
        try:
            return (o.read() + e.read()).decode("utf-8", "replace")
        except Exception as ex:
            return f"(err {ex})"

    before = run("ss -lntp | grep -E ':9527|:9526' || true")
    print("ports before:\n", before)

    stamp = time.strftime("%Y%m%d_%H%M%S")
    sftp = j.open_sftp()
    for rel in files:
        local = LOCAL / rel
        if not local.is_file():
            raise SystemExit(f"missing local file: {local}")
        remote = f"{REMOTE}/{rel}"
        run(f"mkdir -p $(dirname {remote})")
        run(f"cp -a {remote} {remote}.bak_{stamp} 2>/dev/null || true")
        sftp.put(str(local), remote)
        print("uploaded", remote)

    if args.netdisk:
        data_dir = LOCAL / "netdisk_search" / "data"
        remote_data = f"{REMOTE}/netdisk_search/data"
        run(f"mkdir -p {remote_data} {REMOTE}/netdisk_search/results")
        for xlsx in sorted(data_dir.glob("*.xlsx")):
            if xlsx.name.endswith(".bak"):
                continue
            remote = f"{remote_data}/{xlsx.name}"
            sftp.put(str(xlsx), remote)
            print("uploaded", remote, xlsx.stat().st_size)

    if args.mcloud:
        data_dir = LOCAL / "mcloud_search" / "data"
        remote_data = f"{REMOTE}/mcloud_search/data"
        run(f"mkdir -p {remote_data} {REMOTE}/mcloud_search/results")
        for xlsx in sorted(data_dir.glob("*.xlsx")):
            if xlsx.name.endswith(".bak"):
                continue
            remote = f"{remote_data}/{xlsx.name}"
            sftp.put(str(xlsx), remote)
            print("uploaded", remote, xlsx.stat().st_size)

    if args.apk:
        apk = newest_devicesdk_apk()
        if apk is None:
            raise SystemExit("no devicesdk apk found")
        remote_dir = f"{REMOTE}/apks/devicesdkdemo-cloud-v3"
        run(f"mkdir -p {remote_dir}")
        run(f"rm -f {remote_dir}/devicesdk-*.apk {remote_dir}/devicesdkdemo.apk")
        sftp.put(str(apk), f"{remote_dir}/{apk.name}")
        sftp.put(str(apk), f"{REMOTE}/apks/DeviceSDK-Demo-cloud-v3.apk")
        print("uploaded apk", apk.name, apk.stat().st_size)

    sftp.close()

    if "webserver_v2.py" in files:
        print(run(f"/opt/VoxTest/.venv/bin/python3.10 -m py_compile {REMOTE}/webserver_v2.py && echo SYNTAX_OK"))
        if args.netdisk:
            print(run(
                f"/opt/VoxTest/.venv/bin/python3.10 -c "
                f"\"import sys; sys.path.insert(0,'{REMOTE}'); "
                f"from netdisk_search import register_netdisk; print('NETDISK_IMPORT_OK')\""
            ))
        if args.mcloud:
            print(run(
                f"/opt/VoxTest/.venv/bin/python3.10 -c "
                f"\"import sys; sys.path.insert(0,'{REMOTE}'); "
                f"from mcloud_search import register_mcloud; print('MCLOUD_IMPORT_OK')\""
            ))

    if not args.no_restart:
        print(run(
            "if systemctl list-unit-files | grep -q '^webserver-9526.service'; then "
            "systemctl restart webserver-9526.service; "
            "else bash /opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9526/stop_9526.sh; "
            "bash /opt/TestAppGUI/TestAPPGUI/testApp/webserverV2_9526/start_9526.sh; "
            "fi; sleep 2; "
            "ss -lntp | grep -E ':9527|:9526' || true; "
            "systemctl is-active webserver-9526.service 2>/dev/null || true"
        ))
        print(run(
            f"curl -sS --max-time 8 http://127.0.0.1:{PORT}/api/download/packages "
            "| python3.10 -c \"import sys,json; d=json.load(sys.stdin); "
            "pkgs=d.get('packages') or []; "
            "p=next((x for x in pkgs if x.get('id')=='devicesdkdemo_cloud_v3'), None); "
            "print(p and {k:p.get(k) for k in ('id','filename','available','size')} )\""
        ))
        if args.netdisk:
            print(run(
                f"curl -sS --max-time 8 -o /dev/null -w 'pan-search=%{{http_code}}\\n' "
                f"http://127.0.0.1:{PORT}/pan-search"
            ))
            print(run(
                f"curl -sS --max-time 8 http://127.0.0.1:{PORT}/api/netdisk/health"
            ))
            print(run(
                f"curl -sS --max-time 12 http://127.0.0.1:{PORT}/api/netdisk/devices"
            ))
        if args.mcloud:
            print(run(
                f"curl -sS --max-time 8 -o /dev/null -w 'mcloud-search=%{{http_code}}\\n' "
                f"http://127.0.0.1:{PORT}/mcloud-search"
            ))
            print(run(
                f"curl -sS --max-time 8 http://127.0.0.1:{PORT}/api/mcloud/health"
            ))
            print(run(
                f"curl -sS --max-time 12 http://127.0.0.1:{PORT}/api/mcloud/devices"
            ))
        print(run(
            "curl -sS --max-time 8 http://127.0.0.1:9527/api/devices "
            "| python3.10 -c \"import sys,json; d=json.load(sys.stdin); "
            "print('9527_ok', d.get('success'), 'devices', len(d.get('devices') or []))\""
        ))

    j.close()
    print("OK (9526 only)")


if __name__ == "__main__":
    main()
