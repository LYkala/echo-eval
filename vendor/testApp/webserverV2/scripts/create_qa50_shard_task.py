#!/usr/bin/env python3
"""Create a shard concurrent task for QA_Concurrent_50 on all online TestApp devices."""
import json
import sys
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:9527"
ACCOUNT = sys.argv[2] if len(sys.argv) > 2 else "lab"


def get(path):
    with urllib.request.urlopen(BASE + path, timeout=30) as r:
        return json.load(r)


def post(path, body):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


devs = get("/api/devices").get("devices") or []
online = [d["device_id"] for d in devs if not d.get("busy")]
print("online", online)
if len(online) < 2:
    raise SystemExit("need >=2 online TestApp devices")

sets = get("/api/testsets").get("testsets") or []
names = [t.get("name") if isinstance(t, dict) else t for t in sets]
print("testsets", names)
if "QA_Concurrent_50" not in names:
    raise SystemExit("QA_Concurrent_50 not found; restart webserver or scan testfiles")

body = {
    "account": ACCOUNT,
    "devices": online[:5],
    "testset": "QA_Concurrent_50",
    "source_type": "json",
    "target_package": "com.example.devicesdkdemo",
    "start_type": "immediate",
    "concurrency_mode": "shard",
}
print("create", body)
print(post("/api/tasks", body))
