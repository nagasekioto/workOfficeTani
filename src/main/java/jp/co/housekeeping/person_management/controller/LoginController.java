package jp.co.housekeeping.person_management.controller;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private static final String CORRECT_PASSWORD = "7136";

    private static final boolean SKIP_PASSWORD_CHECK = false;

    // ログイン試行回数制限：IPアドレスごとに5回連続失敗したら5分間ロックする
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 5 * 60 * 1000L; // 5分

    private static class AttemptInfo {
        int failCount = 0;
        long lockedUntil = 0L; // このタイムスタンプまでロック中（0=ロックなし）
    }

    private static final ConcurrentHashMap<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    @GetMapping("/login")
    public String loginPage() {
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