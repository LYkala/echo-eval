#!/usr/bin/env python3
"""Reverse-tunnel helper: expose jump-side TCP peers on the ARM target.

Bind 0.0.0.0 (not only 127.0.0.1) so the qemu guest can reach Hub via
10.0.2.2:8731/8735 after guest DNAT. Requires GatewayPorts clientspecified
on the target sshd.

Also used together with emu iptables DNAT so isolated emulator can reach
dui.ai auth/ASR endpoints via the jump host's internet.
"""
from __future__ import annotations

import os
import socket
import struct
import sys
import threading
import time

import paramiko

TARGET_HOST = os.environ.get("EMU_TARGET_HOST", "121.37.53.201")
TARGET_PORT = int(os.environ.get("EMU_TARGET_PORT", "40204"))
TARGET_USER = os.environ.get("EMU_TARGET_USER", "root")
TARGET_PASS = os.environ.get("EMU_TARGET_PASS", "")

# remote_port on target -> (dest_host, dest_port) reached FROM JUMP
# Guest can reach 8731/8735/8736/8888. Use 8736 as raw TCP to dds/asr/tts
# (long-lived WSS). Auth/push stay on CONNECT via :8888 from the guest gw.
FORWARDS = [
    (8735, "127.0.0.1", 8735),  # VoxTest agent WS
    (8731, "127.0.0.1", 8731),  # VoxTest HTTP
    (8736, "120.55.26.227", 443),  # dds/asr/tts.dui.ai raw TLS
]

# Simple static DNS answered on target UDP 5353 (and optionally 53)
DNS_RECORDS = {
    b"auth.dui.ai": "47.111.175.204",
    b"asr.dui.ai": "120.55.26.227",
    b"tts.dui.ai": "120.55.26.227",
    b"push.duiopen.com": "47.114.114.166",
}


def _close_quiet(*objs) -> None:
    for s in objs:
        try:
            s.close()
        except Exception:
            pass


def _unidirectional_pipe(src, dst) -> None:
    """Copy bytes src→dst. Works for socket and paramiko.Channel (do NOT select on Channel)."""
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try:
            # Half-close when possible so peer sees EOF.
            if hasattr(dst, "shutdown"):
                dst.shutdown(socket.SHUT_WR)
        except Exception:
            pass
        try:
            if hasattr(src, "shutdown"):
                src.shutdown(socket.SHUT_RD)
        except Exception:
            pass


def _handle_channel(chan: paramiko.Channel, dest_host: str, dest_port: int) -> None:
    try:
        sock = socket.create_connection((dest_host, dest_port), timeout=15)
    except Exception as ex:
        print(f"connect {dest_host}:{dest_port} failed: {ex}", flush=True)
        _close_quiet(chan)
        return
    # One direction per thread — never run two bidirectional select loops on the
    # same sockets (races), and never select() on a paramiko Channel (shared fd).
    try:
        chan.settimeout(None)
    except Exception:
        pass
    try:
        sock.settimeout(None)
    except Exception:
        pass
    t1 = threading.Thread(target=_unidirectional_pipe, args=(chan, sock), daemon=True)
    t2 = threading.Thread(target=_unidirectional_pipe, args=(sock, chan), daemon=True)
    t1.start()
    t2.start()
    t1.join()
    t2.join()
    _close_quiet(chan, sock)


def _make_forward_handler(port_map: dict[int, tuple[str, int]]):
    """Demux reverse-forward channels by listen port (paramiko otherwise races)."""

    def handler(chan: paramiko.Channel, origin_addr, server_addr) -> None:
        try:
            listen_port = int(server_addr[1])
        except Exception:
            listen_port = -1
        dest = port_map.get(listen_port)
        if not dest:
            print(f"no dest for forward port {listen_port} origin={origin_addr}", flush=True)
            try:
                chan.close()
            except Exception:
                pass
            return
        print(f"accept :{listen_port} from {origin_addr} -> {dest[0]}:{dest[1]}", flush=True)
        threading.Thread(
            target=_handle_channel, args=(chan, dest[0], dest[1]), daemon=True
        ).start()

    return handler


