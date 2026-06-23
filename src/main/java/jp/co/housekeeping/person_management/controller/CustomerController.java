package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    public static class KaijinRow {
        public String introductionDate = "";
        public String personName = "";
        public SalesDetail detail;
    }
}
