@echo off
setlocal
cd /d "%~dp0"
if not exist "MonitorBlackout-compat.exe" call "%~dp0build.bat"
if not exist "MonitorBlackout-compat.exe" (
    echo Build failed. See the message above.
    pause
    exit /b 1
)
start "" "%~dp0MonitorBlackout-compat.exe"
