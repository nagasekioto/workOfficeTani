package jp.co.housekeeping.person_management.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("introductions")
public class Introduction {
    @Id private Long id;
    private String refNo;
    private Long personId;
    private Long customerId;
    private LocalDate introDate;
    private LocalDate startDate;
    private String formData;
    private LocalDateTime createdAt;

    // 求人管理簿（1-2-2）用の入力項目
    private String empStatus;     // 雇用期間（状況）
    private String hireResult;    // 採否
    private String ledgerRemarks; // 備考
    private String laborContract; // 労働契約（有期／無期）

    // 求職管理簿（1-1-4）用の入力項目
    private String rishokuStatus; // 離職状況（6カ月以内または不明）
    private String henreikin;     // 返戻金

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LocalDate getIntroDate() { return introDate; }
    public void setIntroDate(LocalDate introDate) { this.introDate = introDate; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public String getFormData() { return formData; }
    public void setFormData(String formData) { this.formData = formData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getEmpStatus() { return empStatus; }
    public void setEmpStatus(String empStatus) { this.empStatus = empStatus; }
    public String getHireResult() { return hireResult; }
    public void setHireResult(String hireResult) { this.hireResult = hireResult; }
    public String getLedgerRemarks() { return ledgerRemarks; }
    public void setLedgerRemarks(String ledgerRemarks) { this.ledgerRemarks = ledgerRemarks; }
    public String getLaborContract() { return laborContract; }
    public void setLaborContract(String laborContract) { this.laborContract = laborContract; }
    public String getRishokuStatus() { return rishokuStatus; }
    public void setRishokuStatus(String rishokuStatus) { this.rishokuStatus = rishokuStatus; }
    public String getHenreikin() { return henreikin; }
    public void setHenreikin(String henreikin) { this.henreikin = henreikin; }
}
