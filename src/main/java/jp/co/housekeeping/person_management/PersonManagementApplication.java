package jp.co.housekeeping.person_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PersonManagementApplication {

	public static void main(String[] args) {
		checkRequiredEnv();
		SpringApplication.run(PersonManagementApplication.class, args);
	}

	/**
	 * 起動前に、必須の環境変数が設定されているか確認する。
	 *
	 * ここで確認しているのは DB_PASSWORD だけ。
	 * Spring の起動処理に入るとDBへの接続が先に走ってしまい、
	 * 未設定の場合はPostgreSQLの英語のスタックトレースが延々と出るだけで、
	 * 何が悪いのか分からないため。
	 *
	 * TOTP_ENCRYPTION_KEY は未設定でも起動させる（初回セットアップ画面に案内を
	 * 出したいため）。そちらの警告は StartupSecurityCheck が行う。
	 */
	private static void checkRequiredEnv() {
		String dbPassword = System.getenv("DB_PASSWORD");
		if (dbPassword != null && !dbPassword.isBlank()) {
			return;
		}
		// System.getenv に無くても -DDB_PASSWORD=... のようにシステムプロパティで
		// 渡されている場合があるため、そちらも確認する
		String fromProperty = System.getProperty("DB_PASSWORD");
		if (fromProperty != null && !fromProperty.isBlank()) {
			return;
		}

		String bar = "=".repeat(78);
		System.err.println();
		System.err.println(bar);
		System.err.println("  【設定が必要です】データベースのパスワードが設定されていません");
		System.err.println(bar);
		System.err.println("  環境変数 DB_PASSWORD が未設定のため、システムを起動できません。");
		System.err.println();
		System.err.println("  管理者権限のPowerShellで、以下を実行してください。");
		System.err.println("    [Environment]::SetEnvironmentVariable(\"DB_PASSWORD\", \"(実際のパスワード)\", \"Machine\")");
		System.err.println();
		System.err.println("  実行後、PowerShellを開き直してから、もう一度起動してください。");
		System.err.println();
		System.err.println("  ※ 設定ファイルにパスワードを直接書く運用は廃止しました。");
		System.err.println("     詳細は docs/PASSWORD_POLICY.md を参照してください。");
		System.err.println(bar);
		System.err.println();
		System.exit(1);
	}

}
