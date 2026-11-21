param(
    [string]$Abi = "x86_64"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ProjectDir = Join-Path $Root "voice-assistant-app\project"
$Gradlew = Join-Path $ProjectDir "gradlew.bat"
$GradleBat = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.2-bin\*\gradle-8.2\bin\gradle.bat" -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not (Test-Path $ProjectDir)) {
    Write-Host "Run .\scripts\prepare_voice_assistant_app.ps1 first" -ForegroundColor Yellow
    exit 1
}

$javaCandidates = @(
    "C:\Program Files\Microsoft\jdk-*",
    $env:JAVA_HOME,
    "C:\Users\h30055616\.jdks\jbr-*"
)
foreach ($pattern in $javaCandidates) {
    if (-not $pattern) { continue }
    $resolved = Get-Item $pattern -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1
    if ($resolved -and (Test-Path (Join-Path $resolved.FullName "bin\java.exe"))) {
        $env:JAVA_HOME = $resolved.FullName
        $env:Path = "$(Join-Path $resolved.FullName 'bin');$env:Path"
        break
    }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Install JDK 17+ or set JAVA_HOME" -ForegroundColor Red
    exit 1
}

Push-Location $ProjectDir
try {
    if ((Test-Path $Gradlew) -and (Test-Path "gradle\wrapper\gradle-wrapper.jar")) {
        & $Gradlew assembleDebug --no-daemon
    } elseif ($GradleBat) {
        Write-Host "Using cached Gradle: $($GradleBat.FullName)"
        & $GradleBat.FullName assembleDebug --offline --no-daemon
    } else {
        throw "No gradlew and no cached Gradle 8.2"
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $apk = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
    $dest = Join-Path $Root "voice-assistant-app\VoiceAssistant-debug-$Abi.apk"
    Copy-Item -Force $apk $dest
    Write-Host "`nAPK: $dest" -ForegroundColor Green
    Write-Host "Install: adb install -r `"$dest`""
} finally {
    Pop-Location
}
