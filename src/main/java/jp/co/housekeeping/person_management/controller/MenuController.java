package jp.co.housekeeping.person_management.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MenuController {

    @GetMapping("/")
    public String root(HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "menu";
    }

    @GetMapping("/person-menu")
    public String personMenu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "person-menu";
    }

    @GetMapping("/customer-menu")
    public String customerMenu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "customer-menu";
    }

    @GetMapping("/introduction-menu")
    public String introductionMenu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "introduction-menu";
    }

    @GetMapping("/register-menu")
    public String registerMenu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "register-menu";
    }


}
