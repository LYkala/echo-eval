#!/usr/bin/env bash
# Android emulator lifecycle helper (list/create/update/start/stop/delete).
# Usage:
#   manage_emulator.sh list
#   manage_emulator.sh create --name NAME [--ram 8192] [--cores 4] [--disk 15G] [--display NAME]
#   manage_emulator.sh create --name NAME --clone-from SRC [--display NAME] [--ram N] [--cores N] [--disk SIZE]
#   manage_emulator.sh update --name NAME [--ram N] [--cores N] [--disk SIZE] [--display NAME]
#   manage_emulator.sh start --name NAME [--port 5554]
#   manage_emulator.sh stop --name NAME
#   manage_emulator.sh delete --name NAME
#   manage_emulator.sh status --name NAME
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LAB_ROOT}/env.sh"
[[ -f "${LAB_ROOT}/.java_env" ]] && source "${LAB_ROOT}/.java_env"

# Prefer large disk for new AVDs; still discover legacy under ~/.android/avd
AVD_HOME_PRIMARY="${ANDROID_AVD_HOME:-/home/android-avds/avd}"
AVD_HOME_LEGACY="${HOME}/.android/avd"
SYS_REL="system-images/android-33/google_apis/arm64-v8a"
SYS_ABS="${ANDROID_SDK_ROOT}/${SYS_REL}"
EMU_BIN="${ANDROID_SDK_ROOT}/emulator/qemu/linux-aarch64/qemu-system-aarch64-headless"
if [[ ! -x "${EMU_BIN}" ]]; then
  EMU_BIN="${ANDROID_SDK_ROOT}/emulator/emulator"
fi

mkdir -p "${AVD_HOME_PRIMARY}" "${LAB_ROOT}/runs"

PROGRESS_FILE="${LAB_ROOT}/runs/manage_progress.json"

set_progress() {
  local pct="$1"
  shift
  local msg="$*"
  mkdir -p "${LAB_ROOT}/runs"
  # Per-PID tmp avoids races when multiple manage_emulator start in parallel.
  PCT="$pct" MSG="$msg" FILE="$PROGRESS_FILE" PID="$$" python3 - <<'PY'
import json, os
from pathlib import Path
pct = max(0, min(100, int(os.environ.get("PCT") or 0)))
msg = os.environ.get("MSG") or ""
path = Path(os.environ["FILE"])
tmp = path.with_name(f"{path.stem}.{os.environ.get('PID','0')}.tmp")
tmp.write_text(json.dumps({"pct": pct, "msg": msg}, ensure_ascii=False), encoding="utf-8")
os.replace(tmp, path)
PY
}

clear_progress() {
  rm -f "${PROGRESS_FILE}" "${PROGRESS_FILE}.tmp" "${PROGRESS_FILE%/*}"/manage_progress.*.tmp 2>/dev/null || true
}

trap clear_progress EXIT

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()[:-1] if False else sys.argv[1]))' "$1"
}

find_avd_dir() {
  local name="$1"
  if [[ -d "${AVD_HOME_PRIMARY}/${name}.avd" ]]; then
    echo "${AVD_HOME_PRIMARY}/${name}.avd"
    return 0
  fi
  if [[ -d "${AVD_HOME_LEGACY}/${name}.avd" ]]; then
    echo "${AVD_HOME_LEGACY}/${name}.avd"
    return 0
  fi
  return 1
}

find_avd_ini() {
  local name="$1"
  if [[ -f "${AVD_HOME_PRIMARY}/${name}.ini" ]]; then
    echo "${AVD_HOME_PRIMARY}/${name}.ini"
    return 0
  fi
  if [[ -f "${AVD_HOME_LEGACY}/${name}.ini" ]]; then
    echo "${AVD_HOME_LEGACY}/${name}.ini"
    return 0
  fi
  return 1
}

