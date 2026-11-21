#!/usr/bin/env python3
"""Sync scripts + offline downloads to target (incremental, no rm -rf)."""

import stat
import sys
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_from_windows import REMOTE_DIR, connect_target, run, upload_tree  # noqa: E402

LAB = Path(__file__).resolve().parent


def put_file(sftp: paramiko.SFTPClient, local: Path, remote: str) -> None:
    parent = "/".join(remote.split("/")[:-1])
    try:
        sftp.mkdir(parent)
    except OSError:
        pass
    size_mb = local.stat().st_size // (1024 * 1024)
    print(f"  upload {local.name} ({size_mb} MB)", flush=True)
    sftp.put(str(local), remote)
    if local.suffix in {".sh", ".py"}:
        mode = sftp.stat(remote).st_mode
        sftp.chmod(remote, mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def main() -> int:
    _, target = connect_target()
    try:
        run(target, f"mkdir -p {REMOTE_DIR}/downloads/rpms {REMOTE_DIR}/runs {REMOTE_DIR}/input {REMOTE_DIR}/apk")
        sftp = target.open_sftp()

        for p in LAB.iterdir():
            if p.name in {"sdk", "runs", "dumps", ".pulse_modules", "__pycache__", "downloads"}:
                continue
            r = f"{REMOTE_DIR}/{p.name}"
            if p.is_dir():
                upload_tree(sftp, p, r)
            else:
                put_file(sftp, p, r)

        dl = LAB / "downloads"
        for p in sorted(dl.rglob("*")):
            if p.is_dir():
                continue
            rel = p.relative_to(dl).as_posix()
            put_file(sftp, p, f"{REMOTE_DIR}/downloads/{rel}")

        sftp.close()
        run(target, f"sed -i 's/\\r$//' {REMOTE_DIR}/*.sh {REMOTE_DIR}/env.sh 2>/dev/null || true")
        run(target, f"chmod +x {REMOTE_DIR}/*.sh")
        print("sync done")
        return 0
    finally:
        target.close()


if __name__ == "__main__":
    sys.exit(main())
