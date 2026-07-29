package jp.co.housekeeping.person_management.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.AuthUser;
import jp.co.housekeeping.person_management.service.AuditLogService;
import jp.co.housekeeping.person_management.service.AuthUserService;
import jp.co.housekeeping.person_management.service.QrCodeService;
import jp.co.housekeeping.person_management.service.SecretCipher;

/**
 * TOTP（Google Authenticator）の初回セットアップ・利用者管理を行うコントローラ。
 *
 * 依存する各サービスは（LoginControllerと同じ理由で）ObjectProviderで受けている。
 * これにより、@WebMvcTest 等 Web層のみをロードするスライステストで
 * サービス層のBeanが存在しなくても、このコントローラ自体の生成には失敗しない。
 */
@Controller
public class AuthSetupController {

    @Value("${app.auth.totp-issuer:ワークオフィス谷}")
    private String issuer;

    /**
     * 登録手続き中の情報を保持するセッション属性名。
     *
     * 「QRを表示した本人だけが、その登録を完了できる」ようにするために使う。
     * userIdをフォームから受け取るだけの実装にすると、ログインしていない第三者が
     * 直接POSTして他人の登録を完了させ、バックアップコードを奪えてしまう。
     */
    private static final String SESSION_PENDING_ENROLLMENT = "pendingEnrollment";

    /** 登録時のコード入力の試行回数（総当たり対策） */
    private static final String SESSION_ENROLL_ATTEMPTS = "pendingEnrollmentAttempts";
    private static final int MAX_ENROLL_ATTEMPTS = 5;

    private final ObjectProvider<AuthUserService> authUserServiceProvider;
    private final ObjectProvider<QrCodeService> qrCodeServiceProvider;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final ObjectProvider<SecretCipher> secretCipherProvider;

    public AuthSetupController(ObjectProvider<AuthUserService> authUserServiceProvider,
                                ObjectProvider<QrCodeService> qrCodeServiceProvider,
                                ObjectProvider<AuditLogService> auditLogServiceProvider,
                                ObjectProvider<SecretCipher> secretCipherProvider) {
        this.authUserServiceProvider = authUserServiceProvider;
        this.qrCodeServiceProvider = qrCodeServiceProvider;
        this.auditLogServiceProvider = auditLogServiceProvider;
        this.secretCipherProvider = secretCipherProvider;
    }

    // ============================================================
    // (A)(B) 初回セットアップ（利用者が1人も居ない状態からの登録）
    // ============================================================

    /**
     * 初回セットアップ画面へアクセスしてよいか。
     * 「利用者が1人も居ない」かつ「このPC上からのアクセス」の両方を満たす場合のみ許可する。
     * GET側だけでなくPOST側でも必ず同じチェックを行うこと（直接POSTで抜けられるため）。
     */
    private boolean canAccessInitialSetup(HttpServletRequest request) {
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        return svc != null && svc.hasNoActiveUser() && LoginController.isLoopback(request);
    }

    @GetMapping("/auth/setup")
    public String setupPage(HttpServletRequest request, Model model) {
        if (!canAccessInitialSetup(request)) {
            return "redirect:/login";
        }
        model.addAttribute("keyMissing", isKeyMissing());
        return "auth-setup";
    }

    @PostMapping("/auth/setup")
    public String setupSubmit(@RequestParam String username, @RequestParam String displayName,
                               HttpServletRequest request, HttpSession session, Model model) {
        // GETのガードだけでは直接POSTを防げないため、ここでも必ず再チェックする。
        if (!canAccessInitialSetup(request)) {
            return "redirect:/login";
        }
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        QrCodeService qr = qrCodeServiceProvider.getIfAvailable();
        if (svc == null || qr == null) {
            model.addAttribute("keyMissing", isKeyMissing());
            model.addAttribute("errorMessage", "システムの初期化に失敗しました。しばらくしてから再度お試しください");
            return "auth-setup";
        }
        try {
            AuthUserService.EnrollmentInfo info = svc.createUser(username, displayName, issuer);
            startPendingEnrollment(session, info);
            fillEnrollmentModel(model, qr, info);
            return "auth-enroll";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("keyMissing", isKeyMissing());
            model.addAttribute("errorMessage", e.getMessage());
            return "auth-setup";
        }
    }

