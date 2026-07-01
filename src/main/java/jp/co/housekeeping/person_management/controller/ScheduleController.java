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
import org.springframework.web.bind.annotation.ResponseBody;

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

    private static final int HOUR_START = 6;   // 6:00スタート
    private static final int HOUR_END   = 6;   // 翌6:00で1周
    private static final String[] DAYS  = {"月", "火", "水", "木", "金", "土", "日"};

    // ─── 個人スケジュール画面 ──────────────────────────────
    @GetMapping("/person/schedule")
    public String schedule(@RequestParam(required = false) Long personId,
                           HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("hours", buildHourList());
        model.addAttribute("slots", buildSlotList());
        model.addAttribute("days", java.util.Arrays.asList(DAYS));

        if (personId != null) {
            Person person = personRepository.findById(personId).orElse(null);
            model.addAttribute("selectedPerson", person);
            model.addAttribute("selectedPersonId", personId);
            model.addAttribute("bookedMap", buildBookedMap(personId));
        }

        return "person-schedule";
    }

    // ─── 空き検索 API ───────────────────────────────────────
    // 指定曜日・時間帯（分単位）で「空いている求職者」の一覧をJSONで返す
    @GetMapping("/person/schedule/search")
    @ResponseBody
    public List<Map<String, String>> searchAvailable(
            @RequestParam String day,
            @RequestParam int slotStart,   // 例: 9:30 → 570
            @RequestParam int slotEnd,     // 例: 11:00 → 660
            HttpSession session) {
        if (session.getAttribute("authenticated") == null) return new ArrayList<>();

        List<Map<String, String>> result = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        // 全求職者について、指定曜日×時間帯に既存の勤務と重なっているか判定
        for (Person p : personRepository.findAll()) {
            boolean booked = false;
            for (Introduction intro : introductionRepository.findAll()) {
                if (!p.getId().equals(intro.getPersonId())) continue;
                if (intro.getFormData() == null || intro.getFormData().isBlank()) continue;
                try {
                    JsonNode fd = mapper.readTree(intro.getFormData());
                    String dowStr = fd.path("dow").asText("");
                    int startH = parseIntSafe(fd.path("wSH").asText(""));
                    int startM = parseIntSafe(fd.path("wSM").asText(""));
                    int endH   = parseIntSafe(fd.path("wEH").asText(""));
                    int endM   = parseIntSafe(fd.path("wEM").asText(""));
                    if (dowStr.isBlank() || startH < 0 || endH < 0) continue;
                    for (String d : dowStr.split("・")) {
                        if (!d.trim().equals(day)) continue;
                        int wStart = startH * 60 + startM;
                        int wEnd   = endH   * 60 + endM;
                        if (wStart < slotEnd && wEnd > slotStart) {
                            booked = true;
                            break;
                        }
                    }
                    if (booked) break;
                } catch (Exception ignored) {}
            }
            if (!booked) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("id",          String.valueOf(p.getId()));
                m.put("name",        p.getLastNameKanji() + " " + p.getFirstNameKanji());
                m.put("kana",        p.getLastNameKana() + " " + p.getFirstNameKana());
                m.put("careHelper",  Boolean.TRUE.equals(p.getQualCareHelper()) ? "1" : "0");
                m.put("cookingGood", "好き".equals(p.getCooking()) ? "1" : "0");
                result.add(m);
            }
        }
        return result;
    }

    // ─── 共通：勤務中マップ構築 ─────────────────────────────
    // key = "月_7"  → 値 = 求人者名のリスト
    public Map<String, List<String>> buildBookedMap(Long personId) {
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
                    // 勤務時間と重なるすべての時間スロット（6:00始まり24時間サイクル）に登録
                    int h = HOUR_START;
                    do {
                        int slotStart = h * 60;
                        int slotEnd   = (h + 1) * 60;
                        if (startMin < slotEnd && endMin > slotStart) {
                            String key = day + "_" + h;
                            bookedMap.computeIfAbsent(key, k -> new ArrayList<>())
                                     .add(custName[0]);
                        }
                        h = nextHour(h);
                    } while (h != HOUR_START);
                }
            } catch (Exception ignored) {}
        }
        return bookedMap;
    }

    private int parseIntSafe(String s) {
        try { return s == null || s.isBlank() ? -1 : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    // 6:00 → 7 → ... → 23 → 0 → 1 → ... → 5 の24時間サイクル
    private static int nextHour(int h) { return (h + 1) % 24; }

    private List<Integer> buildHourList() {
        List<Integer> list = new ArrayList<>();
        int h = HOUR_START;
        do {
            list.add(h);
            h = nextHour(h);
        } while (h != HOUR_START);
        return list;
    }

    private List<SlotOption> buildSlotList() {
        List<SlotOption> list = new ArrayList<>();
        int m = HOUR_START * 60;
        int total = 0;
        while (total <= 24 * 60) {
            int h = (m / 60) % 24, mm = m % 60;
            list.add(new SlotOption(m % (24 * 60), h + ":" + (mm == 0 ? "00" : mm)));
            m += 30; total += 30;
        }
        return list;
    }

    public static class SlotOption {
        public final int minutes;
        public final String label;
        public SlotOption(int minutes, String label) { this.minutes = minutes; this.label = label; }
        public int getMinutes() { return minutes; }
        public String getLabel() { return label; }
    }
}
