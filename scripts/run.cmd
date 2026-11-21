@echo off
REM 免 pip install：用 PYTHONPATH 直接跑 CLI
set ROOT=%~dp0..
set PYTHONPATH=%ROOT%\src;%PYTHONPATH%
cd /d "%ROOT%"
python -m echo_eval %*