running_pid_for() {
  local name="$1"
  pgrep -af "qemu-system-aarch64" 2>/dev/null | grep -F -- "-avd ${name}" | grep -v grep | awk '{print $1}' | head -1 || true
}

adb_port_from_cmdline() {
  local pid="$1"
  local cmd
  cmd="$(tr '\0' ' ' < "/proc/${pid}/cmdline" 2>/dev/null || true)"
  # -ports console,adb  OR  -port console (adb=console+1)
  if [[ "${cmd}" =~ -ports[[:space:]]+([0-9]+),([0-9]+) ]]; then
    echo "${BASH_REMATCH[2]}"
    return
  fi
  if [[ "${cmd}" =~ -port[[:space:]]+([0-9]+) ]]; then
    echo $((BASH_REMATCH[1] + 1))
    return
  fi
  # default first instance
  echo 5555
}

read_config_val() {
  local file="$1" key="$2" def="$3"
  if [[ -f "${file}" ]]; then
    local v
    v="$(grep -E "^${key}=" "${file}" 2>/dev/null | head -1 | cut -d= -f2- || true)"
    if [[ -n "${v}" ]]; then
      echo "${v}"
      return
    fi
  fi
  echo "${def}"
}

cmd_list() {
  python3 - <<'PY'
import json, os, re, subprocess
from pathlib import Path

homes = []
for h in (os.environ.get("AVD_HOME_PRIMARY", "/home/android-avds/avd"),
          os.path.expanduser("~/.android/avd")):
    p = Path(h)
    if p.is_dir() and p not in homes:
        homes.append(p)

items = {}
for home in homes:
    for ini in home.glob("*.ini"):
        name = ini.stem
        avd_dir = home / f"{name}.avd"
        cfg = avd_dir / "config.ini"
        def gv(k, d=""):
            if not cfg.is_file():
                return d
            for line in cfg.read_text(encoding="utf-8", errors="replace").splitlines():
                if line.startswith(k + "="):
                    return line.split("=", 1)[1].strip()
            return d
        items[name] = {
            "name": name,
            "display_name": gv("avd.ini.displayname", name),
            "path": str(avd_dir),
            "home": str(home),
            "ram_mb": int(gv("hw.ramSize", "4096") or 4096),
            "cores": int(gv("hw.cpu.ncore", "4") or 4),
            "disk": gv("disk.dataPartition.size", "6G"),
            "abi": gv("abi.type", "arm64-v8a"),
            "running": False,
            "pid": None,
            "adb_port": None,
            "console_port": None,
            "serial": None,
        }

# running qemu
try:
    out = subprocess.check_output(["ps", "-eo", "pid,cmd"], text=True, errors="replace")
except Exception:
    out = ""
for line in out.splitlines():
    if "qemu-system-aarch64" not in line or "-avd" not in line:
        continue
    m = re.search(r"^\s*(\d+)\s+(.*)$", line)
    if not m:
        continue
    pid, cmd = m.group(1), m.group(2)
    am = re.search(r"-avd\s+(\S+)", cmd)
    if not am:
        continue
    name = am.group(1)
    adb_port = 5555
    pm = re.search(r"-ports\s+(\d+),(\d+)", cmd)
    if pm:
        console, adb_port = int(pm.group(1)), int(pm.group(2))
    else:
        cm = re.search(r"-port\s+(\d+)", cmd)
        if cm:
            console = int(cm.group(1))
            adb_port = console + 1
        else:
            console = 5554
    if name not in items:
        items[name] = {
            "name": name,
            "display_name": name,
            "path": "",
            "home": "",
            "ram_mb": 0,
            "cores": 0,
            "disk": "",
            "abi": "arm64-v8a",
            "running": True,
            "pid": int(pid),
            "adb_port": adb_port,
            "console_port": console,
            "serial": f"emulator-{console}",
        }
    else:
        items[name]["running"] = True
        items[name]["pid"] = int(pid)
        items[name]["adb_port"] = adb_port
        items[name]["console_port"] = console
        items[name]["serial"] = f"emulator-{console}"

print(json.dumps({"ok": True, "emulators": list(items.values())}, ensure_ascii=False))
PY
}

