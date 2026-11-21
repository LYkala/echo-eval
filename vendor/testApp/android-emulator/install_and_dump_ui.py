#!/usr/bin/env python3
"""Install the AISF target APK on emulator and dump UI element IDs."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from datetime import datetime
from glob import glob
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

ROOT = Path(__file__).resolve().parent
DEFAULT_PACKAGE = "com.huawei.aisftvassistant"
DEFAULT_APP_LABEL = "AISF TV Assistant"
VOICE_KEYWORDS = (
    "voice",
    "speech",
    "mic",
    "audio",
    "speak",
    "record",
    "asr",
    "tts",
    "语音",
    "说话",
    "麦克风",
    "录音",
    "按住",
)


@dataclass
class UiNode:
    resource_id: str
    class_name: str
    text: str
    content_desc: str
    clickable: bool
    long_clickable: bool
    focusable: bool
    enabled: bool
    checkable: bool
    checked: bool
    password: bool
    bounds: str
    package: str

    @property
    def short_id(self) -> str:
        if not self.resource_id:
            return ""
        if ":id/" in self.resource_id:
            return self.resource_id.split(":id/", 1)[1]
        return self.resource_id

    @property
    def voice_related(self) -> bool:
        blob = " ".join(
            part.lower()
            for part in (self.resource_id, self.class_name, self.text, self.content_desc)
            if part
        )
        return any(keyword in blob for keyword in VOICE_KEYWORDS)


def _sdk_root() -> Path:
    for key in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        value = os.environ.get(key, "").strip()
        if value:
            return Path(value)
    return Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk"


def _find_tool(name: str, search_dirs: Sequence[Path]) -> Optional[Path]:
    for folder in search_dirs:
        if not folder.is_dir():
            continue
        if folder.name == name or folder.name == f"{name}.exe":
            return folder
        candidate = folder / f"{name}.exe"
        if candidate.is_file():
            return candidate
        candidate = folder / name
        if candidate.is_file():
            return candidate
    return None


def find_adb() -> Path:
    sdk = _sdk_root()
    adb = _find_tool("adb", [sdk / "platform-tools"])
    if adb:
        return adb
    raise FileNotFoundError(
        f"未找到 adb，请安装 Android SDK platform-tools，或设置 ANDROID_SDK_ROOT={sdk}"
    )


def find_aapt() -> Optional[Path]:
    sdk = _sdk_root()
    build_tools = sdk / "build-tools"
    if not build_tools.is_dir():
        return None
    versions = sorted(build_tools.iterdir(), reverse=True)
    for version_dir in versions:
        aapt = _find_tool("aapt", [version_dir])
        if aapt:
            return aapt
    return None


def run_cmd(
    args: Sequence[str],
    *,
    check: bool = True,
    capture: bool = True,
    timeout: Optional[float] = None,
) -> subprocess.CompletedProcess:
    result = subprocess.run(
        list(args),
        capture_output=capture,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )
    if check and result.returncode != 0:
        stderr = (result.stderr or "").strip()
        stdout = (result.stdout or "").strip()
        detail = stderr or stdout or f"exit {result.returncode}"
        raise RuntimeError(f"命令失败: {' '.join(args)}\n{detail}")
    return result


def adb_cmd(
    adb: Path,
    serial: Optional[str],
    *args: str,
    check: bool = True,
) -> subprocess.CompletedProcess:
    cmd = [str(adb)]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return run_cmd(cmd, check=check)


def pick_apk(path: Optional[str]) -> Path:
    if path:
        apk = Path(path).resolve()
        if not apk.is_file():
            raise FileNotFoundError(f"APK 不存在: {apk}")
        return apk
    matches = sorted(ROOT.glob("*.apk"))
    if not matches:
        raise FileNotFoundError(f"在 {ROOT} 下未找到 *.apk，请用 --apk 指定路径")
    if len(matches) > 1:
        print(f"发现多个 APK，默认使用: {matches[0].name}", file=sys.stderr)
    return matches[0]


def parse_apk_info(apk: Path, aapt: Optional[Path]) -> Tuple[str, str, List[str]]:
    if not aapt:
        return DEFAULT_PACKAGE, "", []
    result = run_cmd([str(aapt), "dump", "badging", str(apk)])
    package_match = re.search(r"package: name='([^']+)'", result.stdout)
    activity_match = re.search(r"launchable-activity: name='([^']+)'", result.stdout)
    native_match = re.search(r"native-code: '([^']+)'", result.stdout)
    package_name = package_match.group(1) if package_match else DEFAULT_PACKAGE
    activity = activity_match.group(1) if activity_match else ""
    abis = []
    if native_match:
        abis = [item.strip() for item in native_match.group(1).split(",") if item.strip()]
    return package_name, activity, abis


def get_device_abilist(adb: Path, serial: str) -> List[str]:
    result = adb_cmd(adb, serial, "shell", "getprop", "ro.product.cpu.abilist")
    raw = (result.stdout or "").strip()
    if not raw:
        abi = get_device_abi(adb, serial)
        return [abi] if abi else []
    return [item.strip() for item in raw.split(",") if item.strip()]


def _abi_install_hint(apk_abis: Sequence[str], device_abilist: Sequence[str], serial: str) -> str:
    if not apk_abis:
        return ""
    device_set = set(device_abilist)
    if device_set.intersection(apk_abis):
        return ""

    arm_only = set(apk_abis).issubset({"armeabi-v7a", "armeabi"})
    if arm_only and "armeabi-v7a" not in device_set:
        return (
            f"APK 仅支持 {', '.join(apk_abis)}，设备 {serial} 支持 {', '.join(device_abilist)}。\n"
            "请使用带 ARM 转译的模拟器: .\\scripts\\start-emulator-aisf.ps1 (TestAppGUI_API30_ARMTRANS)，"
            "或改用 ARM 真机。"
        )
    return f"APK ABI={', '.join(apk_abis)} 与设备 ABI={', '.join(device_abilist)} 不匹配。"


def start_emulator_if_needed(adb: Path, timeout_sec: int = 300) -> None:
    if list_devices(adb):
        return
    script = ROOT / "scripts" / "start-emulator.ps1"
    if not script.is_file():
        raise RuntimeError(
            "没有在线设备，且未找到 scripts/start-emulator.ps1，请先手动启动模拟器或连接真机。"
        )
    print("No device online, starting emulator ...")
    run_cmd(
        [
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(script),
        ],
        timeout=timeout_sec,
    )
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        if list_devices(adb):
            return
        time.sleep(2)
    raise TimeoutError("启动模拟器后仍未检测到在线设备。")


def list_devices(adb: Path) -> List[str]:
    result = adb_cmd(adb, None, "devices")
    devices: List[str] = []
    for line in (result.stdout or "").splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def pick_device(adb: Path, serial: Optional[str], apk_abis: Optional[Sequence[str]] = None) -> str:
    devices = list_devices(adb)
    if not devices:
        raise RuntimeError("没有在线设备")
    if serial:
        if serial not in devices:
            raise RuntimeError(f"指定设备不在线: {serial}，当前设备: {', '.join(devices)}")
        return serial

    arm_only = apk_abis and set(apk_abis).issubset({"armeabi-v7a", "armeabi"})
    if arm_only:
        physical = [item for item in devices if not item.startswith("emulator-")]
        if physical:
            print(f"APK is ARM-only, prefer physical device: {physical[0]}")
            return physical[0]

    emulators = [item for item in devices if item.startswith("emulator-")]
    if emulators:
        return emulators[0]
    return devices[0]


def wait_for_boot(adb: Path, serial: str, timeout_sec: int = 180) -> None:
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        result = adb_cmd(adb, serial, "shell", "getprop", "sys.boot_completed", check=False)
        if (result.stdout or "").strip() == "1":
            return
        time.sleep(2)
    raise TimeoutError("等待设备启动超时")


def get_device_abi(adb: Path, serial: str) -> str:
    result = adb_cmd(adb, serial, "shell", "getprop", "ro.product.cpu.abi")
    return (result.stdout or "").strip()


def install_apk(adb: Path, serial: str, apk: Path, reinstall: bool, apk_abis: Sequence[str]) -> None:
    device_abilist = get_device_abilist(adb, serial)
    hint = _abi_install_hint(apk_abis, device_abilist, serial)
    if hint:
        raise RuntimeError(hint)

    args = [str(adb), "-s", serial, "install"]
    if reinstall:
        args.append("-r")
    args.append(str(apk))
    sdk = adb_cmd(adb, serial, "shell", "getprop", "ro.build.version.sdk", check=False)
    sdk_level = (sdk.stdout or "").strip()
    print(
        f"Installing {apk.name} on {serial} "
        f"(sdk={sdk_level or '?'}, abilist={','.join(device_abilist)}) ..."
    )
    result = run_cmd(args, timeout=300, check=False)
    if result.returncode == 0:
        return
    detail = (result.stderr or result.stdout or "").strip()
    if "INSTALL_FAILED_NO_MATCHING_ABIS" in detail:
        raise RuntimeError(f"{detail}\n\n{_abi_install_hint(apk_abis, device_abilist, serial)}")
    if "INSTALL_FAILED_OLDER_SDK" in detail:
        raise RuntimeError(
            f"{detail}\n\n"
            "该 APK 要求 minSdk 31+，但带 armeabi-v7a 转译的模拟器目前只有 API 30 可用。\n"
            "可选方案：\n"
            "  1. 使用 ARM 真机\n"
            "  2. 向开发方要 arm64-v8a 或 x86_64 版本的 APK"
        )
    raise RuntimeError(f"命令失败: {' '.join(args)}\n{detail}")


def resolve_launcher_activity(adb: Path, serial: str, package_name: str) -> str:
    result = adb_cmd(
        adb,
        serial,
        "shell",
        "cmd",
        "package",
        "resolve-activity",
        "--brief",
        package_name,
        check=False,
    )
    lines = [line.strip() for line in (result.stdout or "").splitlines() if line.strip()]
    for line in reversed(lines):
        if "/" in line:
            return line.split("/", 1)[1]
    raise RuntimeError(f"无法解析 {package_name} 的启动 Activity")


def launch_app(adb: Path, serial: str, package_name: str, activity: str) -> None:
    if activity.startswith("."):
        component = f"{package_name}/{activity}"
    elif "/" in activity:
        component = activity
    else:
        component = f"{package_name}/{activity}"
    print(f"Launching {component} ...")
    adb_cmd(adb, serial, "shell", "am", "start", "-W", "-n", component)


def dump_ui_xml(adb: Path, serial: str, remote_path: str = "/sdcard/testapp_ui_dump.xml") -> str:
    adb_cmd(adb, serial, "shell", "uiautomator", "dump", remote_path)
    local_xml = ROOT / "dumps" / f"ui_dump_{int(time.time())}.xml"
    local_xml.parent.mkdir(parents=True, exist_ok=True)
    adb_cmd(adb, serial, "pull", remote_path, str(local_xml))
    return local_xml.read_text(encoding="utf-8", errors="replace")


def parse_ui_nodes(xml_text: str, package_filter: Optional[str] = None) -> List[UiNode]:
    root = ET.fromstring(xml_text)
    nodes: List[UiNode] = []
    for elem in root.iter("node"):
        package = elem.attrib.get("package", "")
        if package_filter and package and package != package_filter:
            continue
        node = UiNode(
            resource_id=elem.attrib.get("resource-id", ""),
            class_name=elem.attrib.get("class", ""),
            text=elem.attrib.get("text", ""),
            content_desc=elem.attrib.get("content-desc", ""),
            clickable=elem.attrib.get("clickable", "false") == "true",
            long_clickable=elem.attrib.get("long-clickable", "false") == "true",
            focusable=elem.attrib.get("focusable", "false") == "true",
            enabled=elem.attrib.get("enabled", "true") == "true",
            checkable=elem.attrib.get("checkable", "false") == "true",
            checked=elem.attrib.get("checked", "false") == "true",
            password=elem.attrib.get("password", "false") == "true",
            bounds=elem.attrib.get("bounds", ""),
            package=package,
        )
        if any(
            [
                node.resource_id,
                node.text,
                node.content_desc,
                node.clickable,
                node.long_clickable,
                node.focusable,
                "EditText" in node.class_name,
                "Button" in node.class_name,
            ]
        ):
            nodes.append(node)
    return nodes


def _node_line(node: UiNode) -> str:
    parts = [node.class_name.rsplit(".", 1)[-1]]
    if node.resource_id:
        parts.append(f"id={node.resource_id}")
    if node.text:
        parts.append(f'text="{node.text}"')
    if node.content_desc:
        parts.append(f'desc="{node.content_desc}"')
    flags = []
    if node.clickable:
        flags.append("clickable")
    if node.long_clickable:
        flags.append("long-clickable")
    if node.focusable:
        flags.append("focusable")
    if node.password:
        flags.append("password")
    if flags:
        parts.append("[" + ",".join(flags) + "]")
    if node.bounds:
        parts.append(f"bounds={node.bounds}")
    return " | ".join(parts)


def write_reports(
    output_dir: Path,
    package_name: str,
    apk: Path,
    xml_text: str,
    nodes: List[UiNode],
) -> Tuple[Path, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    xml_path = output_dir / f"{package_name}_{stamp}.xml"
    json_path = output_dir / f"{package_name}_{stamp}.json"
    txt_path = output_dir / f"{package_name}_{stamp}.txt"

    xml_path.write_text(xml_text, encoding="utf-8")

    resource_ids = sorted({node.resource_id for node in nodes if node.resource_id})
    voice_nodes = [node for node in nodes if node.voice_related]
    input_nodes = [node for node in nodes if "EditText" in node.class_name]
    button_nodes = [
        node
        for node in nodes
        if "Button" in node.class_name or (node.clickable and node.resource_id)
    ]

    payload = {
        "package": package_name,
        "apk": str(apk),
        "generated_at": stamp,
        "resource_ids": resource_ids,
        "voice_related": [asdict(node) for node in voice_nodes],
        "inputs": [asdict(node) for node in input_nodes],
        "clickables": [asdict(node) for node in button_nodes],
        "all_nodes": [asdict(node) for node in nodes],
    }
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        f"Package: {package_name}",
        f"APK: {apk.name}",
        f"Generated: {stamp}",
        "",
        "== Resource IDs ==",
    ]
    lines.extend(resource_ids or ["(none)"])
    lines.extend(["", "== Likely input fields (EditText) =="])
    lines.extend(_node_line(node) for node in input_nodes[:30] or [])
    if not input_nodes:
        lines.append("(none)")
    lines.extend(["", "== Likely buttons / clickables =="])
    lines.extend(_node_line(node) for node in button_nodes[:40])
    lines.extend(["", "== Voice-related candidates =="])
    lines.extend(_node_line(node) for node in voice_nodes)
    if not voice_nodes:
        lines.append("(none matched keywords; inspect full xml/json)")
    txt_path.write_text("\n".join(lines), encoding="utf-8")
    return json_path, txt_path


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Install target APK on Android emulator and dump UI element IDs.",
    )
    parser.add_argument(
        "--apk",
        help="APK path (default: first *.apk under android-emulator/)",
    )
    parser.add_argument(
        "--package",
        default="",
        help=f"Target package (default: parse from APK or {DEFAULT_PACKAGE})",
    )
    parser.add_argument(
        "--activity",
        default="",
        help="Launcher activity (default: parse from APK or resolve via adb)",
    )
    parser.add_argument(
        "--serial",
        help="Device serial (default: first emulator-*)",
    )
    parser.add_argument(
        "--no-start-emulator",
        action="store_true",
        help="Do not auto-start emulator when no device is online (default: auto start)",
    )
    parser.add_argument(
        "--skip-install",
        action="store_true",
        help="Skip APK install",
    )
    parser.add_argument(
        "--reinstall",
        action="store_true",
        help="Reinstall APK with adb install -r",
    )
    parser.add_argument(
        "--wait-sec",
        type=float,
        default=5.0,
        help="Seconds to wait after launch before UI dump",
    )
    parser.add_argument(
        "--output-dir",
        default=str(ROOT / "dumps"),
        help="Directory for xml/json/txt output",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    apk = pick_apk(args.apk)
    adb = find_adb()
    aapt = find_aapt()

    parsed_package, parsed_activity, apk_abis = parse_apk_info(apk, aapt)
    package_name = args.package.strip() or parsed_package
    activity = args.activity.strip() or parsed_activity

    if not args.no_start_emulator:
        start_emulator_if_needed(adb)

    serial = pick_device(adb, args.serial, apk_abis)
    print(f"Using device: {serial}")
    if apk_abis:
        print(f"APK ABIs : {', '.join(apk_abis)}")
        print(f"Device ABI list: {', '.join(get_device_abilist(adb, serial))}")
    wait_for_boot(adb, serial)

    if not args.skip_install:
        install_apk(adb, serial, apk, reinstall=args.reinstall, apk_abis=apk_abis)

    if not activity:
        activity = resolve_launcher_activity(adb, serial, package_name)

    launch_app(adb, serial, package_name, activity)
    if args.wait_sec > 0:
        print(f"Waiting {args.wait_sec}s for UI ...")
        time.sleep(args.wait_sec)

    xml_text = dump_ui_xml(adb, serial)
    nodes = parse_ui_nodes(xml_text, package_filter=package_name)
    json_path, txt_path = write_reports(
        Path(args.output_dir),
        package_name,
        apk,
        xml_text,
        nodes,
    )

    print("")
    print(f"Package : {package_name}")
    print(f"Activity: {activity}")
    print(f"Nodes   : {len(nodes)}")
    print(f"Report  : {txt_path}")
    print(f"JSON    : {json_path}")
    print("")
    print("Top resource IDs:")
    for resource_id in sorted({node.resource_id for node in nodes if node.resource_id})[:20]:
        print(f"  - {resource_id}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
