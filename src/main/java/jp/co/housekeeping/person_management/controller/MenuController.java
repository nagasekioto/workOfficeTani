package jp.co.housekeeping.person_management.controller;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MenuController {
    
    // 求職者管理メニュー
    @GetMapping("/person-menu")
    public String personMenu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        return "person-menu";
    }
    
    // 求人者管理メニュー
    @GetMapping("/customer-menu")
    public String customerMenu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        return "customer-menu";
    }
}