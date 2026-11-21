#!/usr/bin/env python3
"""Per-AVD WebSocket proxy: rewrite agent register deviceId so clones look unique on Hub.

Guest still dials ws://124.71.69.102:8735/agent/ws; iptables DNATs to this proxy
on the host (10.0.2.2:LISTEN_PORT). Proxy forwards to the real Hub reverse tunnel
(127.0.0.1:8735) after rewriting register JSON.

Only the register (and early text frames that still carry the original deviceId)
are rewritten. After that, bytes are spliced through unchanged so permessage-deflate
/ RSV / fragmentation stay intact (re-framing those caused Ignore non-JSON WS frame).
"""
from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import struct
import sys
from typing import Optional

LOG = logging.getLogger("emu_ws_deviceid_proxy")


def _rewrite_json(raw: str, new_id: str, avd: str, orig_holder: dict) -> tuple[str, bool]:
    try:
        msg = json.loads(raw)
    except Exception:
        return raw, False
    if not isinstance(msg, dict):
        return raw, False
    changed = False
    is_register = msg.get("type") == "register"
    if is_register:
        old = (msg.get("deviceId") or "").strip()
        if old:
            orig_holder["orig"] = old
        msg["deviceId"] = new_id
        meta = msg.get("meta") if isinstance(msg.get("meta"), dict) else {}
        meta = dict(meta)
        meta["avd"] = avd
        if old:
            meta["originalDeviceId"] = old
        msg["meta"] = meta
        changed = True
        orig_holder["registered"] = True
    else:
        old = orig_holder.get("orig")
        if old and msg.get("deviceId") == old:
            msg["deviceId"] = new_id
            changed = True
    if not changed:
        return raw, False
    return json.dumps(msg, ensure_ascii=False, separators=(",", ":")), True


def _parse_ws_frames(buf: bytearray) -> tuple[list[tuple[int, bytes, bytes]], bytearray]:
    """Return complete frames (opcode, payload, raw_frame_bytes) and remaining buffer."""
    out: list[tuple[int, bytes, bytes]] = []
    while True:
        if len(buf) < 2:
            break
        start = 0
        b0, b1 = buf[0], buf[1]
        opcode = b0 & 0x0F
        masked = (b1 & 0x80) != 0
        ln = b1 & 0x7F
        idx = 2
        if ln == 126:
            if len(buf) < 4:
                break
            ln = struct.unpack("!H", buf[2:4])[0]
            idx = 4
        elif ln == 127:
            if len(buf) < 10:
                break
            ln = struct.unpack("!Q", buf[2:10])[0]
            idx = 10
        if masked:
            if len(buf) < idx + 4:
                break
            mask = bytes(buf[idx : idx + 4])
            idx += 4
        else:
            mask = b""
        if len(buf) < idx + ln:
            break
        raw_frame = bytes(buf[start : idx + ln])
        payload = bytearray(buf[idx : idx + ln])
        if masked:
            for i in range(len(payload)):
                payload[i] ^= mask[i % 4]
        out.append((opcode, bytes(payload), raw_frame))
        del buf[: idx + ln]
    return out, buf


def _build_ws_frame(opcode: int, payload: bytes, *, mask: bool) -> bytes:
    fin_op = 0x80 | (opcode & 0x0F)
    ln = len(payload)
    if ln < 126:
        hdr = bytes([fin_op, (0x80 if mask else 0) | ln])
    elif ln < 65536:
        hdr = bytes([fin_op, (0x80 if mask else 0) | 126]) + struct.pack("!H", ln)
    else:
        hdr = bytes([fin_op, (0x80 if mask else 0) | 127]) + struct.pack("!Q", ln)
    if not mask:
        return hdr + payload
    m = os.urandom(4)
    data = bytearray(payload)
    for i in range(len(data)):
        data[i] ^= m[i % 4]
    return hdr + m + bytes(data)


async def _read_http_headers(reader: asyncio.StreamReader) -> tuple[bytes, bytes]:
    data = bytearray()
    while b"\r\n\r\n" not in data:
        chunk = await reader.read(4096)
        if not chunk:
            break
        data.extend(chunk)
        if len(data) > 65536:
            break
    marker = data.find(b"\r\n\r\n")
    if marker < 0:
        return bytes(data), b""
    end = marker + 4
    return bytes(data[:end]), bytes(data[end:])


