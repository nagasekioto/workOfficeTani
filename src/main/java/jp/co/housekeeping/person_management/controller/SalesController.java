package jp.co.housekeeping.person_management.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
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
public class SalesController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;

    // ─── 売上入力画面 ───────────────────────────────
    @GetMapping("/person/sales")
    public String sales(@RequestParam(required = false) Long personId,
                        HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customers", customerRepository.findAll());

        if (personId != null) {
            Person person = personRepository.findById(personId).orElse(null);
            List<Sales> salesList = salesRepository.findByPersonId(personId);
            List<SalesDetail> allDetails = new ArrayList<>();
            for (Sales s : salesList) {
                allDetails.addAll(salesDetailRepository.findBySalesId(s.getId()));
            }
            model.addAttribute("selectedPerson", person);
            model.addAttribute("selectedPersonId", personId);
            model.addAttribute("existingDetails", allDetails);
        }

        return "person-sales";
    }

    // ─── 保存処理 ───────────────────────────────────
    @PostMapping("/person/sales/save")
    public String saveSales(
            @RequestParam Long personId,
            // 勤務先1～5の各項目（配列）
            @RequestParam(required = false) Long[] customerIds,
            @RequestParam(required = false) String[] introductionDates,
            @RequestParam(required = false) Integer[] receptionFees,
            @RequestParam(required = false) Integer[] customerFees,
            @RequestParam(required = false) Integer[] hourlyWages,
            @RequestParam(required = false) Integer[] hourlyWageOvertimes,
            @RequestParam(required = false) String[] dailyWagesList,
            @RequestParam(required = false) String[] workStartDates,
            @RequestParam(required = false) String[] workEndDates,
            @RequestParam(required = false) String[] workingHoursList,
            @RequestParam(required = false) String[] remarksList,
            HttpSession session) {

        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        // 既存salesレコードを取得 or 新規作成（person単位で1レコード）
        List<Sales> existing = salesRepository.findByPersonId(personId);
        Sales sales;
        if (existing.isEmpty()) {
            sales = new Sales();
            sales.setPersonId(personId);
            sales = salesRepository.save(sales);
        } else {
            sales = existing.get(0);
        }

        // 既存詳細を削除して再登録
        List<SalesDetail> oldDetails = salesDetailRepository.findBySalesId(sales.getId());
        for (SalesDetail od : oldDetails) {
            salesDetailRepository.deleteById(od.getId());
        }

        if (customerIds == null) {
            return "redirect:/person/sales?saved=" + personId;
        }

        for (int i = 0; i < customerIds.length; i++) {
            if (customerIds[i] == null) continue;

            SalesDetail detail = new SalesDetail();
            detail.setSalesId(sales.getId());
            detail.setCustomerId(customerIds[i]);
            detail.setDetailOrder(i + 1);

            if (introductionDates != null && i < introductionDates.length && !introductionDates[i].isBlank()) {
                detail.setIntroductionDate(LocalDate.parse(introductionDates[i]));
            }
            if (receptionFees != null && i < receptionFees.length) {
                detail.setReceptionFee(receptionFees[i]);
            }
            if (customerFees != null && i < customerFees.length) {
                detail.setCustomerFee(customerFees[i]);
            }
            if (hourlyWages != null && i < hourlyWages.length) {
                detail.setHourlyWage(hourlyWages[i]);
            }
            if (hourlyWageOvertimes != null && i < hourlyWageOvertimes.length) {
                detail.setHourlyWageOvertime(hourlyWageOvertimes[i]);
            }
            if (dailyWagesList != null && i < dailyWagesList.length && dailyWagesList[i] != null) {
                detail.setDailyWages(dailyWagesList[i]);
            }
            if (workStartDates != null && i < workStartDates.length && workStartDates[i] != null && !workStartDates[i].isBlank()) {
                detail.setWorkStartDate(LocalDate.parse(workStartDates[i]));
            }
            if (workEndDates != null && i < workEndDates.length && workEndDates[i] != null && !workEndDates[i].isBlank()) {
                detail.setWorkEndDate(LocalDate.parse(workEndDates[i]));
            }
            if (workingHoursList != null && i < workingHoursList.length && workingHoursList[i] != null && !workingHoursList[i].isBlank()) {
                try { detail.setWorkingHours(new BigDecimal(workingHoursList[i])); } catch (NumberFormatException ignored) {}
            }
            if (remarksList != null && i < remarksList.length) {
                detail.setRemarks(remarksList[i]);
            }

            // 領収書Noを採番
            String receiptNo = generateReceiptNo();
            detail.setReceiptNo(receiptNo);

            detail.calculateAmounts();
            salesDetailRepository.save(detail);
        }

        return "redirect:/person/sales?saved=" + personId;
    }

    // 領収書No採番（0001～）
    private String generateReceiptNo() {
        int maxNo = salesDetailRepository.findMaxReceiptNo();
        return String.format("%04d", maxNo + 1);
    }

    // ─── PDF印刷 ─────────────────────────────────────
    @PostMapping("/person/sales/print")
    public void printSales(
            @RequestParam Long personId,
            @RequestParam(required = false) Long[] customerIds,
            @RequestParam(required = false) String[] introductionDates,
            @RequestParam(required = false) Integer[] receptionFees,
            @RequestParam(required = false) Integer[] customerFees,
            @RequestParam(required = false) Integer[] hourlyWages,
            @RequestParam(required = false) Integer[] hourlyWageOvertimes,
            @RequestParam(required = false) String[] dailyWagesList,
            @RequestParam(required = false) String[] workStartDates,
            @RequestParam(required = false) String[] workEndDates,
            @RequestParam(required = false) String[] workingHoursList,
            @RequestParam(required = false) String[] remarksList,
            HttpServletResponse response) throws IOException, DocumentException {

        Person person = personRepository.findById(personId).orElse(null);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=sales_report.pdf");

        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        BaseFont baseFont = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont  = new Font(baseFont, 16, Font.BOLD);
        Font headerFont = new Font(baseFont, 11, Font.BOLD);
        Font normalFont = new Font(baseFont, 10);
        Font smallFont  = new Font(baseFont, 9);

        // タイトル
        Paragraph title = new Paragraph("求人受付・紹介手数料　領収証書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        if (customerIds == null) { document.close(); return; }

        for (int i = 0; i < customerIds.length; i++) {
            if (customerIds[i] == null) continue;

            Customer customer = customerRepository.findById(customerIds[i]).orElse(null);
            if (customer == null) continue;

            // 勤務先ブロック
            PdfPTable block = new PdfPTable(4);
            block.setWidthPercentage(100);
            block.setSpacingBefore(10);
            block.setWidths(new float[]{2f, 3f, 2f, 3f});

            String customerName = customer.getLastNameKanji() + " " + customer.getFirstNameKanji();
            String personName   = person != null ? person.getLastNameKanji() + " " + person.getFirstNameKanji() : "";

            addCell2(block, "求人者", customerName, headerFont, normalFont);
            addCell2(block, "求職者（家政婦）", personName, headerFont, normalFont);

            String introDate = (introductionDates != null && i < introductionDates.length) ? introductionDates[i] : "";
            addCell2(block, "紹介年月日", introDate, headerFont, normalFont);

            String workPeriod = "";
            if (workStartDates != null && i < workStartDates.length && workStartDates[i] != null && !workStartDates[i].isBlank()) {
                workPeriod = workStartDates[i];
                if (workEndDates != null && i < workEndDates.length && workEndDates[i] != null && !workEndDates[i].isBlank()) {
                    workPeriod += " ～ " + workEndDates[i];
                }
            }
            addCell2(block, "就労月日", workPeriod, headerFont, normalFont);

            Integer wage = (hourlyWages != null && i < hourlyWages.length) ? hourlyWages[i] : null;
            addCell2(block, "時給（日給）", wage != null ? "¥" + String.format("%,d", wage) : "-", headerFont, normalFont);

            Integer wageOT = (hourlyWageOvertimes != null && i < hourlyWageOvertimes.length) ? hourlyWageOvertimes[i] : null;
            addCell2(block, "時給（残業）", wageOT != null ? "¥" + String.format("%,d", wageOT) : "-", headerFont, normalFont);

            // 日給
            String dailyWages = (dailyWagesList != null && i < dailyWagesList.length) ? dailyWagesList[i] : "";
            addCell2(block, "日給", dailyWages != null ? dailyWages.replace(",", " / ") : "-", headerFont, normalFont);

            // 受付料
            Integer recFee = (receptionFees != null && i < receptionFees.length) ? receptionFees[i] : null;
            addCell2(block, "受付料", recFee != null ? "¥" + String.format("%,d", recFee) : "-", headerFont, normalFont);

            // 求人受付手数料
            Integer cusFee = (customerFees != null && i < customerFees.length) ? customerFees[i] : null;
            addCell2(block, "求人受付手数料", cusFee != null ? "¥" + String.format("%,d", cusFee) : "-", headerFont, normalFont);

            // 賃金計算
            int totalWage = 0;
            if (wage != null && workingHoursList != null && i < workingHoursList.length && workingHoursList[i] != null && !workingHoursList[i].isBlank()) {
                try { totalWage += (int)(wage * Double.parseDouble(workingHoursList[i])); } catch (NumberFormatException ignored) {}
            }
            if (dailyWages != null && !dailyWages.isBlank()) {
                for (String dw : dailyWages.split(",")) {
                    try { totalWage += Integer.parseInt(dw.trim()); } catch (NumberFormatException ignored) {}
                }
            }
            int commission = (int)(totalWage * 0.15);
            int tax        = (int)(commission * 0.10);
            int grandTotal = totalWage + commission + tax + (recFee != null ? recFee : 0) + (cusFee != null ? cusFee : 0);

            addCell2(block, "賃金総額", "¥" + String.format("%,d", totalWage), headerFont, normalFont);
            addCell2(block, "手数料（15%）", "¥" + String.format("%,d", commission), headerFont, normalFont);
            addCell2(block, "消費税（10%）", "¥" + String.format("%,d", tax), headerFont, normalFont);
            addCell2(block, "合計金額", "¥" + String.format("%,d", grandTotal), headerFont, headerFont);

            document.add(block);

            // 区切り線
            Paragraph sep = new Paragraph("─────────────────────────────────────────", smallFont);
            sep.setSpacingBefore(5);
            sep.setSpacingAfter(5);
            document.add(sep);
        }

        // 事務所情報
        Paragraph footer = new Paragraph("\n\nワークオフィス谷　家政婦紹介事務所", normalFont);
        footer.setAlignment(Element.ALIGN_RIGHT);
        document.add(footer);

        document.close();
    }

    private void addCell2(PdfPTable table, String key, String value, Font keyFont, Font valFont) {
        PdfPCell kc = new PdfPCell(new Phrase(key, keyFont));
        kc.setPadding(5);
        kc.setBackgroundColor(new BaseColor(230, 230, 230));
        table.addCell(kc);

        PdfPCell vc = new PdfPCell(new Phrase(value != null ? value : "", valFont));
        vc.setPadding(5);
        table.addCell(vc);
    }
}
