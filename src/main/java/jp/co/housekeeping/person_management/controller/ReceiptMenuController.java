package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.ReceiptsIssued;
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.ReceiptsIssuedRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/receipt-menu")
public class ReceiptMenuController {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private ReceiptsIssuedRepository receiptsIssuedRepository;

    @GetMapping("")
    public String menu(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "receipt-menu";
    }

    // ─── 1-7-1 求人者宛領収書一覧 ────────────────────────
    @GetMapping("/customer-receipt")
    public String customerReceipt(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        List<ReceiptItem> items = new ArrayList<>();
        for (Customer c : customerRepository.findAll()) {
            for (Sales s : salesRepository.findAll()) {
                for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                    if (d.getCustomerId() != null && d.getCustomerId().equals(c.getId())
                            && d.getCustomerFee() != null && d.getCustomerFee() > 0) {
                        ReceiptItem item = new ReceiptItem();
                        item.customer = c;
                        item.detail   = d;
                        item.salesId  = s.getId();
                        item.personId = s.getPersonId();
                        receiptsIssuedRepository.findBySalesDetailId(d.getId()).ifPresent(ri -> {
                            item.issued        = true;
                            item.receiptNumber = ri.getReceiptNumberEffective();
                        });
                        items.add(item);
                    }
                }
            }
        }
        model.addAttribute("items", items);
        return "receipt-customer-list";
    }

    // ─── 1-7-1 PDF出力 ────────────────────────────────
    @GetMapping("/customer-receipt/pdf")
    public void customerReceiptPdf(@RequestParam Long detailId,
                                    HttpSession session,
                                    HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        SalesDetail detail = salesDetailRepository.findById(detailId).orElse(null);
        if (detail == null) { response.sendError(404); return; }

        Customer customer = detail.getCustomerId() != null
                ? customerRepository.findById(detail.getCustomerId()).orElse(null) : null;
        Sales sales = detail.getSalesId() != null
                ? salesRepository.findById(detail.getSalesId()).orElse(null) : null;
        Person person = (sales != null && sales.getPersonId() != null)
                ? personRepository.findById(sales.getPersonId()).orElse(null) : null;

        // 採番：未発行なら新規保存してidを採番番号とする
        int receiptNumber;
        ReceiptsIssued existing = receiptsIssuedRepository.findBySalesDetailId(detailId).orElse(null);
        if (existing != null) {
            receiptNumber = existing.getReceiptNumberEffective();
        } else {
            ReceiptsIssued ri = new ReceiptsIssued();
            ri.setSalesDetailId(detailId);
            ri.setCustomerId(customer != null ? customer.getId() : null);
            ri.setReceiptType("CUSTOMER");
            int tw  = detail.getMonthlyTotal()  != null ? detail.getMonthlyTotal()  : 0;
            int cf  = detail.getCustomerFee()   != null ? detail.getCustomerFee()   : 1000;
            ri.setAmount(cf + (int)(tw * 0.15));
            ri.setPrinted(true);
            ri.setPrintedAt(LocalDateTime.now());
            ri.setCreatedAt(LocalDateTime.now());
            ReceiptsIssued saved = receiptsIssuedRepository.save(ri);
            receiptNumber = saved.getReceiptNumberEffective();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createCustomerReceiptPdf(detail, customer, person, receiptNumber, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=customer-receipt.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ─── 1-7-2 求職受付手数料領収書一覧 ─────────────────
    @GetMapping("/jobseeker-receipt")
    public String jobseekerReceipt(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        List<JobseekerReceiptItem> items = new ArrayList<>();
        for (Sales s : salesRepository.findAll()) {
            Person person = personRepository.findById(s.getPersonId()).orElse(null);
            if (person == null) continue;
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                if (d.getReceptionFee() != null && d.getReceptionFee() > 0) {
                    JobseekerReceiptItem item = new JobseekerReceiptItem();
                    item.person  = person;
                    item.detail  = d;
                    item.salesId = s.getId();
                    receiptsIssuedRepository.findBySalesDetailId(d.getId()).ifPresent(ri -> {
                        item.issued        = true;
                        item.receiptNumber = ri.getReceiptNumberEffective();
                    });
                    items.add(item);
                }
            }
        }
        model.addAttribute("items", items);
        return "receipt-jobseeker-list";
    }

    // ─── 1-7-2 PDF出力 ────────────────────────────────
    @GetMapping("/jobseeker-receipt/pdf")
    public void jobseekerReceiptPdf(@RequestParam Long detailId,
                                     HttpSession session,
                                     HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        SalesDetail detail = salesDetailRepository.findById(detailId).orElse(null);
        if (detail == null) { response.sendError(404); return; }

        Sales sales = detail.getSalesId() != null
                ? salesRepository.findById(detail.getSalesId()).orElse(null) : null;
        Person person = (sales != null && sales.getPersonId() != null)
                ? personRepository.findById(sales.getPersonId()).orElse(null) : null;

        int receiptNumber;
        ReceiptsIssued existing = receiptsIssuedRepository.findBySalesDetailId(detailId).orElse(null);
        if (existing != null) {
            receiptNumber = existing.getReceiptNumberEffective();
        } else {
            ReceiptsIssued ri = new ReceiptsIssued();
            ri.setSalesDetailId(detailId);
            ri.setCustomerId(null);
            ri.setReceiptType("JOBSEEKER");
            ri.setAmount(detail.getReceptionFee());
            ri.setPrinted(true);
            ri.setPrintedAt(LocalDateTime.now());
            ri.setCreatedAt(LocalDateTime.now());
            ReceiptsIssued saved = receiptsIssuedRepository.save(ri);
            receiptNumber = saved.getReceiptNumberEffective();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createJobseekerReceiptPdf(detail, person, receiptNumber, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=jobseeker-receipt.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ─── 発行済み一覧（月単位）────────────────────────
    @GetMapping("/issued-list")
    public String issuedList(@RequestParam(required = false) String month,
                              HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        if (month == null || month.isBlank()) {
            String cur = LocalDateTime.now().getYear() + "-"
                + String.format("%02d", LocalDateTime.now().getMonthValue());
            return "redirect:/receipt-menu/issued-list?month=" + cur;
        }

        List<IssuedListRow> rows = new ArrayList<>();
        for (ReceiptsIssued ri : receiptsIssuedRepository.findByMonth(month)) {
            IssuedListRow row  = new IssuedListRow();
            row.receiptNumber  = ri.getReceiptNumberEffective();
            row.receiptType    = "CUSTOMER".equals(ri.getReceiptType()) ? "1-7-1 求人者宛" : "1-7-2 求職受付";
            row.amount         = ri.getAmount() != null ? ri.getAmount() : 0;
            row.amountStr      = "¥" + String.format("%,d", row.amount);
            row.issuedAt       = ri.getPrintedAt() != null ? ri.getPrintedAt() : ri.getCreatedAt();
            row.salesDetailId  = ri.getSalesDetailId();

            if ("CUSTOMER".equals(ri.getReceiptType()) && ri.getCustomerId() != null) {
                customerRepository.findById(ri.getCustomerId())
                    .ifPresent(c -> row.partyName = c.getLastNameKanji() + " " + c.getFirstNameKanji());
            }
            // JOBSEEKER は customer_id=null なので sales_detail から person を逆引き
            if ("JOBSEEKER".equals(ri.getReceiptType()) && ri.getSalesDetailId() != null) {
                salesDetailRepository.findById(ri.getSalesDetailId()).ifPresent(d -> {
                    if (d.getSalesId() != null) {
                        salesRepository.findById(d.getSalesId()).ifPresent(s -> {
                            if (s.getPersonId() != null) {
                                personRepository.findById(s.getPersonId())
                                    .ifPresent(p -> row.partyName = p.getLastNameKanji() + " " + p.getFirstNameKanji());
                            }
                        });
                    }
                });
            }
            if (row.partyName == null) row.partyName = "－";
            rows.add(row);
        }

        long total = rows.stream().mapToLong(r -> r.amount).sum();
        model.addAttribute("rows",           rows);
        model.addAttribute("totalAmountStr", "¥" + String.format("%,d", total));
        model.addAttribute("totalCount",     rows.size());
        model.addAttribute("selectedMonth",  month);
        return "receipt-issued-list";
    }

    // ─── PDF生成：1-7-1 ──────────────────────────────
    private void createCustomerReceiptPdf(SalesDetail detail, Customer customer, Person person,
                                           int receiptNumber, ByteArrayOutputStream baos)
            throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont  = new Font(bf, 16, Font.BOLD);
        Font boldFont   = new Font(bf, 10, Font.BOLD);
        Font normalFont = new Font(bf, 10);
        Font smallFont  = new Font(bf, 8);
        Font largeFont  = new Font(bf, 13, Font.BOLD);

        // 右上：領収番号
        PdfPTable topRight = new PdfPTable(1);
        topRight.setWidthPercentage(35);
        topRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell noCell = new PdfPCell(new Phrase(String.format("領収番号　%04d", receiptNumber), boldFont));
        noCell.setBorder(Rectangle.BOX); noCell.setPadding(5);
        noCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        topRight.addCell(noCell);
        doc.add(topRight);

        // タイトル
        Paragraph title = new Paragraph("求 人 者 宛 領 収 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(8); title.setSpacingAfter(6);
        doc.add(title);

        // 受付月日・発行年月日（右寄せ）
        LocalDate introDate = detail.getIntroductionDate();
        String receptStr = introDate != null
            ? String.format("受付月日　%d月　%d日", introDate.getMonthValue(), introDate.getDayOfMonth())
            : "受付月日　　月　　日";
        LocalDate today = LocalDate.now();
        String issueStr = String.format("%d年　%d月　%d日", today.getYear(), today.getMonthValue(), today.getDayOfMonth());

        PdfPTable dateTable = new PdfPTable(1);
        dateTable.setWidthPercentage(40);
        dateTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell rc = new PdfPCell(new Phrase(receptStr, normalFont));
        rc.setBorder(Rectangle.NO_BORDER); rc.setHorizontalAlignment(Element.ALIGN_RIGHT); rc.setPadding(2);
        dateTable.addCell(rc);
        PdfPCell ic = new PdfPCell(new Phrase(issueStr, normalFont));
        ic.setBorder(Rectangle.NO_BORDER); ic.setHorizontalAlignment(Element.ALIGN_RIGHT); ic.setPadding(2);
        dateTable.addCell(ic);
        doc.add(dateTable);

        // 宛名
        String customerName = customer != null
            ? customer.getLastNameKanji() + " " + customer.getFirstNameKanji() : "　　　　　";
        Paragraph nameP = new Paragraph(customerName + "　様", largeFont);
        nameP.setSpacingBefore(10); nameP.setSpacingAfter(4);
        doc.add(nameP);

        int totalWage   = detail.getMonthlyTotal() != null ? detail.getMonthlyTotal() : 0;
        int customerFee = detail.getCustomerFee()  != null ? detail.getCustomerFee()  : 1000;
        int commission  = (int)(totalWage * 0.15);
        int total       = customerFee + commission;

        Paragraph amountP = new Paragraph("（②+③）一金　　" + String.format("%,d", total) + " 円也", largeFont);
        amountP.setSpacingAfter(4);
        doc.add(amountP);

        Paragraph desc = new Paragraph("職業紹介手数料として上記金額を正に領収しました。", normalFont);
        desc.setSpacingAfter(10);
        doc.add(desc);

        // 会社情報（右寄せ）
        PdfPTable companyTable = new PdfPTable(1);
        companyTable.setWidthPercentage(45);
        companyTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (String line : new String[]{
                "厚生労働省許可　13-ユ-040077", "有限会社　ワークオフィス谷",
                "〒107-0052", "東京都港区赤坂6-10-45-203", "TEL 03-5544-8315　FAX 03-5544-8316"}) {
            PdfPCell c = new PdfPCell(new Phrase(line, smallFont));
            c.setBorder(Rectangle.NO_BORDER); c.setPadding(1);
            companyTable.addCell(c);
        }
        doc.add(companyTable);

        // 賃金算定テーブル
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable wageTable = new PdfPTable(new float[]{2, 2, 4, 3, 3});
        wageTable.setWidthPercentage(100); wageTable.setSpacingBefore(5);

        String workerName = person != null
            ? person.getLastNameKanji() + " " + person.getFirstNameKanji() : "";
        addWageHeader(wageTable, workerName, boldFont);

        String dailyWages = detail.getDailyWages();
        int dailyWageUnit = 0, dailyDays = 0, dailyTotal = 0;
        if (dailyWages != null && !dailyWages.isBlank()) {
            String[] wages = dailyWages.split(",");
            dailyDays = wages.length;
            for (String w : wages) try { dailyTotal += Integer.parseInt(w.trim()); } catch (NumberFormatException ignored) {}
            if (dailyDays > 0) dailyWageUnit = dailyTotal / dailyDays;
        }
        addWageRow(wageTable, "日給",   "1日",
            dailyWageUnit > 0 ? String.format("① %,d円", dailyWageUnit) : "①　　　円",
            dailyDays > 0 ? String.format("② %d日間", dailyDays) : "②　　日間",
            dailyTotal > 0 ? String.format("%,d 円", dailyTotal) : "　　　　0 円", normalFont);

        int hw  = detail.getHourlyWage()    != null ? detail.getHourlyWage()    : 0;
        double wh = detail.getWorkingHours() != null ? detail.getWorkingHours().doubleValue() : 0;
        addWageRow(wageTable, "時間給", "1時間",
            hw > 0 ? String.format("%,d円", hw) : "　　　円",
            wh > 0 ? String.format("%.0f 時間", wh) : "　　時間",
            hw > 0 && wh > 0 ? String.format("%,d 円", (int)(hw * wh)) : "　　　　0 円", normalFont);

        int hwo = detail.getHourlyWageOvertime() != null ? detail.getHourlyWageOvertime() : 0;
        addWageRow(wageTable, "", "1時間",
            hwo > 0 ? String.format("%,d円", hwo) : "　　　円", "　　時間", "　　　　0 円", normalFont);
        addWageRow(wageTable, "手", "1時間", "　　　円", "　　時間", "　0 円", normalFont);
        addEmptyWageRow(wageTable, "当", normalFont);
        addEmptyWageRow(wageTable, "　", normalFont);

        PdfPCell tl = new PdfPCell(new Phrase("賃 金 総 額", boldFont));
        tl.setColspan(3); tl.setPadding(5); tl.setHorizontalAlignment(Element.ALIGN_CENTER);
        wageTable.addCell(tl);
        PdfPCell tn = new PdfPCell(new Phrase("①", boldFont));
        tn.setPadding(5); tn.setHorizontalAlignment(Element.ALIGN_CENTER); wageTable.addCell(tn);
        PdfPCell ta = new PdfPCell(new Phrase(String.format("%,d 円", totalWage), boldFont));
        ta.setPadding(5); ta.setHorizontalAlignment(Element.ALIGN_RIGHT); wageTable.addCell(ta);
        doc.add(wageTable);

        // 領収金額算定テーブル
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable feeTable = new PdfPTable(new float[]{1, 5, 1, 3});
        feeTable.setWidthPercentage(100); feeTable.setSpacingBefore(5);

        addFeeRow(feeTable, "領\n収\n金\n額\n算\n定",
            "求職受付手数料（求人1件につき1回）",
            "②", String.format("%,d 円", customerFee), boldFont, normalFont, true);
        addFeeRow(feeTable, "", "紹介手数料（①×15% ※円未満切り捨て）",
            "③", String.format("%,d 円", commission), boldFont, normalFont, false);
        addFeeRow(feeTable, "", "消費税（10%）", "", "", boldFont, normalFont, false);

        for (PdfPCell[] cc : new PdfPCell[][]{
            {cell("", normalFont), cell("手数料合計　（②+③）", boldFont),
             cell("", normalFont), cell(String.format("%,d 円", total), boldFont)}}) {
            cc[3].setHorizontalAlignment(Element.ALIGN_RIGHT);
            for (PdfPCell c : cc) { c.setBorder(Rectangle.BOX); c.setPadding(4); feeTable.addCell(c); }
        }
        doc.add(feeTable);
        doc.close();
    }

    // ─── PDF生成：1-7-2 ──────────────────────────────
    private void createJobseekerReceiptPdf(SalesDetail detail, Person person,
                                            int receiptNumber, ByteArrayOutputStream baos)
            throws DocumentException, IOException {
        Document doc = new Document(PageSize.A5.rotate());
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont  = new Font(bf, 14, Font.BOLD);
        Font boldFont   = new Font(bf, 10, Font.BOLD);
        Font normalFont = new Font(bf, 10);
        Font smallFont  = new Font(bf, 8);

        // 右上：領収番号
        PdfPTable topRight = new PdfPTable(1);
        topRight.setWidthPercentage(35);
        topRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell noCell = new PdfPCell(new Phrase(String.format("領収番号　%04d", receiptNumber), boldFont));
        noCell.setBorder(Rectangle.BOX); noCell.setPadding(4);
        noCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        topRight.addCell(noCell);
        doc.add(topRight);

        // タイトル
        Paragraph title = new Paragraph("求 職 受 付 手 数 料 領 収 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(6); title.setSpacingAfter(6);
        doc.add(title);

        // 受付月日・発行年月日（右寄せ）
        LocalDate introDate = detail.getIntroductionDate();
        String receptStr = introDate != null
            ? String.format("受付月日　%d月　%d日", introDate.getMonthValue(), introDate.getDayOfMonth())
            : "受付月日　　月　　日";
        LocalDate today = LocalDate.now();
        String issueStr = String.format("%d年　%d月　%d日", today.getYear(), today.getMonthValue(), today.getDayOfMonth());

        PdfPTable dateTable = new PdfPTable(1);
        dateTable.setWidthPercentage(50);
        dateTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell rc = new PdfPCell(new Phrase(receptStr, normalFont));
        rc.setBorder(Rectangle.NO_BORDER); rc.setHorizontalAlignment(Element.ALIGN_RIGHT); rc.setPadding(2);
        dateTable.addCell(rc);
        PdfPCell ic = new PdfPCell(new Phrase(issueStr, normalFont));
        ic.setBorder(Rectangle.NO_BORDER); ic.setHorizontalAlignment(Element.ALIGN_RIGHT); ic.setPadding(2);
        dateTable.addCell(ic);
        doc.add(dateTable);

        // 宛名
        String personName = person != null
            ? person.getLastNameKanji() + " " + person.getFirstNameKanji() : "　　　　　　　";
        Paragraph nameP = new Paragraph(personName + "　様", boldFont);
        nameP.setSpacingBefore(6); nameP.setSpacingAfter(4);
        doc.add(nameP);

        doc.add(new Paragraph("下記の受領証正に領収いたしました。", normalFont));
        doc.add(new Paragraph(" ", smallFont));

        // メインテーブル（就労先・受領番号なし）
        PdfPTable mainTable = new PdfPTable(new float[]{3, 1, 2});
        mainTable.setWidthPercentage(100);
        for (String h : new String[]{"求職受付手数料", "1件", "領 収 番 号"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, boldFont));
            c.setPadding(5); c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setBackgroundColor(new BaseColor(220, 220, 220));
            mainTable.addCell(c);
        }
        int receptionFee = detail.getReceptionFee() != null ? detail.getReceptionFee() : 710;
        PdfPCell fd = new PdfPCell(new Phrase("求職受付手数料", normalFont));
        fd.setPadding(5); fd.setHorizontalAlignment(Element.ALIGN_CENTER); mainTable.addCell(fd);
        PdfPCell fc = new PdfPCell(new Phrase(String.format("%,d 円", receptionFee), boldFont));
        fc.setPadding(5); fc.setHorizontalAlignment(Element.ALIGN_CENTER); mainTable.addCell(fc);
        PdfPCell fn = new PdfPCell(new Phrase(String.format("%04d", receiptNumber), boldFont));
        fn.setPadding(5); fn.setHorizontalAlignment(Element.ALIGN_CENTER); mainTable.addCell(fn);
        doc.add(mainTable);

        // 会社情報
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable companyTable = new PdfPTable(1);
        companyTable.setWidthPercentage(60);
        companyTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (String line : new String[]{
                "有限会社　ワークオフィス谷",
                "〒107-0052　東京都港区赤坂6-10-45-203",
                "TEL 03-5544-8315　FAX 03-5544-8316"}) {
            PdfPCell c = new PdfPCell(new Phrase(line, smallFont));
            c.setBorder(Rectangle.NO_BORDER); c.setPadding(2); companyTable.addCell(c);
        }
        doc.add(companyTable);
        doc.add(new Paragraph(" ", smallFont));
        doc.add(new Paragraph("※受付会員登録にお申込み1回についていただいております。", smallFont));
        doc.add(new Paragraph("※お申込み1回につき3回までにいただいております。3回分の受領手数料", smallFont));
        doc.close();
    }

    // ─── PDF ヘルパー ──────────────────────────────────
    private PdfPCell cell(String text, Font f) {
        return new PdfPCell(new Phrase(text, f));
    }

    private void addWageHeader(PdfPTable t, String workerName, Font f) {
        PdfPCell c1 = new PdfPCell(new Phrase("勤務者氏名", f));
        c1.setColspan(2); c1.setPadding(4); c1.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(c1);
        PdfPCell c2 = new PdfPCell(new Phrase(workerName, f));
        c2.setColspan(3); c2.setPadding(4); c2.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(c2);
        PdfPCell h1 = new PdfPCell(new Phrase("賃", f));
        h1.setRowspan(7); h1.setPadding(4);
        h1.setHorizontalAlignment(Element.ALIGN_CENTER); h1.setVerticalAlignment(Element.ALIGN_MIDDLE); t.addCell(h1);
        PdfPCell h2 = new PdfPCell(new Phrase("就 労 期 間", f));
        h2.setColspan(2); h2.setPadding(4); h2.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(h2);
        PdfPCell h3 = new PdfPCell(new Phrase("から", f));
        h3.setPadding(4); h3.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(h3);
        PdfPCell h4 = new PdfPCell(new Phrase("まで　　　日間", f));
        h4.setPadding(4); t.addCell(h4);
    }

    private void addWageRow(PdfPTable t, String label, String unit, String unitAmt, String qty, String total, Font f) {
        for (Phrase[] ph : new Phrase[][]{{new Phrase(label, f)},{new Phrase(unit, f)},
                {new Phrase(unitAmt, f)},{new Phrase(qty, f)},{new Phrase(total, f)}}) {
            PdfPCell c = new PdfPCell(ph[0]); c.setPadding(4); t.addCell(c);
        }
    }

    private void addEmptyWageRow(PdfPTable t, String label, Font f) {
        for (int i = 0; i < 4; i++) {
            PdfPCell c = new PdfPCell(new Phrase(i == 0 ? label : "　", f)); c.setPadding(4); t.addCell(c);
        }
    }

    private void addFeeRow(PdfPTable t, String left, String desc, String num, String amt,
                            Font bf, Font nf, boolean firstRow) {
        if (firstRow) {
            PdfPCell c1 = new PdfPCell(new Phrase(left, bf));
            c1.setPadding(4); c1.setHorizontalAlignment(Element.ALIGN_CENTER);
            c1.setVerticalAlignment(Element.ALIGN_MIDDLE); c1.setRowspan(4); t.addCell(c1);
        }
        PdfPCell c2 = new PdfPCell(new Phrase(desc, nf)); c2.setPadding(4); t.addCell(c2);
        PdfPCell c3 = new PdfPCell(new Phrase(num, bf));
        c3.setPadding(4); c3.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(c3);
        PdfPCell c4 = new PdfPCell(new Phrase(amt, nf));
        c4.setPadding(4); c4.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(c4);
    }

    // ─── 内部クラス ────────────────────────────────────
    public static class JobseekerReceiptItem {
        public Person     person;
        public SalesDetail detail;
        public Long       salesId;
        public boolean    issued;
        public int        receiptNumber;
    }

    public static class ReceiptItem {
        public Customer    customer;
        public SalesDetail detail;
        public Long        salesId;
        public Long        personId;
        public boolean     issued;
        public int         receiptNumber;
    }

    public static class IssuedListRow {
        public int           receiptNumber;
        public String        receiptType;
        public String        partyName;
        public int           amount;
        public String        amountStr;
        public LocalDateTime issuedAt;
        public Long          salesDetailId;
    }
}
