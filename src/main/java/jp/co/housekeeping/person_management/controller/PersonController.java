package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

@Controller
@RequestMapping("/person")
public class PersonController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;

    private boolean checkAuth(HttpSession session) {
        return session.getAttribute("authenticated") != null;
    }

    // ─── 名簿入力画面（新規登録 + 一覧）───────────────
    @GetMapping("/register")
    public String registerForm(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("persons", personRepository.findAll());
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
            HttpSession session) {

        if (!checkAuth(session)) return "redirect:/login";

        applyCheckboxes(person, qualNursery, qualCook, qualCareWorker, qualCareHelper,
                animalDogOk, animalCatOk, animalDogAllergy, animalCatAllergy, lineWorks);
        applyJobPrefs(person, workLocationList, workDutiesList, desiredTypeList,
                specificDaysJson, workAvailableHoursFrom, workAvailableHoursTo, workStartPeriod);

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
            HttpSession session) {

        if (!checkAuth(session)) return "redirect:/login";

        applyCheckboxes(person, qualNursery, qualCook, qualCareWorker, qualCareHelper,
                animalDogOk, animalCatOk, animalDogAllergy, animalCatAllergy, lineWorks);
        applyJobPrefs(person, workLocationList, workDutiesList, desiredTypeList,
                specificDaysJson, workAvailableHoursFrom, workAvailableHoursTo, workStartPeriod);

        personRepository.findById(person.getId()).ifPresent(existing -> {
            if (person.getRegisteredDate() == null) person.setRegisteredDate(existing.getRegisteredDate());
            if (person.getBirthDate() == null) person.setBirthDate(existing.getBirthDate());
            if (person.getHomePhone() == null) person.setHomePhone(existing.getHomePhone());
            if (person.getMobilePhone() == null) person.setMobilePhone(existing.getMobilePhone());
            if (person.getFaxPhone() == null) person.setFaxPhone(existing.getFaxPhone());
            if (person.getNo() == null) person.setNo(existing.getNo());
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
            String workStartPeriod) {

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
    }

    private String listToStr(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(",", list);
    }

    // ─── 1-1-6 求職者情報一覧 ──────────────────────────
    @GetMapping("/list")
    public String list(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("persons", personRepository.findAll());
        return "person-list";
    }

    // ─── 求職者削除 ────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        personRepository.deleteById(id);
        return "redirect:/person/register";
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

            List<jp.co.housekeeping.person_management.model.Sales> salesList =
                salesRepository.findByPersonId(personId);
            List<WorkingLedgerController.LedgerRow> rows = new ArrayList<>();
            for (jp.co.housekeeping.person_management.model.Sales s : salesList) {
                List<jp.co.housekeeping.person_management.model.SalesDetail> details =
                    salesDetailRepository.findBySalesId(s.getId());
                for (jp.co.housekeeping.person_management.model.SalesDetail d : details) {
                    WorkingLedgerController.LedgerRow row = new WorkingLedgerController.LedgerRow();
                    row.detail = d;
                    row.sales = s;
                    customerRepository.findById(d.getCustomerId() != null ? d.getCustomerId() : 0L)
                        .ifPresent(c -> row.customerName = c.getLastNameKanji() + " " + c.getFirstNameKanji());
                    rows.add(row);
                }
            }
            model.addAttribute("rows", rows);
            model.addAttribute("emptyRows", Math.max(5, 10 - rows.size()));
        }
        return "person-shokuji-ledger";
    }

    // ─── 1-1-6 紹介状 ──────────────────────────────────
    @GetMapping("/introduction")
    public String introduction(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customers", customerRepository.findAll());
        return "person-introduction";
    }
}