rewrite_avd_identity() {
  # Rewrite AvdId / display name / optional hw overrides inside dest config.ini + write .ini
  local name="$1" display="$2" avd_dir="$3" ini="$4" ram="${5:-}" cores="${6:-}" disk="${7:-}"
  NAME="$name" DISPLAY_NAME="$display" RAM="$ram" CORES="$cores" DISK="$disk" \
    CFG="${avd_dir}/config.ini" INI="$ini" AVD_DIR="$avd_dir" python3 - <<'PY'
import os, re
from pathlib import Path
name = os.environ["NAME"]
display = os.environ.get("DISPLAY_NAME") or name
ram = (os.environ.get("RAM") or "").strip()
cores = (os.environ.get("CORES") or "").strip()
disk = (os.environ.get("DISK") or "").strip()
cfg = Path(os.environ["CFG"])
avd_dir = os.environ["AVD_DIR"]
ini = Path(os.environ["INI"])
text = cfg.read_text(encoding="utf-8", errors="replace") if cfg.is_file() else ""
kv, order = {}, []
for line in text.splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k, v = line.split("=", 1)
        kv[k] = v
        order.append(k)
    else:
        order.append(line)

def setk(k, v):
    if k not in kv:
        order.append(k)
    kv[k] = str(v)

setk("AvdId", name)
setk("avd.ini.displayname", display)
if ram:
    setk("hw.ramSize", ram)
if cores:
    setk("hw.cpu.ncore", cores)
if disk:
    setk("disk.dataPartition.size", disk)
lines, seen = [], set()
for item in order:
    if isinstance(item, str) and "=" not in item and item not in kv:
        lines.append(item)
        continue
    k = item.split("=", 1)[0] if "=" in str(item) else item
    if k in kv and k not in seen:
        lines.append(f"{k}={kv[k]}")
        seen.add(k)
for k, v in kv.items():
    if k not in seen:
        lines.append(f"{k}={v}")
cfg.write_text("\n".join(lines) + "\n", encoding="utf-8")
ini.write_text(
    f"avd.ini.displayname={display}\n"
    f"avd.ini.encoding=UTF-8\n"
    f"path={avd_dir}\n"
    f"path.rel=avd/{name}.avd\n"
    f"target=android-33\n",
    encoding="utf-8",
)
print(kv.get("hw.ramSize", ""), kv.get("hw.cpu.ncore", ""), kv.get("disk.dataPartition.size", ""))
PY
}

