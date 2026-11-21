#!/usr/bin/env bash
# Stop current avd_2, clone into avd_1..avd_5 (independent), delete source backup,
# start each with unique port + VirtualMic, uniquify android_id, apply guest net.
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${LAB_ROOT}"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh" 2>/dev/null || true
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-/home/android-avds/avd}"
export ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-/home/android-avds}"
AVD_HOME="${ANDROID_AVD_HOME}"
MANAGE="${LAB_ROOT}/manage_emulator.sh"
LOG="${LAB_ROOT}/runs/provision_five_avds.log"
mkdir -p "${LAB_ROOT}/runs" "${AVD_HOME}"
exec > >(tee -a "${LOG}") 2>&1

echo "[$(date '+%F %T')] === provision five independent AVDs ==="
echo "AVD_HOME=${AVD_HOME}"
ls -la "${AVD_HOME}" || true

# Prefer live source: avd_1 (current), else avd_2 (legacy), else _clone_src.
SRC=""
for cand in avd_1 avd_2; do
  if [[ -d "${AVD_HOME}/${cand}.avd" ]]; then
    SRC="${cand}"
    break
  fi
done

echo "[1/6] stop running qemu / ${SRC:-all}"
if [[ -n "${SRC}" ]]; then
  bash "${MANAGE}" stop --name "${SRC}" || true
  pkill -f "qemu-system-aarch64.*-avd ${SRC}" 2>/dev/null || true
fi
# Ensure no leftover emulators hold ports 5554-5563
pkill -f 'qemu-system-aarch64-headless' 2>/dev/null || true
sleep 3

if [[ -z "${SRC}" && ! -d "${AVD_HOME}/_clone_src.avd" ]]; then
  echo "ERROR: no avd_1/avd_2/_clone_src under ${AVD_HOME}"
  ls -la "${AVD_HOME}" || true
  exit 1
fi

if [[ -n "${SRC}" && -d "${AVD_HOME}/${SRC}.avd" ]]; then
  echo "[2/6] rename ${SRC} -> _clone_src"
  rm -rf "${AVD_HOME}/_clone_src.avd" "${AVD_HOME}/_clone_src.ini"
  mv "${AVD_HOME}/${SRC}.avd" "${AVD_HOME}/_clone_src.avd"
  rm -f "${AVD_HOME}/${SRC}.ini"
  cat > "${AVD_HOME}/_clone_src.ini" <<EOF
avd.ini.displayname=_clone_src
avd.ini.encoding=UTF-8
path=${AVD_HOME}/_clone_src.avd
path.rel=avd/_clone_src.avd
target=android-33
EOF
else
  echo "[2/6] using existing _clone_src"
fi

# Remove any leftover target AVDs from a partial previous run
echo "[3/6] clone _clone_src -> avd_1..avd_5"
for i in 1 2 3 4 5; do
  name="avd_${i}"
  if [[ -d "${AVD_HOME}/${name}.avd" ]]; then
    echo "  deleting existing ${name}"
    bash "${MANAGE}" delete --name "${name}" || true
  fi
  echo "  cloning ${name} ..."
  bash "${MANAGE}" create --name "${name}" --clone-from _clone_src --display "${name}"
done

echo "[4/6] delete source backup _clone_src"
bash "${MANAGE}" delete --name _clone_src || true

echo "[5/6] start avd_1..5 with unique ports + per-AVD VirtualMic"
declare -A PORTS=([1]=5554 [2]=5556 [3]=5558 [4]=5560 [5]=5562)
for i in 1 2 3 4 5; do
  name="avd_${i}"
  port="${PORTS[$i]}"
  echo "  starting ${name} on console port ${port} ..."
  bash "${MANAGE}" start --name "${name}" --port "${port}"
done

echo "[6/6] uniquify identity + guest net + restart assistant"
python3 - <<'PY'
import hashlib, time, subprocess, textwrap, tempfile, os

