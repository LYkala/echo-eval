#!/usr/bin/env python3
"""Open scrcpy to remote ARM64 Android emulator via jump host."""
from __future__ import annotations

import argparse
import base64
import os
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_from_windows import JUMP, TARGET, parse_creds, run

ROOT = Path(__file__).resolve().parent
SCRCPY_DIR = ROOT / "tools" / "scrcpy"
LOCAL_ADB = Path.home() / "AppData" / "Local" / "Android" / "Sdk" / "platform-tools" / "adb.exe"
DEFAULT_PORT = 15555


def find_scrcpy() -> Path:
    for p in SCRCPY_DIR.rglob("scrcpy.exe"):
        return p
    which = subprocess.run(["where", "scrcpy"], capture_output=True, text=True)
    if which.returncode == 0:
        return Path(which.stdout.strip().splitlines()[0])
    raise SystemExit(f"scrcpy.exe not found under {SCRCPY_DIR}")


def find_adb() -> Path:
    if LOCAL_ADB.is_file():
        return LOCAL_ADB
    which = subprocess.run(["where", "adb"], capture_output=True, text=True)
    if which.returncode == 0:
        return Path(which.stdout.strip().splitlines()[0])
    for p in SCRCPY_DIR.rglob("adb.exe"):
        return p
    raise SystemExit("adb.exe not found")


def pipe_chan(sock: socket.socket, chan) -> None:
    def s2c():
        try:
            while True:
                data = sock.recv(65536)
                if not data:
                    break
                chan.sendall(data)
        except OSError:
            pass
        finally:
            try:
                chan.close()
            except Exception:
                pass

    def c2s():
        try:
            while True:
                data = chan.recv(65536)
                if not data:
                    break
                sock.sendall(data)
        except OSError:
            pass
        finally:
            try:
                sock.close()
            except Exception:
                pass

    t1 = threading.Thread(target=s2c, daemon=True)
    t2 = threading.Thread(target=c2s, daemon=True)
    t1.start()
    t2.start()
    t1.join()
    t2.join()


def start_forward(local_port: int):
    ju, jp = parse_creds()[0]
    tu, tp = parse_creds()[1]
    jump = paramiko.SSHClient()
    jump.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    jump.connect(JUMP[0], 22, username=ju, password=jp, timeout=90, banner_timeout=90)
    jump.get_transport().set_keepalive(30)
    chan = jump.get_transport().open_channel("direct-tcpip", (TARGET[0], TARGET[1]), ("127.0.0.1", 0))
    target = paramiko.SSHClient()
    target.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    target.connect(TARGET[0], TARGET[1], username=tu, password=tp, sock=chan, timeout=90, banner_timeout=90)
    target.get_transport().set_keepalive(30)
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", local_port))
    srv.listen(32)
    srv.settimeout(1.0)
    stop = threading.Event()

    def accept_loop():
        while not stop.is_set():
            try:
                client, _addr = srv.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            try:
                remote_chan = target.get_transport().open_channel(
                    "direct-tcpip", ("127.0.0.1", 5555), ("127.0.0.1", 0)
                )
            except Exception as ex:
                print("forward open failed:", ex, flush=True)
                client.close()
                continue
            threading.Thread(target=pipe_chan, args=(client, remote_chan), daemon=True).start()

    threading.Thread(target=accept_loop, daemon=True).start()
    return jump, target, srv, stop


