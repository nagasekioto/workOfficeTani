package jp.co.housekeeping.person_management.controller;

import java.io.IOException;
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
