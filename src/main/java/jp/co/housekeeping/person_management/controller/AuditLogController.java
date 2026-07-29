package jp.co.housekeeping.person_management.controller;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.service.AuditLogService;

/**
 * 監査ログ閲覧画面（1-7-7）。
 *
 * access_logs テーブル（AuditLogServiceが記録）を絞り込み検索して表示する。
 * SQLは必ず ? プレースホルダを使い、値を直接文字列連結しない。
 */
@Controller
public class AuditLogController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 一覧の最大表示件数 */
    private static final int MAX_ROWS = 1000;

    @GetMapping("/audit-log")
    public String auditLog(@RequestParam(required = false) String from,
                            @RequestParam(required = false) String to,
                            @RequestParam(required = false) String eventType,
                            @RequestParam(required = false) String clientIp,
                            HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("eventType", eventType);
        model.addAttribute("clientIp", clientIp);

        // from/to はパース出来ない場合、その条件を無視する（OtherMenuController.systemQa の YearMonth.parse と同じ考え方）
        LocalDate fromDate = null;
        LocalDate toDate = null;
        try {
            if (from != null && !from.isBlank()) fromDate = LocalDate.parse(from);
        } catch (Exception ignored) {}
        try {
            if (to != null && !to.isBlank()) toDate = LocalDate.parse(to);
        } catch (Exception ignored) {}

        List<LogRow> rows = new ArrayList<>();
        boolean truncated = false;
        long totalCount = 0;
        long loginFailureCount = 0;
        String loadError = null;

        // 一覧・件数サマリ共通の絞り込み条件（期間・IP）
        StringBuilder commonWhere = new StringBuilder(" WHERE 1=1");
        List<Object> commonParams = new ArrayList<>();
        if (fromDate != null) {
            commonWhere.append(" AND occurred_at >= ?");
            commonParams.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            commonWhere.append(" AND occurred_at < ?");
            commonParams.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }
        if (clientIp != null && !clientIp.isBlank()) {
            commonWhere.append(" AND client_ip = ?");
            commonParams.add(clientIp);
        }

        // 一覧・件数用: イベント種別も絞り込みに含める
        StringBuilder listWhere = new StringBuilder(commonWhere);
        List<Object> listParams = new ArrayList<>(commonParams);
        if (eventType != null && !eventType.isBlank()) {
            listWhere.append(" AND event_type = ?");
            listParams.add(eventType);
        }

        try {
            Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM access_logs" + listWhere, Long.class, listParams.toArray());
            totalCount = total != null ? total : 0;

            // ログイン失敗件数は、絞り込んだイベント種別に関わらず「同じ期間・IP条件」での件数
            List<Object> failParams = new ArrayList<>(commonParams);
            failParams.add(AuditLogService.EVENT_LOGIN_FAILURE);
            Long fail = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM access_logs" + commonWhere + " AND event_type = ?",
                Long.class, failParams.toArray());
            loginFailureCount = fail != null ? fail : 0;

            String sql = "SELECT id, occurred_at, event_type, session_key, client_ip, http_method, uri, "
                + "query_masked, status_code, duration_ms, authenticated, user_agent FROM access_logs"
                + listWhere + " ORDER BY occurred_at DESC LIMIT " + MAX_ROWS;
            rows = jdbcTemplate.query(sql, ROW_MAPPER, listParams.toArray());

            truncated = totalCount > MAX_ROWS;
        } catch (Exception e) {
            // access_logs テーブルが未作成の環境（マイグレーション前）などでも画面を落とさない
            loadError = e.getClass().getSimpleName() + ": " + e.getMessage();
            rows = new ArrayList<>();
            totalCount = 0;
            loginFailureCount = 0;
            truncated = false;
        }

        model.addAttribute("rows", rows);
        model.addAttribute("truncated", truncated);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("loginFailureCount", loginFailureCount);
        model.addAttribute("loadError", loadError);

        return "audit-log";
    }

    private static final RowMapper<LogRow> ROW_MAPPER = (rs, rowNum) -> {
        LogRow r = new LogRow();
        r.id = rs.getLong("id");
        Timestamp ts = rs.getTimestamp("occurred_at");
        r.occurredAt = ts != null ? ts.toLocalDateTime().toString().replace("T", " ") : "";
        r.eventType = rs.getString("event_type");
        r.sessionKey = rs.getString("session_key");
        r.clientIp = rs.getString("client_ip");
        r.httpMethod = rs.getString("http_method");
        r.uri = rs.getString("uri");
        r.queryMasked = rs.getString("query_masked");

        int status = rs.getInt("status_code");
        r.statusCode = rs.wasNull() ? "" : String.valueOf(status);

        int duration = rs.getInt("duration_ms");
        r.durationMs = rs.wasNull() ? "" : String.valueOf(duration);

        r.authenticated = rs.getBoolean("authenticated");
        r.userAgent = rs.getString("user_agent");
        return r;
    };

    /** 一覧1行分（Thymeleafから参照するためpublicフィールド＋getterの形にする） */
    public static class LogRow {
        public Long id;
        public String occurredAt, eventType, sessionKey, clientIp, httpMethod, uri, queryMasked;
        public String statusCode, durationMs;
        public boolean authenticated;
        public String userAgent;

        public Long getId()             { return id; }
        public String getOccurredAt()   { return occurredAt; }
        public String getEventType()    { return eventType; }
        public String getSessionKey()   { return sessionKey; }
        public String getClientIp()     { return clientIp; }
        public String getHttpMethod()   { return httpMethod; }
        public String getUri()          { return uri; }
        public String getQueryMasked()  { return queryMasked; }
        public String getStatusCode()   { return statusCode; }
        public String getDurationMs()   { return durationMs; }
        public boolean isAuthenticated(){ return authenticated; }
        public String getUserAgent()    { return userAgent; }
    }
}
