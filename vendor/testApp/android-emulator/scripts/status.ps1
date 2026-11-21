. "$PSScriptRoot\env.ps1"
Write-Host "SDK: $script:SdkRoot"
Write-Host "AVD: $script:AvdName"
Write-Host "--- avds ---"
& $script:Emulator -list-avds
Write-Host "--- adb ---"
& $script:Adb devices -l
Write-Host "--- system-images ---"
$img = Join-Path $script:SdkRoot "system-images\android-34\google_apis\x86_64"
Write-Host ("system-image android-34: " + (Test-Path $img))
