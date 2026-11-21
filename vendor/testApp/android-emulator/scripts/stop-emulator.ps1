. "$PSScriptRoot\env.ps1"
Write-Host "Stopping emulators via adb emu kill..."
$serials = & $script:Adb devices | Select-String "emulator-\d+" | ForEach-Object {
    ($_ -split "\s+")[0]
}
foreach ($s in $serials) {
    Write-Host "kill $s"
    & $script:Adb -s $s emu kill 2>$null
}
Get-Process -Name "qemu-system*", "emulator" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Write-Host "Done."
& $script:Adb devices
