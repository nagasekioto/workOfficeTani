package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/report-menu")
public class ReportMenuController {

    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private SalesRepository       salesRepository;
    @Autowired private CustomerRepository    customerRepository;

    private static final double FEE_RATE = 0.15;
    private static final int    ROWS_PER_PAGE = 30;

    // ─── 一覧表示 ──────────────────────────────────────────────
    @GetMapping("")
    public String reportMenu(@RequestParam(required = false) String month,
                             HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        model.addAttribute("selectedMonth", month);

        if (month == null || month.isBlank()) {
            model.addAttribute("items", new ArrayList<>());
            return "report-menu";
        }

        YearMonth ym = YearMonth.parse(month);
        List<FeeLedgerRow> items = buildRows(ym);
        model.addAttribute("items", items);
        return "report-menu";
    }

    // ─── PDF出力 ───────────────────────────────────────────────
    @GetMapping("/pdf")
    public void reportMenuPdf(@RequestParam(required = false) String month,
                               HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        YearMonth ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();
        List<FeeLedgerRow> items = buildRows(ym);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createFeeLedgerPdf(items, ym, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=fee-ledger.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ─── データ構築 ────────────────────────────────────────────
    private List<FeeLedgerRow> buildRows(YearMonth ym) {
        LocalDate monthEnd = ym.atEndOfMonth();
        List<FeeLedgerRow> items = new ArrayList<>();

        for (Sales s : salesRepository.findAll()) {
            List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
            for (SalesDetail d : details) {
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;

                // 領収日（issuedAt優先 → workEndDate → workStartDate）
                LocalDate receiptDate = null;
                if (d.getIssuedAt() != null) receiptDate = d.getIssuedAt().toLocalDate();
                else if (d.getWorkEndDate() != null) receiptDate = d.getWorkEndDate();
                else if (d.getWorkStartDate() != null) receiptDate = d.getWorkStartDate();
                if (receiptDate == null) continue;

                // 月フィルタ：同年同月のみ
                if (receiptDate.getYear() != ym.getYear() ||
                    receiptDate.getMonthValue() != ym.getMonthValue()) continue;

                Customer customer = d.getCustomerId() != null
                    ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                String customerName = customer != null
                    ? customer.getLastNameKanji() + "　" + customer.getFirstNameKanji() : "";

                int totalWage   = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                int customerFee = d.getCustomerFee()  != null ? d.getCustomerFee()  : 0;
                int commission  = (int)(totalWage * FEE_RATE);
                int consTax     = (int)(commission * 0.10);

                FeeLedgerRow row = new FeeLedgerRow();
                // 領収年月日は末日
                row.receiptDate    = String.format("%d/%d/%d",
                    monthEnd.getYear(), monthEnd.getMonthValue(), monthEnd.getDayOfMonth());
                row.customerName   = customerName;
                row.commission     = commission;   // 手数料※1 届出手数料 ③
                row.tax            = consTax;       // 手数料※2 ④
                row.customerFee    = customerFee;  // 求人受付事務費 ②
                row.feeRate        = "15%";
                row.receiptNo      = d.getReceiptNo();  // 備考
                row.dailyWage      = totalWage;     // 日雇1ヶ月 ①
                row.commissionStr  = commission > 0  ? String.format("%,d", commission)  : "0";
                row.taxStr         = consTax > 0     ? String.format("%,d", consTax)     : "0";
                row.customerFeeStr = customerFee > 0 ? String.format("%,d", customerFee) : "";
                row.dailyWageStr   = totalWage > 0   ? String.format("%,d", totalWage)   : "0";
                items.add(row);
            }
        }

        // 領収番号順ソート
        items.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.receiptNo), Integer.parseInt(b.receiptNo)); }
            catch (NumberFormatException e) { return a.receiptNo.compareTo(b.receiptNo); }
        });
        return items;
    }

    // ─── PDF生成（実物フォーマット完全再現） ──────────────────
    private void createFeeLedgerPdf(List<FeeLedgerRow> items, YearMonth ym,
                                     ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        // A4縦
        Document doc = new Document(PageSize.A4, 28, 28, 28, 28);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont  = new Font(bf, 11, Font.BOLD);
        Font boldFont   = new Font(bf, 8,  Font.BOLD);
        Font normalFont = new Font(bf, 8);
        Font smallFont  = new Font(bf, 7);
        Font noteFont   = new Font(bf, 7);

        // ── タイトル ──
        Paragraph title = new Paragraph("手数料管理簿〔届出制手数料用〕", titleFont);
        title.setSpacingAfter(4);
        doc.add(title);

        // 列幅: 領収年月日 | 支払者名 | 手数料※1 | 手数料※2 | 求人受付事務費 | 手数料割合 | 備考 | 日雇1ヶ月 | 臨時3ヶ月
        float[] widths = {2.2f, 2.8f, 1.6f, 1.4f, 1.6f, 1.2f, 1.2f, 1.6f, 1.4f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(2);

        // ── ヘッダー行1 ──
        addHdr(table, "領収",       boldFont, 1, 1);
        addHdr(table, "",           boldFont, 1, 1);  // 支払者名（列名なし）
        addHdr(table, "手数料※1",  boldFont, 1, 1);
        addHdr(table, "手数料※2",  boldFont, 1, 1);
        addHdr(table, "求人受付",   boldFont, 1, 1);
        addHdr(table, "手数料",     boldFont, 1, 1);
        addHdr(table, "備考",       boldFont, 1, 1);
        addHdr(table, "日雇",       boldFont, 1, 1);
        addHdr(table, "臨時",       boldFont, 1, 1);

        // ── ヘッダー行2 ──
        addHdr(table, "年月日",     boldFont, 1, 1);
        addHdr(table, "",           boldFont, 1, 1);
        addHdr(table, "届出手数料", boldFont, 1, 1);
        addHdr(table, "",           boldFont, 1, 1);
        addHdr(table, "事務費",     boldFont, 1, 1);
        addHdr(table, "割合",       boldFont, 1, 1);
        addHdr(table, "",           boldFont, 1, 1);
        addHdr(table, "1ヶ月",      boldFont, 1, 1);
        addHdr(table, "3ヶ月",      boldFont, 1, 1);

        // ── データ行（30行固定）──
        String lastDate = "";
        int sumComm = 0, sumTax = 0, sumCustFee = 0, sumDailyWage = 0;

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            if (i < items.size()) {
                FeeLedgerRow r = items.get(i);
                // 領収年月日：初行は実日付、同じ日付が続く場合は〃
                String dateStr = r.receiptDate.equals(lastDate) ? "〃" : r.receiptDate;
                lastDate = r.receiptDate;

                addData(table, dateStr,          normalFont, Element.ALIGN_LEFT);
                addData(table, r.customerName,   normalFont, Element.ALIGN_LEFT);
                addData(table, r.commissionStr,  normalFont, Element.ALIGN_RIGHT);
                addData(table, r.taxStr,         normalFont, Element.ALIGN_RIGHT);
                addData(table, r.customerFeeStr, normalFont, Element.ALIGN_RIGHT);
                addData(table, r.feeRate,        normalFont, Element.ALIGN_CENTER);
                addData(table, r.receiptNo,      normalFont, Element.ALIGN_CENTER);
                addData(table, r.dailyWageStr,   normalFont, Element.ALIGN_RIGHT);
                addData(table, "0",              normalFont, Element.ALIGN_RIGHT);

                sumComm     += r.commission;
                sumTax      += r.tax;
                sumCustFee  += r.customerFee;
                sumDailyWage += r.dailyWage;
            } else {
                // 空行
                addData(table, "〃",  normalFont, Element.ALIGN_LEFT);
                addData(table, "",    normalFont, Element.ALIGN_LEFT);
                addData(table, "0",   normalFont, Element.ALIGN_RIGHT);
                addData(table, "0",   normalFont, Element.ALIGN_RIGHT);
                addData(table, "",    normalFont, Element.ALIGN_RIGHT);
                addData(table, "〃",  normalFont, Element.ALIGN_CENTER);
                addData(table, "",    normalFont, Element.ALIGN_CENTER);
                addData(table, "0",   normalFont, Element.ALIGN_RIGHT);
                addData(table, "0",   normalFont, Element.ALIGN_RIGHT);
            }
        }

        // ── ページ計行 ──
        PdfPCell pgLabel = sumCell("ページ計", boldFont, 2);
        table.addCell(pgLabel);
        addSum(table, sumComm,     boldFont);
        addSum(table, sumTax,      boldFont);
        addSum(table, sumCustFee,  boldFont);
        addData(table, "",    boldFont, Element.ALIGN_CENTER);
        addData(table, "",    boldFont, Element.ALIGN_CENTER);
        addSum(table, sumDailyWage, boldFont);
        addSum(table, 0,           boldFont);

        // ── 月分累計行 ──
        String monthLabel = ym.getMonthValue() + "月分累計";
        PdfPCell mnLabel = sumCell(monthLabel, boldFont, 2);
        table.addCell(mnLabel);
        addSum(table, sumComm,     boldFont);
        addSum(table, sumTax,      boldFont);
        addSum(table, sumCustFee,  boldFont);
        addData(table, "",    boldFont, Element.ALIGN_CENTER);
        addData(table, "",    boldFont, Element.ALIGN_CENTER);
        addSum(table, sumDailyWage, boldFont);
        addSum(table, 0,           boldFont);

        doc.add(table);

        // ── 注記 ──
        Paragraph note1 = new Paragraph(
            "※1は、徴収した届け出手数料の総額から第二種特別加入料に充てるべき手数料額を除いた額を記載する。", noteFont);
        note1.setSpacingBefore(6);
        doc.add(note1);
        Paragraph note2 = new Paragraph("※2は、第二種特別加入保険料に充てるべき手数料。", noteFont);
        doc.add(note2);

        doc.close();
    }

    // ヘッダーセル
    private void addHdr(PdfPTable t, String text, Font f, int rowspan, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.BOX);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3);
        if (rowspan > 1) c.setRowspan(rowspan);
        if (colspan > 1) c.setColspan(colspan);
        t.addCell(c);
    }

    // データセル
    private void addData(PdfPTable t, String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3);
        c.setFixedHeight(17f);
        t.addCell(c);
    }

    // 合計セル（colspan=2で支払者名欄まで結合）
    private PdfPCell sumCell(String text, Font f, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.BOX);
        c.setColspan(colspan);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3);
        c.setFixedHeight(18f);
        return c;
    }

    // 合計数値セル
    private void addSum(PdfPTable t, int val, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(val > 0 ? String.format("%,d", val) : "0", f));
        c.setBorder(Rectangle.BOX);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3);
        c.setFixedHeight(18f);
        t.addCell(c);
    }

    // ─── DTO ──────────────────────────────────────────────────
    public static class FeeLedgerRow {
        public String receiptDate;
        public String customerName;
        public int    commission;
        public int    tax;
        public int    customerFee;
        public String feeRate;
        public String receiptNo;
        public int    dailyWage;
        public String commissionStr;
        public String taxStr;
        public String customerFeeStr;
        public String dailyWageStr;
    }
}
