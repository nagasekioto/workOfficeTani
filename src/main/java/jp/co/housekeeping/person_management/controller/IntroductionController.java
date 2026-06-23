package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.Introduction;
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.IntroductionRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;

@Controller
@RequestMapping("/introduction")
public class IntroductionController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private IntroductionRepository introductionRepository;

    private boolean checkAuth(HttpSession session) {
        return session.getAttribute("authenticated") != null;
    }

    // 1-6-1 紹介状入力
    @GetMapping("")
    public String introduction(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customers", customerRepository.findAll());
        return "introduction";
    }

    // 1-6-1 修正（既存データ読み込み）
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customers", customerRepository.findAll());
        introductionRepository.findById(id).ifPresent(intro -> model.addAttribute("intro", intro));
        return "introduction";
    }

    // 保存API（新規・更新）
    @PostMapping("/save")
    @ResponseBody
    public String save(
            @RequestParam(required = false) Long editId,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String introDate,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String formData,
            HttpSession session) {
        if (!checkAuth(session)) return "UNAUTHORIZED";

        Introduction intro;
        String refNo;

        if (editId != null) {
            // 更新
            intro = introductionRepository.findById(editId).orElse(new Introduction());
            refNo = intro.getRefNo();
        } else {
            // 新規採番
            int maxNo = introductionRepository.findMaxRefNo();
            refNo = String.format("%04d", maxNo + 1);
            intro = new Introduction();
            intro.setRefNo(refNo);
        }

        intro.setPersonId(personId);
        intro.setCustomerId(customerId);
        try { if (introDate != null && !introDate.isBlank()) intro.setIntroDate(LocalDate.parse(introDate)); } catch (Exception ignored) {}
        try { if (startDate != null && !startDate.isBlank()) intro.setStartDate(LocalDate.parse(startDate)); } catch (Exception ignored) {}
        intro.setFormData(formData);
        introductionRepository.save(intro);
        return refNo;
    }

    // 1-6-2 紹介状一覧
    @GetMapping("/list")
    public String list(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";

        Iterable<Introduction> intros = introductionRepository.findAllOrderByCreatedAtDesc();
        model.addAttribute("introductions", intros);

        // 求職者・求人者名マップ
        Map<Long, String> personMap = new HashMap<>();
        StreamSupport.stream(personRepository.findAll().spliterator(), false).forEach(p ->
            personMap.put(p.getId(), p.getLastNameKanji() + " " + p.getFirstNameKanji()));

        Map<Long, String> customerMap = new HashMap<>();
        StreamSupport.stream(customerRepository.findAll().spliterator(), false).forEach(c ->
            customerMap.put(c.getId(), c.getLastNameKanji() + " " + c.getFirstNameKanji()));

        model.addAttribute("personMap", personMap);
        model.addAttribute("customerMap", customerMap);
        return "introduction-list";
    }

    // 削除（個別）
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        introductionRepository.deleteById(id);
        return "redirect:/introduction/list";
    }

    // ─── 一括削除 ──────────────────────────────────────
    @PostMapping("/delete-all")
    public String deleteAll(HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        introductionRepository.deleteAll();
        return "redirect:/introduction/list";
    }

    // ─── 1-6-3 紹介状 PDF一括エクスポート（ZIP） ────────
    @GetMapping("/export-pdf")
    public void exportPdfZip(HttpSession session, HttpServletResponse response)
            throws IOException {
        if (!checkAuth(session)) { response.sendError(401); return; }

        List<Introduction> intros = new ArrayList<>();
        introductionRepository.findAllOrderByCreatedAtDesc().forEach(intros::add);

        Map<Long, String> personMap = new HashMap<>();
        StreamSupport.stream(personRepository.findAll().spliterator(), false)
            .forEach(p -> personMap.put(p.getId(),
                p.getLastNameKanji() + " " + p.getFirstNameKanji()));
        Map<Long, String> customerMap = new HashMap<>();
        StreamSupport.stream(customerRepository.findAll().spliterator(), false)
            .forEach(c -> customerMap.put(c.getId(),
                c.getLastNameKanji() + " " + c.getFirstNameKanji()));

        String zipName = "紹介状一括_"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
            "attachment; filename*=UTF-8''"
            + java.net.URLEncoder.encode(zipName, "UTF-8").replace("+", "%20"));

        ObjectMapper mapper = new ObjectMapper();

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Introduction intro : intros) {
                String refNo = intro.getRefNo() != null ? intro.getRefNo() : "0000";
                String personName  = intro.getPersonId()   != null
                    ? personMap.getOrDefault(intro.getPersonId(),   "不明") : "不明";
                String customerName = intro.getCustomerId() != null
                    ? customerMap.getOrDefault(intro.getCustomerId(), "不明") : "不明";
                String introDateStr = intro.getIntroDate() != null
                    ? intro.getIntroDate().toString() : "";

                // formDataをパース
                JsonNode fd;
                try {
                    String raw = intro.getFormData();
                    fd = (raw != null && !raw.isBlank() && !raw.equals("{}"))
                        ? mapper.readTree(raw) : mapper.createObjectNode();
                } catch (Exception e) {
                    fd = mapper.createObjectNode();
                }

                // 3種類のPDFを生成してZIPに追加
                String[][] docTypes = {
                    {"求職者控", "労働条件明示書"},
                    {"求人者控", "紹介状"},
                    {"求人者控", "雇用契約書"}
                };
                for (String[] docType : docTypes) {
                    String corner = docType[0];
                    String title  = docType[1];
                    String safePersonName   = personName.replaceAll("[\\/:*?"<>|]", "_");
                    String safeCustomerName = customerName.replaceAll("[\\/:*?"<>|]", "_");
                    String fileName = refNo + "_" + title + "_" + safePersonName
                        + "_" + safeCustomerName + ".pdf";

                    byte[] pdfBytes = generateIntroPdf(
                        corner, title, refNo, introDateStr,
                        personName, customerName, fd);

                    zos.putNextEntry(new ZipEntry(fileName));
                    zos.write(pdfBytes);
                    zos.closeEntry();
                }
            }
        }
    }

    // ─── iText PDF生成（1通） ────────────────────────────
    private byte[] generateIntroPdf(String corner, String title,
            String refNo, String introDate,
            String personName, String customerName,
            JsonNode fd) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 28, 28, 28, 28);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            BaseFont bf = BaseFont.createFont(
                "HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont  = new Font(bf, 14, Font.BOLD);
            Font boldFont   = new Font(bf, 9,  Font.BOLD);
            Font normalFont = new Font(bf, 9);
            Font smallFont  = new Font(bf, 8);

            // ── ヘッダー行（角枠 + 紹介番号） ────────────
            PdfPTable hdr = new PdfPTable(new float[]{1, 3});
            hdr.setWidthPercentage(100);
            hdr.setSpacingAfter(4);
            PdfPCell cornerCell = new PdfPCell(new Phrase(corner, boldFont));
            cornerCell.setBorder(Rectangle.BOX); cornerCell.setPadding(3);
            cornerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            hdr.addCell(cornerCell);
            PdfPCell refCell = new PdfPCell(new Phrase("紹介番号　" + refNo, normalFont));
            refCell.setBorder(Rectangle.NO_BORDER);
            refCell.setHorizontalAlignment(Element.ALIGN_RIGHT); refCell.setPadding(3);
            hdr.addCell(refCell);
            doc.add(hdr);

            // ── タイトル ──────────────────────────────────
            String titleSpaced = title.replace("", "　").trim();
            // 文字間スペース（簡易）
            String[] tc = title.split("");
            StringBuilder sb = new StringBuilder();
            for (String ch : tc) { if (sb.length() > 0) sb.append("　"); sb.append(ch); }
            Paragraph titleP = new Paragraph(sb.toString(), titleFont);
            titleP.setAlignment(Element.ALIGN_CENTER);
            titleP.setSpacingAfter(4);
            doc.add(titleP);

            // ── 紹介年月日 ────────────────────────────────
            Paragraph dateP = new Paragraph("紹介年月日：" + introDate, normalFont);
            dateP.setAlignment(Element.ALIGN_RIGHT);
            dateP.setSpacingAfter(3);
            doc.add(dateP);

            // ── 求人者 ────────────────────────────────────
            Paragraph kyuninP = new Paragraph("求人者　" + customerName + "　様", boldFont);
            kyuninP.setSpacingAfter(2);
            doc.add(kyuninP);

            Paragraph noticeP = new Paragraph("お申込みにより下記の者を紹介いたします。", smallFont);
            noticeP.setSpacingAfter(3);
            doc.add(noticeP);

            // ── 求職者氏名 ────────────────────────────────
            PdfPTable personBox = new PdfPTable(new float[]{1, 3});
            personBox.setWidthPercentage(100); personBox.setSpacingAfter(3);
            PdfPCell pLabel = new PdfPCell(new Phrase("求職者氏名", boldFont));
            pLabel.setBorder(Rectangle.NO_BORDER); pLabel.setPadding(3);
            personBox.addCell(pLabel);
            PdfPCell pName = new PdfPCell(new Phrase(personName, boldFont));
            pName.setBorder(Rectangle.BOTTOM); pName.setPadding(3);
            personBox.addCell(pName);
            doc.add(personBox);

            Paragraph subTitle = new Paragraph("ご依頼の内容及び雇用条件", smallFont);
            subTitle.setSpacingAfter(3);
            doc.add(subTitle);

            // ── 雇用条件テーブル ──────────────────────────
            PdfPTable tbl = new PdfPTable(new float[]{2, 5});
            tbl.setWidthPercentage(100);

            // ヘルパー
            java.util.function.BiConsumer<String, String> addRow = (key, val) -> {
                PdfPCell k = new PdfPCell(new Phrase(key, boldFont));
                k.setBackgroundColor(new BaseColor(240,240,240));
                k.setBorder(Rectangle.BOX); k.setPadding(3);
                k.setVerticalAlignment(Element.ALIGN_MIDDLE);
                tbl.addCell(k);
                PdfPCell v = new PdfPCell(new Phrase(val != null ? val : "", normalFont));
                v.setBorder(Rectangle.BOX); v.setPadding(3);
                tbl.addCell(v);
            };

            // 職務内容
            StringBuilder jobSb = new StringBuilder();
            if (fd.path("jobKaji").asBoolean())  jobSb.append("✓家事サービス　");
            if (fd.path("jobKaigo").asBoolean()) jobSb.append("✓介護サービス　");
            if (fd.path("jobOther").asBoolean()) {
                jobSb.append("✓その他");
                String ot = fd.path("jobOtherText").asText("");
                if (!ot.isBlank()) jobSb.append("：").append(ot);
            }
            addRow.accept("職務内容", jobSb.toString());

            // 就業場所
            String postal    = fd.path("workPostal").asText("");
            String place     = fd.path("workPlace").asText("");
            String station   = fd.path("workStation").asText("");
            String line      = fd.path("workLine").asText("");
            String access    = fd.path("workAccess").asText("");
            String smokingV  = fd.path("smoking").asText("");
            addRow.accept("就業場所",
                "〒" + postal + "　" + place + "
"
                + "最寄り駅　" + station + "　" + line + "線　徒歩・バス " + access + "
"
                + "受動喫煙防止措置状況　" + smokingV);

            // 雇用期間
            String empType = fd.path("empPeriod").asText("無期");
            String empPeriod = "";
            if ("有期".equals(empType)) {
                empPeriod = fd.path("empFrom").asText("") + " 〜 " + fd.path("empTo").asText("");
            }
            addRow.accept("雇用期間", empType + (empPeriod.isBlank() ? "" : "　" + empPeriod));

            // 試用期間
            String trial = fd.path("trialPeriod").asText("無");
            String trialStr = trial;
            if ("有".equals(trial)) {
                String tf = fd.path("trialFrom").asText(""), tt = fd.path("trialTo").asText("");
                trialStr += "（詳細は備考欄）";
                if (!tf.isBlank() || !tt.isBlank()) trialStr += "　" + tf + " 〜 " + tt;
            }
            addRow.accept("試用期間", trialStr);

            // 勤務日
            addRow.accept("勤務日",
                fd.path("workStyle").asText("通勤") + "　" + fd.path("dow").asText(""));

            // 勤務時間
            String sh = fd.path("wSH").asText(""), sm = fd.path("wSM").asText("");
            String eh = fd.path("wEH").asText(""), em = fd.path("wEM").asText("");
            String hoursStr = (sh.isBlank() || sm.isBlank()) ? "" : sh + ":" + sm + " 〜 " + eh + ":" + em;
            String actual = fd.path("actualHours").asText("");
            addRow.accept("勤務時間", hoursStr + (actual.isBlank() ? "" : "　〔実働　" + actual + "〕"));

            addRow.accept("時間外労働", fd.path("overtime").asText("無"));
            addRow.accept("休憩時間",   fd.path("breakTime").asText(""));

            // 休日
            StringBuilder holSb = new StringBuilder();
            if (fd.path("holWeekly").asBoolean())   holSb.append("毎週　");
            if (fd.path("holBiWeekly").asBoolean())  holSb.append("隔週　");
            String holDow = fd.path("holDow").asText("");
            if (!holDow.isBlank()) holSb.append(holDow).append("曜日　");
            if (fd.path("holHoliday").asBoolean())   holSb.append("祝日　");
            if (fd.path("holOther").asBoolean())     holSb.append("その他");
            addRow.accept("休　日", holSb.toString().trim());

            // 賃金
            String wageType = fd.path("wageType").asText("時給");
            String baseWage = fd.path("baseWage").asText("");
            try {
                if (!baseWage.isBlank())
                    baseWage = String.format("%,d", Long.parseLong(baseWage));
            } catch (NumberFormatException ignored) {}
            addRow.accept("賃金形態", wageType + "　　基本給　" + baseWage + "　円");

            String owage = fd.path("overtimeWage").asText("");
            try {
                if (!owage.isBlank())
                    owage = String.format("%,d", Long.parseLong(owage));
            } catch (NumberFormatException ignored) {}
            addRow.accept("諸手当", "時間外手当　" + owage + "　円");

            // 交通費
            StringBuilder trSb = new StringBuilder();
            if (fd.path("transNone").asBoolean()) trSb.append("無　");
            if (fd.path("transReal").asBoolean()) trSb.append("往復の実費　");
            if (fd.path("transYes").asBoolean()) {
                trSb.append("有");
                String ta = fd.path("transportAmt").asText("");
                if (!ta.isBlank()) trSb.append("　").append(ta).append("円");
            }
            addRow.accept("交通費", trSb.toString().trim());

            addRow.accept("昇給", fd.path("raise").asText("無"));

            // 賃金支払方法
            StringBuilder paySb = new StringBuilder();
            if (fd.path("payDaily").asBoolean())   paySb.append("毎日　");
            if (fd.path("payWeekly").asBoolean())  paySb.append("毎週").append(fd.path("payWeeklyDay").asText("")).append("曜日　");
            if (fd.path("payMonthly").asBoolean()) paySb.append("毎月").append(fd.path("payMonthlyDay").asText("")).append("日　");
            String pm = fd.path("payMethod").asText("");
            if (!pm.isBlank()) paySb.append("方法：").append(pm);
            addRow.accept("賃金支払方法", paySb.toString().trim());

            // 社会・労働保険
            StringBuilder insSb = new StringBuilder();
            if (fd.path("insHealth").asBoolean())      insSb.append("健康保険　");
            if (fd.path("insPension").asBoolean())     insSb.append("厚生年金　");
            if (fd.path("insEmploy").asBoolean())      insSb.append("雇用　");
            if (fd.path("insWork").asBoolean())        insSb.append("労災　");
            if (fd.path("insWorkSpecial").asBoolean()) insSb.append("労災特別加入　");
            if (fd.path("insOther").asBoolean()) {
                insSb.append("その他");
                String ot2 = fd.path("insOtherText").asText("");
                if (!ot2.isBlank()) insSb.append("：").append(ot2);
            }
            // 手数料を右寄せで追加
            String insStr = insSb.toString().trim() + "\n（紹介手数料15%　＋消費税）";
            addRow.accept("社会・労働保険
加入状況", insStr);

            addRow.accept("備考", fd.path("remarks").asText(""));

            doc.add(tbl);

            // ── 注意文 ────────────────────────────────────
            Paragraph note = new Paragraph(
                "1. 手数料は別表のとおりです。2. 採否については至急に電話または書面によりご連絡をお願いいたします。",
                smallFont);
            note.setSpacingBefore(4);
            doc.add(note);

            // ── フッター ──────────────────────────────────
            PdfPTable footer = new PdfPTable(1);
            footer.setWidthPercentage(100);
            footer.setSpacingBefore(6);
            for (String line2 : new String[]{
                    "所在地　〒107-0052 東京都港区赤坂6-10-45-203",
                    "紹介所　有限会社　ワークオフィス谷",
                    "代表者　代表取締役　谷 二三代",
                    "連絡先　TEL 03-5544-8315　FAX 03-5544-8316"}) {
                Font ff = (line2.contains("有限会社")) ? boldFont : smallFont;
                PdfPCell fc = new PdfPCell(new Phrase(line2, ff));
                fc.setBorder(Rectangle.NO_BORDER);
                fc.setHorizontalAlignment(Element.ALIGN_RIGHT);
                fc.setPadding(2);
                footer.addCell(fc);
            }
            PdfPCell fBorder = new PdfPCell(footer);
            fBorder.setBorder(Rectangle.BOX); fBorder.setPadding(4);
            PdfPTable fWrap = new PdfPTable(1);
            fWrap.setWidthPercentage(100); fWrap.setSpacingBefore(6);
            fWrap.addCell(fBorder);
            doc.add(fWrap);

            doc.close();
        } catch (DocumentException e) {
            throw new IOException("PDF生成エラー: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    // ─── 1-6-3 紹介状一覧 Excel エクスポート ──────────
    @GetMapping("/export")
    public void exportExcel(HttpSession session, HttpServletResponse response)
            throws IOException {
        if (!checkAuth(session)) { response.sendError(401); return; }

        // データ収集
        List<Introduction> intros = new ArrayList<>();
        introductionRepository.findAllOrderByCreatedAtDesc().forEach(intros::add);

        Map<Long, String> personMap = new HashMap<>();
        StreamSupport.stream(personRepository.findAll().spliterator(), false)
            .forEach(p -> personMap.put(p.getId(),
                p.getLastNameKanji() + " " + p.getFirstNameKanji()));

        Map<Long, String> customerMap = new HashMap<>();
        StreamSupport.stream(customerRepository.findAll().spliterator(), false)
            .forEach(c -> customerMap.put(c.getId(),
                c.getLastNameKanji() + " " + c.getFirstNameKanji()));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("紹介状一覧");

            // ── ヘッダースタイル ──────────────────────
            CellStyle hStyle = wb.createCellStyle();
            hStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hStyle.setAlignment(HorizontalAlignment.CENTER);
            hStyle.setBorderBottom(BorderStyle.THIN);
            hStyle.setBorderTop(BorderStyle.THIN);
            hStyle.setBorderLeft(BorderStyle.THIN);
            hStyle.setBorderRight(BorderStyle.THIN);
            Font hFont = wb.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            hFont.setFontName("メイリオ");
            hStyle.setFont(hFont);

            // ── データスタイル ─────────────────────────
            CellStyle dStyle = wb.createCellStyle();
            dStyle.setBorderBottom(BorderStyle.THIN);
            dStyle.setBorderTop(BorderStyle.THIN);
            dStyle.setBorderLeft(BorderStyle.THIN);
            dStyle.setBorderRight(BorderStyle.THIN);
            Font dFont = wb.createFont();
            dFont.setFontName("メイリオ");
            dStyle.setFont(dFont);

            // ── ヘッダー行 ────────────────────────────
            String[] headers = {
                "紹介番号", "紹介年月日", "求職者（家政婦）", "求人者", "登録日時"
            };
            Row hRow = sheet.createRow(0);
            hRow.setHeightInPoints(20);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = hRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(hStyle);
            }

            // ── データ行 ──────────────────────────────
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
            int rowNum = 1;
            for (Introduction intro : intros) {
                Row row = sheet.createRow(rowNum++);
                row.setHeightInPoints(18);

                String[] vals = {
                    intro.getRefNo() != null ? intro.getRefNo() : "",
                    intro.getIntroDate() != null ? intro.getIntroDate().toString() : "",
                    intro.getPersonId() != null
                        ? personMap.getOrDefault(intro.getPersonId(), "") : "",
                    intro.getCustomerId() != null
                        ? customerMap.getOrDefault(intro.getCustomerId(), "") : "",
                    intro.getCreatedAt() != null
                        ? intro.getCreatedAt().format(dtf) : ""
                };
                for (int i = 0; i < vals.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(vals[i]);
                    cell.setCellStyle(dStyle);
                }
            }

            // ── 列幅自動調整 ─────────────────────────
            int[] colWidths = {12, 14, 20, 20, 20};
            for (int i = 0; i < colWidths.length; i++) {
                sheet.setColumnWidth(i, colWidths[i] * 256);
            }

            // ── ファイル出力 ──────────────────────────
            String fileName = "紹介状一覧_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                + ".xlsx";
            response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" +
                java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"));
            wb.write(response.getOutputStream());
            response.getOutputStream().flush();
        }
    }
}
