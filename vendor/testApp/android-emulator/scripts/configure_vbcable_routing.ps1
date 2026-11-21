# Set CABLE Output as Windows default microphone (for adb emu avd hostmicon).
param(
    [switch]$Restore
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "vbcable_utils.ps1")

if ($Restore) {
    Restore-DefaultAudioDevices
    Write-Host "Restored previous default audio devices."
    exit 0
}

if (-not (Test-VBCableInstalled)) {
    Write-Host "VB-Cable not found. Run: .\scripts\setup_virtual_mic.ps1"
    exit 1
}

Set-VBCableDefaultMicrophone | Out-Null
Write-Host ""
Write-Host "Then:"
Write-Host "  adb emu avd hostmicon"
Write-Host "  .\scripts\play_to_vbcable.ps1 -AudioPath .\audio_samples\weather_question.wav"
