package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.CustomerRequest;
import jp.co.housekeeping.person_management.model.Introduction;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.CustomerRequestRepository;
import jp.co.housekeeping.person_management.repository.IntroductionRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    // 求人者No(no)の採番を排他制御するためのロック
    private static final Object CUSTOMER_NO_LOCK = new Object();

    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerRequestRepository customerRequestRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private IntroductionRepository introductionRepository;

    private boolean checkAuth(HttpSession session) {
        return session.getAttribute("authenticated") != null;
    }

    // 紹介履歴行を「紹介状一覧（1-6-2）」のデータから構築する
    private List<KaijinRow> buildRowsFromIntroductions(Long customerId) {
        List<KaijinRow> rows = new ArrayList<>();
        if (customerId == null) return rows;

        for (Introduction intro : introductionRepository.findAll()) {
            if (!customerId.equals(intro.getCustomerId())) continue;
            KaijinRow row = new KaijinRow();
            row.introId = intro.getId();
            row.introDate = intro.getIntroDate();
            row.introductionDate = intro.getIntroDate() != null ? intro.getIntroDate().toString() : "";
            row.personId = intro.getPersonId();
            row.customerId = intro.getCustomerId();
            row.formData = intro.getFormData() != null ? intro.getFormData() : "";
            if (intro.getEmpStatus() != null && !intro.getEmpStatus().isBlank()) {
                row.empStatus = intro.getEmpStatus();
            }
            row.hireResult = nvl(intro.getHireResult());
            row.remarks = nvl(intro.getLedgerRemarks());
            if (intro.getPersonId() != null) {
                personRepository.findById(intro.getPersonId())
                    .ifPresent(p -> row.personName = p.getLastNameKanji() + " " + p.getFirstNameKanji());
            }
            rows.add(row);
        }

        rows.sort((a, b) -> {
            if (a.introDate == null && b.introDate == null) return 0;
            if (a.introDate == null) return 1;
            if (b.introDate == null) return -1;
            return a.introDate.compareTo(b.introDate);
        });
        return rows;
    }

    // ─── 1-2 求人者新規登録フォーム ───────────────────
    @GetMapping("/register")
    public String registerForm(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        List<Customer> active = new ArrayList<>();
        for (Customer c : customerRepository.findAll()) {
            if (c.getRetiredAt() == null) active.add(c);
        }
        model.addAttribute("customers", active);
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customer", new Customer());
        model.addAttribute("editMode", false);
        return "customer-register";
    }

    // ─── 求人者新規登録処理 ────────────────────────────
    @PostMapping("/register")
    public String register(@ModelAttribute Customer customer, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        if (customer.getRegisteredDate() == null) customer.setRegisteredDate(LocalDate.now());
        if (customer.getNo() == null) {
            synchronized (CUSTOMER_NO_LOCK) {
                int maxNo = 0;
                for (Customer c : customerRepository.findAll()) {
                    if (c.getNo() != null && c.getNo() > maxNo) maxNo = c.getNo();
                }
                customer.setNo(maxNo + 1);
                customerRepository.save(customer);
            }
        } else {
            customerRepository.save(customer);
        }
        return "redirect:/customer/register";
    }

    // ─── 求人者編集フォーム ────────────────────────────
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        Customer c = customerRepository.findById(id).orElse(null);
        if (c == null) return "redirect:/customer/list";
        List<Customer> active = new ArrayList<>();
        for (Customer cu : customerRepository.findAll()) {
            if (cu.getRetiredAt() == null) active.add(cu);
        }
        model.addAttribute("customers", active);
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customer", c);
        model.addAttribute("editMode", true);
        return "customer-register";
    }

    // ─── 求人者更新処理 ────────────────────────────────
    @PostMapping("/update")
    public String update(@ModelAttribute Customer customer, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        customerRepository.findById(customer.getId()).ifPresent(existing -> {
            if (customer.getLastNameKana() == null || customer.getLastNameKana().isBlank())
                customer.setLastNameKana(existing.getLastNameKana());
            if (customer.getFirstNameKana() == null || customer.getFirstNameKana().isBlank())
                customer.setFirstNameKana(existing.getFirstNameKana());
            if (customer.getLastNameKanji() == null || customer.getLastNameKanji().isBlank())
                customer.setLastNameKanji(existing.getLastNameKanji());
            if (customer.getFirstNameKanji() == null || customer.getFirstNameKanji().isBlank())
                customer.setFirstNameKanji(existing.getFirstNameKanji());
            if (customer.getNo() == null)
                customer.setNo(existing.getNo());
            if (customer.getRegisteredDate() == null)
                customer.setRegisteredDate(existing.getRegisteredDate());
            if (customer.getHomePhone() == null)
                customer.setHomePhone(existing.getHomePhone());
            if (customer.getMobilePhone() == null)
                customer.setMobilePhone(existing.getMobilePhone());
            if (customer.getFaxPhone() == null)
                customer.setFaxPhone(existing.getFaxPhone());
            if (customer.getPostalCode() == null)
                customer.setPostalCode(existing.getPostalCode());
            if (customer.getAddress1() == null)
                customer.setAddress1(existing.getAddress1());
            if (customer.getAddress2() == null)
                customer.setAddress2(existing.getAddress2());
            if (customer.getAddress3() == null)
                customer.setAddress3(existing.getAddress3());
            if (customer.getNearestStation() == null)
                customer.setNearestStation(existing.getNearestStation());
            if (customer.getNearestLine() == null)
                customer.setNearestLine(existing.getNearestLine());
            if (customer.getNotes() == null)
                customer.setNotes(existing.getNotes());
            if (customer.getAccessTime() == null)
                customer.setAccessTime(existing.getAccessTime());
            if (customer.getRetiredAt() == null)
                customer.setRetiredAt(existing.getRetiredAt());
        });
        customerRepository.save(customer);
        return "redirect:/customer/list";
    }

    // ─── 求人者を取引終了扱いにする（1-2-3の「削除」ボタン→「退職」ボタン）───
    @PostMapping("/retire/{id}")
    public String retire(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        Customer c = customerRepository.findById(id).orElse(null);
        if (c != null) {
            c.setRetiredAt(LocalDate.now());
            customerRepository.save(c);
        }
        return "redirect:/customer/list";
    }

    // ─── 1-2-4 元求人先 ────────────────────────────────
    @GetMapping("/retired-list")
    public String retiredList(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        List<Customer> retired = new ArrayList<>();
        for (Customer c : customerRepository.findAll()) {
            if (c.getRetiredAt() != null) retired.add(c);
        }
        retired.sort((a, b) -> b.getRetiredAt().compareTo(a.getRetiredAt()));
        model.addAttribute("customers", retired);
        return "customer-retired-list";
    }

    // ─── 取引終了を取り消して取引中に戻す ────────────────────
    @PostMapping("/retired-list/reinstate/{id}")
    public String reinstate(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        Customer c = customerRepository.findById(id).orElse(null);
        if (c != null) {
            c.setRetiredAt(null);
            customerRepository.save(c);
        }
        return "redirect:/customer/retired-list";
    }

    // ─── 求人者削除（元求人先からの完全削除。元に戻せません）─────
    @PostMapping("/retired-list/delete/{id}")
    public String deleteRetired(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        customerRepository.deleteById(id);
        return "redirect:/customer/retired-list";
    }

    // ─── 1-2-3 求人者一覧 ──────────────────────────────
    @GetMapping("/list")
    public String list(@RequestParam(required = false) String sort,
                       HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        List<Customer> active = new ArrayList<>();
        for (Customer c : customerRepository.findAll()) {
            if (c.getRetiredAt() == null) active.add(c);
        }
        model.addAttribute("customers", active);
        model.addAttribute("sort", sort);
        return "customer-list";
    }

    // ─── 1-2-1 求人受付表（新規）──────────────────────
    @GetMapping("/request/new")
    public String requestForm(@RequestParam(required = false) Long customerId,
                              HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("customers", customerRepository.findAll());
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("selectedCustomerId", customerId);
        model.addAttribute("request", new CustomerRequest());
        if (customerId != null) {
            customerRepository.findById(customerId).ifPresent(c ->
                model.addAttribute("selectedCustomer", c));
        }
        return "customer-request-form";
    }

    // ─── 1-2-1 求人受付表保存 ─────────────────────────
    @PostMapping("/request/save")
    public String requestSave(
            @RequestParam Long customerId,
            @RequestParam(required = false) String postalCode,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String workAddress,
            @RequestParam(required = false) Boolean jobCooking,
            @RequestParam(required = false) Boolean jobLaundry,
            @RequestParam(required = false) Boolean jobCleaning,
            @RequestParam(required = false) Boolean jobIroning,
            @RequestParam(required = false) Boolean jobBabysitting,
            @RequestParam(required = false) Boolean jobNursing,
            @RequestParam(required = false) Boolean jobOther,
            @RequestParam(required = false) String jobOtherText,
            @RequestParam(required = false) String freqType,
            @RequestParam(required = false) String freqTempDate,
            @RequestParam(required = false) String freqWeeklyDays,
            @RequestParam(required = false) String freqWeeklyStart,
            @RequestParam(required = false) String freqWeeklyEnd,
            @RequestParam(required = false) Integer familyAdults,
            @RequestParam(required = false) Integer familyChildren,
            @RequestParam(required = false) String introducerName,
            @RequestParam(required = false) Boolean introInternet,
            @RequestParam(required = false) Boolean introTownpage,
            @RequestParam(required = false) Boolean introOther,
            @RequestParam(required = false) String introOtherText,
            @RequestParam(required = false) Boolean petNone,
            @RequestParam(required = false) Boolean petDog,
            @RequestParam(required = false) Boolean petCat,
            @RequestParam(required = false) Boolean petOther,
            @RequestParam(required = false) String petOtherText,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) Boolean interviewNone,
            @RequestParam(required = false) String interviewDate1,
            @RequestParam(required = false) String interviewDate2,
            @RequestParam(required = false) Long candidatePersonId,
            HttpSession session) {

        if (!checkAuth(session)) return "redirect:/login";

        CustomerRequest req = new CustomerRequest();
        req.setCustomerId(customerId);
        req.setPostalCode(postalCode);
        req.setAddress(address);
        req.setWorkAddress(workAddress);
        req.setJobCooking(Boolean.TRUE.equals(jobCooking));
        req.setJobLaundry(Boolean.TRUE.equals(jobLaundry));
        req.setJobCleaning(Boolean.TRUE.equals(jobCleaning));
        req.setJobIroning(Boolean.TRUE.equals(jobIroning));
        req.setJobBabysitting(Boolean.TRUE.equals(jobBabysitting));
        req.setJobNursing(Boolean.TRUE.equals(jobNursing));
        req.setJobOther(Boolean.TRUE.equals(jobOther));
        req.setJobOtherText(jobOtherText);
        req.setFreqType(freqType);
        if (freqTempDate != null && !freqTempDate.isBlank())
            req.setFreqTempDate(LocalDate.parse(freqTempDate));
        req.setFreqWeeklyDays(freqWeeklyDays);
        if (freqWeeklyStart != null && !freqWeeklyStart.isBlank())
            req.setFreqWeeklyStart(LocalTime.parse(freqWeeklyStart));
        if (freqWeeklyEnd != null && !freqWeeklyEnd.isBlank())
            req.setFreqWeeklyEnd(LocalTime.parse(freqWeeklyEnd));
        req.setFamilyAdults(familyAdults != null ? familyAdults : 0);
        req.setFamilyChildren(familyChildren != null ? familyChildren : 0);
        req.setIntroducerName(introducerName);
        req.setIntroInternet(Boolean.TRUE.equals(introInternet));
        req.setIntroTownpage(Boolean.TRUE.equals(introTownpage));
        req.setIntroOther(Boolean.TRUE.equals(introOther));
        req.setIntroOtherText(introOtherText);
        req.setPetNone(Boolean.TRUE.equals(petNone));
        req.setPetDog(Boolean.TRUE.equals(petDog));
        req.setPetCat(Boolean.TRUE.equals(petCat));
        req.setPetOther(Boolean.TRUE.equals(petOther));
        req.setPetOtherText(petOtherText);
        req.setRemarks(remarks);
        req.setInterviewNone(Boolean.TRUE.equals(interviewNone));
        if (interviewDate1 != null && !interviewDate1.isBlank())
            req.setInterviewDate1(LocalDateTime.parse(interviewDate1));
        if (interviewDate2 != null && !interviewDate2.isBlank())
            req.setInterviewDate2(LocalDateTime.parse(interviewDate2));
        req.setCandidatePersonId(candidatePersonId);
        customerRequestRepository.save(req);
        return "redirect:/customer/request/list?saved=true";
    }

    // ─── 求人受付表一覧 ────────────────────────────────
    @GetMapping("/request/list")
    public String requestList(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("requests", customerRequestRepository.findAllOrdered());
        model.addAttribute("customers", customerRepository.findAll());
        model.addAttribute("persons", personRepository.findAll());
        return "customer-request-list";
    }

    // ─── 1-2-2 管理簿入力 ─────────────────────────────
    @GetMapping("/report")
    public String report(@RequestParam(required = false) Long customerId,
                         HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("customers", customerRepository.findAll());
        model.addAttribute("requests", customerRequestRepository.findAllOrdered());
        model.addAttribute("persons", personRepository.findAll());

        if (customerId != null) {
            customerRepository.findById(customerId).ifPresent(c ->
                model.addAttribute("selectedCustomer", c));

            List<CustomerRequest> reqs = customerRequestRepository.findByCustomerId(customerId);
            if (!reqs.isEmpty()) model.addAttribute("request", reqs.get(0));

            List<KaijinRow> rows = buildRowsFromIntroductions(customerId);
            model.addAttribute("rows", rows);
            model.addAttribute("emptyRows", Math.max(5, 10 - rows.size()));
        }
        return "customer-report";
    }

    // ─── 1-2-2 紹介履歴の採否・雇用期間（状況）・備考を保存 ──
    @PostMapping("/report/save-row-status")
    @ResponseBody
    public String saveRowStatus(@RequestParam Long customerId,
                                @RequestParam(required = false) Long[] introIds,
                                @RequestParam(required = false) String[] empStatusList,
                                @RequestParam(required = false) String[] hireResultList,
                                @RequestParam(required = false) String[] remarksList,
                                HttpSession session) {
        if (!checkAuth(session)) return "UNAUTHORIZED";
        if (introIds == null) return "OK";

        for (int i = 0; i < introIds.length; i++) {
            Long introId = introIds[i];
            if (introId == null) continue;
            final int idx = i;
            introductionRepository.findById(introId).ifPresent(intro -> {
                if (empStatusList != null && idx < empStatusList.length) {
                    intro.setEmpStatus(empStatusList[idx]);
                }
                if (hireResultList != null && idx < hireResultList.length) {
                    intro.setHireResult(hireResultList[idx]);
                }
                if (remarksList != null && idx < remarksList.length) {
                    intro.setLedgerRemarks(remarksList[idx]);
                }
                introductionRepository.save(intro);
            });
        }
        return "OK";
    }

    // ─── 1-2-2 求人管理簿 PDF出力 ──────────────────────────
    @GetMapping("/report/pdf")
    public void reportPdf(@RequestParam(required = false) Long customerId,
                          @RequestParam(required = false, defaultValue = "inline") String mode,
                          @RequestParam(required = false) String[] empStatusList,
                          @RequestParam(required = false) String[] hireResultList,
                          @RequestParam(required = false) String[] remarksList,
                          HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (!checkAuth(session)) { response.sendError(401); return; }

        Customer customer = customerId != null
                ? customerRepository.findById(customerId).orElse(null) : null;

        // 紹介履歴行を収集（紹介状一覧[1-6-2]のデータから構築）
        List<KaijinRow> rows = buildRowsFromIntroductions(customerId);
        // 画面側で入力された雇用期間ステータス・採否・備考を行に反映
        for (int i = 0; i < rows.size(); i++) {
            KaijinRow row = rows.get(i);
            if (empStatusList != null && i < empStatusList.length
                    && empStatusList[i] != null && !empStatusList[i].isBlank()) {
                row.empStatus = empStatusList[i];
            }
            if (hireResultList != null && i < hireResultList.length && hireResultList[i] != null) {
                row.hireResult = hireResultList[i];
            }
            if (remarksList != null && i < remarksList.length && remarksList[i] != null) {
                row.remarks = remarksList[i];
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildCustomerLedgerPdf(customer, rows, baos);

        response.setContentType("application/pdf");
        boolean download = "download".equals(mode);
        response.setHeader("Content-Disposition",
                (download ? "attachment" : "inline") + "; filename=customer-ledger.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    private void buildCustomerLedgerPdf(Customer c, List<KaijinRow> rows, ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        // A4縦 余白小さめ
        Document doc = new Document(PageSize.A4, 14, 14, 14, 14);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf  = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font title   = new Font(bf, 14, Font.BOLD);
        Font bold9   = new Font(bf, 9,  Font.BOLD);
        Font bold7   = new Font(bf, 7,  Font.BOLD);
        Font norm7   = new Font(bf, 7);
        Font norm6   = new Font(bf, 6);
        Font bold6   = new Font(bf, 6,  Font.BOLD);
        Font bold5   = new Font(bf, 5,  Font.BOLD);
        Font infoVal = new Font(bf, 11, Font.BOLD);
        Font infoLabel = new Font(bf, 10, Font.BOLD);
        Font groupFont = new Font(bf, 12, Font.BOLD);

        // ── タイトル ──
        PdfPTable titleTbl = new PdfPTable(1);
        titleTbl.setWidthPercentage(100);
        titleTbl.setSpacingAfter(4);
        PdfPCell titleCell = new PdfPCell(new Phrase("求　人　管　理　簿", title));
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setPaddingBottom(3);
        titleTbl.addCell(titleCell);
        doc.add(titleTbl);

        // ── ヘッダー部（求人者情報） ──
        // [求人者氏名・住所エリア] | [連絡担当者エリア]
        PdfPTable hdr = new PdfPTable(new float[]{3.5f, 2.5f});
        hdr.setWidthPercentage(100);
        hdr.setSpacingAfter(2);

        // 電話番号：自宅電話番号を優先、無ければ携帯電話番号を使用
        String customerPhone = "";
        if (c != null) {
            String home = c.getHomePhone();
            if (home != null && !home.isBlank() && !home.equals("-")) {
                customerPhone = home;
            } else {
                customerPhone = nvl(c.getMobilePhone());
            }
        }

        // 左列: 求人者情報（3列構成：グループ｜ラベル｜値）。各行18fで4行＝合計72f。
        PdfPTable leftInfo = new PdfPTable(new float[]{1.0f, 1.2f, 4f});
        leftInfo.setWidthPercentage(100);
        addHdrRow(leftInfo, "求人者", "氏名",
                c != null ? (c.getLastNameKanji() + "　" + c.getFirstNameKanji()) : "", groupFont, infoLabel, infoVal, 4, 18f);
        addHdrRow2(leftInfo, "〒",
                c != null && c.getPostalCode() != null ? c.getPostalCode() : "", infoLabel, infoVal, 18f);
        addHdrRow2(leftInfo, "住所",
                c != null ? nvl(c.getAddress1()) + nvl(c.getAddress2()) : "", infoLabel, infoVal, 18f);
        addHdrRow2(leftInfo, "電話番号", customerPhone, infoLabel, infoVal, 18f);
        PdfPCell leftCell = new PdfPCell(leftInfo);
        leftCell.setBorder(Rectangle.BOX); leftCell.setPadding(0);
        hdr.addCell(leftCell);

        // 中列: 連絡担当者（氏名・電話番号を同じ高さ36fにし、合計72fで求人者欄と揃える）。
        // 氏名＝担当者名、電話番号＝担当者電話番号を表示。
        String staffName  = c != null ? nvl(c.getStaffName())  : "";
        String staffPhone = c != null ? nvl(c.getStaffPhone()) : "";
        PdfPTable midInfo = new PdfPTable(new float[]{1.0f, 1.2f, 3f});
        midInfo.setWidthPercentage(100);
        addHdrRow(midInfo, "連絡\n担当者", "氏名", staffName, groupFont, infoLabel, infoVal, 2, 36f);
        addHdrRow2(midInfo, "電話番号", staffPhone, infoLabel, infoVal, 36f);
        PdfPCell midCell = new PdfPCell(midInfo);
        midCell.setBorder(Rectangle.BOX); midCell.setPadding(0);
        hdr.addCell(midCell);

        doc.add(hdr);

        // ── 求人希望職種行 ──（求人者新規登録の仕事内容（複数選択可）を反映）
        String jobContentsStr = c != null && c.getJobContents() != null
                ? c.getJobContents().replace(",", "　") : "";
        PdfPTable jobRow = new PdfPTable(new float[]{1.2f, 9f});
        jobRow.setWidthPercentage(100);
        jobRow.setSpacingAfter(2);
        addLblVal(jobRow, "求人希望職種", jobContentsStr, bold6, norm7);
        doc.add(jobRow);

        // ── メイン表 ──
        // 列: 受付年月日|有効期間|求人数|就業場所|雇用期間|賃金(給・円)|紹介年月日|求職者氏名|採否|採用年月日|備考|
        //     取扱情報(労働契約｜無期雇用就職者(転職勧奨禁止期間/6カ月以内または不明/返戻金))
        float[] colW = {1.5f, 1.7f, 0.5f, 1.1f, 2.3f, 0.6f, 1.5f, 1.5f, 1.7f, 0.6f, 1.5f, 1.3f, 0.8f, 1.3f, 1.3f, 0.6f};
        PdfPTable tbl = new PdfPTable(colW);
        tbl.setWidthPercentage(100);
        tbl.setSpacingBefore(2);

        // ヘッダー行1（16列ぶん。賃金と取扱情報以外はrowspan3で3段分を貫通）
        addTh(tbl, "受付年月日",        bold6, 1, 3, 20f);
        addTh(tbl, "有効期間",          bold6, 1, 3, 20f);
        addTh(tbl, "求人数\n人",       bold6, 1, 3, 20f);
        addTh(tbl, "就業場所",          bold6, 1, 3, 20f);
        addTh(tbl, "雇用期間",          bold6, 1, 3, 20f);
        addTh(tbl, "賃金",              bold6, 2, 1, 15f);
        addTh(tbl, "紹介年月日",        bold6, 1, 3, 20f);
        addTh(tbl, "求職者氏名",        bold6, 1, 3, 20f);
        addTh(tbl, "採否",              bold6, 1, 3, 20f);
        addTh(tbl, "採用年月日",        bold6, 1, 3, 20f);
        addTh(tbl, "備考",              bold6, 1, 3, 20f);
        addTh(tbl, "取扱情報",          bold6, 4, 1, 15f);

        // ヘッダー行2（賃金サブ：給・円／取扱情報サブ：労働契約・無期雇用就職者）
        addTh(tbl, "給",                bold6, 1, 2, 15f);
        addTh(tbl, "円",                bold6, 1, 2, 15f);
        addTh(tbl, "労働\n契約",       bold6, 1, 2, 15f);
        addTh(tbl, "無期雇用就職者",    bold6, 3, 1, 15f);

        // ヘッダー行3（無期雇用就職者サブ：転職勧奨禁止期間・6カ月以内または不明・返戻金）
        addThSub(tbl, "転職勧奨禁止期間", bold5);
        addThSub(tbl, "6カ月以内\nまたは不明", bold5);
        addThSub(tbl, "返戻\n金", bold6);

        // データ行（実データ + 空行で計38行）
        int DATA_ROWS = 38;
        int filled = 0;
        ObjectMapper mapper = new ObjectMapper();
        for (KaijinRow row : rows) {
            if (filled >= DATA_ROWS) break;

            LocalDate introDate = row.introDate;
            String introDateStr = introDate != null ? formatDot(introDate) : "";
            String validPeriod  = formatValidPeriod(introDate);
            String workPeriod   = (introDate != null ? formatDot(introDate) : "")
                    + "〜" + nvl(row.empStatus);

            // 紹介状一覧（1-6-2）の賃金形態・基本給を参照（その行自身の紹介状データから直接取得）
            List<String[]> wageRows = extractWageRows(mapper, row.formData);
            if (wageRows.isEmpty()) wageRows.add(new String[]{"", ""});

            for (String[] wage : wageRows) {
                if (filled >= DATA_ROWS) break;
                addTdC(tbl, introDateStr,    norm6);  // 受付年月日（紹介年月日と同じ）
                addTdC(tbl, validPeriod,     norm6);  // 有効期間
                addTdC(tbl, "1",             norm6);  // 求人数
                addTdC(tbl, "同上",          norm6);  // 就業場所
                addTdC(tbl, workPeriod,      norm6);  // 雇用期間
                addTdC(tbl, wage[0],         norm6);  // 給（時給／日給）
                addTdC(tbl, wage[1],         norm6);  // 円（基本給）
                addTdC(tbl, introDateStr,    norm6);  // 紹介年月日
                addTdC(tbl, row.personName,  norm6);  // 求職者氏名
                addTdC(tbl, nvl(row.hireResult), norm6);  // 採否
                addTdC(tbl, introDateStr,    norm6);  // 採用年月日（紹介年月日と同じ）
                addTdC(tbl, nvl(row.remarks),norm6);  // 備考
                addTdC(tbl, extractLaborContract(mapper, row.formData), norm6);  // 労働契約（紹介状作成[1-6-1]③雇用条件の雇用期間を参照）
                addTdC(tbl, "",              norm6);  // 転職勧奨禁止期間
                addTdC(tbl, "",              norm6);  // 6カ月以内または不明
                addTdC(tbl, "",              norm6);  // 返戻金
                filled++;
            }
        }
        for (int i = filled; i < DATA_ROWS; i++) {
            for (int col = 0; col < 16; col++) {
                addTdC(tbl, (col == 1 || col == 4) ? "〜" : "", norm6);
            }
        }
        doc.add(tbl);
        doc.close();
    }

    /** 紹介状（formData）の③雇用条件・雇用期間（無期／有期）を労働契約として抽出する */
    private String extractLaborContract(ObjectMapper mapper, String formData) {
        if (formData == null || formData.isBlank()) return "有期";
        try {
            JsonNode node = mapper.readTree(formData);
            String empPeriod = node.path("empPeriod").asText("");
            if ("無期".equals(empPeriod) || "有期".equals(empPeriod)) return empPeriod;
        } catch (Exception ignored) {}
        return "有期";
    }

    /** 紹介状（formData）から[給,円]のペア一覧を抽出する（時給・日給両方あれば2行）*/
    private List<String[]> extractWageRows(ObjectMapper mapper, String formData) {
        List<String[]> result = new ArrayList<>();
        if (formData == null || formData.isBlank()) return result;

        try {
            JsonNode node = mapper.readTree(formData);
            String wageTypeRaw = node.path("wageType").asText("");
            List<String> types = new ArrayList<>();
            for (String p : wageTypeRaw.split("・")) {
                if (p.equals("時給") || p.equals("日給")) types.add(p);
            }
            if (types.isEmpty()) types.add("時給");

            if (types.contains("時給")) {
                StringBuilder h = new StringBuilder();
                String h1 = node.path("hourlyLine1").asText("");
                String h2 = node.path("hourlyLine2").asText("");
                if (!h1.isBlank()) h.append(h1);
                if (!h2.isBlank()) { if (h.length() > 0) h.append("\n"); h.append(h2); }
                if (h.length() == 0) {
                    // 旧データ（基本給が単一の数値だった時期）との後方互換
                    String oldBaseWage = node.path("baseWage").asText("");
                    if (!oldBaseWage.isBlank()) h.append(oldBaseWage).append("　円");
                }
                result.add(new String[]{"時給", h.toString()});
            }
            if (types.contains("日給")) {
                String d1 = node.path("dailyLine1").asText("");
                result.add(new String[]{"日給", d1});
            }
        } catch (Exception ignored) {}

        return result;
    }

    /** 紹介年月日が属する月の月初〜月末を「yyyy.M.d〜yyyy.M.d」形式で返す */
    private String formatValidPeriod(LocalDate introDate) {
        if (introDate == null) return "";
        java.time.YearMonth ym = java.time.YearMonth.from(introDate);
        return formatDot(ym.atDay(1)) + "〜" + formatDot(ym.atEndOfMonth());
    }

    private String formatDot(LocalDate d) {
        if (d == null) return "";
        return d.getYear() + "." + d.getMonthValue() + "." + d.getDayOfMonth();
    }

    // ─── PDF ヘルパー ────────────────────────────────────────────

    private void addHdrRow(PdfPTable t, String group, String label, String val, Font gf, Font lf, Font vf, int rowspan, float rowHeight) {
        PdfPCell g = new PdfPCell(new Phrase(group, gf));
        g.setBorder(Rectangle.BOX); g.setPadding(2); g.setRowspan(rowspan);
        g.setHorizontalAlignment(Element.ALIGN_CENTER); g.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(g);
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.BOX); lc.setPadding(2); lc.setMinimumHeight(rowHeight); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(val, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(2); vc.setMinimumHeight(rowHeight); t.addCell(vc);
    }

    private void addHdrRow2(PdfPTable t, String label, String val, Font lf, Font vf, float rowHeight) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.BOX); lc.setPadding(2); lc.setMinimumHeight(rowHeight); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(val, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(2); vc.setMinimumHeight(rowHeight); t.addCell(vc);
    }

    private void addLblVal(PdfPTable t, String label, String val, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.TOP | Rectangle.LEFT | Rectangle.BOTTOM); lc.setPadding(2);
        lc.setHorizontalAlignment(Element.ALIGN_CENTER); lc.setMinimumHeight(16f);
        t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(val, vf));
        vc.setBorder(Rectangle.BOX); vc.setPadding(2); vc.setMinimumHeight(16f);
        t.addCell(vc);
    }

    private void addTh(PdfPTable t, String text, Font f, int colspan, int rowspan, float h) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setColspan(colspan); c.setRowspan(rowspan);
        c.setBorder(Rectangle.BOX); c.setPadding(2);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setMinimumHeight(h); c.setBackgroundColor(new BaseColor(220, 220, 220));
        t.addCell(c);
    }

    private void addThSub(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.BOX); c.setPadding(1);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setMinimumHeight(12f); c.setBackgroundColor(new BaseColor(230, 230, 230));
        t.addCell(c);
    }

    private void addTdC(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX); c.setPadding(1);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setMinimumHeight(14f);
        t.addCell(c);
    }

    private String nvl(String s) { return s != null ? s : ""; }

    public static class KaijinRow {
        public Long introId;
        public String introductionDate = "";
        public LocalDate introDate;
        public String personName = "";
        public Long personId;
        public Long customerId;
        public String formData = "";
        public String empStatus = "就労中";
        public String hireResult = "";
        public String remarks = "";
    }
}
