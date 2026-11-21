@echo off
chcp 65001 >nul
set "ROOT=E:\TestAPPGUI\testApp\webserverV2"
if not exist "%ROOT%\tools\prepare_agent.py" (
  echo [错误] 未找到 %ROOT%\tools\prepare_agent.py
  pause
  exit /b 1
)
start "NetdiskPrepareAgent" /min cmd /c "cd /d "%ROOT%" && python tools\prepare_agent.py"
echo 已后台启动本机准备助手。打开 http://124.71.69.102:9526/pan-search 点「一键准备」。
timeout /t 2 >nul
