$ErrorActionPreference = "Stop"
$script:SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$script:JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Users\h30055616\.jdks\jbr-17.0.14" }
$script:AvdName = if ($env:ANDROID_AVD_NAME) { $env:ANDROID_AVD_NAME } else { "TestAppGUI_API34" }

$env:ANDROID_SDK_ROOT = $script:SdkRoot
$env:ANDROID_HOME = $script:SdkRoot
$env:JAVA_HOME = $script:JavaHome
$env:Path = @(
    (Join-Path $script:JavaHome "bin"),
    (Join-Path $script:SdkRoot "cmdline-tools\latest\bin"),
    (Join-Path $script:SdkRoot "platform-tools"),
    (Join-Path $script:SdkRoot "emulator"),
    $env:Path
) -join ";"

$script:Emulator = Join-Path $script:SdkRoot "emulator\emulator.exe"
$script:Adb = Join-Path $script:SdkRoot "platform-tools\adb.exe"
