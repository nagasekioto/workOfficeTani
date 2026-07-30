# ============================================================
# 人物管理システム 起動スクリプト（本体）
#
# 利用者は scripts\システム起動.bat をダブルクリックする。
# このファイルは直接実行せず、そのbatから呼ばれる。
#
# 設計方針:
#  - 失敗したら必ず画面に日本語で理由を出して止まる。
#    黙って落ちると、利用者には「ブラウザが繋がらない」としか分からないため
#  - スクリプト自身の場所からシステムの場所を割り出す。
#    パスを直書きすると、フォルダを移動しただけで動かなくなるため
#  - 起動完了を実際に確認してからブラウザを開く。
#    固定秒数の待ち合わせだと、遅れたときに「接続できません」の画面が出るため
# ============================================================

# -Hidden は start-system.vbs（自動起動）から渡される。
# 画面が無い状態で起動されるため、キー入力待ちをしてはいけない
# （待つと、誰にも見えないまま永久に固まる）。
param([switch]$Hidden)

$ErrorActionPreference = "Stop"

# このスクリプトは scripts\ にあるので、その親がシステム本体の場所
$appDir = Split-Path -Parent $PSScriptRoot
$logFile = Join-Path $appDir "system-log.txt"
$launcherLog = Join-Path $appDir "launcher-log.txt"

# HTTPS_ENABLED / SERVER_PORT からURLを組み立てる。
# 既定(HTTPS_ENABLED未設定)ではこれまでどおり http://localhost:8080 になる。
$scheme = if ($env:HTTPS_ENABLED -eq "true") { "https" } else { "http" }
$port = if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "8080" } else { $env:SERVER_PORT }
$url = "${scheme}://localhost:${port}/login"

# 進行状況は画面とファイルの両方に出す。
# Write-Host は画面にしか出ないため、自動起動（画面なし）で失敗したとき
# 何が起きたのか後から追えなくなる。
function Write-Log($message, $color) {
    $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    try { Add-Content -Path $launcherLog -Value "$stamp $message" -Encoding UTF8 } catch { }
    if (-not $Hidden) {
        if ([string]::IsNullOrEmpty($color)) { Write-Host "  $message" }
        else { Write-Host "  $message" -ForegroundColor $color }
    }
}

function Show-Error($title, $lines) {
    Write-Log "[失敗] $title"
    foreach ($l in $lines) { Write-Log "        $l" }

    if ($Hidden) {
        # 画面が無いのでキー入力を待てない。ダイアログで知らせる。
        $body = $title + [Environment]::NewLine + [Environment]::NewLine + ($lines -join [Environment]::NewLine)
        try {
            $wsh = New-Object -ComObject WScript.Shell
            $null = $wsh.Popup($body, 0, "人物管理システム 起動エラー", 16)
        } catch { }
        exit 1
    }

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host "  $title" -ForegroundColor Red
    Write-Host "============================================================" -ForegroundColor Red
    foreach ($l in $lines) { Write-Host "  $l" }
    Write-Host ""
    Write-Host "  この画面を閉じるには、何かキーを押してください。"
    Write-Host ""
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

if (-not $Hidden) {
    Write-Host ""
    Write-Host "  家政婦紹介事務所 人物管理システム" -ForegroundColor Cyan
    Write-Host "  ------------------------------------------------------------"
}
Write-Log "===== 起動処理を開始しました (Hidden=$Hidden) ====="
Write-Log "場所: $appDir"
Write-Log "接続方式: $scheme  ポート: $port"
if (-not $Hidden) { Write-Host "" }

# ------------------------------------------------------------
# HTTPSとCookieのsecure設定の組み合わせを確認する。
# 片方だけ設定すると、実害が出る（片方はブラウザ警告、もう片方は
# ログイン不能）ため、起動前に気付けるようここで警告しておく。
# ------------------------------------------------------------
$cookieSecure = $env:COOKIE_SECURE -eq "true"
$httpsEnabled = $env:HTTPS_ENABLED -eq "true"

if ($httpsEnabled -and -not $cookieSecure) {
    Write-Log "[注意] HTTPS_ENABLED=true なのに COOKIE_SECURE が true ではありません。" "Yellow"
    Write-Log "       COOKIE_SECURE=true も設定することを推奨します。" "Yellow"
}
if (-not $httpsEnabled -and $cookieSecure) {
    Write-Log "[注意] COOKIE_SECURE=true なのに HTTPS_ENABLED が true ではありません。" "Yellow"
    Write-Log "       この組み合わせではHTTP接続時にCookieが送られず、ログインできません。" "Yellow"
    Write-Log "       COOKIE_SECURE を false にするか、HTTPS_ENABLED を true にしてください。" "Yellow"
}

# ------------------------------------------------------------
# 1. 必要な設定があるか確認する
#    ここで止めておかないと、起動してから初めて失敗が分かることになる
# ------------------------------------------------------------
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    Show-Error "設定が足りません: DB_PASSWORD" @(
        "データベースのパスワードが設定されていないため、システムは起動できません。",
        "",
        "管理者権限のPowerShellで、以下を実行してください。",
        "",
        '  [Environment]::SetEnvironmentVariable("DB_PASSWORD", "(パスワード)", "Machine")',
        "",
        "実行後、パソコンを再起動するか、サインアウトしてからやり直してください。",
        "詳しくは SETUP_NEW_PC.md の手順4を参照してください。"
    )
}

