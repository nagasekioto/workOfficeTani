# ============================================================
# 人物管理システム 停止スクリプト（本体）
#
# 利用者は scripts\システム停止.bat をダブルクリックする。
#
# システムが使っているポート（既定8080、SERVER_PORTで変更可）を
# 握っているプロセスだけを止める。
# 「javaを全部止める」やり方にすると、無関係の他のJavaアプリまで
# 巻き添えで終了させてしまうため。
# ============================================================

$ErrorActionPreference = "Stop"

# 起動時と同じポートを見に行く必要がある。SERVER_PORT未設定なら既定の8080。
$port = if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "8080" } else { $env:SERVER_PORT }

Write-Host ""
Write-Host "  家政婦紹介事務所 人物管理システム - 停止" -ForegroundColor Cyan
Write-Host "  ------------------------------------------------------------"
Write-Host ""

$conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

if ($null -eq $conns) {
    Write-Host "  システムは起動していません。" -ForegroundColor Yellow
    Write-Host ""
    Start-Sleep -Seconds 3
    exit 0
}

$stopped = 0
foreach ($pid8080 in ($conns | Select-Object -ExpandProperty OwningProcess -Unique)) {
    $p = Get-Process -Id $pid8080 -ErrorAction SilentlyContinue
    if ($null -eq $p) { continue }

    Write-Host ("  停止します: {0} (プロセスID {1})" -f $p.ProcessName, $pid8080)
    try {
        Stop-Process -Id $pid8080 -Force -ErrorAction Stop
        $stopped++
    } catch {
        Write-Host "  停止できませんでした: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Start-Sleep -Seconds 2

$still = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($null -eq $still) {
    Write-Host ""
    Write-Host "  システムを停止しました。" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "  まだポート$port が使われています。" -ForegroundColor Red
    Write-Host "  パソコンを再起動すると確実に停止します。" -ForegroundColor Red
}

Write-Host ""
Start-Sleep -Seconds 4
