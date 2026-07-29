package jp.co.housekeeping.person_management;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * アプリ起動時にDBマイグレーションを自動実行する。
 * IF NOT EXISTS を使うため冪等（何度実行しても安全）。
 */
@Component
public class DatabaseMigrationRunner implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection();
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
            throw e;
        }
    }
}
