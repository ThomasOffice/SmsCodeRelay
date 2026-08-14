# ===== SmsRelay PyInstaller build script =====
# Builds a windowed (no console), tray-resident, single-file exe.
# config.yaml is bundled inside the exe (not exposed in dist).
# Usage: pwsh -ExecutionPolicy Bypass -File build_exe.ps1

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

Write-Host "===== SmsRelay exe build =====" -ForegroundColor Cyan

Remove-Item -Path "build","dist" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "SmsRelay.spec" -Force -ErrorAction SilentlyContinue

pyinstaller --noconfirm --windowed --onefile `
    --name "SmsRelay" `
    --icon "app_icon.ico" `
    --add-data "config.yaml;." `
    --add-data "app_icon.png;." `
    --collect-all pystray `
    --collect-all PIL `
    --hidden-import "tkinter" `
    --hidden-import "code_parser" `
    --hidden-import "bt_rfcomm" `
    --hidden-import "email_poller" `
    --hidden-import "imaplib" `
    --exclude-module "PyQt5" --exclude-module "PyQt6" `
    --exclude-module "PySide2" --exclude-module "PySide6" `
    --exclude-module "matplotlib" --exclude-module "numpy" `
    --exclude-module "pandas" --exclude-module "scipy" `
    --exclude-module "IPython" --exclude-module "jupyter" `
    --exclude-module "notebook" --exclude-module "spyder" `
    --exclude-module "serial" `
    server.py

$exe = "dist\SmsRelay.exe"
if (Test-Path -LiteralPath $exe) {
    $size = [math]::Round((Get-Item $exe).Length / 1MB, 2)
    Write-Host ""
    Write-Host "BUILD OK" -ForegroundColor Green
    Write-Host "exe: $exe ($size MB)"
    Write-Host ""
    Get-ChildItem "dist" | Select-Object Name, @{N='MB';E={[math]::Round($_.Length/1MB,2)}}
    Write-Host ""
    Write-Host "config.yaml is bundled inside the exe." -ForegroundColor Cyan
    Write-Host "User config is stored at: %APPDATA%\SmsRelay\config.yaml" -ForegroundColor Cyan
    Write-Host "Usage: copy SmsRelay.exe to target machine, double-click to run" -ForegroundColor Cyan
} else {
    Write-Host "BUILD FAILED: exe not found" -ForegroundColor Red
    exit 1
}
