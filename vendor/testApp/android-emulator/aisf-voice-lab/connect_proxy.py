#!/usr/bin/env python3
"""HTTP CONNECT + plain HTTP forward proxy for emulator public access."""
import select
import socket
import threading
from urllib.parse import urlsplit

HOST, PORT = "127.0.0.1", 8888


def relay(a, b):
    sockets = [a, b]
    while True:
        # Long idle is normal for WSS; only exit on error or EOF (empty recv).
        r, _, e = select.select(sockets, [], sockets, 600)
        if e:
            break
        if not r:
            continue
        for s in r:
            other = b if s is a else a
            try:
                buf = s.recv(65536)
            except Exception:
                buf = b""
            if not buf:
                try:
                    a.close()
                    b.close()
                except Exception:
                    pass
                return
            try:
                other.sendall(buf)
            except Exception:
                try:
                    a.close()
                    b.close()
                except Exception:
                    pass
                return


def handle_connect(client, target, rest):
    host, _, port = target.partition(":")
    port = int(port or 443)
    remote = socket.create_connection((host, port), timeout=20)
    client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
    # discard any body already read after headers (usually none for CONNECT)
    relay(client, remote)


def handle_http(client, method, url, version, header_blob, body):
    # Absolute form: http://host/path  or https shouldn't appear here
    parts = urlsplit(url)
    if parts.scheme and parts.scheme.lower() == "http" and parts.hostname:
        host = parts.hostname
        port = parts.port or 80
        path = parts.path or "/"
        if parts.query:
            path += "?" + parts.query
    else:
        # origin-form: need Host header
        host = None
        port = 80
        path = url
        for line in header_blob.split(b"\r\n")[1:]:
            if not line:
                break
            if b":" not in line:
                continue
            k, v = line.split(b":", 1)
            if k.strip().lower() == b"host":
                hv = v.strip().decode("latin1", "ignore")
                host, _, p = hv.partition(":")
                if p:
                    port = int(p)
                break
        if not host:
            client.sendall(b"HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n")
            client.close()
            return

    remote = socket.create_connection((host, port), timeout=20)
    # Rebuild request as origin-form for origin server
    req = f"{method} {path} {version}\r\n".encode("latin1", "ignore")
    # filter hop-by-hop Proxy-* headers; keep Host
    lines = header_blob.split(b"\r\n")
    out_headers = []
    has_host = False
    for line in lines[1:]:
        if not line:
            break
        low = line.lower()
        if low.startswith(b"proxy-connection:"):
            continue
        if low.startswith(b"proxy-authorization:"):
            continue
        if low.startswith(b"host:"):
            has_host = True
        out_headers.append(line)
    if not has_host:
        out_headers.insert(0, f"Host: {host}:{port}".encode() if port != 80 else f"Host: {host}".encode())
    out_headers.append(b"Connection: close")
    remote.sendall(req + b"\r\n".join(out_headers) + b"\r\n\r\n" + body)
    relay(client, remote)


def handle(client):
    try:
        data = b""
        client.settimeout(30)
        while b"\r\n\r\n" not in data:
            chunk = client.recv(4096)
            if not chunk:
                client.close()
                return
            data += chunk
            if len(data) > 1024 * 1024:
                break
        head, _, body = data.partition(b"\r\n\r\n")
        first = head.split(b"\r\n", 1)[0].decode("latin1", "ignore")
        parts = first.split()
        if len(parts) < 3:
            client.sendall(b"HTTP/1.1 400 Bad Request\r\n\r\n")
            client.close()
            return
        method, target, version = parts[0].upper(), parts[1], parts[2]
        if method == "CONNECT":
            handle_connect(client, target, body)
            return
        # Plain HTTP proxy (GET/POST/HEAD/...) — needed for APK download links on http://
        handle_http(client, method, target, version, head, body)
    except Exception:
        try:
            client.close()
        except Exception:
            pass


def main():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((HOST, PORT))
    srv.listen(100)
    open("/tmp/connect_proxy.ok", "w").write("ok\n")
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()


if __name__ == "__main__":
    main()
