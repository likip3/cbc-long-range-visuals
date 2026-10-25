@echo off
setlocal
cd /d "%~dp0"
python scripts\setup_dev.py
if errorlevel 1 exit /b %errorlevel%
call dev.cmd build prepareClientRun prepareServerRun
exit /b %errorlevel%