def _accept_loop_fallback(transport: paramiko.Transport, port_map: dict[int, tuple[str, int]]) -> None:
    """Fallback when request_port_forward handler is unavailable."""
    while transport.is_active():
        try:
            chan = transport.accept(timeout=1.0)
        except Exception:
            continue
        if chan is None:
            continue
        # Best-effort: without server_addr we cannot demux; prefer 8736 CONNECT proxy.
        dest = port_map.get(8736) or next(iter(port_map.values()), None)
        if not dest:
            try:
                chan.close()
            except Exception:
                pass
            continue
        threading.Thread(
            target=_handle_channel, args=(chan, dest[0], dest[1]), daemon=True
        ).start()


def _dns_name(data: bytes, offset: int):
    labels = []
    jumped = False
    orig = offset
    while True:
        if offset >= len(data):
            raise ValueError("bad name")
        l = data[offset]
        if l == 0:
            offset += 1
            break
        if (l & 0xC0) == 0xC0:
            if offset + 1 >= len(data):
                raise ValueError("bad ptr")
            ptr = ((l & 0x3F) << 8) | data[offset + 1]
            if not jumped:
                orig = offset + 2
            offset = ptr
            jumped = True
            continue
        offset += 1
        labels.append(data[offset : offset + l].decode("ascii", "ignore"))
        offset += l
    return ".".join(labels).encode("ascii"), (orig if jumped else offset)


def _build_dns_response(req: bytes) -> bytes | None:
    if len(req) < 12:
        return None
    tid = req[:2]
    # parse first question
    try:
        name, qend = _dns_name(req, 12)
        qtype = struct.unpack("!H", req[qend : qend + 2])[0]
        qclass = struct.unpack("!H", req[qend + 2 : qend + 4])[0]
    except Exception:
        return None
    ip = DNS_RECORDS.get(name.lower())
    # flags: response, recursion available
    flags = struct.pack("!H", 0x8180)
    counts = struct.pack("!HHHH", 1, 1 if ip and qtype == 1 else 0, 0, 0)
    question = req[12:qend + 4]
    if not ip or qtype != 1:
        # NXDOMAIN-ish empty answer
        return tid + struct.pack("!H", 0x8183) + struct.pack("!HHHH", 1, 0, 0, 0) + question
    # name pointer to question
    answer = (
        struct.pack("!H", 0xC00C)
        + struct.pack("!HHIH", 1, 1, 60, 4)
        + socket.inet_aton(ip)
    )
    return tid + flags + counts + question + answer


def _dns_server(bind_host: str = "127.0.0.1", bind_port: int = 5353) -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((bind_host, bind_port))
    print(f"dns static on {bind_host}:{bind_port}", flush=True)
    while True:
        try:
            data, addr = sock.recvfrom(2048)
            resp = _build_dns_response(data)
            if resp:
                sock.sendto(resp, addr)
        except Exception as ex:
            print(f"dns err: {ex}", flush=True)


