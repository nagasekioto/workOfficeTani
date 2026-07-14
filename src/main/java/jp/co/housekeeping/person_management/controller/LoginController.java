package jp.co.housekeeping.person_management.controller;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.service.EmailAuthService;

@Controller
public class LoginController {

    private static final String CORRECT_PASSWORD = "tani";

    private static final boolean SKIP_PASSWORD_CHECK = false;

    // ログイン試行回数制限：IPアドレスごとに5回連続失敗したら5分間ロックする
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 5 * 60 * 1000L; // 5分

    // メール認証への切り替えフラグ。false(デフォルト)の間は本クラスの挙動は一切変わらない。
    @Value("${app.auth.email-enabled:false}")
    private boolean emailAuthEnabled;

    private final EmailAuthService emailAuthService;

    private static final String SESSION_PENDING_EMAIL = "pendingEmail";

    public LoginController(EmailAuthService emailAuthService) {
        this.emailAuthService = emailAuthService;
    }

    private static class AttemptInfo {
        int failCount = 0;
        long lockedUntil = 0L; // このタイムスタンプまでロック中（0=ロックなし）
    }

    private static final ConcurrentHashMap<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        model.addAttribute("emailAuthEnabled", emailAuthEnabled);
        if (emailAuthEnabled) {
            boolean codeSent = session.getAttribute(SESSION_PENDING_EMAIL) != null;
            model.addAttribute("codeSent", codeSent);
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String password, HttpSession session,
                         HttpServletRequest request, Model model) {
        String ip = request.getRemoteAddr();
        AttemptInfo info = attempts.computeIfAbsent(ip, k -> new AttemptInfo());

        long now = System.currentTimeMillis();
        if (info.lockedUntil > now) {
            long remainingSec = (info.lockedUntil - now) / 1000 + 1;
            return "redirect:/login?locked=" + remainingSec;
        }

        if (SKIP_PASSWORD_CHECK || CORRECT_PASSWORD.equals(password)) {
            info.failCount = 0;
            info.lockedUntil = 0L;
            session.setAttribute("authenticated", true);
            return "redirect:/menu";
        }

        info.failCount++;
        if (info.failCount >= MAX_ATTEMPTS) {
            info.lockedUntil = now + LOCK_MILLIS;
            info.failCount = 0;
            return "redirect:/login?locked=" + (LOCK_MILLIS / 1000);
        }
        return "redirect:/login?error";
    }

    // ============================================================
    // 以下、メール認証コード方式(app.auth.email-enabled=true の時のみ動作)
    // フラグがfalseの間はガード節でリダイレクトするだけで実質未使用。
    // ============================================================

    @PostMapping("/login/email/request")
    public String requestEmailCode(@RequestParam String email, HttpSession session,
                                    HttpServletRequest request) {
        if (!emailAuthEnabled) {
            return "redirect:/login";
        }
        String ip = request.getRemoteAddr();
        AttemptInfo info = attempts.computeIfAbsent(ip, k -> new AttemptInfo());
        long now = System.currentTimeMillis();
        if (info.lockedUntil > now) {
            long remainingSec = (info.lockedUntil - now) / 1000 + 1;
            return "redirect:/login?locked=" + remainingSec;
        }

        if (!emailAuthService.isAllowed(email)) {
            // 許可されていないアドレスかどうかは画面には出さず、一般的なエラーだけ返す
            // (どのアドレスが登録済みかを外部に推測させないため)
            return "redirect:/login?error";
        }

        emailAuthService.sendCode(email);
        session.setAttribute(SESSION_PENDING_EMAIL, email);
        return "redirect:/login?sent";
    }

    @PostMapping("/login/email/verify")
    public String verifyEmailCode(@RequestParam String code, HttpSession session,
                                   HttpServletRequest request) {
        if (!emailAuthEnabled) {
            return "redirect:/login";
        }
        String ip = request.getRemoteAddr();
        AttemptInfo info = attempts.computeIfAbsent(ip, k -> new AttemptInfo());
        long now = System.currentTimeMillis();
        if (info.lockedUntil > now) {
            long remainingSec = (info.lockedUntil - now) / 1000 + 1;
            return "redirect:/login?locked=" + remainingSec;
        }

        Object pendingEmail = session.getAttribute(SESSION_PENDING_EMAIL);
        if (pendingEmail == null) {
            return "redirect:/login";
        }

        if (emailAuthService.verifyCode(pendingEmail.toString(), code)) {
            info.failCount = 0;
            info.lockedUntil = 0L;
            session.removeAttribute(SESSION_PENDING_EMAIL);
            session.setAttribute("authenticated", true);
            return "redirect:/menu";
        }

        info.failCount++;
        if (info.failCount >= MAX_ATTEMPTS) {
            info.lockedUntil = now + LOCK_MILLIS;
            info.failCount = 0;
            session.removeAttribute(SESSION_PENDING_EMAIL);
            return "redirect:/login?locked=" + (LOCK_MILLIS / 1000);
        }
        return "redirect:/login?error";
    }

    @GetMapping("/menu")
    public String menu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        return "menu";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}