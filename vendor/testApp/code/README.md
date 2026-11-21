# TestApp 项目构建说明

## 项目结构

```
code/
├── testapp/              # 测试控制器应用
├── appforfunction/       # 被测应用
├── build.sh              # 构建脚本
├── version.properties    # 版本配置
└── .gitignore
```

## 环境要求

- **Gradle 8.2**（本地已有即可，无需下载 8.7.2）
- **Gradle JDK 必须为 17**（项目已指向 PyCharm 自带 JBR 17，勿用 Android Studio 的 21）
- Android SDK (API 34)
- 须用 **testApp/code** 作为项目根目录打开

## Android Studio Sync 失败

1. 打开目录必须是 **`testApp/code`**（不要打开 `testapp` 子目录）。
2. **Settings → Gradle → Gradle JDK** 选 **PyCharm JBR 17**（路径：`D:\Program Files\JetBrains\PyCharm 2024.1.4\jbr`）。
3. 不要用 Gradle 8.2 + JDK 21 组合。
4. 仍失败：**Invalidate Caches and Restart** 后再 Sync。

## 快速构建

### 方式一：使用构建脚本（推荐）

```bash
cd code
chmod +x build.sh
./build.sh
```

构建完成后，APK 文件将输出到 `output/` 目录：
- `testapp-v1.0.0-debug-{日期}.apk`
- `testapp-v1.0.0-release-{日期}.apk`
- `appforfunction-v1.0.0-debug-{日期}.apk`
- `appforfunction-v1.0.0-release-{日期}.apk`

### 方式二：手动构建

```bash
cd code

# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

APK 位置：
- `testapp/build/outputs/apk/debug/testapp-v1.0.0-debug-{日期}.apk`
- `appforfunction/build/outputs/apk/debug/appforfunction-v1.0.0-debug-{日期}.apk`

## 版本管理

修改 `version.properties` 文件中的版本号，然后同步更新对应模块的 `build.gradle`：

```properties
TESTAPP_VERSION_CODE=1
TESTAPP_VERSION_NAME=1.0.0
```

## 安装测试

```bash
# 安装 testapp
adb install output/testapp-v1.0.0-debug-{日期}.apk

# 安装 appforfunction
adb install output/appforfunction-v1.0.0-debug-{日期}.apk
```

## 清理构建

```bash
./gradlew clean
```