LAB = "/root/aisf-voice-lab"
PORTS = {1: 5554, 2: 5556, 3: 5558, 4: 5560, 5: 5562}
PREF = textwrap.dedent(
    """\
    <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
    <map>
        <boolean name="enabled" value="true" />
        <string name="ws_url">ws://124.71.69.102:8735/agent/ws</string>
        <string name="wsUrl">ws://124.71.69.102:8735/agent/ws</string>
    </map>
    """
)

def run(cmd, t=120):
    print(">>", cmd[:220], flush=True)
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=t)
    out = (p.stdout or "") + (p.stderr or "")
    if out.strip():
        print(out[-2000:], flush=True)
    return p.returncode, out

pref_host = "/tmp/voxtest_agent.xml"
open(pref_host, "w", encoding="utf-8").write(PREF)

for i, port in PORTS.items():
    name = f"avd_{i}"
    serial = f"emulator-{port}"
    adb = f"cd {LAB} && python3 mini_adb_auth.py -s {serial}"
    for _ in range(80):
        code, out = run(f"{adb} shell getprop sys.boot_completed", t=30)
        if "1" in (out or ""):
            break
        time.sleep(3)
    else:
        print(f"WARN {name} boot timeout", flush=True)

    android_id = hashlib.md5(f"aisf-{name}-independent".encode()).hexdigest()[:16]
    print(f"=== {name} serial={serial} android_id={android_id} ===", flush=True)
    run(
        f"{adb} shell \"su 0 sh -c 'settings put secure android_id {android_id}; "
        f"rm -rf /data/data/com.huawei.aisftvassistant/cache/* "
        f"/data/data/com.huawei.aisftvassistant/files/dds "
        f"/data/data/com.huawei.aisftvassistant/files/logs "
        f"/data/data/com.huawei.aisftvassistant/code_cache; "
        f"mkdir -p /data/data/com.huawei.aisftvassistant/shared_prefs'\"",
        t=60,
    )
    for perm in (
        "android.permission.READ_PHONE_STATE",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.POST_NOTIFICATIONS",
    ):
        run(f"{adb} shell pm grant com.huawei.aisftvassistant {perm}", t=30)
    run(f"{adb} shell appops set com.huawei.aisftvassistant SYSTEM_ALERT_WINDOW allow", t=20)
    run(f"{adb} shell appops set com.huawei.aisftvassistant WRITE_SETTINGS allow", t=20)
    run(f"{adb} push {pref_host} /data/local/tmp/voxtest_agent.xml", t=30)
    run(
        f"{adb} shell \"su 0 sh -c '"
        f"cp /data/local/tmp/voxtest_agent.xml "
        f"/data/data/com.huawei.aisftvassistant/shared_prefs/voxtest_agent.xml; "
        f"cp /data/local/tmp/voxtest_agent.xml "
        f"/data/data/com.huawei.aisftvassistant/shared_prefs/voxtest.xml; "
        f"chmod 660 /data/data/com.huawei.aisftvassistant/shared_prefs/voxtest*.xml; "
        f"app_uid=$(stat -c %u /data/data/com.huawei.aisftvassistant); "
        f"chown $app_uid:$app_uid /data/data/com.huawei.aisftvassistant/shared_prefs/voxtest*.xml"
        f"'\"",
        t=60,
    )
    # Rewrite Hub deviceId per AVD (clones share ro.serialno → same native id).
    run(
        f"cd {LAB} && AVD_NAME={name} ADB_SERIAL={serial} bash ./ensure_avd_agent_identity.sh",
        t=180,
    )
    run(
        f"{adb} shell \"su 0 sh -c '"
        f"am force-stop com.huawei.aisftvassistant; sleep 1; "
        f"am start -n com.huawei.aisftvassistant/.activity.MainActivity"
        f"'\"",
        t=60,
    )
    run(f"{adb} emu avd hostmicon", t=20)

print("done identity+gw", flush=True)
PY

echo "[$(date '+%F %T')] === list ==="
bash "${MANAGE}" list
echo "Done. Log: ${LOG}"
