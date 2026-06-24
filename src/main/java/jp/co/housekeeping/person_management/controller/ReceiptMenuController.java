package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/receipt-menu")
public class ReceiptMenuController {

    private static final double FEE_RATE = 0.15; // 15%（PDFに合わせて変更）

    @Autowired private CustomerRepository    customerRepository;
    @Autowired private SalesRepository       salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private PersonRepository      personRepository;

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
                List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
                for (SalesDetail d : details) {
                    if (d.getCustomerId() == null) continue;
                    if (!d.getCustomerId().equals(c.getId())) continue;
                    if (d.getCustomerFee() == null || d.getCustomerFee() <= 0) continue;

                    ReceiptItem item   = new ReceiptItem();
                    item.customer      = c;
                    item.detail        = d;
                    item.salesId       = s.getId();
                    item.personId      = s.getPersonId();
                    item.issued        = d.getReceiptNo() != null && !d.getReceiptNo().isEmpty();
                    item.receiptNumber = item.issued ? d.getReceiptNo() : "";
                    items.add(item);
                }
            }
        }
        model.addAttribute("items", items);
        return "receipt-customer-list";
    }

    // ─── 1-7-1 PDF出力 ────────────────────────────────
    @GetMapping("/customer-receipt/pdf")
    public void customerReceiptPdf(@RequestParam Long detailId,
                                    HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        SalesDetail detail = salesDetailRepository.findById(detailId).orElse(null);
        if (detail == null) { response.sendError(404); return; }

        Customer customer = null;
        if (detail.getCustomerId() != null)
            customer = customerRepository.findById(detail.getCustomerId()).orElse(null);

        Sales sales = null;
        if (detail.getSalesId() != null)
            sales = salesRepository.findById(detail.getSalesId()).orElse(null);

        Person person = null;
        if (sales != null && sales.getPersonId() != null)
            person = personRepository.findById(sales.getPersonId()).orElse(null);

        // 領収番号：未発行なら採番してDBに保存
        String receiptNo = detail.getReceiptNo();
        if (receiptNo == null || receiptNo.isEmpty()) {
            int nextNo = salesDetailRepository.findMaxReceiptNo() + 1;
            receiptNo  = String.format("%04d", nextNo);
            detail.setReceiptNo(receiptNo);
            detail.setIssuedAt(LocalDateTime.now());
            salesDetailRepository.save(detail);
        } else if (detail.getIssuedAt() == null) {
            detail.setIssuedAt(LocalDateTime.now());
            salesDetailRepository.save(detail);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createCustomerReceiptPdf(detail, customer, person, receiptNo, baos);

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
            if (s.getPersonId() == null) continue;
            Person person = personRepository.findById(s.getPersonId()).orElse(null);
            if (person == null) continue;

            List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
            for (SalesDetail d : details) {
                if (d.getReceptionFee() == null || d.getReceptionFee() <= 0) continue;

                JobseekerReceiptItem item = new JobseekerReceiptItem();
                item.person        = person;
                item.detail        = d;
                item.salesId       = s.getId();
                item.issued        = d.getReceiptNo() != null && !d.getReceiptNo().isEmpty();
                item.receiptNumber = item.issued ? d.getReceiptNo() : "";
                items.add(item);
            }
        }
        model.addAttribute("items", items);
        return "receipt-jobseeker-list";
    }

    // ─── 1-7-2 PDF出力 ────────────────────────────────
    @GetMapping("/jobseeker-receipt/pdf")
    public void jobseekerReceiptPdf(@RequestParam Long detailId,
                                     HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        SalesDetail detail = salesDetailRepository.findById(detailId).orElse(null);
        if (detail == null) { response.sendError(404); return; }

        Sales sales = null;
        if (detail.getSalesId() != null)
            sales = salesRepository.findById(detail.getSalesId()).orElse(null);

        Person person = null;
        if (sales != null && sales.getPersonId() != null)
            person = personRepository.findById(sales.getPersonId()).orElse(null);

        // 領収番号
        String receiptNo = detail.getReceiptNo();
        if (receiptNo == null || receiptNo.isEmpty()) {
            int nextNo = salesDetailRepository.findMaxReceiptNo() + 1;
            receiptNo  = String.format("%04d", nextNo);
            detail.setReceiptNo(receiptNo);
            detail.setIssuedAt(LocalDateTime.now());
            salesDetailRepository.save(detail);
        } else if (detail.getIssuedAt() == null) {
            detail.setIssuedAt(LocalDateTime.now());
            salesDetailRepository.save(detail);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createJobseekerReceiptPdf(detail, person, receiptNo, baos);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=jobseeker-receipt.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    // ─── 発行済み一覧（月単位）────────────────────────
    @GetMapping("/issued-list")
    public String issuedList(@RequestParam(required = false) String month,
                              @RequestParam(required = false) String year,
                              @RequestParam(required = false, defaultValue = "month") String viewMode,
                              HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        // デフォルトリダイレクト
        if ("year".equals(viewMode)) {
            if (year == null || year.isBlank()) {
                year = String.valueOf(LocalDateTime.now().getYear());
                return "redirect:/receipt-menu/issued-list?viewMode=year&year=" + year;
            }
        } else {
            viewMode = "month";
            if (month == null || month.isBlank()) {
                String cur = String.format("%d-%02d",
                    LocalDateTime.now().getYear(), LocalDateTime.now().getMonthValue());
                return "redirect:/receipt-menu/issued-list?viewMode=month&month=" + cur;
            }
            // month を yyyy-MM 形式に正規化（例: 2026-6 → 2026-06）
            if (month.matches("\\d{4}-\\d{1}")) {
                month = month.substring(0, 5) + "0" + month.substring(5);
            }
        }

        final String targetMonth = month;
        final String targetYear  = year;

        List<IssuedListRow> rows = new ArrayList<>();

        for (Sales s : salesRepository.findAll()) {
            List<SalesDetail> details = salesDetailRepository.findBySalesId(s.getId());
            for (SalesDetail d : details) {
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;

                // 発行日時（issuedAt優先、introductionDate次点、両方nullなら今日）
                LocalDateTime issuedAt = d.getIssuedAt();
                LocalDate filterDate = issuedAt != null ? issuedAt.toLocalDate()
                                     : d.getIntroductionDate() != null ? d.getIntroductionDate()
                                     : LocalDate.now();

                if ("year".equals(viewMode)) {
                    String detailYear = String.valueOf(filterDate.getYear());
                    if (!detailYear.equals(targetYear)) continue;
                } else {
                    String detailMonth = String.format("%d-%02d", filterDate.getYear(), filterDate.getMonthValue());
                    if (!detailMonth.equals(targetMonth)) continue;
                }

                IssuedListRow row = new IssuedListRow();
                row.receiptNumber = d.getReceiptNo();
                row.salesDetailId = d.getId();
                row.issuedAt      = issuedAt;

                boolean isCustomer  = d.getCustomerFee()  != null && d.getCustomerFee()  > 0;
                boolean isJobseeker = d.getReceptionFee() != null && d.getReceptionFee() > 0;
                row.receiptType = isCustomer ? "1-7-1 求人者宛" : (isJobseeker ? "1-7-2 求職受付" : "不明");

                if (isCustomer) {
                    int tw = d.getMonthlyTotal() != null ? d.getMonthlyTotal() : 0;
                    int commission = (int)(tw * FEE_RATE);
                    int tax = (int)(commission * 0.10);
                    row.amount = (d.getCustomerFee() != null ? d.getCustomerFee() : 1000) + commission + tax;
                } else {
                    row.amount = d.getReceptionFee() != null ? d.getReceptionFee() : 0;
                }
                row.amountStr = "¥" + String.format("%,d", row.amount);
                row.issuedDate = filterDate;

                if (isCustomer && d.getCustomerId() != null) {
                    customerRepository.findById(d.getCustomerId()).ifPresent(c ->
                        row.partyName = c.getLastNameKanji() + " " + c.getFirstNameKanji());
                }
                if (!isCustomer && s.getPersonId() != null) {
                    personRepository.findById(s.getPersonId()).ifPresent(p ->
                        row.partyName = p.getLastNameKanji() + " " + p.getFirstNameKanji());
                }
                if (row.partyName == null) row.partyName = "－";

                rows.add(row);
            }
        }

        rows.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.receiptNumber), Integer.parseInt(b.receiptNumber)); }
            catch (NumberFormatException e) { return a.receiptNumber.compareTo(b.receiptNumber); }
        });

        long total = rows.stream().mapToLong(r -> r.amount).sum();
        // 月タブ用：年単位→当年当月、月単位→そのまま
        String tabMonthVal = "year".equals(viewMode)
            ? String.format("%d-%02d", LocalDateTime.now().getYear(), LocalDateTime.now().getMonthValue())
            : month;
        // 年タブ用：月単位→その年、年単位→そのまま
        String tabYearVal = "month".equals(viewMode) && month != null && month.length() >= 4
            ? month.substring(0, 4)
            : (year != null ? year : String.valueOf(LocalDateTime.now().getYear()));

        model.addAttribute("rows",           rows);
        model.addAttribute("totalAmountStr", "¥" + String.format("%,d", total));
        model.addAttribute("totalCount",     rows.size());
        model.addAttribute("selectedMonth",  month);
        model.addAttribute("selectedYear",   year);
        model.addAttribute("viewMode",       viewMode);
        model.addAttribute("tabMonthUrl",    "/receipt-menu/issued-list?viewMode=month&month=" + tabMonthVal);
        model.addAttribute("tabYearUrl",     "/receipt-menu/issued-list?viewMode=year&year=" + tabYearVal);
        String periodLabel = "year".equals(viewMode) ? year + "年" : (month != null ? month : "");
        model.addAttribute("periodLabel",    periodLabel);
        return "receipt-issued-list";
    }
    // ─── 発行済み一覧 PDF一括ZIP ──────────────────────────
    @GetMapping("/issued-list/export-pdf")
    public void issuedListExportPdf(@RequestParam(required = false) String month,
                                     @RequestParam(required = false) String year,
                                     @RequestParam(required = false, defaultValue = "month") String viewMode,
                                     HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (session.getAttribute("authenticated") == null) { response.sendError(401); return; }

        final String targetMonth = (month != null && !month.isBlank()) ? month : null;
        final String targetYear  = (year  != null && !year.isBlank())  ? year  : null;
        final boolean isYearMode = "year".equals(viewMode);

        // 対象データ収集
        List<Object[]> targets = new ArrayList<>();
        for (Sales s : salesRepository.findAll()) {
            for (SalesDetail d : salesDetailRepository.findBySalesId(s.getId())) {
                if (d.getReceiptNo() == null || d.getReceiptNo().isEmpty()) continue;
                LocalDateTime issuedAt = d.getIssuedAt();
                LocalDate filterDate = issuedAt != null ? issuedAt.toLocalDate()
                                     : d.getIntroductionDate() != null ? d.getIntroductionDate()
                                     : LocalDate.now();
                if (isYearMode) {
                    if (targetYear == null || !String.valueOf(filterDate.getYear()).equals(targetYear)) continue;
                } else {
                    String dm = String.format("%d-%02d", filterDate.getYear(), filterDate.getMonthValue());
                    if (targetMonth == null || !dm.equals(targetMonth)) continue;
                }
                targets.add(new Object[]{d, s});
            }
        }

        String periodStr = isYearMode ? (targetYear != null ? targetYear + "年" : "全期間")
                                      : (targetMonth != null ? targetMonth : "不明");
        String zipName = "領収書一括_" + periodStr + "_"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
            "attachment; filename*=UTF-8''"
            + java.net.URLEncoder.encode(zipName, "UTF-8").replace("+", "%20"));

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Object[] row : targets) {
                SalesDetail d = (SalesDetail) row[0];
                Sales s       = (Sales) row[1];
                boolean isCustomer = d.getCustomerFee() != null && d.getCustomerFee() > 0;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                String fileName;

                if (isCustomer) {
                    Customer customer = d.getCustomerId() != null
                        ? customerRepository.findById(d.getCustomerId()).orElse(null) : null;
                    Person person = s.getPersonId() != null
                        ? personRepository.findById(s.getPersonId()).orElse(null) : null;
                    createCustomerReceiptPdf(d, customer, person, d.getReceiptNo(), baos);
                    String name = customer != null
                        ? (customer.getLastNameKanji() + customer.getFirstNameKanji()).replaceAll("[/:*?<>|]", "_")
                        : "不明";
                    fileName = d.getReceiptNo() + "_求人者宛_" + name + ".pdf";
                } else {
                    Person person = s.getPersonId() != null
                        ? personRepository.findById(s.getPersonId()).orElse(null) : null;
                    createJobseekerReceiptPdf(d, person, d.getReceiptNo(), baos);
                    String name = person != null
                        ? (person.getLastNameKanji() + person.getFirstNameKanji()).replaceAll("[/:*?<>|]", "_")
                        : "不明";
                    fileName = d.getReceiptNo() + "_求職受付_" + name + ".pdf";
                }

                zos.putNextEntry(new ZipEntry(fileName));
                zos.write(baos.toByteArray());
                zos.closeEntry();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  1-7-1  求人受付・紹介手数料領収書 PDF生成
    //  PDFレイアウト参照：求人受付_紹介手数料領収書.pdf
    // ═══════════════════════════════════════════════════════════
    private void createCustomerReceiptPdf(SalesDetail detail, Customer customer,
                                           Person person, String receiptNo,
                                           ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont   = new Font(bf, 16, Font.BOLD);
        Font boldFont    = new Font(bf, 10, Font.BOLD);
        Font normalFont  = new Font(bf, 10);
        Font smallFont   = new Font(bf, 8);
        Font largeFont   = new Font(bf, 13, Font.BOLD);

        // ── 右上：領収番号・領収日 ──────────────────────────
        LocalDate today = LocalDate.now();
        PdfPTable topTable = new PdfPTable(new float[]{1, 1});
        topTable.setWidthPercentage(100);
        PdfPCell emptyLeft = cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);
        topTable.addCell(emptyLeft);

        PdfPTable noDateTable = new PdfPTable(new float[]{1, 2});
        noDateTable.setWidthPercentage(100);
        PdfPCell noLabel = cell("領収番号", smallFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT);
        noDateTable.addCell(noLabel);
        PdfPCell noValue = cell(receiptNo, boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        noDateTable.addCell(noValue);
        PdfPCell dateLabel = cell("領収日", smallFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT);
        noDateTable.addCell(dateLabel);
        PdfPCell dateValue = cell(
            String.format("%d年　%d月　%d日", today.getYear(), today.getMonthValue(), today.getDayOfMonth()),
            normalFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT);
        noDateTable.addCell(dateValue);

        PdfPCell rightCell = new PdfPCell(noDateTable);
        rightCell.setBorder(Rectangle.NO_BORDER);
        topTable.addCell(rightCell);
        doc.add(topTable);

        // ── タイトル ────────────────────────────────────────
        Paragraph title = new Paragraph("求 人 受 付 ・ 紹 介 手 数 料 領 収 証 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(4);
        title.setSpacingAfter(10);
        doc.add(title);

        // ── 宛名 ────────────────────────────────────────────
        String customerName = customer != null
            ? customer.getLastNameKanji() + "　" + customer.getFirstNameKanji() : "　　　　　";
        Paragraph nameP = new Paragraph(customerName + "　様", largeFont);
        nameP.setSpacingAfter(2);
        doc.add(nameP);

        // ── 金額算定（賃金総額・手数料計算） ──────────────────
        int totalWage   = detail.getMonthlyTotal() != null ? detail.getMonthlyTotal() : 0;
        int customerFee = detail.getCustomerFee()  != null ? detail.getCustomerFee()  : 1000;
        int commission  = (int)(totalWage * FEE_RATE);        // ②：賃金総額×15%切捨
        int consumptionTax = (int)(commission * 0.10);        // ③：消費税10%
        int grandTotal  = customerFee + commission + consumptionTax; // ①+②+③

        // ── 丸D マーク + 一金 ──────────────────────────────
        PdfPTable amountLine = new PdfPTable(new float[]{1, 6});
        amountLine.setWidthPercentage(100);
        amountLine.setSpacingBefore(4);
        amountLine.setSpacingAfter(4);
        PdfPCell circleD = cell("（D）", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        circleD.setPadding(4);
        amountLine.addCell(circleD);
        PdfPCell amtVal = cell(
            "一金　　" + String.format("%,d", grandTotal) + " 円也",
            largeFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);
        amountLine.addCell(amtVal);
        doc.add(amountLine);

        // ── 右：会社情報 ──────────────────────────────────
        PdfPTable splitTable = new PdfPTable(new float[]{1, 1});
        splitTable.setWidthPercentage(100);
        splitTable.setSpacingBefore(2);

        // 左：説明文
        PdfPCell descCell = new PdfPCell(
            new Phrase("職業紹介手数料として上記金額を正に領収しました。", normalFont));
        descCell.setBorder(Rectangle.NO_BORDER);
        descCell.setPaddingTop(8);
        splitTable.addCell(descCell);

        // 右：会社情報
        PdfPTable companyTable = new PdfPTable(1);
        companyTable.setWidthPercentage(100);
        for (String line : new String[]{
                "厚生労働省許可　13-ユ-040077",
                "有限会社　ワークオフィス谷",
                "〒107-0052",
                "東京都港区赤坂6-10-45-203",
                "TEL 03-5544-8315　FAX 03-5544-8316"}) {
            PdfPCell c = cell(line, smallFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);
            c.setPadding(1);
            companyTable.addCell(c);
        }
        PdfPCell companyWrap = new PdfPCell(companyTable);
        companyWrap.setBorder(Rectangle.NO_BORDER);
        splitTable.addCell(companyWrap);
        doc.add(splitTable);

        // ── 賃金算定テーブル ─────────────────────────────
        doc.add(new Paragraph(" ", smallFont));

        // 就労期間の計算
        LocalDate startDate = detail.getWorkStartDate();
        LocalDate endDate   = detail.getWorkEndDate();
        int workDays = 0;
        if (startDate != null && endDate != null) {
            workDays = (int)(endDate.toEpochDay() - startDate.toEpochDay()) + 1;
        }
        String periodStr = "";
        if (startDate != null && endDate != null) {
            periodStr = String.format("%d月　%d日　から　%d日　まで　%d　日間",
                startDate.getMonthValue(), startDate.getDayOfMonth(),
                endDate.getDayOfMonth(), workDays);
        }

        // 日給情報
        String dailyWages = detail.getDailyWages();
        int dailyDays = 0, dailyTotalAmt = 0, dailyWageUnit = 0;
        if (dailyWages != null && !dailyWages.isBlank()) {
            String[] wages = dailyWages.split(",");
            dailyDays = wages.length;
            for (String w : wages)
                try { dailyTotalAmt += Integer.parseInt(w.trim()); } catch (NumberFormatException ignored) {}
            if (dailyDays > 0) dailyWageUnit = dailyDays > 0 ? dailyTotalAmt / dailyDays : 0;
        }

        // 時給情報
        int hw  = detail.getHourlyWage() != null ? detail.getHourlyWage() : 0;
        double wh = detail.getWorkingHours() != null ? detail.getWorkingHours().doubleValue() : 0;
        int hwOt = detail.getHourlyWageOvertime() != null ? detail.getHourlyWageOvertime() : 0;

        // ワーカー名
        String workerName = person != null
            ? person.getLastNameKanji() + "　" + person.getFirstNameKanji() : "";

        PdfPTable wageTable = new PdfPTable(new float[]{1, 2, 2, 2, 3});
        wageTable.setWidthPercentage(100);
        wageTable.setSpacingBefore(4);

        // ヘッダー行1：勤務者氏名
        PdfPCell wn1 = cell("勤務者氏名", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        wn1.setColspan(2);
        wageTable.addCell(wn1);
        PdfPCell wn2 = cell(workerName, boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        wn2.setColspan(3);
        wageTable.addCell(wn2);

        // ヘッダー行2：就労期間
        PdfPCell period1 = cell("賃", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        period1.setRowspan(7);
        period1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        wageTable.addCell(period1);
        PdfPCell period2 = cell("就 労 期 間", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        period2.setColspan(2);
        wageTable.addCell(period2);
        PdfPCell period3 = cell(periodStr, normalFont, Rectangle.BOX, Element.ALIGN_LEFT);
        period3.setColspan(2);
        wageTable.addCell(period3);

        // 日給行
        String dailyUnitStr  = dailyWageUnit > 0 ? String.format("%,d円", dailyWageUnit) : "　　　円";
        String dailyDaysStr  = dailyDays > 0 ? String.format("%d 日間", dailyDays) : "　　日間";
        String dailyTotalStr = dailyTotalAmt > 0 ? String.format("%,d 円", dailyTotalAmt) : "0 円";
        addWageRow5(wageTable, "日給", "（1日　　" + dailyUnitStr + "）", dailyDaysStr, dailyTotalStr, normalFont);

        // 時間給行1
        String hw1Str   = hw > 0 ? String.format("%,d円", hw) : "　　　円";
        String wh1Str   = wh > 0 ? String.format("%.0f 時間", wh) : "　　時間";
        String hwAmt1   = hw > 0 && wh > 0 ? String.format("%,d 円", (int)(hw * wh)) : "0 円";
        addWageRow5(wageTable, "時間給", "（1時間　" + hw1Str + "）", wh1Str, hwAmt1, normalFont);

        // 時間給行2（残業）
        String hw2Str   = hwOt > 0 ? String.format("%,d円", hwOt) : "　　　円";
        addWageRow5(wageTable, "", "（1時間　" + hw2Str + "）", "　　時間", "0 円", normalFont);

        // 諸手当行
        PdfPCell kinCell = cell("金", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        kinCell.setRowspan(4);
        kinCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        wageTable.addCell(kinCell);
        addWageRow4WithFirstLabel(wageTable, "手", "（1時間　　　　円）", "　　時間", "0 円", normalFont);
        addWageRow4(wageTable, "当", "", "", "", normalFont);
        addWageRow4(wageTable, "", "", "", "", normalFont);
        addWageRow4(wageTable, "", "", "", "", normalFont);

        // 賃金総額行
        PdfPCell tl = cell("賃 金 総 額", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        tl.setColspan(3);
        wageTable.addCell(tl);
        PdfPCell tn = cell("①", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        wageTable.addCell(tn);
        PdfPCell ta = cell(String.format("%,d 円", totalWage), boldFont, Rectangle.BOX, Element.ALIGN_RIGHT);
        wageTable.addCell(ta);
        doc.add(wageTable);

        // ── 領収金額算定テーブル ─────────────────────────
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable feeTable = new PdfPTable(new float[]{1, 6, 1, 3});
        feeTable.setWidthPercentage(100);
        feeTable.setSpacingBefore(4);

        // 左側ラベル（縦書き的に）
        PdfPCell feeLabel = cell("領\n収\n金\n額\n算\n定", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        feeLabel.setRowspan(4);
        feeLabel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        feeTable.addCell(feeLabel);

        // ①：求人受付手数料
        feeTable.addCell(cell("求人受付手数料（求人1件につき1回）", normalFont, Rectangle.BOX, Element.ALIGN_LEFT));
        feeTable.addCell(cell("①", boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
        feeTable.addCell(cell(String.format("%,d 円", customerFee), normalFont, Rectangle.BOX, Element.ALIGN_RIGHT));

        // ②：紹介手数料
        feeTable.addCell(cell("紹介手数料（①×15% ※円未満切り捨て）", normalFont, Rectangle.BOX, Element.ALIGN_LEFT));
        feeTable.addCell(cell("②", boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
        feeTable.addCell(cell(String.format("%,d 円", commission), normalFont, Rectangle.BOX, Element.ALIGN_RIGHT));

        // ③：消費税
        feeTable.addCell(cell("消費税（10%）", normalFont, Rectangle.BOX, Element.ALIGN_LEFT));
        feeTable.addCell(cell("③", boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
        feeTable.addCell(cell(String.format("%,d 円", consumptionTax), normalFont, Rectangle.BOX, Element.ALIGN_RIGHT));

        // 手数料合計
        feeTable.addCell(cell("手数料合計　（①+②+③）", boldFont, Rectangle.BOX, Element.ALIGN_LEFT));
        feeTable.addCell(cell("D", boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
        feeTable.addCell(cell(String.format("%,d 円", grandTotal), boldFont, Rectangle.BOX, Element.ALIGN_RIGHT));

        doc.add(feeTable);
        doc.close();
    }

    // ═══════════════════════════════════════════════════════════
    //  1-7-2  求職受付手数料領収書 PDF生成
    //  PDFレイアウト参照：求職受付手数料領収書.pdf
    // ═══════════════════════════════════════════════════════════
    private void createJobseekerReceiptPdf(SalesDetail detail, Person person,
                                            String receiptNo, ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        Document doc = new Document(PageSize.A5, 30, 30, 30, 30);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont   = new Font(bf, 14, Font.BOLD | Font.UNDERLINE);
        Font boldFont    = new Font(bf, 10, Font.BOLD);
        Font normalFont  = new Font(bf, 10);
        Font smallFont   = new Font(bf, 8);

        // ── 右上：領収番号 ──────────────────────────────────
        PdfPTable topTable = new PdfPTable(new float[]{1, 1});
        topTable.setWidthPercentage(100);
        topTable.addCell(cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT));

        PdfPTable noTable = new PdfPTable(new float[]{1, 2});
        noTable.setWidthPercentage(100);
        noTable.addCell(cell("領収番号", smallFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT));
        PdfPCell noVal = cell(receiptNo, boldFont, Rectangle.BOTTOM, Element.ALIGN_CENTER);
        noTable.addCell(noVal);
        // 2行目（空欄線）
        noTable.addCell(cell("", smallFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT));
        noTable.addCell(cell("", normalFont, Rectangle.BOTTOM, Element.ALIGN_LEFT));

        PdfPCell noWrap = new PdfPCell(noTable);
        noWrap.setBorder(Rectangle.NO_BORDER);
        topTable.addCell(noWrap);
        doc.add(topTable);

        // ── タイトル ────────────────────────────────────────
        Paragraph title = new Paragraph("求 職 受 付 手 数 料 領 収 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(6);
        title.setSpacingAfter(10);
        doc.add(title);

        // ── 求職者名＋会社情報（横並び） ──────────────────
        PdfPTable nameCompTable = new PdfPTable(new float[]{1, 1});
        nameCompTable.setWidthPercentage(100);
        nameCompTable.setSpacingBefore(4);

        String personName = person != null
            ? person.getLastNameKanji() + "　" + person.getFirstNameKanji() : "　　　　　　　";

        // 左：求職者名
        PdfPTable nameTable = new PdfPTable(1);
        nameTable.setWidthPercentage(100);
        nameTable.addCell(cell("求職者名", smallFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT));
        PdfPCell nameLine = cell(personName + "　様", boldFont, Rectangle.BOTTOM, Element.ALIGN_LEFT);
        nameLine.setPaddingTop(16);
        nameTable.addCell(nameLine);
        PdfPCell nameWrap = new PdfPCell(nameTable);
        nameWrap.setBorder(Rectangle.NO_BORDER);
        nameCompTable.addCell(nameWrap);

        // 右：会社情報
        PdfPTable compTable = new PdfPTable(1);
        compTable.setWidthPercentage(100);
        for (String line : new String[]{
                "有限会社ワークオフィス谷",
                "〒107-0052",
                "東京都港区赤坂6-10-45-203",
                "TEL03-5544-8315"}) {
            compTable.addCell(cell(line, smallFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT));
        }
        PdfPCell compWrap = new PdfPCell(compTable);
        compWrap.setBorder(Rectangle.NO_BORDER);
        nameCompTable.addCell(compWrap);
        doc.add(nameCompTable);

        // ── 説明文 ──────────────────────────────────────────
        doc.add(new Paragraph(" ", smallFont));
        Paragraph desc = new Paragraph("下記の金額正に領収いたしました。　（1件につき710円）", normalFont);
        desc.setSpacingAfter(4);
        doc.add(desc);

        // ── 受付月日テーブル ────────────────────────────────
        LocalDate introDate = detail.getIntroductionDate();

        PdfPTable dateTable = new PdfPTable(new float[]{2, 1, 1, 1, 1, 2});
        dateTable.setWidthPercentage(100);
        dateTable.setSpacingBefore(4);

        // ヘッダー
        dateTable.addCell(cell("受付月日", boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
        dateTable.addCell(cell("月", boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
        dateTable.addCell(cell("日", boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
        // 空列（スペーサー）
        PdfPCell spacer = cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_CENTER);
        spacer.setColspan(3);
        dateTable.addCell(spacer);

        // 受付月日3行
        String[] months = {"", "", ""};
        String[] days   = {"", "", ""};
        if (introDate != null) {
            months[0] = String.valueOf(introDate.getMonthValue());
            days[0]   = String.valueOf(introDate.getDayOfMonth());
        }
        int receptionFee = detail.getReceptionFee() != null ? detail.getReceptionFee() : 710;
        int count = receptionFee / 710; // 件数
        if (count < 1) count = 1;
        if (count > 3) count = 3;
        int totalFee = receptionFee;

        for (int i = 0; i < 3; i++) {
            dateTable.addCell(cell("", normalFont, Rectangle.BOX, Element.ALIGN_CENTER));
            dateTable.addCell(cell(months[i], normalFont, Rectangle.BOX, Element.ALIGN_CENTER));
            dateTable.addCell(cell(days[i],   normalFont, Rectangle.BOX, Element.ALIGN_CENTER));
            if (i == 2) {
                // 最終行に合計
                dateTable.addCell(cell("合計", boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
                dateTable.addCell(cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT));
                dateTable.addCell(cell(String.format("%,d　円", totalFee), boldFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT));
            } else {
                PdfPCell emptyRight = cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);
                emptyRight.setColspan(3);
                dateTable.addCell(emptyRight);
            }
        }
        doc.add(dateTable);

        // ── 注意書き ────────────────────────────────────────
        doc.add(new Paragraph(" ", smallFont));
        doc.add(new Paragraph("（注）求職受付手数料は求職のお申し込み1回ごとにいただいております。", smallFont));
        doc.add(new Paragraph("　　　求職お申し込みが1か月に3回を超える場合は、3回分の金額です。", smallFont));

        doc.close();
    }

    // ─── PDF ヘルパー ─────────────────────────────────────────
    private PdfPCell cell(String text, Font font, int border, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(border);
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        return c;
    }

    private void addWageRow5(PdfPTable t, String label, String unit, String qty, String total, Font f) {
        t.addCell(cell(label, f, Rectangle.BOX, Element.ALIGN_CENTER));
        t.addCell(cell(unit,  f, Rectangle.BOX, Element.ALIGN_LEFT));
        t.addCell(cell(qty,   f, Rectangle.BOX, Element.ALIGN_CENTER));
        t.addCell(cell(total, f, Rectangle.BOX, Element.ALIGN_RIGHT));
    }

    // 「金」縦ラベル付きの賃金行（最初の行）
    private void addWageRow4WithFirstLabel(PdfPTable t, String label, String unit, String qty, String total, Font f) {
        // 「金」セルはすでにrowspan=4で追加済み（呼び出し元で管理）
        t.addCell(cell(label, f, Rectangle.BOX, Element.ALIGN_CENTER));
        t.addCell(cell(unit,  f, Rectangle.BOX, Element.ALIGN_LEFT));
        t.addCell(cell(qty,   f, Rectangle.BOX, Element.ALIGN_CENTER));
        t.addCell(cell(total, f, Rectangle.BOX, Element.ALIGN_RIGHT));
    }

    private void addWageRow4(PdfPTable t, String label, String unit, String qty, String total, Font f) {
        t.addCell(cell(label, f, Rectangle.BOX, Element.ALIGN_CENTER));
        t.addCell(cell(unit,  f, Rectangle.BOX, Element.ALIGN_LEFT));
        t.addCell(cell(qty,   f, Rectangle.BOX, Element.ALIGN_CENTER));
        t.addCell(cell(total, f, Rectangle.BOX, Element.ALIGN_RIGHT));
    }

    // ─── 内部クラス ────────────────────────────────────────────
    public static class JobseekerReceiptItem {
        public Person      person;
        public SalesDetail detail;
        public Long        salesId;
        public boolean     issued;
        public String      receiptNumber;
    }

    public static class ReceiptItem {
        public Customer    customer;
        public SalesDetail detail;
        public Long        salesId;
        public Long        personId;
        public boolean     issued;
        public String      receiptNumber;
    }

    public static class IssuedListRow {
        public String    receiptNumber;
        public String    receiptType;
        public String    partyName;
        public int       amount;
        public String    amountStr;
        public LocalDate issuedDate;
        public Long      salesDetailId;
        public LocalDateTime issuedAt;
    }
}
