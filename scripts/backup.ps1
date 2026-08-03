# ============================================================
# 家政婦紹介事務所 人物管理システム  DBバックアップ
#
# 実行方法
#   手動 : & "C:\workOfficeTani\backup\backup.ps1"
#   自動 : タスクスケジューラ "kaseihu-DB-Backup"（5日ごと・SYSTEM実行）
#
# 設計方針
#  - DBパスワードはこのファイルに書かない。環境変数 DB_PASSWORD から読む。
#    ここに直接書くと、このファイル（＝OneDriveにも同期される）を見られた人が
#    そのまま個人情報DBを取り出せてしまうため。
#  - 保存先は「このPC」と「OneDrive」の2箇所。1箇所だけだと、故障・盗難・
#    ランサムウェアで元データと一緒に失われる。
#  - 失敗したら必ず終了コードを1以上にする。タスクスケジューラの「前回の結果」に
#    残り、後から失敗に気付けるようにするため。
#  - 実行のたびに backup-log.txt と last-backup.txt を残す。
#    last-backup.txt はシステムの「1-7-5 バックアップ手順」画面が読み取り、
#    最終バックアップ日時を表示するために使う。
# ============================================================

$backupDir = "C:\workOfficeTani\backup"
$cloudDir  = "C:\Users\worko\OneDrive\バックアップ"
$pgBin     = "C:\Program Files\PostgreSQL\17\bin"
$auditSrc  = "C:\workOfficeTani\logs\audit"
$dbName    = "kaseihu"
$dbUser    = "postgres"

# 残す世代数。5日に1回の実行なので 75世代 ≒ 約1年分。
# 1世代あたり数十KBしかないため、多めに残しても容量の心配はない。
$keep = 75

$logFile    = Join-Path $backupDir "backup-log.txt"
$statusFile = Join-Path $backupDir "last-backup.txt"

New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

function Write-Log($message) {
    $line = (Get-Date -Format "yyyy-MM-dd HH:mm:ss") + " " + $message
    try { Add-Content -Path $logFile -Value $line -Encoding UTF8 } catch { }
    Write-Output $line
}

# 画面（1-7-5）が読み取る状態ファイル。失敗時も必ず書き、結果を残す。
function Write-Status($result, $backupPath, $cloudTime) {
    $lines = @(
        "last_backup=" + (Get-Date -Format "yyyy-MM-dd HH:mm:ss"),
        "last_backup_file=" + $backupPath,
        "last_cloud_copy=" + $cloudTime,
        "result=" + $result
    )
    try { Set-Content -Path $statusFile -Value $lines -Encoding UTF8 } catch { }
}

Write-Log "===== バックアップ開始 ====="

# ------------------------------------------------------------
# 1. DBパスワードを環境変数から取得する
#    SYSTEMアカウントで動くため、ユーザー環境変数ではなく
#    システム環境変数(Machine)に設定されている必要がある。
# ------------------------------------------------------------
$pw = $env:DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($pw)) {
    $pw = [Environment]::GetEnvironmentVariable("DB_PASSWORD", "Machine")
}
if ([string]::IsNullOrWhiteSpace($pw)) {
    Write-Log "失敗: 環境変数 DB_PASSWORD が設定されていません。"
    Write-Log "      管理者PowerShellで次を実行してください。"
    Write-Log '      [Environment]::SetEnvironmentVariable("DB_PASSWORD", "(パスワード)", "Machine")'
    Write-Status "NG_NO_PASSWORD" "" ""
    exit 1
}
$env:PGPASSWORD = $pw

# ------------------------------------------------------------
# 2. DBをダンプする
# ------------------------------------------------------------
$dateStr    = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = Join-Path $backupDir ("kaseihu_" + $dateStr + ".backup")

