# 鸿蒙版 TestApp / DeviceSDK Demo

## 现状（请先读）

仓库里已有两个 **HarmonyOS NEXT 工程脚手架**：

| 工程 | 路径 | bundleName | 当前能力 |
|------|------|------------|----------|
| TestApp 鸿蒙 | `code/harmonyos/TestAppOhos` | `com.example.testapp.ohos` | 连 `ws://…:9526/ws/device`、register、heartbeat |
| DeviceSDK 鸿蒙 | `code/harmonyos/DeviceSDKDemoOhos` | `com.example.devicesdkdemo.ohos` | 同上（`clientApp=devicesdkdemo`） |

**还不能直接给出可安装的 `.hap`**，原因：

1. 本机 / 跳板机 **没有** 支持鸿蒙 6 的 **DevEco Studio 5.0.3+ / 6.x**（winget 里的 DevEco 是 2023 的 3.1，编不了鸿蒙 6）。
2. 这两个脚手架 **没有** 移植安卓无障碍、AISF DeviceSDK、跨 App GUI 执行链路；收到 `execute_test` 只会回失败说明。
3. APK **不能** 转成 HAP；必须用 DevEco 重新编译签名。

## 你需要做的（出安装包）

1. 从 [华为开发者联盟 - DevEco Studio](https://developer.huawei.com/consumer/cn/download/deveco-studio) 下载并安装 **DevEco Studio 5.0.3+ 或 6.x**（勾选 HarmonyOS SDK / API 12+）。
2. 用 DevEco **Open** 上述两个工程目录。
3. 配置自动签名（File → Project Structure → Signing Configs）。
4. Build → Build Hap(s)/APP(s) → Build Hap(s)。
5. 产物一般在：
   - `entry/build/default/outputs/default/*.hap`
6. 用已装好的 hdc 安装：

```text
E:\TestAPPGUI\testApp\webserverV2\tools\hdc\bin\hdc.exe -t <设备序列号> install -r path\to\xxx.hap
```

## 装好后预期

- 能在 9526「设备」里看到鸿蒙机上线（WS 注册）。
- **还不能** 跑优酷/爱奇艺 GUI 用例，直到补齐：
  - 鸿蒙无障碍 / 屏幕操控能力
  - DeviceSDK / AISF 鸿蒙侧能力（若官方有 HAP/SDK）
  - `execute_test` 执行与截图上报

## hdc

本机已就绪：`webserverV2/tools/hdc/bin/hdc.exe`
