package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPCellEvent;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;
import org.springframework.jdbc.core.JdbcTemplate;

@Controller
public class ReportMenuController {

    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private SalesRepository       salesRepository;
    @Autowired private CustomerRepository    customerRepository;
    @Autowired private JdbcTemplate          jdbcTemplate;

    private static final double FEE_RATE = 0.15;

    // ─── 1-3 サブメニュー ────────────────────────────────────────
    @GetMapping("/fee-menu")
    public String feeMenu(HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "fee-menu";
    }

    // ─── 1-3-1 紹介手数料管理簿 一覧 ────────────────────────────
    @RequestMapping("/report-menu")
    public String list(@RequestParam(required = false) String month,
                        HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        if (month == null || month.isBlank()) {
            String cur = LocalDateTime.now().getYear() + "-"
                + String.format("%02d", LocalDateTime.now().getMonthValue());
            return "redirect:/report-menu?month=" + cur;
        }
        model.addAttribute("selectedMonth", month);
        model.addAttribute("items", new ArrayList<FeeLedgerRow>());
        try {
            model.addAttribute("items", buildRows(YearMonth.parse(month)));
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMsg", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return "report-menu";
    }

    // ─── 1-3-1 日雇1ヶ月・臨時3ヶ月 保存 ───────────────────────
    @PostMapping("/report-menu/save-daily-wage")
    public String saveDailyWage(@RequestParam Map<String,String> params,
                                 HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        String month = params.get("month");
        params.forEach((key, value) -> {
            if (key.startsWith("dw_")) {
                try {
                    Long detailId = Long.parseLong(key.substring(3));
                    int val = value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
                    jdbcTemplate.update(
                        "UPDATE sales_details SET daily_wage_1month = ? WHERE id = ?",
                        val, detailId);
                } catch (Exception ignored) {}
            } else if (key.startsWith("t3_")) {
                try {
                    Long detailId = Long.parseLong(key.substring(3));
                    int val = value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
                    jdbcTemplate.update(
                        "UPDATE sales_details SET temp_3month = ? WHERE id = ?",
                        val, detailId);
                } catch (Exception ignored) {}
            }
        });
        return "redirect:/report-menu?month=" + (month != null ? month : "");
    }

    // ─── 1-3-1 PDF ────────────────────────────────────────────────
    @GetMapping("/report-menu/pdf")
    public void pdf(@RequestParam(required = false) String month,
                    @RequestParam(required = false) String dl,
                    HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }
        YearMonth ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildPdf131(buildRows(ym), ym, baos);
        response.setContentType("application/pdf");
        boolean download = "1".equals(dl);
        response.setHeader("Content-Disposition",
            (download ? "attachment" : "inline") + "; filename=intro-fee-ledger-" + ym + ".pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ─── 1-3-2 求職受付手数料管理簿 一覧 ───────────────────────
    @GetMapping("/fee-reception-ledger")
    public String receptionLedger(@RequestParam(required = false) String month,
                                   HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        if (month == null || month.isBlank()) {
            String cur = LocalDateTime.now().getYear() + "-"
                + String.format("%02d", LocalDateTime.now().getMonthValue());
            return "redirect:/fee-reception-ledger?month=" + cur;
        }
        model.addAttribute("selectedMonth", month);
        try {
            model.addAttribute("items", buildReceptionRows(YearMonth.parse(month)));
        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
        }
        return "fee-reception-ledger";
    }

    // ─── 1-3-2 PDF ────────────────────────────────────────────────
    @GetMapping("/fee-reception-ledger/pdf")
    public void receptionPdf(@RequestParam(required = false) String month,
                              @RequestParam(required = false) String dl,
                              HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }
        YearMonth ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildPdf132(buildReceptionRows(ym), ym, baos);
        response.setContentType("application/pdf");
        boolean download = "1".equals(dl);
        response.setHeader("Content-Disposition",
            (download ? "attachment" : "inline") + "; filename=reception-fee-ledger-" + ym + ".pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ─── 1-3-3 手数料収入決算表 ────────────────────────────────
    @GetMapping("/fee-settlement")
    public String feeSettlement(@RequestParam(required = false) Integer compYear,
                                 @RequestParam(required = false) Integer laborYear,
                                 HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        int curYear = LocalDateTime.now().getYear();
        List<Integer> yearList = new ArrayList<>();
        for (int y = curYear + 1; y >= 2020; y--) yearList.add(y);
        model.addAttribute("yearList", yearList);
        model.addAttribute("selectedCompYear", compYear);
        model.addAttribute("selectedLaborYear", laborYear);

        // 会社決算: 2月〜翌1月
        List<Integer> compMonths = List.of(2,3,4,5,6,7,8,9,10,11,12,1);
        model.addAttribute("compMonths", compMonths);
        // 労働局: 4月〜翌3月
        List<Integer> laborMonths = List.of(4,5,6,7,8,9,10,11,12,1,2,3);
        model.addAttribute("laborMonths", laborMonths);

        if (compYear != null) {
            model.addAttribute("compData", buildSettlementData(compYear, compMonths, "comp"));
        }
        SettlementData laborData = null;
        if (laborYear != null) {
            laborData = buildSettlementData(laborYear, laborMonths, "labor");
            model.addAttribute("laborData", laborData);
            model.addAttribute("reportData", buildReportData(laborYear, laborMonths, laborData));
        }
        return "fee-settlement";
    }

    // ─── 1-3-3 サンケアネット保存 ────────────────────────────────
    @PostMapping("/fee-settlement/save-sancare")
    public String saveSancare(@RequestParam Map<String,String> params,
                               HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        Integer compYear  = null;
        Integer laborYear = null;
        try { compYear  = Integer.parseInt(params.get("compYear"));  } catch (Exception ignored) {}
        try { laborYear = Integer.parseInt(params.get("laborYear")); } catch (Exception ignored) {}

        params.forEach((key, value) -> {
            // sc_YYYY_M or scl_YYYY_M
            if (key.startsWith("sc_") || key.startsWith("scl_")) {
                try {
                    String[] parts = key.split("_");
                    // parts[0]=sc or scl, parts[1]=year, parts[2]=month
                    int yr = Integer.parseInt(parts[1]);
                    int mo = Integer.parseInt(parts[2]);
                    String ym = yr + "-" + String.format("%02d", mo);
                    int val = (value == null || value.isBlank()) ? 0 : Integer.parseInt(value.trim());
                    jdbcTemplate.update(
                        "INSERT INTO sancare_net_monthly (year_month, amount, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                        "ON CONFLICT (year_month) DO UPDATE SET amount = EXCLUDED.amount, updated_at = CURRENT_TIMESTAMP",
                        ym, val);
                } catch (Exception ignored) {}
            }
        });

        String redirect = "/fee-settlement";
        if (compYear != null || laborYear != null) {
            redirect += "?compYear=" + (compYear != null ? compYear : "")
                      + "&laborYear=" + (laborYear != null ? laborYear : "");
        }
        return "redirect:" + redirect;
    }

    // ─── 1-3-3 PDF ────────────────────────────────────────────────
    @GetMapping("/fee-settlement/pdf")
    public void settlementPdf(@RequestParam(required = false) Integer compYear,
                               @RequestParam(required = false) Integer laborYear,
                               @RequestParam(required = false) String dl,
                               HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildPdf133(compYear, laborYear, baos);
        response.setContentType("application/pdf");
        boolean download = "1".equals(dl);
        response.setHeader("Content-Disposition",
            (download ? "attachment" : "inline") + "; filename=fee-settlement.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ═══════════════════════════════════════════════════════════════
    // データ取得メソッド
    // ═══════════════════════════════════════════════════════════════

    /** 1-3-1: 紹介手数料管理簿 行データ */
    private List<FeeLedgerRow> buildRows(YearMonth ym) {
        List<FeeLedgerRow> list = new ArrayList<>();
        LocalDate monthEnd = ym.atEndOfMonth();
        String dateStr = monthEnd.getYear() + "/" + monthEnd.getMonthValue() + "/" + monthEnd.getDayOfMonth();

        for (Sales s : salesRepository.findAll()) {
            if (s.getId() == null) continue;
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                try { if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue; }
                catch (Exception e2) { continue; }

                LocalDate rd = null;
                // 領収書発行日を最優先
                if      (d.getIssuedAt()          != null) rd = d.getIssuedAt().toLocalDate();
                else if (d.getWorkEndDate()        != null) rd = d.getWorkEndDate();
                else if (d.getWorkStartDate()      != null) rd = d.getWorkStartDate();
                else if (d.getIntroductionDate()   != null) rd = d.getIntroductionDate();
                if (rd == null) continue;
                if (rd.getYear() != ym.getYear() || rd.getMonthValue() != ym.getMonthValue()) continue;

                Customer c = d.getCustomerId() != null
                    ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                String cName = c != null ? c.getLastNameKanji() + "　" + c.getFirstNameKanji() : "";

                int wage   = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                int cFee   = d.getCustomerFee()  != null ? d.getCustomerFee()  : 0;
                int comm   = (int)(wage * FEE_RATE);
                int tax    = (int)(comm * 0.10);

                int dw1m = 0, t3m = 0;
                try {
                    Integer v = jdbcTemplate.queryForObject(
                        "SELECT daily_wage_1month FROM sales_details WHERE id = ?", Integer.class, d.getId());
                    if (v != null) dw1m = v;
                } catch (Exception ignored) {}
                try {
                    Integer v = jdbcTemplate.queryForObject(
                        "SELECT temp_3month FROM sales_details WHERE id = ?", Integer.class, d.getId());
                    if (v != null) t3m = v;
                } catch (Exception ignored) {}

                FeeLedgerRow row = new FeeLedgerRow();
                row.detailId         = d.getId();
                row.receiptDate      = dateStr;
                row.customerName     = cName;
                row.wage             = wage;
                row.commission       = comm;
                row.tax              = tax;
                row.customerFee      = cFee;
                row.receiptNo        = d.getReceiptNo();
                row.dailyWage1Month  = dw1m;
                row.temp3Month       = t3m;
                row.wageStr          = fmt(wage);
                row.commissionStr    = fmt(comm);
                row.taxStr           = fmt(tax);
                row.customerFeeStr   = fmt(cFee);
                row.dailyWage1MonthStr = fmt(dw1m);
                row.temp3MonthStr    = fmt(t3m);
                list.add(row);
            }
        }
        list.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.receiptNo), Integer.parseInt(b.receiptNo)); }
            catch (NumberFormatException e) { return a.receiptNo.compareTo(b.receiptNo); }
        });
        return list;
    }

    /** 1-3-2: 求職受付手数料管理簿 行データ */
    private List<ReceptionLedgerRow> buildReceptionRows(YearMonth ym) {
        List<ReceptionLedgerRow> list = new ArrayList<>();
        LocalDate monthEnd = ym.atEndOfMonth();
        String dateStr = monthEnd.getYear() + "/" + monthEnd.getMonthValue() + "/" + monthEnd.getDayOfMonth();

        for (Sales s : salesRepository.findAll()) {
            if (s.getId() == null) continue;
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                try { if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue; }
                catch (Exception e2) { continue; }

                // reception_fee がある行のみ対象
                int recFee = d.getReceptionFee() != null ? d.getReceptionFee() : 0;
                if (recFee == 0) continue;

                LocalDate rd = null;
                // 領収書発行日を最優先
                if      (d.getIssuedAt()          != null) rd = d.getIssuedAt().toLocalDate();
                else if (d.getWorkEndDate()        != null) rd = d.getWorkEndDate();
                else if (d.getWorkStartDate()      != null) rd = d.getWorkStartDate();
                else if (d.getIntroductionDate()   != null) rd = d.getIntroductionDate();
                if (rd == null) continue;
                if (rd.getYear() != ym.getYear() || rd.getMonthValue() != ym.getMonthValue()) continue;

                Customer c = d.getCustomerId() != null
                    ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                String cName = c != null ? c.getLastNameKanji() + "　" + c.getFirstNameKanji() : "";

                // person名（求職者）
                Long personId = s.getPersonId();
                String personName = "";
                if (personId != null) {
                    try {
                        String n = jdbcTemplate.queryForObject(
                            "SELECT last_name_kanji || '　' || first_name_kanji FROM persons WHERE id = ?",
                            String.class, personId);
                        if (n != null) personName = n;
                    } catch (Exception ignored) {}
                }

                ReceptionLedgerRow row = new ReceptionLedgerRow();
                row.receiptDate     = dateStr;
                row.customerName    = personName.isBlank() ? cName : personName;
                row.receptionFee    = recFee;
                row.receptionFeeStr = fmt(recFee);
                row.receiptNo       = d.getReceiptNo();
                list.add(row);
            }
        }
        list.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.receiptNo), Integer.parseInt(b.receiptNo)); }
            catch (NumberFormatException e) { return a.receiptNo.compareTo(b.receiptNo); }
        });
        return list;
    }

    /** 1-3-3: 決算表データ */
    private SettlementData buildSettlementData(int baseYear, List<Integer> months, String type) {
        SettlementData d = new SettlementData();

        for (int m : months) {
            // 年の判定: 会社決算は2月〜12月がbaseYear、1月はbaseYear+1
            // 労働局は4月〜12月がbaseYear、1月〜3月はbaseYear+1
            int actualYear = baseYear;
            if ("comp".equals(type) && m == 1) actualYear = baseYear + 1;
            if ("labor".equals(type) && (m == 1 || m == 2 || m == 3)) actualYear = baseYear + 1;

            String ym = actualYear + "-" + String.format("%02d", m);
            YearMonth yearMonth;
            try { yearMonth = YearMonth.parse(ym); } catch (Exception e) { continue; }

            // Thymeleaf の Map アクセスは String キーが確実なため文字列化
            String key = String.valueOf(m);

            // ②求職受付手数料管理簿の求人受付事務費(710円×件数) = reception_fee合計
            int fee710 = calcReceptionFee710(yearMonth);
            d.receptionFee710.put(key, fee710);

            // ③紹介手数料管理簿の求人受付事務費(1000円×件数) = customer_fee合計
            int fee1000 = calcCustomerFee1000(yearMonth);
            d.receptionFee1000.put(key, fee1000);

            // ④紹介手数料 = 手数料※1累計 + 手数料※2累計 - サンケアネット
            int comm1 = calcCommission(yearMonth);   // 手数料※1
            int comm2 = calcTax(yearMonth);           // 手数料※2
            int sancare = getSancareNet(ym);
            int introFee = comm1 + comm2 - sancare;
            d.introFee.put(key, introFee);

            // ①サンケアネット
            d.sancareNet.put(key, sancare);

            // 月別合計 = ① + ② + ③ + ④
            int monthTotal = fee710 + fee1000 + introFee + sancare;
            d.monthTotal.put(key, monthTotal);
        }

        d.receptionFee710Total  = d.receptionFee710.values().stream().mapToInt(Integer::intValue).sum();
        d.receptionFee1000Total = d.receptionFee1000.values().stream().mapToInt(Integer::intValue).sum();
        d.introFeeTotal         = d.introFee.values().stream().mapToInt(Integer::intValue).sum();
        d.sancareNetTotal       = d.sancareNet.values().stream().mapToInt(Integer::intValue).sum();
        d.grandTotal            = d.monthTotal.values().stream().mapToInt(Integer::intValue).sum();

        return d;
    }

    /**
     * 事業報告書月別表（労働局用年度別）データ組み立て。
     *
     * 現時点で仕様が確定している以下6行のみ実装:
     *   求人-臨時, 求職-有効, 手数料-常用, 手数料-臨時, 手数料-日雇, 手数料-求職受付手数料
     * 他の行（求人-常用/日雇、求職-新規申込み、就職-常用/臨時/日雇）は未実装のため "-"/0 のまま。
     */
    private ReportData buildReportData(int baseYear, List<Integer> months, SettlementData laborData) {
        ReportData rd = new ReportData();

        for (int m : months) {
            int actualYear = baseYear;
            if (m == 1 || m == 2 || m == 3) actualYear = baseYear + 1;
            String key = String.valueOf(m);

            YearMonth yearMonth;
            try { yearMonth = YearMonth.parse(actualYear + "-" + String.format("%02d", m)); }
            catch (Exception e) { continue; }

            // 求人-臨時: 紹介手数料管理簿(1-3-1)の「臨時3ヶ月」に金額が入っている"件数"
            //           (金額の合計ではない。例: 1,000円と3,000円の2件なら "2")
            int jobTemp = countTemp3MonthRecords(yearMonth);
            rd.jobTemp.put(key, jobTemp);

            // 求職-有効: 受付料 710円 の"件数"
            int seekerValid = countReceptionFee710Records(yearMonth);
            rd.seekerValid.put(key, seekerValid);

            // 手数料-臨時: 紹介手数料管理簿(1-3-1)の当月「臨時3ヶ月」合計金額
            int feeTemp = sumTemp3Month(yearMonth);
            rd.feeTemp.put(key, feeTemp);

            // 手数料-日雇: 紹介手数料管理簿(1-3-1)の当月「日雇1ヶ月」合計金額
            int feeDaily = sumDailyWage1Month(yearMonth);
            rd.feeDaily.put(key, feeDaily);

            // 手数料-求職受付手数料: 手数料収入決算表(労働局用決算表)の求職受付手数料
            int feeReception = laborData.receptionFee710.getOrDefault(key, 0);
            rd.feeReception.put(key, feeReception);

            // 手数料-常用: 労働局用決算表の紹介手数料の1行目 + サンケアネット - 手数料-臨時
            int introFee = laborData.introFee.getOrDefault(key, 0);
            int sancare  = laborData.sancareNet.getOrDefault(key, 0);
            int feeRegular = introFee + sancare - feeTemp;
            rd.feeRegular.put(key, feeRegular);
        }

        rd.jobTempTotal     = rd.jobTemp.values().stream().mapToInt(Integer::intValue).sum();
        rd.seekerValidTotal = rd.seekerValid.values().stream().mapToInt(Integer::intValue).sum();
        rd.feeRegularTotal  = rd.feeRegular.values().stream().mapToInt(Integer::intValue).sum();
        rd.feeTempTotal     = rd.feeTemp.values().stream().mapToInt(Integer::intValue).sum();
        rd.feeDailyTotal    = rd.feeDaily.values().stream().mapToInt(Integer::intValue).sum();
        rd.feeReceptionTotal = rd.feeReception.values().stream().mapToInt(Integer::intValue).sum();

        return rd;
    }

    /** その月の「臨時3ヶ月」に金額が入っている(0でない)明細の件数 */
    private int countTemp3MonthRecords(YearMonth ym) {
        String sql =
            "SELECT COUNT(*) FROM sales_details " +
            "WHERE receipt_no IS NOT NULL AND receipt_no <> '' " +
            "AND temp_3month IS NOT NULL AND temp_3month <> 0 " +
            "AND EXTRACT(YEAR  FROM COALESCE(issued_at::date, work_end_date, work_start_date, introduction_date)) = ? " +
            "AND EXTRACT(MONTH FROM COALESCE(issued_at::date, work_end_date, work_start_date, introduction_date)) = ?";
        Integer v = jdbcTemplate.queryForObject(sql, Integer.class, ym.getYear(), ym.getMonthValue());
        return v != null ? v : 0;
    }

    /** その月の「臨時3ヶ月」の合計金額 */
    private int sumTemp3Month(YearMonth ym) {
        String sql =
            "SELECT COALESCE(SUM(temp_3month),0) FROM sales_details " +
            "WHERE receipt_no IS NOT NULL AND receipt_no <> '' " +
            "AND EXTRACT(YEAR  FROM COALESCE(issued_at::date, work_end_date, work_start_date, introduction_date)) = ? " +
            "AND EXTRACT(MONTH FROM COALESCE(issued_at::date, work_end_date, work_start_date, introduction_date)) = ?";
        Integer v = jdbcTemplate.queryForObject(sql, Integer.class, ym.getYear(), ym.getMonthValue());
        return v != null ? v : 0;
    }

    /** その月の「日雇1ヶ月」の合計金額 */
    private int sumDailyWage1Month(YearMonth ym) {
        String sql =
            "SELECT COALESCE(SUM(daily_wage_1month),0) FROM sales_details " +
            "WHERE receipt_no IS NOT NULL AND receipt_no <> '' " +
            "AND EXTRACT(YEAR  FROM COALESCE(issued_at::date, work_end_date, work_start_date, introduction_date)) = ? " +
            "AND EXTRACT(MONTH FROM COALESCE(issued_at::date, work_end_date, work_start_date, introduction_date)) = ?";
        Integer v = jdbcTemplate.queryForObject(sql, Integer.class, ym.getYear(), ym.getMonthValue());
        return v != null ? v : 0;
    }

    /** その月の 受付料=710円 の明細件数（求職-有効） */
    private int countReceptionFee710Records(YearMonth ym) {
        String sql =
            "SELECT COUNT(*) FROM sales_details " +
            "WHERE receipt_no IS NOT NULL AND receipt_no <> '' " +
            "AND reception_fee = 710 " +
            "AND EXTRACT(YEAR  FROM COALESCE(issued_at::date, work_end_date, work_start_date, introduction_date)) = ? " +
            "AND EXTRACT(MONTH FROM COALESCE(issued_at::date, work_end_date, work_start_date, introduction_date)) = ?";
        Integer v = jdbcTemplate.queryForObject(sql, Integer.class, ym.getYear(), ym.getMonthValue());
        return v != null ? v : 0;
    }

    private int calcReceptionFee710(YearMonth ym) {
        int total = 0;
        for (Sales s : salesRepository.findAll()) {
            if (s.getId() == null) continue;
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;
                int fee = d.getReceptionFee() != null ? d.getReceptionFee() : 0;
                if (fee == 0) continue;
                LocalDate rd = getRefDate(d);
                if (rd == null) continue;
                if (rd.getYear() == ym.getYear() && rd.getMonthValue() == ym.getMonthValue())
                    total += fee;
            }
        }
        return total;
    }

    private int calcCustomerFee1000(YearMonth ym) {
        int total = 0;
        for (Sales s : salesRepository.findAll()) {
            if (s.getId() == null) continue;
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;
                int fee = d.getCustomerFee() != null ? d.getCustomerFee() : 0;
                if (fee == 0) continue;
                LocalDate rd = getRefDate(d);
                if (rd == null) continue;
                if (rd.getYear() == ym.getYear() && rd.getMonthValue() == ym.getMonthValue())
                    total += fee;
            }
        }
        return total;
    }

    private int calcCommission(YearMonth ym) {
        int total = 0;
        for (Sales s : salesRepository.findAll()) {
            if (s.getId() == null) continue;
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;
                int wage = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                if (wage == 0) continue;
                LocalDate rd = getRefDate(d);
                if (rd == null) continue;
                if (rd.getYear() == ym.getYear() && rd.getMonthValue() == ym.getMonthValue())
                    total += (int)(wage * FEE_RATE);
            }
        }
        return total;
    }

    private int calcTax(YearMonth ym) {
        int total = 0;
        for (Sales s : salesRepository.findAll()) {
            if (s.getId() == null) continue;
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;
                int wage = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                if (wage == 0) continue;
                LocalDate rd = getRefDate(d);
                if (rd == null) continue;
                if (rd.getYear() == ym.getYear() && rd.getMonthValue() == ym.getMonthValue()) {
                    int comm = (int)(wage * FEE_RATE);
                    total += (int)(comm * 0.10);
                }
            }
        }
        return total;
    }

    private int getSancareNet(String ym) {
        try {
            Integer v = jdbcTemplate.queryForObject(
                "SELECT amount FROM sancare_net_monthly WHERE year_month = ?", Integer.class, ym);
            return v != null ? v : 0;
        } catch (Exception e) { return 0; }
    }

    private LocalDate getRefDate(SalesDetail d) {
        // 領収書発行日を最優先（手数料管理簿は領収書発行月で管理）
        if (d.getIssuedAt()         != null) return d.getIssuedAt().toLocalDate();
        if (d.getWorkEndDate()      != null) return d.getWorkEndDate();
        if (d.getWorkStartDate()    != null) return d.getWorkStartDate();
        if (d.getIntroductionDate() != null) return d.getIntroductionDate();
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // PDF 生成
    // ═══════════════════════════════════════════════════════════════

    /** 1-3-1 PDF */
    private void buildPdf131(List<FeeLedgerRow> rows, YearMonth ym, ByteArrayOutputStream baos)
            throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4.rotate(), 18, 18, 18, 18);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleF = new Font(bf, 11, Font.BOLD);
        Font bold   = new Font(bf, 8,  Font.BOLD);
        Font normal = new Font(bf, 8);
        Font noteF  = new Font(bf, 6);

        Paragraph title = new Paragraph("紹介手数料管理簿〔届出制手数料用〕　" + ym, titleF);
        title.setAlignment(Element.ALIGN_CENTER); title.setSpacingAfter(6);
        doc.add(title);

        float[] w = {2.2f, 2.6f, 1.8f, 1.8f, 1.4f, 1.8f, 1.2f, 1.4f, 1.6f, 1.6f};
        PdfPTable t = new PdfPTable(w);
        t.setWidthPercentage(100); t.setSpacingBefore(4);

        t.addCell(hdr("領収\n年月日", bold)); t.addCell(hdr("支払者名", bold));
        t.addCell(hdr("賃金", bold));         t.addCell(hdr("手数料※1\n届出手数料", bold));
        t.addCell(hdr("手数料※2", bold));     t.addCell(hdr("求人受付\n事務費", bold));
        t.addCell(hdr("手数料\n割合", bold));  t.addCell(hdr("備考\n領収番号", bold));
        t.addCell(hdr("日雇\n1ヶ月", bold));  t.addCell(hdr("臨時\n3ヶ月", bold));

        int sw=0, sc=0, st=0, sf=0, sd=0, s3=0;
        for (FeeLedgerRow r : rows) {
            t.addCell(cen(r.receiptDate,       normal)); t.addCell(cen(r.customerName, normal));
            t.addCell(rit(r.wageStr,           normal)); t.addCell(rit(r.commissionStr, normal));
            t.addCell(rit(r.taxStr,            normal)); t.addCell(rit(r.customerFeeStr, normal));
            t.addCell(cen("15%",               normal)); t.addCell(cen(r.receiptNo, normal));
            t.addCell(rit(r.dailyWage1MonthStr,normal)); t.addCell(rit(r.temp3MonthStr, normal));
            sw+=r.wage; sc+=r.commission; st+=r.tax; sf+=r.customerFee;
            sd+=r.dailyWage1Month; s3+=r.temp3Month;
        }

        // ページ計
        t.addCell(spanCell("ページ計", bold, 2));
        t.addCell(rit(fmt(sw), bold)); t.addCell(rit(fmt(sc), bold));
        t.addCell(rit(fmt(st), bold)); t.addCell(rit(fmt(sf), bold));
        t.addCell(diag(bold)); t.addCell(diag(bold));
        t.addCell(rit(fmt(sd), bold)); t.addCell(rit(fmt(s3), bold));

        // 月分累計
        t.addCell(spanCell(ym.getMonthValue() + "月分累計", bold, 2));
        t.addCell(rit(fmt(sw), bold)); t.addCell(rit(fmt(sc), bold));
        t.addCell(rit(fmt(st), bold)); t.addCell(rit(fmt(sf), bold));
        t.addCell(diag(bold)); t.addCell(diag(bold));
        t.addCell(rit(fmt(sd), bold)); t.addCell(rit(fmt(s3), bold));

        doc.add(t);
        Paragraph n1 = new Paragraph("※1は、徴収した届け出手数料の総額から第二種特別加入料に充てるべき手数料額を除いた額を記載する。", noteF);
        n1.setSpacingBefore(4); doc.add(n1);
        doc.add(new Paragraph("※2は、第二種特別加入保険料に充てるべき手数料。", noteF));
        doc.close();
    }

    /** 1-3-2 PDF */
    private void buildPdf132(List<ReceptionLedgerRow> rows, YearMonth ym, ByteArrayOutputStream baos)
            throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4, 18, 18, 18, 18);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleF = new Font(bf, 11, Font.BOLD);
        Font bold   = new Font(bf, 9,  Font.BOLD);
        Font normal = new Font(bf, 9);

        Paragraph title = new Paragraph("求職受付手数料管理簿　" + ym, titleF);
        title.setAlignment(Element.ALIGN_CENTER); title.setSpacingAfter(8);
        doc.add(title);

        float[] w = {2.5f, 3.5f, 2.5f, 1.5f, 2.0f};
        PdfPTable t = new PdfPTable(w);
        t.setWidthPercentage(100); t.setSpacingBefore(4);

        t.addCell(hdr("領収年月日", bold)); t.addCell(hdr("支払者名（求職者）", bold));
        t.addCell(hdr("求人受付事務費\n（710円×件数）", bold));
        t.addCell(hdr("手数料割合", bold)); t.addCell(hdr("備考（領収番号）", bold));

        int total = 0;
        for (ReceptionLedgerRow r : rows) {
            t.addCell(cen(r.receiptDate,    normal));
            t.addCell(cen(r.customerName,   normal));
            t.addCell(rit(r.receptionFeeStr,normal));
            t.addCell(cen("固定",           normal));
            t.addCell(cen(r.receiptNo,      normal));
            total += r.receptionFee;
        }

        PdfPCell lc = new PdfPCell(new Phrase(ym.getMonthValue() + "月分累計", bold));
        lc.setColspan(2); lc.setBorder(Rectangle.BOX); lc.setPadding(3);
        lc.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(lc);
        t.addCell(rit(fmt(total), bold));
        t.addCell(diag(bold)); t.addCell(diag(bold));

        doc.add(t);
        doc.close();
    }

    /** 1-3-3 PDF */
    private void buildPdf133(Integer compYear, Integer laborYear, ByteArrayOutputStream baos)
            throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4.rotate(), 14, 14, 14, 14);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleF  = new Font(bf, 11, Font.BOLD);
        Font bold    = new Font(bf, 7,  Font.BOLD);
        Font normal  = new Font(bf, 7);

        List<Integer> compMonths  = List.of(2,3,4,5,6,7,8,9,10,11,12,1);
        List<Integer> laborMonths = List.of(4,5,6,7,8,9,10,11,12,1,2,3);

        // 会社決算表
        if (compYear != null) {
            Paragraph t = new Paragraph("手数料収入決算表（会社決算表）　"
                + compYear + "年2月 ～ " + (compYear+1) + "年1月　（有）ワークオフィス谷", titleF);
            t.setSpacingAfter(4); doc.add(t);

            SettlementData sd = buildSettlementData(compYear, compMonths, "comp");
            doc.add(buildSettlementTable(sd, compMonths, bold, normal, bf));
            doc.add(new Paragraph(" ", normal));
        }

        // 労働局決算表
        SettlementData laborSettlement = null;
        if (laborYear != null) {
            Paragraph t = new Paragraph("手数料収入決算表（労働局用決算表）　"
                + laborYear + "年4月 ～ " + (laborYear+1) + "年3月", titleF);
            t.setSpacingAfter(4); doc.add(t);

            laborSettlement = buildSettlementData(laborYear, laborMonths, "labor");
            doc.add(buildSettlementTable(laborSettlement, laborMonths, bold, normal, bf));
            doc.add(new Paragraph(" ", normal));
        }

        // 事業報告書月別表
        Paragraph rpt = new Paragraph("事業報告書月別表（労働局用年度別）", titleF);
        rpt.setSpacingAfter(4); doc.add(rpt);
        if (laborYear != null) {
            ReportData reportData = buildReportData(laborYear, laborMonths, laborSettlement);
            doc.add(buildReportTable(reportData, laborMonths, bold, normal));
        }

        doc.close();
    }

    private PdfPTable buildSettlementTable(SettlementData sd, List<Integer> months,
                                            Font bold, Font normal, BaseFont bf)
            throws DocumentException {
        float[] w = new float[months.size() + 2];
        w[0] = 2.5f;
        for (int i = 1; i <= months.size(); i++) w[i] = 1.2f;
        w[months.size() + 1] = 1.5f;

        PdfPTable t = new PdfPTable(w);
        t.setWidthPercentage(100); t.setSpacingBefore(3);

        // ヘッダー
        t.addCell(hdrN("月　別", bold));
        for (int m : months) t.addCell(hdrN(m + "月", bold));
        t.addCell(hdrN("決算額", bold));

        // 求職受付手数料（710円×件数）1行
        t.addCell(hdrN("求職受付\n手数料", bold));
        for (int m : months) t.addCell(rit(fmtN(sd.receptionFee710.getOrDefault(String.valueOf(m), 0)), normal));
        t.addCell(rit(fmtN(sd.receptionFee710Total), bold));

        // 紹介手数料（ラベルを rowspan=2 で結合）
        PdfPCell introLabel = new PdfPCell(new Phrase("紹介手数料", bold));
        introLabel.setRowspan(2);
        introLabel.setBorder(Rectangle.BOX);
        introLabel.setPadding(2);
        introLabel.setHorizontalAlignment(Element.ALIGN_CENTER);
        introLabel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(introLabel);
        // 1行目（※1累計＋※2累計−サンケアネット）
        for (int m : months) t.addCell(rit(fmtN(sd.introFee.getOrDefault(String.valueOf(m), 0)), normal));
        t.addCell(rit(fmtN(sd.introFeeTotal), bold));
        // 2行目（求人受付事務費 1,000円×件数）※ラベルセルは rowspan で結合済みのため追加不要
        for (int m : months) t.addCell(rit(fmtN(sd.receptionFee1000.getOrDefault(String.valueOf(m), 0)), normal));
        t.addCell(rit(fmtN(sd.receptionFee1000Total), bold));

        t.addCell(hdrN("サン・ケアネット", bold));
        for (int m : months) t.addCell(rit(fmtN(sd.sancareNet.getOrDefault(String.valueOf(m), 0)), normal));
        t.addCell(rit(fmtN(sd.sancareNetTotal), bold));

        // 月別合計行（太枠）
        PdfPCell totalLabel = new PdfPCell(new Phrase("月別合計", bold));
        totalLabel.setBorder(Rectangle.BOX); totalLabel.setBorderWidth(1.5f); totalLabel.setPadding(3);
        totalLabel.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(totalLabel);
        for (int m : months) {
            PdfPCell tc = rit(fmtN(sd.monthTotal.getOrDefault(String.valueOf(m), 0)), bold);
            tc.setBorderWidth(1.5f); t.addCell(tc);
        }
        PdfPCell gtc = rit(fmtN(sd.grandTotal), bold);
        gtc.setBorderWidth(1.5f); t.addCell(gtc);

        return t;
    }

    private PdfPTable buildReportTable(ReportData rd, List<Integer> months, Font bold, Font normal) {
        float[] w = new float[months.size() + 3];
        w[0] = 1.0f; w[1] = 2.5f;
        for (int i = 2; i <= months.size() + 1; i++) w[i] = 1.1f;
        w[months.size() + 2] = 1.4f;

        PdfPTable t = new PdfPTable(w);
        t.setWidthPercentage(100); t.setSpacingBefore(3);

        PdfPCell corner = new PdfPCell(new Phrase("項目 月別", bold));
        corner.setColspan(2); corner.setBorder(Rectangle.BOX); corner.setPadding(2);
        corner.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(corner);
        for (int m : months) t.addCell(hdrN(m + "月", bold));
        t.addCell(hdrN("決算額", bold));

        // row = { グループラベル(先頭行のみ), 小ラベル, データmap(未実装ならnull), 合計値, 金額表示か(true=カンマ区切り/false=単純な件数) }
        Object[][] rows = {
            {"求\n人", "常用",       null,           0,                       false},
            {null,     "臨時",       rd.jobTemp,     rd.jobTempTotal,         false},
            {null,     "日雇",       null,           0,                       false},
            {"求\n職", "有効",       rd.seekerValid, rd.seekerValidTotal,     false},
            {null,     "新規申込み", null,           0,                       false},
            {"就\n職", "常用",       null,           0,                       false},
            {null,     "臨時",       null,           0,                       false},
            {null,     "日雇",       null,           0,                       false},
            {"手\n数\n料", "常用",   rd.feeRegular,  rd.feeRegularTotal,      true},
            {null,     "臨時",       rd.feeTemp,     rd.feeTempTotal,         true},
            {null,     "日雇",       rd.feeDaily,    rd.feeDailyTotal,        true},
            {null,     "求職受付料", rd.feeReception,rd.feeReceptionTotal,    true},
        };

        int[] groupSpans = {3, 2, 3, 4};
        int groupIdx = 0, inGroup = 0, spanLeft = groupSpans[0];

        for (Object[] row : rows) {
            String groupLabel = (String) row[0];
            String subLabel   = (String) row[1];
            @SuppressWarnings("unchecked")
            Map<String,Integer> data = (Map<String,Integer>) row[2];
            int total      = (Integer) row[3];
            boolean isMoney = (Boolean) row[4];

            if (groupLabel != null) {
                PdfPCell gc = new PdfPCell(new Phrase(groupLabel, bold));
                gc.setRowspan(spanLeft); gc.setBorder(Rectangle.BOX); gc.setPadding(2);
                gc.setHorizontalAlignment(Element.ALIGN_CENTER); gc.setVerticalAlignment(Element.ALIGN_MIDDLE);
                t.addCell(gc);
            }
            PdfPCell sc = new PdfPCell(new Phrase(subLabel, normal));
            sc.setBorder(Rectangle.BOX); sc.setPadding(2);
            t.addCell(sc);

            for (int m : months) {
                String text;
                if (data == null) {
                    text = "";
                } else {
                    int v = data.getOrDefault(String.valueOf(m), 0);
                    text = isMoney ? fmt(v) : String.valueOf(v);
                }
                PdfPCell dc = new PdfPCell(new Phrase(text, normal));
                dc.setBorder(Rectangle.BOX); dc.setPadding(2);
                dc.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(dc);
            }
            String totalText = data == null ? "0" : (isMoney ? fmt(total) : String.valueOf(total));
            PdfPCell tc = new PdfPCell(new Phrase(totalText, bold));
            tc.setBorder(Rectangle.BOX); tc.setPadding(2);
            tc.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(tc);

            inGroup++;
            if (inGroup >= spanLeft && groupIdx + 1 < groupSpans.length) {
                groupIdx++; inGroup = 0; spanLeft = groupSpans[groupIdx];
            }
        }
        return t;
    }

    // ═══════════════════════════════════════════════════════════════
    // セルヘルパー
    // ═══════════════════════════════════════════════════════════════

    private PdfPCell diag(Font f) {
        PdfPCell c = new PdfPCell(new Phrase("", f));
        c.setBorder(Rectangle.BOX); c.setFixedHeight(18f);
        c.setCellEvent((cell, position, canvases) -> {
            PdfContentByte cb = canvases[PdfPTable.LINECANVAS];
            cb.saveState(); cb.setLineWidth(0.5f);
            cb.moveTo(position.getLeft(), position.getBottom());
            cb.lineTo(position.getRight(), position.getTop());
            cb.stroke(); cb.restoreState();
        });
        return c;
    }

    private PdfPCell cen(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX); c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(2); c.setFixedHeight(18f);
        return c;
    }

    private PdfPCell rit(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX); c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(2); c.setFixedHeight(18f);
        return c;
    }

    private PdfPCell hdr(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.BOX); c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(2); c.setFixedHeight(22f);
        return c;
    }

    private PdfPCell hdrN(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.BOX); c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(2);
        return c;
    }

    private PdfPCell spanCell(String text, Font f, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setColspan(colspan); c.setBorder(Rectangle.BOX);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(2); c.setFixedHeight(20f);
        return c;
    }

    private String fmt(int val)  { return String.format("%,d", val); }
    private String fmtN(int val) { return val == 0 ? "0" : String.format("%,d", val); }

    // ═══════════════════════════════════════════════════════════════
    // DTO
    // ═══════════════════════════════════════════════════════════════

    public static class FeeLedgerRow {
        public Long   detailId;
        public String receiptDate, customerName, receiptNo;
        public int    wage, commission, tax, customerFee, dailyWage1Month, temp3Month;
        public String wageStr, commissionStr, taxStr, customerFeeStr, dailyWage1MonthStr, temp3MonthStr;

        public Long   getDetailId()             { return detailId; }
        public String getReceiptDate()          { return receiptDate; }
        public String getCustomerName()         { return customerName; }
        public int    getWage()                 { return wage; }
        public int    getCommission()           { return commission; }
        public int    getTax()                  { return tax; }
        public int    getCustomerFee()          { return customerFee; }
        public String getReceiptNo()            { return receiptNo; }
        public int    getDailyWage1Month()      { return dailyWage1Month; }
        public int    getTemp3Month()           { return temp3Month; }
        public String getWageStr()              { return wageStr; }
        public String getCommissionStr()        { return commissionStr; }
        public String getTaxStr()               { return taxStr; }
        public String getCustomerFeeStr()       { return customerFeeStr; }
        public String getDailyWage1MonthStr()   { return dailyWage1MonthStr; }
        public String getTemp3MonthStr()        { return temp3MonthStr; }
    }

    public static class ReceptionLedgerRow {
        public String receiptDate, customerName, receiptNo, receptionFeeStr;
        public int    receptionFee;

        public String getReceiptDate()      { return receiptDate; }
        public String getCustomerName()     { return customerName; }
        public String getReceiptNo()        { return receiptNo; }
        public int    getReceptionFee()     { return receptionFee; }
        public String getReceptionFeeStr()  { return receptionFeeStr; }
    }

    public static class SettlementData {
        public Map<String,Integer> receptionFee710  = new LinkedHashMap<>();
        public Map<String,Integer> receptionFee1000 = new LinkedHashMap<>();
        public Map<String,Integer> introFee         = new LinkedHashMap<>();
        public Map<String,Integer> sancareNet       = new LinkedHashMap<>();
        public Map<String,Integer> monthTotal       = new LinkedHashMap<>();
        public int receptionFee710Total, receptionFee1000Total;
        public int introFeeTotal, sancareNetTotal, grandTotal;

        public Map<String,Integer> getReceptionFee710()   { return receptionFee710; }
        public Map<String,Integer> getReceptionFee1000()  { return receptionFee1000; }
        public Map<String,Integer> getIntroFee()          { return introFee; }
        public Map<String,Integer> getSancareNet()        { return sancareNet; }
        public Map<String,Integer> getMonthTotal()        { return monthTotal; }
        public int getReceptionFee710Total()  { return receptionFee710Total; }
        public int getReceptionFee1000Total() { return receptionFee1000Total; }
        public int getIntroFeeTotal()         { return introFeeTotal; }
        public int getSancareNetTotal()       { return sancareNetTotal; }
        public int getGrandTotal()            { return grandTotal; }
    }

    public static class ReportData {
        public Map<String,Integer> jobTemp       = new LinkedHashMap<>(); // 求人-臨時(件数)
        public Map<String,Integer> seekerValid   = new LinkedHashMap<>(); // 求職-有効(件数)
        public Map<String,Integer> feeRegular    = new LinkedHashMap<>(); // 手数料-常用
        public Map<String,Integer> feeTemp       = new LinkedHashMap<>(); // 手数料-臨時
        public Map<String,Integer> feeDaily      = new LinkedHashMap<>(); // 手数料-日雇
        public Map<String,Integer> feeReception  = new LinkedHashMap<>(); // 手数料-求職受付手数料
        public int jobTempTotal, seekerValidTotal, feeRegularTotal, feeTempTotal, feeDailyTotal, feeReceptionTotal;

        public Map<String,Integer> getJobTemp()      { return jobTemp; }
        public Map<String,Integer> getSeekerValid()  { return seekerValid; }
        public Map<String,Integer> getFeeRegular()   { return feeRegular; }
        public Map<String,Integer> getFeeTemp()      { return feeTemp; }
        public Map<String,Integer> getFeeDaily()     { return feeDaily; }
        public Map<String,Integer> getFeeReception() { return feeReception; }
        public int getJobTempTotal()      { return jobTempTotal; }
        public int getSeekerValidTotal()  { return seekerValidTotal; }
        public int getFeeRegularTotal()   { return feeRegularTotal; }
        public int getFeeTempTotal()      { return feeTempTotal; }
        public int getFeeDailyTotal()     { return feeDailyTotal; }
        public int getFeeReceptionTotal() { return feeReceptionTotal; }
    }
}
