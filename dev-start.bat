@echo off
cd /d %~dp0
set "PY=D:\python3.14.3\python.exe"
if not exist "%PY%" set "PY=python"
"%PY%" dev-start.py
pause
