package jp.co.housekeeping.person_management.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;

import jp.co.housekeeping.person_management.service.AuditLogService;
import jp.co.housekeeping.person_management.service.AuditLogService.AuditEvent;

/**
 * 全リクエストを1箇所で捕捉して監査ログに記録するInterceptor。
 *
 * 各コントローラ(14ファイル・97エンドポイント)には一切手を入れず、
 * ここだけで「誰が・いつ・何を見たか」を記録する。
 *
 * 【最重要】このクラスは絶対に例外を外へ投げない。
 * 監査ログの不具合で全画面が500エラーになることを防ぐ。
 */
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final String ATTR_START = "auditLogStartMillis";

    // AuditLogServiceのBeanが無い環境(@WebMvcTest等)でも動くようObjectProviderで受ける。
    // getIfAvailable()がnullを返した場合は何も記録せず素通しする。
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;

    public AuditLogInterceptor(ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            request.setAttribute(ATTR_START, System.currentTimeMillis());
        } catch (Exception ignored) {
            // 記録できなくても本来の処理は続行する
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        try {
            AuditLogService service = auditLogServiceProvider.getIfAvailable();
            if (service == null) {
                return;
            }

            AuditEvent event = new AuditEvent();
            event.eventType   = AuditLogService.EVENT_ACCESS;
            event.clientIp    = request.getRemoteAddr();
            event.httpMethod  = request.getMethod();
            event.uri         = request.getRequestURI();
            event.queryMasked = AuditLogService.maskQuery(request.getQueryString());
            event.statusCode  = response.getStatus();
            event.userAgent   = request.getHeader("User-Agent");

            // セッションを新規作成しないよう getSession(false) を使う
            HttpSession session = request.getSession(false);
            if (session != null) {
                event.sessionKey    = AuditLogService.toSessionKey(session.getId());
                event.authenticated = session.getAttribute("authenticated") != null;
                Object username = session.getAttribute(AuditLogService.SESSION_USERNAME);
                event.username = username != null ? username.toString() : null;
            }

            Object start = request.getAttribute(ATTR_START);
            if (start instanceof Long startMillis) {
                event.durationMs = (int) (System.currentTimeMillis() - startMillis);
            }

            service.record(event);
        } catch (Exception e) {
            // 監査ログの失敗で画面を落とさない
            System.err.println("[AuditLog] 記録処理でエラー: " + e.getMessage());
        }
    }
}
