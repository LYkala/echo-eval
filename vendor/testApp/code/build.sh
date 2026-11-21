#!/bin/bash

# TestApp 构建脚本
# 仅编译 testapp 的 Debug APK 并拷贝到 output 目录

set -e

# 配置
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${PROJECT_DIR}/../output"
DATE=$(date +%Y%m%d)
VERSION="1.0.0"

echo "=========================================="
echo "TestApp 构建脚本 (testapp Debug)"
echo "版本: ${VERSION}"
echo "日期: ${DATE}"
echo "=========================================="

mkdir -p "${OUTPUT_DIR}"
cd "${PROJECT_DIR}"

if [ ! -f "gradlew" ]; then
    if command -v gradle &> /dev/null; then
        GRADLE_CMD="gradle"
    else
        echo "错误: 未找到 gradle 或 gradlew" >&2
        exit 1
    fi
else
    GRADLE_CMD="./gradlew"
    chmod +x gradlew
fi

echo ""
echo "清理 testapp 旧构建..."
${GRADLE_CMD} :testapp:clean

echo ""
echo "编译 testapp Debug APK..."
${GRADLE_CMD} :testapp:assembleDebug

echo ""
echo "拷贝 APK 到 output 目录..."

remove_old_apks() {
    local pattern="$1"
    rm -f "${OUTPUT_DIR}"/${pattern}*.apk
}

TESTAPP_APK=$(find testapp/build/outputs/apk/debug -name "*.apk" | head -1)
if [ -f "${TESTAPP_APK}" ]; then
    remove_old_apks "testapp-v${VERSION}-debug-"
    cp "${TESTAPP_APK}" "${OUTPUT_DIR}/testapp-v${VERSION}-debug-${DATE}.apk"
    echo "已拷贝: testapp-v${VERSION}-debug-${DATE}.apk"
else
    echo "错误: 未找到 testapp APK" >&2
    exit 1
fi

echo ""
echo "=========================================="
echo "构建完成！"
echo "APK 输出目录: ${OUTPUT_DIR}"
ls -la "${OUTPUT_DIR}"
echo "=========================================="