Write-Log "DB_PASSWORD の設定を確認しました"

if ([string]::IsNullOrWhiteSpace($env:TOTP_ENCRYPTION_KEY)) {
    Write-Log "[注意] TOTP_ENCRYPTION_KEY が設定されていません。" "Yellow"
    Write-Log "       システムは起動しますが、ログインはできません。" "Yellow"
    Write-Log "       SETUP_NEW_PC.md の手順6-2を参照してください。" "Yellow"
}

# ------------------------------------------------------------
# 2. Javaがあるか確認する
# ------------------------------------------------------------
$java = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $java) {
    Show-Error "Javaが見つかりません" @(
        "システムを動かすにはJava(JDK 17以降)が必要です。",
        "",
        "https://adoptium.net/ からインストールし、パソコンを再起動してください。",
        "詳しくは SETUP_NEW_PC.md の手順1-3を参照してください。"
    )
}

# ------------------------------------------------------------
# 3. 既に起動していないか確認する
#    二重に起動するとポートが衝突し、後から起動したほうが黙って死ぬ
# ------------------------------------------------------------
$listening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($null -ne $listening) {
    Write-Log "システムは既に起動しています。ブラウザを開きます。" "Green"
    Start-Process $url
    Start-Sleep -Seconds 2
    exit 0
}
Write-Log "ポート$port は空いています"

# ------------------------------------------------------------
# 4. 実行ファイル(jar)を用意する
#    ソースを直した後にjarを作り直し忘れると、古いままのシステムが起動して
#    「直したはずなのに直っていない」という事故になる。
#    そのため、ソースのほうが新しければ自動で作り直す。
# ------------------------------------------------------------
$jar = Get-ChildItem (Join-Path $appDir "target") -Filter "person-management-*.jar" -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike "*sources*" } |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1

$needBuild = $false
$reason = ""

if ($null -eq $jar) {
    $needBuild = $true
    $reason = "実行ファイルがまだ作られていません"
} else {
    $newestSource = Get-ChildItem (Join-Path $appDir "src") -Recurse -File -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $pom = Get-Item (Join-Path $appDir "pom.xml") -ErrorAction SilentlyContinue
    if ($null -ne $newestSource -and $newestSource.LastWriteTime -gt $jar.LastWriteTime) {
        $needBuild = $true
        $reason = "プログラムが更新されています"
    } elseif ($null -ne $pom -and $pom.LastWriteTime -gt $jar.LastWriteTime) {
        $needBuild = $true
        $reason = "設定(pom.xml)が更新されています"
    }
}

