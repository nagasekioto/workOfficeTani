package jp.co.housekeeping.person_management.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 領収書発行記録テーブル (receipts_issued)
 *
 * 既存カラム: id, customer_id, sales_detail_id, receipt_type, amount, printed, printed_at, created_at
 * 追加カラム: receipt_number, person_id  ← schema-all.sql 適用要（旧schema-update-5.sql）
 *
 * DBマイグレーション未適用の場合は receipt_number / person_id を @Transient 相当として
 * アプリ側でのみ使用し、DBには id を採番番号として利用する。
 */
@Table("receipts_issued")
public class ReceiptsIssued {

    @Id
    private Long id;

    private Long customerId;       // 求人者ID（1-7-1）
    private Long salesDetailId;
    private String receiptType;    // "CUSTOMER"=1-7-1, "JOBSEEKER"=1-7-2
    private Integer amount;
    private Boolean printed;
    private LocalDateTime printedAt;
    private LocalDateTime createdAt;

    // ↓ これらは schema-all.sql 適用後に有効
    // Spring Data JDBC は未知カラムがあるとエラーになるため
    // マイグレーション適用後にコメントを外してください
    // private Integer receiptNumber;
    // private Long personId;

    public ReceiptsIssued() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getSalesDetailId() { return salesDetailId; }
    public void setSalesDetailId(Long salesDetailId) { this.salesDetailId = salesDetailId; }
    public String getReceiptType() { return receiptType; }
    public void setReceiptType(String receiptType) { this.receiptType = receiptType; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public Boolean getPrinted() { return printed; }
    public void setPrinted(Boolean printed) { this.printed = printed; }
    public LocalDateTime getPrintedAt() { return printedAt; }
    public void setPrintedAt(LocalDateTime printedAt) { this.printedAt = printedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** 採番済み領収番号: DBマイグレーション前は id を代用 */
    public int getReceiptNumberEffective() {
        return id != null ? id.intValue() : 0;
    }
}
