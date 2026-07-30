# ============================================================
# HTTPS証明書作成ツール
#
# 社内の他PCからも画面を使う運用にする場合に、このスクリプトで
# 自己署名証明書（PKCS12形式）を作成する。
#
# 通常（このPC1台だけで使う運用）では実行不要。
# 127.0.0.1だけで待ち受けている限り通信はPCの外に出ないため、
# HTTPSにしなくても盗聴の危険は無い。
#
# 作成した証明書は config\https-keystore.p12 に保存され、
# application.yml の server.ssl 設定（HTTPS_ENABLED等の環境変数）
# から読み込まれる。
# ============================================================

$ErrorActionPreference = "Stop"

# このスクリプトは scripts\ にあるので、その親がシステム本体の場所
$appDir = Split-Path -Parent $PSScriptRoot
$configDir = Join-Path $appDir "config"
$keystorePath = Join-Path $configDir "https-keystore.p12"

Write-Host ""
Write-Host "  HTTPS証明書作成ツール" -ForegroundColor Cyan
Write-Host "  ------------------------------------------------------------"
Write-Host ""

# ------------------------------------------------------------
# 1. keytool(JDK付属)を探す
#    JAVA_HOMEを優先し、無ければ java コマンドの場所から辿る。
#    どちらも無ければ、ここで理由を出して止める。
# ------------------------------------------------------------
$keytool = $null

if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $candidate) {
        $keytool = $candidate
    }
}

if ($null -eq $keytool) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCmd) {
        # java.exe の場所から bin フォルダを割り出し、同じ場所の keytool.exe を探す
        $javaBinDir = Split-Path -Parent $javaCmd.Source
        $candidate = Join-Path $javaBinDir "keytool.exe"
        if (Test-Path $candidate) {
            $keytool = $candidate
        }
    }
}

if ($null -eq $keytool) {
    Write-Host "  [エラー] keytool.exe が見つかりませんでした。" -ForegroundColor Red
    Write-Host ""
    Write-Host "  証明書の作成にはJDK(Java Development Kit)が必要です。" -ForegroundColor Red
    Write-Host "  JRE(実行環境)のみではkeytoolが含まれないことがあります。" -ForegroundColor Red
    Write-Host "  JAVA_HOME環境変数にJDKのインストール先を設定するか、" -ForegroundColor Red
    Write-Host "  JDKのbinフォルダにPATHを通してから、やり直してください。" -ForegroundColor Red
    Write-Host ""
    exit 1
}

Write-Host "  keytool: $keytool"

# ------------------------------------------------------------
# 2. 出力先フォルダを準備する
# ------------------------------------------------------------
if (-not (Test-Path $configDir)) {
    New-Item -ItemType Directory -Path $configDir | Out-Null
    Write-Host "  作成しました: $configDir"
}

# ------------------------------------------------------------
# 3. 既にキーストアがある場合は上書き確認する
#    上書きすると、既存の証明書（秘密鍵）が失われるため。
# ------------------------------------------------------------
if (Test-Path $keystorePath) {
    Write-Host ""
    Write-Host "  [確認] 既にキーストアが存在します。" -ForegroundColor Yellow
    Write-Host "         $keystorePath" -ForegroundColor Yellow
    Write-Host "  上書きすると、既存の証明書は復元できません。" -ForegroundColor Yellow
    $answer = Read-Host "  上書きして続行しますか？ (y/N)"
    if ($answer -ne "y" -and $answer -ne "Y") {
        Write-Host ""
        Write-Host "  中止しました。既存のキーストアはそのままです。"
        Write-Host ""
        exit 0
    }
    Remove-Item $keystorePath -Force
}

# ------------------------------------------------------------
# 4. キーストアのパスワードを利用者に入力させる
#    スクリプトに直書きしない。誰でも読める場所にパスワードを
#    残さないため、必ずその場で入力してもらう。
# ------------------------------------------------------------
Write-Host ""
$securePw1 = Read-Host "  キーストアのパスワードを入力してください（6文字以上）" -AsSecureString
$securePw2 = Read-Host "  確認のため、もう一度入力してください" -AsSecureString

# SecureStringは keytool に渡すために、この場だけ平文に戻す。
# 使い終わったら環境変数・変数の両方をすぐに消す。
$bstr1 = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePw1)
$plainPw1 = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr1)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr1)

$bstr2 = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePw2)
$plainPw2 = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr2)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr2)

if ($plainPw1 -ne $plainPw2) {
    Write-Host ""
    Write-Host "  [エラー] 入力したパスワードが一致しませんでした。" -ForegroundColor Red
    Write-Host ""
    exit 1
}

if ([string]::IsNullOrEmpty($plainPw1) -or $plainPw1.Length -lt 6) {
    Write-Host ""
    Write-Host "  [エラー] パスワードは6文字以上にしてください。" -ForegroundColor Red
    Write-Host ""
    exit 1
}

# ------------------------------------------------------------
# 5. keytoolで自己署名証明書のPKCS12キーストアを作る
#
#    パスワードはコマンドライン引数に直接書くと、他のプロセスから
#    見える恐れがあるため、環境変数経由(-storepass:env)で渡す。
# ------------------------------------------------------------
$env:WOT_KEYSTORE_PW = $plainPw1
$exitCode = 1