if ($needBuild) {
    Write-Log "$reason。実行ファイルを作成します。" "Yellow"
    Write-Log "初回は数分かかることがあります。そのままお待ちください..." "Yellow"

    Push-Location $appDir
    try {
        & (Join-Path $appDir "mvnw.cmd") package "-DskipTests" -q
        $buildExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($buildExit -ne 0) {
        Show-Error "実行ファイルの作成に失敗しました" @(
            "プログラムに誤りがあるか、インターネットに接続できていない可能性があります。",
            "",
            "以下を手動で実行すると、詳しい原因が表示されます。",
            "",
            "  cd $appDir",
            "  .\mvnw.cmd package -DskipTests"
        )
    }

    $jar = Get-ChildItem (Join-Path $appDir "target") -Filter "person-management-*.jar" -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notlike "*sources*" } |
           Sort-Object LastWriteTime -Descending | Select-Object -First 1

    if ($null -eq $jar) {
        Show-Error "実行ファイルが見つかりません" @(
            "作成処理は成功しましたが、jarファイルが見つかりませんでした。",
            "target フォルダの中を確認してください。"
        )
    }
    Write-Log "実行ファイルを作成しました。" "Green"
}
Write-Log "実行ファイル: $($jar.FullName)"

# ------------------------------------------------------------
# 5. 起動する
#    画面を出さずに裏で動かし、出力はログファイルに残す。
#    起動に失敗したときは、このログを画面に出して原因を見せる。
# ------------------------------------------------------------
Write-Log "システムを起動しています..."

$cmdLine = 'java -jar "' + $jar.FullName + '" > "' + $logFile + '" 2>&1'
$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $cmdLine `
                      -WorkingDirectory $appDir -WindowStyle Hidden -PassThru
Write-Log "起動プロセスID: $($proc.Id)"

if ($scheme -eq "https") {
    # 自己署名証明書だと検証に失敗し、起動完了を検知できない。
    # ここは「自分自身のポートが応答するか」を見るだけの確認なので、検証を無効化する。
    [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
}

# ------------------------------------------------------------
# 6. 起動が終わるまで待つ（固定秒数ではなく実際に応答するまで）
# ------------------------------------------------------------
$ready = $false
for ($i = 0; $i -lt 90; $i++) {

    # 起動処理そのものが落ちた場合は、待ち続けても意味がないので即座に知らせる
    if ($proc.HasExited) {
        Write-Host ""
        $tail = @()
        if (Test-Path $logFile) {
            $tail = Get-Content $logFile -Tail 25 -ErrorAction SilentlyContinue
        }
        Show-Error "システムの起動に失敗しました" (@(
            "起動処理が途中で終了しました。ログの末尾を表示します。",
            "",
            "----- $logFile -----"
        ) + $tail)
    }

    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
        if ($resp.StatusCode -eq 200) { $ready = $true; break }
    } catch {
        # まだ起動中。1秒待って再確認する
    }

    if (-not $Hidden) { Write-Host "." -NoNewline }
    Start-Sleep -Seconds 1
}

if (-not $Hidden) { Write-Host "" }

if (-not $ready) {
    $tail = @()
    if (Test-Path $logFile) {
        $tail = Get-Content $logFile -Tail 25 -ErrorAction SilentlyContinue
    }
    Show-Error "システムが応答しません" (@(
        "90秒待ちましたが、ログイン画面が開けませんでした。",
        "ログの末尾を表示します。",
        "",
        "----- $logFile -----"
    ) + $tail)
}

# ------------------------------------------------------------
# 7. ブラウザを開く
# ------------------------------------------------------------
Write-Log "起動しました。ブラウザにログイン画面を開きます。" "Green"
Start-Process $url

if (-not $Hidden) {
    Write-Host ""
    Write-Host "  ------------------------------------------------------------"
    Write-Host "  この黒い画面は閉じてかまいません。システムは動き続けます。"
    Write-Host "  終了するときは scripts\システム停止.bat をダブルクリックしてください。"
    Write-Host "  ------------------------------------------------------------"
    Write-Host ""
    Start-Sleep -Seconds 5
}