cmd_create() {
  local name="" display="" ram="" cores="" disk="" clone_from=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --name) name="$2"; shift 2 ;;
      --display) display="$2"; shift 2 ;;
      --ram) ram="$2"; shift 2 ;;
      --cores) cores="$2"; shift 2 ;;
      --disk) disk="$2"; shift 2 ;;
      --clone-from) clone_from="$2"; shift 2 ;;
      *) echo "unknown arg $1" >&2; exit 2 ;;
    esac
  done
  [[ -n "${name}" ]] || { echo '{"ok":false,"error":"name required"}'; exit 1; }
  [[ "${name}" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo '{"ok":false,"error":"invalid name"}'; exit 1; }
  display="${display:-$name}"
  if find_avd_dir "${name}" >/dev/null 2>&1; then
    echo "{\"ok\":false,\"error\":\"AVD already exists: ${name}\"}"
    exit 1
  fi

  local avd_dir="${AVD_HOME_PRIMARY}/${name}.avd"
  local ini="${AVD_HOME_PRIMARY}/${name}.ini"
  mkdir -p "${AVD_HOME_PRIMARY}"

  if [[ -n "${clone_from}" ]]; then
    [[ "${clone_from}" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo '{"ok":false,"error":"invalid clone-from name"}'; exit 1; }
    [[ "${clone_from}" != "${name}" ]] || { echo '{"ok":false,"error":"clone-from must differ from name"}'; exit 1; }
    local src_dir
    src_dir="$(find_avd_dir "${clone_from}")" || { echo "{\"ok\":false,\"error\":\"source AVD not found: ${clone_from}\"}"; exit 1; }
    local src_pid
    src_pid="$(running_pid_for "${clone_from}" || true)"
    if [[ -n "${src_pid}" ]]; then
      echo "{\"ok\":false,\"error\":\"源模拟器 ${clone_from} 正在运行，请先停止再整机克隆（避免数据不一致）\"}"
      exit 1
    fi
    mkdir -p "${avd_dir}"
    set_progress 5 "整机克隆：准备复制 ${clone_from} → ${name}"
    local src_bytes=0
    src_bytes="$(du -sb "${src_dir}" 2>/dev/null | awk '{print $1}')"
    src_bytes="${src_bytes:-0}"
    # Copy full userdata / apps; drop locks and runtime hardware binding (absolute paths).
    local watch_pid=""
    if [[ "${src_bytes}" =~ ^[0-9]+$ ]] && [[ "${src_bytes}" -gt 0 ]]; then
      (
        while true; do
          dst="$(du -sb "${avd_dir}" 2>/dev/null | awk '{print $1}')"
          dst="${dst:-0}"
          pct=$(( 8 + dst * 85 / src_bytes ))
          [[ "${pct}" -gt 93 ]] && pct=93
          set_progress "${pct}" "整机克隆：复制中 ${pct}%"
          sleep 1
        done
      ) &
      watch_pid=$!
    else
      set_progress 15 "整机克隆：复制中…"
    fi
    if command -v rsync >/dev/null 2>&1; then
      rsync -a --delete \
        --exclude='*.lock' \
        --exclude='multiinstance.lock' \
        --exclude='tmpAdbCmds' \
        --exclude='hardware-qemu.ini' \
        --exclude='hardware-qemu.ini.lock' \
        --exclude='snapshots' \
        "${src_dir}/" "${avd_dir}/"
    else
      rm -rf "${avd_dir}"
      cp -a "${src_dir}" "${avd_dir}"
      rm -f "${avd_dir}/"*.lock "${avd_dir}/multiinstance.lock" \
        "${avd_dir}/tmpAdbCmds" "${avd_dir}/hardware-qemu.ini" \
        "${avd_dir}/hardware-qemu.ini.lock" 2>/dev/null || true
      rm -rf "${avd_dir}/snapshots" 2>/dev/null || true
    fi
    if [[ -n "${watch_pid}" ]]; then
      kill "${watch_pid}" 2>/dev/null || true
      wait "${watch_pid}" 2>/dev/null || true
    fi
    set_progress 95 "整机克隆：写入配置"
    local hw_out
    hw_out="$(rewrite_avd_identity "${name}" "${display}" "${avd_dir}" "${ini}" "${ram}" "${cores}" "${disk}")"
    local out_ram out_cores out_disk
    read -r out_ram out_cores out_disk <<<"${hw_out}"
    local size_h
    size_h="$(du -sh "${avd_dir}" 2>/dev/null | awk '{print $1}')"
    echo "{\"ok\":true,\"mode\":\"clone\",\"name\":\"${name}\",\"clone_from\":\"${clone_from}\",\"path\":\"${avd_dir}\",\"display_name\":\"${display}\",\"ram_mb\":${out_ram:-null},\"cores\":${out_cores:-null},\"disk\":\"${out_disk}\",\"size\":\"${size_h}\"}"
    exit 0
  fi

  # Blank AVD
  set_progress 20 "创建空白模拟器 ${name}"
  ram="${ram:-8192}"
  cores="${cores:-4}"
  disk="${disk:-15G}"
  if [[ ! -f "${SYS_ABS}/system.img" ]]; then
    echo "{\"ok\":false,\"error\":\"system image missing: ${SYS_ABS}/system.img\"}"
    exit 1
  fi
  mkdir -p "${avd_dir}"
  cat > "${ini}" <<EOF
avd.ini.displayname=${display}
avd.ini.encoding=UTF-8
path=${avd_dir}
path.rel=avd/${name}.avd
target=android-33
EOF
  cat > "${avd_dir}/config.ini" <<EOF
AvdId=${name}
PlayStore.enabled=false
abi.type=arm64-v8a
avd.ini.displayname=${display}
avd.ini.encoding=UTF-8
disk.dataPartition.size=${disk}
hw.cpu.arch=arm64
hw.cpu.ncore=${cores}
hw.ramSize=${ram}
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
image.sysdir.1=${SYS_REL}/
tag.display=Google APIs
tag.id=google_apis
vm.heapSize=256
quickbootChoice=no
hw.lcd.width=720
hw.lcd.height=1280
hw.lcd.depth=16
hw.lcd.density=320
skin.name=720x1280
skin.path=_no_skin
EOF
  echo "{\"ok\":true,\"mode\":\"blank\",\"name\":\"${name}\",\"path\":\"${avd_dir}\",\"display_name\":\"${display}\",\"ram_mb\":${ram},\"cores\":${cores},\"disk\":\"${disk}\"}"
}

cmd_update() {
  local name="" display="" ram="" cores="" disk=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --name) name="$2"; shift 2 ;;
      --display) display="$2"; shift 2 ;;
      --ram) ram="$2"; shift 2 ;;
      --cores) cores="$2"; shift 2 ;;
      --disk) disk="$2"; shift 2 ;;
      *) shift ;;
    esac
  done
  local avd_dir cfg ini
  avd_dir="$(find_avd_dir "${name}")" || { echo '{"ok":false,"error":"AVD not found"}'; exit 1; }
  cfg="${avd_dir}/config.ini"
  ini="$(find_avd_ini "${name}" || true)"
  NAME="$name" DISPLAY_NAME="$display" RAM="$ram" CORES="$cores" DISK="$disk" CFG="$cfg" INI="$ini" python3 - <<'PY'
