#!/usr/bin/env python3
"""Upload aisf-voice-lab bundle to Huawei Cloud target via jump host and run deploy."""

from __future__ import annotations

import os
import stat
import sys
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parent
EMULATOR_ROOT = ROOT.parent
CREDS_FILE = EMULATOR_ROOT / "华为云服务器.txt"
REMOTE_DIR = "/root/aisf-voice-lab"

JUMP = ("124.71.69.102", 22)
TARGET = ("121.37.53.201", 40204)


def parse_creds() -> tuple[tuple[str, str], tuple[str, str]]:
    text = CREDS_FILE.read_text(encoding="utf-8", errors="replace")
    jump_pass = "H1uawei@CLOUD9!"
    tgt_pass = "Clabuser@2022"
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("root") and "CLOUD" in line:
            jump_pass = line.split()[-1]
        if line.startswith("root") and "Clab" in line:
            tgt_pass = line.split()[-1]
    return (("root", jump_pass), ("root", tgt_pass))


def connect_target() -> tuple[paramiko.SSHClient, paramiko.SSHClient]:
    jump_user, jump_pass = parse_creds()[0]
    tgt_user, tgt_pass = parse_creds()[1]

    jump = paramiko.SSHClient()
    jump.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    jump.connect(JUMP[0], port=JUMP[1], username=jump_user, password=jump_pass, timeout=60)

    transport = jump.get_transport()
    assert transport is not None
    channel = transport.open_channel("direct-tcpip", (TARGET[0], TARGET[1]), ("127.0.0.1", 0))

    target = paramiko.SSHClient()
    target.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    target.connect(
        TARGET[0],
        port=TARGET[1],
        username=tgt_user,
        password=tgt_pass,
        sock=channel,
        timeout=60,
    )
    return jump, target


def run(client: paramiko.SSHClient, cmd: str, timeout: int = 7200) -> tuple[int, str, str]:
    print(f"$ {cmd[:120]}...", flush=True)
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout, get_pty=True)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def upload_tree(sftp: paramiko.SFTPClient, local: Path, remote: str) -> None:
    try:
        sftp.mkdir(remote)
    except OSError:
        pass

    for item in local.rglob("*"):
        rel = item.relative_to(local)
        rpath = f"{remote}/{rel.as_posix()}".replace("\\", "/")
        if item.is_dir():
            try:
                sftp.mkdir(rpath)
            except OSError:
                pass
        else:
            parent = "/".join(rpath.split("/")[:-1])
            try:
                sftp.mkdir(parent)
            except OSError:
                pass
            print(f"  upload {rel} ({item.stat().st_size // 1024} KB)", flush=True)
            sftp.put(str(item), rpath)
            if item.suffix in {".sh", ".py"} or item.name.endswith(".sh"):
                mode = sftp.stat(rpath).st_mode
                sftp.chmod(rpath, mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def main() -> int:
    bundle = ROOT
    files_needed = [
        bundle / "apk" / "aisf_voice_assistant.apk",
        EMULATOR_ROOT / "aisf_voice_assistant_tv-phoneRelease-20260715_103645.apk",
        EMULATOR_ROOT / "audio_samples" / "weather_question.wav",
    ]
    apk_src = files_needed[0] if files_needed[0].is_file() else files_needed[1]
    if not apk_src.is_file():
        print("AISF APK missing", file=sys.stderr)
        return 1
    wav_src = files_needed[2]
    if not wav_src.is_file():
        print("weather_question.wav missing", file=sys.stderr)
        return 1

    (bundle / "apk").mkdir(exist_ok=True)
    (bundle / "input").mkdir(exist_ok=True)
    import shutil

    dst_apk = bundle / "apk" / "aisf_voice_assistant.apk"
    if not dst_apk.is_file() or dst_apk.stat().st_size != apk_src.stat().st_size:
        try:
            shutil.copy2(apk_src, dst_apk)
        except PermissionError:
            if not dst_apk.is_file():
                raise
    dst_wav = bundle / "input" / "weather_question.wav"
    if not dst_wav.is_file():
        shutil.copy2(wav_src, dst_wav)

    jump, target = connect_target()
    try:
        run(target, f"rm -rf {REMOTE_DIR} && mkdir -p {REMOTE_DIR}/downloads {REMOTE_DIR}/runs")
        sftp = target.open_sftp()
        upload_list = [
            p
            for p in bundle.iterdir()
            if p.name not in {"sdk", "runs", "dumps", ".pulse_modules", "__pycache__", "downloads"}
        ]
        for p in upload_list:
            r = f"{REMOTE_DIR}/{p.name}"
            if p.is_dir():
                upload_tree(sftp, p, r)
            else:
                sftp.put(str(p), r)
        # optional offline zips
        dl = bundle / "downloads"
        if dl.is_dir():
            upload_tree(sftp, dl, f"{REMOTE_DIR}/downloads")
        sftp.close()

        run(target, f"sed -i 's/\\r$//' {REMOTE_DIR}/*.sh {REMOTE_DIR}/env.sh 2>/dev/null || true")
        run(target, f"chmod +x {REMOTE_DIR}/*.sh")
        run(
            target,
            f"sed -i 's|audio_samples/weather_question.wav|input/weather_question.wav|' {REMOTE_DIR}/env.sh || true",
        )

        code, out, err = run(
            target,
            f"cd {REMOTE_DIR} && export JAVA_HOME=/usr/lib/jvm/java-17-openjdk 2>/dev/null; "
            f"bash remote_deploy.sh 2>&1 | tee runs/deploy_full.log; "
            f"test ${{PIPESTATUS[0]}} -eq 0",
            timeout=7200,
        )
        print(out[-12000:] if len(out) > 12000 else out)
        if err.strip():
            print(err[-4000:], file=sys.stderr)
        if code != 0:
            print(f"deploy exit code {code}", file=sys.stderr)
            return code

        code2, out2, _ = run(target, f"ls -la {REMOTE_DIR}/runs/*/result.json 2>/dev/null; tail -30 {REMOTE_DIR}/runs/deploy_full.log")
        print(out2)
        return 0
    finally:
        target.close()
        jump.close()


if __name__ == "__main__":
    sys.exit(main())
