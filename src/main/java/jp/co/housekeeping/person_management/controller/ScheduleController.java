package jp.co.housekeeping.person_management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.Schedule;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.ScheduleRepository;

@Controller
public class ScheduleController {
    
    @Autowired
    private PersonRepository personRepository;
    
    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private ScheduleRepository scheduleRepository;
    
    @GetMapping("/person/schedule")
    public String schedule(@RequestParam(required = false) Long personId, 
                          HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) {
            return "redirect:/login";
        }
        
        // 求職者一覧
        Iterable<Person> persons = personRepository.findAll();
        model.addAttribute("persons", persons);
        
        // 求人者一覧
        Iterable<Customer> customers = customerRepository.findAll();
        model.addAttribute("customers", customers);
        
        if (personId != null) {
            // 選択された求職者のスケジュール取得
            List<Schedule> schedules = scheduleRepository.findByPersonId(personId);
            
            // スケジュールをマップ化
            Map<String, String> scheduleMap = new HashMap<>();
            for (Schedule schedule : schedules) {
                String key = schedule.getDayOfWeek() + "_" + schedule.getTimeSlot();
                
                // 求人者名を取得
                Optional<Customer> customer = customerRepository.findById(schedule.getCustomerId());
                if (customer.isPresent()) {
                    Customer c = customer.get();
                    scheduleMap.put(key, c.getLastNameKanji() + " " + c.getFirstNameKanji());
                }
            }
            
            model.addAttribute("scheduleMap", scheduleMap);
            model.addAttribute("selectedPersonId", personId);
        }
        
        return "person-schedule";
    }
}