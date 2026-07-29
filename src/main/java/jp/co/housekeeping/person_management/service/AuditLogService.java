package jp.co.housekeeping.person_management.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 監査ログ（誰が・いつ・何を見たか）を記録するサービス。
 *
 * 記録先を2箇所に分けている。
 *   1) DBテーブル access_logs … 画面(1-7-7)から検索・集計するため
 *   2) ファイル logs/audit/audit-YYYY-MM-DD.log … 追記のみ。DBを消されても残す
 *
 * ログと守るべきデータが同じDB・同じ権限内にしか無いと、侵入時に
 * DELETE FROM access_logs の一行で証跡を消されるため、必ず2箇所に書く。
 * ファイルを別媒体へ退避する運用手順は docs/NETWORK_RESTRICTION.md を参照。
 *
 * 【最重要】このクラスは絶対に例外を外へ投げない。
 * 監査ログの不具合で業務システム全体が停止することを防ぐため、
 * 記録に失敗した場合は標準エラーへ出すだけで処理を続行する。
 */
@Service
public class AuditLogService {

    /** イベント種別 */
    public static final String EVENT_ACCESS        = "ACCESS";
    public static final String EVENT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String EVENT_LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String EVENT_LOGIN_LOCKED  = "LOGIN_LOCKED";
    public static final String EVENT_LOGOUT        = "LOGOUT";
    /** バックアップコードでのログイン。認証アプリが使えない状況を意味するため個別に記録する */
    public static final String EVENT_LOGIN_BACKUP  = "LOGIN_BACKUP_CODE";
    /** TOTP利用者の新規登録・無効化など、認証設定そのものの変更 */
    public static final String EVENT_AUTH_CHANGE   = "AUTH_CHANGE";

    /**
     * クエリ文字列のうち、値をそのまま記録してよいパラメータ名。
     * ここに無いパラメータの値は *** に伏せる。
     *
     * 監査ログ自体が新たな個人情報の漏えい源にならないようにするための許可リスト方式。
     * 例えば稼働管理簿の search パラメータには氏名が入りうるため、値は記録しない。
     */
    private static final Set<String> LOGGABLE_PARAMS = Set.of(
        "month", "year", "ym", "workMonth", "date",
        "id", "personId", "customerId", "salesId", "detailId", "introductionId",
        "dl", "mode", "download", "page", "tab", "type", "status",
        "error", "locked", "sent", "confirmed",
        "deletedPerson", "deletedCustomer"
    );

    private static final DateTimeFormatter TS_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // JdbcTemplateのBeanが無い環境(@WebMvcTest等のスライステスト)でも
    // このサービスの生成に失敗しないようObjectProviderで受ける。
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    private final boolean enabled;
    private final Path logDir;
    private final int retentionDays;

    /** 日付が変わったタイミングでのみ古いログの掃除を行うための記録 */
    private LocalDate lastCleanupDate = null;

