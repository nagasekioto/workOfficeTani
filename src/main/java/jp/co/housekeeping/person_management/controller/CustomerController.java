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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.CustomerRequest;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.CustomerRequestRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerRequestRepository customerRequestRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;

    private boolean checkAuth(HttpSession session) {
        return session.getAttribute("authenticated") != null;
    }

    // ─── 1-2 求人者新規登録フォーム ───────────────────
    @GetMapping("/register")
    public String registerForm(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("customers", customerRepository.findAll());
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
            int maxNo = 0;
            for (Customer c : customerRepository.findAll()) {
                if (c.getNo() != null && c.getNo() > maxNo) maxNo = c.getNo();
            }
            customer.setNo(maxNo + 1);
        }
        customerRepository.save(customer);
        return "redirect:/customer/register";
    }

    // ─── 求人者編集フォーム ────────────────────────────
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        Customer c = customerRepository.findById(id).orElse(null);
        if (c == null) return "redirect:/customer/list";
        model.addAttribute("customers", customerRepository.findAll());
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
        });
        customerRepository.save(customer);
        return "redirect:/customer/list";
    }

    // ─── 求人者削除 ────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        customerRepository.deleteById(id);
        return "redirect:/customer/list";
    }

    // ─── 1-2-3 求人者一覧 ──────────────────────────────
    @GetMapping("/list")
    public String list(@RequestParam(required = false) String sort,
                       HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("customers", customerRepository.findAll());
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

            List<KaijinRow> rows = new ArrayList<>();
            salesRepository.findAll().forEach(s ->
                salesDetailRepository.findBySalesId(s.getId()).forEach(d -> {
                    if (customerId.equals(d.getCustomerId())) {
                        KaijinRow row = new KaijinRow();
                        row.introductionDate = d.getIntroductionDate() != null ? d.getIntroductionDate().toString() : "";
                        personRepository.findById(s.getPersonId() != null ? s.getPersonId() : 0L)
                            .ifPresent(p -> row.personName = p.getLastNameKanji() + " " + p.getFirstNameKanji());
                        row.detail = d;
                        rows.add(row);
                    }
                }));
            model.addAttribute("rows", rows);
            model.addAttribute("emptyRows", Math.max(5, 10 - rows.size()));
            if (!rows.isEmpty()) model.addAttribute("latestDetail", rows.get(0).detail);
        }
        return "customer-report";
    }

    // ─── 1-2-2 求人管理簿 PDF出力 ──────────────────────────
    @GetMapping("/report/pdf")
    public void reportPdf(@RequestParam(required = false) Long customerId,
                          @RequestParam(required = false, defaultValue = "inline") String mode,
                          HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (!checkAuth(session)) { response.sendError(401); return; }

        Customer customer = customerId != null
                ? customerRepository.findById(customerId).orElse(null) : null;

        // 紹介履歴行を収集
        List<KaijinRow> rows = new ArrayList<>();
        if (customerId != null) {
            salesRepository.findAll().forEach(s ->
                salesDetailRepository.findBySalesId(s.getId()).forEach(d -> {
                    if (customerId.equals(d.getCustomerId())) {
                        KaijinRow row = new KaijinRow();
                        row.introductionDate = d.getIntroductionDate() != null
                                ? d.getIntroductionDate().toString() : "";
                        personRepository.findById(s.getPersonId() != null ? s.getPersonId() : 0L)
                                .ifPresent(p -> row.personName = p.getLastNameKanji() + " " + p.getFirstNameKanji());
                        row.detail = d;
                        rows.add(row);
                    }
                }));
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
        // [求人者氏名・住所エリア] | [連絡担当者エリア] | [取扱状況エリア]
        PdfPTable hdr = new PdfPTable(new float[]{3.5f, 2.5f, 2.0f});
        hdr.setWidthPercentage(100);
        hdr.setSpacingAfter(2);

        // 左列: 求人者情報
        PdfPTable leftInfo = new PdfPTable(new float[]{1.2f, 4f});
        leftInfo.setWidthPercentage(100);
        addHdrRow(leftInfo, "求人者", "氏名",
                c != null ? (c.getLastNameKanji() + "　" + c.getFirstNameKanji()) : "", bold6, norm7);
        addHdrRow2(leftInfo, "", "〒",
                c != null && c.getPostalCode() != null ? c.getPostalCode() : "", bold6, norm7);
        addHdrRow2(leftInfo, "", "住所",
                c != null ? nvl(c.getAddress1()) + nvl(c.getAddress2()) : "", bold6, norm7);
        addHdrRow2(leftInfo, "", "電話番号",
                c != null ? nvl(c.getHomePhone()) : "", bold6, norm7);
        PdfPCell leftCell = new PdfPCell(leftInfo);
        leftCell.setBorder(Rectangle.BOX); leftCell.setPadding(0);
        hdr.addCell(leftCell);

        // 中列: 連絡担当者
        PdfPTable midInfo = new PdfPTable(new float[]{1.5f, 3f});
        midInfo.setWidthPercentage(100);
        addHdrRow(midInfo, "連絡担当者", "氏名", "", bold6, norm7);
        addHdrRow2(midInfo, "", "電話番号", "", bold6, norm7);
        PdfPCell midCell = new PdfPCell(midInfo);
        midCell.setBorder(Rectangle.BOX); midCell.setPadding(0);
        hdr.addCell(midCell);

        // 右列: 取扱状況ヘッダー
        PdfPTable rightInfo = new PdfPTable(new float[]{1f, 1f});
        rightInfo.setWidthPercentage(100);
        PdfPCell rLabel = new PdfPCell(new Phrase("取扱状況", bold6));
        rLabel.setColspan(2); rLabel.setBorder(Rectangle.BOX); rLabel.setPadding(2);
        rLabel.setHorizontalAlignment(Element.ALIGN_CENTER);
        rightInfo.addCell(rLabel);
        addRightCell(rightInfo, "無期雇用就職者", bold6);
        addRightCell(rightInfo, "取扱状況", bold6);
        PdfPCell rCell = new PdfPCell(rightInfo);
        rCell.setBorder(Rectangle.BOX); rCell.setPadding(0);
        hdr.addCell(rCell);

        doc.add(hdr);

        // ── 求人希望職種行 ──
        PdfPTable jobRow = new PdfPTable(new float[]{2f, 4f, 1.5f, 1.5f, 1.5f});
        jobRow.setWidthPercentage(100);
        jobRow.setSpacingAfter(2);
        addLblVal(jobRow, "求人希望職種", "", bold6, norm7);
        addLblVal(jobRow, "求人数", "　　　人", bold6, norm7);
        addLblVal(jobRow, "就業場所", "", bold6, norm7);
        addLblVal(jobRow, "雇用期間", "", bold6, norm7);
        PdfPCell salaryHdr = new PdfPCell(new Phrase("賃金", bold6));
        salaryHdr.setBorder(Rectangle.BOX); salaryHdr.setPadding(2);
        salaryHdr.setHorizontalAlignment(Element.ALIGN_CENTER);
        jobRow.addCell(salaryHdr);
        doc.add(jobRow);

        // ── メイン表 ──
        // 列: 受付年月日 | 有効期間 | 求人数/人 | 就業場所 | 雇用期間 | 賃金(給・円) | 紹介年月日 | 求職者氏名 | 採否 | 採用年月日 | 備考 | 労働契約 | 無期/転職/返戻 | 取扱(日雇/有期)
        float[] colW = {1.8f, 1.8f, 0.7f, 1.5f, 1.8f, 0.6f, 0.6f, 1.8f, 2.0f, 0.7f, 1.8f, 1.5f, 0.7f, 0.7f, 0.8f, 0.6f, 0.5f, 0.5f};
        PdfPTable tbl = new PdfPTable(colW);
        tbl.setWidthPercentage(100);
        tbl.setSpacingBefore(2);

        // ヘッダー行1
        // ヘッダー行: 18列
        // 受付|有効|求人|就業|雇用|給|円|紹介|求職者|採否|採用|備考|契約|無期|転職禁|返戻|日雇|有期
        addTh(tbl, "受付年月日",        bold6, 1, 2, 30f);
        addTh(tbl, "有効期間",          bold6, 1, 2, 30f);
        addTh(tbl, "求人数\n人",       bold6, 1, 2, 30f);
        addTh(tbl, "就業場所",          bold6, 1, 2, 30f);
        addTh(tbl, "雇用期間",          bold6, 1, 2, 30f);
        // 賃金（給・円の2列、行1でまとめラベル）
        addTh(tbl, "賃金",              bold6, 2, 1, 15f);
        // 取扱状況（紹介～採用）
        addTh(tbl, "紹介年月日",        bold6, 1, 2, 30f);
        addTh(tbl, "求職者氏名",        bold6, 1, 2, 30f);
        addTh(tbl, "採否",              bold6, 1, 2, 30f);
        addTh(tbl, "採用年月日",        bold6, 1, 2, 30f);
        addTh(tbl, "備考",              bold6, 1, 2, 30f);
        addTh(tbl, "労働\n契約",       bold6, 1, 2, 30f);
        addTh(tbl, "無期雇用\n就職者", bold6, 1, 2, 30f);
        addTh(tbl, "転職勧奨\n禁止期間", bold6, 1, 2, 30f);
        addTh(tbl, "返戻金",            bold6, 1, 2, 30f);
        // 取扱状況（日雇/有期）
        addTh(tbl, "取扱\n状況",       bold6, 2, 1, 15f);

        // ヘッダー行2（賃金サブ・取扱状況サブ）
        addThSub(tbl, "給",  bold6);
        addThSub(tbl, "円",  bold6);
        addThSub(tbl, "日雇", bold6);
        addThSub(tbl, "有期", bold6);

        // データ行（実データ + 空行で計40行）
        int DATA_ROWS = 38;
        int filled = 0;
        for (KaijinRow row : rows) {
            if (filled >= DATA_ROWS) break;
            addTdC(tbl, "",                   norm6);  // 受付年月日
            addTdC(tbl, "〜",                 norm6);  // 有効期間
            addTdC(tbl, "",                   norm6);  // 求人数
            addTdC(tbl, "",                   norm6);  // 就業場所
            addTdC(tbl, "〜",                 norm6);  // 雇用期間
            addTdC(tbl, "",                   norm6);  // 給
            addTdC(tbl, "",                   norm6);  // 円
            addTdC(tbl, row.introductionDate,   norm6);  // 紹介年月日
            addTdC(tbl, row.personName,         norm6);  // 求職者氏名
            addTdC(tbl, "",                   norm6);  // 採否
            addTdC(tbl, "",                   norm6);  // 採用年月日
            addTdC(tbl, "",                   norm6);  // 備考
            addTdC(tbl, "",                   norm6);  // 労働契約
            addTdC(tbl, "",                   norm6);  // 無期雇用
            addTdC(tbl, "",                   norm6);  // 転職勧奨禁止
            addTdC(tbl, "",                   norm6);  // 返戻金
            addTdC(tbl, "",                   norm6);  // 日雇
            addTdC(tbl, "",                   norm6);  // 有期
            filled++;
        }
        for (int i = filled; i < DATA_ROWS; i++) {
            for (int col = 0; col < 18; col++) {
                addTdC(tbl, (col == 1 || col == 4) ? "〜" : "", norm6);
            }
        }
        doc.add(tbl);
        doc.close();
    }

    // ─── PDF ヘルパー ────────────────────────────────────────────

    private void addHdrRow(PdfPTable t, String group, String label, String val, Font lf, Font vf) {
        PdfPCell g = new PdfPCell(new Phrase(group, lf));
        g.setBorder(Rectangle.BOX); g.setPadding(2); g.setRowspan(4);
        g.setHorizontalAlignment(Element.ALIGN_CENTER); g.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(g);
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.BOX); lc.setPadding(2); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(val, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(2); t.addCell(vc);
    }

    private void addHdrRow2(PdfPTable t, String group, String label, String val, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.BOX); lc.setPadding(2); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(val, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(2); t.addCell(vc);
    }

    private void addRightCell(PdfPTable t, String label, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(label, f));
        c.setBorder(Rectangle.BOX); c.setPadding(2);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setMinimumHeight(18f);
        t.addCell(c);
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
        c.setFixedHeight(h); c.setBackgroundColor(new BaseColor(220, 220, 220));
        t.addCell(c);
    }

    private void addThSub(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.BOX); c.setPadding(1);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setFixedHeight(12f); c.setBackgroundColor(new BaseColor(230, 230, 230));
        t.addCell(c);
    }

    private void addTdC(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX); c.setPadding(1);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setFixedHeight(14f);
        t.addCell(c);
    }

    private String nvl(String s) { return s != null ? s : ""; }

    public static class KaijinRow {
        public String introductionDate = "";
        public String personName = "";
        public SalesDetail detail;
    }
}
