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
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate   = ym.atEndOfMonth();

        List<FeeLedgerRow> items = buildRows(startDate, endDate);
        model.addAttribute("items", items);
        return "report-menu";
    }

    // ─── PDF出力 ───────────────────────────────────────────────
    @GetMapping("/pdf")
    public void reportMenuPdf(@RequestParam(required = false) String month,
                               HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        YearMonth ym = month != null && !month.isBlank()
            ? YearMonth.parse(month) : YearMonth.now();

        LocalDate startDate = ym.atDay(1);
        LocalDate endDate   = ym.atEndOfMonth();

        List<FeeLedgerRow> items = buildRows(startDate, endDate);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createFeeLedgerPdf(items, ym, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=fee-ledger.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ─── データ構築 ────────────────────────────────────────────
    private List<FeeLedgerRow> buildRows(LocalDate startDate, LocalDate endDate) {
        List<FeeLedgerRow> items = new ArrayList<>();

        for (Sales s : salesRepository.findAll()) {
            List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
            for (SalesDetail d : details) {
                // 領収番号が発行済みのもののみ
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;

                // 領収日（issuedAt優先、なければworkEndDate、なければworkStartDate）
                LocalDate receiptDate = null;
                if (d.getIssuedAt() != null) {
                    receiptDate = d.getIssuedAt().toLocalDate();
                } else if (d.getWorkEndDate() != null) {
                    receiptDate = d.getWorkEndDate();
                } else if (d.getWorkStartDate() != null) {
                    receiptDate = d.getWorkStartDate();
                }
                if (receiptDate == null) continue;

                // 月フィルタ：領収日の月末日で判定
                LocalDate monthEnd = LocalDate.of(receiptDate.getYear(), receiptDate.getMonthValue(),
                    receiptDate.withDayOfMonth(receiptDate.lengthOfMonth()).getDayOfMonth());
                if (monthEnd.isBefore(startDate) || monthEnd.isAfter(endDate)) continue;

                // 求人者名
                Customer customer = d.getCustomerId() != null
                    ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                String customerName = customer != null
                    ? customer.getLastNameKanji() + "　" + customer.getFirstNameKanji() : "";

                // 金額計算（1-7-1と同じロジック）
                int totalWage = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                int customerFee = d.getCustomerFee() != null ? d.getCustomerFee() : 0;
                int commission = (int)(totalWage * FEE_RATE);
                int consumptionTax = (int)(commission * 0.10);

                FeeLedgerRow row = new FeeLedgerRow();
                // 領収年月日は末日
                row.receiptDate    = String.format("%d年%d月%d日",
                    monthEnd.getYear(), monthEnd.getMonthValue(), monthEnd.getDayOfMonth());
                row.customerName   = customerName;
                row.wage           = totalWage;
                row.commission     = commission;
                row.tax            = consumptionTax;
                row.customerFee    = customerFee;
                row.receiptNo      = d.getReceiptNo();
                row.wageStr        = totalWage > 0 ? String.format("%,d円", totalWage) : "-";
                row.commissionStr  = commission > 0 ? String.format("%,d円", commission) : "-";
                row.taxStr         = consumptionTax > 0 ? String.format("%,d円", consumptionTax) : "-";
                row.customerFeeStr = customerFee > 0 ? String.format("%,d円", customerFee) : "-";
                items.add(row);
            }
        }

        // 領収番号順にソート
        items.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.receiptNo), Integer.parseInt(b.receiptNo)); }
            catch (NumberFormatException e) { return a.receiptNo.compareTo(b.receiptNo); }
        });

        return items;
    }

    // ─── PDF生成 ───────────────────────────────────────────────
    private void createFeeLedgerPdf(List<FeeLedgerRow> items, YearMonth ym,
                                     ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        // A4横向き
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont  = new Font(bf, 13, Font.BOLD);
        Font boldFont   = new Font(bf, 9, Font.BOLD);
        Font normalFont = new Font(bf, 9);
        Font smallFont  = new Font(bf, 8);

        // タイトル
        Paragraph title = new Paragraph(
            "手 数 料 管 理 簿 〔 届 出 制 手 数 料 用 〕　" + ym.getYear() + "年" + ym.getMonthValue() + "月分",
            titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(12);
        doc.add(title);

        // 会社名
        Paragraph company = new Paragraph("有限会社　ワークオフィス谷", new Font(bf, 9));
        company.setAlignment(Element.ALIGN_RIGHT);
        company.setSpacingAfter(8);
        doc.add(company);

        // テーブル
        // 列: No | 領収年月日 | 支払者名 | 賃金① | 手数料※1③ | 手数料※2④ | 求人受付事務費② | 備考
        float[] widths = {0.5f, 1.8f, 2f, 1.5f, 1.5f, 1.5f, 1.8f, 1.4f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        // ヘッダー
        String[] headers = {"No", "領収年月日", "支払者名", "賃金\n①",
            "手数料※1\n届出手数料③", "手数料※2\n消費税④", "求人受付\n事務費②", "備考\n（領収番号）"};
        for (String h : headers) {
            PdfPCell hc = new PdfPCell(new Phrase(h, boldFont));
            hc.setBackgroundColor(new com.itextpdf.text.BaseColor(123, 31, 162));
            hc.setBorder(Rectangle.BOX);
            hc.setHorizontalAlignment(Element.ALIGN_CENTER);
            hc.setVerticalAlignment(Element.ALIGN_MIDDLE);
            hc.setPadding(4);
            table.addCell(hc);
        }

        // データ行
        for (int i = 0; i < items.size(); i++) {
            FeeLedgerRow r = items.get(i);
            addCell(table, String.valueOf(i + 1), normalFont, Element.ALIGN_CENTER);
            addCell(table, r.receiptDate,    normalFont, Element.ALIGN_CENTER);
            addCell(table, r.customerName,   normalFont, Element.ALIGN_LEFT);
            addCell(table, r.wageStr,        normalFont, Element.ALIGN_RIGHT);
            addCell(table, r.commissionStr,  normalFont, Element.ALIGN_RIGHT);
            addCell(table, r.taxStr,         normalFont, Element.ALIGN_RIGHT);
            addCell(table, r.customerFeeStr, normalFont, Element.ALIGN_RIGHT);
            addCell(table, r.receiptNo,      normalFont, Element.ALIGN_CENTER);
        }

        // 合計行
        int totalWage = items.stream().mapToInt(r -> r.wage).sum();
        int totalComm = items.stream().mapToInt(r -> r.commission).sum();
        int totalTax  = items.stream().mapToInt(r -> r.tax).sum();
        int totalFee  = items.stream().mapToInt(r -> r.customerFee).sum();

        PdfPCell sumLabel = new PdfPCell(new Phrase("合　計", boldFont));
        sumLabel.setColspan(3);
        sumLabel.setBorder(Rectangle.BOX);
        sumLabel.setHorizontalAlignment(Element.ALIGN_CENTER);
        sumLabel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        sumLabel.setPadding(4);
        table.addCell(sumLabel);
        addCell(table, totalWage > 0 ? String.format("%,d円", totalWage) : "-", boldFont, Element.ALIGN_RIGHT);
        addCell(table, totalComm > 0 ? String.format("%,d円", totalComm) : "-", boldFont, Element.ALIGN_RIGHT);
        addCell(table, totalTax  > 0 ? String.format("%,d円", totalTax)  : "-", boldFont, Element.ALIGN_RIGHT);
        addCell(table, totalFee  > 0 ? String.format("%,d円", totalFee)  : "-", boldFont, Element.ALIGN_RIGHT);
        addCell(table, "", normalFont, Element.ALIGN_LEFT);

        doc.add(table);

        // 注記
        Paragraph note = new Paragraph(
            "※1 届出手数料：賃金総額①×15%（円未満切り捨て）　※2 消費税：届出手数料③×10%（円未満切り捨て）",
            smallFont);
        note.setSpacingBefore(8);
        doc.add(note);

        doc.close();
    }

    private void addCell(PdfPTable t, String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        t.addCell(c);
    }

    // ─── DTO ──────────────────────────────────────────────────
    public static class FeeLedgerRow {
        public String receiptDate;
        public String customerName;
        public int    wage;
        public int    commission;
        public int    tax;
        public int    customerFee;
        public String receiptNo;
        public String wageStr;
        public String commissionStr;
        public String taxStr;
        public String customerFeeStr;
    }
}
