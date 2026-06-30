package jp.co.housekeeping.person_management.controller;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
public class MonthlySalesController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;

    @GetMapping("/sales-monthly-test")
    @ResponseBody
    public String test() { return "OK"; }

    @GetMapping("/sales-monthly")
    public String monthly(@RequestParam(required = false) String month,
                          HttpSession session, Model model) {

        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        // 月未指定時は当月をデフォルト表示する
        if (month == null || month.isBlank()) {
            month = YearMonth.now().toString();
        }

        model.addAttribute("selectedMonth",     month);
        model.addAttribute("rows",              new ArrayList<>());
        model.addAttribute("totalCount",        0);
        model.addAttribute("totalWageStr",      "0");
        model.addAttribute("totalCommissionStr","0");
        model.addAttribute("totalFeesStr",      "0");
        model.addAttribute("errorMsg",          null);

        try {
            Map<Long, String> personMap = new HashMap<>();
            StreamSupport.stream(personRepository.findAll().spliterator(), false)
                .forEach(p -> personMap.put(p.getId(),
                    p.getLastNameKanji() + " " + p.getFirstNameKanji()));

            Map<Long, String> customerMap = new HashMap<>();
            StreamSupport.stream(customerRepository.findAll().spliterator(), false)
                .forEach(c -> customerMap.put(c.getId(),
                    c.getLastNameKanji() + " " + c.getFirstNameKanji()));

            Map<Long, Long> salesPersonMap = new HashMap<>();
            StreamSupport.stream(salesRepository.findAll().spliterator(), false)
                .forEach(s -> salesPersonMap.put(s.getId(), s.getPersonId()));

            YearMonth ym        = YearMonth.parse(month);
            LocalDate startDate = ym.atDay(1);
            LocalDate endDate   = ym.atEndOfMonth();

            List<MonthlyRow> rows = new ArrayList<>();
            long totalWage = 0, totalCommission = 0, totalFees = 0;

            for (SalesDetail d : salesDetailRepository.findAll()) {
                boolean match = isInMonth(d.getIntroductionDate(), startDate, endDate)
                             || isInMonth(d.getWorkStartDate(),    startDate, endDate)
                             || isInMonth(d.getWorkEndDate(),      startDate, endDate);
                if (!match) continue;

                MonthlyRow row   = new MonthlyRow();
                row.detail       = d;
                Long pid         = salesPersonMap.get(d.getSalesId());
                row.personName   = pid != null ? personMap.getOrDefault(pid, "不明") : "不明";
                row.customerName = d.getCustomerId() != null
                        ? customerMap.getOrDefault(d.getCustomerId(), "不明") : "-";

                // 日給合計
                long daily = 0;
                if (d.getDailyWages() != null && !d.getDailyWages().isBlank()) {
                    for (String w : d.getDailyWages().split(",")) {
                        try { daily += Long.parseLong(w.trim()); }
                        catch (NumberFormatException ignored) {}
                    }
                }

                // フォーマット済み文字列をJava側で生成（¥記号の問題を回避）
                row.hourlyWageStr    = fmt(d.getHourlyWage());
                row.dailyTotalStr    = daily > 0 ? fmt(daily) : "-";
                row.monthlyTotalStr  = fmt(d.getMonthlyTotal());
                row.commissionStr    = fmt(d.getCommission());
                row.receptionFeeStr  = fmt(d.getReceptionFee());
                row.customerFeeStr   = fmt(d.getCustomerFee());

                totalWage       += d.getMonthlyTotal()  != null ? d.getMonthlyTotal()  : 0;
                totalCommission += d.getCommission()     != null ? d.getCommission()    : 0;
                totalFees       += (d.getReceptionFee() != null ? d.getReceptionFee()  : 0)
                                 + (d.getCustomerFee()  != null ? d.getCustomerFee()   : 0);
                rows.add(row);
            }

            rows.sort((a, b) -> {
                LocalDate da = firstNonNull(
                    a.detail.getIntroductionDate(), a.detail.getWorkStartDate(), startDate);
                LocalDate db = firstNonNull(
                    b.detail.getIntroductionDate(), b.detail.getWorkStartDate(), startDate);
                return da.compareTo(db);
            });

            model.addAttribute("rows",              rows);
            model.addAttribute("totalCount",        rows.size());
            model.addAttribute("totalWageStr",      fmt(totalWage));
            model.addAttribute("totalCommissionStr",fmt(totalCommission));
            model.addAttribute("totalFeesStr",      fmt(totalFees));

        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            model.addAttribute("errorMsg", sw.toString());
        }

        return "sales-monthly";
    }

    /** 数値を「¥1,234」形式にフォーマット（null → "-"） */
    private String fmt(Number n) {
        if (n == null) return "-";
        long v = n.longValue();
        if (v == 0) return "-";
        return "\u00a5" + NumberFormat.getNumberInstance(Locale.JAPAN).format(v);
    }

    private boolean isInMonth(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }

    private LocalDate firstNonNull(LocalDate... dates) {
        for (LocalDate d : dates) if (d != null) return d;
        return LocalDate.MAX;
    }

    public static class MonthlyRow {
        public SalesDetail detail;
        public String personName      = "";
        public String customerName    = "";
        public String hourlyWageStr   = "-";
        public String dailyTotalStr   = "-";
        public String monthlyTotalStr = "-";
        public String commissionStr   = "-";
        public String receptionFeeStr = "-";
        public String customerFeeStr  = "-";
    }
}
