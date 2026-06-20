package jp.co.housekeeping.person_management.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("receipts_issued")
public class ReceiptsIssued {

    @Id
    private Long id;

    private Long customerId;       // 求人者ID（1-7-1）or null
    private Long personId;         // 求職者ID（1-7-2）or null
    private Long salesDetailId;
    private String receiptType;    // "CUSTOMER"=1-7-1, "JOBSEEKER"=1-7-2
    private Integer amount;
    private Integer receiptNumber; // 自動採番の領収番号
    private Boolean printed;
    private LocalDateTime printedAt;
    private LocalDateTime createdAt;

    public ReceiptsIssued() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }
    public Long getSalesDetailId() { return salesDetailId; }
    public void setSalesDetailId(Long salesDetailId) { this.salesDetailId = salesDetailId; }
    public String getReceiptType() { return receiptType; }
    public void setReceiptType(String receiptType) { this.receiptType = receiptType; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public Integer getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(Integer receiptNumber) { this.receiptNumber = receiptNumber; }
    public Boolean getPrinted() { return printed; }
    public void setPrinted(Boolean printed) { this.printed = printed; }
    public LocalDateTime getPrintedAt() { return printedAt; }
    public void setPrintedAt(LocalDateTime printedAt) { this.printedAt = printedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
