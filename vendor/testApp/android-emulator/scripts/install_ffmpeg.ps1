# Install portable ffmpeg into tools/ffmpeg (conda-forge mirror, no admin).
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$ToolsFfmpeg = Join-Path $Root "tools\ffmpeg\ffmpeg.exe"
$CondaEnv = Join-Path $Root "tools\conda-ffmpeg"
$Mirror = "https://mirrors.tuna.tsinghua.edu.cn/anaconda/cloud/conda-forge/"

if ((Test-Path $ToolsFfmpeg) -and ((Get-Item $ToolsFfmpeg).Length -gt 1000000)) {
    Write-Host "[ok] ffmpeg already installed: $ToolsFfmpeg"
    & $ToolsFfmpeg -hide_banner -version | Select-Object -First 1
    exit 0
}

Write-Host "Installing ffmpeg via conda-forge ($Mirror) ..."
if (Test-Path $CondaEnv) {
    Remove-Item $CondaEnv -Recurse -Force
}
conda create -p $CondaEnv -c $Mirror ffmpeg -y --no-default-packages | Out-Host
$built = Join-Path $CondaEnv "Library\bin\ffmpeg.exe"
if (-not (Test-Path $built)) {
    throw "conda ffmpeg not found: $built"
}
New-Item -ItemType Directory -Force -Path (Split-Path $ToolsFfmpeg) | Out-Null
Copy-Item $built $ToolsFfmpeg -Force
Write-Host "[ok] $ToolsFfmpeg"
& $ToolsFfmpeg -hide_banner -version | Select-Object -First 1
