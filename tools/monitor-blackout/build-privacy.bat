@echo off
setlocal
cd /d "%~dp0"
set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
if not exist "%CSC%" (
    echo .NET Framework compiler not found. Install .NET Framework 4.8.
    exit /b 1
)
"%CSC%" /nologo /target:winexe /platform:anycpu /optimize+ /warnaserror+ /out:PrivacyScreen-fullscreen.exe /reference:System.dll /reference:System.Core.dll /reference:System.Drawing.dll /reference:System.Windows.Forms.dll PrivacyScreen.cs
if errorlevel 1 exit /b 1
echo Built: %~dp0PrivacyScreen-fullscreen.exe
exit /b 0
