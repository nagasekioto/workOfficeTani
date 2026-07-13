package jp.co.housekeeping.person_management.controller;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import jp.co.housekeeping.person_management.model.Customer;
import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.model.Sales;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.CustomerRequestRepository;
import jp.co.housekeeping.person_management.repository.IntroductionRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.ReceiptsIssuedRepository;
import jp.co.housekeeping.person_management.repository.RegisterRecordRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

// ─── 1-7-6 完全削除（売上記録も含む） ─────────────────────────
// 通常の「完全に削除」ボタンは、売上・紹介状などの記録が残っている
// 求職者・求人者を外部キー制約により削除できないようにブロックしている
// （意図的な安全装置）。このページは、その安全装置を理解した上で、
// 過去の売上記録ごと完全に消し去りたい場合の管理者向け機能。
// 一度実行すると元に戻せないため、退職・取引終了済みの人物のみを対象とする。
@Controller
public class PermanentDeleteController {

    @Autowired private PersonRepository personRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesDetailRepository salesDetailRepository;
    @Autowired private IntroductionRepository introductionRepository;
    @Autowired private RegisterRecordRepository registerRecordRepository;
    @Autowired private ReceiptsIssuedRepository receiptsIssuedRepository;
    @Autowired private CustomerRequestRepository customerRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping("/permanent-delete")
    public String index(HttpSession session, Model model) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        List<PersonRow> personRows = new ArrayList<>();
        for (Person p : personRepository.findAll()) {
            if (p.getRetiredAt() == null) continue; // 退職済みのみ対象
            int salesCount = 0;
            for (Sales s : salesRepository.findByPersonId(p.getId())) {
                salesCount += salesDetailRepository.findBySalesId(s.getId()).size();
            }
            PersonRow row = new PersonRow();
            row.id = p.getId();
            row.no = p.getNo();
            row.name = p.getLastNameKanji() + " " + p.getFirstNameKanji();
            row.retiredAt = p.getRetiredAt() != null ? p.getRetiredAt().toString() : "";
            row.salesCount = salesCount;
            row.introCount = introductionRepository.findByPersonId(p.getId()).size();
            personRows.add(row);
        }

        List<CustomerRow> customerRows = new ArrayList<>();
        for (Customer c : customerRepository.findAll()) {
            if (c.getRetiredAt() == null) continue; // 取引終了済みのみ対象
            int detailCount = 0;
            for (Sales s : salesRepository.findAll()) {
                detailCount += (int) salesDetailRepository.findBySalesId(s.getId()).stream()
                        .filter(d -> c.getId().equals(d.getCustomerId()))
                        .count();
            }
            CustomerRow row = new CustomerRow();
            row.id = c.getId();
            row.no = c.getNo();
            row.name = c.getLastNameKanji() + " " + c.getFirstNameKanji();
            row.retiredAt = c.getRetiredAt() != null ? c.getRetiredAt().toString() : "";
            row.salesDetailCount = detailCount;
            row.introCount = introductionRepository.findByCustomerId(c.getId()).size();
            customerRows.add(row);
        }

        model.addAttribute("personRows", personRows);
        model.addAttribute("customerRows", customerRows);
        return "permanent-delete";
    }

    // ─── 求職者の完全削除（売上記録も含む） ─────────────────────
    @PostMapping("/permanent-delete/person/{id}")
    @Transactional
    public String deletePersonPermanently(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        Person p = personRepository.findById(id).orElse(null);
        // 安全装置：退職処理済みの求職者のみ対象（在職中の人物を誤って完全削除するのを防ぐ）
        if (p == null || p.getRetiredAt() == null) {
            return "redirect:/permanent-delete?notRetired";
        }

        // 売上明細 → 売上 の順に削除（sales_details.sales_id が sales.id を参照しているため）
        for (Sales s : salesRepository.findByPersonId(id)) {
            salesDetailRepository.deleteAll(salesDetailRepository.findBySalesId(s.getId()));
            salesRepository.deleteById(s.getId());
        }

        introductionRepository.deleteAll(introductionRepository.findByPersonId(id));
        registerRecordRepository.deleteAll(registerRecordRepository.findByPersonId(id));
        receiptsIssuedRepository.deleteAll(receiptsIssuedRepository.findByPersonId(id));

        // 会費振込確認（membership_confirmations）は専用エンティティが無くJdbcTemplateで直接管理されているため同様に削除
        jdbcTemplate.update("DELETE FROM membership_confirmations WHERE person_id = ?", id);

        // 求人受付表(customer_requests)の「候補者」欄がこの人物を指している場合はリンクだけ解除
        // （customer_requests自体は求人者側の記録なので削除しない）
        jdbcTemplate.update("UPDATE customer_requests SET candidate_person_id = NULL WHERE candidate_person_id = ?", id);

        personRepository.deleteById(id);
        return "redirect:/permanent-delete?deletedPerson";
    }

    // ─── 求人者の完全削除（売上記録も含む） ─────────────────────
    @PostMapping("/permanent-delete/customer/{id}")
    @Transactional
    public String deleteCustomerPermanently(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("authenticated") == null) return "redirect:/login";

        Customer c = customerRepository.findById(id).orElse(null);
        // 安全装置：取引終了処理済みの求人者のみ対象
        if (c == null || c.getRetiredAt() == null) {
            return "redirect:/permanent-delete?notRetired";
        }

        // この求人者向けの売上明細のみ削除（sales本体は求職者側の記録なので残す）
        for (Sales s : salesRepository.findAll()) {
            for (var d : salesDetailRepository.findBySalesId(s.getId())) {
                if (id.equals(d.getCustomerId())) {
                    salesDetailRepository.deleteById(d.getId());
                }
            }
        }

        introductionRepository.deleteAll(introductionRepository.findByCustomerId(id));
        receiptsIssuedRepository.deleteAll(receiptsIssuedRepository.findByCustomerId(id));
        customerRequestRepository.deleteAll(customerRequestRepository.findByCustomerId(id));

        // customer_ledgers（求人管理簿）にもcustomer_idの列があるため念のため削除
        jdbcTemplate.update("DELETE FROM customer_ledgers WHERE customer_id = ?", id);

        // 求職者側の「出向先」欄がこの求人者を指している場合はリンクだけ解除
        jdbcTemplate.update("UPDATE persons SET dispatch_customer_id = NULL WHERE dispatch_customer_id = ?", id);

        customerRepository.deleteById(id);
        return "redirect:/permanent-delete?deletedCustomer";
    }

    public static class PersonRow {
        public Long id;
        public Integer no;
        public String name;
        public String retiredAt;
        public int salesCount;
        public int introCount;

        public Long getId() { return id; }
        public Integer getNo() { return no; }
        public String getName() { return name; }
        public String getRetiredAt() { return retiredAt; }
        public int getSalesCount() { return salesCount; }
        public int getIntroCount() { return introCount; }
    }

    public static class CustomerRow {
        public Long id;
        public Integer no;
        public String name;
        public String retiredAt;
        public int salesDetailCount;
        public int introCount;

        public Long getId() { return id; }
        public Integer getNo() { return no; }
        public String getName() { return name; }
        public String getRetiredAt() { return retiredAt; }
        public int getSalesDetailCount() { return salesDetailCount; }
        public int getIntroCount() { return introCount; }
    }
}
