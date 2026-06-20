package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

        Iterable<Customer> customers = customerRepository.findAll();
        List<ReceiptItem> items = new ArrayList<>();

        for (Customer c : customers) {
            Iterable<Sales> allSales = salesRepository.findAll();
            for (Sales s : allSales) {
                List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
                for (SalesDetail d : details) {
                    if (d.getCustomerId() != null && d.getCustomerId().equals(c.getId())
                            && d.getCustomerFee() != null && d.getCustomerFee() > 0) {
                        ReceiptItem item = new ReceiptItem();
                        item.customer = c;
                        item.detail = d;
                        item.salesId = s.getId();
                        item.personId = s.getPersonId();
                        // 既発行チェック
                        receiptsIssuedRepository.findBySalesDetailId(d.getId())
                            .ifPresent(ri -> {
                                item.issued = true;
                                item.receiptNumber = ri.getReceiptNumber();
                            });
                        items.add(item);
                    }
                }
            }
        }

        model.addAttribute("items", items);
        return "receipt-customer-list";
    }

    // ─── 1-7-1 PDF出力（発行時に自動採番して receipts_issued に保存）────
    @GetMapping("/customer-receipt/pdf")
    public void customerReceiptPdf(
            @RequestParam Long detailId,
            HttpSession session,
            HttpServletResponse response) throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        SalesDetail detail = salesDetailRepository.findById(detailId).orElse(null);
        if (detail == null) { response.sendError(404); return; }

        Customer customer = detail.getCustomerId() != null
                ? customerRepository.findById(detail.getCustomerId()).orElse(null) : null;

        Sales sales = detail.getSalesId() != null
                ? salesRepository.findById(detail.getSalesId()).orElse(null) : null;

        Person person = (sales != null && sales.getPersonId() != null)
                ? personRepository.findById(sales.getPersonId()).orElse(null) : null;

        // 領収番号を採番（未発行なら新番号、発行済みなら既存番号を使用）
        int receiptNumber;
        ReceiptsIssued existing = receiptsIssuedRepository.findBySalesDetailId(detailId).orElse(null);
        if (existing != null) {
            receiptNumber = existing.getReceiptNumber() != null ? existing.getReceiptNumber() : existing.getId().intValue();
        } else {
            receiptNumber = receiptsIssuedRepository.findMaxReceiptNumber() + 1;
            // 発行記録を保存
            ReceiptsIssued ri = new ReceiptsIssued();
            ri.setSalesDetailId(detailId);
            ri.setCustomerId(customer != null ? customer.getId() : null);
            ri.setReceiptType("CUSTOMER");
            int totalWage = detail.getMonthlyTotal() != null ? detail.getMonthlyTotal() : 0;
            int customerFee = detail.getCustomerFee() != null ? detail.getCustomerFee() : 1000;
            int commission = (int)(totalWage * 0.15);
            ri.setAmount(customerFee + commission);
            ri.setReceiptNumber(receiptNumber);
            ri.setPrinted(true);
            ri.setPrintedAt(LocalDateTime.now());
            ri.setCreatedAt(LocalDateTime.now());
            receiptsIssuedRepository.save(ri);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createCustomerReceiptPdf(detail, customer, person, receiptNumber, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=customer-receipt.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

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

        // ── 右上：領収番号（タイトルより先に配置）──
        PdfPTable topRight = new PdfPTable(1);
        topRight.setWidthPercentage(35);
        topRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell noCell = new PdfPCell(new Phrase(
            String.format("領収番号　%04d", receiptNumber), boldFont));
        noCell.setBorder(Rectangle.BOX);
        noCell.setPadding(5);
        noCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        topRight.addCell(noCell);
        doc.add(topRight);

        // ── タイトル ──
        Paragraph title = new Paragraph("求 人 者 宛 領 収 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(8);
        title.setSpacingAfter(6);
        doc.add(title);

        // ── 受付月日・年月日（右寄せ・領収番号の下） ──
        LocalDate introDate = detail.getIntroductionDate();
        String receptDateStr = introDate != null
            ? String.format("受付月日　%d月　%d日", introDate.getMonthValue(), introDate.getDayOfMonth())
            : "受付月日　　月　　日";
        LocalDate today = LocalDate.now();
        String issueDateStr = String.format("%d年　%d月　%d日",
            today.getYear(), today.getMonthValue(), today.getDayOfMonth());

        PdfPTable dateTable = new PdfPTable(1);
        dateTable.setWidthPercentage(40);
        dateTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell receptCell = new PdfPCell(new Phrase(receptDateStr, normalFont));
        receptCell.setBorder(Rectangle.NO_BORDER);
        receptCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        receptCell.setPadding(2);
        dateTable.addCell(receptCell);
        PdfPCell issueDateCell = new PdfPCell(new Phrase(issueDateStr, normalFont));
        issueDateCell.setBorder(Rectangle.NO_BORDER);
        issueDateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        issueDateCell.setPadding(2);
        dateTable.addCell(issueDateCell);
        doc.add(dateTable);

        // ── 宛名 ──
        String customerName = customer != null
                ? customer.getLastNameKanji() + " " + customer.getFirstNameKanji()
                : "　　　　　";
        Paragraph nameP = new Paragraph(customerName + "　様", largeFont);
        nameP.setSpacingBefore(10);
        nameP.setSpacingAfter(4);
        doc.add(nameP);

        // ── 合計金額 ──
        int totalWage    = detail.getMonthlyTotal() != null ? detail.getMonthlyTotal() : 0;
        int customerFee  = detail.getCustomerFee()  != null ? detail.getCustomerFee()  : 1000;
        int commission   = (int)(totalWage * 0.15);
        int total        = customerFee + commission;

        Paragraph amountP = new Paragraph(
            "（②+③）一金　　" + String.format("%,d", total) + " 円也", largeFont);
        amountP.setSpacingAfter(4);
        doc.add(amountP);

        Paragraph desc = new Paragraph(
            "職業紹介手数料として上記金額を正に領収しました。", normalFont);
        desc.setSpacingAfter(10);
        doc.add(desc);

        // ── 右側：会社情報 ──
        PdfPTable companyTable = new PdfPTable(1);
        companyTable.setWidthPercentage(45);
        companyTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        String[] companyLines = {
            "厚生労働省許可　13-ユ-040077",
            "有限会社　ワークオフィス谷",
            "〒107-0052",
            "東京都港区赤坂6-10-45-203",
            "TEL 03-5544-8315　FAX 03-5544-8316"
        };
        for (String line : companyLines) {
            PdfPCell c = new PdfPCell(new Phrase(line, smallFont));
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(1);
            companyTable.addCell(c);
        }
        doc.add(companyTable);

        // ── 賃金算定テーブル ──
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable wageTable = new PdfPTable(new float[]{2, 2, 4, 3, 3});
        wageTable.setWidthPercentage(100);
        wageTable.setSpacingBefore(5);

        String workerName = person != null
                ? person.getLastNameKanji() + " " + person.getFirstNameKanji() : "";
        addWageHeader(wageTable, workerName, boldFont);

        // 日給行
        String dailyWages = detail.getDailyWages();
        int dailyWageUnit = 0, dailyDays = 0, dailyTotal = 0;
        if (dailyWages != null && !dailyWages.isBlank()) {
            String[] wages = dailyWages.split(",");
            dailyDays = wages.length;
            for (String w : wages) {
                try { dailyTotal += Integer.parseInt(w.trim()); } catch (NumberFormatException ignored) {}
            }
            if (dailyDays > 0) dailyWageUnit = dailyTotal / dailyDays;
        }
        addWageRow(wageTable, "日給", "1日",
            dailyWageUnit > 0 ? String.format("① %,d円", dailyWageUnit) : "①　　　円",
            dailyDays > 0 ? String.format("② %d日間", dailyDays) : "②　　日間",
            dailyTotal > 0 ? String.format("%,d 円", dailyTotal) : "　　　　0 円", normalFont);

        int hw   = detail.getHourlyWage()     != null ? detail.getHourlyWage()     : 0;
        double wh = detail.getWorkingHours()  != null ? detail.getWorkingHours().doubleValue() : 0;
        int hwTotal = (int)(hw * wh);
        addWageRow(wageTable, "時間給", "1時間",
            hw > 0 ? String.format("%,d円", hw) : "　　　円",
            wh > 0 ? String.format("%.0f 時間", wh) : "　　時間",
            hwTotal > 0 ? String.format("%,d 円", hwTotal) : "　　　　0 円", normalFont);

        int hwo = detail.getHourlyWageOvertime() != null ? detail.getHourlyWageOvertime() : 0;
        addWageRow(wageTable, "", "1時間",
            hwo > 0 ? String.format("%,d円", hwo) : "　　　円",
            "　　時間", "　　　　0 円", normalFont);

        addWageRow(wageTable, "手", "1時間", "　　　円", "　　時間", "　0 円", normalFont);
        addEmptyWageRow(wageTable, "当", normalFont);
        addEmptyWageRow(wageTable, "　", normalFont);

        PdfPCell totalLabelCell = new PdfPCell(new Phrase("賃 金 総 額", boldFont));
        totalLabelCell.setColspan(3); totalLabelCell.setPadding(5);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        wageTable.addCell(totalLabelCell);
        PdfPCell totalNumCell = new PdfPCell(new Phrase("①", boldFont));
        totalNumCell.setPadding(5); totalNumCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        wageTable.addCell(totalNumCell);
        PdfPCell totalAmtCell = new PdfPCell(new Phrase(String.format("%,d 円", totalWage), boldFont));
        totalAmtCell.setPadding(5); totalAmtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        wageTable.addCell(totalAmtCell);
        doc.add(wageTable);

        // ── 領収金額算定テーブル ──
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable feeTable = new PdfPTable(new float[]{1, 5, 1, 3});
        feeTable.setWidthPercentage(100);
        feeTable.setSpacingBefore(5);

        addFeeRow(feeTable, "領\n収\n金\n額\n算\n定",
            "求職受付手数料（求人1件につき1回）",
            "②", String.format("%,d 円", customerFee), boldFont, normalFont, true);
        addFeeRow(feeTable, "",
            "紹介手数料（①×15% ※円未満切り捨て）",
            "③", String.format("%,d 円", commission), boldFont, normalFont, false);
        addFeeRow(feeTable, "",
            "消費税（10%）",
            "", "", boldFont, normalFont, false);

        PdfPCell lbl1 = new PdfPCell(new Phrase("", normalFont));
        lbl1.setBorder(Rectangle.BOX); lbl1.setPadding(4); feeTable.addCell(lbl1);
        PdfPCell lbl2 = new PdfPCell(new Phrase("手数料合計　（②+③）", boldFont));
        lbl2.setBorder(Rectangle.BOX); lbl2.setPadding(4); feeTable.addCell(lbl2);
        PdfPCell lbl3 = new PdfPCell(new Phrase("", normalFont));
        lbl3.setBorder(Rectangle.BOX); lbl3.setPadding(4); feeTable.addCell(lbl3);
        PdfPCell lbl4 = new PdfPCell(new Phrase(String.format("%,d 円", total), boldFont));
        lbl4.setBorder(Rectangle.BOX); lbl4.setPadding(4);
        lbl4.setHorizontalAlignment(Element.ALIGN_RIGHT); feeTable.addCell(lbl4);

        doc.add(feeTable);
        doc.close();
    }

    // ─── 1-7-2 求職受付手数料領収書一覧 ─────────────────
    @GetMapping("/jobseeker-receipt")
    public String jobseekerReceipt(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        List<JobseekerReceiptItem> items = new ArrayList<>();
        Iterable<Sales> allSales = salesRepository.findAll();
        for (Sales s : allSales) {
            Person person = personRepository.findById(s.getPersonId()).orElse(null);
            if (person == null) continue;
            List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
            for (SalesDetail d : details) {
                if (d.getReceptionFee() != null && d.getReceptionFee() > 0) {
                    JobseekerReceiptItem item = new JobseekerReceiptItem();
                    item.person = person;
                    item.detail = d;
                    item.salesId = s.getId();
                    receiptsIssuedRepository.findBySalesDetailId(d.getId())
                        .ifPresent(ri -> {
                            item.issued = true;
                            item.receiptNumber = ri.getReceiptNumber();
                        });
                    items.add(item);
                }
            }
        }
        model.addAttribute("items", items);
        return "receipt-jobseeker-list";
    }

    // ─── 1-7-2 PDF出力（発行時に自動採番）───────────────
    @GetMapping("/jobseeker-receipt/pdf")
    public void jobseekerReceiptPdf(
            @RequestParam Long detailId,
            HttpSession session,
            HttpServletResponse response) throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        SalesDetail detail = salesDetailRepository.findById(detailId).orElse(null);
        if (detail == null) { response.sendError(404); return; }

        Sales sales = detail.getSalesId() != null
                ? salesRepository.findById(detail.getSalesId()).orElse(null) : null;
        Person person = (sales != null && sales.getPersonId() != null)
                ? personRepository.findById(sales.getPersonId()).orElse(null) : null;

        // 領収番号を採番（未発行なら新番号）
        int receiptNumber;
        ReceiptsIssued existing = receiptsIssuedRepository.findBySalesDetailId(detailId).orElse(null);
        if (existing != null) {
            receiptNumber = existing.getReceiptNumber() != null ? existing.getReceiptNumber() : existing.getId().intValue();
        } else {
            receiptNumber = receiptsIssuedRepository.findMaxReceiptNumber() + 1;
            ReceiptsIssued ri = new ReceiptsIssued();
            ri.setSalesDetailId(detailId);
            ri.setPersonId(person != null ? person.getId() : null);
            ri.setReceiptType("JOBSEEKER");
            ri.setAmount(detail.getReceptionFee());
            ri.setReceiptNumber(receiptNumber);
            ri.setPrinted(true);
            ri.setPrintedAt(LocalDateTime.now());
            ri.setCreatedAt(LocalDateTime.now());
            receiptsIssuedRepository.save(ri);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createJobseekerReceiptPdf(detail, person, receiptNumber, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=jobseeker-receipt.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

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

        // ── 右上：領収番号 ──
        PdfPTable topRight = new PdfPTable(1);
        topRight.setWidthPercentage(35);
        topRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell noCell = new PdfPCell(new Phrase(
            String.format("領収番号　%04d", receiptNumber), boldFont));
        noCell.setBorder(Rectangle.BOX);
        noCell.setPadding(4);
        noCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        topRight.addCell(noCell);
        doc.add(topRight);

        // ── タイトル ──
        Paragraph title = new Paragraph("求 職 受 付 手 数 料 領 収 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(6);
        title.setSpacingAfter(6);
        doc.add(title);

        // ── 受付月日・年月日（右寄せ）──
        LocalDate introDate = detail.getIntroductionDate();
        String receptDateStr = introDate != null
            ? String.format("受付月日　%d月　%d日", introDate.getMonthValue(), introDate.getDayOfMonth())
            : "受付月日　　月　　日";
        LocalDate today = LocalDate.now();
        String issueDateStr = String.format("%d年　%d月　%d日",
            today.getYear(), today.getMonthValue(), today.getDayOfMonth());

        PdfPTable dateTable = new PdfPTable(1);
        dateTable.setWidthPercentage(50);
        dateTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell receptCell = new PdfPCell(new Phrase(receptDateStr, normalFont));
        receptCell.setBorder(Rectangle.NO_BORDER);
        receptCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        receptCell.setPadding(2);
        dateTable.addCell(receptCell);
        PdfPCell issueDateCell = new PdfPCell(new Phrase(issueDateStr, normalFont));
        issueDateCell.setBorder(Rectangle.NO_BORDER);
        issueDateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        issueDateCell.setPadding(2);
        dateTable.addCell(issueDateCell);
        doc.add(dateTable);

        // ── 宛名 ──
        String personName = person != null
                ? person.getLastNameKanji() + " " + person.getFirstNameKanji()
                : "　　　　　　　";
        Paragraph nameP = new Paragraph(personName + "　様", boldFont);
        nameP.setSpacingBefore(6);
        nameP.setSpacingAfter(4);
        doc.add(nameP);

        Paragraph desc = new Paragraph("下記の受領証正に領収いたしました。", normalFont);
        desc.setSpacingAfter(8);
        doc.add(desc);

        // ── メインテーブル（就労先列を削除）──
        PdfPTable mainTable = new PdfPTable(new float[]{2, 1, 2});
        mainTable.setWidthPercentage(100);

        String[] headers = {"求職受付手数料", "1件", "領 収 番 号"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, boldFont));
            c.setPadding(5); c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setBackgroundColor(new BaseColor(220, 220, 220));
            mainTable.addCell(c);
        }

        PdfPCell feeDescCell = new PdfPCell(new Phrase("求職受付手数料", normalFont));
        feeDescCell.setPadding(5); feeDescCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        mainTable.addCell(feeDescCell);

        int receptionFee = detail.getReceptionFee() != null ? detail.getReceptionFee() : 710;
        PdfPCell feeCell = new PdfPCell(new Phrase(String.format("%,d 円", receptionFee), boldFont));
        feeCell.setPadding(5); feeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        mainTable.addCell(feeCell);

        PdfPCell receiptNoCell = new PdfPCell(new Phrase(String.format("%04d", receiptNumber), boldFont));
        receiptNoCell.setPadding(5); receiptNoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        mainTable.addCell(receiptNoCell);

        doc.add(mainTable);

        // ── 会社情報 ──
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable companyTable = new PdfPTable(1);
        companyTable.setWidthPercentage(60);
        companyTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        String[] companyLines = {
            "有限会社　ワークオフィス谷",
            "〒107-0052　東京都港区赤坂6-10-45-203",
            "TEL 03-5544-8315　FAX 03-5544-8316"
        };
        for (String line : companyLines) {
            PdfPCell c = new PdfPCell(new Phrase(line, smallFont));
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(2);
            companyTable.addCell(c);
        }
        doc.add(companyTable);

        doc.add(new Paragraph(" ", smallFont));
        doc.add(new Paragraph("※受付会員登録にお申込み1回についていただいております。", smallFont));
        doc.add(new Paragraph("※お申込み1回につき3回までにいただいております。3回分の受領手数料", smallFont));

        doc.close();
    }

    // ─── 1-7-3 発行済み領収書一覧（月単位）─────────────
    @GetMapping("/issued-list")
    public String issuedList(@RequestParam(required = false) String month,
                              HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        // monthなしなら現在月にリダイレクト
        if (month == null || month.isBlank()) {
            String currentMonth = LocalDateTime.now().getYear() + "-"
                + String.format("%02d", LocalDateTime.now().getMonthValue());
            return "redirect:/receipt-menu/issued-list?month=" + currentMonth;
        }

        List<ReceiptsIssued> rawList = receiptsIssuedRepository.findByMonth(month);
        List<IssuedListRow> rows = new ArrayList<>();

        for (ReceiptsIssued ri : rawList) {
            IssuedListRow row = new IssuedListRow();
            row.receiptNumber = ri.getReceiptNumber();
            row.receiptType   = "CUSTOMER".equals(ri.getReceiptType()) ? "1-7-1 求人者宛" : "1-7-2 求職受付";
            row.amount        = ri.getAmount() != null ? ri.getAmount() : 0;
            row.amountStr     = "¥" + String.format("%,d", row.amount);
            row.issuedAt      = ri.getPrintedAt() != null ? ri.getPrintedAt() : ri.getCreatedAt();
            row.salesDetailId = ri.getSalesDetailId();

            // 相手先名を解決
            if ("CUSTOMER".equals(ri.getReceiptType()) && ri.getCustomerId() != null) {
                customerRepository.findById(ri.getCustomerId()).ifPresent(c ->
                    row.partyName = c.getLastNameKanji() + " " + c.getFirstNameKanji());
            } else if ("JOBSEEKER".equals(ri.getReceiptType()) && ri.getPersonId() != null) {
                personRepository.findById(ri.getPersonId()).ifPresent(p ->
                    row.partyName = p.getLastNameKanji() + " " + p.getFirstNameKanji());
            }
            if (row.partyName == null) row.partyName = "－";

            rows.add(row);
        }

        long totalAmount = rows.stream().mapToLong(r -> r.amount).sum();
        model.addAttribute("rows", rows);
        model.addAttribute("totalAmountStr", "¥" + String.format("%,d", totalAmount));
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("selectedMonth", month);
        return "receipt-issued-list";
    }

    // ─── PDF ヘルパー ──────────────────────────────────
    private void addNoBorderRow(PdfPTable t, String key, String val, Font kf, Font vf) {
        PdfPCell k = new PdfPCell(new Phrase(key, kf));
        k.setBorder(Rectangle.NO_BORDER); k.setPadding(2); t.addCell(k);
        PdfPCell v = new PdfPCell(new Phrase(val, vf));
        v.setBorder(Rectangle.NO_BORDER); v.setPadding(2); t.addCell(v);
    }

    private void addWageHeader(PdfPTable t, String workerName, Font f) {
        PdfPCell c1 = new PdfPCell(new Phrase("勤務者氏名", f));
        c1.setColspan(2); c1.setPadding(4); c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c1);
        PdfPCell c2 = new PdfPCell(new Phrase(workerName, f));
        c2.setColspan(3); c2.setPadding(4); c2.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c2);
        PdfPCell h1 = new PdfPCell(new Phrase("賃", f));
        h1.setRowspan(7); h1.setPadding(4);
        h1.setHorizontalAlignment(Element.ALIGN_CENTER); h1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(h1);
        PdfPCell h2 = new PdfPCell(new Phrase("就 労 期 間", f));
        h2.setColspan(2); h2.setPadding(4); h2.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(h2);
        PdfPCell h3 = new PdfPCell(new Phrase("から", f));
        h3.setPadding(4); h3.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(h3);
        PdfPCell h4 = new PdfPCell(new Phrase("まで　　　日間", f));
        h4.setPadding(4); t.addCell(h4);
    }

    private void addWageRow(PdfPTable t, String rowLabel, String unit, String unitAmt,
                             String qty, String total, Font f) {
        PdfPCell c1 = new PdfPCell(new Phrase(rowLabel, f));
        c1.setPadding(4); c1.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(c1);
        PdfPCell c2 = new PdfPCell(new Phrase(unit, f));
        c2.setPadding(4); c2.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(c2);
        PdfPCell c3 = new PdfPCell(new Phrase(unitAmt, f));
        c3.setPadding(4); c3.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(c3);
        PdfPCell c4 = new PdfPCell(new Phrase(qty, f));
        c4.setPadding(4); c4.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(c4);
        PdfPCell c5 = new PdfPCell(new Phrase(total, f));
        c5.setPadding(4); c5.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(c5);
    }

    private void addEmptyWageRow(PdfPTable t, String label, Font f) {
        for (int i = 0; i < 4; i++) {
            PdfPCell c = new PdfPCell(new Phrase(i == 0 ? label : "　", f));
            c.setPadding(4); t.addCell(c);
        }
    }

    private void addFeeRow(PdfPTable t, String left, String desc, String num, String amt,
                            Font bf, Font nf, boolean firstRow) {
        if (firstRow) {
            PdfPCell c1 = new PdfPCell(new Phrase(left, bf));
            c1.setPadding(4); c1.setHorizontalAlignment(Element.ALIGN_CENTER);
            c1.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c1.setRowspan(4);
            t.addCell(c1);
        }
        PdfPCell c2 = new PdfPCell(new Phrase(desc, nf));
        c2.setPadding(4); t.addCell(c2);
        PdfPCell c3 = new PdfPCell(new Phrase(num, bf));
        c3.setPadding(4); c3.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(c3);
        PdfPCell c4 = new PdfPCell(new Phrase(amt, nf));
        c4.setPadding(4); c4.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(c4);
    }

    // ─── 内部クラス ────────────────────────────────────
    public static class JobseekerReceiptItem {
        public Person person;
        public SalesDetail detail;
        public Long salesId;
        public boolean issued;
        public Integer receiptNumber;
    }

    public static class ReceiptItem {
        public Customer customer;
        public SalesDetail detail;
        public Long salesId;
        public Long personId;
        public boolean issued;
        public Integer receiptNumber;
    }

    public static class IssuedListRow {
        public Integer receiptNumber;
        public String  receiptType;
        public String  partyName;
        public int     amount;
        public String  amountStr;
        public LocalDateTime issuedAt;
        public Long    salesDetailId;
    }
}
