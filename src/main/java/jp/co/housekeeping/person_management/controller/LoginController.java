package jp.co.housekeeping.person_management.controller;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.service.EmailAuthService;

@Controller
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    // ドキュメント(SETUP_NEW_PC.md等)に記載している既定のログインパスワード。
    // 実際の認証判定にはcorrectPasswordを使う。これは起動時ログでの比較専用。
    private static final String DOCUMENTED_DEFAULT_PASSWORD = "tani";

    private static final boolean SKIP_PASSWORD_CHECK = false;

    // ログインパスワード。app.login.password(環境変数 LOGIN_PASSWORD)で上書き可能。
    // 未設定時はデフォルトで"tani"となり、既存の動作と完全に同一。
    @Value("${app.login.password:tani}")
    private String correctPassword;

    // 起動時に「実際に使われるログインパスワードが、ドキュメント記載の既定値(tani)と
    // 一致しているかどうか」だけをログに出す。値そのものは絶対にログに出さない。
    // (環境変数LOGIN_PASSWORDの設定し忘れ・消し忘れによる「ログインできない」問い合わせの
    //  原因調査を、system-log.txt/コンソールログを見るだけで一目で判別できるようにするため)
    @PostConstruct
    private void logLoginPasswordSource() {
        if (DOCUMENTED_DEFAULT_PASSWORD.equals(correctPassword)) {
            logger.info("[起動確認] ログインパスワードは既定値(tani)で動作します。" +
                    "環境変数 LOGIN_PASSWORD は未設定、または既定値と同じです。");
        } else {
            logger.warn("[起動確認] ログインパスワードは既定値(tani)から変更されています。" +
                    "環境変数 LOGIN_PASSWORD、または app.login.password 設定が既定値以外の" +
                    "値で読み込まれています(値自体はセキュリティのためログに出力しません)。" +
                    "『tani』でログインできない場合は、まずこの環境変数の設定を確認してください。");
        }
    }

    // ログイン試行回数制限：IPアドレスごとに5回連続失敗したら5分間ロックする
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 5 * 60 * 1000L; // 5分

    // メール認証への切り替えフラグ。false(デフォルト)の間は本クラスの挙動は一切変わらない。
    @Value("${app.auth.email-enabled:false}")
    private boolean emailAuthEnabled;

    // ObjectProviderにすることで、EmailAuthServiceのBeanが存在しない環境
    // (@WebMvcTestなどWeb層のみをロードするスライステスト等)でも
    // LoginController自体の生成には失敗しないようにしている。
    private final ObjectProvider<EmailAuthService> emailAuthServiceProvider;

    private static final String SESSION_PENDING_EMAIL = "pendingEmail";

    public LoginController(ObjectProvider<EmailAuthService> emailAuthServiceProvider) {
        this.emailAuthServiceProvider = emailAuthServiceProvider;
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

        if (SKIP_PASSWORD_CHECK || correctPassword.equals(password)) {
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
        EmailAuthService emailAuthService = emailAuthServiceProvider.getIfAvailable();
        if (emailAuthService == null) {
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
        EmailAuthService emailAuthService = emailAuthServiceProvider.getIfAvailable();
        if (emailAuthService == null) {
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