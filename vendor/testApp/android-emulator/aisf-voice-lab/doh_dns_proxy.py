#!/usr/bin/env python3
"""Minimal DNS stub: resolve A/AAAA via DoH. Prefer IP DoH to avoid recursive DNS."""
import json
import socket
import struct
import threading
import time
import urllib.request
import ssl

PROXY = "http://127.0.0.1:8888"
# Avoid resolving dns.alidns.com (host resolv points at this stub).
DOH_IP = "223.5.5.5"
DOH = f"https://{DOH_IP}/resolve"
LISTEN = ("127.0.0.1", 53)
CACHE = {}
CACHE_TTL = 120

STATIC_A = {
    "dds.dui.ai": ["120.55.26.227", "47.111.175.204"],
    "auth.dui.ai": ["47.111.175.204"],
    "asr.dui.ai": ["120.55.26.227", "47.111.175.204"],
    "tts.dui.ai": ["120.55.26.227", "47.111.175.204"],
    "push.duiopen.com": ["47.114.114.166", "47.114.96.117"],
    "log.aispeech.com": ["60.204.143.165"],
    "dns.alidns.com": ["223.5.5.5", "223.6.6.6"],
}

STATIC_A.update({
  "www.iqiyi.com": [
    "123.129.218.119",
    "123.129.218.122",
    "123.129.246.26"
  ],
  "api.iqiyi.com": [
    "111.202.15.6"
  ],
  "iface2.iqiyi.com": [
    "111.202.15.11"
  ],
  "login-mq.iqiyi.com": [
    "110.242.75.14",
    "220.202.19.96",
    "220.202.19.97"
  ],
  "doh.iqiyi.com": [
    "110.242.72.21",
    "111.202.15.20"
  ],
  "hotchat-im.iqiyi.com": [
    "110.242.72.92"
  ],
  "cache.video.iqiyi.com": [
    "111.202.15.38"
  ],
  "data.video.iqiyi.com": [
    "110.242.72.108"
  ],
  "mesh.if.iqiyi.com": [
    "111.202.15.11"
  ],
  "qy.qchannel03.cn": [
    "120.53.128.248"
  ],
  "qmsg.qy.net": [
    "110.242.72.89",
    "123.126.131.2"
  ],
  "dns.alidns.com": [
    "223.5.5.5",
    "223.6.6.6"
  ],

  "m.iqiyipic.com": ["122.191.171.115", "124.95.157.81", "124.95.157.83"],
  "pic0.iqiyipic.com": ["123.129.246.22", "123.129.246.23", "123.129.246.28"],
  "pic1.iqiyipic.com": ["123.129.218.118", "123.129.218.119", "123.129.246.23"],
  "pic2.iqiyipic.com": ["123.129.218.119", "123.129.218.122", "123.129.246.26"],
  "pic3.iqiyipic.com": ["123.129.218.119", "123.129.246.22", "123.129.246.26"],
  "pic4.iqiyipic.com": ["123.129.218.119", "123.129.246.26", "123.129.246.28"],
  "pic5.iqiyipic.com": ["123.129.218.119", "123.129.218.120", "123.129.218.122"],
  "pic6.iqiyipic.com": ["123.129.218.118", "123.129.218.120", "123.129.218.122"],
  "pic7.iqiyipic.com": ["123.129.218.119", "123.129.218.122", "123.129.246.23"],
  "pic8.iqiyipic.com": ["123.129.218.120", "123.129.246.22", "123.129.246.23"],
  "pic9.iqiyipic.com": ["123.129.218.122", "123.129.246.23", "123.129.246.26"],
  "static-s.iqiyi.com": ["221.6.171.224"],
  "static-d.iqiyi.com": ["221.6.171.224"],
  "static.iqiyi.com": ["123.129.218.120", "123.129.218.122", "123.129.246.26"],
  "cdndata.video.iqiyi.com": ["110.242.72.108"],
  "meta.video.iqiyi.com": ["123.129.218.120", "123.129.218.122", "123.129.246.26"],
  "msg.qy.net": ["110.242.72.89", "123.126.131.2"],
  "cmts.iqiyi.com": ["221.6.171.224"],
  "passport.iqiyi.com": ["111.202.15.40"],
  "security.iqiyi.com": ["123.129.246.22", "123.129.246.23", "123.129.246.28"],

  "tpa-data.iqiyi.com": ["111.202.15.11"],
  "cards.iqiyi.com": ["111.202.15.11"],
  "nl-rcd.iqiyi.com": ["111.202.15.40"],
  "l-rcd.iqiyi.com": ["111.202.15.40"],
  "cm.passport.iqiyi.com": ["111.202.15.40"],
  "pcw-api.iqiyi.com": ["111.202.15.11"],
  "passport.iqiyi.com": ["111.202.15.40"],
})


