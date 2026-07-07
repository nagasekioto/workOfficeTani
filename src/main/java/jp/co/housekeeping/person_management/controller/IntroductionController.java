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

        // formData(JSON)からempPeriod(無期/有期)とempSubType(臨時/日雇い)を抽出し、
        // 「有期」かつサブ選択がある場合はサブ選択の値(臨時/日雇い)を、
        // それ以外は主選択(無期/有期)をそのままemp_periodに保存する。
        // 例: 有期+臨時 → emp_period="臨時"、有期+該当なし → emp_period="有期"
        try {
            if (formData != null && !formData.isBlank()) {
                JsonNode node = new ObjectMapper().readTree(formData);
                String ep  = node.has("empPeriod")  ? node.get("empPeriod").asText(null)  : null;
                String sub = node.has("empSubType") ? node.get("empSubType").asText(null) : null;
                String finalEp = ("有期".equals(ep) && sub != null && !sub.isBlank()) ? sub : ep;
                intro.setEmpPeriod((finalEp == null || finalEp.isBlank()) ? null : finalEp);
            }
        } catch (Exception ignored) {}

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

    // 1-6-2 詳細表示
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        Introduction intro = introductionRepository.findById(id).orElse(null);
        if (intro == null) return "redirect:/introduction/list";

        String personName = intro.getPersonId() != null
            ? StreamSupport.stream(personRepository.findAll().spliterator(), false)
                .filter(p -> p.getId().equals(intro.getPersonId()))
                .map(p -> p.getLastNameKanji() + " " + p.getFirstNameKanji())
                .findFirst().orElse("-")
            : "-";
        String customerName = intro.getCustomerId() != null
            ? StreamSupport.stream(customerRepository.findAll().spliterator(), false)
                .filter(c -> c.getId().equals(intro.getCustomerId()))
                .map(c -> c.getLastNameKanji() + " " + c.getFirstNameKanji())
                .findFirst().orElse("-")
            : "-";

        // formDataをパースして表示用マップに変換
        Map<String, String> fd = new java.util.LinkedHashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = (intro.getFormData() != null && !intro.getFormData().isBlank())
                ? mapper.readTree(intro.getFormData()) : mapper.createObjectNode();

            // 職務内容
            List<String> jobList = new ArrayList<>();
            if (node.path("jobKaji").asBoolean()) jobList.add("家事サービス");
            if (node.path("jobKaigo").asBoolean()) jobList.add("介護サービス");
            if (node.path("jobOther").asBoolean()) {
                String ot = node.path("jobOtherText").asText("");
                jobList.add("その他" + (ot.isBlank() ? "" : "：" + ot));
            }
            fd.put("jobContent", String.join("　", jobList));
            fd.put("workPostal", node.path("workPostal").asText(""));
            fd.put("workPlace", node.path("workPlace").asText(""));
            fd.put("workStation", node.path("workStation").asText(""));
            fd.put("workLine", node.path("workLine").asText(""));
            fd.put("workAccess", node.path("workAccess").asText(""));
            fd.put("smoking", node.path("smoking").asText(""));
            fd.put("empPeriod", node.path("empPeriod").asText("無期"));
            fd.put("empFrom", node.path("empFrom").asText(""));
            fd.put("empTo", node.path("empTo").asText(""));
            fd.put("trialPeriod", node.path("trialPeriod").asText("無"));
            fd.put("workStyle", node.path("workStyle").asText(""));
            fd.put("dow", node.path("dow").asText(""));
            String sh = node.path("wSH").asText(""), sm = node.path("wSM").asText("");
            String eh = node.path("wEH").asText(""), em = node.path("wEM").asText("");
            fd.put("workHours", (!sh.isBlank() && !sm.isBlank()) ? sh + ":" + sm + " 〜 " + eh + ":" + em : "");
            fd.put("actualHours", node.path("actualHours").asText(""));
            fd.put("overtime", node.path("overtime").asText(""));
            fd.put("breakTime", node.path("breakTime").asText(""));
            // 休日
            List<String> holList = new ArrayList<>();
            if (node.path("holWeekly").asBoolean()) holList.add("毎週");
            if (node.path("holBiWeekly").asBoolean()) holList.add("隔週");
            String hd = node.path("holDow").asText("");
            if (!hd.isBlank()) holList.add(hd + "曜日");
            if (node.path("holHoliday").asBoolean()) holList.add("祝日");
            if (node.path("holOther").asBoolean()) holList.add("その他");
            fd.put("holiday", String.join("　", holList));
            // 賃金形態（時給・日給。複数選択可。月給は廃止）
            String wageTypeRaw = node.path("wageType").asText("");
            List<String> wageTypeParts = new ArrayList<>();
            for (String p : wageTypeRaw.split("・")) {
                if (p.equals("時給") || p.equals("日給")) wageTypeParts.add(p);
            }
            String wageTypeVal = String.join("・", wageTypeParts);
            fd.put("wageType", wageTypeVal);
            // 基本給（時給・日給をそれぞれ独立した行として算出／旧データ互換）
            String baseWageHourly = "";
            String baseWageDaily  = "";
            boolean hasNewWageFields = node.has("hourlyLine1") || node.has("dailyLine1")
                    || node.has("monthlyLine1") || node.has("baseWageLine1");
            if (hasNewWageFields) {
                // 現行〜一世代前のデータ：時給・日給それぞれ専用の入力欄を持つ
                List<String> hLines = new ArrayList<>();
                String h1 = node.path("hourlyLine1").asText("");
                String h2 = node.path("hourlyLine2").asText("");
                if (!h1.isBlank()) hLines.add(h1);
                if (!h2.isBlank()) hLines.add(h2);
                if (wageTypeParts.contains("時給")) baseWageHourly = String.join("\n", hLines);

                String d1 = node.path("dailyLine1").asText("");
                if (wageTypeParts.contains("日給") && !d1.isBlank()) baseWageDaily = d1;
            } else if (node.has("wageLine1")) {
                // さらに古いデータ（賃金形態が3行自由入力だった時期。型までは判別できないため時給扱い）
                baseWageHourly = node.path("wageType").asText("");
            } else {
                // 最古データ（基本給が単一の数値だった時期）
                String oldBaseWage = node.path("baseWage").asText("");
                if (!oldBaseWage.isBlank()) {
                    if (wageTypeParts.contains("日給")) baseWageDaily = oldBaseWage + "　円";
                    else baseWageHourly = oldBaseWage + "　円";
                }
            }
            fd.put("baseWageHourly", baseWageHourly);
            fd.put("baseWageDaily", baseWageDaily);
            fd.put("overtimeWage", node.path("overtimeWage").asText(""));
            // 交通費
            List<String> trList = new ArrayList<>();
            if (node.path("transNone").asBoolean()) trList.add("無");
            if (node.path("transReal").asBoolean()) trList.add("往復の実費");
            if (node.path("transYes").asBoolean()) {
                String ta = node.path("transportAmt").asText("");
                trList.add("有" + (ta.isBlank() ? "" : "　" + ta + "円"));
            }
            fd.put("transport", String.join("　", trList));
            fd.put("raise", node.path("raise").asText(""));
            // 賃金支払方法
            List<String> payList = new ArrayList<>();
            if (node.path("payDaily").asBoolean()) payList.add("毎日");
            if (node.path("payWeekly").asBoolean()) payList.add("毎週" + node.path("payWeeklyDay").asText("") + "曜日");
            if (node.path("payMonthly").asBoolean()) payList.add("毎月" + node.path("payMonthlyDay").asText("") + "日");
            String pm = node.path("payMethod").asText("");
            if (!pm.isBlank()) payList.add("方法：" + pm);
            fd.put("payMethod", String.join("　", payList));
            // 保険
            List<String> insList = new ArrayList<>();
            if (node.path("insHealth").asBoolean()) insList.add("健康保険");
            if (node.path("insPension").asBoolean()) insList.add("厚生年金");
            if (node.path("insEmploy").asBoolean()) insList.add("雇用");
            if (node.path("insWork").asBoolean()) insList.add("労災");
            if (node.path("insWorkSpecial").asBoolean()) insList.add("労災特別加入");
            if (node.path("insOther").asBoolean()) {
                String ot2 = node.path("insOtherText").asText("");
                insList.add("その他" + (ot2.isBlank() ? "" : "：" + ot2));
            }
            fd.put("insurance", String.join("　", insList));
            fd.put("remarks", node.path("remarks").asText(""));
        } catch (Exception e) {
            fd.put("jobContent", "");
        }

        model.addAttribute("intro", intro);
        model.addAttribute("personName", personName);
        model.addAttribute("customerName", customerName);
        model.addAttribute("fd", fd);
        return "introduction-detail";
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
                    String safePersonName   = personName.replaceAll("[/:*?<>|]", "_");
                    String safeCustomerName = customerName.replaceAll("[/:*?<>|]", "_");
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

            // ── ヘッダー行 ────────────────────────────────
            PdfPTable hdr = new PdfPTable(new float[]{1, 3});
            hdr.setWidthPercentage(100); hdr.setSpacingAfter(4);
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
            String[] tc = title.split("");
            StringBuilder tsb = new StringBuilder();
            for (String ch : tc) { if (tsb.length() > 0) tsb.append("　"); tsb.append(ch); }
            Paragraph titleP = new Paragraph(tsb.toString(), titleFont);
            titleP.setAlignment(Element.ALIGN_CENTER); titleP.setSpacingAfter(4);
            doc.add(titleP);

            // ── 紹介年月日 ────────────────────────────────
            Paragraph dateP = new Paragraph("紹介年月日：" + introDate, normalFont);
            dateP.setAlignment(Element.ALIGN_RIGHT); dateP.setSpacingAfter(3);
            doc.add(dateP);

            // ── 求人者 ────────────────────────────────────
            Paragraph kyuninP = new Paragraph("求人者　" + customerName + "　様", boldFont);
            kyuninP.setSpacingAfter(2); doc.add(kyuninP);
            Paragraph noticeP = new Paragraph("お申込みにより下記の者を紹介いたします。", smallFont);
            noticeP.setSpacingAfter(3); doc.add(noticeP);

            // ── 求職者氏名 ────────────────────────────────
            PdfPTable personBox = new PdfPTable(new float[]{1, 3});
            personBox.setWidthPercentage(100); personBox.setSpacingAfter(3);
            PdfPCell pLabel = new PdfPCell(new Phrase("求職者氏名", boldFont));
            pLabel.setBorder(Rectangle.NO_BORDER); pLabel.setPadding(3); personBox.addCell(pLabel);
            PdfPCell pName = new PdfPCell(new Phrase(personName, boldFont));
            pName.setBorder(Rectangle.BOTTOM); pName.setPadding(3); personBox.addCell(pName);
            doc.add(personBox);
            Paragraph subTitle = new Paragraph("ご依頼の内容及び雇用条件", smallFont);
            subTitle.setSpacingAfter(3); doc.add(subTitle);

            // ── 雇用条件テーブル ──────────────────────────
            PdfPTable tbl = new PdfPTable(new float[]{2, 5});
            tbl.setWidthPercentage(100);

            // 職務内容
            StringBuilder jobSb = new StringBuilder();
            if (fd.path("jobKaji").asBoolean())  jobSb.append("✓家事サービス　");
            if (fd.path("jobKaigo").asBoolean()) jobSb.append("✓介護サービス　");
            if (fd.path("jobOther").asBoolean()) {
                jobSb.append("✓その他");
                String ot = fd.path("jobOtherText").asText("");
                if (!ot.isBlank()) jobSb.append("：").append(ot);
            }
            addPdfRow(tbl, "職務内容", jobSb.toString(), boldFont, normalFont);

            // 就業場所
            String postal  = fd.path("workPostal").asText("");
            String place   = fd.path("workPlace").asText("");
            String station = fd.path("workStation").asText("");
            String line2   = fd.path("workLine").asText("");
            String access  = fd.path("workAccess").asText("");
            String smokingV = fd.path("smoking").asText("");
            addPdfRow(tbl, "就業場所",
                "〒" + postal + "\u3000" + place + "\n"
                + "最寄り駅\u3000" + station + "\u3000" + line2 + "線\u3000徒歩・バス " + access + "\n"
                + "受動喫煙防止措置状況\u3000" + smokingV,
                boldFont, normalFont);

            // 雇用期間
            String empType = fd.path("empPeriod").asText("無期");
            String empPeriod = "";
            if ("有期".equals(empType))
                empPeriod = fd.path("empFrom").asText("") + " 〜 " + fd.path("empTo").asText("");
            addPdfRow(tbl, "雇用期間",
                empType + (empPeriod.isBlank() ? "" : "　" + empPeriod), boldFont, normalFont);

            // 試用期間
            String trial = fd.path("trialPeriod").asText("無");
            String trialStr = trial;
            if ("有".equals(trial)) {
                String tf = fd.path("trialFrom").asText(""), tt = fd.path("trialTo").asText("");
                trialStr += "（詳細は備考欄）";
                if (!tf.isBlank() || !tt.isBlank()) trialStr += "　" + tf + " 〜 " + tt;
            }
            addPdfRow(tbl, "試用期間", trialStr, boldFont, normalFont);

            // 勤務日
            addPdfRow(tbl, "勤務日",
                fd.path("workStyle").asText("通勤") + "　" + fd.path("dow").asText(""),
                boldFont, normalFont);

            // 勤務時間
            String sh = fd.path("wSH").asText(""), sm = fd.path("wSM").asText("");
            String eh = fd.path("wEH").asText(""), em = fd.path("wEM").asText("");
            String hoursStr = (!sh.isBlank() && !sm.isBlank()) ? sh + ":" + sm + " 〜 " + eh + ":" + em : "";
            String actual = fd.path("actualHours").asText("");
            addPdfRow(tbl, "勤務時間",
                hoursStr + (actual.isBlank() ? "" : "　〔実働　" + actual + "〕"),
                boldFont, normalFont);

            addPdfRow(tbl, "時間外労働", fd.path("overtime").asText("無"), boldFont, normalFont);
            addPdfRow(tbl, "休憩時間",   fd.path("breakTime").asText(""),  boldFont, normalFont);

            // 休日
            StringBuilder holSb = new StringBuilder();
            if (fd.path("holWeekly").asBoolean())   holSb.append("毎週　");
            if (fd.path("holBiWeekly").asBoolean())  holSb.append("隔週　");
            String holDow = fd.path("holDow").asText("");
            if (!holDow.isBlank()) holSb.append(holDow).append("曜日　");
            if (fd.path("holHoliday").asBoolean())   holSb.append("祝日　");
            if (fd.path("holOther").asBoolean())     holSb.append("その他");
            addPdfRow(tbl, "休　日", holSb.toString().trim(), boldFont, normalFont);

            // 賃金形態・基本給（時給・日給をそれぞれ独立した行として出力／旧データ互換）
            String wageTypeRaw = fd.path("wageType").asText("時給");
            List<String> wageTypeParts = new ArrayList<>();
            for (String p : wageTypeRaw.split("・")) {
                if (p.equals("時給") || p.equals("日給")) wageTypeParts.add(p);
            }
            if (wageTypeParts.isEmpty()) wageTypeParts.add("時給");

            boolean hasNewWageFields = fd.has("hourlyLine1") || fd.has("dailyLine1")
                    || fd.has("monthlyLine1") || fd.has("baseWageLine1");
            String baseWageHourly = "";
            String baseWageDaily  = "";
            if (hasNewWageFields) {
                // 現行〜一世代前のデータ：時給・日給それぞれ専用の入力欄を持つ
                StringBuilder h = new StringBuilder();
                String h1 = fd.path("hourlyLine1").asText("");
                String h2 = fd.path("hourlyLine2").asText("");
                if (!h1.isBlank()) h.append(h1);
                if (!h2.isBlank()) { if (h.length() > 0) h.append("\n"); h.append(h2); }
                if (wageTypeParts.contains("時給")) baseWageHourly = h.toString();

                if (wageTypeParts.contains("日給")) baseWageDaily = fd.path("dailyLine1").asText("");
            } else if (fd.has("wageLine1")) {
                // 旧データ（賃金形態が3行自由入力だった時期。型までは判別できないため時給扱い）
                baseWageHourly = fd.path("wageType").asText("");
            } else {
                // 最古データ（基本給が単一の数値だった時期のデータ）
                String oldBaseWage = fd.path("baseWage").asText("");
                try { if (!oldBaseWage.isBlank()) oldBaseWage = String.format("%,d", Long.parseLong(oldBaseWage)); }
                catch (NumberFormatException ignored) {}
                if (!oldBaseWage.isBlank()) {
                    if (wageTypeParts.contains("日給")) baseWageDaily = oldBaseWage + "　円";
                    else baseWageHourly = oldBaseWage + "　円";
                }
            }
            if (wageTypeParts.contains("時給")) {
                addPdfRow(tbl, "賃金形態（時給）", baseWageHourly, boldFont, normalFont);
            }
            if (wageTypeParts.contains("日給")) {
                addPdfRow(tbl, "賃金形態（日給）", baseWageDaily, boldFont, normalFont);
            }

            String owage = fd.path("overtimeWage").asText("");
            try { if (!owage.isBlank()) owage = String.format("%,d", Long.parseLong(owage)); }
            catch (NumberFormatException ignored) {}
            addPdfRow(tbl, "諸手当", "時間外手当　" + owage + "　円", boldFont, normalFont);

            // 交通費
            StringBuilder trSb = new StringBuilder();
            if (fd.path("transNone").asBoolean()) trSb.append("無　");
            if (fd.path("transReal").asBoolean()) trSb.append("往復の実費　");
            if (fd.path("transYes").asBoolean()) {
                trSb.append("有");
                String ta = fd.path("transportAmt").asText("");
                if (!ta.isBlank()) trSb.append("　").append(ta).append("円");
            }
            addPdfRow(tbl, "交通費", trSb.toString().trim(), boldFont, normalFont);
            addPdfRow(tbl, "昇給", fd.path("raise").asText("無"), boldFont, normalFont);

            // 賃金支払方法
            StringBuilder paySb = new StringBuilder();
            if (fd.path("payDaily").asBoolean())   paySb.append("毎日　");
            if (fd.path("payWeekly").asBoolean())
                paySb.append("毎週").append(fd.path("payWeeklyDay").asText("")).append("曜日　");
            if (fd.path("payMonthly").asBoolean())
                paySb.append("毎月").append(fd.path("payMonthlyDay").asText("")).append("日　");
            String pm = fd.path("payMethod").asText("");
            if (!pm.isBlank()) paySb.append("方法：").append(pm);
            addPdfRow(tbl, "賃金支払方法", paySb.toString().trim(), boldFont, normalFont);

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
            String insStr = insSb.toString().trim() + "\n（紹介手数料15%　＋消費税）";
            addPdfRow(tbl, "社会・労働保険\n加入状況", insStr, boldFont, normalFont);

            addPdfRow(tbl, "備考", fd.path("remarks").asText(""), boldFont, normalFont);
            doc.add(tbl);

            // ── 注意文 ────────────────────────────────────
            Paragraph note = new Paragraph(
                "1. 手数料は別表のとおりです。2. 採否については至急に電話または書面によりご連絡をお願いいたします。",
                smallFont);
            note.setSpacingBefore(4); doc.add(note);

            // ── フッター ──────────────────────────────────
            PdfPTable footer = new PdfPTable(1);
            footer.setWidthPercentage(100); footer.setSpacingBefore(6);
            String[] footerLines = {
                "所在地　〒107-0052 東京都港区赤坂6-10-45-203",
                "紹介所　有限会社　ワークオフィス谷",
                "代表者　代表取締役　谷 二三代",
                "連絡先　TEL 03-5544-8315　FAX 03-5544-8316"
            };
            for (String fl : footerLines) {
                Font ff = fl.contains("有限会社") ? boldFont : smallFont;
                PdfPCell fc = new PdfPCell(new Phrase(fl, ff));
                fc.setBorder(Rectangle.NO_BORDER);
                fc.setHorizontalAlignment(Element.ALIGN_RIGHT); fc.setPadding(2);
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

    // ─── PDF行追加ヘルパー ────────────────────────────
    private void addPdfRow(PdfPTable tbl, String key, String val,
                            Font boldFont, Font normalFont) {
        PdfPCell k = new PdfPCell(new Phrase(key, boldFont));
        k.setBackgroundColor(new BaseColor(240, 240, 240));
        k.setBorder(Rectangle.BOX); k.setPadding(3);
        k.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tbl.addCell(k);
        PdfPCell v = new PdfPCell(new Phrase(val != null ? val : "", normalFont));
        v.setBorder(Rectangle.BOX); v.setPadding(3);
        tbl.addCell(v);
    }
}