& "$pgBin\pg_dump.exe" -U $dbUser -h localhost -d $dbName -F c -f $backupFile
if ($LASTEXITCODE -ne 0) {
    Write-Log "失敗: pg_dump が異常終了しました (終了コード $LASTEXITCODE)"
    Write-Status "NG_DUMP_FAILED" "" ""
    exit 1
}

# pg_dump が成功を返しても、中身が空では復元できない。
# 「バックアップは取れていたのに復元できなかった」を防ぐため必ず確認する。
if (-not (Test-Path $backupFile) -or (Get-Item $backupFile).Length -eq 0) {
    Write-Log "失敗: バックアップファイルが作成されていないか、中身が空です"
    Write-Status "NG_EMPTY_FILE" "" ""
    exit 1
}
$sizeKb = [math]::Round((Get-Item $backupFile).Length / 1KB, 1)
Write-Log ("成功: " + $backupFile + " (" + $sizeKb + " KB)")

# ------------------------------------------------------------
# 3. 監査ログ（1-7-7）も退避する
#    侵入された場合、DB内の access_logs は消される可能性がある。
#    ファイル側を別に残しておけば、後から追跡できる。
# ------------------------------------------------------------
$auditDst = ""
if (Test-Path $auditSrc) {
    $auditDst = Join-Path $backupDir ("audit_" + $dateStr)
    New-Item -ItemType Directory -Path $auditDst -Force | Out-Null
    Copy-Item (Join-Path $auditSrc "*.log") $auditDst -Force -ErrorAction SilentlyContinue
    Write-Log ("監査ログを退避しました: " + $auditDst)
}

# ------------------------------------------------------------
# 4. OneDriveへ二重化する
#    ここが失敗しても、このPC上のバックアップ自体は成功しているため
#    処理は止めず、終了コード2（警告）で知らせる。
# ------------------------------------------------------------
$cloudOk   = $false
$cloudTime = ""
if (-not [string]::IsNullOrWhiteSpace($cloudDir)) {
    try {
        New-Item -ItemType Directory -Path $cloudDir -Force | Out-Null
        Copy-Item $backupFile $cloudDir -Force
        if ($auditDst -ne "") {
            Copy-Item $auditDst (Join-Path $cloudDir (Split-Path $auditDst -Leaf)) -Recurse -Force
        }
        $cloudOk   = $true
        $cloudTime = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        Write-Log ("OneDriveへコピーしました: " + $cloudDir)
    } catch {
        Write-Log ("警告: OneDriveへのコピーに失敗しました: " + $_.Exception.Message)
    }
}

# ------------------------------------------------------------
# 5. 古い世代を削除する（このPC・OneDriveの両方）
#    日数ではなく件数で残す。実行間隔を変えても
#    「前回分が必ず消える」といった事故が起きないようにするため。
# ------------------------------------------------------------
function Remove-OldGenerations($dir) {
    if (-not (Test-Path $dir)) { return }
    Get-ChildItem -Path $dir -Filter "kaseihu_*.backup" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -Skip $keep |
        Remove-Item -Force -ErrorAction SilentlyContinue
    Get-ChildItem -Path $dir -Directory -Filter "audit_*" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -Skip $keep |
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
}
Remove-OldGenerations $backupDir
Remove-OldGenerations $cloudDir

$localCount = @(Get-ChildItem -Path $backupDir -Filter "kaseihu_*.backup" -File -ErrorAction SilentlyContinue).Count
Write-Log ("保存されている世代数: " + $localCount + " (最大 " + $keep + ")")

# ------------------------------------------------------------
# 6. 結果を残して終了する
# ------------------------------------------------------------
if ($cloudOk) {
    Write-Status "OK" $backupFile $cloudTime
    Write-Log "===== 完了（このPC・OneDrive の2箇所に保存） ====="
    exit 0
}

Write-Status "WARN_CLOUD_FAILED" $backupFile ""
Write-Log "===== 完了（このPCのみ。OneDriveへのコピーは失敗） ====="
exit 2
