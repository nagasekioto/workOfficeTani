package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
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

    // ─── 1-8-3 手数料管理簿 PDF出力 ─────────────────────
    @GetMapping("/fee-ledger/pdf")
    public void feeLedgerPdf(@RequestParam Integer year,
                              HttpSession session,
                              HttpServletResponse response) throws IOException, DocumentException {
        if (!checkAuth(session)) { response.sendError(401); return; }

        List<RegisterRecord> raw = registerRecordRepository.findByYear(year + "-%");

        // 求職者名マップ
        Map<Long, String> personMap = new HashMap<>();
        StreamSupport.stream(personRepository.findAll().spliterator(), false).forEach(p ->
            personMap.put(p.getId(), p.getLastNameKanji() + " " + p.getFirstNameKanji()));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createFeeLedgerPdf(year, raw, personMap, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=fee-ledger-" + year + ".pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    private void createFeeLedgerPdf(int year, List<RegisterRecord> records,
                                     Map<Long, String> personMap,
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
        Paragraph title = new Paragraph("手数料管理簿　[届出制手数料用]　" + year + "年", titleFont);
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

        // データ行（月ごとにグループ）
        String currentMonth = "";
        long pageSalary = 0, pageFee = 0;

        for (RegisterRecord r : records) {
            String month = r.getWorkMonth(); // "2025-03"
            String[] parts = month.split("-");
            String displayDate = parts[0] + "/" + parts[1];

            addLedgerCell(table, displayDate, normalFont, Element.ALIGN_CENTER);
            addLedgerCell(table, personMap.getOrDefault(r.getPersonId(), ""), normalFont, Element.ALIGN_LEFT);
            addLedgerCell(table, r.getSalary() != null ? String.format("%,d", r.getSalary()) : "0", normalFont, Element.ALIGN_RIGHT);
            addLedgerCell(table, r.getFee() != null ? String.format("%,d", r.getFee()) : "0", boldFont, Element.ALIGN_RIGHT);
            addLedgerCell(table, "", normalFont, Element.ALIGN_RIGHT); // ※2
            addLedgerCell(table, "", normalFont, Element.ALIGN_RIGHT); // 求人受付
            addLedgerCell(table, "15%", normalFont, Element.ALIGN_CENTER);
            addLedgerCell(table, r.getMemo() != null ? r.getMemo() : "", normalFont, Element.ALIGN_LEFT);
            addLedgerCell(table, "0", normalFont, Element.ALIGN_RIGHT);
            addLedgerCell(table, "0", normalFont, Element.ALIGN_RIGHT);

            pageSalary += r.getSalary() != null ? r.getSalary() : 0;
            pageFee += r.getFee() != null ? r.getFee() : 0;
        }

        // ページ計
        BaseColor totalColor = new BaseColor(240, 240, 200);
        addLedgerTotalRow(table, "ページ計", pageSalary, pageFee, boldFont, totalColor);
        // 累計
        addLedgerTotalRow(table, year + "年分累計", pageSalary, pageFee, boldFont, totalColor);

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
// NOTE: this append won't work cleanly - we'll rewrite the file