try {
    # -validity 825:
    #   証明書の有効期間。これより長くすると、ブラウザによっては
    #   ルールの上限を超えて証明書そのものを信頼しなくなる仕様があるため825日にしている。
    # -ext "SAN=dns:localhost,ip:127.0.0.1":
    #   SAN(Subject Alternative Name)。最近のブラウザは証明書の古い形式(CNのみ)では
    #   ホスト名を確認できず、SANが無いと接続そのものを拒否するため必須。
    & $keytool -genkeypair `
        -alias kaseihu `
        -keyalg RSA `
        -keysize 2048 `
        -storetype PKCS12 `
        -keystore $keystorePath `
        -validity 825 `
        -dname "CN=localhost, OU=WorkOfficeTani, O=WorkOfficeTani, C=JP" `
        -ext "SAN=dns:localhost,ip:127.0.0.1" `
        -storepass:env WOT_KEYSTORE_PW `
        -keypass:env WOT_KEYSTORE_PW

    $exitCode = $LASTEXITCODE
} finally {
    # パスワードをこの場限りで消す（環境変数・変数の両方）
    Remove-Item Env:\WOT_KEYSTORE_PW -ErrorAction SilentlyContinue
    $plainPw1 = $null
    $plainPw2 = $null
}

if ($exitCode -ne 0 -or -not (Test-Path $keystorePath)) {
    Write-Host ""
    Write-Host "  [エラー] キーストアの作成に失敗しました。" -ForegroundColor Red
    Write-Host "  上のkeytoolの出力を確認してください。" -ForegroundColor Red
    Write-Host ""
    exit 1
}

# ------------------------------------------------------------
# 6. .gitignore に追記する（キーストアには秘密鍵が入っているため
#    絶対にリポジトリに入れてはいけない）
# ------------------------------------------------------------
$gitignorePath = Join-Path $appDir ".gitignore"
$ignoreEntry = "config/https-keystore.p12"
if (Test-Path $gitignorePath) {
    $content = Get-Content $gitignorePath -Raw -Encoding UTF8
    if ($content -notmatch [regex]::Escape($ignoreEntry)) {
        Add-Content -Path $gitignorePath -Value "" -Encoding UTF8
        Add-Content -Path $gitignorePath -Value "### HTTPSキーストア（秘密鍵を含むためGit管理対象外） ###" -Encoding UTF8
        Add-Content -Path $gitignorePath -Value $ignoreEntry -Encoding UTF8
        Write-Host "  .gitignore に追記しました: $ignoreEntry"
    }
}

# ------------------------------------------------------------
# 7. 次にすることを案内する
# ------------------------------------------------------------
Write-Host ""
Write-Host "  ------------------------------------------------------------" -ForegroundColor Green
Write-Host "  キーストアを作成しました。" -ForegroundColor Green
Write-Host "  $keystorePath" -ForegroundColor Green
Write-Host "  ------------------------------------------------------------" -ForegroundColor Green
Write-Host ""
Write-Host "  HTTPSを有効にするには、次の環境変数を設定してください。"
Write-Host ""
Write-Host "    HTTPS_ENABLED = true"
Write-Host "    HTTPS_KEYSTORE = $keystorePath"
Write-Host "    HTTPS_KEYSTORE_PASSWORD = (今入力したパスワード)"
Write-Host "    COOKIE_SECURE = true"
Write-Host ""
Write-Host "  ポートも合わせて変えることを推奨します（HTTPSの慣例）。"
Write-Host ""
Write-Host "    SERVER_PORT = 8443"
Write-Host ""
Write-Host "  例（管理者権限のPowerShell）:"
Write-Host '    [Environment]::SetEnvironmentVariable("HTTPS_ENABLED", "true", "Machine")'
Write-Host "    [Environment]::SetEnvironmentVariable(`"HTTPS_KEYSTORE`", `"$keystorePath`", `"Machine`")"
Write-Host '    [Environment]::SetEnvironmentVariable("HTTPS_KEYSTORE_PASSWORD", "(パスワード)", "Machine")'
Write-Host '    [Environment]::SetEnvironmentVariable("COOKIE_SECURE", "true", "Machine")'
Write-Host '    [Environment]::SetEnvironmentVariable("SERVER_PORT", "8443", "Machine")'
Write-Host ""
Write-Host "  [注意] 自己署名証明書のため、ブラウザで開くと" -ForegroundColor Yellow
Write-Host "         「この接続ではプライバシーが保護されません」等の警告が毎回出ます。" -ForegroundColor Yellow
Write-Host "         警告を消したい場合は、この証明書をWindowsの" -ForegroundColor Yellow
Write-Host "         「信頼されたルート証明機関」に登録する必要がありますが、" -ForegroundColor Yellow
Write-Host "         それはシステム設定の変更にあたるため、このスクリプトでは行いません。" -ForegroundColor Yellow
Write-Host "         必要であれば、利用者の判断でWindowsの証明書管理(certmgr.msc)から" -ForegroundColor Yellow
Write-Host "         手動で登録してください。" -ForegroundColor Yellow
Write-Host ""
