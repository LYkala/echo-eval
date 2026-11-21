. "$PSScriptRoot\env.ps1"

$existing = & $script:Adb devices 2>$null | Select-String "emulator-\d+\s+device"
if ($existing) {
    Write-Host "Emulator already running:"
    & $script:Adb devices -l
    exit 0
}

Write-Host "Starting AVD: $script:AvdName"
Start-Process -FilePath $script:Emulator -ArgumentList @(
    "-avd", $script:AvdName,
    "-no-snapshot-save",
    "-gpu", "swiftshader_indirect",
    "-no-boot-anim",
    "-netdelay", "none",
    "-netspeed", "full"
) | Out-Null

Write-Host "Waiting for boot..."
& $script:Adb wait-for-device
$deadline = (Get-Date).AddMinutes(5)
while ((Get-Date) -lt $deadline) {
    $prop = ((& $script:Adb shell getprop sys.boot_completed 2>$null) | Out-String).Trim()
    if ($prop -eq "1") {
        Write-Host "BOOT_OK"
        & $script:Adb devices -l
        exit 0
    }
    Start-Sleep -Seconds 3
}
Write-Error "Emulator boot timeout"
exit 1
