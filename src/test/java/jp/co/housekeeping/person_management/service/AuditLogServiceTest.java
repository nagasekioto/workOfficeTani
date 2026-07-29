package jp.co.housekeeping.person_management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import jp.co.housekeeping.person_management.service.AuditLogService.AuditEvent;

/**
 * AuditLogService の純粋な単体テスト（Springコンテキスト不要）。
 * JdbcTemplateのObjectProviderは getIfAvailable() が null を返すようにし、
 * DBが使えない環境でもファイルへの記録だけは行われることを確認する。
 */
class AuditLogServiceTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<JdbcTemplate> noDbProvider() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    // ─── maskQuery ─────────────────────────────────────────

    @Test
    void maskQueryは許可リストのパラメータの値をそのまま残す() {
        assertEquals("month=2026-07", AuditLogService.maskQuery("month=2026-07"));
    }

    @Test
    void maskQueryは許可リストに無いパラメータの値を伏せる() {
        assertEquals("search=***", AuditLogService.maskQuery("search=山田"));
    }

    @Test
    void maskQueryは複数パラメータを個別に判定する() {
        assertEquals("month=2026-07&search=***&id=5",
            AuditLogService.maskQuery("month=2026-07&search=山田&id=5"));
    }

    @Test
    void maskQueryはnullと空文字で空文字を返す() {
        assertEquals("", AuditLogService.maskQuery(null));
        assertEquals("", AuditLogService.maskQuery(""));
    }

    // ─── toSessionKey ──────────────────────────────────────

    @Test
    void toSessionKeyは16文字で入力値そのものとは異なり同じ入力なら毎回同じ結果になる() {
        String key1 = AuditLogService.toSessionKey("abc");
        String key2 = AuditLogService.toSessionKey("abc");

        assertEquals(16, key1.length());
        assertNotEquals("abc", key1);
        assertEquals(key1, key2);
    }

    @Test
    void toSessionKeyはnullで空文字を返す() {
        assertEquals("", AuditLogService.toSessionKey(null));
    }

    // ─── record（ファイル出力） ───────────────────────────────

    @Test
    void recordを呼ぶとlogDirにaudit日付ログが作られURIが1行書かれる(@TempDir Path tempDir) throws IOException {
        AuditLogService service = new AuditLogService(noDbProvider(), true, tempDir.toString(), 365);

        AuditEvent event = new AuditEvent();
        event.eventType = AuditLogService.EVENT_ACCESS;
        event.uri = "/audit-log";
        event.httpMethod = "GET";

        service.record(event);

        Path logFile = tempDir.resolve(
            "audit-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log");
        assertTrue(Files.exists(logFile), "ログファイルが作られていること");

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("/audit-log"));
    }

    @Test
    void uriに改行やタブを含んでいてもログファイルの行数は1行のままである(@TempDir Path tempDir) throws IOException {
        AuditLogService service = new AuditLogService(noDbProvider(), true, tempDir.toString(), 365);

        AuditEvent event = new AuditEvent();
        event.eventType = AuditLogService.EVENT_ACCESS;
        event.uri = "/injected\nFAKE-LINE\tstill-here";
        event.httpMethod = "GET";

        service.record(event);

        Path logFile = tempDir.resolve(
            "audit-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log");

        try (Stream<String> lines = Files.lines(logFile)) {
            assertEquals(1, lines.count(), "改行・タブが除去され1行のままであること（ログインジェクション対策）");
        }
    }

    // ─── stripPathParameters（セッションID漏えい対策） ──────────

    @Test
    void stripPathParametersはjsessionidを含むURIからセッションIDを落とす() {
        assertEquals("/menu",
            AuditLogService.stripPathParameters("/menu;jsessionid=722D07D958BD3E786132723E27925B83"));
    }

    @Test
    void stripPathParametersは通常のURIをそのまま返す() {
        assertEquals("/person/list", AuditLogService.stripPathParameters("/person/list"));
        assertEquals(null, AuditLogService.stripPathParameters(null));
    }

    @Test
    void recordはjsessionid付きURIをそのまま記録しない(@TempDir Path tempDir) throws IOException {
        AuditLogService service = new AuditLogService(noDbProvider(), true, tempDir.toString(), 365);

        AuditEvent event = new AuditEvent();
        event.eventType = AuditLogService.EVENT_ACCESS;
        event.uri = "/menu;jsessionid=722D07D958BD3E786132723E27925B83";

        service.record(event);

        Path logFile = tempDir.resolve(
            "audit-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log");
        String content = Files.readString(logFile);

        assertFalse(content.contains("jsessionid"),
            "監査ログを読んだ人がセッションを乗っ取れないよう、セッションIDが残っていないこと");
        assertTrue(content.contains("/menu"));
    }

    @Test
    void enabledがfalseならrecordを呼んでもファイルは作られない(@TempDir Path tempDir) {
        AuditLogService service = new AuditLogService(noDbProvider(), false, tempDir.toString(), 365);

        AuditEvent event = new AuditEvent();
        event.eventType = AuditLogService.EVENT_ACCESS;
        event.uri = "/audit-log";

        service.record(event);

        Path logFile = tempDir.resolve(
            "audit-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log");
        assertFalse(Files.exists(logFile));
    }
}
