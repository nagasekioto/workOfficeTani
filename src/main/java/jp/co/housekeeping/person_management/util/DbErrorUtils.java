package jp.co.housekeeping.person_management.util;

/**
 * DB例外の判別ヘルパー。
 *
 * このプロジェクトは Spring Data JDBC を使用しているため、外部キー制約違反は
 * Spring Data JPA/Hibernateでよく使われる DataIntegrityViolationException ではなく
 * org.springframework.data.relational.core.conversion.DbActionExecutionException
 * として投げられる（内部の cause チェーンの奥に本来の PSQLException が入っている）。
 *
 * 例外の「型」だけで判定すると環境やSpring Data実装のバージョンによって
 * 変わりうるため、cause チェーンを辿って PostgreSQLのSQLState
 * （23503 = foreign_key_violation）を直接確認する、より確実な方法を取る。
 */
public final class DbErrorUtils {

    private static final String FOREIGN_KEY_VIOLATION_SQLSTATE = "23503";

    private DbErrorUtils() {}

    /**
     * 例外のcauseチェーンを辿り、PostgreSQLの外部キー制約違反
     * (SQLState 23503) が原因かどうかを判定する。
     */
    public static boolean isForeignKeyViolation(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 20) {
            if (current instanceof java.sql.SQLException sqlEx) {
                if (FOREIGN_KEY_VIOLATION_SQLSTATE.equals(sqlEx.getSQLState())) {
                    return true;
                }
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
