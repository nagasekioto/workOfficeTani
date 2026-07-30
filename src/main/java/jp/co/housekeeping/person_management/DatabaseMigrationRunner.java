package jp.co.housekeeping.person_management;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * アプリ起動時にDBマイグレーションを自動実行する。
 * IF NOT EXISTS を使うため冪等（何度実行しても安全）。
 *
 * 【接続について】
 * ここは ALTER TABLE / CREATE TABLE を実行するため、
 * テーブルの所有者権限が必要になる。
 *
 * 一方でアプリの通常動作は、権限を絞ったロール（kaseihu_app）で
 * 接続させたい（対策1: 最小権限の原則）。
 * そのため、マイグレーション専用の接続情報が設定されていればそれを使い、
 * 設定されていなければ従来どおり通常の接続を使う。
 *
 * **設定していない場合の動作は従来と完全に同じ**。
 * 既存の起動方法を壊さないため、あえてフォールバックを残している。
 *
 * ロールの作り方は scripts\create-db-roles.sql を参照。
 */
@Component
public class DatabaseMigrationRunner implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    // マイグレーション専用の接続情報。未設定なら通常の接続を使う。
    @Value("${app.db.migration.username:}")
    private String migrationUsername;

    @Value("${app.db.migration.password:}")
    private String migrationPassword;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    /**
     * DDLを実行するための接続を取り出す。
     *
     * 専用の接続情報があるときは、コネクションプールを介さず
     * DriverManager で直接つなぐ。起動時に一度だけ使う接続なので、
     * プールを1つ増やして常駐させる必要が無いため。
     */
    private Connection openConnection() throws SQLException {
        if (migrationUsername == null || migrationUsername.isBlank()) {
            return dataSource.getConnection();
        }
        System.out.println("[Migration] マイグレーション専用の接続を使用します（利用者: "
            + migrationUsername + "）");
        return DriverManager.getConnection(jdbcUrl, migrationUsername, migrationPassword);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {

            // ─── schema-update-6: persons テーブル拡張（就職希望条件） ───
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_location TEXT");
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_duties TEXT");
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS desired_types TEXT");
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS specific_days TEXT");
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_available_hours TEXT");
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_start_period TEXT");

            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS emergency_relation TEXT");
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS emergency_phone TEXT");
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS babysitter_exp TEXT");
            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS babysitter_avail TEXT");

            stmt.execute(
                "ALTER TABLE persons ADD COLUMN IF NOT EXISTS notes TEXT");

            // ─── customers テーブル拡張 ───────────────────────
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS staff_name TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS staff_phone TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS staff_notes TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS job_contents TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_type TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_temp_date TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_weekly_days TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_weekly_start TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_weekly_end TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS family_adults INTEGER");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS family_children INTEGER");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS introducer_name TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS intro_route TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS intro_other_text TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS pet_type TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS pet_other_text TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS interview_none BOOLEAN");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS interview_date1 TEXT");
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS interview_date2 TEXT");

            // ─── introductions テーブル拡張（求人管理簿用） ───────
            stmt.execute("ALTER TABLE introductions ADD COLUMN IF NOT EXISTS emp_status TEXT");
            stmt.execute("ALTER TABLE introductions ADD COLUMN IF NOT EXISTS hire_result TEXT");
            stmt.execute("ALTER TABLE introductions ADD COLUMN IF NOT EXISTS ledger_remarks TEXT");
            stmt.execute("ALTER TABLE introductions ADD COLUMN IF NOT EXISTS labor_contract TEXT");
            stmt.execute("ALTER TABLE introductions ADD COLUMN IF NOT EXISTS rishoku_status TEXT");
            stmt.execute("ALTER TABLE introductions ADD COLUMN IF NOT EXISTS henreikin TEXT");
            stmt.execute("ALTER TABLE introductions ADD COLUMN IF NOT EXISTS emp_period TEXT");

            // ─── register_records テーブル拡張（会費・振込済みフラグ） ─
            stmt.execute("ALTER TABLE register_records ADD COLUMN IF NOT EXISTS membership_fee INTEGER");
            stmt.execute("ALTER TABLE register_records ADD COLUMN IF NOT EXISTS transferred BOOLEAN NOT NULL DEFAULT FALSE");

            // ─── persons テーブル拡張（1-1-7 会費） ───────────────
            stmt.execute("ALTER TABLE persons ADD COLUMN IF NOT EXISTS membership_fee TEXT"); // '有' / '無'
            stmt.execute("ALTER TABLE persons ADD COLUMN IF NOT EXISTS membership_fee_amount INTEGER"); // 1550 or 350

            // ─── 会費(1-1-7)の月別・振込確認チェック ──────────────
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS membership_confirmations (" +
                "  id BIGSERIAL PRIMARY KEY," +
                "  person_id BIGINT NOT NULL," +
                "  work_month VARCHAR(7) NOT NULL," +
                "  confirmed BOOLEAN NOT NULL DEFAULT FALSE," +
                "  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  UNIQUE (person_id, work_month)" +
                ")");

            // ─── 監査ログ（誰が・いつ・何を見たか） ──────────────
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS access_logs (" +
                "  id BIGSERIAL PRIMARY KEY," +
                "  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  event_type VARCHAR(30) NOT NULL," +      // ACCESS / LOGIN_SUCCESS / LOGIN_FAILURE / LOGIN_LOCKED / LOGOUT
                "  session_key VARCHAR(16)," +               // セッションIDのハッシュ先頭16文字（生IDは保存しない）
                "  client_ip VARCHAR(45)," +
                "  http_method VARCHAR(10)," +
                "  uri VARCHAR(500)," +
                "  query_masked VARCHAR(500)," +             // 許可リスト外のパラメータ値は***に伏せた状態で保存
                "  status_code INTEGER," +
                "  duration_ms INTEGER," +
                "  authenticated BOOLEAN," +
                "  user_agent VARCHAR(300)" +
                ")");
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_access_logs_occurred_at ON access_logs (occurred_at DESC)");
            // 利用者ごとのTOTP認証(対策4)を入れたことで「誰が」を記録できるようになった
            stmt.execute("ALTER TABLE access_logs ADD COLUMN IF NOT EXISTS username VARCHAR(50)");

            // ─── TOTP認証（Google Authenticator）の利用者 ──────────
            // totp_secret_enc は AES-GCM で暗号化して保存する。
            // 暗号化キーは環境変数 TOTP_ENCRYPTION_KEY（DBの外）に置いているため、
            // DBのダンプだけを手に入れてもTOTPコードは生成できない。
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS auth_users (" +
                "  id BIGSERIAL PRIMARY KEY," +
                "  username VARCHAR(50) NOT NULL UNIQUE," +
                "  display_name VARCHAR(100)," +
                "  totp_secret_enc TEXT NOT NULL," +
                "  last_totp_step BIGINT," +          // 同じコードの使い回しを防ぐため最後に使った時間枠を記録
                "  enrolled_at TIMESTAMP," +           // 初回のコード確認が通った日時。nullなら登録途中
                "  disabled_at TIMESTAMP," +           // 値が入っていれば無効（退職者など）
                "  last_login_at TIMESTAMP," +
                "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");

            // ─── バックアップコード（認証アプリを入れた端末の紛失に備える） ──
            // 平文は発行直後に1度だけ画面表示し、DBにはSHA-256のハッシュのみ保存する。
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS auth_backup_codes (" +
                "  id BIGSERIAL PRIMARY KEY," +
                "  user_id BIGINT NOT NULL," +
                "  code_hash VARCHAR(64) NOT NULL," +
                "  used_at TIMESTAMP," +               // 1回使ったら日時が入り、以降は使えない
                "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_auth_backup_codes_user ON auth_backup_codes (user_id)");

            System.out.println("[Migration] persons テーブルのカラム追加完了（IF NOT EXISTS）");

        } catch (SQLException e) {
            System.err.println("[Migration] エラー: " + e.getMessage());

            // 権限不足はこの処理で最も起こりやすい失敗であり、
            // かつメッセージを読んでも原因が分かりにくい。
            // 「アプリが起動しない」だけで放り出さず、直し方まで示す。
            if (isPermissionError(e)) {
                printPermissionHelp();
            }
            throw e;
        }
    }

    private boolean isPermissionError(SQLException e) {
        // PostgreSQLの権限不足は SQLState 42501 (insufficient_privilege)
        String state = e.getSQLState();
        if ("42501".equals(state)) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null
            && (msg.contains("permission denied") || msg.contains("must be owner"));
    }

    private void printPermissionHelp() {
        String bar = "=".repeat(78);
        System.err.println();
        System.err.println(bar);
        System.err.println("  【権限不足】DBのスキーマを変更する権限がありません");
        System.err.println(bar);
        System.err.println("  起動時のマイグレーション(ALTER TABLE / CREATE TABLE)には、");
        System.err.println("  テーブルの所有者権限が必要です。");
        System.err.println("  権限を絞ったロールで接続している場合、ここで必ず失敗します。");
        System.err.println();
        System.err.println("  マイグレーション専用の接続情報を設定してください。");
        System.err.println("    [Environment]::SetEnvironmentVariable(\"DB_MIGRATION_USER\", \"kaseihu_owner\", \"User\")");
        System.err.println("    [Environment]::SetEnvironmentVariable(\"DB_MIGRATION_PASSWORD\", \"(所有者のパスワード)\", \"User\")");
        System.err.println();
        System.err.println("  設定後、PowerShellを開き直してから起動し直してください。");
        System.err.println();
        System.err.println("  すぐに元に戻したい場合は、DB_USER と DB_PASSWORD を");
        System.err.println("  postgres のものに戻すか、以下でロール分離自体を取り消せます。");
        System.err.println("    psql -U postgres -h localhost -d kaseihu -f scripts\\rollback-db-roles.sql");
        System.err.println(bar);
        System.err.println();
    }
}