def run_once() -> None:
    if not TARGET_PASS:
        raise SystemExit("set EMU_TARGET_PASS")
    # DNS helper runs on JUMP? No — must run on TARGET.
    # We start DNS via a remote command after SSH is up.
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"connect {TARGET_HOST}:{TARGET_PORT} ...", flush=True)
    client.connect(
        TARGET_HOST,
        port=TARGET_PORT,
        username=TARGET_USER,
        password=TARGET_PASS,
        timeout=60,
        allow_agent=False,
        look_for_keys=False,
    )
    transport = client.get_transport()
    assert transport is not None
    transport.set_keepalive(30)

    # Deploy & start static DNS on target
    dns_py = r'''
import socket,struct,threading
RECS={b"auth.dui.ai":"47.111.175.204",b"asr.dui.ai":"120.55.26.227",b"tts.dui.ai":"120.55.26.227",b"push.duiopen.com":"47.114.114.166"}
def parse_name(data,off):
    labels=[]; jumped=False; orig=off
    while True:
        l=data[off]
        if l==0: off+=1; break
        if (l&0xC0)==0xC0:
            ptr=((l&0x3F)<<8)|data[off+1]
            if not jumped: orig=off+2
            off=ptr; jumped=True; continue
        off+=1; labels.append(data[off:off+l].decode("ascii","ignore")); off+=l
    return ".".join(labels).encode("ascii"), (orig if jumped else off)
def build(req):
    if len(req)<12: return None
    tid=req[:2]
    try:
        name,qend=parse_name(req,12)
        qtype=struct.unpack("!H",req[qend:qend+2])[0]
    except Exception:
        return None
    ip=RECS.get(name.lower()); question=req[12:qend+4]
    if not ip or qtype!=1:
        return tid+struct.pack("!H",0x8183)+struct.pack("!HHHH",1,0,0,0)+question
    ans=struct.pack("!H",0xC00C)+struct.pack("!HHIH",1,1,60,4)+socket.inet_aton(ip)
    return tid+struct.pack("!H",0x8180)+struct.pack("!HHHH",1,1,0,0)+question+ans
def serve(port):
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); s.bind(("0.0.0.0",port))
    while True:
        data,addr=s.recvfrom(2048); r=build(data)
        if r: s.sendto(r,addr)
for p in (5353,53):
    threading.Thread(target=serve,args=(p,),daemon=True).start()
print("dns up", flush=True)
import time
while True: time.sleep(3600)
'''
    sftp = client.open_sftp()
    with sftp.file("/tmp/emu_static_dns.py", "w") as f:
        f.write(dns_py)
    sftp.close()
    client.exec_command("pkill -f emu_static_dns.py 2>/dev/null; nohup python3 /tmp/emu_static_dns.py >/tmp/emu_static_dns.log 2>&1 &")
    time.sleep(1)

    # Prefer 127.0.0.1: Android emulator 10.0.2.2 maps to host loopback.
    # IMPORTANT: use one handler that demuxes by listen port — multiple
    # transport.accept() loops race and mis-route channels (8736→8735 etc).
    port_map: dict[int, tuple[str, int]] = {}
    handler = _make_forward_handler(port_map)
    for remote_port, dest_host, dest_port in FORWARDS:
        for bind in ("0.0.0.0", "127.0.0.1", ""):
            try:
                transport.cancel_port_forward(bind or "127.0.0.1", remote_port)
            except Exception:
                pass
        port_map[remote_port] = (dest_host, dest_port)
        bound = None
        for bind in ("127.0.0.1", "0.0.0.0"):
            try:
                transport.request_port_forward(bind, remote_port, handler)
                bound = bind
                break
            except TypeError:
                # Older paramiko: no handler kw — fall back later
                try:
                    transport.request_port_forward(bind, remote_port)
                    bound = bind
                    break
                except Exception as ex:
                    print(f"forward {bind}:{remote_port} failed: {ex}", flush=True)
            except Exception as ex:
                print(f"forward {bind}:{remote_port} failed: {ex}", flush=True)
        if bound is None:
            print(f"forward {remote_port} failed (all binds)", flush=True)
            continue
        print(f"reverse {bound}:{remote_port} -> {dest_host}:{dest_port}", flush=True)

    # If handler kw unsupported, one demuxed accept loop (still imperfect).
    try:
        import inspect

        if "handler" not in inspect.signature(transport.request_port_forward).parameters:
            threading.Thread(
                target=_accept_loop_fallback, args=(transport, port_map), daemon=True
            ).start()
    except Exception:
        pass

    print("tunnel up", flush=True)
    while transport.is_active():
        time.sleep(2)
    print("transport dead", flush=True)
    client.close()


def main() -> int:
    while True:
        try:
            run_once()
        except Exception as ex:
            print(f"tunnel error: {ex}", flush=True)
        time.sleep(5)
    return 0


if __name__ == "__main__":
    sys.exit(main())
