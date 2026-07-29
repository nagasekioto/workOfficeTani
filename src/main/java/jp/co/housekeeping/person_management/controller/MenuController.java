package jp.co.housekeeping.person_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MenuController {

    @GetMapping("/")
    public String root() {
        return "menu";
    }

    @GetMapping("/person-menu")
    public String personMenu() {
        return "person-menu";
    }

    @GetMapping("/customer-menu")
    public String customerMenu() {
        return "customer-menu";
    }

    @GetMapping("/introduction-menu")
    public String introductionMenu() {
        return "introduction-menu";
    }

    @GetMapping("/register-menu")
    public String registerMenu() {
        return "register-menu";
    }


}
