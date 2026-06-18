package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import jakarta.servlet.http.HttpSession;

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

    // 削除
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        introductionRepository.deleteById(id);
        return "redirect:/introduction/list";
    }
}
