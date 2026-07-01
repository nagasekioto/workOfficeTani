package jp.co.housekeeping.person_management.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.housekeeping.person_management.model.Introduction;
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.IntroductionRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;

@Controller
public class ScheduleController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private IntroductionRepository introductionRepository;

    private static final int HOUR_START = 7;
    private static final int HOUR_END   = 22;
    private static final String[] DAYS  = {"月", "火", "水", "木", "金", "土", "日"};

    @GetMapping("/person/schedule")
    public String schedule(@RequestParam(required = false) Long personId,
                           HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("hours", buildHourList());
        model.addAttribute("days", java.util.Arrays.asList(DAYS));

        if (personId != null) {
            Person person = personRepository.findById(personId).orElse(null);
            model.addAttribute("selectedPerson", person);
            model.addAttribute("selectedPersonId", personId);

            // key = "月_7"  → 値 = 求人者名のリスト（重複もありうる）
            Map<String, List<String>> bookedMap = new LinkedHashMap<>();
            ObjectMapper mapper = new ObjectMapper();

            for (Introduction intro : introductionRepository.findAll()) {
                if (!Long.valueOf(personId).equals(intro.getPersonId())) continue;
                if (intro.getFormData() == null || intro.getFormData().isBlank()) continue;

                try {
                    JsonNode fd = mapper.readTree(intro.getFormData());
                    String dowStr = fd.path("dow").asText("");
                    int startH = parseIntSafe(fd.path("wSH").asText(""));
                    int startM = parseIntSafe(fd.path("wSM").asText(""));
                    int endH   = parseIntSafe(fd.path("wEH").asText(""));
                    int endM   = parseIntSafe(fd.path("wEM").asText(""));

                    if (dowStr.isBlank() || startH < 0 || endH < 0) continue;

                    final String[] custName = {""};
                    if (intro.getCustomerId() != null) {
                        customerRepository.findById(intro.getCustomerId())
                            .ifPresent(c -> custName[0] = c.getLastNameKanji() + " " + c.getFirstNameKanji());
                    }

                    int startMin = startH * 60 + startM;
                    int endMin   = endH   * 60 + endM;

                    for (String day : dowStr.split("・")) {
                        day = day.trim();
                        if (day.isBlank()) continue;
                        for (int h = HOUR_START; h < HOUR_END; h++) {
                            if (startMin < (h + 1) * 60 && endMin > h * 60) {
                                String key = day + "_" + h;
                                bookedMap.computeIfAbsent(key, k -> new ArrayList<>())
                                         .add(custName[0]);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            model.addAttribute("bookedMap", bookedMap);
        }

        return "person-schedule";
    }

    private int parseIntSafe(String s) {
        try { return s == null || s.isBlank() ? -1 : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private List<Integer> buildHourList() {
        List<Integer> list = new ArrayList<>();
        for (int h = HOUR_START; h < HOUR_END; h++) list.add(h);
        return list;
    }
}