    public AuditLogService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                            @Value("${app.audit.enabled:true}") boolean enabled,
                            @Value("${app.audit.log-dir:logs/audit}") String logDir,
                            @Value("${app.audit.retention-days:365}") int retentionDays) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.enabled = enabled;
        this.logDir = Paths.get(logDir);
        this.retentionDays = retentionDays;
    }

    /**
     * 監査ログを1件記録する。
     * DBとファイルの両方に書き、どちらが失敗しても例外は投げない。
     */
    public void record(AuditEvent event) {
        if (!enabled || event == null) {
            return;
        }
        // URIに ;jsessionid=... が付いた状態で記録すると、監査ログを読める人が
        // 生きているセッションを乗っ取れてしまう。記録の入口で必ず落とす。
        event.uri = stripPathParameters(event.uri);
        // 片方が失敗しても、もう片方は必ず試みる（両方が同時に消されることを防ぐのが目的のため）
        try {
            writeToFile(event);
        } catch (Exception e) {
            System.err.println("[AuditLog] ファイルへの記録に失敗: " + e.getMessage());
        }
        try {
            writeToDatabase(event);
        } catch (Exception e) {
            System.err.println("[AuditLog] DBへの記録に失敗: " + e.getMessage());
        }
        try {
            cleanupIfDateChanged();
        } catch (Exception e) {
            System.err.println("[AuditLog] 古いログの整理に失敗: " + e.getMessage());
        }
    }

    // ─── 記録先1: ファイル（追記のみ） ─────────────────────────

    private synchronized void writeToFile(AuditEvent e) throws IOException {
        Files.createDirectories(logDir);
        Path file = logDir.resolve("audit-" + e.occurredAt.format(FILE_DATE_FORMAT) + ".log");

        // タブ・改行を除去してから連結する。除去しないと、URIに改行を仕込むことで
        // ログファイル上に偽の行を作られる（ログインジェクション）。
        String line = String.join("\t",
            e.occurredAt.format(TS_FORMAT),
            safe(e.eventType),
            safe(e.sessionKey),
            safe(e.clientIp),
            safe(e.httpMethod),
            safe(e.uri),
            safe(e.queryMasked),
            e.statusCode == null ? "" : String.valueOf(e.statusCode),
            e.durationMs == null ? "" : String.valueOf(e.durationMs),
            String.valueOf(e.authenticated),
            safe(e.username),
            safe(e.userAgent)
        ) + System.lineSeparator();

        Files.writeString(file, line, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ─── 記録先2: DBテーブル ────────────────────────────────

    private void writeToDatabase(AuditEvent e) {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            return; // DBが使えない環境ではファイルのみに記録する
        }
        jdbc.update(
            "INSERT INTO access_logs (occurred_at, event_type, session_key, client_ip, "
            + "http_method, uri, query_masked, status_code, duration_ms, authenticated, user_agent, username) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            java.sql.Timestamp.valueOf(e.occurredAt),
            trim(e.eventType, 30),
            trim(e.sessionKey, 16),
            trim(e.clientIp, 45),
            trim(e.httpMethod, 10),
            trim(e.uri, 500),
            trim(e.queryMasked, 500),
            e.statusCode,
            e.durationMs,
            e.authenticated,
            trim(e.userAgent, 300),
            trim(e.username, 50));
    }

    // ─── 保持期間を超えたログの整理（1日1回） ────────────────────

    private void cleanupIfDateChanged() throws IOException {
        LocalDate today = LocalDate.now();
        if (today.equals(lastCleanupDate)) {
            return;
        }
        lastCleanupDate = today;

        LocalDate threshold = today.minusDays(retentionDays);

        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc != null) {
            jdbc.update("DELETE FROM access_logs WHERE occurred_at < ?",
                java.sql.Timestamp.valueOf(threshold.atStartOfDay()));
        }

        if (!Files.isDirectory(logDir)) {
            return;
        }
        try (var files = Files.list(logDir)) {
            files.filter(p -> p.getFileName().toString().startsWith("audit-"))
                 .filter(p -> isOlderThan(p.getFileName().toString(), threshold))
                 .forEach(p -> {
                     try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                 });
        }
    }

    private boolean isOlderThan(String fileName, LocalDate threshold) {
        // audit-YYYY-MM-DD.log から日付部分を取り出す
        try {
            String datePart = fileName.substring("audit-".length(), fileName.length() - ".log".length());
            return LocalDate.parse(datePart).isBefore(threshold);
        } catch (Exception e) {
            return false; // 想定外の名前のファイルは触らない
        }
    }

    // ─── ユーティリティ ────────────────────────────────────

    /**
     * クエリ文字列を、許可リストに無いパラメータの値を伏せた形に変換する。
     * 例: month=2026-07&amp;search=山田 → month=2026-07&amp;search=***
     */
    public static String maskQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String pair : queryString.split("&")) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                sb.append(LOGGABLE_PARAMS.contains(pair) ? pair : "***");
                continue;
            }
            String name = pair.substring(0, eq);
            sb.append(name).append('=');
            sb.append(LOGGABLE_PARAMS.contains(name) ? pair.substring(eq + 1) : "***");
        }
        return sb.toString();
    }

    /**
     * セッションIDをそのまま保存するとログを見た人がセッションを乗っ取れてしまうため、
     * ハッシュ化した先頭16文字だけを「同一セッションの識別子」として保存する。
     */
    public static String toSessionKey(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * URIからパスパラメータ（; 以降）を取り除く。
     *
     * ブラウザがCookieを使わない場合、Tomcatは /menu;jsessionid=XXXX の形式で
     * セッションIDをURLに載せる。これをそのまま記録すると、監査ログ自体が
     * セッション乗っ取りの材料になるため、記録前に必ず落とす。
     * （URL方式のセッション追跡自体も application.yml で無効化しているが、
     * 　設定が戻された場合に備えて記録側でも二重に防ぐ）
     */
    public static String stripPathParameters(String uri) {
        if (uri == null) {
            return null;
        }
        int idx = uri.indexOf(';');
        return idx < 0 ? uri : uri.substring(0, idx);
    }

    /** タブ・改行・制御文字を除去する（ログファイル上に偽の行を作られないため） */
    private static String safe(String v) {
        if (v == null) {
            return "";
        }
        return v.replaceAll("[\\p{Cntrl}]", " ");
    }

    private static String trim(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    /** 監査ログ1件分のデータ */
    public static class AuditEvent {
        public LocalDateTime occurredAt = LocalDateTime.now();
        public String  eventType = EVENT_ACCESS;
        public String  sessionKey;
        public String  clientIp;
        public String  httpMethod;
        public String  uri;
        public String  queryMasked;
        public Integer statusCode;
        public Integer durationMs;
        public boolean authenticated;
        public String  userAgent;
        /** 誰が。TOTP認証(対策4)でログインした利用者名。未ログイン時はnull */
        public String  username;
    }

    /** ログイン中の利用者名を保持するセッション属性名 */
    public static final String SESSION_USERNAME = "authUsername";
}
