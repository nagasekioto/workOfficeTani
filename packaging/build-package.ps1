# ============================================================
# build-package.ps1
# 「家政婦紹介事務所 人物管理システム」を、
#   ・PostgreSQLを含む完全自己完結型
#   ・exeをダブルクリックするだけで起動できるフォルダ配布
# として一式パッケージングするスクリプト。
#
# 事前準備:
#   1. JDK17がインストール済みであること(jpackageコマンドが使えること)
#   2. packaging\pgsql-portable フォルダに、PostgreSQLの
#      「zip archive(インストーラー不要版)」の中身を展開しておくこと
#      (詳細は packaging\pgsql-portable\README.md を参照)
#
# 使い方:
#   cd C:\workOfficeTani
#   .\packaging\build-package.ps1
#
# 出力先:
#   dist\WorkOfficeTani\  (このフォルダごとコピーすれば別PCでも動く)
# ============================================================

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$PackagingDir = Join-Path $ProjectRoot "packaging"
$DistDir = Join-Path $ProjectRoot "dist"
$AppName = "WorkOfficeTani"
$OutputDir = Join-Path $DistDir $AppName

Write-Host "============================================================"
Write-Host "  1/5 Mavenでjarをビルドしています..."
Write-Host "============================================================"
Set-Location $ProjectRoot
& .\mvnw.cmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "mvnwのビルドに失敗しました" }

$JarPath = Get-ChildItem -Path (Join-Path $ProjectRoot "target") -Filter "person-management-*.jar" |
    Where-Object { $_.Name -notlike "*sources*" } |
    Select-Object -First 1
if (-not $JarPath) { throw "target配下にjarファイルが見つかりません" }

Write-Host "============================================================"
Write-Host "  2/5 既存の出力フォルダを削除しています..."
Write-Host "============================================================"
if (Test-Path $DistDir) {
    Remove-Item -Path $DistDir -Recurse -Force
}
New-Item -ItemType Directory -Path $DistDir | Out-Null

Write-Host "============================================================"
Write-Host "  3/5 jpackageでexe化しています(JRE同梱)..."
Write-Host "============================================================"
$IconArg = @()
$IconPath = Join-Path $PackagingDir "icon.ico"
if (Test-Path $IconPath) {
    $IconArg = @("--icon", $IconPath)
}
jpackage `
    --input (Split-Path -Parent $JarPath.FullName) `
    --name $AppName `
    --main-jar $JarPath.Name `
    --type app-image `
    --dest $DistDir `
    --java-options "-Dfile.encoding=UTF-8" `
    @IconArg
if ($LASTEXITCODE -ne 0) { throw "jpackageに失敗しました" }

Write-Host "============================================================"
Write-Host "  4/5 PostgreSQL(ポータブル版)を同梱しています..."
Write-Host "============================================================"
$PgSrc = Join-Path $PackagingDir "pgsql-portable"
$PgBinCheck = Join-Path $PgSrc "bin\initdb.exe"
if (-not (Test-Path $PgBinCheck)) {
    throw "packaging\pgsql-portable\bin\initdb.exe が見つかりません。`n" +
          "packaging\pgsql-portable\README.md の手順に従ってPostgreSQLポータブル版を配置してください。"
}
Copy-Item -Path $PgSrc -Destination (Join-Path $OutputDir "pgsql") -Recurse -Force

Write-Host "============================================================"
Write-Host "  5/5 起動スクリプト・スキーマファイルを配置しています..."
Write-Host "============================================================"
$ScriptsOut = Join-Path $OutputDir "scripts"
New-Item -ItemType Directory -Path $ScriptsOut -Force | Out-Null
Copy-Item -Path (Join-Path $ProjectRoot "src\main\resources\schema-bootstrap.sql") -Destination $ScriptsOut -Force
Copy-Item -Path (Join-Path $ProjectRoot "src\main\resources\schema-all.sql") -Destination $ScriptsOut -Force
Copy-Item -Path (Join-Path $PackagingDir "runtime\start-app.vbs") -Destination $OutputDir -Force
Copy-Item -Path (Join-Path $PackagingDir "runtime\stop-app.bat") -Destination $OutputDir -Force

Write-Host ""
Write-Host "============================================================"
Write-Host "  完了しました！"
Write-Host "  出力先: $OutputDir"
Write-Host "  この $AppName フォルダをそのまま別PCにコピーしても動作します。"
Write-Host "  起動: $AppName\start-app.vbs をダブルクリック"
Write-Host "  停止: $AppName\stop-app.bat をダブルクリック"
Write-Host "============================================================"
