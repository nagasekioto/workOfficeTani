package jp.co.housekeeping.person_management.controller;

import java.io.IOException;
import java.math.BigDecimal;

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
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
public class SalesController {
    
    @Autowired
    private PersonRepository personRepository;
    
    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private SalesRepository salesRepository;
    
    @Autowired
    private SalesDetailRepository salesDetailRepository;
    
    @GetMapping("/person/sales")
    public String sales(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        
        Iterable<Person> persons = personRepository.findAll();
        Iterable<Customer> customers = customerRepository.findAll();
        
        model.addAttribute("persons", persons);
        model.addAttribute("customers", customers);
        
        return "person-sales";
    }
    
    @PostMapping("/person/sales/print")
    public void printSales(@RequestParam Long personId,
                          @RequestParam String introductionDate,
                          @RequestParam Integer receptionFee,
                          @RequestParam String receiptNo,
                          @RequestParam(required = false) Long[] customerIds,
                          @RequestParam(required = false) Integer[] hourlyWages,
                          @RequestParam(required = false) String[] workingHours,
                          HttpServletResponse response) throws IOException, DocumentException {
        
        // 求職者情報取得
        Person person = personRepository.findById(personId).orElse(null);
        
        // PDF生成
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=sales_report.pdf");
        
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, response.getOutputStream());
        
        document.open();
        
        // 日本語フォント
        BaseFont baseFont = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont = new Font(baseFont, 20, Font.BOLD);
        Font headerFont = new Font(baseFont, 14, Font.BOLD);
        Font normalFont = new Font(baseFont, 12);
        
        // タイトル
        Paragraph title = new Paragraph("売上報告書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(30);
        document.add(title);
        
        // 基本情報
        if (person != null) {
            document.add(new Paragraph("求職者: " + person.getLastNameKanji() + " " + person.getFirstNameKanji(), headerFont));
        }
        document.add(new Paragraph("紹介年月日: " + introductionDate, normalFont));
        document.add(new Paragraph("受付料金: ¥" + String.format("%,d", receptionFee), normalFont));
        document.add(new Paragraph("領収書No: " + receiptNo, normalFont));
        document.add(new Paragraph("\n", normalFont));
        
        // 詳細テーブル
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new int[]{3, 2, 2, 3, 3, 3});
        
        addHeaderCell(table, "求人者", baseFont);
        addHeaderCell(table, "時給", baseFont);
        addHeaderCell(table, "時間", baseFont);
        addHeaderCell(table, "給与", baseFont);
        addHeaderCell(table, "手数料", baseFont);
        addHeaderCell(table, "消費税", baseFont);
        
        int totalSalary = 0;
        int totalCommission = 0;
        int totalTax = 0;
        
        if (customerIds != null) {
            for (int i = 0; i < customerIds.length; i++) {
                if (customerIds[i] != null && hourlyWages[i] != null && workingHours[i] != null) {
                    Customer customer = customerRepository.findById(customerIds[i]).orElse(null);
                    
                    int wage = hourlyWages[i];
                    BigDecimal hours = new BigDecimal(workingHours[i]);
                    int salary = (int) (wage * hours.doubleValue());
                    int commission = (int) (salary * 0.15);
                    int tax = (int) (commission * 0.10);
                    
                    totalSalary += salary;
                    totalCommission += commission;
                    totalTax += tax;
                    
                    String customerName = customer != null ? 
                        customer.getLastNameKanji() + " " + customer.getFirstNameKanji() : "";
                    
                    addCell(table, customerName, baseFont);
                    addCell(table, "¥" + String.format("%,d", wage), baseFont);
                    addCell(table, hours + "h", baseFont);
                    addCell(table, "¥" + String.format("%,d", salary), baseFont);
                    addCell(table, "¥" + String.format("%,d", commission), baseFont);
                    addCell(table, "¥" + String.format("%,d", tax), baseFont);
                }
            }
        }
        
        document.add(table);
        
        // 合計
        document.add(new Paragraph("\n", normalFont));
        document.add(new Paragraph("給与合計: ¥" + String.format("%,d", totalSalary), headerFont));
        document.add(new Paragraph("手数料合計: ¥" + String.format("%,d", totalCommission), normalFont));
        document.add(new Paragraph("消費税合計: ¥" + String.format("%,d", totalTax), normalFont));
        
        document.close();
    }
    
    private void addHeaderCell(PdfPTable table, String text, BaseFont font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(font, 12, Font.BOLD)));
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setPadding(5);
        table.addCell(cell);
    }
    
    private void addCell(PdfPTable table, String text, BaseFont font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(font, 10)));
        cell.setPadding(5);
        table.addCell(cell);
    }
}