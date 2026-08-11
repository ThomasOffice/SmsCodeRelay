@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ===== 验证码蓝牙中继 PC 端 =====
python server.py
if %errorlevel% neq 0 (
    echo.
    echo [错误] 程序异常退出，代码: %errorlevel%
    pause
)
