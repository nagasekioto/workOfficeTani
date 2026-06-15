package jp.co.housekeeping.person_management.model;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("receipts")
public class Receipt {
    
    @Id
    private Long id;
    
    // 家政婦情報
    private String lastNameKana;
    private String firstNameKana;
    private String lastNameKanji;
    private String firstNameKanji;
    private String postalCode;
    private String address1;
    private String address2;
    private String address3;
    private LocalDate birthDate;
    
    // 勤務情報
    private String workPlace;      // 働き先
    private String workMonth;      // 働いていた月（例: 2026-01）
    private Integer hourlyWage;    // 時給
    private Double workingHours;   // 時間数
    
    // 計算項目
    private Integer totalAmount;   // 時給 × 時間数
    private Integer commission;    // 手数料（15%）
    private Integer tax;           // 消費税（10%）
    
    private LocalDate issuedDate;  // 発行日
    
    // コンストラクタ
    public Receipt() {
    }
    
    // Getter / Setter（全項目分）
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getLastNameKana() {
        return lastNameKana;
    }
    
    public void setLastNameKana(String lastNameKana) {
        this.lastNameKana = lastNameKana;
    }
    
    public String getFirstNameKana() {
        return firstNameKana;
    }
    
    public void setFirstNameKana(String firstNameKana) {
        this.firstNameKana = firstNameKana;
    }
    
    public String getLastNameKanji() {
        return lastNameKanji;
    }
    
    public void setLastNameKanji(String lastNameKanji) {
        this.lastNameKanji = lastNameKanji;
    }
    
    public String getFirstNameKanji() {
        return firstNameKanji;
    }
    
    public void setFirstNameKanji(String firstNameKanji) {
        this.firstNameKanji = firstNameKanji;
    }
    
    public String getPostalCode() {
        return postalCode;
    }
    
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    
    public String getAddress1() {
        return address1;
    }
    
    public void setAddress1(String address1) {
        this.address1 = address1;
    }
    
    public String getAddress2() {
        return address2;
    }
    
    public void setAddress2(String address2) {
        this.address2 = address2;
    }
    
    public String getAddress3() {
        return address3;
    }
    
    public void setAddress3(String address3) {
        this.address3 = address3;
    }
    
    public LocalDate getBirthDate() {
        return birthDate;
    }
    
    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
    
    public String getWorkPlace() {
        return workPlace;
    }
    
    public void setWorkPlace(String workPlace) {
        this.workPlace = workPlace;
    }
    
    public String getWorkMonth() {
        return workMonth;
    }
    
    public void setWorkMonth(String workMonth) {
        this.workMonth = workMonth;
    }
    
    public Integer getHourlyWage() {
        return hourlyWage;
    }
    
    public void setHourlyWage(Integer hourlyWage) {
        this.hourlyWage = hourlyWage;
    }
    
    public Double getWorkingHours() {
        return workingHours;
    }
    
    public void setWorkingHours(Double workingHours) {
        this.workingHours = workingHours;
    }
    
    public Integer getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
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
    
    public LocalDate getIssuedDate() {
        return issuedDate;
    }
    
    public void setIssuedDate(LocalDate issuedDate) {
        this.issuedDate = issuedDate;
    }
    
    // 自動計算メソッド
    public void calculateAmounts() {
        if (hourlyWage != null && workingHours != null) {
            this.totalAmount = (int) (hourlyWage * workingHours);
            this.commission = (int) (totalAmount * 0.15);
            this.tax = (int) (commission * 0.10);
        }
    }
}