import os, re
from pathlib import Path
cfg = Path(os.environ["CFG"])
text = cfg.read_text(encoding="utf-8", errors="replace") if cfg.is_file() else ""
kv, order = {}, []
for line in text.splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k, v = line.split("=", 1)
        kv[k] = v
        order.append(k)
    else:
        order.append(line)

def setk(k, v):
    if k not in kv:
        order.append(k)
    kv[k] = str(v)

display = os.environ.get("DISPLAY_NAME") or ""
ram = os.environ.get("RAM") or ""
cores = os.environ.get("CORES") or ""
disk = os.environ.get("DISK") or ""
if display:
    setk("avd.ini.displayname", display)
if ram:
    setk("hw.ramSize", ram)
if cores:
    setk("hw.cpu.ncore", cores)
if disk:
    setk("disk.dataPartition.size", disk)
lines, seen = [], set()
for item in order:
    if isinstance(item, str) and "=" not in item and item not in kv:
        lines.append(item)
        continue
    k = item.split("=", 1)[0] if "=" in str(item) else item
    if k in kv and k not in seen:
        lines.append(f"{k}={kv[k]}")
        seen.add(k)
for k, v in kv.items():
    if k not in seen:
        lines.append(f"{k}={v}")
cfg.write_text("\n".join(lines) + "\n", encoding="utf-8")
ini_s = os.environ.get("INI") or ""
ini = Path(ini_s) if ini_s else None
if ini and ini.is_file() and display:
    t = ini.read_text(encoding="utf-8", errors="replace")
    t2 = re.sub(r"(?m)^avd.ini.displayname=.*$", f"avd.ini.displayname={display}", t)
    if t2 == t and "avd.ini.displayname=" not in t:
        t2 = t.rstrip() + f"\navd.ini.displayname={display}\n"
    ini.write_text(t2, encoding="utf-8")
