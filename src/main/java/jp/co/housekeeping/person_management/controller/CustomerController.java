package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.CustomerRequestRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerRequestRepository customerRequestRepository;
    @Autowired private PersonRepository personRepository;

    private boolean checkAuth(HttpSession session) {
        return session.getAttribute("authenticated") != null;
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
    @GetMapping("/register")
    public String registerForm(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("customers", customerRepository.findAll());
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customer", new Customer());
        model.addAttribute("editMode", false);
        return "customer-register";
    }

    // ─── 求人者新規登録 ────────────────────────────────
    @PostMapping("/register")
    public String register(@ModelAttribute Customer customer, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        if (customer.getRegisteredDate() == null) customer.setRegisteredDate(LocalDate.now());
        customerRepository.save(customer);
        return "redirect:/customer/register";
    }

    // ─── 求人者編集 ────────────────────────────────────
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

    @PostMapping("/update")
    public String update(@ModelAttribute Customer customer, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        customerRepository.save(customer);
        return "redirect:/customer/list";
    }

    // ─── 1-2-1 求人受付表 ─────────────────────────────
    @GetMapping("/request/new")
    public String requestForm(@RequestParam(required = false) Long customerId,
                              HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("customers", customerRepository.findAll());
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("selectedCustomerId", customerId);
        model.addAttribute("request", new CustomerRequest());

        // 求人者が選択されている場合、住所を自動設定
        if (customerId != null) {
            customerRepository.findById(customerId).ifPresent(c -> {
                model.addAttribute("selectedCustomer", c);
            });
        }
        return "customer-request-form";
    }

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

    // 求人受付表一覧
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
    public String report(HttpSession session, Model model) {
        if (!checkAuth(session)) return "redirect:/login";
        model.addAttribute("customers", customerRepository.findAll());
        model.addAttribute("requests", customerRequestRepository.findAllOrdered());
        model.addAttribute("persons", personRepository.findAll());
        return "customer-report";
    }
}