    /**
     * 登録手続きを開始したことをセッションに記録する。
     * 平文シークレットをフォームのhiddenで往復させず、サーバー側のセッションに置く。
     */
    private void startPendingEnrollment(HttpSession session, AuthUserService.EnrollmentInfo info) {
        session.setAttribute(SESSION_PENDING_ENROLLMENT, info);
        session.setAttribute(SESSION_ENROLL_ATTEMPTS, 0);
    }

    private void clearPendingEnrollment(HttpSession session) {
        session.removeAttribute(SESSION_PENDING_ENROLLMENT);
        session.removeAttribute(SESSION_ENROLL_ATTEMPTS);
    }

    private void fillEnrollmentModel(Model model, QrCodeService qr,
                                      AuthUserService.EnrollmentInfo info) {
        model.addAttribute("username", info.getUsername());
        model.addAttribute("qrDataUri", qr.toSvgDataUri(info.getOtpAuthUri(), 240));
        model.addAttribute("secretBase32", info.getSecretBase32());
    }

    private boolean isKeyMissing() {
        SecretCipher cipher = secretCipherProvider.getIfAvailable();
        return cipher == null || !cipher.isKeyConfigured();
    }

    // ============================================================
    // (C) 初回コード確認とバックアップコード発行
    // ============================================================

    /**
     * 初回のコード確認。
     *
     * 対象の利用者は**セッションに記録された登録手続きからのみ**決定する。
     * フォームからuserIdを受け取る実装にすると、ログインしていない第三者が
     * 他人のuserIdを指定して直接POSTでき、6桁コードを一度覗き見ただけで
     * バックアップコード10個を奪えてしまうため。
     */
    @PostMapping("/auth/enroll/confirm")
    public String confirmEnrollment(@RequestParam String code,
                                     HttpServletRequest request,
                                     HttpSession session,
                                     Model model) {
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        QrCodeService qr = qrCodeServiceProvider.getIfAvailable();
        Object pending = session.getAttribute(SESSION_PENDING_ENROLLMENT);

        if (svc == null || qr == null || !(pending instanceof AuthUserService.EnrollmentInfo info)) {
            // 手続きを開始していない相手からのPOSTはここで止まる
            return "redirect:/login";
        }

        // 6桁の総当たりを防ぐ。上限に達したら手続き自体を破棄し、最初からやり直させる。
        int attempts = session.getAttribute(SESSION_ENROLL_ATTEMPTS) instanceof Integer n ? n : 0;
        if (attempts >= MAX_ENROLL_ATTEMPTS) {
            clearPendingEnrollment(session);
            return "redirect:/login?error";
        }

        try {
            List<String> backupCodes = svc.completeEnrollment(info.getUserId(), code);
            clearPendingEnrollment(session);
            recordAuthChange(request, "利用者を登録した");
            model.addAttribute("backupCodes", backupCodes);
            return "auth-backup-codes";
        } catch (Exception e) {
            session.setAttribute(SESSION_ENROLL_ATTEMPTS, attempts + 1);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("remainingAttempts", MAX_ENROLL_ATTEMPTS - (attempts + 1));
            // QRとシークレットはセッションの登録情報から作り直す（hiddenで往復させない）
            fillEnrollmentModel(model, qr, info);
            return "auth-enroll";
        }
    }

