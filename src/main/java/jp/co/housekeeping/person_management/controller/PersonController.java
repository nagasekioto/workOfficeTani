package jp.co.housekeeping.person_management.controller;

import java.time.LocalDate;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    
    // 名簿入力画面
    @GetMapping("/register")
    public String registerForm(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        
        Iterable<Person> persons = personRepository.findAll();
        model.addAttribute("persons", persons);
        model.addAttribute("person", new Person());
        
        return "person-register";
    }
    
    // 登録処理
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
                          HttpSession session) {
        
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        
        // チェックボックスの値を設定
        person.setQualNursery(qualNursery != null && qualNursery);
        person.setQualCook(qualCook != null && qualCook);
        person.setQualCareWorker(qualCareWorker != null && qualCareWorker);
        person.setQualCareHelper(qualCareHelper != null && qualCareHelper);
        person.setAnimalDogOk(animalDogOk != null && animalDogOk);
        person.setAnimalCatOk(animalCatOk != null && animalCatOk);
        person.setAnimalDogAllergy(animalDogAllergy != null && animalDogAllergy);
        person.setAnimalCatAllergy(animalCatAllergy != null && animalCatAllergy);
        
        // 登録日が空なら今日の日付
        if (person.getRegisteredDate() == null) {
            person.setRegisteredDate(LocalDate.now());
        }
        
        personRepository.save(person);
        
        return "redirect:/person/register";
    }
    
    // 管理簿入力画面
    @GetMapping("/report")
    public String report(HttpSession session) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        return "person-report";
    }
}