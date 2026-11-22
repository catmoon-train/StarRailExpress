@echo off
setlocal
cd /d "%~dp0"
where pythonw.exe >nul 2>nul
if not errorlevel 1 (
    start "" pythonw.exe "%~dp0quickbuild.py" %*
    exit /b 0
)
python "%~dp0quickbuild.py" %*
if errorlevel 1 pause
