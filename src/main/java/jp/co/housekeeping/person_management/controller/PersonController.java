package jp.co.housekeeping.person_management.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

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
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jp.co.housekeeping.person_management.model.Introduction;
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.RegisterRecord;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.IntroductionRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;
import jp.co.housekeeping.person_management.util.ValidationUtils;

@Controller
@RequestMapping("/person")
public class PersonController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private IntroductionRepository introductionRepository;
    @Autowired private jp.co.housekeeping.person_management.repository.RegisterRecordRepository registerRecordRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private boolean checkAuth(HttpSession session) {
        return session.getAttribute("authenticated") != null;
    }

    // ─── 名簿入力画面（新規登録 + 一覧）───────────────
    @GetMapping("/register")
    public String registerForm(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        List<Person> active = new ArrayList<>();
        for (Person p : personRepository.findAll()) {
            if (p.getRetiredAt() == null) active.add(p);
        }
        model.addAttribute("persons", active);
        model.addAttribute("person", new Person());
        model.addAttribute("editMode", false);
        return "person-register";
    }

    // ─── 新規登録処理 ──────────────────────────────────
    @PostMapping("/register")
    public String register(@ModelAttribute Person person,
            @RequestParam(required = false) Boolean qualNursery,
            @RequestParam(required = false) Boolean qualCook,
            @RequestParam(required = false) Boolean qualCareWorker,
            @RequestParam(required = false) Boolean qualCareHelper,
            @RequestParam(required = false) Boolean animalDogOk,
            @RequestParam(required = false) Boolean animalCatOk,
            @RequestParam(required = false) Boolean animalDogAllergy,
            @RequestParam(required = false) Boolean animalCatAllergy,
            @RequestParam(required = false) Boolean lineWorks,
            // 就職希望条件
            @RequestParam(required = false) List<String> workLocationList,
            @RequestParam(required = false) List<String> workDutiesList,
            @RequestParam(required = false) List<String> desiredTypeList,
            @RequestParam(required = false) List<String> specificDaysList,
            @RequestParam(required = false, defaultValue = "") String specificDaysJson,
            @RequestParam(required = false) String workAvailableHoursFrom,
            @RequestParam(required = false) String workAvailableHoursTo,
            @RequestParam(required = false) String workStartPeriod,
            @RequestParam(required = false) String emergencyRelation,
            @RequestParam(required = false) String emergencyPhone,
            @RequestParam(required = false) String babysitterExp,
            @RequestParam(required = false) String babysitterAvail,
            HttpSession session) {

        if (!checkAuth(session)) return "redirect:/login";

        applyCheckboxes(person, qualNursery, qualCook, qualCareWorker, qualCareHelper,
                animalDogOk, animalCatOk, animalDogAllergy, animalCatAllergy, lineWorks);
        applyJobPrefs(person, workLocationList, workDutiesList, desiredTypeList,
                specificDaysJson, workAvailableHoursFrom, workAvailableHoursTo, workStartPeriod,
                emergencyRelation, emergencyPhone, babysitterExp, babysitterAvail);

        if (person.getRegisteredDate() == null) {
            person.setRegisteredDate(LocalDate.now());
        }
        if (person.getNo() == null) {
            int maxNo = 0;
            for (Person p : personRepository.findAll()) {
                if (p.getNo() != null && p.getNo() > maxNo) maxNo = p.getNo();
            }
            person.setNo(maxNo + 1);
        }

        personRepository.save(person);
        return "redirect:/person/register";
    }

    // ─── 編集画面 ──────────────────────────────────────
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        Person person = personRepository.findById(id).orElse(null);
        if (person == null) return "redirect:/person/register";
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("person", person);
        model.addAttribute("editMode", true);
        return "person-register";
    }

    // ─── 更新処理 ──────────────────────────────────────
    @PostMapping("/update")
    public String update(@ModelAttribute Person person,
            @RequestParam(required = false) Boolean qualNursery,
            @RequestParam(required = false) Boolean qualCook,
            @RequestParam(required = false) Boolean qualCareWorker,
            @RequestParam(required = false) Boolean qualCareHelper,
            @RequestParam(required = false) Boolean animalDogOk,
            @RequestParam(required = false) Boolean animalCatOk,
            @RequestParam(required = false) Boolean animalDogAllergy,
            @RequestParam(required = false) Boolean animalCatAllergy,
            @RequestParam(required = false) Boolean lineWorks,
            // 就職希望条件
            @RequestParam(required = false) List<String> workLocationList,
            @RequestParam(required = false) List<String> workDutiesList,
            @RequestParam(required = false) List<String> desiredTypeList,
            @RequestParam(required = false) List<String> specificDaysList,
            @RequestParam(required = false, defaultValue = "") String specificDaysJson,
            @RequestParam(required = false) String workAvailableHoursFrom,
            @RequestParam(required = false) String workAvailableHoursTo,
            @RequestParam(required = false) String workStartPeriod,
            @RequestParam(required = false) String emergencyRelation,
            @RequestParam(required = false) String emergencyPhone,
            @RequestParam(required = false) String babysitterExp,
            @RequestParam(required = false) String babysitterAvail,
            HttpSession session) {

        if (!checkAuth(session)) return "redirect:/login";

        applyCheckboxes(person, qualNursery, qualCook, qualCareWorker, qualCareHelper,
                animalDogOk, animalCatOk, animalDogAllergy, animalCatAllergy, lineWorks);
        applyJobPrefs(person, workLocationList, workDutiesList, desiredTypeList,
                specificDaysJson, workAvailableHoursFrom, workAvailableHoursTo, workStartPeriod,
                emergencyRelation, emergencyPhone, babysitterExp, babysitterAvail);

        personRepository.findById(person.getId()).ifPresent(existing -> {
            if (person.getRegisteredDate() == null) person.setRegisteredDate(existing.getRegisteredDate());
            if (person.getBirthDate() == null) person.setBirthDate(existing.getBirthDate());
            if (person.getHomePhone() == null) person.setHomePhone(existing.getHomePhone());
            if (person.getMobilePhone() == null) person.setMobilePhone(existing.getMobilePhone());
            if (person.getFaxPhone() == null) person.setFaxPhone(existing.getFaxPhone());
            if (person.getNo() == null) person.setNo(existing.getNo());
            if (person.getRetiredAt() == null) person.setRetiredAt(existing.getRetiredAt());
        });

        personRepository.save(person);
        return "redirect:/person/register";
    }

    // ─── 共通：チェックボックス処理 ────────────────────
    private void applyCheckboxes(Person person,
            Boolean qualNursery, Boolean qualCook, Boolean qualCareWorker, Boolean qualCareHelper,
            Boolean animalDogOk, Boolean animalCatOk, Boolean animalDogAllergy, Boolean animalCatAllergy,
            Boolean lineWorks) {
        person.setQualNursery(qualNursery != null && qualNursery);
        person.setQualCook(qualCook != null && qualCook);
        person.setQualCareWorker(qualCareWorker != null && qualCareWorker);
        person.setQualCareHelper(qualCareHelper != null && qualCareHelper);
        person.setAnimalDogOk(animalDogOk != null && animalDogOk);
        person.setAnimalCatOk(animalCatOk != null && animalCatOk);
        person.setAnimalDogAllergy(animalDogAllergy != null && animalDogAllergy);
        person.setAnimalCatAllergy(animalCatAllergy != null && animalCatAllergy);
        person.setLineWorks(lineWorks != null && lineWorks);
    }

    // ─── 共通：就職希望条件処理 ────────────────────────
    private void applyJobPrefs(Person person,
            List<String> workLocationList,
            List<String> workDutiesList,
            List<String> desiredTypeList,
            String specificDaysJson,
            String workAvailableHoursFrom,
            String workAvailableHoursTo,
            String workStartPeriod,
            String emergencyRelation,
            String emergencyPhone,
            String babysitterExp,
            String babysitterAvail) {

        // 就労場所
        person.setWorkLocation(listToStr(workLocationList));

        // 職務内容
        person.setWorkDuties(listToStr(workDutiesList));

        // 希望形態（複数選択）→ desiredTypes に保存、desiredType（旧）は先頭値
        String typesStr = listToStr(desiredTypeList);
        person.setDesiredTypes(typesStr);
        if (desiredTypeList != null && !desiredTypeList.isEmpty()) {
            person.setDesiredType(desiredTypeList.get(0));
        }

        // 特定日（JS側でJSON文字列を作成してhiddenフィールドに入れる）
        person.setSpecificDays(specificDaysJson != null && !specificDaysJson.isBlank()
                ? specificDaysJson : null);

        // 就業可能時間
        if (workAvailableHoursFrom != null && !workAvailableHoursFrom.isBlank()
                && workAvailableHoursTo != null && !workAvailableHoursTo.isBlank()) {
            person.setWorkAvailableHours(workAvailableHoursFrom + "-" + workAvailableHoursTo);
        } else {
            person.setWorkAvailableHours(null);
        }

        // 労働開始時期
        person.setWorkStartPeriod(workStartPeriod != null && !workStartPeriod.isBlank()
                ? workStartPeriod : null);

        // 緊急連絡先
        person.setEmergencyRelation(emergencyRelation != null && !emergencyRelation.isBlank()
                ? emergencyRelation : null);
        person.setEmergencyPhone(emergencyPhone != null && !emergencyPhone.isBlank()
                ? emergencyPhone : null);

        // ベビーシッター
        person.setBabysitterExp(babysitterExp != null && !babysitterExp.isBlank()
                ? babysitterExp : null);
        person.setBabysitterAvail(babysitterAvail != null && !babysitterAvail.isBlank()
                ? babysitterAvail : null);
    }

    private String listToStr(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(",", list);
    }

    // ─── 1-1-6 求職者情報一覧 ──────────────────────────
    @GetMapping("/list")
    public String list(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        List<Person> active = new ArrayList<>();
        for (Person p : personRepository.findAll()) {
            if (p.getRetiredAt() == null) active.add(p);
        }
        model.addAttribute("persons", active);
        return "person-list";
    }

    // ─── 求職者を退職扱いにする（1-1-6の「削除」ボタン→「退職」ボタン）───
    @PostMapping("/retire/{id}")
    public String retire(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        Person p = personRepository.findById(id).orElse(null);
        if (p != null) {
            p.setRetiredAt(LocalDate.now());
            personRepository.save(p);
        }
        return "redirect:/person/list";
    }

    // ─── 1-1-8 退職者リスト ────────────────────────────
    @GetMapping("/retired-list")
    public String retiredList(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        List<Person> retired = new ArrayList<>();
        for (Person p : personRepository.findAll()) {
            if (p.getRetiredAt() != null) retired.add(p);
        }
        retired.sort((a, b) -> b.getRetiredAt().compareTo(a.getRetiredAt()));
        model.addAttribute("persons", retired);
        return "person-retired-list";
    }

    // ─── 退職を取り消して在職中に戻す ──────────────────────
    @PostMapping("/retired-list/reinstate/{id}")
    public String reinstate(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        Person p = personRepository.findById(id).orElse(null);
        if (p != null) {
            p.setRetiredAt(null);
            personRepository.save(p);
        }
        return "redirect:/person/retired-list";
    }

    // ─── 求職者削除（退職者リストからの完全削除。元に戻せません）─────
    @PostMapping("/retired-list/delete/{id}")
    public String deleteRetired(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        personRepository.deleteById(id);
        return "redirect:/person/retired-list";
    }

    // ─── 1-1-7 会費 ────────────────────────────────────
    @GetMapping("/membership")
    public String membership(@RequestParam(required = false) String month,
                              HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";

        if (month == null || month.isBlank()) {
            return "redirect:/person/membership?month=" + YearMonth.now();
        }

        List<Person> persons = new ArrayList<>();
        personRepository.findAll().forEach(persons::add);

        // 選択月に会費入金(membership_fee > 0)のあった求職者idの集合（自動判定用）
        java.util.Set<Long> autoPaid = new java.util.HashSet<>();
        for (RegisterRecord r : registerRecordRepository.findByWorkMonth(month)) {
            if (r.getMembershipFee() != null && r.getMembershipFee() > 0 && r.getPersonId() != null) {
                autoPaid.add(r.getPersonId());
            }
        }

        // 手動の振込確認チェック(membership_confirmations)を選択月ぶん取得
        java.util.Map<Long, Boolean> confirmedMap = new java.util.HashMap<>();
        jdbcTemplate.query(
            "SELECT person_id, confirmed FROM membership_confirmations WHERE work_month = ?",
            rs -> {
                confirmedMap.put(rs.getLong("person_id"), rs.getBoolean("confirmed"));
            }, month);

        List<MembershipRow> rows = new ArrayList<>();
        for (Person p : persons) {
            MembershipRow row = new MembershipRow();
            row.id = p.getId();
            row.name = p.getLastNameKanji() + " " + p.getFirstNameKanji();
            row.membershipFee = p.getMembershipFee() != null ? p.getMembershipFee() : "無";
            row.membershipFeeAmount = p.getMembershipFeeAmount();
            row.hasFee = "有".equals(row.membershipFee);
            // 手動チェックが記録されていればそれを優先、無ければ振込金入力の自動判定を使う
            row.paidThisMonth = confirmedMap.containsKey(p.getId())
                ? confirmedMap.get(p.getId())
                : autoPaid.contains(p.getId());
            rows.add(row);
        }

        model.addAttribute("rows", rows);
        model.addAttribute("currentMonth", month);
        model.addAttribute("selectedMonth", month);
        return "person-membership";
    }

    @PostMapping("/membership/confirm")
    @ResponseBody
    public String membershipConfirm(@RequestParam Long personId,
                                     @RequestParam String month,
                                     @RequestParam boolean confirmed,
                                     HttpSession session,
                                     HttpServletResponse response) {
        if (!checkAuth(session)) { response.setStatus(401); return "UNAUTHORIZED"; }
        jdbcTemplate.update(
            "INSERT INTO membership_confirmations (person_id, work_month, confirmed, updated_at) " +
            "VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (person_id, work_month) DO UPDATE SET confirmed = EXCLUDED.confirmed, updated_at = CURRENT_TIMESTAMP",
            personId, month, confirmed);
        return "OK";
    }

    @PostMapping("/membership/save")
    @ResponseBody
    public String membershipSave(@RequestParam Long personId,
                                  @RequestParam String membershipFee,
                                  @RequestParam(required = false) Integer membershipFeeAmount,
                                  HttpSession session,
                                  HttpServletResponse response) {
        if (!checkAuth(session)) { response.setStatus(401); return "UNAUTHORIZED"; }
        if (membershipFeeAmount != null && ValidationUtils.requireNonNegative(membershipFeeAmount) == null) {
            response.setStatus(400);
            return "INVALID_AMOUNT";
        }
        personRepository.findById(personId).ifPresent(p -> {
            p.setMembershipFee(membershipFee);
            p.setMembershipFeeAmount("有".equals(membershipFee) ? membershipFeeAmount : null);
            personRepository.save(p);
        });
        return "OK";
    }

    public static class MembershipRow {
        public Long id;
        public String name;
        public String membershipFee;       // '有' / '無'
        public Integer membershipFeeAmount; // 1550 / 350
        public boolean hasFee;
        public boolean paidThisMonth;
    }

    // ─── 1-1-4 求職管理簿 ──────────────────────────────
    @GetMapping("/shokuji-ledger")
    public String shokujiLedger(@RequestParam(required = false) Long personId,
                                HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";

        model.addAttribute("persons", personRepository.findAll());

        if (personId != null) {
            Person person = personRepository.findById(personId).orElse(null);
            model.addAttribute("selectedPerson", person);

            List<PersonLedgerRow> rows = buildPersonRowsFromIntroductions(personId);
            model.addAttribute("rows", rows);
            model.addAttribute("emptyRows", Math.max(5, 10 - rows.size()));
        }
        return "person-shokuji-ledger";
    }

    // 紹介履歴行を「紹介状一覧（1-6-2）」のデータから構築する
    private List<PersonLedgerRow> buildPersonRowsFromIntroductions(Long personId) {
        List<PersonLedgerRow> rows = new ArrayList<>();
        if (personId == null) return rows;

        for (Introduction intro : introductionRepository.findAll()) {
            if (!personId.equals(intro.getPersonId())) continue;
            PersonLedgerRow row = new PersonLedgerRow();
            row.introId = intro.getId();
            row.introDate = intro.getIntroDate();
            row.introductionDate = intro.getIntroDate() != null ? intro.getIntroDate().toString() : "";
            row.customerId = intro.getCustomerId();
            if (intro.getCustomerId() != null) {
                customerRepository.findById(intro.getCustomerId())
                    .ifPresent(c -> row.customerName = c.getLastNameKanji() + " " + c.getFirstNameKanji());
            }
            row.hireResult = pnvl(intro.getHireResult());
            row.remarks = pnvl(intro.getLedgerRemarks());
            row.formData = intro.getFormData() != null ? intro.getFormData() : "";
            row.rishokuStatus = pnvl(intro.getRishokuStatus());
            row.henreikin = pnvl(intro.getHenreikin());
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

    // ─── 1-1-4 求職管理簿 採否・備考・離職状況・返戻金の保存 ──
    @PostMapping("/shokuji-ledger/save-row-status")
    @ResponseBody
    public String saveShokujiRowStatus(@RequestParam Long personId,
                                       @RequestParam(required = false) Long[] introIds,
                                       @RequestParam(required = false) String[] hireResultList,
                                       @RequestParam(required = false) String[] remarksList,
                                       @RequestParam(required = false) String[] rishokuStatusList,
                                       @RequestParam(required = false) String[] henreikinList,
                                       HttpSession session,
                                       HttpServletResponse response) {
        if (!checkAuth(session)) { response.setStatus(401); return "UNAUTHORIZED"; }
        if (introIds == null) return "OK";

        for (int i = 0; i < introIds.length; i++) {
            Long introId = introIds[i];
            if (introId == null) continue;
            final int idx = i;
            introductionRepository.findById(introId).ifPresent(intro -> {
                if (hireResultList != null && idx < hireResultList.length) {
                    intro.setHireResult(hireResultList[idx]);
                }
                if (remarksList != null && idx < remarksList.length) {
                    intro.setLedgerRemarks(remarksList[idx]);
                }
                if (rishokuStatusList != null && idx < rishokuStatusList.length) {
                    intro.setRishokuStatus(rishokuStatusList[idx]);
                }
                if (henreikinList != null && idx < henreikinList.length) {
                    intro.setHenreikin(henreikinList[idx]);
                }
                introductionRepository.save(intro);
            });
        }
        return "OK";
    }

    // ─── 1-1-4 求職管理簿 PDF出力 ──────────────────────────
    @GetMapping("/shokuji-ledger/pdf")
    public void shokujiLedgerPdf(@RequestParam(required = false) Long personId,
                                 @RequestParam(required = false, defaultValue = "inline") String mode,
                                 @RequestParam(required = false) String[] hireResultList,
                                 @RequestParam(required = false) String[] remarksList,
                                 @RequestParam(required = false) String[] rishokuStatusList,
                                 @RequestParam(required = false) String[] henreikinList,
                                 HttpSession session, HttpServletResponse response)
            throws IOException, DocumentException {
        if (!checkAuth(session)) { response.sendError(401); return; }

        Person person = personId != null
                ? personRepository.findById(personId).orElse(null) : null;

        List<PersonLedgerRow> rows = buildPersonRowsFromIntroductions(personId);
        for (int i = 0; i < rows.size(); i++) {
            PersonLedgerRow row = rows.get(i);
            if (hireResultList != null && i < hireResultList.length && hireResultList[i] != null) {
                row.hireResult = hireResultList[i];
            }
            if (remarksList != null && i < remarksList.length && remarksList[i] != null) {
                row.remarks = remarksList[i];
            }
            if (rishokuStatusList != null && i < rishokuStatusList.length && rishokuStatusList[i] != null) {
                row.rishokuStatus = rishokuStatusList[i];
            }
            if (henreikinList != null && i < henreikinList.length && henreikinList[i] != null) {
                row.henreikin = henreikinList[i];
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildPersonLedgerPdf(person, rows, baos);

        response.setContentType("application/pdf");
        boolean download = "download".equals(mode);
        response.setHeader("Content-Disposition",
                (download ? "attachment" : "inline") + "; filename=shokuji-ledger.pdf");
        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    private void buildPersonLedgerPdf(Person p, List<PersonLedgerRow> rows, ByteArrayOutputStream baos)
            throws DocumentException, IOException {

        Document doc = new Document(PageSize.A4, 14, 14, 14, 14);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf  = BaseFont.createFont("HeiseiMin-W3", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font title   = new Font(bf, 14, Font.BOLD);
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
        PdfPCell titleCell = new PdfPCell(new Phrase("求　職　管　理　簿", title));
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setPaddingBottom(3);
        titleTbl.addCell(titleCell);
        doc.add(titleTbl);

        // 電話番号：自宅電話番号を優先、無ければ携帯電話番号を使用
        String personPhone = "";
        if (p != null) {
            String home = p.getHomePhone();
            if (home != null && !home.isBlank() && !home.equals("-")) {
                personPhone = home;
            } else {
                personPhone = pnvl(p.getMobilePhone());
            }
        }

        // ── ヘッダー部（求職者情報） ──
        PdfPTable hdr = new PdfPTable(new float[]{1f});
        hdr.setWidthPercentage(100);
        hdr.setSpacingAfter(2);

        // 求職者情報（氏名・〒・住所・電話番号・生年月日・希望職種の6行、各18f＝合計108f）
        PdfPTable leftInfo = new PdfPTable(new float[]{1.0f, 1.2f, 4f});
        leftInfo.setWidthPercentage(100);
        addPHdrRow(leftInfo, "求職者", "氏名",
                p != null ? (p.getLastNameKanji() + "　" + p.getFirstNameKanji()) : "", groupFont, infoLabel, infoVal, 6, 18f);
        addPHdrRow2(leftInfo, "〒",
                p != null && p.getPostalCode() != null ? p.getPostalCode() : "", infoLabel, infoVal, 18f);
        addPHdrRow2(leftInfo, "住所",
                p != null ? pnvl(p.getAddress1()) + pnvl(p.getAddress2()) + pnvl(p.getAddress3()) : "", infoLabel, infoVal, 18f);
        addPHdrRow2(leftInfo, "電話番号", personPhone, infoLabel, infoVal, 18f);
        addPHdrRow2(leftInfo, "生年月日",
                p != null && p.getBirthDate() != null ? p.getBirthDate().toString() : "", infoLabel, infoVal, 18f);
        addPHdrRow2(leftInfo, "希望職種",
                p != null ? pnvl(p.getDesiredJob()) + (p.getDesiredType() != null && !p.getDesiredType().isBlank() ? "　/　" + p.getDesiredType() : "") : "",
                infoLabel, infoVal, 18f);
        PdfPCell leftCell = new PdfPCell(leftInfo);
        leftCell.setBorder(Rectangle.BOX); leftCell.setPadding(0);
        hdr.addCell(leftCell);

        doc.add(hdr);

        // ── メイン表 ──
        // 列: 受付年月日|有効期間|取扱状況(紹介年月日・求人者氏名・採否・採用年月日)|備考|
        //     取扱状況(労働契約・無期雇用就職者(転職勧奨禁止期間・離職状況(6カ月以内または不明・返戻金)))
        float[] colW = {1.6f, 1.8f, 1.6f, 1.9f, 0.7f, 1.6f, 1.6f, 0.9f, 1.4f, 1.4f, 0.7f};
        PdfPTable tbl = new PdfPTable(colW);
        tbl.setWidthPercentage(100);
        tbl.setSpacingBefore(2);

        // ヘッダー行1（11列。受付年月日・有効期間・備考はrowspan4で4段分を貫通）
        addPTh(tbl, "受付年月日", bold6, 1, 4, 15f);
        addPTh(tbl, "有効期間",   bold6, 1, 4, 15f);
        addPTh(tbl, "取扱状況",   bold6, 4, 1, 15f);
        addPTh(tbl, "備考",       bold6, 1, 4, 15f);
        addPTh(tbl, "取扱状況",   bold6, 4, 1, 15f);

        // ヘッダー行2：紹介年月日・求人者氏名・採否・採用年月日（rowspan3）｜労働契約（rowspan3）｜無期雇用就職者
        addPTh(tbl, "紹介年月日", bold6, 1, 3, 15f);
        addPTh(tbl, "求人者氏名", bold6, 1, 3, 15f);
        addPTh(tbl, "採否",       bold6, 1, 3, 15f);
        addPTh(tbl, "採用年月日", bold6, 1, 3, 15f);
        addPTh(tbl, "労働\n契約", bold6, 1, 3, 15f);
        addPTh(tbl, "無期雇用就職者", bold6, 3, 1, 15f);

        // ヘッダー行3：転職勧奨禁止期間（rowspan2）｜離職状況
        addPTh(tbl, "転職勧奨禁止期間\n（2018.1以降）", bold5, 1, 2, 15f);
        addPTh(tbl, "離職状況", bold6, 2, 1, 15f);

        // ヘッダー行4：6カ月以内または不明｜返戻金
        addPThSub(tbl, "6カ月以内\nまたは不明", bold5);
        addPThSub(tbl, "返戻\n金", bold6);

        // データ行（実データ + 空行で計38行）
        int DATA_ROWS = 38;
        int filled = 0;
        ObjectMapper mapper = new ObjectMapper();
        for (PersonLedgerRow row : rows) {
            if (filled >= DATA_ROWS) break;

            LocalDate introDate = row.introDate;
            String introDateStr = introDate != null ? pformatDot(introDate) : "";
            String validPeriod  = pformatValidPeriod(introDate);
            String laborContract = extractLaborContract(mapper, row.formData);

            // 労働契約が「無期」の場合、転職勧奨禁止期間＝採用年月日から2年間
            String tenshokuPeriod = "";
            if ("無期".equals(laborContract) && introDate != null) {
                LocalDate end = introDate.plusYears(2).minusDays(1);
                tenshokuPeriod = pformatDot(introDate) + "　〜　" + pformatDot(end);
            }

            addPTdC(tbl, introDateStr,        norm6);  // 受付年月日（紹介年月日と同じ）
            addPTdC(tbl, validPeriod,         norm6);  // 有効期間
            addPTdC(tbl, introDateStr,        norm6);  // 紹介年月日
            addPTdC(tbl, row.customerName,    norm6);  // 求人者氏名
            addPTdC(tbl, pnvl(row.hireResult),norm6);  // 採否
            addPTdC(tbl, introDateStr,        norm6);  // 採用年月日（紹介年月日と同じ）
            addPTdC(tbl, pnvl(row.remarks),   norm6);  // 備考
            addPTdC(tbl, laborContract,       norm6);  // 労働契約
            addPTdC(tbl, tenshokuPeriod,      norm6);  // 転職勧奨禁止期間
            addPTdC(tbl, pnvl(row.rishokuStatus), norm6);  // 離職状況（6カ月以内または不明）
            addPTdC(tbl, pnvl(row.henreikin),     norm6);  // 返戻金
            filled++;
        }
        for (int i = filled; i < DATA_ROWS; i++) {
            for (int col = 0; col < 11; col++) {
                addPTdC(tbl, (col == 1) ? "〜" : "", norm6);
            }
        }
        doc.add(tbl);
        doc.close();
    }

    // ─── 求職管理簿PDF用ヘルパー ──────────────────────────
    private void addPHdrRow(PdfPTable t, String group, String label, String val, Font gf, Font lf, Font vf, int rowspan, float rowHeight) {
        PdfPCell g = new PdfPCell(new Phrase(group, gf));
        g.setBorder(Rectangle.BOX); g.setPadding(2); g.setRowspan(rowspan);
        g.setHorizontalAlignment(Element.ALIGN_CENTER); g.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(g);
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.BOX); lc.setPadding(2); lc.setMinimumHeight(rowHeight); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(val, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(2); vc.setMinimumHeight(rowHeight); t.addCell(vc);
    }

    private void addPHdrRow2(PdfPTable t, String label, String val, Font lf, Font vf, float rowHeight) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.BOX); lc.setPadding(2); lc.setMinimumHeight(rowHeight); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(val, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(2); vc.setMinimumHeight(rowHeight); t.addCell(vc);
    }

    private void addPTh(PdfPTable t, String text, Font f, int colspan, int rowspan, float h) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setColspan(colspan); c.setRowspan(rowspan);
        c.setBorder(Rectangle.BOX); c.setPadding(2);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setMinimumHeight(h); c.setBackgroundColor(new BaseColor(220, 220, 220));
        t.addCell(c);
    }

    private void addPThSub(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.BOX); c.setPadding(1);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setMinimumHeight(12f); c.setBackgroundColor(new BaseColor(230, 230, 230));
        t.addCell(c);
    }

    private void addPTdC(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
        c.setBorder(Rectangle.BOX); c.setPadding(1);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setMinimumHeight(14f);
        t.addCell(c);
    }

    private String pnvl(String s) { return s != null ? s : ""; }

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

    private String pformatDot(LocalDate d) {
        if (d == null) return "";
        return d.getYear() + "." + d.getMonthValue() + "." + d.getDayOfMonth();
    }

    private String pformatValidPeriod(LocalDate introDate) {
        if (introDate == null) return "";
        YearMonth ym = YearMonth.from(introDate);
        return pformatDot(ym.atDay(1)) + "〜" + pformatDot(ym.atEndOfMonth());
    }

    public static class PersonLedgerRow {
        public Long introId;
        public LocalDate introDate;
        public String introductionDate = "";
        public Long customerId;
        public String customerName = "";
        public String hireResult = "";
        public String remarks = "";
        public String formData = "";
        public String rishokuStatus = "";
        public String henreikin = "";
    }

    // ─── 1-1-6 紹介状 ──────────────────────────────────
}
