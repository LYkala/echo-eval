@echo off
cd /d "%~dp0"
title Netdisk Prepare Agent

if exist "%~dp0NetdiskPrepareAgent.exe" (
  echo Starting portable agent...
  "%~dp0NetdiskPrepareAgent.exe"
  goto :end
)

if exist "%~dp0tools\dist\NetdiskPrepareAgent.exe" (
  echo Starting repo agent exe...
  "%~dp0tools\dist\NetdiskPrepareAgent.exe"
  goto :end
)

if exist "%~dp0tools\prepare_agent.py" (
  echo Starting repo agent via python...
  python "%~dp0tools\prepare_agent.py"
  goto :end
)

set "ROOT=E:\TestAPPGUI\testApp\webserverV2"
if exist "%ROOT%\tools\dist\NetdiskPrepareAgent.exe" (
  "%ROOT%\tools\dist\NetdiskPrepareAgent.exe"
  goto :end
)

echo ERROR: agent not found. Download:
echo   http://124.71.69.102:9526/static/netdisk/NetdiskPrepareAgent.zip
echo Unzip and run StartAgent.bat
pause
exit /b 1

:end
if errorlevel 1 pause
pause
