from __future__ import annotations

"""Pull ARM deploy artifacts from jump (+ pulse libs from target) into offline-deploy/."""

import os
import sys
import tarfile
import time
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_from_windows import JUMP, TARGET, parse_creds

OUT = Path(__file__).resolve().parent / "offline-deploy"

JUMP_FILES = [
    ("/root/aisf-emu-build/emulator-linux_aarch64-built.tar.gz", OUT / "emulator" / "emulator-linux_aarch64-built.tar.gz"),
    ("/root/aisf-emu-build/emu-runtime-extra.tar.gz", OUT / "emulator" / "emu-runtime-extra.tar.gz"),
    ("/root/aisf-emu-build/glibc-bundle.tar.gz", OUT / "emulator" / "glibc-bundle.tar.gz"),
    ("/root/aisf-emu-build/linux-aarch64-deps.tar.gz", OUT / "emulator" / "linux-aarch64-deps.tar.gz"),
    ("/root/aisf-emu-build/vulkan-pack.tar.gz", OUT / "emulator" / "vulkan-pack.tar.gz"),
]


def sftp_get(sftp: paramiko.SFTPClient, remote: str, local: Path) -> None:
    local.parent.mkdir(parents=True, exist_ok=True)
    if local.exists() and local.stat().st_size > 0:
        try:
            rsize = sftp.stat(remote).st_size
        except OSError:
            print(f"skip missing {remote}", flush=True)
            return
        if local.stat().st_size == rsize:
            print(f"exists {local.name} ({rsize} bytes)", flush=True)
            return
        print(f"size mismatch, re-download {local.name}", flush=True)
    print(f"GET {remote} -> {local}", flush=True)
    t0 = time.time()

    last = {"pct": -10}

    def _cb(transferred, total):
        if not total:
            return
        pct = 100.0 * transferred / total
        if pct - last["pct"] >= 10 or transferred == total:
            last["pct"] = pct
            print(f"  {local.name}: {pct:.1f}% ({transferred}/{total})", flush=True)

    sftp.get(remote, str(local), callback=_cb)
    print(f"  done {local.name} in {time.time() - t0:.1f}s size={local.stat().st_size}", flush=True)


def main() -> int:
    ju, jp = parse_creds()[0]
    tu, tp = parse_creds()[1]
    j = paramiko.SSHClient()
    j.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    j.connect(JUMP[0], 22, username=ju, password=jp, timeout=90)
    sftp = j.open_sftp()

    for remote, local in JUMP_FILES:
        try:
            sftp_get(sftp, remote, local)
        except Exception as ex:
            print(f"FAIL {remote}: {ex}", flush=True)

    # Package pulse aarch64 libs on target, pull via jump temp
    print("=== package pulse libs on target ===", flush=True)
    transport = j.get_transport()
    assert transport is not None
    chan = transport.open_channel("direct-tcpip", (TARGET[0], TARGET[1]), ("127.0.0.1", 0))
    tgt = paramiko.SSHClient()
    tgt.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    tgt.connect(TARGET[0], TARGET[1], username=tu, password=tp, sock=chan, timeout=60)
    stdin, stdout, stderr = tgt.exec_command(
        r"""
set -e
TMP=/tmp/pulse-a64-libs
rm -rf $TMP && mkdir -p $TMP
cp -a /usr/local/lib64/libltdl.so* /usr/local/lib64/liborc-0.4.so* /usr/local/lib64/libspeexdsp.so* $TMP/ 2>/dev/null || true
# also pulse if present under /usr/local
ls /usr/local/lib64/libpulse* 2>/dev/null | head
cp -a /usr/local/lib64/libpulse*.so* $TMP/ 2>/dev/null || true
tar -C $TMP -czf /tmp/pulse-aarch64-libs.tar.gz .
ls -lah /tmp/pulse-aarch64-libs.tar.gz
""",
        timeout=120,
        get_pty=False,
    )
    print(stdout.read().decode("utf-8", errors="replace"))
    print(stderr.read().decode("utf-8", errors="replace"))

    # also grab mini_adb + adb wrapper + metrics backup if any
    stdin, stdout, stderr = tgt.exec_command(
        r"""
set -e
LAB=/root/aisf-voice-lab
tar -C $LAB -czf /tmp/aisf-runtime-scripts.tgz \
  mini_adb_auth.py \
  setup_virtual_mic.sh \
  linux_audio.py \
  2>/dev/null || tar -C $LAB -czf /tmp/aisf-runtime-scripts.tgz mini_adb_auth.py setup_virtual_mic.sh
# metrics pre_tls if exists
MET=$LAB/sdk/emulator/lib64/libandroid-emu-metrics.so
if [[ -f ${MET}.pre_tls_patch ]]; then
  cp -a ${MET}.pre_tls_patch /tmp/
  cp -a $MET /tmp/libandroid-emu-metrics.so.current
fi
# wrapper
cp -a $LAB/sdk/platform-tools/adb /tmp/adb.wrapper 2>/dev/null || true
ls -lah /tmp/aisf-runtime-scripts.tgz /tmp/pulse-aarch64-libs.tar.gz /tmp/libandroid-emu-metrics.so* /tmp/adb.wrapper 2>/dev/null
""",
        timeout=120,
        get_pty=False,
    )
    print(stdout.read().decode("utf-8", errors="replace"))

    tsftp = tgt.open_sftp()
    for remote, name in [
        ("/tmp/pulse-aarch64-libs.tar.gz", "pulse-aarch64-libs.tar.gz"),
        ("/tmp/aisf-runtime-scripts.tgz", "aisf-runtime-scripts.tgz"),
        ("/tmp/libandroid-emu-metrics.so.pre_tls_patch", "libandroid-emu-metrics.so.pre_tls_patch"),
        ("/tmp/libandroid-emu-metrics.so.current", "libandroid-emu-metrics.so.current"),
        ("/tmp/adb.wrapper", "adb.wrapper"),
    ]:
        try:
            sftp_get(tsftp, remote, OUT / "runtime" / name)
        except Exception as ex:
            print(f"skip {remote}: {ex}", flush=True)
    tsftp.close()
    tgt.close()
    sftp.close()
    j.close()

    # write inventory
    lines = []
    for p in sorted(OUT.rglob("*")):
        if p.is_file():
            lines.append(f"{p.relative_to(OUT)}\t{p.stat().st_size}")
    (OUT / "INVENTORY.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("=== INVENTORY ===")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
