from __future__ import annotations
import os, shutil
from pathlib import Path

lab = Path(__file__).resolve().parent
out = lab / "offline-deploy"
sdk = out / "sdk"
sdk.mkdir(parents=True, exist_ok=True)

q = out / "emulator" / "qemu-system-aarch64-headless.pristine"
if q.exists():
    print("remove incomplete", q.name, q.stat().st_size)
    q.unlink()

downloads = lab / "downloads"
needed = [
    "arm64-v8a-33_r08.zip",
    "platform-33-ext3_r03.zip",
    "commandlinetools-linux-latest.zip",
    "OpenJDK17U-jdk_aarch64_linux.tar.gz",
    "aarch64-toolchain.tar.bz2",
    "android-sdk-tools-static-aarch64.zip",
]
map_lines = [
    "# SDK / system image caches (hardlink into sdk/)",
    "# Do NOT use downloads/emulator-linux_aarch64.zip if tiny/stub",
]
for name in needed:
    src = downloads / name
    dst = sdk / name
    if not src.exists():
        print("MISSING", src)
        map_lines.append(f"{name}=MISSING:{src}")
        continue
    if dst.exists() and dst.stat().st_size == src.stat().st_size:
        print("sdk exists", name, dst.stat().st_size)
    else:
        if dst.exists():
            dst.unlink()
        try:
            os.link(src, dst)
            print("hardlink", name)
        except OSError as e:
            print("hardlink fail", e, "-> copy")
            shutil.copy2(src, dst)
            print("copied", name)
    map_lines.append(f"{name}=sdk/{name}  (source ../downloads/{name}, size={src.stat().st_size})")

map_lines += [
    "emulator-linux_aarch64-built.tar.gz=emulator/emulator-linux_aarch64-built.tar.gz",
    "emu-runtime-extra.tar.gz=emulator/emu-runtime-extra.tar.gz",
    "glibc-bundle.tar.gz=emulator/glibc-bundle.tar.gz",
    "linux-aarch64-deps.tar.gz=emulator/linux-aarch64-deps.tar.gz",
    "vulkan-pack.tar.gz=emulator/vulkan-pack.tar.gz",
    "aisf_arm64_signed.apk=apk/aisf_arm64_signed.apk",
    "pulse-aarch64-libs.tar.gz=runtime/pulse-aarch64-libs.tar.gz",
    "aisf-runtime-scripts.tgz=runtime/aisf-runtime-scripts.tgz",
    "mini_adb_auth.py=runtime/mini_adb_auth.py",
    "libandroid-emu-metrics.so.*=runtime/",
]
(out / "LOCAL_CACHE_MAP.txt").write_text("\n".join(map_lines) + "\n", encoding="utf-8")

readme = """# AISF ARM64 Emulator - Offline Deploy Cache

本目录缓存 ARM64 Android 模拟器部署依赖，后续换机/重装可直接从本地推送，无需再交叉编译或从公网拉取。

## 目录

```
offline-deploy/
  emulator/   # 交叉编译产物、glibc、运行时 extra
  runtime/    # PulseAudio aarch64 库、mini_adb、metrics、脚本
  apk/        # arm64 AISF APK
  sdk/        # 系统镜像 / cmdline-tools / JDK（与 downloads/ 硬链接）
  LOCAL_CACHE_MAP.txt
  INVENTORY.txt
  README.md
```

## 必需要素

| 组件 | 本地路径 | 说明 |
|------|----------|------|
| 模拟器包 | `emulator/emulator-linux_aarch64-built.tar.gz` | 真实 headless qemu (~391MB) |
| 运行时附件 | `emulator/emu-runtime-extra.tar.gz` | ~185MB |
| glibc | `emulator/glibc-bundle.tar.gz` | 目标 glibc < 2.38 时必需 |
| aarch64 deps | `emulator/linux-aarch64-deps.tar.gz` | |
| 系统镜像 | `sdk/arm64-v8a-33_r08.zip` | Android 33 google_apis arm64 (~1.5GB) |
| Platform | `sdk/platform-33-ext3_r03.zip` | |
| cmdline-tools | `sdk/commandlinetools-linux-latest.zip` | |
| JDK aarch64 | `sdk/OpenJDK17U-jdk_aarch64_linux.tar.gz` | |
| APK | `apk/aisf_arm64_signed.apk` | 含 arm64-v8a .so |
| Pulse 库 | `runtime/pulse-aarch64-libs.tar.gz` | VirtualMic 依赖 |
| adb 兜底 | `runtime/mini_adb_auth.py` | 替代会 fdsan abort 的官方 adb |
| 脚本包 | `runtime/aisf-runtime-scripts.tgz` | setup_virtual_mic 等 |

## 新环境部署顺序（概要）

1. 解压 `emulator-linux_aarch64-built.tar.gz` 到 `$LAB/sdk/emulator`
2. 解压 `glibc-bundle` / `emu-runtime-extra` / 需要时 `linux-aarch64-deps`
3. 解压系统镜像到 `system-images/android-33/google_apis/arm64-v8a`
4. 解压 cmdline-tools、JDK；创建 AVD `AISF_EulerOS_ARM64`
5. 安装 Pulse 库到 `/usr/local/lib64`，跑 `setup_virtual_mic.sh`
6. 启动配方（保持此顺序）：
   - 从 `.bak` 恢复 qemu → 先 `patchelf --force-rpath` + bundled `ld-linux-aarch64.so.1`
   - 再 virtio CBZ→B 补丁（VA `0x5fea04`）
   - wrapper 必须 `unset LD_LIBRARY_PATH`
   - 启动加 `-no-snapshot -no-snapshot-load -no-snapshot-save`；语音场景不要加 `-audio none`
7. 用 `mini_adb_auth.py` 替换/包装 adb；安装 `apk/aisf_arm64_signed.apk`
8. 目标无公网时：跳板 CONNECT 代理 + `ssh -R 127.0.0.1:8888` + emulator `-http-proxy`

## 注意

- `../downloads/emulator-linux_aarch64.zip` 若为 stub/极小文件，勿用；只用本目录 `built.tar.gz`
- 勿把 bundled glibc 放进 `LD_LIBRARY_PATH`
- DDS 语音云凭证与本离线包无关；语音 E2E 仍需有效 productId/apiKey
- 云服务器密码文件不要提交到 git

## 刷新缓存

```powershell
cd d:\\pythonProj\\MyGUITest\\TestAppGUI\\testApp\\android-emulator\\aisf-voice-lab
python _pull_offline_deps.py
```

跳板已存在且大小一致的文件会自动 skip。
"""
(out / "README.md").write_text(readme, encoding="utf-8")

lines = []
total = 0
for p in sorted(out.rglob("*")):
    if p.is_file():
        sz = p.stat().st_size
        total += sz
        lines.append(f"{p.relative_to(out)}\t{sz}")
(out / "INVENTORY.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
print("TOTAL_BYTES", total)
print("TOTAL_GB", round(total / 1024 / 1024 / 1024, 2))
print("files", len(lines))
for L in lines:
    print(L)