STATIC_A.update({
  "doh.iqiyi.com": ["110.242.72.21", "111.202.15.20"],
  "cmsg.qy.net": ["110.242.72.89", "123.126.131.2"],
  "qmsg.qy.net": ["110.242.72.89", "123.126.131.2"],
  "absod.video.iqiyi.com": ["111.202.15.34"],
  "cdn.data.video.iqiyi.com": ["110.242.72.108"],
  "qiniucdnct.inter.iqiyi.com": ["180.105.72.24", "180.105.72.25", "180.105.72.26"],
  "tpa-data.iqiyi.com": ["111.202.15.11"],
  "cards.iqiyi.com": ["111.202.15.11"],
  "iface2.iqiyi.com": ["111.202.15.11"],
  "www.baidu.com": ["110.242.69.21", "110.242.70.57"],
  "dns.alidns.com": ["223.5.5.5", "223.6.6.6"],
  "nl-rcd.iqiyi.com": ["111.202.15.40"],
  "l-rcd.iqiyi.com": ["111.202.15.40"],
  "pcw-api.iqiyi.com": ["111.202.15.11"],
  "mesh.if.iqiyi.com": ["111.202.15.11"],
  "data.video.iqiyi.com": ["110.242.72.108"],
  "cache.video.iqiyi.com": ["111.202.15.38"],
  "meta.video.iqiyi.com": ["123.129.218.122", "123.129.246.22", "123.129.246.26"],
  "msg.qy.net": ["110.242.72.89", "123.126.131.2"],
  "static-s.iqiyi.com": ["221.204.61.41"],
  "static-d.iqiyi.com": ["221.204.61.41"],
})

def doh_query(name, typ="A"):
    key = (name.lower().rstrip("."), typ)
    now = time.time()
    hit = CACHE.get(key)
    if hit and hit[0] > now:
        return hit[1]
    if typ == "A":
        static = STATIC_A.get(key[0])
        if static:
            CACHE[key] = (now + CACHE_TTL, static)
            return static
    url = f"{DOH}?name={name}&type={typ}"
    req = urllib.request.Request(
        url,
        headers={
            "Accept": "application/dns-json",
            "Host": "dns.alidns.com",
        },
    )
    ctx = ssl.create_default_context()
    # SNI for alidns while connecting to IP
    last_err = None
    for handlers in (
        [urllib.request.ProxyHandler({"http": PROXY, "https": PROXY})],
        [],
    ):
        try:
            opener = urllib.request.build_opener(*handlers)
            with opener.open(req, timeout=4, context=ctx) as resp:
                data = json.loads(resp.read().decode())
            answers = []
            want = 1 if typ == "A" else 28
            for a in data.get("Answer") or []:
                if a.get("type") == want and a.get("data"):
                    answers.append(a["data"])
            # CNAME-only answers: follow one level via DoH name in data? skip; cache empty briefly
            CACHE[key] = (now + CACHE_TTL, answers)
            return answers
        except Exception as exc:
            last_err = exc
            continue
    if last_err:
        raise last_err
    return []


def parse_qname(data, offset):
    labels = []
    while True:
        if offset >= len(data):
            raise ValueError("bad qname")
        length = data[offset]
        if length == 0:
            return (".".join(labels) if labels else "."), offset + 1
        if length & 0xC0 == 0xC0:
            ptr = struct.unpack_from("!H", data, offset)[0] & 0x3FFF
            name, _ = parse_qname(data, ptr)
            return name, offset + 2
        offset += 1
        labels.append(data[offset : offset + length].decode("ascii", "ignore"))
        offset += length


def build_name(name):
    out = b""
    for lab in name.rstrip(".").split("."):
        if not lab:
            continue
        b = lab.encode("ascii")
        out += bytes([len(b)]) + b
    return out + b"\x00"


def handle_query(data, addr, sock):
    try:
        if len(data) < 12:
            return
        tid = data[:2]
        flags = b"\x81\x80"
        qname, qend = parse_qname(data, 12)
        qtype, qclass = struct.unpack_from("!HH", data, qend)
        question = data[12 : qend + 4]
        answers = b""
        ancount = 0
        if qclass == 1 and qtype in (1, 28):
            typ = "A" if qtype == 1 else "AAAA"
            try:
                rr = doh_query(qname, typ)
            except Exception:
                rr = []
                flags = b"\x81\x82"
            for ip in rr:
                try:
                    answers += build_name(qname)
                    answers += struct.pack("!HHI", qtype, 1, 60)
                    if qtype == 1:
                        raw = socket.inet_aton(ip)
                    else:
                        raw = socket.inet_pton(socket.AF_INET6, ip)
                    answers += struct.pack("!H", len(raw)) + raw
                    ancount += 1
                except Exception:
                    continue
        header = tid + flags + struct.pack("!HHHH", 1, ancount, 0, 0)
        sock.sendto(header + question + answers, addr)
    except Exception:
        pass


def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(LISTEN)
    open("/tmp/doh_dns.ok", "w").write("ok\n")
    print("doh_dns_proxy listening", LISTEN, flush=True)
    while True:
        data, addr = sock.recvfrom(4096)
        threading.Thread(target=handle_query, args=(data, addr, sock), daemon=True).start()


if __name__ == "__main__":
    main()
