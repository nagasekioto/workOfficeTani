package jp.co.housekeeping.person_management.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("register_records")
public class RegisterRecord {

    @Id
    private Long id;

    private Long personId;
    private String workMonth;   // "2025-01" format
    private Integer salary;
    private Integer fee;        // salary * 0.165
    private Integer membershipFee; // 会費(1550/350)。振込金入力時に会費として徴収した額
    private Boolean transferred;   // 振込済みかどうか
    private String memo;
    private LocalDateTime createdAt;

    public RegisterRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }
    public String getWorkMonth() { return workMonth; }
    public void setWorkMonth(String workMonth) { this.workMonth = workMonth; }
    public Integer getSalary() { return salary; }
    public void setSalary(Integer salary) { this.salary = salary; }
    public Integer getFee() { return fee; }
    public void setFee(Integer fee) { this.fee = fee; }
    public Integer getMembershipFee() { return membershipFee; }
    public void setMembershipFee(Integer membershipFee) { this.membershipFee = membershipFee; }
    public Boolean getTransferred() { return transferred; }
    public void setTransferred(Boolean transferred) { this.transferred = transferred; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