print('{"ok":true,"name":"%s"}' % os.environ["NAME"])
PY
}

cmd_start() {
  local name="" port=5554
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --name) name="$2"; shift 2 ;;
      --port) port="$2"; shift 2 ;;
      *) shift ;;
    esac
  done
  set_progress 3 "启动模拟器 ${name}"
  local avd_dir
  avd_dir="$(find_avd_dir "${name}")" || { echo '{"ok":false,"error":"AVD not found"}'; exit 1; }
  local pid
  pid="$(running_pid_for "${name}")"
  if [[ -n "${pid}" ]]; then
    local adb_port
    adb_port="$(adb_port_from_cmdline "${pid}")"
    set_progress 100 "已在运行"
    echo "{\"ok\":true,\"already_running\":true,\"name\":\"${name}\",\"pid\":${pid},\"adb_port\":${adb_port},\"console_port\":$((adb_port-1)),\"serial\":\"emulator-$((adb_port-1))\"}"
    exit 0
  fi
  # Ensure ANDROID_AVD_HOME points at the home containing this AVD
  local home_dir
  home_dir="$(dirname "${avd_dir}")"
  export ANDROID_AVD_HOME="${home_dir}"
  export ANDROID_SDK_HOME="$(dirname "${home_dir}")"
  local log="${LAB_ROOT}/runs/emulator_${name}.log"
  local adb_port=$((port + 1))
  # Default: no qemu http-proxy. Per-AVD agent DNAT/rewrite + guest_connect_gw need direct guest TCP.
  # Opt-in only: AISF_HTTP_PROXY=http://127.0.0.1:8888
  local proxy_args=()
  if [[ -n "${AISF_HTTP_PROXY:-}" && "${AISF_HTTP_PROXY}" != "0" && "${AISF_HTTP_PROXY}" != "off" ]]; then
    proxy_args+=(-http-proxy "${AISF_HTTP_PROXY}")
  fi
  # Emulators use -dns-server 127.0.0.1 → require local DoH stub (same as restart_emulator.sh).
  if [[ -f "${LAB_ROOT}/doh_dns_proxy.py" ]]; then
    printf 'nameserver 127.0.0.1\noptions timeout:2 attempts:2\n' > /etc/resolv.conf
    if ! ss -ulnp 2>/dev/null | grep -q '127.0.0.1:53'; then
      nohup python3 "${LAB_ROOT}/doh_dns_proxy.py" >/tmp/doh_dns.log 2>&1 &
      echo $! > /tmp/doh_dns.pid
      sleep 1
    fi
    if [[ -x "${LAB_ROOT}/watch_doh_dns.sh" ]] && ! kill -0 "$(cat /tmp/doh_watch.pid 2>/dev/null)" 2>/dev/null; then
      nohup bash "${LAB_ROOT}/watch_doh_dns.sh" >/tmp/doh_watch.out 2>&1 &
      echo $! > /tmp/doh_watch.pid
    fi
  fi
  # Per-AVD Pulse VirtualMic (ignore env.sh default VirtualMic so clones stay isolated).
  export PULSE_RUNTIME_PATH="${PULSE_RUNTIME_PATH:-/tmp/pulse-root}"
  export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/pulse-root}"
  local vmic="VirtualMic_${name}"
  if [[ -n "${AISF_VIRTUAL_MIC_OVERRIDE:-}" ]]; then
    vmic="${AISF_VIRTUAL_MIC_OVERRIDE}"
  fi
  export AISF_VIRTUAL_MIC="${vmic}"
  export PULSE_SOURCE="${vmic}.monitor"
  if [[ -x "${LAB_ROOT}/setup_virtual_mic.sh" ]]; then
    set_progress 8 "启动：准备 ${vmic}"
    AISF_VIRTUAL_MIC="${vmic}" bash "${LAB_ROOT}/setup_virtual_mic.sh" --ensure \
      || echo "[manage] warn: setup_virtual_mic --ensure failed" >&2
  fi
  mkdir -p "${LAB_ROOT}/runs"
  bash "${LAB_ROOT}/start_emu_qos_monitor.sh" >/dev/null 2>&1 || true
  echo "${vmic}" > "${LAB_ROOT}/runs/virtual_mic_${name}.name"
  # Emulator expects host adb on :5037; start it if another AVD was launched without platform-tools.
  if [[ -x "${LAB_ROOT}/sdk/platform-tools/adb" ]]; then
    "${LAB_ROOT}/sdk/platform-tools/adb" start-server >/dev/null 2>&1 || true
  fi
  set_progress 12 "启动：拉起模拟器进程"
  local cpuset=""
  if [[ -z "${AISF_SKIP_CPUSET:-}" && -f "${LAB_ROOT}/avd_cpuset.conf" ]]; then
    cpuset="$(
      AVD_CPU_NAME="${name}" CONF="${LAB_ROOT}/avd_cpuset.conf" python3 - <<'PY'
