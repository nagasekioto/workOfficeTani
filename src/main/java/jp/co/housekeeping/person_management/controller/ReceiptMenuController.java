package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
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
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/receipt-menu")
public class ReceiptMenuController {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private PersonRepository personRepository;

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
                        items.add(item);
                    }
                }
            }
        }

        model.addAttribute("items", items);
        return "receipt-customer-list";
    }

    @PostMapping("/customer-receipt/mark-printed")
    public String markPrinted(@RequestParam Long detailId, HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "redirect:/receipt-menu/customer-receipt?printed=" + detailId;
    }

    // ─── 1-7-1 求人受付・紹介手数料領収証書 PDF出力 ────────
    @GetMapping("/customer-receipt/pdf")
    public void customerReceiptPdf(
            @RequestParam Long detailId,
            HttpSession session,
            HttpServletResponse response) throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) {
            response.sendError(401);
            return;
        }

        SalesDetail detail = salesDetailRepository.findById(detailId).orElse(null);
        if (detail == null) { response.sendError(404); return; }

        Customer customer = detail.getCustomerId() != null
                ? customerRepository.findById(detail.getCustomerId()).orElse(null) : null;

        Sales sales = detail.getSalesId() != null
                ? salesRepository.findById(detail.getSalesId()).orElse(null) : null;

        Person person = (sales != null && sales.getPersonId() != null)
                ? personRepository.findById(sales.getPersonId()).orElse(null) : null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createCustomerReceiptPdf(detail, customer, person, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=customer-receipt.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    private void createCustomerReceiptPdf(SalesDetail detail, Customer customer, Person person,
                                           ByteArrayOutputStream baos) throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont  = new Font(bf, 16, Font.BOLD);
        Font boldFont   = new Font(bf, 10, Font.BOLD);
        Font normalFont = new Font(bf, 10);
        Font smallFont  = new Font(bf, 8);
        Font largeFont  = new Font(bf, 13, Font.BOLD);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
        String today = LocalDate.now().format(dtf);

        // ── タイトル ──
        Paragraph title = new Paragraph("求人受付・紹介手数料領収証書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        doc.add(title);

        // ── 右上：領収番号・領収日 ──
        String receiptNo = detail.getReceiptNo() != null ? detail.getReceiptNo() : "";
        PdfPTable headerRight = new PdfPTable(2);
        headerRight.setWidthPercentage(50);
        headerRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        headerRight.setWidths(new float[]{4, 3});
        addNoBorderRow(headerRight, "領収番号", receiptNo, boldFont, normalFont);
        addNoBorderRow(headerRight, "領収日", today, boldFont, normalFont);
        doc.add(headerRight);

        // ── 宛名 ──
        String customerName = customer != null
                ? customer.getLastNameKanji() + " " + customer.getFirstNameKanji()
                : "　　　　　";
        Paragraph nameP = new Paragraph(customerName + "　様", largeFont);
        nameP.setSpacingBefore(12);
        nameP.setSpacingAfter(4);
        doc.add(nameP);

        // 合計金額
        int totalWage = detail.getMonthlyTotal() != null ? detail.getMonthlyTotal() : 0;
        int customerFee = detail.getCustomerFee() != null ? detail.getCustomerFee() : 1000;
        int commission = (int)(totalWage * 0.15);
        int total = customerFee + commission;

        Paragraph amountP = new Paragraph("（②+③）一金　　" + String.format("%,d", total) + " 円也", largeFont);
        amountP.setSpacingAfter(4);
        doc.add(amountP);

        Paragraph desc = new Paragraph("職業紹介手数料として上記金額を正に領収しました。", normalFont);
        desc.setSpacingAfter(10);
        doc.add(desc);

        // 右側：会社情報
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

        // ヘッダー行
        String workerName = person != null
                ? person.getLastNameKanji() + " " + person.getFirstNameKanji() : "";
        addWageHeader(wageTable, workerName, boldFont);

        // 日給行
        String dailyWages = detail.getDailyWages();
        int dailyWageUnit = 0;
        int dailyDays = 0;
        int dailyTotal = 0;
        if (dailyWages != null && !dailyWages.isBlank()) {
            String[] wages = dailyWages.split(",");
            dailyDays = wages.length;
            for (String w : wages) {
                try { dailyTotal += Integer.parseInt(w.trim()); } catch (NumberFormatException ignored) {}
            }
            if (dailyDays > 0) dailyWageUnit = dailyTotal / dailyDays;
        }
        addWageRow(wageTable, "日給", "1日", dailyWageUnit > 0 ? String.format("① %,d円", dailyWageUnit) : "①　　　円",
                dailyDays > 0 ? String.format("② %d日間", dailyDays) : "②　　日間",
                dailyTotal > 0 ? String.format("%,d 円", dailyTotal) : "　　　　0 円", normalFont);

        // 時間給行
        int hw = detail.getHourlyWage() != null ? detail.getHourlyWage() : 0;
        double wh = detail.getWorkingHours() != null ? detail.getWorkingHours().doubleValue() : 0;
        int hwTotal = (int)(hw * wh);
        addWageRow(wageTable, "時間給", "1時間", hw > 0 ? String.format("%,d円", hw) : "　　　円",
                wh > 0 ? String.format("%.0f 時間", wh) : "　　時間",
                hwTotal > 0 ? String.format("%,d 円", hwTotal) : "　　　　0 円", normalFont);

        // 時間給（残業）
        int hwo = detail.getHourlyWageOvertime() != null ? detail.getHourlyWageOvertime() : 0;
        addWageRow(wageTable, "", "1時間", hwo > 0 ? String.format("%,d円", hwo) : "　　　円",
                "　　時間", "　　　　0 円", normalFont);

        // 手当（空行）
        addWageRow(wageTable, "手", "1時間", "　　　円", "　　時間", "　0 円", normalFont);
        addEmptyWageRow(wageTable, "当", normalFont);
        addEmptyWageRow(wageTable, "　", normalFont);

        // 賃金総額
        PdfPCell totalLabelCell = new PdfPCell(new Phrase("賃 金 総 額", boldFont));
        totalLabelCell.setColspan(3);
        totalLabelCell.setPadding(5);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        wageTable.addCell(totalLabelCell);
        PdfPCell totalNumCell = new PdfPCell(new Phrase("①", boldFont));
        totalNumCell.setPadding(5);
        totalNumCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        wageTable.addCell(totalNumCell);
        PdfPCell totalAmtCell = new PdfPCell(new Phrase(String.format("%,d 円", totalWage), boldFont));
        totalAmtCell.setPadding(5);
        totalAmtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        wageTable.addCell(totalAmtCell);

        doc.add(wageTable);

        // ── 領収金額算定テーブル ──
        doc.add(new Paragraph(" ", smallFont));

        PdfPTable feeTable = new PdfPTable(new float[]{1, 5, 1, 3});
        feeTable.setWidthPercentage(100);
        feeTable.setSpacingBefore(5);

        // ヘッダーセル（左列：「領収金額算定」縦書き風）
        addFeeRow(feeTable, "領\n収\n金\n額\n算\n定",
                "求人受付手数料（求人1件につき1回）",
                "②", String.format("%,d 円", customerFee), boldFont, normalFont, true);
        addFeeRow(feeTable, "",
                "紹介手数料（①×15% ※円未満切り捨て）",
                "③", String.format("%,d 円", commission), boldFont, normalFont, false);
        addFeeRow(feeTable, "",
                "消費税（10%）",
                "", "", boldFont, normalFont, false);
        // 手数料合計
        PdfPCell lbl1 = new PdfPCell(new Phrase("", normalFont));
        lbl1.setBorder(Rectangle.BOX);
        lbl1.setPadding(4);
        feeTable.addCell(lbl1);
        PdfPCell lbl2 = new PdfPCell(new Phrase("手数料合計　（②+③）", boldFont));
        lbl2.setBorder(Rectangle.BOX);
        lbl2.setPadding(4);
        feeTable.addCell(lbl2);
        PdfPCell lbl3 = new PdfPCell(new Phrase("", normalFont));
        lbl3.setBorder(Rectangle.BOX);
        lbl3.setPadding(4);
        feeTable.addCell(lbl3);
        PdfPCell lbl4 = new PdfPCell(new Phrase(String.format("%,d 円", total), boldFont));
        lbl4.setBorder(Rectangle.BOX);
        lbl4.setPadding(4);
        lbl4.setHorizontalAlignment(Element.ALIGN_RIGHT);
        feeTable.addCell(lbl4);

        doc.add(feeTable);

        doc.close();
    }

    private void addNoBorderRow(PdfPTable t, String key, String val, Font kf, Font vf) {
        PdfPCell k = new PdfPCell(new Phrase(key, kf));
        k.setBorder(Rectangle.NO_BORDER); k.setPadding(2);
        t.addCell(k);
        PdfPCell v = new PdfPCell(new Phrase(val, vf));
        v.setBorder(Rectangle.NO_BORDER); v.setPadding(2);
        t.addCell(v);
    }

    private void addWageHeader(PdfPTable t, String workerName, Font f) {
        PdfPCell c1 = new PdfPCell(new Phrase("勤務者氏名", f));
        c1.setColspan(2); c1.setPadding(4); c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c1);
        PdfPCell c2 = new PdfPCell(new Phrase(workerName, f));
        c2.setColspan(3); c2.setPadding(4); c2.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c2);

        // 2行目：就労期間ヘッダー
        PdfPCell h1 = new PdfPCell(new Phrase("賃", f));
        h1.setRowspan(7); h1.setPadding(4); h1.setHorizontalAlignment(Element.ALIGN_CENTER);
        h1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(h1);
        PdfPCell h2 = new PdfPCell(new Phrase("就 労 期 間", f));
        h2.setColspan(2); h2.setPadding(4); h2.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(h2);
        PdfPCell h3 = new PdfPCell(new Phrase("から", f));
        h3.setPadding(4); h3.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(h3);
        PdfPCell h4 = new PdfPCell(new Phrase("まで　　　日間", f));
        h4.setPadding(4);
        t.addCell(h4);
    }

    private void addWageRow(PdfPTable t, String rowLabel, String unit, String unitAmt,
                             String qty, String total, Font f) {
        if (!rowLabel.isEmpty() && !rowLabel.equals("手") && !rowLabel.equals("当")) {
            // rowLabel handled via rowspan in header
        }
        PdfPCell c1 = new PdfPCell(new Phrase(rowLabel, f));
        c1.setPadding(4); c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c1);
        PdfPCell c2 = new PdfPCell(new Phrase(unit, f));
        c2.setPadding(4); c2.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c2);
        PdfPCell c3 = new PdfPCell(new Phrase(unitAmt, f));
        c3.setPadding(4); c3.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c3);
        PdfPCell c4 = new PdfPCell(new Phrase(qty, f));
        c4.setPadding(4); c4.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c4);
        PdfPCell c5 = new PdfPCell(new Phrase(total, f));
        c5.setPadding(4); c5.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c5);
    }

    private void addEmptyWageRow(PdfPTable t, String label, Font f) {
        for (int i = 0; i < 4; i++) {
            PdfPCell c = new PdfPCell(new Phrase(i == 0 ? label : "　", f));
            c.setPadding(4);
            t.addCell(c);
        }
    }

    private void addFeeRow(PdfPTable t, String left, String desc, String num, String amt,
                            Font bf, Font nf, boolean firstRow) {
        PdfPCell c1 = new PdfPCell(new Phrase(left, bf));
        c1.setPadding(4); c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        c1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (firstRow) c1.setRowspan(4);
        if (!firstRow) {
            // skip first cell after rowspan
        } else {
            t.addCell(c1);
        }
        PdfPCell c2 = new PdfPCell(new Phrase(desc, nf));
        c2.setPadding(4);
        t.addCell(c2);
        PdfPCell c3 = new PdfPCell(new Phrase(num, bf));
        c3.setPadding(4); c3.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c3);
        PdfPCell c4 = new PdfPCell(new Phrase(amt, nf));
        c4.setPadding(4); c4.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c4);
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
                    items.add(item);
                }
            }
        }
        model.addAttribute("items", items);
        return "receipt-jobseeker-list";
    }

    @PostMapping("/jobseeker-receipt/print")
    public String jobseekerReceiptPrint(@RequestParam Long detailId, HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        return "redirect:/receipt-menu/jobseeker-receipt?printed=" + detailId;
    }

    // ─── 1-7-2 求職受付手数料領収書 PDF出力 ─────────────
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

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createJobseekerReceiptPdf(detail, person, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=jobseeker-receipt.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    private void createJobseekerReceiptPdf(SalesDetail detail, Person person,
                                            ByteArrayOutputStream baos) throws DocumentException, IOException {
        Document doc = new Document(PageSize.A5.rotate()); // 横向きA5
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont  = new Font(bf, 14, Font.BOLD);
        Font boldFont   = new Font(bf, 10, Font.BOLD);
        Font normalFont = new Font(bf, 10);
        Font smallFont  = new Font(bf, 8);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
        String today = LocalDate.now().format(dtf);

        // タイトル
        Paragraph title = new Paragraph("求 職 受 付 手 数 料 領 収 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8);
        doc.add(title);

        // 受領番号・年月日
        PdfPTable headerTable = new PdfPTable(new float[]{1, 1});
        headerTable.setWidthPercentage(100);
        addNoBorderRow(headerTable, "受領番号　" + (detail.getReceiptNo() != null ? detail.getReceiptNo() : ""), "", boldFont, normalFont);
        addNoBorderRow(headerTable, "", "年　　月　　日", normalFont, normalFont);
        doc.add(headerTable);

        // 宛名
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

        // メインテーブル
        PdfPTable mainTable = new PdfPTable(new float[]{2, 2, 2, 1, 2});
        mainTable.setWidthPercentage(100);

        // ヘッダー
        String[] headers = {"就 労 先", "月", "日", "1件", "領 収 書 No"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, boldFont));
            c.setPadding(5); c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setBackgroundColor(new BaseColor(220, 220, 220));
            mainTable.addCell(c);
        }

        // 就労先（勤務先求人者は detail から取得できないため空欄）
        String workPlace = "";
        Customer customer = detail.getCustomerId() != null
                ? customerRepository.findById(detail.getCustomerId()).orElse(null) : null;
        if (customer != null) {
            workPlace = customer.getLastNameKanji() + " " + customer.getFirstNameKanji() + " 様";
        }

        // 期間行（3行分）
        LocalDate startDate = detail.getWorkStartDate();
        LocalDate endDate   = detail.getWorkEndDate();

        for (int i = 0; i < 3; i++) {
            PdfPCell wpCell = new PdfPCell(new Phrase(i == 0 ? workPlace : "", normalFont));
            wpCell.setPadding(5); wpCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            mainTable.addCell(wpCell);

            if (i == 0 && startDate != null) {
                PdfPCell mCell = new PdfPCell(new Phrase(String.valueOf(startDate.getMonthValue()) + "月", normalFont));
                mCell.setPadding(5); mCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                mainTable.addCell(mCell);
                PdfPCell dCell = new PdfPCell(new Phrase(String.valueOf(startDate.getDayOfMonth()) + "日", normalFont));
                dCell.setPadding(5); dCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                mainTable.addCell(dCell);
            } else {
                mainTable.addCell(new PdfPCell(new Phrase("", normalFont)));
                mainTable.addCell(new PdfPCell(new Phrase("", normalFont)));
            }

            // 1件欄
            PdfPCell feeCell = new PdfPCell(new Phrase(i == 0 ? "710 円" : "", boldFont));
            feeCell.setPadding(5); feeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            mainTable.addCell(feeCell);

            PdfPCell noCell = new PdfPCell(new Phrase("", normalFont));
            noCell.setPadding(5);
            mainTable.addCell(noCell);
        }

        doc.add(mainTable);

        // 会社情報
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

        // 注釈
        doc.add(new Paragraph(" ", smallFont));
        doc.add(new Paragraph("※受付会員登録にお申込み1回についていただいております。", smallFont));
        doc.add(new Paragraph("※お申込み1回につき3回までにいただいております。3回分の受領手数料", smallFont));

        doc.close();
    }

    // ─── 内部クラス ────────────────────────────────────
    public static class JobseekerReceiptItem {
        public Person person;
        public SalesDetail detail;
        public Long salesId;
    }

    public static class ReceiptItem {
        public Customer customer;
        public SalesDetail detail;
        public Long salesId;
        public Long personId;
    }
}
