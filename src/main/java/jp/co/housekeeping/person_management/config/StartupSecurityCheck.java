package jp.co.housekeeping.person_management.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jp.co.housekeeping.person_management.service.AuthUserService;
import jp.co.housekeeping.person_management.service.SecretCipher;

/**
 * 起動時に、秘密の値の設定漏れを検出して知らせる。
 *
 * 設定を忘れたまま動いてしまうと、いざログインしようとした時や
 * 事故が起きた時になって初めて気付くことになる。
 * 起動時に日本語で明確に伝えることで、その場で直せるようにしている。
 */
@Component
@Order(1) // DatabaseMigrationRunnerより先に動かす
public class StartupSecurityCheck implements ApplicationRunner {

    private final SecretCipher secretCipher;
    private final AuthUserService authUserService;
    private final boolean httpsEnabled;
    private final boolean cookieSecure;
    private final int serverPort;

    public StartupSecurityCheck(SecretCipher secretCipher, AuthUserService authUserService,
            @Value("${server.ssl.enabled:false}") boolean httpsEnabled,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSecure,
            @Value("${server.port:8080}") int serverPort) {
        this.secretCipher = secretCipher;
        this.authUserService = authUserService;
        this.httpsEnabled = httpsEnabled;
        this.cookieSecure = cookieSecure;
        this.serverPort = serverPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        // TOTPの暗号化キー。未設定でも起動自体は止めない
        // （初回セットアップ画面に案内を出すため。ここで落とすと画面すら開けない）
        if (!secretCipher.isKeyConfigured()) {
            printBox(
                "【設定が必要です】TOTPの暗号化キーが設定されていません",
                "ログインに必要な設定が未完了です。このままではログインできません。",
                "",
                "管理者権限のPowerShellで、以下を実行してください。",
                "  [Environment]::SetEnvironmentVariable(\"TOTP_ENCRYPTION_KEY\", \"(30文字以上のランダムな文字列)\", \"Machine\")",
                "",
                "実行後、PowerShellを開き直してからシステムを起動し直してください。",
                "この値は必ず、このパソコンとは別の場所(紙・別クラウド)にも控えてください。",
                "詳細は SETUP_NEW_PC.md の手順4を参照してください。");
        }

        // HTTPSとCookieのsecure設定が食い違っていないか確認する。
        // ここで止めると初回セットアップ画面すら開けなくなるため、警告のみで起動は続ける。
        String scheme = httpsEnabled ? "https" : "http";
        String loginUrl = scheme + "://localhost:" + serverPort + "/login";

        if (httpsEnabled && !cookieSecure) {
            printBox(
                "【設定の不整合】HTTPSが有効なのにCookieにsecureが付いていません",
                "server.ssl.enabled=true なのに、Cookieのsecureがfalseのままです。",
                "HTTPSなのにCookieに secure が付いていない状態です。",
                "COOKIE_SECURE=true を設定してください。");
        }
        if (!httpsEnabled && cookieSecure) {
            printBox(
                "【設定の不整合】ログインできません",
                "HTTPS_ENABLEDが無効なのに、CookieのsecureがTrueになっています。",
                "HTTPでは secure 付きCookieがブラウザから送られないため、ログインできません。",
                "COOKIE_SECURE を false にするか、HTTPSを有効にしてください。");
        }

        // 利用者が1人もいない場合の案内
        try {
            if (authUserService.hasNoActiveUser()) {
                printBox(
                    "【初回セットアップが必要です】ログイン利用者が登録されていません",
                    "ブラウザで " + loginUrl + " を開くと、",
                    "初回セットアップ画面が自動的に表示されます。",
                    "",
                    "画面の案内に従って Google Authenticator を登録し、",
                    "表示されるバックアップコードを必ず印刷して保管してください。");
            }
        } catch (Exception e) {
            // DBがまだ整っていない場合（初回のマイグレーション前など）は何もしない
            System.err.println("[起動チェック] 利用者の確認をスキップしました: " + e.getMessage());
        }
    }

    private void printBox(String title, String... lines) {
        String bar = "=".repeat(78);
        System.out.println();
        System.out.println(bar);
        System.out.println("  " + title);
        System.out.println(bar);
        for (String line : lines) {
            System.out.println("  " + line);
        }
        System.out.println(bar);
        System.out.println();
    }
}
