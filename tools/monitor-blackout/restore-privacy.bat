@echo off
setlocal
if not exist "%~dp0PrivacyScreen-fullscreen.exe" exit /b 1
start /wait "" "%~dp0PrivacyScreen-fullscreen.exe" --restore
exit /b %errorlevel%