async def _raw_pipe(
    reader: asyncio.StreamReader,
    writer: asyncio.StreamWriter,
    initial: bytes = b"",
) -> None:
    try:
        if initial:
            writer.write(initial)
            await writer.drain()
        while True:
            chunk = await reader.read(65536)
            if not chunk:
                break
            writer.write(chunk)
            await writer.drain()
    except Exception as exc:
        LOG.debug("raw pipe end: %s", exc)
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def _pipe_client_rewrite_then_raw(
    reader: asyncio.StreamReader,
    writer: asyncio.StreamWriter,
    new_id: str,
    avd: str,
    orig_holder: dict,
    initial: bytes = b"",
) -> None:
    """Rewrite early text frames; after register, splice remaining bytes raw."""
    buf = bytearray(initial)
    try:
        while not orig_holder.get("registered"):
            frames, buf = _parse_ws_frames(buf)
            for opcode, payload, raw_frame in frames:
                if opcode == 0x1:
                    text = payload.decode("utf-8", errors="replace")
                    text2, changed = _rewrite_json(text, new_id, avd, orig_holder)
                    if changed:
                        writer.write(
                            _build_ws_frame(opcode, text2.encode("utf-8"), mask=True)
                        )
                        LOG.info(
                            "rewrote %s -> %s avd=%s",
                            orig_holder.get("orig"),
                            new_id,
                            avd,
                        )
                    else:
                        # keep original wire bytes (masking intact)
                        writer.write(raw_frame)
                else:
                    writer.write(raw_frame)
            if frames:
                await writer.drain()
            if orig_holder.get("registered"):
                break
            chunk = await reader.read(65536)
            if not chunk:
                # flush remaining
                frames, buf = _parse_ws_frames(buf)
                for opcode, payload, raw_frame in frames:
                    if opcode == 0x1:
                        text = payload.decode("utf-8", errors="replace")
                        text2, changed = _rewrite_json(text, new_id, avd, orig_holder)
                        if changed:
                            writer.write(
                                _build_ws_frame(
                                    opcode, text2.encode("utf-8"), mask=True
                                )
                            )
                        else:
                            writer.write(raw_frame)
                    else:
                        writer.write(raw_frame)
                if frames:
                    await writer.drain()
                if buf:
                    writer.write(bytes(buf))
                    await writer.drain()
                return
            buf.extend(chunk)
        # registered: flush any leftover unparsed bytes, then raw splice
        if buf:
            writer.write(bytes(buf))
            await writer.drain()
            buf.clear()
        while True:
            chunk = await reader.read(65536)
            if not chunk:
                break
            writer.write(chunk)
            await writer.drain()
    except Exception as exc:
        LOG.debug("c2u end: %s", exc)
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def _handle(
    client_r: asyncio.StreamReader,
    client_w: asyncio.StreamWriter,
    upstream_host: str,
    upstream_port: int,
    new_id: str,
    avd: str,
) -> None:
    peer = client_w.get_extra_info("peername")
    LOG.info("conn from %s avd=%s -> %s", peer, avd, new_id)
    up_w: Optional[asyncio.StreamWriter] = None
    try:
        req, req_rest = await _read_http_headers(client_r)
        if not req:
            return
        up_r, up_w = await asyncio.open_connection(upstream_host, upstream_port)
        up_w.write(req)
        await up_w.drain()
        resp, resp_rest = await _read_http_headers(up_r)
        client_w.write(resp)
        await client_w.drain()
        if b"101" not in resp.split(b"\r\n", 1)[0]:
            LOG.warning("upstream non-101: %s", resp.split(b"\r\n", 1)[0][:120])
            return
        orig_holder: dict = {}
        t1 = asyncio.create_task(
            _pipe_client_rewrite_then_raw(
                client_r, up_w, new_id, avd, orig_holder, initial=req_rest
            )
        )
        t2 = asyncio.create_task(_raw_pipe(up_r, client_w, initial=resp_rest))
        done, pending = await asyncio.wait(
            {t1, t2}, return_when=asyncio.FIRST_COMPLETED
        )
        for t in pending:
            t.cancel()
        await asyncio.gather(*pending, return_exceptions=True)
        for t in done:
            exc = t.exception()
            if exc:
                LOG.debug("pipe done with err: %s", exc)
    except Exception as exc:
        LOG.warning("handle error: %s", exc)
    finally:
        for w in (up_w, client_w):
            if w is None:
                continue
            try:
                w.close()
            except Exception:
                pass


async def main_async(args: argparse.Namespace) -> None:
    host_u, port_u = args.upstream.split(":", 1)
    port_u_i = int(port_u)

    async def _on(r, w):
        await _handle(r, w, host_u, port_u_i, args.device_id, args.avd)

    server = await asyncio.start_server(_on, args.bind, args.listen)
    socks = ", ".join(str(s.getsockname()) for s in server.sockets or [])
    LOG.info(
        "listening %s rewrite->%s upstream=%s avd=%s",
        socks,
        args.device_id,
        args.upstream,
        args.avd,
    )
    async with server:
        await server.serve_forever()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--bind", default="127.0.0.1")
    ap.add_argument("--listen", type=int, required=True)
    ap.add_argument("--upstream", default="127.0.0.1:8735")
    ap.add_argument("--device-id", required=True)
    ap.add_argument("--avd", required=True)
    args = ap.parse_args()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )
    try:
        asyncio.run(main_async(args))
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
