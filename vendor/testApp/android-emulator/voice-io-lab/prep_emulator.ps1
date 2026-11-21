# Dismiss ANR dialogs and bring voice-assistant-app to foreground.
$ErrorActionPreference = "Stop"
. (Join-Path (Split-Path $PSScriptRoot -Parent) "scripts\env.ps1")

$pkg = "com.testappgui.voiceassistant"
$activity = "$pkg/com.k2fsa.sherpa.onnx.MainActivity"

for ($i = 0; $i -lt 5; $i++) {
    & $script:Adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP | Out-Null
    & $script:Adb -s emulator-5554 shell uiautomator dump /sdcard/prep_ui.xml 2>$null | Out-Null
    $xml = (& $script:Adb -s emulator-5554 shell cat /sdcard/prep_ui.xml 2>$null) -join "`n"
    if ($xml -match 'aerr_wait') {
        & $script:Adb -s emulator-5554 shell input tap 540 1350 | Out-Null
        Start-Sleep -Seconds 2
        continue
    }
    if ($xml -match $pkg) { break }
    & $script:Adb -s emulator-5554 shell am start -W -n $activity | Out-Null
    Start-Sleep -Seconds 2
}

Write-Host "Voice app foreground ready."
