package jp.co.housekeeping.person_management.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 売上エンティティ
 */
@Table("sales")
public class Sales {
    
    @Id
    private Long id;
    
    private Long personId;
    private LocalDate introductionDate;
    private Integer receptionFee;
    private String receiptNo;
    private LocalDateTime createdAt;
    
    // コンストラクタ
    public Sales() {
    }
    
    // Getter / Setter
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getPersonId() {
        return personId;
    }
    
    public void setPersonId(Long personId) {
        this.personId = personId;
    }
    
    public LocalDate getIntroductionDate() {
        return introductionDate;
    }
    
    public void setIntroductionDate(LocalDate introductionDate) {
        this.introductionDate = introductionDate;
    }
    
    public Integer getReceptionFee() {
        return receptionFee;
    }
    
    public void setReceptionFee(Integer receptionFee) {
        this.receptionFee = receptionFee;
    }
    
    public String getReceiptNo() {
        return receiptNo;
    }
    
    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}