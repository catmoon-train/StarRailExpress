@echo off
setlocal
cd /d "%~dp0"
if not exist "PrivacyScreen-fullscreen.exe" call "%~dp0build-privacy.bat"
if not exist "PrivacyScreen-fullscreen.exe" (
    echo Build failed. See the message above.
    pause
    exit /b 1
)
start "" "%~dp0PrivacyScreen-fullscreen.exe"
