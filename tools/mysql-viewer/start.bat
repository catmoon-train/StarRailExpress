@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === StarRailExpress 数据管理台 ===

if not exist "config.local.js" (
  echo [!] 未找到 config.local.js，请先复制 config.sample.js 为 config.local.js 并填写数据库配置。
  echo     copy config.sample.js config.local.js
  pause
  exit /b 1
)

if not exist "node_modules" (
  echo [*] 首次运行，正在安装依赖 npm install ...
  call npm install
  if errorlevel 1 (
    echo [!] 依赖安装失败，请检查 Node.js / 网络。
    pause
    exit /b 1
  )
)

echo [*] 启动服务...
node server.js
pause
