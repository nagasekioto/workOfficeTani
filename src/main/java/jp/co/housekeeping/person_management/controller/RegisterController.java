package jp.co.housekeeping.person_management.controller;

import java.time.LocalDateTime;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.RegisterRecord;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.RegisterRecordRepository;

@Controller
@RequestMapping("/register")
public class RegisterController {

    @Autowired private PersonRepository personRepository;
    @Autowired private RegisterRecordRepository registerRecordRepository;

    private boolean checkAuth(HttpSession session) {
        return session.getAttribute("authenticated") != null;
    }

    // ─── 1-8-1 計算 ───────────────────────────────────
    @GetMapping("/calc")
    public String calc(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("persons", personRepository.findAll());
        return "register-calc";
    }

    @PostMapping("/calc/save")
    @ResponseBody
    public String saveCalc(
            @RequestParam Long personId,
            @RequestParam String workMonth,
            @RequestParam Integer salary,
            @RequestParam Integer fee,
            @RequestParam(required = false) String memo,
            HttpSession session) {
        if (!checkAuth(session)) return "UNAUTHORIZED";

        RegisterRecord record = new RegisterRecord();
        record.setPersonId(personId);
        record.setWorkMonth(workMonth);
        record.setSalary(salary);
        record.setFee(fee);
        record.setMemo(memo);
        record.setCreatedAt(LocalDateTime.now());
        registerRecordRepository.save(record);
        return "OK";
    }

    @PostMapping("/calc/delete")
    public String deleteCalc(@RequestParam Long id, @RequestParam(required = false) String month, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        registerRecordRepository.deleteById(id);
        if (month != null && !month.isBlank()) {
            return "redirect:/register/list?month=" + month;
        }
        return "redirect:/register/list";
    }

    // ─── 1-8-2 レジ一覧 ────────────────────────────────
    @GetMapping("/list")
    public String list(@RequestParam(required = false) String month,
                       HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";

        // 求職者名マップ
        Map<Long, String> personMap = new HashMap<>();
        StreamSupport.stream(personRepository.findAll().spliterator(), false).forEach(p ->
            personMap.put(p.getId(), p.getLastNameKanji() + " " + p.getFirstNameKanji()));

        if (month != null && !month.isBlank()) {
            List<RegisterRecord> raw = registerRecordRepository.findByWorkMonth(month);
            List<RegisterRow> records = new ArrayList<>();
            long totalSalary = 0, totalFee = 0;
            for (RegisterRecord r : raw) {
                RegisterRow row = new RegisterRow();
                row.id = r.getId();
                row.personName = personMap.getOrDefault(r.getPersonId(), "不明");
                row.workMonth = r.getWorkMonth();
                row.salary = r.getSalary() != null ? r.getSalary() : 0;
                row.fee = r.getFee() != null ? r.getFee() : 0;
                row.memo = r.getMemo();
                row.createdAt = r.getCreatedAt();
                records.add(row);
                totalSalary += row.salary;
                totalFee += row.fee;
            }
            model.addAttribute("records", records);
            model.addAttribute("totalSalary", totalSalary);
            model.addAttribute("totalFee", totalFee);
            model.addAttribute("selectedMonth", month);
        } else {
            model.addAttribute("records", new ArrayList<>());
            model.addAttribute("totalSalary", 0);
            model.addAttribute("totalFee", 0);
            model.addAttribute("selectedMonth", null);
        }

        return "register-list";
    }

    // ─── 1-8-3 手数料管理簿 ─────────────────────────────
    @GetMapping("/fee-ledger")
    public String feeLedger(@RequestParam(required = false) Integer year,
                            HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";

        // 年リスト（2020～現在＋2年）
        int currentYear = LocalDateTime.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int y = 2020; y <= currentYear + 1; y++) years.add(y);
        model.addAttribute("years", years);

        if (year != null) {
            List<RegisterRecord> raw = registerRecordRepository.findByYear(year + "-%");
            // 月別集計
            Map<String, long[]> monthly = new HashMap<>();
            for (int m = 1; m <= 12; m++) {
                monthly.put(String.format("%02d", m), new long[]{0, 0, 0}); // count, salary, fee
            }
            for (RegisterRecord r : raw) {
                String m = r.getWorkMonth().substring(5, 7); // "2025-03" -> "03"
                long[] vals = monthly.get(m);
                if (vals != null) {
                    vals[0]++;
                    vals[1] += r.getSalary() != null ? r.getSalary() : 0;
                    vals[2] += r.getFee() != null ? r.getFee() : 0;
                }
            }

            List<MonthlyRow> monthlyRows = new ArrayList<>();
            long yearSalary = 0, yearFee = 0, yearCount = 0;
            for (int m = 1; m <= 12; m++) {
                long[] vals = monthly.get(String.format("%02d", m));
                if (vals[0] > 0) {
                    MonthlyRow row = new MonthlyRow();
                    row.month = String.valueOf(m);
                    row.count = vals[0];
                    row.salary = vals[1];
                    row.fee = vals[2];
                    monthlyRows.add(row);
                    yearSalary += vals[1];
                    yearFee += vals[2];
                    yearCount += vals[0];
                }
            }

            model.addAttribute("monthlyRows", monthlyRows);
            model.addAttribute("yearTotalSalary", yearSalary);
            model.addAttribute("yearTotalFee", yearFee);
            model.addAttribute("yearTotalCount", yearCount);
            model.addAttribute("selectedYear", year);
        } else {
            model.addAttribute("monthlyRows", new ArrayList<>());
            model.addAttribute("yearTotalSalary", 0L);
            model.addAttribute("yearTotalFee", 0L);
            model.addAttribute("yearTotalCount", 0L);
            model.addAttribute("selectedYear", null);
        }

        return "register-fee-ledger";
    }

    // ─── 内部クラス ────────────────────────────────────
    public static class RegisterRow {
        public Long id;
        public String personName;
        public String workMonth;
        public long salary;
        public long fee;
        public String memo;
        public LocalDateTime createdAt;
    }

    public static class MonthlyRow {
        public String month;
        public long count;
        public long salary;
        public long fee;
    }
}
