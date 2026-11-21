#!/bin/bash
# TestApp 权限预授权脚本
# 通过 ADB 预先开启悬浮窗权限，减少用户手动操作

echo "=== TestApp 权限预授权脚本 ==="
echo ""

# 检查设备连接
echo "检查设备连接..."
DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    echo "错误: 未检测到已连接的设备"
    echo "请确保:"
    echo "  1. 设备已通过 USB 连接"
    echo "  2. 设备已开启 USB 调试模式"
    echo "  3. 已授权此电脑进行调试"
    exit 1
fi

echo "检测到 $DEVICES 台设备"
echo ""

# 设置包名
PACKAGE_NAME="com.example.testapp"

# 1. 授予悬浮窗权限
echo "正在授予悬浮窗权限..."
adb shell appops set $PACKAGE_NAME SYSTEM_ALERT_WINDOW allow
echo "悬浮窗权限已授予"
echo ""

# 3. 尝试通过 ADB 启用无障碍服务
echo "正在尝试启用无障碍服务..."
SERVICE_NAME="$PACKAGE_NAME/com.example.testapp.service.TestAccessibilityService"

# 方法1: 使用 settings 命令 (需要 root 或系统应用权限)
adb shell "settings put secure enabled_accessibility_services $SERVICE_NAME" 2>/dev/null
adb shell "settings put secure accessibility_enabled 1" 2>/dev/null

# 检查是否成功
ENABLED=$(adb shell "settings get secure enabled_accessibility_services" 2>/dev/null | grep -o "$PACKAGE_NAME")
if [ -n "$ENABLED" ]; then
    echo "无障碍服务已通过 ADB 启用"
else
    echo "提示: 无障碍服务需要手动开启 (设备限制)"
fi
echo ""

# 4. 提示用户
echo "==========================================="
echo "权限设置状态"
echo "==========================================="
echo "✓ 悬浮窗权限: 已授予"
echo "○ 无障碍服务: 请按以下步骤手动开启"
echo ""
echo "手动开启步骤:"
echo "  1. 在设备上打开 testapp"
echo "  2. 点击弹出的'去开启'按钮"
echo "  3. 找到 'TestApp Accessibility Service' 并开启"
echo ""

read -p "是否立即打开无障碍设置页面? (y/n): " choice
if [ "$choice" = "y" ] || [ "$choice" = "Y" ]; then
    echo "正在打开无障碍设置页面..."
    adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
    echo "已打开无障碍设置页面，请在设备上手动开启服务"
fi

echo ""
echo "=== 完成 ==="