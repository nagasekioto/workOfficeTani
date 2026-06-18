package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jp.co.housekeeping.person_management.model.Introduction;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.IntroductionRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;

@Controller
@RequestMapping("/introduction")
public class IntroductionController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private IntroductionRepository introductionRepository;

    @GetMapping("")
    public String introduction(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";
        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customers", customerRepository.findAll());
        return "introduction";
    }

    @PostMapping("/save")
    @ResponseBody
    public String save(
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String introDate,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String formData,
            HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "UNAUTHORIZED";

        int maxNo = introductionRepository.findMaxRefNo();
        String refNo = String.format("%04d", maxNo + 1);

        Introduction intro = new Introduction();
        intro.setRefNo(refNo);
        intro.setPersonId(personId);
        intro.setCustomerId(customerId);
        try { if (introDate != null && !introDate.isBlank()) intro.setIntroDate(LocalDate.parse(introDate)); } catch (Exception ignored) {}
        try { if (startDate != null && !startDate.isBlank()) intro.setStartDate(LocalDate.parse(startDate)); } catch (Exception ignored) {}
        intro.setFormData(formData);
        introductionRepository.save(intro);
        return refNo;
    }
}
