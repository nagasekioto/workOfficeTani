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

    private static final double FEE_RATE     = 0.15;
    private static final int    ROWS_PER_PAGE = 30;

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
            List<FeeLedgerRow> rows = buildRows(ym);
            model.addAttribute("items", rows);
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
        List<FeeLedgerRow> rows = buildRows(ym);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildPdf(rows, ym, baos);

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
                if (d.getIssuedAt()      != null) rd = d.getIssuedAt().toLocalDate();
                else if (d.getWorkEndDate()   != null) rd = d.getWorkEndDate();
                else if (d.getWorkStartDate() != null) rd = d.getWorkStartDate();
                if (rd == null) continue;
                if (rd.getYear() != ym.getYear() || rd.getMonthValue() != ym.getMonthValue()) continue;

                Customer c = d.getCustomerId() != null
                    ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                String cName = c != null ? c.getLastNameKanji() + "　" + c.getFirstNameKanji() : "";

                int wage   = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                int cFee   = d.getCustomerFee()  != null ? d.getCustomerFee()  : 0;
                int comm   = (int)(wage * FEE_RATE);
                int tax    = (int)(comm * 0.10);

                FeeLedgerRow row = new FeeLedgerRow();
                row.receiptDate    = dateStr;
                row.customerName   = cName;
                row.commission     = comm;
                row.tax            = tax;
                row.customerFee    = cFee;
                row.receiptNo      = d.getReceiptNo();
                row.dailyWage      = wage;
                row.commissionStr  = String.format("%,d", comm);
                row.taxStr         = String.format("%,d", tax);
                row.customerFeeStr = cFee > 0 ? String.format("%,d", cFee) : "";
                row.dailyWageStr   = String.format("%,d", wage);
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

        Document doc = new Document(PageSize.A4, 28, 28, 28, 28);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font title8  = new Font(bf, 11, Font.BOLD);
        Font bold    = new Font(bf, 8,  Font.BOLD);
        Font normal  = new Font(bf, 8);
        Font note    = new Font(bf, 7);

        doc.add(para("手数料管理簿〔届出制手数料用〕", title8, Element.ALIGN_LEFT, 4));

        float[] w = {2.2f, 2.8f, 1.6f, 1.4f, 1.6f, 1.2f, 1.2f, 1.6f, 1.4f};
        PdfPTable t = new PdfPTable(w);
        t.setWidthPercentage(100);
        t.setSpacingBefore(2);

        // ヘッダー2行
        for (String s : new String[]{"領収","","手数料※1","手数料※2","求人受付","手数料","備考","日雇","臨時"})
            t.addCell(hdr(s, bold));
        for (String s : new String[]{"年月日","","届出手数料","","事務費","割合","","1ヶ月","3ヶ月"})
            t.addCell(hdr(s, bold));

        // データ30行
        String lastDate = "";
        int sc=0, st=0, sf=0, sw=0;
        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            if (i < rows.size()) {
                FeeLedgerRow r = rows.get(i);
                String ds = r.receiptDate.equals(lastDate) ? "〃" : r.receiptDate;
                lastDate = r.receiptDate;
                t.addCell(dat(ds,             normal, Element.ALIGN_LEFT));
                t.addCell(dat(r.customerName, normal, Element.ALIGN_LEFT));
                t.addCell(dat(r.commissionStr,normal, Element.ALIGN_RIGHT));
                t.addCell(dat(r.taxStr,       normal, Element.ALIGN_RIGHT));
                t.addCell(dat(r.customerFeeStr,normal,Element.ALIGN_RIGHT));
                t.addCell(dat("15%",          normal, Element.ALIGN_CENTER));
                t.addCell(dat(r.receiptNo,    normal, Element.ALIGN_CENTER));
                t.addCell(dat(r.dailyWageStr, normal, Element.ALIGN_RIGHT));
                t.addCell(dat("0",            normal, Element.ALIGN_RIGHT));
                sc+=r.commission; st+=r.tax; sf+=r.customerFee; sw+=r.dailyWage;
            } else {
                t.addCell(dat("〃","",normal,Element.ALIGN_LEFT));
                t.addCell(dat("",   normal, Element.ALIGN_LEFT));
                t.addCell(dat("0",  normal, Element.ALIGN_RIGHT));
                t.addCell(dat("0",  normal, Element.ALIGN_RIGHT));
                t.addCell(dat("",   normal, Element.ALIGN_RIGHT));
                t.addCell(dat("〃", normal, Element.ALIGN_CENTER));
                t.addCell(dat("",   normal, Element.ALIGN_CENTER));
                t.addCell(dat("0",  normal, Element.ALIGN_RIGHT));
                t.addCell(dat("0",  normal, Element.ALIGN_RIGHT));
            }
        }

        // ページ計
        PdfPCell pg = new PdfPCell(new Phrase("ページ計", bold));
        pg.setColspan(2); pg.setBorder(Rectangle.BOX); pg.setHorizontalAlignment(Element.ALIGN_CENTER);
        pg.setPadding(3); pg.setFixedHeight(18f); t.addCell(pg);
        t.addCell(sum(sc, bold)); t.addCell(sum(st, bold)); t.addCell(sum(sf, bold));
        t.addCell(dat("", bold, Element.ALIGN_CENTER)); t.addCell(dat("", bold, Element.ALIGN_CENTER));
        t.addCell(sum(sw, bold)); t.addCell(sum(0, bold));

        // 月分累計
        PdfPCell mn = new PdfPCell(new Phrase(ym.getMonthValue() + "月分累計", bold));
        mn.setColspan(2); mn.setBorder(Rectangle.BOX); mn.setHorizontalAlignment(Element.ALIGN_CENTER);
        mn.setPadding(3); mn.setFixedHeight(18f); t.addCell(mn);
        t.addCell(sum(sc, bold)); t.addCell(sum(st, bold)); t.addCell(sum(sf, bold));
        t.addCell(dat("", bold, Element.ALIGN_CENTER)); t.addCell(dat("", bold, Element.ALIGN_CENTER));
        t.addCell(sum(sw, bold)); t.addCell(sum(0, bold));

        doc.add(t);
        doc.add(para("※1は、徴収した届け出手数料の総額から第二種特別加入料に充てるべき手数料額を除いた額を記載する。", note, Element.ALIGN_LEFT, 6));
        doc.add(para("※2は、第二種特別加入保険料に充てるべき手数料。", note, Element.ALIGN_LEFT, 0));
        doc.close();
    }

    private Paragraph para(String text, Font f, int align, float spaceBefore) {
        Paragraph p = new Paragraph(text, f);
        p.setAlignment(align);
        p.setSpacingBefore(spaceBefore);
        return p;
    }

    private PdfPCell hdr(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.BOX); c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(3); c.setFixedHeight(16f);
        return c;
    }

    private PdfPCell dat(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX); c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(3); c.setFixedHeight(17f);
        return c;
    }

    private PdfPCell dat(String a, String b, Font f, int align) {
        return dat(a, f, align);
    }

    private PdfPCell sum(int val, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(String.format("%,d", val), f));
        c.setBorder(Rectangle.BOX); c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(3); c.setFixedHeight(18f);
        return c;
    }

    // ─── DTO ──────────────────────────────────────────────────
    public static class FeeLedgerRow {
        public String receiptDate;
        public String customerName;
        public int    commission;
        public int    tax;
        public int    customerFee;
        public String receiptNo;
        public int    dailyWage;
        public String commissionStr;
        public String taxStr;
        public String customerFeeStr;
        public String dailyWageStr;

        public String getReceiptDate()    { return receiptDate; }
        public String getCustomerName()   { return customerName; }
        public int    getCommission()     { return commission; }
        public int    getTax()            { return tax; }
        public int    getCustomerFee()    { return customerFee; }
        public String getReceiptNo()      { return receiptNo; }
        public int    getDailyWage()      { return dailyWage; }
        public String getCommissionStr()  { return commissionStr; }
        public String getTaxStr()         { return taxStr; }
        public String getCustomerFeeStr() { return customerFeeStr; }
        public String getDailyWageStr()   { return dailyWageStr; }
    }
}