def authorize_local_adb_key(jump, target) -> None:
    pub = Path.home() / ".android" / "adbkey.pub"
    if not pub.is_file():
        print("WARN: no local adbkey.pub", flush=True)
        return
    key_line = pub.read_text(encoding="utf-8", errors="replace").strip().splitlines()[0].strip()
    sftp = target.open_sftp()
    with sftp.file("/tmp/win_adbkey.pub", "w") as f:
        f.write(key_line + "\n")
    sftp.close()
    tu, _tp = parse_creds()[1]
    script = r"""
set +e
LAB=/root/aisf-voice-lab
ADB="python3 $LAB/mini_adb_auth.py"
$ADB shell "mkdir -p /data/misc/adb; touch /data/misc/adb/adb_keys"
$ADB push /tmp/win_adbkey.pub /data/local/tmp/win_adbkey.pub
python3 - <<"PY"
from pathlib import Path
import subprocess
new = Path("/tmp/win_adbkey.pub").read_text(encoding="utf-8", errors="replace").strip()
rc = subprocess.run(["python3", "/root/aisf-voice-lab/mini_adb_auth.py", "shell", "cat /data/misc/adb/adb_keys"], capture_output=True, text=True)
keys = rc.stdout or ""
if new and new in keys:
    print("KEY_ALREADY")
else:
    subprocess.check_call(["python3", "/root/aisf-voice-lab/mini_adb_auth.py", "shell", "sh", "-c", "cat /data/local/tmp/win_adbkey.pub >> /data/misc/adb/adb_keys"])
    print("KEY_APPENDED")
PY
$ADB shell "setprop ctl.restart adbd" 2>/dev/null || true
sleep 2
$ADB devices
"""
    b64 = base64.b64encode(script.encode()).decode()
    _c, o, e = run(
        jump,
        'ssh -o StrictHostKeyChecking=no -o BatchMode=yes -p %s %s@%s "echo %s | base64 -d | bash"'
        % (TARGET[1], tu, TARGET[0], b64),
        90,
    )
    print((o or e or "")[-800:], flush=True)


def main() -> int:
    ap = argparse.ArgumentParser(description="scrcpy to remote ARM emulator")
    ap.add_argument("--port", type=int, default=DEFAULT_PORT)
    ap.add_argument("--max-size", type=int, default=1024)
    ap.add_argument("--bit-rate", default="4M")
    args = ap.parse_args()
    scrcpy = find_scrcpy()
    adb = find_adb()
    serial = f"127.0.0.1:{args.port}"
    print("scrcpy:", scrcpy, flush=True)
    print("adb:", adb, flush=True)
    print(f"tunnel 127.0.0.1:{args.port} -> target:5555 ...", flush=True)
    jump, target, srv, stop = start_forward(args.port)
    try:
        print("authorizing Windows adb key...", flush=True)
        authorize_local_adb_key(jump, target)
        time.sleep(1.5)
        subprocess.run([str(adb), "disconnect", serial], capture_output=True)
        r = subprocess.run([str(adb), "connect", serial], capture_output=True, text=True, timeout=30)
        print(r.stdout or r.stderr, flush=True)
        online = False
        for i in range(10):
            devices = subprocess.run([str(adb), "devices"], capture_output=True, text=True, timeout=15)
            print(devices.stdout.strip(), flush=True)
            if serial in devices.stdout and "\tdevice" in devices.stdout:
                online = True
                break
            subprocess.run([str(adb), "connect", serial], capture_output=True)
            time.sleep(1)
        if not online:
            print("ERROR: adb device not online", flush=True)
            return 2
        cmd = [
            str(scrcpy), "-s", serial,
            "--max-size", str(args.max_size),
            "--video-bit-rate", args.bit_rate,
            "--window-title", "AISF ARM64 Emulator",
            "--no-audio",
        ]
        print("launching:", " ".join(cmd), flush=True)
        env = dict(os.environ)
        env["ADB"] = str(adb)
        proc = subprocess.Popen(cmd, cwd=str(scrcpy.parent), env=env)
        print("scrcpy running. Close window or Ctrl+C to stop.", flush=True)
        try:
            return proc.wait()
        except KeyboardInterrupt:
            proc.terminate()
            return 0
    finally:
        try:
            stop.set()
        except Exception:
            pass
        try:
            srv.close()
        except Exception:
            pass
        try:
            target.close()
        except Exception:
            pass
        try:
            jump.close()
        except Exception:
            pass
        subprocess.run([str(adb), "disconnect", serial], capture_output=True)


if __name__ == "__main__":
    raise SystemExit(main())
