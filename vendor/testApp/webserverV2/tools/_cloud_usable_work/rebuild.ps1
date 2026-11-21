# Rebuild cloud APK (isolated from v25 usable tree)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkDir = "e:\TestAPPGUI\testApp\local_device_gui_runner\apks"
$mobile = Join-Path $root "MobileAPK"
$outName = "DeviceSDK-Demo-cloud.apk"

$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "E:\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "=== assembleRelease (cloud v30, v25 + TestReply only) ==="
Push-Location $mobile
try {
    cmd /c "gradlew.bat :app:assembleRelease --no-daemon -x lint -x lintVitalRelease"
    if ($LASTEXITCODE -ne 0) { throw "gradle failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}

$built = Join-Path $mobile "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $built)) {
    throw "APK not found: $built"
}

New-Item -ItemType Directory -Force -Path $apkDir | Out-Null
$dest = Join-Path $apkDir $outName
$cloudFolder = Join-Path $apkDir "cloud"
New-Item -ItemType Directory -Force -Path $cloudFolder | Out-Null
$destKeep = Join-Path $cloudFolder $outName
Copy-Item $built $dest -Force
Copy-Item $built $destKeep -Force
Write-Host "OK: $dest"
Write-Host "OK: $destKeep"
Write-Host ("Size: {0:N0} bytes" -f (Get-Item $dest).Length)
