# Voice Assistant App (方案 B)
#
# Sherpa ONNX ASR + Kimi LLM + Android TTS — 单 APK。
# 模型与 native 库从模拟器已安装的 Sherpa ASR APK 提取（无需访问 GitHub）。

param(
    [string]$Abi = "x86_64",
    [switch]$PullSherpaApk
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Downloads = Join-Path $Root "downloads"
$ProjectDir = Join-Path $Root "voice-assistant-app\project"
$OverlayDir = Join-Path $Root "voice-assistant-app\overlay"
$TemplateDir = Join-Path $Root "voice-assistant-app\template"
$SherpaApk = Join-Path $Downloads "sherpa-installed.apk"
$SherpaUnpack = Join-Path $Downloads "sherpa-apk-unpacked2"
$GradleHome = Join-Path $Root "voice-assistant-app\template\gradle\wrapper\gradle-wrapper.jar"
$GradleCacheJar = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.2-bin\*\gradle-8.2\lib\plugins\gradle-wrapper-8.2.jar" -ErrorAction SilentlyContinue | Select-Object -First 1

function Write-Step($msg) {
    Write-Host "`n=== $msg ===" -ForegroundColor Cyan
}

New-Item -ItemType Directory -Force -Path $Downloads | Out-Null

Write-Step "1/4 Pull Sherpa reference APK from emulator"
if ($PullSherpaApk -or -not (Test-Path $SherpaApk)) {
    $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (-not (Test-Path $adb)) {
        throw "adb not found. Install Sherpa ASR on emulator first, or place APK at $SherpaApk"
    }
    $pathLine = & $adb shell pm path com.k2fsa.sherpa.onnx 2>$null | Select-String "base.apk"
    if (-not $pathLine) {
        throw "com.k2fsa.sherpa.onnx not installed. Install Sherpa ASR APK on emulator first."
    }
    $remote = ($pathLine -replace "^package:", "").Trim()
    & $adb pull $remote $SherpaApk | Out-Host
}

Write-Step "2/4 Extract libs + ASR model"
python -c @"
import shutil, zipfile, pathlib
apk = pathlib.Path(r'$SherpaApk')
out = pathlib.Path(r'$SherpaUnpack')
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)
with zipfile.ZipFile(apk) as z:
    z.extractall(out)
libs = list((out / 'lib' / '$Abi').glob('*.so'))
if not libs:
    raise SystemExit('No native libs for ABI $Abi')
print('libs', [p.name for p in libs])
"@

Write-Step "3/4 Assemble project from template + overlay"
if (Test-Path $ProjectDir) {
    Remove-Item -Recurse -Force $ProjectDir
}
Copy-Item -Recurse -Force $TemplateDir $ProjectDir
Copy-Item -Recurse -Force (Join-Path $OverlayDir "app\*") (Join-Path $ProjectDir "app")

$JniDest = Join-Path $ProjectDir "app\src\main\jniLibs\$Abi"
New-Item -ItemType Directory -Force -Path $JniDest | Out-Null
Copy-Item -Force (Join-Path $SherpaUnpack "lib\$Abi\*.so") $JniDest

$AssetsDest = Join-Path $ProjectDir "app\src\main\assets"
New-Item -ItemType Directory -Force -Path $AssetsDest | Out-Null
Copy-Item -Recurse -Force (Join-Path $SherpaUnpack "assets\*") $AssetsDest

$WrapperDest = Join-Path $ProjectDir "gradle\wrapper\gradle-wrapper.jar"
if ($GradleCacheJar) {
    Copy-Item -Force $GradleCacheJar.FullName $WrapperDest
    Write-Host "gradle-wrapper.jar <- $($GradleCacheJar.FullName)"
} elseif (Test-Path $GradleHome) {
    Copy-Item -Force $GradleHome $WrapperDest
} else {
    Write-Host "gradle-wrapper.jar missing; build script will use cached Gradle directly." -ForegroundColor Yellow
}

Write-Step "4/4 local.properties"
$sdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdk)) {
    $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
"sdk.dir=$($sdk -replace '\\','/')" | Out-File -FilePath (Join-Path $ProjectDir "local.properties") -Encoding ascii

Write-Host "`nReady: $ProjectDir" -ForegroundColor Green
Write-Host "Build: .\scripts\build_voice_assistant_app.ps1"
