package jp.co.housekeeping.person_management.controller;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {
    
    private static final String CORRECT_PASSWORD = "7136";

    // ⚠️ テスト段階のため一時的にパスワードチェックを無効化(常に成功扱い)。
    //    本番運用に戻す際は、この行を false に戻すこと。
    private static final boolean SKIP_PASSWORD_CHECK = true;
    
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
    
    @PostMapping("/login")
    public String login(@RequestParam String password, HttpSession session) {
        if (SKIP_PASSWORD_CHECK || CORRECT_PASSWORD.equals(password)) {
            session.setAttribute("authenticated", true);
            return "redirect:/menu";
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