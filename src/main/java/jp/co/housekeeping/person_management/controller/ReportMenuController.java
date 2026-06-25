package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
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
@RequestMapping("/report-menu")
public class ReportMenuController {

    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private SalesRepository       salesRepository;
    @Autowired private CustomerRepository    customerRepository;
    @Autowired private JdbcTemplate          jdbcTemplate;

    private static final double FEE_RATE = 0.15;

    // ─── 一覧 ─────────────────────────────────────────────────
    @GetMapping("")
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

    // ─── 日雇1ヶ月 保存 ──────────────────────────────────────
    @PostMapping("/save-daily-wage")
    public String saveDailyWage(@RequestParam Map<String,String> params,
                                 HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        String month = params.get("month");
        // detailId -> dailyWage1Month を更新
        params.forEach((key, value) -> {
            if (key.startsWith("dw_")) {
                try {
                    Long detailId = Long.parseLong(key.substring(3));
                    int val = value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
                    jdbcTemplate.update(
                        "UPDATE sales_details SET daily_wage_1month = ? WHERE id = ?",
                        val, detailId);
                } catch (Exception ignored) {}
            }
        });
        return "redirect:/report-menu?month=" + (month != null ? month : "");
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
                try { if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue; }
                catch (Exception e2) { continue; }

                // 紹介日→就労終了日→就労開始日→発行日 の優先順で月判定
                LocalDate rd = null;
                if      (d.getIntroductionDate() != null) rd = d.getIntroductionDate();
                else if (d.getWorkEndDate()       != null) rd = d.getWorkEndDate();
                else if (d.getWorkStartDate()     != null) rd = d.getWorkStartDate();
                else if (d.getIssuedAt()          != null) rd = d.getIssuedAt().toLocalDate();
                if (rd == null) continue;
                if (rd.getYear() != ym.getYear() || rd.getMonthValue() != ym.getMonthValue()) continue;

                Customer c = d.getCustomerId() != null
                    ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                String cName = c != null ? c.getLastNameKanji() + "　" + c.getFirstNameKanji() : "";

                int wage   = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                int cFee   = d.getCustomerFee()  != null ? d.getCustomerFee()  : 0;
                int comm   = (int)(wage * FEE_RATE);
                int tax    = (int)(comm * 0.10);
                int dw1m = 0;
                try {
                    Integer fetched = jdbcTemplate.queryForObject(
                        "SELECT daily_wage_1month FROM sales_details WHERE id = ?",
                        Integer.class, d.getId());
                    if (fetched != null) dw1m = fetched;
                } catch (Exception ignored) {}

                FeeLedgerRow row = new FeeLedgerRow();
                row.detailId       = d.getId();
                row.receiptDate    = dateStr;
                row.customerName   = cName;
                row.wage           = wage;
                row.commission     = comm;
                row.tax            = tax;
                row.customerFee    = cFee;
                row.receiptNo      = d.getReceiptNo();
                row.dailyWage1Month = dw1m;
                row.wageStr        = fmt(wage);
                row.commissionStr  = fmt(comm);
                row.taxStr         = fmt(tax);
                row.customerFeeStr = fmt(cFee);
                row.dailyWage1MonthStr = fmt(dw1m);
                list.add(row);
            }
        }
        list.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.receiptNo), Integer.parseInt(b.receiptNo)); }
            catch (NumberFormatException e) { return a.receiptNo.compareTo(b.receiptNo); }
        });
        return list;
    }

    // ─── PDF生成（A4縦・斜め線対応） ─────────────────────────
    private void buildPdf(List<FeeLedgerRow> rows, YearMonth ym, ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        // A4縦
        Document doc = new Document(PageSize.A4, 18, 18, 18, 18);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleF = new Font(bf, 11, Font.BOLD);
        Font bold   = new Font(bf, 8,  Font.BOLD);
        Font normal = new Font(bf, 8);
        Font noteF  = new Font(bf, 6);

        doc.add(new Paragraph("手数料管理簿〔届出制手数料用〕", titleF));

        // A4縦なので列を少し絞る
        float[] w = {2.0f, 2.4f, 1.6f, 1.6f, 1.4f, 1.6f, 1.2f, 1.2f, 1.6f, 1.4f};
        PdfPTable t = new PdfPTable(w);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);

        // ── ヘッダー行1 ──
        t.addCell(hdr("領収",      bold, false));
        t.addCell(hdrSpan("支払者名",  bold));
        t.addCell(hdrSpan("賃金",      bold));
        t.addCell(hdr("手数料※1",  bold, false));
        t.addCell(hdrSpan("手数料※2", bold));
        t.addCell(hdr("求人受付",   bold, false));
        t.addCell(hdr("手数料",     bold, false));
        t.addCell(hdrSpan("備考",      bold));
        t.addCell(hdr("日雇",       bold, false));
        t.addCell(hdr("臨時",       bold, false));
        // ── ヘッダー行2 ──
        t.addCell(hdr("年月日",     bold, true));
        t.addCell(hdr("届出手数料", bold, true));
        t.addCell(hdr("事務費",     bold, true));
        t.addCell(hdr("割合",       bold, true));
        t.addCell(hdr("1ヶ月",      bold, true));
        t.addCell(hdr("3ヶ月",      bold, true));

        // ── データ行 ──
        int sw=0, sc=0, st=0, sf=0, sd=0;
        for (FeeLedgerRow r : rows) {
            t.addCell(cen(r.receiptDate,          normal));
            t.addCell(cen(r.customerName,          normal));
            t.addCell(rit(r.wageStr,               normal));
            t.addCell(rit(r.commissionStr,         normal));
            t.addCell(rit(r.taxStr,                normal));
            t.addCell(rit(r.customerFeeStr,        normal));
            t.addCell(cen("15%",                   normal));
            t.addCell(cen(r.receiptNo,             normal));
            t.addCell(rit(r.dailyWage1MonthStr,    normal));
            t.addCell(diag(normal));                           // 臨時3ヶ月：斜め線
            sw+=r.wage; sc+=r.commission; st+=r.tax; sf+=r.customerFee; sd+=r.dailyWage1Month;
        }

        // ── ページ計 ──
        t.addCell(spanCell("ページ計", bold, 2));
        t.addCell(rit(fmt(sw), bold)); t.addCell(rit(fmt(sc), bold));
        t.addCell(rit(fmt(st), bold)); t.addCell(rit(fmt(sf), bold));
        t.addCell(diag(bold));   // 手数料割合：斜め線
        t.addCell(diag(bold));   // 備考：斜め線
        t.addCell(rit(fmt(sd), bold));
        t.addCell(diag(bold));   // 臨時3ヶ月：斜め線

        // ── 月分累計 ──
        t.addCell(spanCell(ym.getMonthValue() + "月分累計", bold, 2));
        t.addCell(rit(fmt(sw), bold)); t.addCell(rit(fmt(sc), bold));
        t.addCell(rit(fmt(st), bold)); t.addCell(rit(fmt(sf), bold));
        t.addCell(diag(bold));
        t.addCell(diag(bold));
        t.addCell(rit(fmt(sd), bold));
        t.addCell(diag(bold));

        doc.add(t);

        Paragraph n1 = new Paragraph(
            "※1は、徴収した届け出手数料の総額から第二種特別加入料に充てるべき手数料額を除いた額を記載する。", noteF);
        n1.setSpacingBefore(4); doc.add(n1);
        doc.add(new Paragraph("※2は、第二種特別加入保険料に充てるべき手数料。", noteF));
        doc.close();
    }

    // ─── セルヘルパー ─────────────────────────────────────────

    // 斜め線セル
    private PdfPCell diag(Font f) {
        PdfPCell c = new PdfPCell(new Phrase("", f));
        c.setBorder(Rectangle.BOX);
        c.setFixedHeight(18f);
        c.setCellEvent((cell, position, canvases) -> {
            PdfContentByte cb = canvases[PdfPTable.LINECANVAS];
            cb.saveState();
            cb.setLineWidth(0.5f);
            cb.moveTo(position.getLeft(), position.getBottom());
            cb.lineTo(position.getRight(), position.getTop());
            cb.stroke();
            cb.restoreState();
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

    private PdfPCell hdr(String text, Font f, boolean doubleBottom) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(2); c.setFixedHeight(15f);
        if (doubleBottom) {
            c.setBorderWidthTop(0.5f); c.setBorderWidthLeft(0.5f);
            c.setBorderWidthRight(0.5f); c.setBorderWidthBottom(2.5f);
        } else {
            c.setBorder(Rectangle.BOX); c.setBorderWidth(0.5f);
        }
        return c;
    }

    private PdfPCell hdrSpan(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setRowspan(2); c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(2);
        c.setBorderWidthTop(0.5f); c.setBorderWidthLeft(0.5f);
        c.setBorderWidthRight(0.5f); c.setBorderWidthBottom(2.5f);
        return c;
    }

    private PdfPCell spanCell(String text, Font f, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setColspan(colspan); c.setBorder(Rectangle.BOX);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(2); c.setFixedHeight(20f);
        return c;
    }

    private String fmt(int val) { return String.format("%,d", val); }

    // ─── DTO ──────────────────────────────────────────────────
    public static class FeeLedgerRow {
        public Long   detailId;
        public String receiptDate;
        public String customerName;
        public int    wage, commission, tax, customerFee, dailyWage1Month;
        public String receiptNo;
        public String wageStr, commissionStr, taxStr, customerFeeStr, dailyWage1MonthStr;

        public Long   getDetailId()            { return detailId; }
        public String getReceiptDate()         { return receiptDate; }
        public String getCustomerName()        { return customerName; }
        public int    getWage()                { return wage; }
        public int    getCommission()          { return commission; }
        public int    getTax()                 { return tax; }
        public int    getCustomerFee()         { return customerFee; }
        public String getReceiptNo()           { return receiptNo; }
        public int    getDailyWage1Month()     { return dailyWage1Month; }
        public String getWageStr()             { return wageStr; }
        public String getCommissionStr()       { return commissionStr; }
        public String getTaxStr()              { return taxStr; }
        public String getCustomerFeeStr()      { return customerFeeStr; }
        public String getDailyWage1MonthStr()  { return dailyWage1MonthStr; }
    }
}
