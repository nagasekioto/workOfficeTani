package jp.co.housekeeping.person_management.model;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 売上詳細エンティティ（最大5件の勤務先）
 */
@Table("sales_details")
public class SalesDetail {
    
    @Id
    private Long id;
    
    private Long salesId;
    private Long customerId;
    private Integer hourlyWage;
    private BigDecimal workingHours;
    private Integer monthlyTotal;
    private Integer commission;
    private Integer tax;
    private Integer detailOrder;
    
    // コンストラクタ
    public SalesDetail() {
    }
    
    // 自動計算メソッド
    public void calculateAmounts() {
        if (hourlyWage != null && workingHours != null) {
            this.monthlyTotal = hourlyWage * workingHours.intValue();
            this.commission = (int) (monthlyTotal * 0.15);
            this.tax = (int) (commission * 0.10);
        }
    }
    
    // Getter / Setter
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getSalesId() {
        return salesId;
    }
    
    public void setSalesId(Long salesId) {
        this.salesId = salesId;
    }
    
    public Long getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }
    
    public Integer getHourlyWage() {
        return hourlyWage;
    }
    
    public void setHourlyWage(Integer hourlyWage) {
        this.hourlyWage = hourlyWage;
    }
    
    public BigDecimal getWorkingHours() {
        return workingHours;
    }
    
    public void setWorkingHours(BigDecimal workingHours) {
        this.workingHours = workingHours;
    }
    
    public Integer getMonthlyTotal() {
        return monthlyTotal;
    }
    
    public void setMonthlyTotal(Integer monthlyTotal) {
        this.monthlyTotal = monthlyTotal;
    }
    
    public Integer getCommission() {
        return commission;
    }
    
    public void setCommission(Integer commission) {
        this.commission = commission;
    }
    
    public Integer getTax() {
        return tax;
    }
    
    public void setTax(Integer tax) {
        this.tax = tax;
    }
    
    public Integer getDetailOrder() {
        return detailOrder;
    }
    
    public void setDetailOrder(Integer detailOrder) {
        this.detailOrder = detailOrder;
    }
}