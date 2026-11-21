# VB-Audio Virtual Cable helpers for emulator mic injection.
# Route: wav -> CABLE Input -> CABLE Output (default mic) -> adb emu avd hostmicon

$ErrorActionPreference = "Stop"

$script:VBCableCaptureName = "CABLE Output"
$script:VBCablePlaybackName = "CABLE Input"
$script:AudioStateFile = Join-Path $env:TEMP "vbcable_audio_defaults.json"

function Find-Ffmpeg {
    $root = Split-Path $PSScriptRoot -Parent
    $candidates = @(
        (Join-Path $root "tools\conda-ffmpeg\Library\bin\ffmpeg.exe"),
        (Get-Command ffmpeg -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
        (Join-Path $root "tools\ffmpeg\ffmpeg.exe"),
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffmpeg.exe",
        "C:\ffmpeg\bin\ffmpeg.exe"
    ) | Where-Object { $_ -and (Test-Path $_) }
    return $candidates | Select-Object -First 1
}

function Get-CableDeviceNames {
    $endpoints = @()
    try {
        $endpoints = Get-PnpDevice -Class AudioEndpoint -Status OK -ErrorAction SilentlyContinue |
            ForEach-Object { $_.FriendlyName } |
            Where-Object { $_ -match "CABLE" }
    } catch {
        $endpoints = @()
    }
    $capture = $endpoints | Where-Object { $_ -like "*$($script:VBCableCaptureName)*" } | Select-Object -First 1
    $playback = $endpoints | Where-Object { $_ -like "*$($script:VBCablePlaybackName)*" } | Select-Object -First 1
    [PSCustomObject]@{
        Capture = $capture
        Playback = $playback
        All = $endpoints
    }
}

function Test-VBCableInstalled {
    $dev = Get-CableDeviceNames
    return [bool]($dev.Capture -and $dev.Playback)
}

function Ensure-AudioDeviceCmdlets {
    if (-not (Get-Module -ListAvailable -Name AudioDeviceCmdlets)) {
        Write-Host "Installing AudioDeviceCmdlets (CurrentUser) ..."
        if (-not (Get-PackageProvider -Name NuGet -ListAvailable -ErrorAction SilentlyContinue)) {
            Install-PackageProvider -Name NuGet -MinimumVersion 2.8.5.201 -Force -Scope CurrentUser -Confirm:$false | Out-Null
        }
        Set-PSRepository -Name PSGallery -InstallationPolicy Trusted -ErrorAction SilentlyContinue
        Install-Module -Name AudioDeviceCmdlets -Scope CurrentUser -Force -AllowClobber -Confirm:$false -ErrorAction Stop
    }
    Import-Module AudioDeviceCmdlets -ErrorAction Stop
}

function Install-VBCableSilent {
    param(
        [string]$ZipPath,
        [string]$ExtractDir
    )
    if (Test-VBCableInstalled) {
        Write-Host "[ok] VB-Cable already installed"
        return $true
    }
    if (-not (Test-Path -LiteralPath $ZipPath)) {
        throw "VB-Cable zip not found: $ZipPath"
    }
    New-Item -ItemType Directory -Force -Path $ExtractDir | Out-Null
    Expand-Archive -LiteralPath $ZipPath -DestinationPath $ExtractDir -Force
    $setup = Join-Path $ExtractDir "VBCABLE_Setup_x64.exe"
    if (-not (Test-Path -LiteralPath $setup)) {
        throw "Missing installer: $setup"
    }
    Write-Host "Installing VB-Cable (admin, silent) ..."
    $proc = Start-Process -FilePath $setup -ArgumentList "-i", "-h" -Wait -PassThru -Verb RunAs
    if ($proc.ExitCode -ne 0) {
        throw "VB-Cable installer exit code $($proc.ExitCode)"
    }
    Start-Sleep -Seconds 2
    if (-not (Test-VBCableInstalled)) {
        throw "VB-Cable install finished but devices not detected (reboot may be required)"
    }
    Write-Host "[ok] VB-Cable installed"
    return $true
}

function Save-DefaultAudioDevices {
    Ensure-AudioDeviceCmdlets
    $playback = Get-AudioDevice -Playback
    $recording = Get-AudioDevice -Recording
    @{
        playback_id = $playback.Index
        playback_name = $playback.Name
        recording_id = $recording.Index
        recording_name = $recording.Name
    } | ConvertTo-Json | Set-Content -LiteralPath $script:AudioStateFile -Encoding UTF8
}

function Restore-DefaultAudioDevices {
    if (-not (Test-Path $script:AudioStateFile)) { return }
    Ensure-AudioDeviceCmdlets
    $saved = Get-Content -LiteralPath $script:AudioStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($null -ne $saved.playback_id) {
        Set-AudioDevice -Index $saved.playback_id -ErrorAction SilentlyContinue
    }
    if ($null -ne $saved.recording_id) {
        Set-AudioDevice -Index $saved.recording_id -ErrorAction SilentlyContinue
    }
}

function Set-VBCableDefaultMicrophone {
    Ensure-AudioDeviceCmdlets
    $dev = Get-CableDeviceNames
    if (-not $dev.Capture) {
        throw "CABLE Output device not found. Install VB-Cable first."
    }
    Save-DefaultAudioDevices
    $rec = Get-AudioDevice -List | Where-Object {
        $_.Type -eq "Recording" -and $_.Name -eq $dev.Capture
    } | Select-Object -First 1
    if (-not $rec) {
        throw "Recording device not found: $($dev.Capture)"
    }
    Set-AudioDevice -Index $rec.Index
    Write-Host "Default microphone -> $($dev.Capture)"
    return $dev
}

function Test-FfmpegWasapi {
    param([string]$FfmpegPath)
    $out = & $FfmpegPath -hide_banner -formats 2>&1 | Out-String
    return $out -match '\sDE?\s+wasapi\s'
}

function Play-WavToCableInput {
    param(
        [Parameter(Mandatory = $true)][string]$AudioPath,
        [switch]$TemporaryDefaultPlayback
    )
    if (-not (Test-Path -LiteralPath $AudioPath)) {
        throw "Audio file not found: $AudioPath"
    }
    $dev = Get-CableDeviceNames
    if (-not $dev.Playback) {
        throw "CABLE Input device not found."
    }

    $ffmpeg = Find-Ffmpeg
    if ($ffmpeg -and (Test-FfmpegWasapi $ffmpeg)) {
        Write-Host "Play via ffmpeg wasapi -> $($dev.Playback)"
        & $ffmpeg -hide_banner -loglevel error -y -i $AudioPath -f wasapi $dev.Playback
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Write-Host "ffmpeg wasapi play failed, fallback to SoundPlayer"
    }

    if (-not $TemporaryDefaultPlayback) {
        $TemporaryDefaultPlayback = $true
    }

    Write-Host "Play via temporary default playback -> $($dev.Playback)"
    Ensure-AudioDeviceCmdlets
    $playback = Get-AudioDevice -Playback
    $savedPlaybackId = $playback.Index
    try {
        $pb = Get-AudioDevice -List | Where-Object {
            $_.Type -eq "Playback" -and $_.Name -eq $dev.Playback
        } | Select-Object -First 1
        if (-not $pb) {
            throw "Playback device not found: $($dev.Playback)"
        }
        Set-AudioDevice -Index $pb.Index
        $player = New-Object System.Media.SoundPlayer $AudioPath
        $player.PlaySync()
    } finally {
        Set-AudioDevice -Index $savedPlaybackId -ErrorAction SilentlyContinue
    }
}

function Test-VBCableLoopback {
    param(
        [string]$AudioPath = (Join-Path $PSScriptRoot "..\audio_samples\weather_question.wav")
    )
    if (-not (Test-VBCableInstalled)) {
        throw "VB-Cable is not installed."
    }
    Set-VBCableDefaultMicrophone | Out-Null
    Write-Host "Playing test wav in 3s. Watch CABLE Output level in Windows Sound settings ..."
    Start-Sleep -Seconds 3
    Play-WavToCableInput -AudioPath $AudioPath -TemporaryDefaultPlayback
    Write-Host "Loopback test done."
}
