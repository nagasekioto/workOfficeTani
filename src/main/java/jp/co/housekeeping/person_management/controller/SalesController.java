package jp.co.housekeeping.person_management.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.model.SalesDetail;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;
import jp.co.housekeeping.person_management.util.ValidationUtils;

@Controller
public class SalesController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;

    // ─── 売上入力画面 ───────────────────────────────
    @GetMapping("/person/sales")
    public String sales(@RequestParam(required = false) Long personId,
                        HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        model.addAttribute("persons", personRepository.findAll());
        model.addAttribute("customers", customerRepository.findAll());

        if (personId != null) {
            Person person = personRepository.findById(personId).orElse(null);
            model.addAttribute("selectedPerson", person);
            model.addAttribute("selectedPersonId", personId);

            // 既存の売上明細を取得してフォームに復元できるようにする
            List<SalesDetail> existingDetails = new ArrayList<>();
            List<Sales> existingSales = salesRepository.findByPersonId(personId);
            if (!existingSales.isEmpty()) {
                existingDetails = salesDetailRepository.findBySalesId(existingSales.get(0).getId());
            }
            model.addAttribute("existingDetails", existingDetails);
        }

        return "person-sales";
    }

    @PostMapping("/person/sales/save")
    public String saveSales(
            @RequestParam Long personId,
            @RequestParam(required = false) String[] customerIds,
            @RequestParam(required = false) String[] detailIds,
            @RequestParam(required = false) String[] introductionDates,
            @RequestParam(required = false) String[] receptionFees,
            @RequestParam(required = false) String[] customerFees,
            @RequestParam(required = false) String[] hourlyWages,
            @RequestParam(required = false) String[] hourlyWageOvertimes,
            @RequestParam(required = false) String[] dailyWagesList,
            @RequestParam(required = false) String[] dailyWageRates,
            @RequestParam(required = false) String[] workStartDates,
            @RequestParam(required = false) String[] workEndDates,
            @RequestParam(required = false) String[] workDaysList,
            @RequestParam(required = false) String[] workingHoursList,
            @RequestParam(required = false) String[] remarksList,
            HttpSession session) {

        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        // 既存salesレコードを取得 or 新規作成
        List<Sales> existing = salesRepository.findByPersonId(personId);
        Sales sales;
        if (existing.isEmpty()) {
            sales = new Sales();
            sales.setPersonId(personId);
            sales = salesRepository.save(sales);
        } else {
            sales = existing.get(0);
        }

        // 既存詳細を取得（receiptNo/issuedAt保持のため削除せず上書き）
        List<SalesDetail> oldDetails = salesDetailRepository.findBySalesId(sales.getId());

        if (customerIds == null) {
            // 行が全削除された場合：receiptNo発行済みのものは保持、未発行のみ削除
            for (SalesDetail od : oldDetails) {
                if (od.getReceiptNo() == null || od.getReceiptNo().isEmpty()) {
                    salesDetailRepository.deleteById(od.getId());
                }
            }
            return "redirect:/person/sales?saved=" + personId;
        }

        // 新しい行セット（customerId×detailOrder）をキーにマップ化
        java.util.Map<String, SalesDetail> oldMap = new java.util.LinkedHashMap<>();
        for (SalesDetail od : oldDetails) {
            String key = (od.getCustomerId() != null ? od.getCustomerId() : "") + "_" + od.getDetailOrder();
            oldMap.put(key, od);
        }
        java.util.Set<Long> newDetailIds = new java.util.HashSet<>();

        for (int i = 0; i < customerIds.length; i++) {
            // 求人者未選択はスキップ
            if (customerIds[i] == null || customerIds[i].isBlank()) continue;
            Long customerId;
            try { customerId = Long.parseLong(customerIds[i]); } catch (NumberFormatException e) { continue; }

            // 同じ画面ブロックに対応する既存レコードを取得
            // 1) detailIds（画面の各ブロックが保持する実IDのhidden値）を最優先で使う
            //    customerId×表示順だけに頼ると、過去の並び替えやdetailOrder未設定の
            //    旧データで一致せず、同じ稼働履歴が重複作成される事象が起きていたため。
            SalesDetail detail = null;
            if (detailIds != null && i < detailIds.length
                    && detailIds[i] != null && !detailIds[i].isBlank()) {
                try {
                    Long did = Long.parseLong(detailIds[i]);
                    for (SalesDetail od : oldDetails) {
                        if (did.equals(od.getId())) { detail = od; break; }
                    }
                } catch (NumberFormatException ignored) {}
            }
            // 2) IDで見つからない場合は customerId×表示順 で再利用を試みる（後方互換）
            if (detail == null) {
                String key = customerId + "_" + (i + 1);
                detail = oldMap.get(key);
            }
            if (detail == null) detail = new SalesDetail();
            detail.setSalesId(sales.getId());
            detail.setCustomerId(customerId);
            detail.setDetailOrder(i + 1);

            if (introductionDates != null && i < introductionDates.length
                    && introductionDates[i] != null && !introductionDates[i].isBlank()) {
                try { detail.setIntroductionDate(LocalDate.parse(introductionDates[i])); } catch (Exception ignored) {}
            }
            // 求職受付手数料（710円）チェックボックス：チェックを外した場合は明示的にクリアする
            if (receptionFees != null && i < receptionFees.length) {
                if (receptionFees[i] != null && !receptionFees[i].isBlank()) {
                    detail.setReceptionFee(ValidationUtils.parseNonNegativeInt(receptionFees[i]));
                } else {
                    detail.setReceptionFee(null);
                }
            }
            // 求人受付手数料（1,000円）チェックボックス：チェックを外した場合は明示的にクリアする
            if (customerFees != null && i < customerFees.length) {
                if (customerFees[i] != null && !customerFees[i].isBlank()) {
                    detail.setCustomerFee(ValidationUtils.parseNonNegativeInt(customerFees[i]));
                } else {
                    detail.setCustomerFee(null);
                }
            }
            if (hourlyWages != null && i < hourlyWages.length
                    && hourlyWages[i] != null && !hourlyWages[i].isBlank()) {
                Integer v = ValidationUtils.parseNonNegativeInt(hourlyWages[i]);
                if (v != null) detail.setHourlyWage(v);
            }
            if (hourlyWageOvertimes != null && i < hourlyWageOvertimes.length
                    && hourlyWageOvertimes[i] != null && !hourlyWageOvertimes[i].isBlank()) {
                Integer v = ValidationUtils.parseNonNegativeInt(hourlyWageOvertimes[i]);
                if (v != null) detail.setHourlyWageOvertime(v);
            }
            if (dailyWagesList != null && i < dailyWagesList.length && dailyWagesList[i] != null) {
                detail.setDailyWages(ValidationUtils.sanitizeNonNegativeIntList(dailyWagesList[i]));
            }
            // 日給への掛け率（%）。未入力時は16.5をデフォルトとして保存する。負数は無効として無視する。
            Double rate = 16.5;
            if (dailyWageRates != null && i < dailyWageRates.length
                    && dailyWageRates[i] != null && !dailyWageRates[i].isBlank()) {
                Double v = ValidationUtils.parseNonNegativeDouble(dailyWageRates[i]);
                if (v != null) rate = v;
            }
            detail.setDailyWageRate(rate);
            if (workStartDates != null && i < workStartDates.length
                    && workStartDates[i] != null && !workStartDates[i].isBlank()) {
                try { detail.setWorkStartDate(LocalDate.parse(workStartDates[i])); } catch (Exception ignored) {}
            }
            if (workEndDates != null && i < workEndDates.length
                    && workEndDates[i] != null && !workEndDates[i].isBlank()) {
                try { detail.setWorkEndDate(LocalDate.parse(workEndDates[i])); } catch (Exception ignored) {}
            }
            // 就労日数から開始・終了日を補完（hiddenが空でも日数だけで計算）
            if (detail.getWorkStartDate() != null && detail.getWorkEndDate() == null
                    && workDaysList != null && i < workDaysList.length
                    && workDaysList[i] != null && !workDaysList[i].isBlank()) {
                Integer days = ValidationUtils.parseNonNegativeInt(workDaysList[i]);
                if (days != null && days > 0) detail.setWorkEndDate(detail.getWorkStartDate().plusDays(days - 1));
            }
            if (workingHoursList != null && i < workingHoursList.length
                    && workingHoursList[i] != null && !workingHoursList[i].isBlank()) {
                java.math.BigDecimal v = ValidationUtils.parseNonNegativeBigDecimal(workingHoursList[i]);
                if (v != null) detail.setWorkingHours(v);
            }
            if (remarksList != null && i < remarksList.length && remarksList[i] != null) {
                detail.setRemarks(remarksList[i]);
            }

            detail.calculateAmounts();
            detail.calculateSalesAmount();
            SalesDetail saved = salesDetailRepository.save(detail);
            if (saved.getId() != null) newDetailIds.add(saved.getId());
        }

        // 今回の保存に含まれなかった旧レコードは削除しない（保持する）
        // ── 求人者を選択しなかったスロットの既存データはそのまま残す。
        //    ユーザーが「勤務先1を空欄のまま勤務先2だけ入力して保存」したとき、
        //    既存の勤務先1データが消えないようにするための仕様変更。
        // ── 完全に削除したい場合は稼働管理簿（1-1-5）の「削除」ボタンを使う。

        return "redirect:/person/sales?saved=" + personId;
    }

    // 領収書No採番（0001～）
    private String generateReceiptNo() {
        int maxNo = salesDetailRepository.findMaxReceiptNo();
        return String.format("%04d", maxNo + 1);
    }

}
