# ===== 验证码蓝牙中继 - 一键打包脚本 =====
# 用法: powershell -ExecutionPolicy Bypass -File build_apk.ps1 [-Release]
# 默认构建 Debug，加 -Release 构建已签名 Release APK

param(
    [switch]$Release
)

$ErrorActionPreference = "Stop"

$JAVA_HOME = $env:JAVA_HOME
if (-not $JAVA_HOME -or -not (Test-Path $JAVA_HOME)) {
    $JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
}
$env:JAVA_HOME = $JAVA_HOME
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "C:\Android\sdk" }
$env:Path = "$JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

Write-Host "===== 验证码蓝牙中继 APK 打包 =====" -ForegroundColor Cyan
Write-Host "JAVA_HOME  = $env:JAVA_HOME"
Write-Host "ANDROID_HOME = $env:ANDROID_HOME"

$task = if ($Release) { "assembleRelease" } else { "assembleDebug" }
Write-Host "构建任务: $task" -ForegroundColor Yellow

& ".\gradlew.bat" $task
if ($LASTEXITCODE -ne 0) {
    Write-Host "构建失败！" -ForegroundColor Red
    exit 1
}

$apkDir = if ($Release) { "app\build\outputs\apk\release" } else { "app\build\outputs\apk\debug" }
$apk = Get-ChildItem $apkDir -Filter "*.apk" | Select-Object -First 1
if ($apk) {
    Write-Host ""
    Write-Host "构建成功！" -ForegroundColor Green
    Write-Host ("APK: {0} ({1:N2} MB)" -f $apk.FullName, ($apk.Length / 1MB))
    Write-Host ""
    Write-Host "安装到手机:" -ForegroundColor Cyan
    Write-Host "  adb install `"$($apk.FullName)`""
} else {
    Write-Host "未找到 APK 文件" -ForegroundColor Red
    exit 1
}
