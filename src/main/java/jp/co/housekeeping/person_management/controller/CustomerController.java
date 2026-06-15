package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.repository.CustomerRepository;

@Controller
@RequestMapping("/customer")
public class CustomerController {
    
    @Autowired
    private CustomerRepository customerRepository;
    
    // 名簿入力画面
    @GetMapping("/register")
    public String registerForm(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        
        Iterable<Customer> customers = customerRepository.findAll();
        model.addAttribute("customers", customers);
        model.addAttribute("customer", new Customer());
        
        return "customer-register";
    }
    
    // 登録処理
    @PostMapping("/register")
    public String register(@ModelAttribute Customer customer, HttpSession session) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        
        if (customer.getRegisteredDate() == null) {
            customer.setRegisteredDate(LocalDate.now());
        }
        
        customerRepository.save(customer);
        
        return "redirect:/customer/register";
    }
    
    // 管理簿入力画面
    @GetMapping("/report")
    public String report(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        
        Iterable<Customer> customers = customerRepository.findAll();
        model.addAttribute("customers", customers);
        
        return "customer-report";
    }
}