package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // ─── 一覧 ─────────────────────────────────────────────────
    @GetMapping("")
    public String index(@RequestParam(required = false) String month,
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
            YearMonth ym = YearMonth.parse(month);
            model.addAttribute("items", buildRows(ym));
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMsg", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return "report-menu";
    }

    // ─── PDF ──────────────────────────────────────────────────
    @GetMapping("/pdf")
    public void pdf(@RequestParam(required = false) String month,
                    HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        YearMonth ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildPdf(buildRows(ym), ym, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=fee-ledger.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ─── データ取得 ───────────────────────────────────────────
    private List<FeeLedgerRow> buildRows(YearMonth ym) {
        List<FeeLedgerRow> list = new ArrayList<>();
        LocalDate monthEnd = ym.atEndOfMonth();
        String dateStr = monthEnd.getYear() + "/" + monthEnd.getMonthValue() + "/" + monthEnd.getDayOfMonth();

        for (Sales s : salesRepository.findAll()) {
            if (s.getId() == null) continue;
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;

                LocalDate rd = null;
                if (d.getIssuedAt()        != null) rd = d.getIssuedAt().toLocalDate();
                else if (d.getWorkEndDate()   != null) rd = d.getWorkEndDate();
                else if (d.getWorkStartDate() != null) rd = d.getWorkStartDate();
                if (rd == null) continue;
                if (rd.getYear() != ym.getYear() || rd.getMonthValue() != ym.getMonthValue()) continue;

                Customer c = d.getCustomerId() != null
                    ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                String cName = c != null ? c.getLastNameKanji() + "　" + c.getFirstNameKanji() : "";

                int wage  = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                int cFee  = d.getCustomerFee()  != null ? d.getCustomerFee()  : 0;
                int comm  = (int)(wage * FEE_RATE);
                int tax   = (int)(comm * 0.10);

                // 日給合計（dailyWages CSV）
                int dailyTotal = 0;
                if (d.getDailyWages() != null && !d.getDailyWages().isBlank())
                    for (String w : d.getDailyWages().split(","))
                        try { dailyTotal += Integer.parseInt(w.trim()); } catch (NumberFormatException ignored) {}

                FeeLedgerRow row = new FeeLedgerRow();
                row.receiptDate    = dateStr;
                row.customerName   = cName;
                row.wage           = wage;
                row.commission     = comm;
                row.tax            = tax;
                row.customerFee    = cFee;
                row.receiptNo      = d.getReceiptNo();
                row.dailyWage      = dailyTotal;
                row.wageStr        = String.format("%,d", wage);
                row.commissionStr  = String.format("%,d", comm);
                row.taxStr         = String.format("%,d", tax);
                row.customerFeeStr = String.format("%,d", cFee);   // 0円もそのまま表示
                row.dailyWageStr   = String.format("%,d", dailyTotal);
                list.add(row);
            }
        }

        list.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.receiptNo), Integer.parseInt(b.receiptNo)); }
            catch (NumberFormatException e) { return a.receiptNo.compareTo(b.receiptNo); }
        });
        return list;
    }

    // ─── PDF生成 ──────────────────────────────────────────────
    private void buildPdf(List<FeeLedgerRow> rows, YearMonth ym, ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        Document doc = new Document(PageSize.A4.rotate(), 18, 18, 18, 18);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleF = new Font(bf, 12, Font.BOLD);
        Font bold   = new Font(bf, 9,  Font.BOLD);
        Font normal = new Font(bf, 9);
        Font noteF  = new Font(bf, 7);

        doc.add(new Paragraph("手数料管理簿〔届出制手数料用〕", titleF));

        // 列幅（合計10列・均等に近い配分）
        float[] w = {2.2f, 2.6f, 1.8f, 1.8f, 1.6f, 1.8f, 1.4f, 1.4f, 1.8f, 1.6f};
        PdfPTable t = new PdfPTable(w);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);

        // ── ヘッダー（rowspan/二重線対応） ──
        // 行1: 領収 | 支払者名(rs2) | 賃金(rs2) | 手数料※1 | 手数料※2(rs2) | 求人受付 | 手数料 | 備考(rs2) | 日雇 | 臨時
        t.addCell(hdr("領収",      bold, false));   // 行1のみ
        t.addCell(hdrSpan("支払者名", bold));         // rowspan=2
        t.addCell(hdrSpan("賃金",    bold));          // rowspan=2
        t.addCell(hdr("手数料※1",  bold, false));
        t.addCell(hdrSpan("手数料※2", bold));        // rowspan=2
        t.addCell(hdr("求人受付",   bold, false));
        t.addCell(hdr("手数料",     bold, false));
        t.addCell(hdrSpan("備考",   bold));           // rowspan=2
        t.addCell(hdr("日雇",       bold, false));
        t.addCell(hdr("臨時",       bold, false));
        // 行2: 年月日 | (skip) | (skip) | 届出手数料 | (skip) | 事務費 | 割合 | (skip) | 1ヶ月 | 3ヶ月
        t.addCell(hdr("年月日",     bold, true));     // 二重下線
        t.addCell(hdr("届出手数料", bold, true));
        t.addCell(hdr("事務費",     bold, true));
        t.addCell(hdr("割合",       bold, true));
        t.addCell(hdr("1ヶ月",      bold, true));
        t.addCell(hdr("3ヶ月",      bold, true));

        // ── データ行（空行なし・データ分のみ） ──
        int sw=0, sc=0, st=0, sf=0, sd=0;
        for (FeeLedgerRow r : rows) {
            t.addCell(cen(r.receiptDate,    normal));
            t.addCell(cen(r.customerName,   normal));
            t.addCell(cen(r.wageStr,        normal));
            t.addCell(cen(r.commissionStr,  normal));
            t.addCell(cen(r.taxStr,         normal));
            t.addCell(cen(r.customerFeeStr, normal));
            t.addCell(cen("15%",            normal));
            t.addCell(cen(r.receiptNo,      normal));
            t.addCell(cen(r.dailyWageStr,   normal));
            t.addCell(cen("0",              normal));
            sw+=r.wage; sc+=r.commission; st+=r.tax; sf+=r.customerFee; sd+=r.dailyWage;
        }

        // ── ページ計 ──
        PdfPCell pg = spanCell("ページ計", bold, 2);
        t.addCell(pg);
        t.addCell(cen(fmt(sw), bold)); t.addCell(cen(fmt(sc), bold));
        t.addCell(cen(fmt(st), bold)); t.addCell(cen(fmt(sf), bold));
        t.addCell(cen("", bold)); t.addCell(cen("", bold));
        t.addCell(cen(fmt(sd), bold)); t.addCell(cen("0", bold));

        // ── 月分累計 ──
        t.addCell(spanCell(ym.getMonthValue() + "月分累計", bold, 2));
        t.addCell(cen(fmt(sw), bold)); t.addCell(cen(fmt(sc), bold));
        t.addCell(cen(fmt(st), bold)); t.addCell(cen(fmt(sf), bold));
        t.addCell(cen("", bold)); t.addCell(cen("", bold));
        t.addCell(cen(fmt(sd), bold)); t.addCell(cen("0", bold));

        doc.add(t);

        Paragraph n1 = new Paragraph(
            "※1は、徴収した届け出手数料の総額から第二種特別加入料に充てるべき手数料額を除いた額を記載する。", noteF);
        n1.setSpacingBefore(6);
        doc.add(n1);
        doc.add(new Paragraph("※2は、第二種特別加入保険料に充てるべき手数料。", noteF));
        doc.close();
    }

    // 中央揃えセル（データ用）
    private PdfPCell cen(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3);
        c.setFixedHeight(18f);
        return c;
    }

    // ヘッダーセル（doubleBottom=trueで下二重線）
    private PdfPCell hdr(String text, Font f, boolean doubleBottom) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3);
        c.setFixedHeight(16f);
        if (doubleBottom) {
            // 上・左・右は通常線、下は二重線（太線で代替）
            c.setBorderWidthTop(0.5f);
            c.setBorderWidthLeft(0.5f);
            c.setBorderWidthRight(0.5f);
            c.setBorderWidthBottom(2.5f);  // 太線で二重線を表現
        } else {
            c.setBorder(Rectangle.BOX);
            c.setBorderWidth(0.5f);
        }
        return c;
    }

    // rowspan=2ヘッダーセル（支払者名・賃金・手数料※2・備考）- 下辺も二重線
    private PdfPCell hdrSpan(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setRowspan(2);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3);
        c.setBorderWidthTop(0.5f);
        c.setBorderWidthLeft(0.5f);
        c.setBorderWidthRight(0.5f);
        c.setBorderWidthBottom(2.5f);  // 下辺を二重線（太線）
        return c;
    }

    // colspan=2セル（ページ計・累計用）
    private PdfPCell spanCell(String text, Font f, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setColspan(colspan);
        c.setBorder(Rectangle.BOX);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3);
        c.setFixedHeight(20f);
        return c;
    }

    private String fmt(int val) { return String.format("%,d", val); }

    // ─── DTO ──────────────────────────────────────────────────
    public static class FeeLedgerRow {
        public String receiptDate;
        public String customerName;
        public int    wage;
        public int    commission;
        public int    tax;
        public int    customerFee;
        public String receiptNo;
        public int    dailyWage;
        public String wageStr;
        public String commissionStr;
        public String taxStr;
        public String customerFeeStr;
        public String dailyWageStr;

        public String getReceiptDate()    { return receiptDate; }
        public String getCustomerName()   { return customerName; }
        public int    getWage()           { return wage; }
        public int    getCommission()     { return commission; }
        public int    getTax()            { return tax; }
        public int    getCustomerFee()    { return customerFee; }
        public String getReceiptNo()      { return receiptNo; }
        public int    getDailyWage()      { return dailyWage; }
        public String getWageStr()        { return wageStr; }
        public String getCommissionStr()  { return commissionStr; }
        public String getTaxStr()         { return taxStr; }
        public String getCustomerFeeStr() { return customerFeeStr; }
        public String getDailyWageStr()   { return dailyWageStr; }
    }
}