    // ============================================================
    // (D)(E)(F)(G) 利用者管理（ログイン必須）
    // ============================================================

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("authenticated") != null;
    }

    @GetMapping("/auth/users")
    public String usersPage(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        List<UserRow> rows = new ArrayList<>();
        if (svc != null) {
            for (AuthUser user : svc.findAll()) {
                int unused = user.getId() == null ? 0 : svc.countUnusedBackupCodes(user.getId());
                rows.add(new UserRow(user, unused));
            }
        }
        model.addAttribute("users", rows);
        return "auth-users";
    }

    @PostMapping("/auth/users/add")
    public String addUser(@RequestParam String username, @RequestParam String displayName,
                           HttpSession session, HttpServletRequest request, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        QrCodeService qr = qrCodeServiceProvider.getIfAvailable();
        if (svc == null || qr == null) {
            return "redirect:/auth/users?errorMessage=" + encode("システムの初期化に失敗しました");
        }
        try {
            AuthUserService.EnrollmentInfo info = svc.createUser(username, displayName, issuer);
            startPendingEnrollment(session, info);
            recordAuthChange(request, "利用者を登録した");
            fillEnrollmentModel(model, qr, info);
            return "auth-enroll";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "redirect:/auth/users?errorMessage=" + encode(e.getMessage());
        }
    }

    @PostMapping("/auth/users/{id}/disable")
    public String disableUser(@PathVariable Long id, HttpSession session, HttpServletRequest request) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        if (svc == null) {
            return "redirect:/auth/users?errorMessage=" + encode("システムの初期化に失敗しました");
        }
        try {
            svc.disableUser(id);
            recordAuthChange(request, "利用者を無効化した");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "redirect:/auth/users?errorMessage=" + encode(e.getMessage());
        }
        return "redirect:/auth/users";
    }

    @PostMapping("/auth/users/{id}/enable")
    public String enableUser(@PathVariable Long id, HttpSession session, HttpServletRequest request) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        if (svc == null) {
            return "redirect:/auth/users?errorMessage=" + encode("システムの初期化に失敗しました");
        }
        try {
            svc.enableUser(id);
            recordAuthChange(request, "利用者を有効化した");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "redirect:/auth/users?errorMessage=" + encode(e.getMessage());
        }
        return "redirect:/auth/users";
    }

    @PostMapping("/auth/users/{id}/backup-codes")
    public String reissueBackupCodes(@PathVariable Long id, HttpSession session,
                                      HttpServletRequest request, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        AuthUserService svc = authUserServiceProvider.getIfAvailable();
        if (svc == null) {
            return "redirect:/auth/users?errorMessage=" + encode("システムの初期化に失敗しました");
        }
        try {
            List<String> codes = svc.issueBackupCodes(id);
            recordAuthChange(request, "バックアップコードを再発行した");
            model.addAttribute("backupCodes", codes);
            return "auth-backup-codes";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "redirect:/auth/users?errorMessage=" + encode(e.getMessage());
        }
    }

    // ============================================================
    // ユーティリティ
    // ============================================================

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** 認証設定そのものの変更（利用者登録・無効化・バックアップコード再発行）を監査ログに記録する */
    private void recordAuthChange(HttpServletRequest request, String description) {
        try {
            AuditLogService service = auditLogServiceProvider.getIfAvailable();
            if (service == null) {
                return;
            }
            AuditLogService.AuditEvent event = new AuditLogService.AuditEvent();
            event.eventType = AuditLogService.EVENT_AUTH_CHANGE;
            event.clientIp = request.getRemoteAddr();
            event.httpMethod = request.getMethod();
            event.uri = request.getRequestURI();
            event.queryMasked = description;
            event.userAgent = request.getHeader("User-Agent");
            event.authenticated = true;
            HttpSession session = request.getSession(false);
            if (session != null) {
                event.sessionKey = AuditLogService.toSessionKey(session.getId());
                Object username = session.getAttribute(AuditLogService.SESSION_USERNAME);
                if (username != null) {
                    event.username = username.toString();
                }
            }
            service.record(event);
        } catch (Exception e) {
            // 監査ログの失敗で利用者管理操作を止めない
            System.err.println("[AuditLog] 認証設定変更イベントの記録に失敗: " + e.getMessage());
        }
    }

    /** 画面表示用：利用者1件 + 未使用バックアップコード残数 */
    public static class UserRow {
        private final AuthUser user;
        private final int unusedBackupCodes;

        UserRow(AuthUser user, int unusedBackupCodes) {
            this.user = user;
            this.unusedBackupCodes = unusedBackupCodes;
        }

        public AuthUser getUser() { return user; }
        public int getUnusedBackupCodes() { return unusedBackupCodes; }

        /** 状態ラベル（有効・無効・登録途中） */
        public String getStatusLabel() {
            if (user.getEnrolledAt() == null) {
                return "登録途中";
            }
            return user.getDisabledAt() == null ? "有効" : "無効";
        }

        public boolean isActive() {
            return user.isActive();
        }

        public boolean isDisabled() {
            return user.getEnrolledAt() != null && user.getDisabledAt() != null;
        }
    }
}
