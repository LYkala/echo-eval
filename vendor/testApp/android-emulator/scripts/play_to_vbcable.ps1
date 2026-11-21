# Play a wav/mp3 file into VB-Cable Input (for emulator mic injection).
param(
    [Parameter(Mandatory = $true)][string]$AudioPath,
    [switch]$TemporaryDefaultPlayback
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "vbcable_utils.ps1")

$resolved = (Resolve-Path -LiteralPath $AudioPath).Path
if ($resolved -match '\.mp3$') {
    $ffmpeg = Find-Ffmpeg
    if (-not $ffmpeg) {
        throw "mp3 requires ffmpeg; use wav or install ffmpeg"
    }
    $tmp = Join-Path $env:TEMP ("vbcable_" + [guid]::NewGuid().ToString("n") + ".wav")
    & $ffmpeg -hide_banner -loglevel error -y -i $resolved $tmp
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg wav convert failed" }
    try {
        Play-WavToCableInput -AudioPath $tmp -TemporaryDefaultPlayback:$TemporaryDefaultPlayback
    } finally {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
} else {
    Play-WavToCableInput -AudioPath $resolved -TemporaryDefaultPlayback:$TemporaryDefaultPlayback
}
