package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import java.time.YearMonth;
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

    // ─── 1ページ目：全求職者一覧 + 検索 ────────────────
    @GetMapping
    public String list(@RequestParam(required = false) String search,
                       HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        Iterable<Person> allPersons = personRepository.findAll();
        List<LedgerListRow> rows = new ArrayList<>();

        for (Person p : allPersons) {
            // 検索フィルター
            if (search != null && !search.isBlank()) {
                boolean match = containsIgnoreCase(p.getLastNameKanji(), search)
                        || containsIgnoreCase(p.getFirstNameKanji(), search)
                        || containsIgnoreCase(p.getLastNameKana(), search)
                        || containsIgnoreCase(p.getFirstNameKana(), search);
                if (!match) continue;
            }

            // 売上件数カウント
            List<Sales> salesList = salesRepository.findByPersonId(p.getId());
            int count = 0;
            for (Sales s : salesList) {
                count += salesDetailRepository.findBySalesId(s.getId()).size();
            }

            LedgerListRow row = new LedgerListRow();
            row.person = p;
            row.salesCount = count;
            rows.add(row);
        }

        model.addAttribute("persons", rows);
        model.addAttribute("search", search);
        return "working-ledger-list";
    }

    public static class LedgerListRow {
        public Person person;
        public int salesCount;
    }

    // ─── 2ページ目：求職者詳細（稼働履歴）──────────────
    @GetMapping("/{personId}")
    public String detail(@PathVariable Long personId,
                         HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        Person person = personRepository.findById(personId).orElse(null);
        if (person == null) return "redirect:/person/working-ledger";

        // salesテーブルからperson_idで取得 → sales_detailsを取得
        List<Sales> salesList = salesRepository.findByPersonId(personId);
        List<LedgerRow> rows = new ArrayList<>();

        for (Sales s : salesList) {
            List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
            for (SalesDetail d : details) {
                LedgerRow row = new LedgerRow();
                row.detail = d;
                row.sales  = s;

                // 求人者名
                if (d.getCustomerId() != null) {
                    Optional<Customer> c = customerRepository.findById(d.getCustomerId());
                    c.ifPresent(cu -> row.customerName =
                        cu.getLastNameKanji() + " " + cu.getFirstNameKanji());
                }

                // 日数計算
                if (d.getWorkStartDate() != null && d.getWorkEndDate() != null) {
                    row.workDays = (int) ChronoUnit.DAYS.between(
                        d.getWorkStartDate(), d.getWorkEndDate()) + 1;
                }

                // 領収月日（就労終了月の月末）
                if (d.getWorkEndDate() != null) {
                    row.receiptDate = YearMonth.from(d.getWorkEndDate()).atEndOfMonth();
                }

                // 賃金総額
                row.wageTotal    = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                row.wageTotalStr = fmt(row.wageTotal);

                // 手数料率（固定15%）
                row.commissionRate = "15%";

                // 時給・日給・受付料フォーマット
                row.hourlyWageStr     = fmt(d.getHourlyWage());
                row.hourlyWageOTStr   = fmt(d.getHourlyWageOvertime());
                row.receptionFeeStr   = fmt(d.getReceptionFee());
                // 日給（カンマ区切り文字列 → ¥付き配列）
                if (d.getDailyWages() != null && !d.getDailyWages().isBlank()) {
                    String[] parts = d.getDailyWages().split(",");
                    row.dailyWageStrs = new String[parts.length];
                    for (int wi = 0; wi < parts.length; wi++) {
                        try { row.dailyWageStrs[wi] = fmt(Long.parseLong(parts[wi].trim())); }
                        catch (NumberFormatException e) { row.dailyWageStrs[wi] = "-"; }
                    }
                } else {
                    row.dailyWageStrs = new String[0];
                }

                rows.add(row);
            }
        }

        // 紹介年月日の昇順ソート
        rows.sort((a, b) -> {
            LocalDate da = a.detail.getIntroductionDate();
            LocalDate db = b.detail.getIntroductionDate();
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });

        model.addAttribute("person", person);
        model.addAttribute("rows", rows);
        return "working-ledger-detail";
    }

    // 備考更新
    @PostMapping("/update-remarks")
    public String updateRemarks(@RequestParam Long detailId,
                                @RequestParam String remarks,
                                @RequestParam Long personId,
                                HttpSession session) {
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

    private String fmt(Number n) {
        if (n == null) return "-";
        long v = n.longValue();
        if (v == 0) return "-";
        return "\u00a5" + java.text.NumberFormat.getNumberInstance(java.util.Locale.JAPAN).format(v);
    }

    // ─── 内部DTO ────────────────────────────────────────
    public static class LedgerRow {
        public SalesDetail detail;
        public Sales       sales;
        public String      customerName   = "";
        public int         workDays       = 0;
        public LocalDate   receiptDate;
        public int         wageTotal      = 0;
        public String      wageTotalStr   = "-";
        public String      commissionRate = "15%";
        public String      hourlyWageStr  = "-";
        public String      hourlyWageOTStr = "-";
        public String      receptionFeeStr = "-";
        public String[]    dailyWageStrs  = new String[0];
    }
}
