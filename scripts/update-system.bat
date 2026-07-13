@echo off
chcp 65001 >nul
setlocal
title 家政婦紹介事務所 人物管理システム - 更新

REM ============================================================
REM update-system.bat
REM 「システムの更新」を1クリックで行うスクリプト。
REM
REM 実行内容：
REM   1. 動いているシステムを安全に停止する
REM   2. 最新版を取得する（git pull）
REM   3. 実行可能な jar ファイルを作り直す（ビルド）
REM   4. システムを再起動する（start-system.vbs を呼び出す）
REM
REM 【使い方】
REM   このファイル（update-system.bat）をダブルクリックするだけ。
REM   黒い画面が開いて進行状況が表示されるので、
REM   「更新が完了しました」と出るまで待ってください。
REM   エラーが出た場合は画面を閉じずに、内容を確認・共有してください。
REM ============================================================

set APPDIR=C:\workOfficeTani

echo ============================================================
echo   システムの更新を開始します
echo ============================================================
echo.

echo [1/4] 実行中のシステムを停止しています...
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*person-management*.jar*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
timeout /t 2 /nobreak >nul
echo   -> 停止しました（もともと起動していなかった場合はそのままでOKです）
echo.

cd /d "%APPDIR%"
if errorlevel 1 (
    echo.
    echo ★ エラー: %APPDIR% が見つかりません。
    pause
    exit /b 1
)

echo [2/4] 最新版を取得しています（git pull）...
git pull
if errorlevel 1 (
    echo.
    echo ★ エラー: git pull に失敗しました。上のメッセージを確認してください。
    echo   （手元でファイルを直接編集した場合、競合していることがあります）
    pause
    exit /b 1
)
echo.

echo [3/4] ビルドしています（少し時間がかかります）...
call mvnw.cmd clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ★ エラー: ビルドに失敗しました。上のメッセージを確認してください。
    pause
    exit /b 1
)
echo.

echo [4/4] システムを再起動しています...
wscript "%APPDIR%\scripts\start-system.vbs"
echo.

echo ============================================================
echo   更新が完了しました。20秒ほどでログイン画面が開きます。
echo ============================================================
echo.
pause
