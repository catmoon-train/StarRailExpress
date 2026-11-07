@echo off
setlocal
pushd "%~dp0"
if errorlevel 1 exit /b 1

call "%~dp0gradlew.bat" :sre-pack-compat:build --console=plain %*
set "BUILD_EXIT_CODE=%ERRORLEVEL%"

echo.
if "%BUILD_EXIT_CODE%"=="0" (
    echo Compat build completed. Output: sre-pack-compat\build\libs\
) else (
    echo Compat build failed. Exit code: %BUILD_EXIT_CODE%
)

popd
if not defined CI pause
exit /b %BUILD_EXIT_CODE%
