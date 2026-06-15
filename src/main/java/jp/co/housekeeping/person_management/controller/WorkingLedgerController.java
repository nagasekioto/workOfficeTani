package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/person/working-ledger")
public class WorkingLedgerController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;

    // ─── 1ページ目：求職者一覧 + 検索 ──────────────────
    @GetMapping
    public String list(@RequestParam(required = false) String search, HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        Iterable<Person> allPersons = personRepository.findAll();
        List<Person> filtered = new ArrayList<>();
        for (Person p : allPersons) {
            if (search == null || search.isBlank()
                    || containsIgnoreCase(p.getLastNameKanji(), search)
                    || containsIgnoreCase(p.getFirstNameKanji(), search)
                    || containsIgnoreCase(p.getLastNameKana(), search)
                    || containsIgnoreCase(p.getFirstNameKana(), search)) {
                filtered.add(p);
            }
        }
        model.addAttribute("persons", filtered);
        model.addAttribute("search", search);
        return "working-ledger-list";
    }

    // ─── 2ページ目：求職者詳細（稼働履歴）──────────────
    @GetMapping("/{personId}")
    public String detail(@PathVariable Long personId, HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        Person person = personRepository.findById(personId).orElse(null);
        if (person == null) return "redirect:/person/working-ledger";

        List<Sales> salesList = salesRepository.findByPersonId(personId);
        List<LedgerRow> rows = new ArrayList<>();

        for (Sales s : salesList) {
            List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
            for (SalesDetail d : details) {
                LedgerRow row = new LedgerRow();
                row.detail = d;
                row.sales = s;

                // 求人者名
                if (d.getCustomerId() != null) {
                    Optional<Customer> c = customerRepository.findById(d.getCustomerId());
                    c.ifPresent(cu -> row.customerName = cu.getLastNameKanji() + " " + cu.getFirstNameKanji());
                }

                // 日数計算
                if (d.getWorkStartDate() != null && d.getWorkEndDate() != null) {
                    row.workDays = (int) ChronoUnit.DAYS.between(d.getWorkStartDate(), d.getWorkEndDate()) + 1;
                }

                // 領収月日（就労終了月の月末）
                if (d.getWorkEndDate() != null) {
                    YearMonth ym = YearMonth.from(d.getWorkEndDate());
                    row.receiptDate = ym.atEndOfMonth();
                }

                // 賃金総額
                int total = 0;
                if (d.getMonthlyTotal() != null) total = d.getMonthlyTotal();
                row.wageTotal = total;

                // 手数料率（固定15%）
                row.commissionRate = "15%";

                rows.add(row);
            }
        }

        // 紹介年月日の昇順ソート
        rows.sort((a, b) -> {
            LocalDate da = a.detail.getIntroductionDate();
            LocalDate db = b.detail.getIntroductionDate();
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });

        model.addAttribute("person", person);
        model.addAttribute("rows", rows);
        return "working-ledger-detail";
    }

    // 備考更新（稼働管理簿詳細ページから）
    @PostMapping("/update-remarks")
    public String updateRemarks(@RequestParam Long detailId, @RequestParam String remarks,
            @RequestParam Long personId, HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        salesDetailRepository.findById(detailId).ifPresent(d -> {
            d.setRemarks(remarks);
            salesDetailRepository.save(d);
        });
        return "redirect:/person/working-ledger/" + personId;
    }

    private boolean containsIgnoreCase(String src, String q) {
        if (src == null) return false;
        return src.toLowerCase().contains(q.toLowerCase());
    }

    // ─── 内部DTO ────────────────────────────────────────
    public static class LedgerRow {
        public SalesDetail detail;
        public Sales sales;
        public String customerName = "";
        public int workDays = 0;
        public LocalDate receiptDate;
        public int wageTotal = 0;
        public String commissionRate = "15%";
    }
}
