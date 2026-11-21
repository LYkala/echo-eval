import sys
from pathlib import Path
import paramiko

sys.path.insert(0, ".")
from deploy_from_windows import JUMP, parse_creds

ju, jp = parse_creds()[0]
local_dir = Path("tools/scrcpy")
local_dir.mkdir(parents=True, exist_ok=True)
zip_name = "scrcpy-win64-v3.3.1.zip"
url = "https://github.com/Genymobile/scrcpy/releases/download/v3.3.1/" + zip_name
remote_zip = "/tmp/" + zip_name

j = paramiko.SSHClient()
j.set_missing_host_key_policy(paramiko.AutoAddPolicy())
j.connect(JUMP[0], 22, username=ju, password=jp, timeout=90)
j.get_transport().set_keepalive(30)

# download on jump (has network)
cmd = f"test -s {remote_zip} || curl -L --retry 3 -o {remote_zip} {url}; ls -lah {remote_zip}"
print("downloading on jump...", flush=True)
stdin, stdout, stderr = j.exec_command(cmd, timeout=300)
out = stdout.read().decode("utf-8", "replace")
err = stderr.read().decode("utf-8", "replace")
print(out or err, flush=True)

sftp = j.open_sftp()
local_zip = local_dir / zip_name
print("pulling to", local_zip, flush=True)

def cb(tr, total):
    if total and (tr == total or tr % (5 * 1024 * 1024) < 32768):
        print(f"  {tr//1024//1024}/{total//1024//1024} MB", flush=True)

sftp.get(remote_zip, str(local_zip), callback=cb)
sftp.close()
j.close()
print("done", local_zip.stat().st_size)