import os
from pathlib import Path
name = os.environ.get("AVD_CPU_NAME", "")
conf = Path(os.environ.get("CONF", ""))
if not name or not conf.is_file():
    raise SystemExit(0)
for raw in conf.read_text(encoding="utf-8", errors="replace").splitlines():
    line = raw.split("#", 1)[0].strip()
    if not line or "=" not in line:
        continue
    k, v = line.split("=", 1)
    if k.strip() == name and v.strip():
        print(v.strip())
        raise SystemExit(0)
PY
    )" || true
    if [[ -n "${cpuset}" ]]; then
      echo "[manage] cpuset ${name} -> ${cpuset}" >&2
    fi
  fi
  local emu_launch=( "${EMU_BIN}" )
  if [[ -n "${cpuset}" ]]; then
    emu_launch=( taskset -c "${cpuset}" "${EMU_BIN}" )
  fi
  nohup env \
    "PULSE_RUNTIME_PATH=${PULSE_RUNTIME_PATH}" \
    "XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR}" \
    "PULSE_SERVER=unix:${PULSE_RUNTIME_PATH}/native" \
    "PULSE_SOURCE=${PULSE_SOURCE}" \
    "AISF_VIRTUAL_MIC=${vmic}" \
    "SDL_AUDIODRIVER=pulse" \
    "QEMU_AUDIO_DRV=pa" \
    "${emu_launch[@]}" \
    -avd "${name}" \
    -port "${port}" \
    -no-window \
    -no-snapshot \
    -no-snapshot-load \
    -no-snapshot-save \
    -gpu swiftshader_indirect \
    -no-boot-anim \
    -no-metrics \
    -feature -Vulkan \
    -audio sdl \
    -allow-host-audio \
    -dns-server 127.0.0.1 \
    "${proxy_args[@]}" \
    > "${log}" 2>&1 &
  local qpid=$!
  # wait for adb port
  local i
  local adb_ready=0
  for i in $(seq 1 90); do
    local pct=$(( 15 + i * 30 / 90 ))
    set_progress "${pct}" "启动：等待 ADB (${i}/90)"
    if (echo >/dev/tcp/127.0.0.1/"${adb_port}") >/dev/null 2>&1; then
      adb_ready=1
      break
    fi
    sleep 2
  done
  # wait boot via mini_adb if available
  if [[ -f "${LAB_ROOT}/mini_adb_auth.py" ]]; then
    local ADB_S="python3 ${LAB_ROOT}/mini_adb_auth.py -s emulator-${port}"
    for i in $(seq 1 120); do
      local pct=$(( 50 + i * 45 / 120 ))
      set_progress "${pct}" "启动：等待系统开机 (${i}/120)"
      boot="$(${ADB_S} shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      if [[ "${boot}" == "1" ]]; then
        # Enable host mic inject path used by voice dial-test.
        ${ADB_S} emu avd hostmicon >/dev/null 2>&1 || true
        set_progress 100 "启动完成"
        if [[ -z "${AISF_SKIP_CPUSET:-}" && -f "${LAB_ROOT}/avd_cpuset.conf" ]]; then
          bash "${LAB_ROOT}/apply_avd_cpuset.sh" apply "${name}" >/dev/null 2>&1 \
            || echo "[manage] warn: apply_avd_cpuset ${name} failed" >&2
        fi
        echo "{\"ok\":true,\"name\":\"${name}\",\"pid\":${qpid},\"adb_port\":${adb_port},\"console_port\":${port},\"serial\":\"emulator-${port}\",\"boot\":true}"
        exit 0
      fi
      sleep 3
    done
    set_progress 98 "启动：开机超时，进程仍在运行"
    echo "{\"ok\":true,\"name\":\"${name}\",\"pid\":${qpid},\"adb_port\":${adb_port},\"console_port\":${port},\"serial\":\"emulator-${port}\",\"boot\":false,\"warning\":\"boot timeout, still starting\"}"
    exit 0
  fi
  set_progress 100 "启动完成"
  echo "{\"ok\":true,\"name\":\"${name}\",\"pid\":${qpid},\"adb_port\":${adb_port},\"console_port\":${port},\"serial\":\"emulator-${port}\",\"adb_ready\":${adb_ready}}"
}

