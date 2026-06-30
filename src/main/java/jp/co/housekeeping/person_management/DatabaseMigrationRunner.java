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

            System.out.println("[Migration] persons テーブルのカラム追加完了（IF NOT EXISTS）");

        } catch (SQLException e) {
            System.err.println("[Migration] エラー: " + e.getMessage());
            throw e;
        }
    }
}
