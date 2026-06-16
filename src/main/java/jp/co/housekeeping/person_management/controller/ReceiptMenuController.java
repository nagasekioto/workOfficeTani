package jp.co.housekeeping.person_management.controller;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/receipt-menu")
public class ReceiptMenuController {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;

    @GetMapping("")
    public String menu(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "receipt-menu";
    }

    // ─── 1-4-1 求人者領収書一覧 ────────────────────────
    @GetMapping("/customer-receipt")
    public String customerReceipt(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        // 求人受付手数料（customer_fee）が設定されているsales_detailsを取得
        Iterable<Customer> customers = customerRepository.findAll();
        List<ReceiptItem> items = new ArrayList<>();

        for (Customer c : customers) {
            // この求人者に紐づくsales_detailsを全取得
            Iterable<Sales> allSales = salesRepository.findAll();
            for (Sales s : allSales) {
                List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
                for (SalesDetail d : details) {
                    if (d.getCustomerId() != null && d.getCustomerId().equals(c.getId())
                            && d.getCustomerFee() != null && d.getCustomerFee() > 0) {
                        ReceiptItem item = new ReceiptItem();
                        item.customer = c;
                        item.detail = d;
                        item.salesId = s.getId();
                        item.personId = s.getPersonId();
                        items.add(item);
                    }
                }
            }
        }

        model.addAttribute("items", items);
        return "receipt-customer-list";
    }

    // 印刷フラグを立てる
    @PostMapping("/customer-receipt/mark-printed")
    public String markPrinted(@RequestParam Long detailId, HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        // SalesDetailにprintedフラグを追加する場合はここで設定
        // 現時点ではリダイレクトのみ
        return "redirect:/receipt-menu/customer-receipt?printed=" + detailId;
    }

    public static class ReceiptItem {
        public Customer customer;
        public SalesDetail detail;
        public Long salesId;
        public Long personId;
    }
}
