package jp.co.housekeeping.person_management.controller;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.AuthUser;
import jp.co.housekeeping.person_management.service.AuditLogService;
import jp.co.housekeeping.person_management.service.AuthUserService;

/**
 * ログイン処理。
 *
 * 認証方式は Google Authenticator（TOTP）の一本のみ。
 * 以前あった「全員共通のパスワード方式」と「メール認証コード方式」は削除した。
 * 設定を1つ変えるだけで二要素認証を迂回できる経路が残っていると、
 * そこが抜け道になるため。認証の入口は1つだけにしておく。
 */
@Controller
public class LoginController {

    // ログイン試行回数制限：IPアドレスごとに5回連続失敗したら5分間ロックする
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 5 * 60 * 1000L; // 5分

    // ObjectProviderにすることで、対象のBeanが存在しない環境
    // (@WebMvcTestなどWeb層のみをロードするスライステスト等)でも
    // LoginController自体の生成には失敗しないようにしている。
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final ObjectProvider<AuthUserService> authUserServiceProvider;

    public LoginController(ObjectProvider<AuditLogService> auditLogServiceProvider,
                            ObjectProvider<AuthUserService> authUserServiceProvider) {
        this.auditLogServiceProvider = auditLogServiceProvider;
        this.authUserServiceProvider = authUserServiceProvider;
    }

    private static class AttemptInfo {
        int failCount = 0;
        long lockedUntil = 0L; // このタイムスタンプまでロック中（0=ロックなし）
    }

    private static final ConcurrentHashMap<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    // ============================================================
    // ログイン画面
    // ============================================================

    @GetMapping("/login")
    public String loginPage(HttpServletRequest request, Model model) {
        // 利用者が1人も登録されていない場合、誰もログインできず締め出されてしまう。
        // このPC上からのアクセスに限り、初回セットアップ画面へ誘導する（ロックアウト防止）。
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        if (svc != null && svc.hasNoActiveUser() && isLoopback(request)) {
            return "redirect:/auth/setup";
        }
        return "login";
    }

    /** リクエスト元がこのPC自身か（初回セットアップを外部から実行させないための判定） */
    static boolean isLoopback(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    // ============================================================
    // TOTP（Google Authenticator）でのログイン
    // ============================================================

    @PostMapping("/login/totp")
    public String loginTotp(@RequestParam String username, @RequestParam String code,
                             HttpSession session, HttpServletRequest request) {
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        if (svc == null) {
            return "redirect:/login?error";
        }

        String ip = request.getRemoteAddr();
        AttemptInfo info = attempts.computeIfAbsent(ip, k -> new AttemptInfo());
        long now = System.currentTimeMillis();
        if (info.lockedUntil > now) {
            long remainingSec = (info.lockedUntil - now) / 1000 + 1;
            recordLoginEvent(AuditLogService.EVENT_LOGIN_LOCKED, request, session, username);
            return "redirect:/login?locked=" + remainingSec;
        }

        AuthUser user = svc.findByUsername(username).orElse(null);

        // 利用者名が存在しない場合も、コードが違う場合と同じ結果を返す。
        // 区別できると「どの利用者名が登録済みか」を外部から探れてしまうため。
        boolean totpOk = user != null && svc.verifyTotp(user, code);
        boolean backupOk = !totpOk && user != null && svc.verifyBackupCode(user, code);

        if (totpOk || backupOk) {
            info.failCount = 0;
            info.lockedUntil = 0L;

            // セッション固定化攻撃対策：ログイン成功のタイミングでセッションIDを変える。
            // 攻撃者が事前に用意したセッションIDのまま認証状態になるのを防ぐ。
            try {
                request.changeSessionId();
            } catch (IllegalStateException ignored) {
                // セッションが無い場合は何もしない
            }

            session.setAttribute("authenticated", true);
            session.setAttribute(AuditLogService.SESSION_USERNAME, user.getUsername());

            recordLoginEvent(
                backupOk ? AuditLogService.EVENT_LOGIN_BACKUP : AuditLogService.EVENT_LOGIN_SUCCESS,
                request, session, user.getUsername());

            // バックアップコードを使った＝認証アプリが使えない状況なので、
            // 残数を確認して再登録を促すため画面側にフラグを渡す
            if (backupOk) {
                session.setAttribute("backupCodeUsed", svc.countUnusedBackupCodes(user.getId()));
            }
            return "redirect:/menu";
        }

        info.failCount++;
        if (info.failCount >= MAX_ATTEMPTS) {
            info.lockedUntil = now + LOCK_MILLIS;
            info.failCount = 0;
            recordLoginEvent(AuditLogService.EVENT_LOGIN_LOCKED, request, session, username);
            return "redirect:/login?locked=" + (LOCK_MILLIS / 1000);
        }
        recordLoginEvent(AuditLogService.EVENT_LOGIN_FAILURE, request, session, username);
        return "redirect:/login?error";
    }

    // ============================================================
    // メニュー・ログアウト
    // ============================================================

    @GetMapping("/menu")
    public String menu() {
        return "menu";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, HttpServletRequest request) {
        // invalidate前に記録する（後だとセッションIDが取得できないため）
        recordLoginEvent(AuditLogService.EVENT_LOGOUT, request, session);
        session.invalidate();
        return "redirect:/login";
    }

    // ============================================================
    // 監査ログ
    // ============================================================

    private void recordLoginEvent(String eventType, HttpServletRequest request, HttpSession session) {
        recordLoginEvent(eventType, request, session, null);
    }

    /**
     * ログインの成否を監査ログに記録する。
     * アクセスログだけでは「ログイン画面にPOSTした」ことしか分からず、
     * 不正ログインの試行が成功したのか失敗したのかを判別できないため個別に記録する。
     */
    private void recordLoginEvent(String eventType, HttpServletRequest request,
                                   HttpSession session, String username) {
        try {
            AuditLogService service = auditLogServiceProvider.getIfAvailable();
            if (service == null) {
                return;
            }
            AuditLogService.AuditEvent event = new AuditLogService.AuditEvent();
            event.username     = username;
            event.eventType    = eventType;
            event.clientIp     = request.getRemoteAddr();
            event.httpMethod   = request.getMethod();
            event.uri          = request.getRequestURI();
            event.userAgent    = request.getHeader("User-Agent");
            event.authenticated = AuditLogService.EVENT_LOGIN_SUCCESS.equals(eventType)
                                || AuditLogService.EVENT_LOGIN_BACKUP.equals(eventType);
            if (session != null) {
                event.sessionKey = AuditLogService.toSessionKey(session.getId());
            }
            service.record(event);
        } catch (Exception e) {
            // 監査ログの失敗でログインを止めない
            System.err.println("[AuditLog] ログインイベントの記録に失敗: " + e.getMessage());
        }
    }
}
