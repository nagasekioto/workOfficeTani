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

        // マージン：上36・右36・下36・左36、タイトル後のコンテンツを下げるためtopを大きく
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont   = new Font(bf, 15, Font.BOLD | Font.UNDERLINE);
        Font boldFont    = new Font(bf, 10, Font.BOLD);
        Font normalFont  = new Font(bf, 10);
        Font smallFont   = new Font(bf, 8);
        Font largeFont   = new Font(bf, 13, Font.BOLD);
        Font underFont   = new Font(bf, 13, Font.BOLD | Font.UNDERLINE); // 一金 下線付き

        // ── ① タイトル（一番上） ────────────────────────────
        Paragraph title = new Paragraph("求 人 受 付 ・ 紹 介 手 数 料 領 収 証 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(24); // タイトル後に空白を入れてコンテンツを下げる
        doc.add(title);

        // ── ② 右上：領収番号・領収日 ─────────────────────────
        LocalDate today = LocalDate.now();
        PdfPTable topTable = new PdfPTable(new float[]{3, 2});
        topTable.setWidthPercentage(100);
        topTable.setSpacingAfter(10);

        PdfPCell emptyLeft = cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);
        topTable.addCell(emptyLeft);

        PdfPTable noDateTable = new PdfPTable(new float[]{1, 2});
        noDateTable.setWidthPercentage(100);
        noDateTable.addCell(cell("領収番号", smallFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT));
        noDateTable.addCell(cell(receiptNo, boldFont, Rectangle.BOX, Element.ALIGN_CENTER));
        noDateTable.addCell(cell("領収日", smallFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT));
        noDateTable.addCell(cell(
            String.format("%d年　%d月　%d日", today.getYear(), today.getMonthValue(), today.getDayOfMonth()),
            normalFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT));
        PdfPCell rightCell = new PdfPCell(noDateTable);
        rightCell.setBorder(Rectangle.NO_BORDER);
        topTable.addCell(rightCell);
        doc.add(topTable);

        // ── ③ 宛名 ──────────────────────────────────────────
        String customerName = customer != null
            ? customer.getLastNameKanji() + "　" + customer.getFirstNameKanji() : "　　　　　";
        Paragraph nameP = new Paragraph(customerName + "　様", largeFont);
        nameP.setSpacingAfter(6);
        doc.add(nameP);

        // ── ④ 金額算定 ───────────────────────────────────────
        int totalWage      = detail.getMonthlyTotal() != null ? detail.getMonthlyTotal() : 0;
        int customerFee    = detail.getCustomerFee()  != null ? detail.getCustomerFee()  : 1000;
        int commission     = (int)(totalWage * FEE_RATE);
        int consumptionTax = (int)(commission * 0.10);
        int grandTotal     = customerFee + commission + consumptionTax;

        // ── ⑤ （②＋③＋④）一金（下線付き） ────────────────────
        PdfPTable amountLine = new PdfPTable(new float[]{1.8f, 5.2f});
        amountLine.setWidthPercentage(100);
        amountLine.setSpacingBefore(2);
        amountLine.setSpacingAfter(6);
        PdfPCell circleLabel = cell("（②＋③＋④）", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        circleLabel.setPadding(4);
        amountLine.addCell(circleLabel);
        PdfPCell amtVal = cell(
            "一金　　" + String.format("%,d", grandTotal) + " 円也",
            underFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);  // 下線付き
        amountLine.addCell(amtVal);
        doc.add(amountLine);

        // ── ⑥ 説明文 ＋ 会社情報（右寄せ・登録番号追加・縦伸ばし） ──
        PdfPTable splitTable = new PdfPTable(new float[]{1, 1});
        splitTable.setWidthPercentage(100);
        splitTable.setSpacingBefore(2);
        splitTable.setSpacingAfter(10);

        PdfPCell descCell = new PdfPCell(
            new Phrase("職業紹介手数料として上記金額を正に領収しました。", normalFont));
        descCell.setBorder(Rectangle.NO_BORDER);
        descCell.setPaddingTop(10);
        splitTable.addCell(descCell);

        Font companyFont = new Font(bf, 11, Font.BOLD);  // 会社情報は11pt太字
        // 右：会社情報（右寄せ）＋登録番号
        PdfPTable companyTable = new PdfPTable(1);
        companyTable.setWidthPercentage(100);
        for (String line : new String[]{
                "厚生労働省許可　13-ユ-040077",
                "有限会社　ワークオフィス谷",
                "〒107-0052",
                "東京都港区赤坂6-10-45-203",
                "TEL 03-5544-8315　FAX 03-5544-8316",
                "登録番号：T6010402013584"}) {
            PdfPCell c = cell(line, companyFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT);
            c.setPadding(2);
            companyTable.addCell(c);
        }
        PdfPCell companyWrap = new PdfPCell(companyTable);
        companyWrap.setBorder(Rectangle.NO_BORDER);
        splitTable.addCell(companyWrap);
        doc.add(splitTable);

        // ── ⑦ 賃金算定テーブル ──────────────────────────────
        LocalDate startDate = detail.getWorkStartDate();
        LocalDate endDate   = detail.getWorkEndDate();
        int workDays = 0;
        if (startDate != null && endDate != null)
            workDays = (int)(endDate.toEpochDay() - startDate.toEpochDay()) + 1;

        String periodStr = "";
        if (startDate != null && endDate != null)
            periodStr = String.format("%d月　%d日　から　%d日　まで　%d　日間",
                startDate.getMonthValue(), startDate.getDayOfMonth(),
                endDate.getDayOfMonth(), workDays);

        String dailyWages = detail.getDailyWages();
        int dailyDays = 0, dailyTotalAmt = 0, dailyWageUnit = 0;
        if (dailyWages != null && !dailyWages.isBlank()) {
            String[] wages = dailyWages.split(",");
            dailyDays = wages.length;
            for (String w : wages)
                try { dailyTotalAmt += Integer.parseInt(w.trim()); } catch (NumberFormatException ignored) {}
            if (dailyDays > 0) dailyWageUnit = dailyTotalAmt / dailyDays;
        }

        int hw   = detail.getHourlyWage()        != null ? detail.getHourlyWage()        : 0;
        double wh = detail.getWorkingHours()      != null ? detail.getWorkingHours().doubleValue() : 0;
        int hwOt = detail.getHourlyWageOvertime() != null ? detail.getHourlyWageOvertime() : 0;

        int calcTotal = dailyTotalAmt + (hw > 0 && wh > 0 ? (int)(hw * wh) : 0);
        if (calcTotal == 0) calcTotal = totalWage;
        else totalWage = calcTotal;
        commission     = (int)(totalWage * FEE_RATE);
        consumptionTax = (int)(commission * 0.10);
        grandTotal     = customerFee + commission + consumptionTax;

        String workerName = person != null
            ? person.getLastNameKanji() + "　" + person.getFirstNameKanji() : "";

        Font smallNormal = new Font(bf, 8);

        // 行の高さを固定して縦伸ばし
        final float ROW_H = 22f;

        // 列: [賃金算定1.2][諸手当1.2][種別1.8][前置き1.4][金額右揃え1.8][数量1.4][合計2.8]
        PdfPTable wageTable = new PdfPTable(new float[]{1.2f, 1.2f, 1.8f, 1.4f, 1.8f, 1.4f, 2.8f});
        wageTable.setWidthPercentage(100);
        wageTable.setSpacingBefore(4);

        // ── 行0: 勤務者氏名 ─────────────────────────────────────
        PdfPCell wn1 = cell("勤務者氏名", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        wn1.setColspan(4); wn1.setFixedHeight(ROW_H);
        wageTable.addCell(wn1);
        PdfPCell wn2 = cell(workerName, boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        wn2.setColspan(3); wn2.setFixedHeight(ROW_H);
        wageTable.addCell(wn2);

        // ── col0: 賃金算定（rowspan=9） ──────────────────────────
        PdfPCell wageLabel = new PdfPCell();
        wageLabel.setBorder(Rectangle.BOX);
        wageLabel.setRowspan(9);
        wageLabel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        wageLabel.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph vp = new Paragraph();
        vp.setAlignment(Element.ALIGN_CENTER);
        for (String ch : new String[]{"賃","金","算","定"}) {
            vp.add(new com.itextpdf.text.Chunk(ch, boldFont));
            vp.add(com.itextpdf.text.Chunk.NEWLINE);
        }
        wageLabel.addElement(vp);
        wageTable.addCell(wageLabel);

        // ── col1: 諸手当（rowspan=8） ────────────────────────────
        PdfPCell kinCell = new PdfPCell();
        kinCell.setBorder(Rectangle.BOX);
        kinCell.setRowspan(8);
        kinCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        kinCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph kinP = new Paragraph();
        kinP.setAlignment(Element.ALIGN_CENTER);
        for (String ch : new String[]{"諸","手","当"}) {
            kinP.add(new com.itextpdf.text.Chunk(ch, boldFont));
            kinP.add(com.itextpdf.text.Chunk.NEWLINE);
        }
        kinCell.addElement(kinP);
        wageTable.addCell(kinCell);

        // ── 行1: 就労日数 ────────────────────────────────────────
        PdfPCell period2 = cell("就 労 日 数", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        period2.setColspan(3); period2.setFixedHeight(ROW_H);
        wageTable.addCell(period2);
        String workDaysStr = workDays > 0 ? workDays + " 日" : "－";
        PdfPCell period3 = cell(workDaysStr, normalFont, Rectangle.BOX, Element.ALIGN_CENTER);
        period3.setColspan(2); period3.setFixedHeight(ROW_H);
        wageTable.addCell(period3);

        // ── 行2〜8: 賃金内訳（種別 | 前置き | 金額） ─────────────
        // 日給
        String dailyUnitAmt = dailyWageUnit > 0 ? String.format("%,d円 ）", dailyWageUnit) : "円 ）";
        addWageDetailRow(wageTable, smallNormal, ROW_H,
            "日給", "（1日", dailyUnitAmt,
            dailyDays > 0 ? String.format("%d日間", dailyDays) : "日間",
            dailyTotalAmt > 0 ? String.format("%,d円", dailyTotalAmt) : "0円");

        // 時間給
        String hw1Amt = hw > 0 ? String.format("%,d円 ）", hw) : "円 ）";
        String wh1Str = wh > 0 ? String.format("%.0f時間", wh) : "時間";
        String hwAmt1 = hw > 0 && wh > 0 ? String.format("%,d円", (int)(hw * wh)) : "0円";
        addWageDetailRow(wageTable, smallNormal, ROW_H,
            "時間給", "（1時間", hw1Amt, wh1Str, hwAmt1);

        // 時間給（残業）
        String hw2Amt = hwOt > 0 ? String.format("%,d円 ）", hwOt) : "円 ）";
        addWageDetailRow(wageTable, smallNormal, ROW_H,
            "", "（1時間", hw2Amt, "時間", "0円");

        // 諸手当4行
        for (int i = 0; i < 4; i++) {
            addWageDetailRow(wageTable, smallNormal, ROW_H,
                "", "（1時間", "円 ）", "時間", "0円");
        }

        // ── 行9: 賃金総額① ──────────────────────────────────────
        PdfPCell totalLabel = cell("賃 金 総 額 ①", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        totalLabel.setColspan(5); totalLabel.setFixedHeight(ROW_H);
        wageTable.addCell(totalLabel);
        PdfPCell totalAmtCell = cell(
            totalWage > 0 ? String.format("%,d 円", totalWage) : "　　　円",
            boldFont, Rectangle.BOX, Element.ALIGN_RIGHT);
        totalAmtCell.setColspan(2); totalAmtCell.setFixedHeight(ROW_H);
        wageTable.addCell(totalAmtCell);

        doc.add(wageTable);

        // ── 領収金額算定テーブル ─────────────────────────────────
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable feeTable = new PdfPTable(new float[]{1, 6, 1, 3});
        feeTable.setWidthPercentage(100);
        feeTable.setSpacingBefore(6);

        final float FEE_ROW_H = 30f;  // 領収金額算定の行は高めに

        PdfPCell feeLabel = new PdfPCell();
        feeLabel.setBorder(Rectangle.BOX);
        feeLabel.setRowspan(4);
        feeLabel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        feeLabel.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph feeLabelP = new Paragraph();
        feeLabelP.setAlignment(Element.ALIGN_CENTER);
        for (String ch : new String[]{"領","収","金","額","算","定"}) {
            feeLabelP.add(new com.itextpdf.text.Chunk(ch, boldFont));
            feeLabelP.add(com.itextpdf.text.Chunk.NEWLINE);
        }
        feeLabel.addElement(feeLabelP);
        feeTable.addCell(feeLabel);

        // ②：求人受付手数料
        addFeeRow(feeTable, normalFont, boldFont, FEE_ROW_H,
            "求人受付手数料（求人1件につき1回）", "②", String.format("%,d 円", customerFee));

        // ③：紹介手数料
        addFeeRow(feeTable, normalFont, boldFont, FEE_ROW_H,
            "紹介手数料（賃金総額①×15% ※円未満切り捨て）", "③", String.format("%,d 円", commission));

        // ④：消費税
        addFeeRow(feeTable, normalFont, boldFont, FEE_ROW_H,
            "消費税（10%）", "④", String.format("%,d 円", consumptionTax));

        // 手数料合計額
        PdfPCell feeTotalLabel = cell("手数料合計額　（②＋③＋④）", boldFont, Rectangle.BOX, Element.ALIGN_LEFT);
        feeTotalLabel.setColspan(2); feeTotalLabel.setFixedHeight(FEE_ROW_H);
        feeTable.addCell(feeTotalLabel);
        PdfPCell feeTotalAmt = cell(String.format("%,d 円", grandTotal), boldFont, Rectangle.BOX, Element.ALIGN_RIGHT);
        feeTotalAmt.setFixedHeight(FEE_ROW_H);
        feeTable.addCell(feeTotalAmt);

        doc.add(feeTable);
        doc.close();
    }

    // wageTable用ヘルパー（種別 | 前置き | 金額右揃え | 数量 | 合計）
    private void addWageDetailRow(PdfPTable t, Font f, float h,
                                   String label, String prefix, String amtStr,
                                   String qty, String total) {
        PdfPCell c1 = cell(label,  f, Rectangle.BOX, Element.ALIGN_CENTER); c1.setFixedHeight(h); t.addCell(c1);
        PdfPCell c2 = cell(prefix, f, Rectangle.BOX, Element.ALIGN_LEFT);   c2.setFixedHeight(h); t.addCell(c2);
        PdfPCell c3 = cell(amtStr, f, Rectangle.BOX, Element.ALIGN_RIGHT);  c3.setFixedHeight(h); t.addCell(c3);
        PdfPCell c4 = cell(qty,    f, Rectangle.BOX, Element.ALIGN_CENTER); c4.setFixedHeight(h); t.addCell(c4);
        PdfPCell c5 = cell(total,  f, Rectangle.BOX, Element.ALIGN_RIGHT);  c5.setFixedHeight(h); t.addCell(c5);
    }

    // wageTable用ヘルパー（旧・未使用だが残置）
    private void addFixedRow(PdfPTable t, Font f, float h,
                              String label, String detail, String qty, String amt) {
        PdfPCell c1 = cell(label,  f, Rectangle.BOX, Element.ALIGN_CENTER); c1.setFixedHeight(h); t.addCell(c1);
        PdfPCell c2 = cell(detail, f, Rectangle.BOX, Element.ALIGN_LEFT);   c2.setFixedHeight(h); t.addCell(c2);
        PdfPCell c3 = cell(qty,    f, Rectangle.BOX, Element.ALIGN_CENTER); c3.setFixedHeight(h); t.addCell(c3);
        PdfPCell c4 = cell(amt,    f, Rectangle.BOX, Element.ALIGN_RIGHT);  c4.setFixedHeight(h); t.addCell(c4);
    }

    // feeTable用ヘルパー
    private void addFeeRow(PdfPTable t, Font nf, Font bf2, float h,
                            String desc, String num, String amt) {
        PdfPCell c1 = cell(desc, nf,  Rectangle.BOX, Element.ALIGN_LEFT);   c1.setFixedHeight(h); t.addCell(c1);
        PdfPCell c2 = cell(num,  bf2, Rectangle.BOX, Element.ALIGN_CENTER); c2.setFixedHeight(h); t.addCell(c2);
        PdfPCell c3 = cell(amt,  nf,  Rectangle.BOX, Element.ALIGN_RIGHT);  c3.setFixedHeight(h); t.addCell(c3);
    }

    // ═══════════════════════════════════════════════════════════
    //  1-7-2  求職受付手数料領収書 PDF生成
    //  PDFレイアウト参照：求職受付手数料領収書.pdf
    // ═══════════════════════════════════════════════════════════
    private void createJobseekerReceiptPdf(SalesDetail detail, Person person,
                                            String receiptNo, ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        Document doc = new Document(PageSize.A5, 30, 30, 28, 28);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont   = new Font(bf, 14, Font.BOLD | Font.UNDERLINE);
        Font boldFont    = new Font(bf, 10, Font.BOLD);
        Font normalFont  = new Font(bf, 10);
        Font smallFont   = new Font(bf, 8);
        Font companyFont = new Font(bf, 9, Font.BOLD);   // 会社情報：1-7-1と同様
        Font largeFont   = new Font(bf, 11, Font.BOLD);  // 求職者名

        LocalDate today = LocalDate.now();

        // ── ① タイトル（一番上） ────────────────────────────────
        Paragraph title = new Paragraph("求 職 受 付 手 数 料 領 収 書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(16);
        doc.add(title);

        // ── ② 領収番号＋領収日（右上） ──────────────────────────
        PdfPTable topTable = new PdfPTable(new float[]{3, 2});
        topTable.setWidthPercentage(100);
        topTable.setSpacingAfter(8);
        topTable.addCell(cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT));

        PdfPTable noDateTable = new PdfPTable(new float[]{1, 2});
        noDateTable.setWidthPercentage(100);
        PdfPCell noLabel = cell("領収番号", smallFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT);
        noLabel.setPaddingTop(10);
        noDateTable.addCell(noLabel);
        PdfPCell noVal = cell(receiptNo, boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        noVal.setPaddingTop(10);
        noVal.setVerticalAlignment(Element.ALIGN_TOP);
        noDateTable.addCell(noVal);
        noDateTable.addCell(cell("領収日", smallFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT));
        PdfPCell dateVal = cell(
            String.format("%d年　%d月　%d日", today.getYear(), today.getMonthValue(), today.getDayOfMonth()),
            normalFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT);
        dateVal.setPaddingBottom(10);
        noDateTable.addCell(dateVal);
        PdfPCell noWrap = new PdfPCell(noDateTable);
        noWrap.setBorder(Rectangle.NO_BORDER);
        topTable.addCell(noWrap);
        doc.add(topTable);

        // ── ③ 求職者名（下線を名前に密着） ─────────────────────
        String personName = person != null
            ? person.getLastNameKanji() + "　" + person.getFirstNameKanji() : "　　　　　　　";

        PdfPTable nameRow = new PdfPTable(new float[]{3, 2});
        nameRow.setWidthPercentage(100);
        nameRow.setSpacingAfter(14);

        // 左：求職者名ラベル＋名前（Chunkで下線を名前直下に密着）
        PdfPTable nameInner = new PdfPTable(1);
        nameInner.setWidthPercentage(100);
        PdfPCell labelCell = cell("求職者名", smallFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);
        labelCell.setPaddingBottom(1);
        nameInner.addCell(labelCell);
        // 下線付きChunkで名前直下に線を引く
        com.itextpdf.text.Chunk nameChunk = new com.itextpdf.text.Chunk(
            personName + "　様", largeFont);
        nameChunk.setUnderline(0.8f, -2f); // 線の太さ0.8、位置-2（文字直下）
        PdfPCell nLine = new PdfPCell(new Phrase(nameChunk));
        nLine.setBorder(Rectangle.NO_BORDER);
        nLine.setHorizontalAlignment(Element.ALIGN_LEFT);
        nLine.setPaddingTop(2);
        nLine.setPaddingBottom(2);
        nLine.setPaddingLeft(2);
        nameInner.addCell(nLine);
        PdfPCell nameWrap = new PdfPCell(nameInner);
        nameWrap.setBorder(Rectangle.NO_BORDER);
        nameRow.addCell(nameWrap);

        // 右：会社情報（右寄せ・1-7-1と同様）
        PdfPTable compTable = new PdfPTable(1);
        compTable.setWidthPercentage(100);
        for (String line : new String[]{
                "有限会社　ワークオフィス谷",
                "〒107-0052",
                "東京都港区赤坂6-10-45-203",
                "TEL 03-5544-8315",
                "登録番号：T6010402013584"}) {
            PdfPCell c = cell(line, companyFont, Rectangle.NO_BORDER, Element.ALIGN_RIGHT);
            c.setPadding(2);
            compTable.addCell(c);
        }
        PdfPCell compWrap = new PdfPCell(compTable);
        compWrap.setBorder(Rectangle.NO_BORDER);
        nameRow.addCell(compWrap);
        doc.add(nameRow);

        // ── ④ 説明文 ──────────────────────────────────────────
        Paragraph desc = new Paragraph("下記の金額正に領収いたしました。　（1件につき710円）", normalFont);
        desc.setSpacingAfter(10);
        doc.add(desc);

        // ── ⑤ 受付月日テーブル ──────────────────────────────────
        // 構成:
        //  ヘッダー行: [受付月日(横)] [年] [月] [日] [スペーサー] [合計] [金額]
        //  データ行1:  [空           ] [年] [月] [日] [スペーサー] [空  ] [空  ]
        //  データ行2:  [空           ] [年] [月] [日] [スペーサー] [空  ] [空  ]
        //  データ行3:  [空           ] [年] [月] [日] [スペーサー] [空  ] [空  ]
        LocalDate introDate = detail.getIntroductionDate();
        int receptionFee = detail.getReceptionFee() != null ? detail.getReceptionFee() : 710;

        int yr1 = introDate != null ? introDate.getYear()       : 0;
        int mo1 = introDate != null ? introDate.getMonthValue() : 0;
        int dy1 = introDate != null ? introDate.getDayOfMonth() : 0;

        final float ROW_H2 = 30f;
        // 列: [受付月日 rowspan=4] [年] [月] [日] [スペーサー] [合計] [金額]
        // 受付月日はrowspan=4（ヘッダー1行＋データ3行）
        PdfPTable dateTable = new PdfPTable(new float[]{2f, 1.4f, 0.9f, 0.9f, 0.3f, 1.2f, 2f});
        dateTable.setWidthPercentage(100);
        dateTable.setSpacingBefore(10);

        // col0: 「受付月日」4行結合
        PdfPCell rcLabel = new PdfPCell();
        rcLabel.setBorder(Rectangle.BOX);
        rcLabel.setRowspan(4);
        rcLabel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        rcLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rcLabel.addElement(new Phrase("受付月日", boldFont));
        dateTable.addCell(rcLabel);

        // ヘッダー行（年・月・日・スペーサー3列）
        PdfPCell hY = cell("年", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        hY.setVerticalAlignment(Element.ALIGN_MIDDLE); dateTable.addCell(hY);
        PdfPCell hM = cell("月", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        hM.setVerticalAlignment(Element.ALIGN_MIDDLE); dateTable.addCell(hM);
        PdfPCell hD = cell("日", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
        hD.setVerticalAlignment(Element.ALIGN_MIDDLE); dateTable.addCell(hD);
        PdfPCell hSp = cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);
        hSp.setColspan(3);
        dateTable.addCell(hSp);

        // データ行1（1件目：紹介年月日を初期値に）
        String[][] dataRows = {
            {yr1 > 0 ? String.valueOf(yr1) : "", mo1 > 0 ? String.valueOf(mo1) : "", dy1 > 0 ? String.valueOf(dy1) : ""},
            {"", "", ""},
            {"", "", ""}
        };
        for (int i = 0; i < 3; i++) {
            PdfPCell cy = cell(dataRows[i][0], normalFont, Rectangle.BOX, Element.ALIGN_CENTER);
            cy.setFixedHeight(ROW_H2); cy.setVerticalAlignment(Element.ALIGN_MIDDLE); dateTable.addCell(cy);
            PdfPCell cm = cell(dataRows[i][1], normalFont, Rectangle.BOX, Element.ALIGN_CENTER);
            cm.setFixedHeight(ROW_H2); cm.setVerticalAlignment(Element.ALIGN_MIDDLE); dateTable.addCell(cm);
            PdfPCell cd = cell(dataRows[i][2], normalFont, Rectangle.BOX, Element.ALIGN_CENTER);
            cd.setFixedHeight(ROW_H2); cd.setVerticalAlignment(Element.ALIGN_MIDDLE); dateTable.addCell(cd);
            if (i == 2) {
                // 最下行：合計を右側に（中央揃え）
                dateTable.addCell(cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT));
                PdfPCell gLabel = cell("合計", boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
                gLabel.setVerticalAlignment(Element.ALIGN_MIDDLE); dateTable.addCell(gLabel);
                PdfPCell gAmt = cell(String.format("%,d　円", receptionFee), boldFont, Rectangle.BOX, Element.ALIGN_CENTER);
                gAmt.setVerticalAlignment(Element.ALIGN_MIDDLE); dateTable.addCell(gAmt);
            } else {
                PdfPCell sp = cell("", normalFont, Rectangle.NO_BORDER, Element.ALIGN_LEFT);
                sp.setColspan(3); dateTable.addCell(sp);
            }
        }

        doc.add(dateTable);

        // ── ⑥ 注意書き ─────────────────────────────────────────
        doc.add(new Paragraph(" ", normalFont));
        Paragraph note1 = new Paragraph("（注）求職受付手数料は求職のお申し込み1回ごとにいただいております。", smallFont);
        Paragraph note2 = new Paragraph("　　　求職お申し込みが1か月に3回を超える場合は、3回分の金額です。", smallFont);
        note1.setSpacingAfter(6);
        doc.add(note1);
        doc.add(note2);

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

    // ─── 領収番号リセット（1-7-1 求人者宛） ──────────────────────
    @PostMapping("/customer-receipt/reset")
    public String resetCustomerReceiptNo(@RequestParam Long detailId,
                                          HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        salesDetailRepository.findById(detailId).ifPresent(d -> {
            d.setReceiptNo(null);
            d.setIssuedAt(null);
            salesDetailRepository.save(d);
        });
        return "redirect:/receipt-menu/customer-receipt";
    }

    // ─── 領収番号リセット（1-7-2 求職受付） ──────────────────────
    @PostMapping("/jobseeker-receipt/reset")
    public String resetJobseekerReceiptNo(@RequestParam Long detailId,
                                           HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        salesDetailRepository.findById(detailId).ifPresent(d -> {
            d.setReceiptNo(null);
            d.setIssuedAt(null);
            salesDetailRepository.save(d);
        });
        return "redirect:/receipt-menu/jobseeker-receipt";
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
