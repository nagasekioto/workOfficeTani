@echo off
chcp 65001 >nul
setlocal
title 家政婦紹介事務所 人物管理システム - 停止

set APPDIR=%~dp0
set PGBIN=%APPDIR%pgsql\bin
set PGDATA=%APPDIR%pgdata

echo ============================================================
echo   システムを停止します
echo ============================================================
echo.

echo [1/2] アプリケーションを停止しています...
powershell -NoProfile -Command "Get-Process -Name 'WorkOfficeTani' -ErrorAction SilentlyContinue | Stop-Process -Force"
echo   -> 停止しました(もともと起動していなかった場合はそのままでOKです)
echo.

echo [2/2] PostgreSQL(同梱版)を停止しています...
if exist "%PGBIN%\pg_ctl.exe" (
    "%PGBIN%\pg_ctl.exe" stop -D "%PGDATA%" -m fast
) else (
    echo   -> pg_ctl.exe が見つかりません。スキップします。
)
echo.

echo ============================================================
echo   停止処理が完了しました。
echo ============================================================
pause
