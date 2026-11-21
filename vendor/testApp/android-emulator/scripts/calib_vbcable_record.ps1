# Verify: play wav into CABLE Input while recording CABLE Output via ffmpeg dshow.
param(
    [string]$OutWav = (Join-Path $PSScriptRoot "..\app_apk_test\_calib_record.wav"),
    [double]$Seconds = 5
)
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot "vbcable_utils.ps1")
$sample = Join-Path $Root "audio_samples\weather_question.wav"
$ffmpeg = Find-Ffmpeg
if (-not $ffmpeg) { throw "ffmpeg not found" }

Ensure-AudioDeviceCmdlets
Save-DefaultAudioDevices
try {
    $dev = Get-CableDeviceNames
    $pb = Get-AudioDevice -List | Where-Object { $_.Type -eq "Playback" -and $_.Name -eq $dev.Playback } | Select-Object -First 1
    Set-AudioDevice -Index $pb.Index
    $cap = "audio=$($dev.Capture)"
    Write-Host "Playback -> $($dev.Playback)"
    Write-Host "Capture  -> $cap"

    $playJob = Start-Job -ScriptBlock {
        param($script, $wav)
        . $script
        Start-Sleep -Seconds 1
        Play-WavToCableInput -AudioPath $wav -TemporaryDefaultPlayback
    } -ArgumentList (Join-Path $PSScriptRoot "vbcable_utils.ps1"), $sample

    & $ffmpeg -hide_banner -loglevel error -y -f dshow -i "$cap" -t $Seconds $OutWav
    Wait-Job $playJob | Out-Null
    Receive-Job $playJob
    Remove-Job $playJob -Force
    Write-Host "Wrote $OutWav ($((Get-Item $OutWav).Length) bytes)"
} finally {
    Restore-DefaultAudioDevices
}
