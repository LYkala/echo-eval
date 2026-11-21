# Build DeviceSDK cloud Demo APK; output name: devicesdk-yyyyMMdd-HHmmss.apk
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$MobileApk = Join-Path $Root "MobileAPK"
$OutDir = Join-Path (Split-Path -Parent (Split-Path -Parent $Root)) "apks"
$CloudApkDir = Join-Path $OutDir "devicesdkdemo-cloud-v3"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ApkName = "devicesdk-$Stamp.apk"

$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "E:\Android\Sdk"

Write-Host "=== assembleRelease cloud-v3 ==="
Write-Host "output name: $ApkName"
Push-Location $MobileApk
try {
  & .\gradlew.bat :app:assembleRelease --no-daemon -x lint -x lintVitalRelease
  if ($LASTEXITCODE -ne 0) { throw "gradle failed: $LASTEXITCODE" }
} finally {
  Pop-Location
}

$built = Join-Path $MobileApk "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $built)) { throw "missing built apk: $built" }

$distDir = Join-Path $Root "dist"
New-Item -ItemType Directory -Force -Path $CloudApkDir, $OutDir, $distDir | Out-Null

# 主产物：devicesdk-日期时间.apk
$distApk = Join-Path $distDir $ApkName
$outApk = Join-Path $OutDir $ApkName
$managedApk = Join-Path $CloudApkDir $ApkName
Copy-Item $built $distApk -Force
Copy-Item $built $outApk -Force
Copy-Item $built $managedApk -Force

# 清理托管目录旧的 devicesdk-*.apk / devicesdkdemo.apk，只留本次
Get-ChildItem $CloudApkDir -Filter "devicesdk-*.apk" -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -ne $ApkName } |
  Remove-Item -Force
Remove-Item (Join-Path $CloudApkDir "devicesdkdemo.apk") -Force -ErrorAction SilentlyContinue
# 兼容旧部署脚本的稳定别名
Copy-Item $built (Join-Path $OutDir "DeviceSDK-Demo-cloud-v3.apk") -Force

$desk = [Environment]::GetFolderPath("Desktop")
Copy-Item $built (Join-Path $desk $ApkName) -Force

Write-Host "OK -> $distApk"
Write-Host "OK -> $outApk"
Write-Host "OK -> $managedApk  (software download)"
Write-Host "OK -> Desktop\$ApkName"
