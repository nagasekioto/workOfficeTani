package jp.co.housekeeping.person_management.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("sales_details")
public class SalesDetail {

    @Id
    private Long id;

    private Long salesId;
    private Long customerId;
    private Integer hourlyWage;          // 時給（日給）
    private Integer hourlyWageOvertime;  // 時給（残業）
    private BigDecimal workingHours;
    private Integer monthlyTotal;
    private Integer commission;
    private Integer tax;
    private Integer detailOrder;

    // 就労月日
    private LocalDate workStartDate;
    private LocalDate workEndDate;

    // 受付料（710円固定、求職者がその求人者に初めて行く場合）
    private Integer receptionFee;

    // 求人受付手数料（1000円固定、求人者がうちの会社を初めて利用する場合）
    private Integer customerFee;

    // 日給（JSON文字列: ["8000","8500"] 最大5枠）
    private String dailyWages;

    // 紹介年月日
    private LocalDate introductionDate;

    // 領収書No
    private String receiptNo;

    // 備考
    private String remarks;

    // 領収書発行日時
    private java.time.LocalDateTime issuedAt;

    // 日雇1ヶ月（手入力）
    private Integer dailyWage1Month;

    public SalesDetail() {}

    public void calculateAmounts() {
        int total = 0;
        if (hourlyWage != null && workingHours != null) {
            total += (int)(hourlyWage * workingHours.doubleValue());
        }
        // 日給合計を加算
        if (dailyWages != null && !dailyWages.isBlank()) {
            String[] wages = dailyWages.split(",");
            for (String w : wages) {
                try { total += Integer.parseInt(w.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        this.monthlyTotal = total;
        this.commission = (int)(total * 0.165);
        this.tax = (int)(commission * 0.10);
    }

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSalesId() { return salesId; }
    public void setSalesId(Long salesId) { this.salesId = salesId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Integer getHourlyWage() { return hourlyWage; }
    public void setHourlyWage(Integer hourlyWage) { this.hourlyWage = hourlyWage; }
    public Integer getHourlyWageOvertime() { return hourlyWageOvertime; }
    public void setHourlyWageOvertime(Integer hourlyWageOvertime) { this.hourlyWageOvertime = hourlyWageOvertime; }
    public BigDecimal getWorkingHours() { return workingHours; }
    public void setWorkingHours(BigDecimal workingHours) { this.workingHours = workingHours; }
    public Integer getMonthlyTotal() { return monthlyTotal; }
    public void setMonthlyTotal(Integer monthlyTotal) { this.monthlyTotal = monthlyTotal; }
    public Integer getCommission() { return commission; }
    public void setCommission(Integer commission) { this.commission = commission; }
    public Integer getTax() { return tax; }
    public void setTax(Integer tax) { this.tax = tax; }
    public Integer getDetailOrder() { return detailOrder; }
    public void setDetailOrder(Integer detailOrder) { this.detailOrder = detailOrder; }
    public LocalDate getWorkStartDate() { return workStartDate; }
    public void setWorkStartDate(LocalDate workStartDate) { this.workStartDate = workStartDate; }
    public LocalDate getWorkEndDate() { return workEndDate; }
    public void setWorkEndDate(LocalDate workEndDate) { this.workEndDate = workEndDate; }
    public Integer getReceptionFee() { return receptionFee; }
    public void setReceptionFee(Integer receptionFee) { this.receptionFee = receptionFee; }
    public Integer getCustomerFee() { return customerFee; }
    public void setCustomerFee(Integer customerFee) { this.customerFee = customerFee; }
    public String getDailyWages() { return dailyWages; }
    public void setDailyWages(String dailyWages) { this.dailyWages = dailyWages; }
    public LocalDate getIntroductionDate() { return introductionDate; }
    public void setIntroductionDate(LocalDate introductionDate) { this.introductionDate = introductionDate; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public java.time.LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(java.time.LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public Integer getDailyWage1Month() { return dailyWage1Month; }
    public void setDailyWage1Month(Integer dailyWage1Month) { this.dailyWage1Month = dailyWage1Month; }
}