cmd_stop() {
  local name=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --name) name="$2"; shift 2 ;;
      *) shift ;;
    esac
  done
  set_progress 20 "停止模拟器 ${name}"
  local pid
  pid="$(running_pid_for "${name}")"
  if [[ -z "${pid}" ]]; then
    set_progress 100 "未在运行"
    echo "{\"ok\":true,\"name\":\"${name}\",\"stopped\":false,\"message\":\"not running\"}"
    return 0
  fi
  set_progress 50 "停止：结束进程 ${pid}"
  kill "${pid}" 2>/dev/null || true
  sleep 2
  if kill -0 "${pid}" 2>/dev/null; then
    set_progress 80 "停止：强制结束"
    kill -9 "${pid}" 2>/dev/null || true
  fi
  set_progress 100 "已停止"
  echo "{\"ok\":true,\"name\":\"${name}\",\"stopped\":true,\"pid\":${pid}}"
  return 0
}

cmd_delete() {
  local name=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --name) name="$2"; shift 2 ;;
      *) shift ;;
    esac
  done
  cmd_stop --name "${name}" >/dev/null || true
  local avd_dir ini
  avd_dir="$(find_avd_dir "${name}" 2>/dev/null || true)"
  ini="$(find_avd_ini "${name}" 2>/dev/null || true)"
  [[ -n "${avd_dir}" ]] && rm -rf "${avd_dir}"
  [[ -n "${ini}" ]] && rm -f "${ini}"
  echo "{\"ok\":true,\"name\":\"${name}\",\"deleted\":true}"
}

cmd_status() {
  local name=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --name) name="$2"; shift 2 ;;
      *) shift ;;
    esac
  done
  cmd_list | python3 -c 'import json,sys; d=json.load(sys.stdin); n=sys.argv[1];
items=[x for x in d.get("emulators",[]) if x.get("name")==n];
print(json.dumps({"ok":True,"emulator":(items[0] if items else None)},ensure_ascii=False))' "${name}"
}

export AVD_HOME_PRIMARY AVD_HOME_LEGACY
action="${1:-}"
shift || true
case "${action}" in
  list) cmd_list ;;
  create) cmd_create "$@" ;;
  update) cmd_update "$@" ;;
  start) cmd_start "$@" ;;
  stop) cmd_stop "$@" ;;
  delete) cmd_delete "$@" ;;
  status) cmd_status "$@" ;;
  *)
    echo '{"ok":false,"error":"usage: list|create|update|start|stop|delete|status"}'
    exit 2
    ;;
esac
