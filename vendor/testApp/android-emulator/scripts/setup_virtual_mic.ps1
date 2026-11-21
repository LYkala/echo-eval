# One-time VB-Cable setup + routing + optional loopback test.
param(
    [switch]$SkipDownload,
    [switch]$ConfigureOnly,
    [switch]$TestPlay,
    [switch]$RestoreDefaults
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot "vbcable_utils.ps1")

$url = "https://download.vb-audio.com/Download_CABLE/VBCABLE_Driver_Pack43.zip"
$dest = Join-Path $Root "downloads\vbcable"
$zip = Join-Path $dest "VBCABLE_Driver_Pack43.zip"
$sample = Join-Path $Root "audio_samples\weather_question.wav"

Write-Host "=== VB-Cable virtual microphone (emulator inject) ==="
Write-Host "Route: wav -> CABLE Input -> CABLE Output -> hostmicon -> emulator ASR"
Write-Host ""

if ($RestoreDefaults) {
    Restore-DefaultAudioDevices
    Write-Host "Restored previous default audio devices."
    exit 0
}

if (-not (Test-VBCableInstalled)) {
    Write-Host "[!] VB-Cable devices not found"
    if (-not $SkipDownload) {
        New-Item -ItemType Directory -Force -Path $dest | Out-Null
        if (-not (Test-Path $zip)) {
            Write-Host "Downloading VB-Cable ..."
            Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        }
        Write-Host "ZIP: $zip"
    }
    if (-not (Test-Path $zip)) {
        Write-Host "Missing zip: $zip (run without -SkipDownload)"
        exit 1
    }
    $extractDir = Join-Path $dest "full"
    try {
        Install-VBCableSilent -ZipPath $zip -ExtractDir $extractDir | Out-Null
    } catch {
        Write-Host "[fail] $($_.Exception.Message)"
        Write-Host "Manual: extract $zip and run VBCABLE_Setup_x64.exe as Administrator"
        exit 1
    }
}

Write-Host "[ok] VB-Cable installed"
$dev = Get-CableDeviceNames
Write-Host "  Playback: $($dev.Playback)"
Write-Host "  Capture : $($dev.Capture)"
Write-Host ""

if ($ConfigureOnly -or $TestPlay -or (-not $ConfigureOnly -and -not $TestPlay)) {
    Write-Host "Setting default microphone ..."
    try {
        Set-VBCableDefaultMicrophone | Out-Null
        Write-Host "[ok] Default mic = CABLE Output"
    } catch {
        Write-Host "[fail] $($_.Exception.Message)"
        Write-Host "Manual: Settings -> System -> Sound -> Input -> CABLE Output"
        exit 1
    }
}

if ($TestPlay) {
    if (-not (Test-Path $sample)) {
        Write-Host "Missing sample wav: $sample"
        exit 1
    }
    Write-Host ""
    Test-VBCableLoopback -AudioPath $sample
}

Write-Host ""
Write-Host "Next:"
Write-Host "  .\scripts\configure_vbcable_routing.ps1"
Write-Host "  .\scripts\play_to_vbcable.ps1 -AudioPath .\audio_samples\weather_question.wav"
Write-Host "  python test_voice_assistant_apk.py --real-mic"
Write-Host "  python voice_app_loop.py -i audio_samples/weather_question.wav"
Write-Host ""
Write-Host "Restore defaults: .\scripts\setup_virtual_mic.ps1 -RestoreDefaults"
