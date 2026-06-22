package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.Receipt;
import jp.co.housekeeping.person_management.repository.PersonRepository;

@Controller
@RequestMapping("/receipt")
public class ReceiptController {
    
    @Autowired
    private PersonRepository personRepository;
    
    @GetMapping
    public String receiptForm(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        
        // 家政婦一覧を取得
        Iterable<Person> persons = personRepository.findAll();
        model.addAttribute("persons", persons);
        model.addAttribute("receipt", new Receipt());
        
        return "receipt-form";
    }
    
    @PostMapping("/generate-pdf")
    public void generatePdf(@ModelAttribute Receipt receipt, 
                           @RequestParam(required = false) String action,
                           HttpServletResponse response) 
            throws IOException, DocumentException {
        
        // 金額計算
        receipt.calculateAmounts();
        
        // PDFバイト配列を生成
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createPdfDocument(receipt, baos);
        byte[] pdfBytes = baos.toByteArray();
        
        // actionパラメータで動作を分岐
        if ("print".equals(action)) {
            // 印刷用：インラインで表示
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=receipt.pdf");
        } else {
            // ダウンロード用
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", 
                "attachment; filename=receipt_" + System.currentTimeMillis() + ".pdf");
        }
        
        response.setContentLength(pdfBytes.length);
        response.getOutputStream().write(pdfBytes);
        response.getOutputStream().flush();
    }
    
    // PDF生成ロジックを別メソッドに分離
    private void createPdfDocument(Receipt receipt, ByteArrayOutputStream baos) 
            throws DocumentException, IOException {
        
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // 日本語フォント設定
        BaseFont baseFont = BaseFont.createFont(
            "HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont = new Font(baseFont, 20, Font.BOLD);
        Font headerFont = new Font(baseFont, 14, Font.BOLD);
        Font normalFont = new Font(baseFont, 12);
        Font largeFont = new Font(baseFont, 16, Font.BOLD);
        
        // タイトル
        Paragraph title = new Paragraph("領　収　書", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(30);
        document.add(title);
        
        // 発行日
        Paragraph date = new Paragraph(
            "発行日: " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")), 
            normalFont);
        date.setAlignment(Element.ALIGN_RIGHT);
        date.setSpacingAfter(20);
        document.add(date);
        
        // 宛名
        Paragraph recipient = new Paragraph(
            receipt.getLastNameKanji() + " " + receipt.getFirstNameKanji() + " 様", 
            headerFont);
        recipient.setSpacingAfter(30);
        document.add(recipient);
        
        // 金額（大きく表示）
        int grandTotal = receipt.getTotalAmount() + receipt.getCommission() + receipt.getTax();
        Paragraph amount = new Paragraph(
            "金額: ¥" + String.format("%,d", grandTotal) + " 円", 
            largeFont);
        amount.setSpacingAfter(30);
        document.add(amount);
        
        // 詳細テーブル
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        table.setSpacingAfter(10);
        
        addTableRow(table, "働き先", receipt.getWorkPlace(), headerFont, normalFont);
        addTableRow(table, "勤務月", receipt.getWorkMonth(), headerFont, normalFont);
        addTableRow(table, "時給", "¥" + String.format("%,d", receipt.getHourlyWage()), 
                   headerFont, normalFont);
        addTableRow(table, "勤務時間", receipt.getWorkingHours() + " 時間", 
                   headerFont, normalFont);
        addTableRow(table, "勤務給与", "¥" + String.format("%,d", receipt.getTotalAmount()), 
                   headerFont, normalFont);
        addTableRow(table, "手数料（16.5%）", "¥" + String.format("%,d", receipt.getCommission()), 
                   headerFont, normalFont);
        addTableRow(table, "消費税（10%）", "¥" + String.format("%,d", receipt.getTax()), 
                   headerFont, normalFont);
        
        document.add(table);
        
        // 住所
        Paragraph address = new Paragraph(
            "〒" + receipt.getPostalCode() + "\n" +
            receipt.getAddress1() + " " + receipt.getAddress2() + " " + 
            (receipt.getAddress3() != null ? receipt.getAddress3() : ""), 
            normalFont);
        address.setSpacingBefore(30);
        document.add(address);
        
        // 会社情報
        Paragraph company = new Paragraph(
            "\n\n家政婦紹介事務所\n株式会社○○○", 
            normalFont);
        company.setAlignment(Element.ALIGN_RIGHT);
        document.add(company);
        
        document.close();
    }
    
    private void addTableRow(PdfPTable table, String key, String value, 
                            Font keyFont, Font valueFont) {
        PdfPCell keyCell = new PdfPCell(new Phrase(key, keyFont));
        keyCell.setPadding(8);
        keyCell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        table.addCell(keyCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setPadding(8);
        table.addCell(valueCell);
    }
}