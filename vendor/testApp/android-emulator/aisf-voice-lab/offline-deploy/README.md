# AISF ARM64 Emulator - Offline Deploy Cache

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
cd d:\pythonProj\MyGUITest\TestAppGUI\testApp\android-emulator\aisf-voice-lab
python pull_offline_deps.py
```

跳板已存在且大小一致的文件会自动 skip。
