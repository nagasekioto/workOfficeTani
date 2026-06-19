package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/person/sales/monthly")
public class MonthlySalesController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;

    @GetMapping
    public String monthly(@RequestParam(required = false) String month,
                          HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        model.addAttribute("selectedMonth", month);

        if (month == null || month.isBlank()) {
            model.addAttribute("rows", new ArrayList<>());
            model.addAttribute("totalCount", 0);
            model.addAttribute("totalWage", 0L);
            model.addAttribute("totalCommission", 0L);
            model.addAttribute("totalFees", 0L);
            return "sales-monthly";
        }

        // 人物名マップ
        Map<Long, String> personMap = new HashMap<>();
        StreamSupport.stream(personRepository.findAll().spliterator(), false)
            .forEach(p -> personMap.put(p.getId(),
                p.getLastNameKanji() + " " + p.getFirstNameKanji()));

        // 求人者名マップ
        Map<Long, String> customerMap = new HashMap<>();
        StreamSupport.stream(customerRepository.findAll().spliterator(), false)
            .forEach(c -> customerMap.put(c.getId(),
                c.getLastNameKanji() + " " + c.getFirstNameKanji()));

        // salesId → personId マップ
        Map<Long, Long> salesPersonMap = new HashMap<>();
        StreamSupport.stream(salesRepository.findAll().spliterator(), false)
            .forEach(s -> salesPersonMap.put(s.getId(), s.getPersonId()));

        // 対象月の範囲
        YearMonth ym = YearMonth.parse(month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate   = ym.atEndOfMonth();

        // 全sales_detailsを取得してJava側で月フィルタ
        // 判定優先順位：紹介日 → 就労開始日 → 就労終了日 → （なければ全件含む）
        List<MonthlyRow> rows = new ArrayList<>();
        long totalWage = 0, totalCommission = 0, totalFees = 0;

        Iterable<SalesDetail> allDetails = salesDetailRepository.findAll();
        for (SalesDetail d : allDetails) {
            // 月判定：いずれかの日付が対象月に含まれるか
            boolean match = isInMonth(d.getIntroductionDate(), startDate, endDate)
                         || isInMonth(d.getWorkStartDate(),    startDate, endDate)
                         || isInMonth(d.getWorkEndDate(),      startDate, endDate);
            if (!match) continue;

            MonthlyRow row = new MonthlyRow();
            row.detail = d;

            Long personId = salesPersonMap.get(d.getSalesId());
            row.personName   = personId != null
                    ? personMap.getOrDefault(personId, "不明") : "不明";
            row.customerName = d.getCustomerId() != null
                    ? customerMap.getOrDefault(d.getCustomerId(), "不明") : "-";

            // 日給合計
            if (d.getDailyWages() != null && !d.getDailyWages().isBlank()) {
                for (String w : d.getDailyWages().split(",")) {
                    try { row.dailyTotal += Integer.parseInt(w.trim()); }
                    catch (NumberFormatException ignored) {}
                }
            }

            totalWage       += d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
            totalCommission += d.getCommission()   != null ? d.getCommission()   : 0;
            totalFees       += (d.getReceptionFee() != null ? d.getReceptionFee() : 0)
                             + (d.getCustomerFee()  != null ? d.getCustomerFee()  : 0);

            rows.add(row);
        }

        // ソート：紹介日→就労開始日の順
        rows.sort((a, b) -> {
            LocalDate da = firstNonNull(
                a.detail.getIntroductionDate(), a.detail.getWorkStartDate(), startDate);
            LocalDate db = firstNonNull(
                b.detail.getIntroductionDate(), b.detail.getWorkStartDate(), startDate);
            return da.compareTo(db);
        });

        model.addAttribute("rows", rows);
        model.addAttribute("totalCount",      rows.size());
        model.addAttribute("totalWage",       totalWage);
        model.addAttribute("totalCommission", totalCommission);
        model.addAttribute("totalFees",       totalFees);

        return "sales-monthly";
    }

    private boolean isInMonth(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null) return false;
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private LocalDate firstNonNull(LocalDate... dates) {
        for (LocalDate d : dates) if (d != null) return d;
        return LocalDate.MAX;
    }

    public static class MonthlyRow {
        public SalesDetail detail;
        public String personName   = "";
        public String customerName = "";
        public long   dailyTotal   = 0;
    }
}
