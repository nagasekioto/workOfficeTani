package jp.co.housekeeping.person_management.controller;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;
import jp.co.housekeeping.person_management.model.Person;

@Controller
public class OtherMenuController {

    @Autowired private SalesRepository       salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private CustomerRepository    customerRepository;
    @Autowired private PersonRepository      personRepository;

    // ─── 1-5 サブメニュー ────────────────────────────────────────
    @GetMapping("/other-menu")
    public String otherMenu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "other-menu";
    }

    // ─── 1-5-1 システム診断 ──────────────────────────────────────
    @GetMapping("/system-qa")
    public String systemQa(@RequestParam(required = false) String month,
                            HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        model.addAttribute("selectedMonth", month);

        // 月フィルタ用の YearMonth（null = 全期間）
        YearMonth filterYm = null;
        if (month != null && !month.isBlank()) {
            try { filterYm = YearMonth.parse(month); } catch (Exception ignored) {}
        }

        List<QaRow> unissuedRows      = new ArrayList<>();  // チェック①: receipt_no が NULL
        List<QaRow> noIssuedAtRows    = new ArrayList<>();  // チェック②: issued_at が NULL（receipt_no あり）
        List<QaRow> noReceptionFeeRows = new ArrayList<>(); // チェック③: reception_fee=0（receipt_no あり）

        for (Sales s : salesRepository.findAll()) {
            if (s.getId() == null) continue;

            Person person = null;
            if (s.getPersonId() != null) {
                person = personRepository.findById(s.getPersonId()).orElse(null);
            }
            String personName = person != null
                ? person.getLastNameKanji() + "　" + person.getFirstNameKanji() : "（不明）";

            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                // 月フィルタ: 参照日を決定
                java.time.LocalDate refDate = refDateOf(d);

                // 月フィルタ適用
                if (filterYm != null && refDate != null) {
                    if (refDate.getYear() != filterYm.getYear()
                        || refDate.getMonthValue() != filterYm.getMonthValue()) {
                        continue;
                    }
                }

                Customer c = d.getCustomerId() != null
                    ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                String customerName = c != null
                    ? c.getLastNameKanji() + "　" + c.getFirstNameKanji() : "（不明）";

                int wage = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;

                QaRow row = new QaRow();
                row.personId        = s.getPersonId();
                row.personName      = personName;
                row.customerName    = customerName;
                row.receiptNo       = d.getReceiptNo();
                row.workStartDate   = d.getWorkStartDate() != null ? d.getWorkStartDate().toString() : "";
                row.workEndDate     = d.getWorkEndDate()   != null ? d.getWorkEndDate().toString()   : "";
                row.introductionDate = d.getIntroductionDate() != null ? d.getIntroductionDate().toString() : "";
                row.wageStr         = String.format("%,d円", wage);
                row.issuedAt        = d.getIssuedAt() != null ? d.getIssuedAt().toString().replace("T", " ").substring(0, 16) : "";

                boolean hasReceiptNo = d.getReceiptNo() != null && !d.getReceiptNo().isEmpty();

                // チェック①: 賃金or受付料があるのに receipt_no が NULL
                boolean hasFee = wage > 0
                    || (d.getReceptionFee() != null && d.getReceptionFee() > 0)
                    || (d.getCustomerFee()  != null && d.getCustomerFee()  > 0);
                if (!hasReceiptNo && hasFee) {
                    unissuedRows.add(row);
                    continue; // チェック②③はreceipt_noがある前提なのでスキップ
                }

                if (hasReceiptNo) {
                    // チェック②: issued_at が NULL
                    if (d.getIssuedAt() == null) {
                        noIssuedAtRows.add(row);
                    }

                    // チェック③: reception_fee = 0（求職受付手数料管理簿に出てこない）
                    int recFee = d.getReceptionFee() != null ? d.getReceptionFee() : 0;
                    if (recFee == 0) {
                        noReceptionFeeRows.add(row);
                    }
                }
            }
        }

        model.addAttribute("unissuedRows",       unissuedRows);
        model.addAttribute("noIssuedAtRows",      noIssuedAtRows);
        model.addAttribute("noReceptionFeeRows",  noReceptionFeeRows);

        return "system-qa";
    }

    /** 参照日（issued_at優先） */
    private java.time.LocalDate refDateOf(SalesDetail d) {
        if (d.getIssuedAt()         != null) return d.getIssuedAt().toLocalDate();
        if (d.getWorkEndDate()       != null) return d.getWorkEndDate();
        if (d.getWorkStartDate()     != null) return d.getWorkStartDate();
        if (d.getIntroductionDate()  != null) return d.getIntroductionDate();
        return null;
    }

    public static class QaRow {
        public Long   personId;
        public String personName, customerName, receiptNo;
        public String workStartDate, workEndDate, introductionDate;
        public String wageStr, issuedAt;

        public Long   getPersonId()         { return personId; }
        public String getPersonName()       { return personName; }
        public String getCustomerName()     { return customerName; }
        public String getReceiptNo()        { return receiptNo; }
        public String getWorkStartDate()    { return workStartDate; }
        public String getWorkEndDate()      { return workEndDate; }
        public String getIntroductionDate() { return introductionDate; }
        public String getWageStr()          { return wageStr; }
        public String getIssuedAt()         { return issuedAt; }
    }
}
