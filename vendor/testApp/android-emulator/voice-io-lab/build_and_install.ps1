param(
    [string]$Abi = "x86_64",
    [string]$Serial = "emulator-5554",
    [switch]$SkipPrepare,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$LabDir = $PSScriptRoot
$ApkDir = Join-Path $LabDir "apk"
$Scripts = Join-Path $Root "scripts"

. (Join-Path $Scripts "env.ps1")

New-Item -ItemType Directory -Force -Path $ApkDir | Out-Null

Write-Host "=== voice-io-lab: build & install ($Abi) ===" -ForegroundColor Cyan

if (-not $SkipPrepare) {
    & (Join-Path $Scripts "prepare_voice_assistant_app.ps1") -Abi $Abi
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not $SkipBuild) {
    & (Join-Path $Scripts "build_voice_assistant_app.ps1") -Abi $Abi
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$srcApk = Join-Path $Root "voice-assistant-app\VoiceAssistant-debug-$Abi.apk"
$destApk = Join-Path $ApkDir "VoiceAssistant-debug-$Abi.apk"
if (-not (Test-Path $srcApk)) {
    throw "APK not found: $srcApk"
}
Copy-Item -Force $srcApk $destApk
Write-Host "APK copied -> $destApk" -ForegroundColor Green

if ($SkipInstall) {
    Write-Host "Skip install (-SkipInstall)" -ForegroundColor Yellow
    exit 0
}

$devices = (& $script:Adb devices) | Select-String "device$"
if (-not $devices) {
    Write-Host "No emulator; starting AVD..." -ForegroundColor Yellow
    & (Join-Path $Scripts "start-emulator.ps1")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "Installing on $Serial ..."
& $script:Adb -s $Serial install -r $destApk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$pkg = "com.testappgui.voiceassistant"
& $script:Adb -s $Serial shell pm grant $pkg android.permission.RECORD_AUDIO 2>$null
& $script:Adb -s $Serial shell appops set $pkg RECORD_AUDIO allow 2>$null
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& $script:Adb -s $Serial shell pm disable-user --user 0 com.k2fsa.sherpa.onnx *> $null
$ErrorActionPreference = $prevEap

Write-Host "Install OK: $destApk" -ForegroundColor Green
