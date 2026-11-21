param(
    [string]$Abi = "x86_64",
    [string]$Serial = "emulator-5554",
    [string]$InputWav = "",
    [switch]$SkipPrepare,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$SkipVbConfigure
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$LabDir = $PSScriptRoot
$RunsDir = Join-Path $LabDir "runs"
$Scripts = Join-Path $Root "scripts"

if (-not $InputWav) {
    $InputWav = Join-Path $Root "audio_samples\weather_question.wav"
}
$InputWav = (Resolve-Path $InputWav).Path

New-Item -ItemType Directory -Force -Path $RunsDir | Out-Null

Write-Host "=== voice-io-lab E2E ===" -ForegroundColor Cyan
Write-Host "Input wav: $InputWav"

& (Join-Path $LabDir "build_and_install.ps1") -Abi $Abi -Serial $Serial -SkipPrepare:$SkipPrepare -SkipBuild:$SkipBuild -SkipInstall:$SkipInstall
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $SkipVbConfigure) {
    Write-Host "`n=== Configure VB-Cable routing ===" -ForegroundColor Cyan
    & (Join-Path $Scripts "setup_virtual_mic.ps1") -ConfigureOnly
    if ($LASTEXITCODE -ne 0) {
        Write-Host "VB-Cable configure failed. Install driver first:" -ForegroundColor Yellow
        Write-Host "  ..\scripts\setup_virtual_mic.ps1"
        exit $LASTEXITCODE
    }
}

Write-Host "`n=== Run voice I/O test (--real-mic) ===" -ForegroundColor Cyan
Push-Location $Root
try {
    python (Join-Path $Root "test_voice_assistant_apk.py") --real-mic -i $InputWav
    $testExit = $LASTEXITCODE
} finally {
    Pop-Location
}

# Move latest app_apk_test output into voice-io-lab/runs/
$latest = Get-ChildItem (Join-Path $Root "app_apk_test") -Directory -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($latest) {
    $dest = Join-Path $RunsDir $latest.Name
    if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
    Move-Item -Force $latest.FullName $dest
    Write-Host "`nResults -> $dest" -ForegroundColor Green
    $resultJson = Join-Path $dest "result.json"
    if (Test-Path $resultJson) {
        Get-Content $resultJson -Raw | Write-Host
    }
}

exit $testExit
