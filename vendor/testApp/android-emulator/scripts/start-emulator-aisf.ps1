# 启动适用于 AISF（armeabi-v7a）APK 的模拟器
# 说明：Windows 不能跑纯 ARM 镜像，此 AVD 为 x86_64 + ARM 转译（API 30）
$env:ANDROID_AVD_NAME = "TestAppGUI_API30_ARMTRANS"
& "$PSScriptRoot\start-emulator.ps1"
