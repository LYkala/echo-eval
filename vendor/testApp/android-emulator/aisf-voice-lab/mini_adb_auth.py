#!/usr/bin/env python3
"""ADB host client that talks directly to device :5555 with RSA auth (openssl)."""
from __future__ import annotations

import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

ADBKEY = Path.home() / ".android" / "adbkey"
ADBKEY_PUB = Path.home() / ".android" / "adbkey.pub"


def _recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("eof")
        buf += chunk
    return buf


def _msg(cmd: bytes, arg0: int, arg1: int, data: bytes = b"") -> bytes:
    magic = struct.unpack("<I", cmd)[0] ^ 0xFFFFFFFF
    return (
        struct.pack(
            "<IIIIII",
            struct.unpack("<I", cmd)[0],
            arg0,
            arg1,
            len(data),
            sum(data) & 0xFFFFFFFF,
            magic,
        )
        + data
    )


def _read(sock: socket.socket):
    hdr = _recv_exact(sock, 24)
    cmd, arg0, arg1, n, _crc, _magic = struct.unpack("<IIIIII", hdr)
    data = _recv_exact(sock, n) if n else b""
    return cmd, arg0, arg1, data


def _cmd_id(name: bytes) -> int:
    return struct.unpack("<I", name)[0]


def connect(host: str = "127.0.0.1", port: int = 5555, timeout: float = 20.0) -> socket.socket:
    sock = socket.create_connection((host, port), timeout=timeout)
    sock.settimeout(timeout)
    sock.sendall(_msg(b"CNXN", 0x01000001, 256 * 1024, b"host::\x00"))
    cmd, arg0, arg1, data = _read(sock)
    if cmd == _cmd_id(b"AUTH"):
        token_path = Path("/tmp/adb_token")
        sig_path = Path("/tmp/adb_sig")
        pem_path = Path("/tmp/adb.pem")
        token_path.write_bytes(data)
        subprocess.check_call(
            ["openssl", "pkey", "-in", str(ADBKEY), "-out", str(pem_path)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        subprocess.check_call(
            [
                "openssl",
                "rsautl",
                "-sign",
                "-inkey",
                str(pem_path),
                "-in",
                str(token_path),
                "-out",
                str(sig_path),
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        sock.sendall(_msg(b"AUTH", 2, 0, sig_path.read_bytes()))
        cmd, arg0, arg1, data = _read(sock)
        if cmd == _cmd_id(b"AUTH"):
            pub = ADBKEY_PUB.read_bytes() + b"\x00"
            sock.sendall(_msg(b"AUTH", 3, 0, pub))
            cmd, arg0, arg1, data = _read(sock)
    if cmd != _cmd_id(b"CNXN"):
        raise RuntimeError(f"CNXN failed cmd={cmd:#x} data={data[:80]!r}")
    return sock


def shell(sock: socket.socket, command: str, timeout: float = 180.0) -> str:
    sock.settimeout(timeout)
    local_id = 1
    sock.sendall(_msg(b"OPEN", local_id, 0, f"shell:{command}\x00".encode()))
    cmd, arg0, arg1, data = _read(sock)
    if cmd != _cmd_id(b"OKAY"):
        raise RuntimeError(f"OPEN failed cmd={cmd:#x} data={data!r}")
    remote_id = arg0
    out = b""
    while True:
        cmd, arg0, arg1, data = _read(sock)
        if cmd == _cmd_id(b"WRTE"):
            out += data
            sock.sendall(_msg(b"OKAY", local_id, remote_id))
        elif cmd == _cmd_id(b"CLSE"):
            sock.sendall(_msg(b"CLSE", local_id, remote_id))
            break
        elif cmd == _cmd_id(b"OKAY"):
            continue
        else:
            raise RuntimeError(f"unexpected cmd={cmd:#x}")
    return out.decode("utf-8", errors="replace")


def sync_push(sock: socket.socket, local_path: str, remote_path: str, mode: int = 0o644) -> None:
    data = Path(local_path).read_bytes()
    local_id = 2
    sock.sendall(_msg(b"OPEN", local_id, 0, b"sync:\x00"))
    cmd, arg0, arg1, _ = _read(sock)
    if cmd != _cmd_id(b"OKAY"):
        raise RuntimeError("sync OPEN failed")
    remote_id = arg0

    def wait_okay():
        while True:
            c, _a0, _a1, d = _read(sock)
            if c == _cmd_id(b"OKAY"):
                return
            if c == _cmd_id(b"WRTE"):
                sock.sendall(_msg(b"OKAY", local_id, remote_id))
            else:
                raise RuntimeError(f"sync unexpected {c:#x} {d[:40]!r}")

    def wrte(payload: bytes):
        sock.sendall(_msg(b"WRTE", local_id, remote_id, payload))
        wait_okay()

    def sync_pkt(ident: bytes, payload: bytes):
        wrte(ident + struct.pack("<I", len(payload)) + payload)

    sync_pkt(b"SEND", remote_path.encode() + b"," + f"{mode:04o}".encode())
    chunk = 64 * 1024
    for i in range(0, len(data), chunk):
        sync_pkt(b"DATA", data[i : i + chunk])
    sync_pkt(b"DONE", struct.pack("<I", int(time.time()) & 0xFFFFFFFF))
    while True:
        c, _a0, _a1, d = _read(sock)
        if c == _cmd_id(b"WRTE"):
            sock.sendall(_msg(b"OKAY", local_id, remote_id))
            if d.startswith(b"FAIL"):
                raise RuntimeError(d.decode(errors="replace"))
            if d.startswith(b"OKAY"):
                break
        elif c == _cmd_id(b"CLSE"):
            break
    sock.sendall(_msg(b"CLSE", local_id, remote_id))


def parse_argv(argv: list[str]):
    port = 5555
    filtered: list[str] = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a in ("-P",) and i + 1 < len(argv):
            port = int(argv[i + 1])
            i += 2
            continue
        if a in ("-s", "--serial") and i + 1 < len(argv):
            ser = argv[i + 1]
            if ser.startswith("emulator-"):
                # emulator-5554 -> host port 5555
                try:
                    console = int(ser.split("-", 1)[1])
                    port = console + 1
                except ValueError:
                    pass
            elif ":" in ser:
                port = int(ser.split(":")[-1])
            i += 2
            continue
        if a in ("-e",):  # use emulator
            i += 1
            continue
        if a.startswith("-") and a not in ("-a",):
            # skip unknown global flags
            if a in ("-d", "-t") and i + 1 < len(argv) and not argv[i + 1].startswith("-"):
                i += 2
                continue
            i += 1
            continue
        filtered = argv[i:]
        break
    return port, filtered


def emu_console(serial: str, command: str) -> str:
    """Send a command to the emulator console (adb emu ...)."""
    port = 5554
    if serial.startswith("emulator-"):
        try:
            port = int(serial.split("-", 1)[1])
        except ValueError:
            port = 5554
    token_path = Path.home() / ".emulator_console_auth_token"
    token = token_path.read_text().strip() if token_path.exists() else ""
    sock = socket.create_connection(("127.0.0.1", port), timeout=5)
    sock.settimeout(5)
    try:
        _ = sock.recv(256)
        out = b""
        if token:
            sock.sendall(f"auth {token}\n".encode())
            out += sock.recv(256)
        sock.sendall((command.strip() + "\n").encode())
        time.sleep(0.2)
        try:
            while True:
                chunk = sock.recv(4096)
                if not chunk:
                    break
                out += chunk
                if b"OK" in chunk or b"KO" in chunk:
                    break
        except socket.timeout:
            pass
        sock.sendall(b"quit\n")
        return out.decode("utf-8", errors="replace")
    finally:
        sock.close()


def shell_raw(sock: socket.socket, command: str, timeout: float = 180.0) -> bytes:
    """Like shell but return raw bytes (for exec-out)."""
    sock.settimeout(timeout)
    local_id = 1
    sock.sendall(_msg(b"OPEN", local_id, 0, f"exec:{command}\x00".encode()))
    cmd, arg0, arg1, data = _read(sock)
    if cmd != _cmd_id(b"OKAY"):
        # fallback to shell:
        sock.sendall(_msg(b"OPEN", local_id, 0, f"shell:{command}\x00".encode()))
        cmd, arg0, arg1, data = _read(sock)
        if cmd != _cmd_id(b"OKAY"):
            raise RuntimeError(f"OPEN failed cmd={cmd:#x} data={data!r}")
    remote_id = arg0
    out = b""
    while True:
        cmd, arg0, arg1, data = _read(sock)
        if cmd == _cmd_id(b"WRTE"):
            out += data
            sock.sendall(_msg(b"OKAY", local_id, remote_id))
        elif cmd == _cmd_id(b"CLSE"):
            sock.sendall(_msg(b"CLSE", local_id, remote_id))
            break
        elif cmd == _cmd_id(b"OKAY"):
            continue
        else:
            raise RuntimeError(f"unexpected cmd={cmd:#x}")
    return out


def reverse_forward(sock: socket.socket, device_local: str, host_remote: str) -> None:
    """adb reverse: make device_local forward to host_remote."""
    service = ("reverse:forward;%s;%s" % (device_local, host_remote)).encode() + b"\x00"
    local_id = 1
    sock.sendall(_msg(b"OPEN", local_id, 0, service))
    cmd, arg0, arg1, data = _read(sock)
    if cmd == _cmd_id(b"OKAY"):
        # drain optional follow-up
        try:
            sock.settimeout(0.5)
            cmd2, _, rid, _ = _read(sock)
            sock.settimeout(20.0)
            if cmd2 == _cmd_id(b"CLSE"):
                sock.sendall(_msg(b"CLSE", local_id, rid))
        except Exception:
            try:
                sock.settimeout(20.0)
            except Exception:
                pass
        return
    if cmd == _cmd_id(b"CLSE"):
        raise RuntimeError("reverse failed: %s" % (data.decode("utf-8", "replace") if data else "closed"))
    raise RuntimeError("reverse unexpected cmd=%#x data=%r" % (cmd, data))


def sync_pull(sock: socket.socket, remote_path: str, local_path: str) -> None:
    local_id = 1
    sock.sendall(_msg(b"OPEN", local_id, 0, b"sync:\x00"))
    cmd, arg0, arg1, data = _read(sock)
    if cmd != _cmd_id(b"OKAY"):
        raise RuntimeError("sync open failed")
    remote_id = arg1

    def wait_okay():
        c, _, _, _ = _read(sock)
        if c != _cmd_id(b"OKAY"):
            raise RuntimeError("expected OKAY")

    def wrte(payload: bytes):
        sock.sendall(_msg(b"WRTE", local_id, remote_id, payload))
        wait_okay()

    wrte(b"RECV" + struct.pack("<I", len(remote_path.encode())) + remote_path.encode())
    chunks = []
    pending = b""
    while True:
        c, a0, a1, d = _read(sock)
        if c == _cmd_id(b"WRTE"):
            sock.sendall(_msg(b"OKAY", local_id, remote_id))
            pending += d
            while True:
                if len(pending) < 8:
                    break
                ident = pending[:4]
                (n,) = struct.unpack("<I", pending[4:8])
                if len(pending) < 8 + n:
                    break
                body = pending[8:8 + n]
                pending = pending[8 + n:]
                if ident == b"DATA":
                    chunks.append(body)
                elif ident == b"DONE":
                    Path(local_path).write_bytes(b"".join(chunks))
                    wrte(b"QUIT" + struct.pack("<I", 0))
                    sock.sendall(_msg(b"CLSE", local_id, remote_id))
                    return
                elif ident == b"FAIL":
                    raise RuntimeError(body.decode("utf-8", "replace"))
                else:
                    raise RuntimeError("bad sync ident %r" % ident)
        elif c == _cmd_id(b"CLSE"):
            raise RuntimeError("sync closed early")
        else:
            raise RuntimeError("unexpected %#x" % c)



def main(argv=None) -> int:
    port, args = parse_argv(list(argv or sys.argv[1:]))
    # recover serial for emu
    serial = "emulator-5554"
    raw_argv = list(argv or sys.argv[1:])
    for i, a in enumerate(raw_argv):
        if a in ("-s", "--serial") and i + 1 < len(raw_argv):
            serial = raw_argv[i + 1]
            break

    if not args:
        print("Android Debug Bridge version 1.0.41 (mini_adb_auth)", file=sys.stderr)
        return 0

    cmd = args[0]
    rest = args[1:]

    if cmd == "version":
        print("Android Debug Bridge version 1.0.41 (mini_adb_auth)")
        print("Version 35.0.2-mini")
        return 0
    if cmd in ("start-server", "kill-server"):
        return 0
    if cmd == "emu":
        print(emu_console(serial, " ".join(rest)))
        return 0
    if cmd == "devices":
        try:
            s = connect(port=port)
            print("List of devices attached")
            print("emulator-5554\tdevice")
            s.close()
        except Exception as e:
            print("List of devices attached")
            print("emulator-5554\toffline")
            print(f"# {e}", file=sys.stderr)
            return 1
        return 0
    if cmd == "wait-for-device":
        for _ in range(180):
            try:
                s = connect(port=port)
                s.close()
                return 0
            except Exception:
                time.sleep(2)
        return 1

    try:
        s = connect(port=port)
    except Exception as e:
        print(f"error: {e}", file=sys.stderr)
        return 1

    try:
        if cmd == "shell":
            sys.stdout.write(shell(s, " ".join(rest) if rest else "sh"))
            return 0
        if cmd == "exec-out":
            data = shell_raw(s, " ".join(rest))
            sys.stdout.buffer.write(data)
            return 0
        if cmd == "push":
            sync_push(s, rest[0], rest[1])
            print(f"{rest[0]}: 1 file pushed")
            return 0
        if cmd == "install":
            apk = [a for a in rest if not a.startswith("-")][-1]
            remote = "/data/local/tmp/_mini_install.apk"
            sync_push(s, apk, remote, 0o644)
            s.close()
            s = connect(port=port)
            out = shell(s, f"pm install -r -t --user 0 {remote}", timeout=300)
            sys.stdout.write(out)
            return 0 if "Success" in out else 1
        if cmd == "uninstall":
            pkg = [a for a in rest if not a.startswith("-")][-1]
            out = shell(s, f"pm uninstall {pkg}")
            sys.stdout.write(out)
            return 0 if "Success" in out else 1
        if cmd == "reverse":
            # adb reverse [--remove-all] | reverse tcp:X tcp:Y
            if rest and rest[0] == "--remove-all":
                # best-effort: open reverse:killforward-all
                try:
                    local_id = 1
                    s.sendall(_msg(b"OPEN", local_id, 0, b"reverse:killforward-all\x00"))
                    _read(s)
                except Exception as ex:
                    print("reverse --remove-all: %s" % ex, file=sys.stderr)
                return 0
            if rest and rest[0] == "--list":
                out = shell(s, "echo reverse-list-unsupported")
                sys.stdout.write(out)
                return 0
            # expect: tcp:19099 tcp:19099
            if len(rest) < 2:
                print("usage: reverse tcp:LOCAL tcp:REMOTE", file=sys.stderr)
                return 1
            reverse_forward(s, rest[0], rest[1])
            print("%s" % " ".join(rest))
            return 0
        if cmd == "forward":
            print("mini_adb_auth: forward not implemented", file=sys.stderr)
            return 0
        if cmd == "pull":
            sync_pull(s, rest[0], rest[1])
            print("%s: 1 file pulled" % rest[0])
            return 0
        sys.stdout.write(shell(s, " ".join(args)))
        return 0
    finally:
        try:
            s.close()
        except Exception:
            pass


if __name__ == "__main__":
    raise SystemExit(main())
