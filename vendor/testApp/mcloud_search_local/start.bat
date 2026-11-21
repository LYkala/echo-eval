@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo === 移动网盘搜索 · 本地USB ===

REM ---- 优先用包内 adb ----
set "ADB="
if exist "%CD%\tools\platform-tools\adb.exe" (
  set "ADB=%CD%\tools\platform-tools\adb.exe"
  set "PATH=%CD%\tools\platform-tools;%PATH%"
) else (
  where adb >nul 2>&1
  if errorlevel 1 (
    echo [错误] 包内无 adb，系统 PATH 也没有 adb。
    pause
    exit /b 1
  )
  for /f "delims=" %%i in ('where adb') do (
    set "ADB=%%i"
    goto :adb_found
  )
)
:adb_found
echo ADB=%ADB%
"%ADB%" version
"%ADB%" start-server >nul 2>&1
"%ADB%" devices -l
echo.

REM ---- 检测 USB 设备 ----
set "HAS_DEV="
for /f "skip=1 tokens=1,2" %%a in ('"%ADB%" devices') do (
  if /i "%%b"=="device" set "HAS_DEV=1"
)
if not defined HAS_DEV (
  echo [提示] 未检测到状态为 device 的手机。
  echo        请插上 USB、打开调试并在手机上点「允许」，然后重新运行 start.bat
  echo        仍可启动网页，但跑测前需先连上手机。
  echo.
)

REM ---- 自动安装 / 启用 ADB Keyboard ----
if exist "%CD%\tools\adbkeyboard\ADBKeyboard.apk" (
  echo 检查 ADB Keyboard...
  "%ADB%" shell pm path com.android.adbkeyboard >nul 2>&1
  if errorlevel 1 (
    echo 安装 ADB Keyboard...
    "%ADB%" install -r "%CD%\tools\adbkeyboard\ADBKeyboard.apk"
  ) else (
    echo ADB Keyboard 已安装
  )
  "%ADB%" shell ime enable com.android.adbkeyboard/.AdbIME >nul 2>&1
  echo ADB Keyboard 已尝试 enable
  echo.
) else (
  echo [警告] 缺少 tools\adbkeyboard\ADBKeyboard.apk
  echo.
)

python -c "import flask, openpyxl" 2>nul
if errorlevel 1 (
  echo 安装 Python 依赖...
  pip install -r requirements.txt
)

echo Starting http://127.0.0.1:9530/
python app.py --host 127.0.0.1 --port 9530
pause
