# Record emulator TTS playback from host speakers (WASAPI loopback via ffmpeg).
param(
    [Parameter(Mandatory = $true)][string]$OutWav,
    [double]$DurationSec = 25.0
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "vbcable_utils.ps1")
$ffmpeg = Find-Ffmpeg
if (-not $ffmpeg) {
    throw "Loopback record needs ffmpeg. Install: winget install Gyan.FFmpeg"
}
$devices = & $ffmpeg -hide_banner -list_devices true -f wasapi -i dummy 2>&1 | Out-String
$device = $null
foreach ($line in $devices -split "`n") {
    if ($line -match '"(.+ \(loopback\))"') { $device = $Matches[1]; break }
}
if (-not $device) {
    foreach ($line in $devices -split "`n") {
        if ($line -match 'loopback' -and $line -match '"([^"]+)"') { $device = $Matches[1]; break }
    }
}
if (-not $device) { throw "No WASAPI loopback device found" }
Write-Host "Recording ${DurationSec}s from: $device"
& $ffmpeg -hide_banner -loglevel error -y -f wasapi -i $device -t $DurationSec $OutWav
if ($LASTEXITCODE -ne 0) { throw "ffmpeg record failed ($LASTEXITCODE)" }
