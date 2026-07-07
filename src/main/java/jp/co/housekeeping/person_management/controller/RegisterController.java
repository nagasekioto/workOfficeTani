package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.RegisterRecord;
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.RegisterRecordRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/register")
public class RegisterController {

    @Autowired private PersonRepository personRepository;
    @Autowired private RegisterRecordRepository registerRecordRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private CustomerRepository customerRepository;

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
            @RequestParam(required = false, defaultValue = "0") Integer membershipFee,
            @RequestParam(required = false) String memo,
            HttpSession session,
            HttpServletResponse response) {
        if (!checkAuth(session)) { response.setStatus(401); return "UNAUTHORIZED"; }

        RegisterRecord record = new RegisterRecord();
        record.setPersonId(personId);
        record.setWorkMonth(workMonth);
        record.setSalary(salary);
        record.setFee(fee);
        record.setMembershipFee(membershipFee);
        record.setTransferred(false);
        record.setMemo(memo);
        record.setCreatedAt(LocalDateTime.now());
        registerRecordRepository.save(record);
        return "OK";
    }

    // ─── 振込済みチェックの切り替え ─────────────────────
    @PostMapping("/calc/toggle-transferred")
    @ResponseBody
    public String toggleTransferred(@RequestParam Long id,
                                     @RequestParam boolean transferred,
                                     HttpSession session,
                                     HttpServletResponse response) {
        if (!checkAuth(session)) { response.setStatus(401); return "UNAUTHORIZED"; }
        registerRecordRepository.findById(id).ifPresent(r -> {
            r.setTransferred(transferred);
            registerRecordRepository.save(r);
        });
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

    // ─── 修正（編集画面）───────────────────────────────
    @GetMapping("/calc/edit")
    public String editForm(@RequestParam Long id, @RequestParam(required = false) String month,
                           HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        RegisterRecord record = registerRecordRepository.findById(id).orElse(null);
        if (record == null) return "redirect:/register/list";

        String personName = "不明";
        if (record.getPersonId() != null) {
            Person p = personRepository.findById(record.getPersonId()).orElse(null);
            if (p != null) personName = p.getLastNameKanji() + " " + p.getFirstNameKanji();
        }

        model.addAttribute("record", record);
        model.addAttribute("personName", personName);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("persons", personRepository.findAll());
        return "register-edit";
    }

    @PostMapping("/calc/edit")
    public String editSave(@RequestParam Long id,
                           @RequestParam Integer salary,
                           @RequestParam Integer fee,
                           @RequestParam(required = false) String memo,
                           @RequestParam(required = false) String month,
                           HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        registerRecordRepository.findById(id).ifPresent(r -> {
            r.setSalary(salary);
            r.setFee(fee);
            r.setMemo(memo);
            registerRecordRepository.save(r);
        });
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

        // monthパラメータがない場合は現在月にリダイレクト
        if (month == null || month.isBlank()) {
            String currentMonth = LocalDateTime.now().getYear() + "-"
                + String.format("%02d", LocalDateTime.now().getMonthValue());
            return "redirect:/register/list?month=" + currentMonth;
        }

        // 求職者名マップ
        Map<Long, String> personMap = new HashMap<>();
        StreamSupport.stream(personRepository.findAll().spliterator(), false).forEach(p ->
            personMap.put(p.getId(), p.getLastNameKanji() + " " + p.getFirstNameKanji()));

        List<RegisterRecord> raw = registerRecordRepository.findByWorkMonth(month);
        List<RegisterRow> records = new ArrayList<>();
        long totalSalary = 0, totalFee = 0;
        long totalTransferred = 0, totalUnpaid = 0;
        for (RegisterRecord r : raw) {
            RegisterRow row = new RegisterRow();
            row.id = r.getId();
            row.personId = r.getPersonId();
            row.personName = personMap.getOrDefault(r.getPersonId(), "不明");
            row.workMonth = r.getWorkMonth();
            row.salary = r.getSalary() != null ? r.getSalary() : 0;
            row.fee = r.getFee() != null ? r.getFee() : 0;
            row.salaryStr = fmtYen(row.salary);
            row.feeStr    = fmtYen(row.fee);
            row.membershipFee = r.getMembershipFee() != null ? r.getMembershipFee() : 0;
            row.membershipFeeStr = row.membershipFee > 0 ? fmtYen(row.membershipFee) : "-";
            row.transferred = r.getTransferred() != null && r.getTransferred();
            row.memo = r.getMemo();
            row.createdAt = r.getCreatedAt();

            // 稼働台帳との照合
            MatchResult match = checkMatch(r.getPersonId(), month, row.salary, row.fee);
            row.matchStatus = match.status;
            row.matchDetail = match.detail;
            row.ledgerSalary = match.ledgerSalary;
            row.ledgerFee = match.ledgerFee;
            row.ledgerSalaryStr = match.ledgerSalary >= 0 ? fmtYen(match.ledgerSalary) : "-";
            row.ledgerFeeStr = match.ledgerFee >= 0 ? fmtYen(match.ledgerFee) : "-";

            records.add(row);
            totalSalary += row.salary;
            totalFee += row.fee;
            if (row.transferred) totalTransferred += row.salary;
            else totalUnpaid += row.salary;
        }
        model.addAttribute("records", records);
        model.addAttribute("totalSalary", totalSalary);
        model.addAttribute("totalFee", totalFee);
        model.addAttribute("totalSalaryStr", fmtYen(totalSalary));
        model.addAttribute("totalFeeStr",    fmtYen(totalFee));
        model.addAttribute("totalTransferredStr", fmtYen(totalTransferred));
        model.addAttribute("totalUnpaidStr",      fmtYen(totalUnpaid));
        model.addAttribute("selectedMonth", month);

        return "register-list";
    }

    /** 
     * 照合ロジック：振込金入力（1-8-1）の同一人物・同一月の register_records 合計と比較
     * レジ一覧の1行（register_records の1レコード）に対して、
     * 同じ person_id・同じ work_month の register_records 全件の salary 合計を「振込金合計」とし、
     * その合計が自分自身の salary と一致するかを確認する。
     * ※同一人物・同月に複数レコードがある場合は合計で判定
     */
    private MatchResult checkMatch(Long personId, String workMonth, long thisSalary, long thisFee) {
        MatchResult result = new MatchResult();
        result.ledgerSalary = -1;
        result.ledgerFee    = -1;
        result.status = "unknown";
        result.detail = "データなし";

        if (personId == null) return result;

        // 同一人物・同月の全振込レコードを取得して合計
        List<RegisterRecord> allRecords = registerRecordRepository.findByWorkMonth(workMonth);
        long totalSalary = 0;
        long totalFee    = 0;
        int  count       = 0;
        for (RegisterRecord r : allRecords) {
            if (personId.equals(r.getPersonId())) {
                totalSalary += r.getSalary() != null ? r.getSalary() : 0;
                totalFee    += r.getFee()    != null ? r.getFee()    : 0;
                count++;
            }
        }

        if (count == 0) {
            result.status = "unknown";
            result.detail = "振込記録なし";
            return result;
        }

        // 稼働台帳（sales_details）の賃金総額も取得（参考表示用）
        long ledgerTotal = 0;
        try {
            YearMonth ym = YearMonth.parse(workMonth);
            List<Sales> salesList = salesRepository.findByPersonId(personId);
            for (Sales s : salesList) {
                for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                    // 就労終了日→就労開始日→紹介日 の優先順で月判定
                    LocalDate refDate = null;
                    if      (d.getWorkEndDate()      != null) refDate = d.getWorkEndDate();
                    else if (d.getWorkStartDate()    != null) refDate = d.getWorkStartDate();
                    else if (d.getIntroductionDate() != null) refDate = d.getIntroductionDate();
                    if (refDate == null) continue;
                    if (!YearMonth.from(refDate).equals(ym)) continue;
                    ledgerTotal += d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                }
            }
        } catch (Exception ignored) {}

        result.ledgerSalary = ledgerTotal > 0 ? ledgerTotal : totalSalary;
        result.ledgerFee    = (long)(result.ledgerSalary * 0.165);

        // 判定：振込合計 = 稼働台帳合計 なら一致
        // 稼働台帳データがない場合は振込レコードが1件のみなら「確認不可」
        if (ledgerTotal == 0) {
            result.status = "unknown";
            result.detail = "稼働台帳に該当月データなし（振込合計: " + fmtYen(totalSalary) + "）";
            return result;
        }

        boolean salaryMatch = (totalSalary == ledgerTotal);
        long    ledgerFee15 = (long)(ledgerTotal * 0.165);
        boolean feeMatch    = Math.abs(totalFee - ledgerFee15) <= 1;

        result.ledgerSalary = ledgerTotal;
        result.ledgerFee    = ledgerFee15;

        if (salaryMatch && feeMatch) {
            result.status = "ok";
            result.detail = "一致（振込合計: " + fmtYen(totalSalary) + "）";
        } else {
            result.status = "ng";
            StringBuilder sb = new StringBuilder("不一致：");
            if (!salaryMatch) sb.append("振込合計=").append(fmtYen(totalSalary))
                                .append(" 台帳=").append(fmtYen(ledgerTotal)).append(" ");
            if (!feeMatch) sb.append("手数料=").append(fmtYen(totalFee))
                             .append(" 台帳手数料=").append(fmtYen(ledgerFee15));
            result.detail = sb.toString().trim();
        }
        return result;
    }

    static class MatchResult {
        String status;  // "ok", "ng", "unknown"
        String detail;
        long ledgerSalary;
        long ledgerFee;
    }

    // ─── 1-8-3 手数料管理簿 ─────────────────────────────
    @GetMapping("/fee-ledger")
    public String feeLedger(@RequestParam(required = false) String month,
                            HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";

        // monthパラメータがない場合は現在月にリダイレクト
        if (month == null || month.isBlank()) {
            String currentMonth = LocalDateTime.now().getYear() + "-"
                + String.format("%02d", LocalDateTime.now().getMonthValue());
            return "redirect:/register/fee-ledger?month=" + currentMonth;
        }

        // 求職者名マップ
        Map<Long, String> personMap = new HashMap<>();
        StreamSupport.stream(personRepository.findAll().spliterator(), false).forEach(p ->
            personMap.put(p.getId(), p.getLastNameKanji() + " " + p.getFirstNameKanji()));

        List<RegisterRecord> raw = registerRecordRepository.findByWorkMonth(month);
        List<FeeLedgerRow> rows = new ArrayList<>();
        long totalSalary = 0, totalFee = 0;

        for (RegisterRecord r : raw) {
            FeeLedgerRow row = new FeeLedgerRow();
            row.id         = r.getId();
            row.personName = personMap.getOrDefault(r.getPersonId(), "不明");
            row.workMonth  = r.getWorkMonth();
            row.salary     = r.getSalary() != null ? r.getSalary() : 0;
            row.fee        = r.getFee()    != null ? r.getFee()    : 0;
            row.salaryStr  = fmtYen(row.salary);
            row.feeStr     = fmtYen(row.fee);
            row.memo       = r.getMemo();
            row.createdAt  = r.getCreatedAt();
            rows.add(row);
            totalSalary += row.salary;
            totalFee    += row.fee;
        }

        model.addAttribute("rows",           rows);
        model.addAttribute("totalSalaryStr", fmtYen(totalSalary));
        model.addAttribute("totalFeeStr",    fmtYen(totalFee));
        model.addAttribute("totalCount",     rows.size());
        model.addAttribute("selectedMonth",  month);

        return "register-fee-ledger";
    }

    private String fmtYen(long v) {
        return "\u00a5" + java.text.NumberFormat.getNumberInstance(java.util.Locale.JAPAN).format(v);
    }

    // ─── 1-8-3 手数料管理簿 PDF出力 ─────────────────────
    @GetMapping("/fee-ledger/pdf")
    public void feeLedgerPdf(@RequestParam String month,
                              HttpSession session,
                              HttpServletResponse response) throws IOException, DocumentException {
        if (!checkAuth(session)) { response.sendError(401); return; }

        List<RegisterRecord> raw = registerRecordRepository.findByWorkMonth(month);

        // 求人者名マップを構築：customer_id → 氏名
        Map<Long, String> customerMap = new HashMap<>();
        StreamSupport.stream(customerRepository.findAll().spliterator(), false).forEach(c ->
            customerMap.put(c.getId(), c.getLastNameKanji() + " " + c.getFirstNameKanji()));

        // person_id → 求人者名リスト（求職者1人が複数の求人者で働いた場合に対応）
        Map<Long, List<String>> personToCustomerNames = buildPersonToCustomerListMap(month, customerMap);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createFeeLedgerPdf(month, raw, personToCustomerNames, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=fee-ledger-" + month + ".pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    /**
     * person_id をキーに、その求職者が該当月に担当した求人者名リストを返すマップ。
     * 求人者ごとに別行で出力するためリストのまま保持する。
     */
    private Map<Long, List<String>> buildPersonToCustomerListMap(String workMonth, Map<Long, String> customerMap) {
        java.time.YearMonth ym;
        try { ym = java.time.YearMonth.parse(workMonth); }
        catch (Exception e) { return new HashMap<>(); }

        // person_id → 求人者名リスト（順序保持・重複除去）
        Map<Long, java.util.LinkedHashSet<String>> result = new HashMap<>();

        Iterable<Sales> allSales = salesRepository.findAll();
        for (Sales s : allSales) {
            List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
            for (SalesDetail d : details) {
                LocalDate endDate = d.getWorkEndDate() != null ? d.getWorkEndDate() : d.getWorkStartDate();
                if (endDate == null) continue;
                if (!java.time.YearMonth.from(endDate).equals(ym)) continue;

                Long personId = s.getPersonId();
                String customerName = d.getCustomerId() != null
                    ? customerMap.getOrDefault(d.getCustomerId(), "不明")
                    : "不明";
                result.computeIfAbsent(personId, k -> new java.util.LinkedHashSet<>()).add(customerName);
            }
        }

        Map<Long, List<String>> out = new HashMap<>();
        result.forEach((pid, names) -> out.put(pid, new ArrayList<>(names)));
        return out;
    }

    private void createFeeLedgerPdf(String month, List<RegisterRecord> records,
                                     Map<Long, List<String>> payerListMap,
                                     ByteArrayOutputStream baos) throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont  = new Font(bf, 13, Font.BOLD);
        Font boldFont   = new Font(bf, 8, Font.BOLD);
        Font normalFont = new Font(bf, 8);
        Font smallFont  = new Font(bf, 7);

        // タイトル
        Paragraph title = new Paragraph("手数料管理簿　[届出制手数料用]　" + month, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8);
        doc.add(title);

        // テーブル列：領収年月日 | 支払者名 | 賃金 | 手数料※1(届出手数料) | 手数料※2 | 求人受付事務費 | 手数料割合 | 備考 | 日雇1ヶ月 | 臨時3ヶ月
        float[] widths = {2.5f, 3f, 2f, 2f, 2f, 2f, 1.5f, 2f, 1.5f, 1.5f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        String[] headers = {"領収\n年月日", "支払者名", "賃金", "手数料※1\n届出手数料", "手数料※2", "求人受付\n事務費", "手数料\n割合", "備考", "日雇\n1ヶ月", "臨時\n3ヶ月"};
        BaseColor headerColor = new BaseColor(200, 200, 200);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, boldFont));
            c.setBackgroundColor(headerColor);
            c.setPadding(3);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(c);
        }

        // データ行：求職者1人が複数の求人者で働いた場合は求人者ごとに行を分ける
        long pageSalary = 0, pageFee = 0;

        for (RegisterRecord r : records) {
            String wm = r.getWorkMonth(); // "2025-03"
            String[] parts = wm.split("-");
            String displayDate = parts[0] + "/" + parts[1];

            List<String> customerNames = payerListMap.getOrDefault(r.getPersonId(), new ArrayList<>());
            if (customerNames.isEmpty()) customerNames = List.of("");

            long recSalary = r.getSalary() != null ? r.getSalary() : 0;
            long recFee    = r.getFee()    != null ? r.getFee()    : 0;
            int  nameCount = customerNames.size();

            // 求人者が複数の場合、給料・手数料を均等分割して各行に出力
            for (int ni = 0; ni < nameCount; ni++) {
                long rowSalary = (ni == nameCount - 1)
                    ? recSalary - (recSalary / nameCount) * ni   // 端数は最終行
                    : recSalary / nameCount;
                long rowFee = (ni == nameCount - 1)
                    ? recFee - (recFee / nameCount) * ni
                    : recFee / nameCount;

                addLedgerCell(table, ni == 0 ? displayDate : "", normalFont, Element.ALIGN_CENTER);
                addLedgerCell(table, customerNames.get(ni), normalFont, Element.ALIGN_LEFT);
                addLedgerCell(table, String.format("%,d", rowSalary), normalFont, Element.ALIGN_RIGHT);
                addLedgerCell(table, String.format("%,d", rowFee), boldFont, Element.ALIGN_RIGHT);
                addLedgerCell(table, "", normalFont, Element.ALIGN_RIGHT); // ※2
                addLedgerCell(table, "", normalFont, Element.ALIGN_RIGHT); // 求人受付
                addLedgerCell(table, "16.5%", normalFont, Element.ALIGN_CENTER);
                addLedgerCell(table, ni == 0 ? (r.getMemo() != null ? r.getMemo() : "") : "", normalFont, Element.ALIGN_LEFT);
                addLedgerCell(table, "0", normalFont, Element.ALIGN_RIGHT);
                addLedgerCell(table, "0", normalFont, Element.ALIGN_RIGHT);
            }

            pageSalary += recSalary;
            pageFee    += recFee;
        }

        // ページ計
        BaseColor totalColor = new BaseColor(240, 240, 200);
        addLedgerTotalRow(table, "ページ計", pageSalary, pageFee, boldFont, totalColor);
        // 累計
        addLedgerTotalRow(table, month + "分累計", pageSalary, pageFee, boldFont, totalColor);

        doc.add(table);

        // 注釈
        doc.add(new Paragraph(" ", smallFont));
        doc.add(new Paragraph("※1は、徴収した届け出制手数料の総額から第二種特別加入料に充てるべき手数料額を除いた額を記載する。", smallFont));
        doc.add(new Paragraph("※2は、第二種特別加入保険料に充てるべき手数料。", smallFont));

        doc.close();
    }

    private void addLedgerCell(PdfPTable t, String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(3);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(c);
    }

    private void addLedgerTotalRow(PdfPTable t, String label, long salary, long fee, Font f, BaseColor bg) {
        // 1列目：ラベル（領収年月日列）
        PdfPCell lc = new PdfPCell(new Phrase(label, f));
        lc.setColspan(2);
        lc.setBackgroundColor(bg);
        lc.setPadding(3);
        lc.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(lc);
        addColorCell(t, String.format("%,d", salary), f, bg, Element.ALIGN_RIGHT);
        addColorCell(t, String.format("%,d", fee), f, bg, Element.ALIGN_RIGHT);
        for (int i = 0; i < 4; i++) addColorCell(t, "0", f, bg, Element.ALIGN_RIGHT);
        addColorCell(t, "0", f, bg, Element.ALIGN_RIGHT);
        addColorCell(t, "0", f, bg, Element.ALIGN_RIGHT);
    }

    private void addColorCell(PdfPTable t, String text, Font f, BaseColor bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(bg);
        c.setPadding(3);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    // ─── 内部クラス ────────────────────────────────────
    public static class RegisterRow {
        public Long id;
        public Long personId;
        public String personName;
        public String workMonth;
        public long salary;
        public long fee;
        public String salaryStr;
        public String feeStr;
        public long membershipFee;
        public String membershipFeeStr;
        public boolean transferred;
        public String memo;
        public LocalDateTime createdAt;
        // 照合フィールド
        public String matchStatus;   // "ok", "ng", "unknown"
        public String matchDetail;
        public long ledgerSalary;
        public long ledgerFee;
        public String ledgerSalaryStr;
        public String ledgerFeeStr;
    }

    public static class MonthlyRow {
        public String  monthLabel;
        public long    count;
        public String  salaryStr;
        public String  feeStr;
        public boolean isTotal;
    }

    public static class FeeLedgerRow {
        public Long          id;
        public String        personName;
        public String        workMonth;
        public long          salary;
        public long          fee;
        public String        salaryStr;
        public String        feeStr;
        public String        memo;
        public LocalDateTime createdAt;
    }
}
