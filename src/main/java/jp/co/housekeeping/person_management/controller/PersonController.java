package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;

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
import jp.co.housekeeping.person_management.repository.PersonRepository;

@Controller
@RequestMapping("/person")
public class PersonController {

    @Autowired
    private PersonRepository personRepository;

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
            HttpSession session) {

        if (!checkAuth(session)) return "redirect:/login";

        person.setQualNursery(qualNursery != null && qualNursery);
        person.setQualCook(qualCook != null && qualCook);
        person.setQualCareWorker(qualCareWorker != null && qualCareWorker);
        person.setQualCareHelper(qualCareHelper != null && qualCareHelper);
        person.setAnimalDogOk(animalDogOk != null && animalDogOk);
        person.setAnimalCatOk(animalCatOk != null && animalCatOk);
        person.setAnimalDogAllergy(animalDogAllergy != null && animalDogAllergy);
        person.setAnimalCatAllergy(animalCatAllergy != null && animalCatAllergy);
        person.setLineWorks(lineWorks != null && lineWorks);

        if (person.getRegisteredDate() == null) {
            person.setRegisteredDate(LocalDate.now());
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
            HttpSession session) {

        if (!checkAuth(session)) return "redirect:/login";

        person.setQualNursery(qualNursery != null && qualNursery);
        person.setQualCook(qualCook != null && qualCook);
        person.setQualCareWorker(qualCareWorker != null && qualCareWorker);
        person.setQualCareHelper(qualCareHelper != null && qualCareHelper);
        person.setAnimalDogOk(animalDogOk != null && animalDogOk);
        person.setAnimalCatOk(animalCatOk != null && animalCatOk);
        person.setAnimalDogAllergy(animalDogAllergy != null && animalDogAllergy);
        person.setAnimalCatAllergy(animalCatAllergy != null && animalCatAllergy);
        person.setLineWorks(lineWorks != null && lineWorks);

        personRepository.save(person);
        return "redirect:/person/register";
    }

    // ─── 管理簿入力画面 ────────────────────────────────
    @GetMapping("/report")
    public String report(HttpSession session) {
        if (!checkAuth(session)) return "redirect:/login";
        return "person-report";
    }
}
