#!/usr/bin/env python3
"""Push VirtualMic.monitor PCM into guest via emulator redir target port."""
from __future__ import annotations

import os
import socket
import subprocess
import sys
import time

HOST = os.environ.get("VMIC_HOST", "127.0.0.1")
PORT = int(os.environ.get("VMIC_PORT", "19201"))  # host redir port
SOURCE = os.environ.get("VMIC_SOURCE", "VirtualMic.monitor")


def main() -> int:
    os.environ.setdefault("PULSE_RUNTIME_PATH", "/tmp/pulse-root")
    os.environ.setdefault("XDG_RUNTIME_DIR", "/tmp/pulse-root")
    # wait until redir/app ready
    for i in range(40):
        try:
            sock = socket.create_connection((HOST, PORT), timeout=1.0)
            break
        except OSError:
            time.sleep(0.25)
    else:
        print("cannot connect", HOST, PORT, file=sys.stderr)
        return 1
    print(f"connected {HOST}:{PORT}", flush=True)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    proc = subprocess.Popen(
        [
            "parec",
            f"--device={SOURCE}",
            "--rate=16000",
            "--channels=1",
            "--format=s16le",
            "--latency-msec=20",
            "--raw",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    try:
        assert proc.stdout is not None
        while True:
            chunk = proc.stdout.read(4096)
            if not chunk:
                break
            sock.sendall(chunk)
    except OSError as ex:
        print("send end:", ex, flush=True)
    finally:
        try:
            sock.close()
        except OSError:
            pass
        proc.terminate()
        try:
            proc.wait(timeout=2)
        except Exception:
            proc.kill()
    return 0


if __name__ == "__main__":
    sys.exit(main())
