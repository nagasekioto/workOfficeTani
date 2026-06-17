package jp.co.housekeeping.person_management.model;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 求人者（雇用主）エンティティ
 */
@Table("customers")
public class Customer {
    
    @Id
    private Long id;
    
    private Integer no;
    
    // 姓名（カタカナ）
    private String lastNameKana;
    private String firstNameKana;
    
    // 姓名（漢字）
    private String lastNameKanji;
    private String firstNameKanji;
    
    // 住所情報
    private String postalCode;
    private String address1;
    private String address2;
    private String address3;
    
    // 最寄り駅
    private String nearestLine;
    private String nearestStation;
    
    // 連絡先
    private String homePhone;
    private String faxPhone;
    private String mobilePhone;
    
    private LocalDate birthDate;
    private LocalDate registeredDate;
    private String notes;
    private String accessTime;  // 駅からの所要時間（schema-update-3.sqlで追加）
    
    // コンストラクタ
    public Customer() {
    }
    
    // Getter / Setter
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Integer getNo() {
        return no;
    }
    
    public void setNo(Integer no) {
        this.no = no;
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
    
    public String getNearestLine() {
        return nearestLine;
    }
    
    public void setNearestLine(String nearestLine) {
        this.nearestLine = nearestLine;
    }
    
    public String getNearestStation() {
        return nearestStation;
    }
    
    public void setNearestStation(String nearestStation) {
        this.nearestStation = nearestStation;
    }
    
    public String getHomePhone() {
        return homePhone;
    }
    
    public void setHomePhone(String homePhone) {
        this.homePhone = homePhone;
    }
    
    public String getFaxPhone() {
        return faxPhone;
    }
    
    public void setFaxPhone(String faxPhone) {
        this.faxPhone = faxPhone;
    }
    
    public String getMobilePhone() {
        return mobilePhone;
    }
    
    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }
    
    public LocalDate getBirthDate() {
        return birthDate;
    }
    
    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
    
    public LocalDate getRegisteredDate() {
        return registeredDate;
    }
    
    public void setRegisteredDate(LocalDate registeredDate) {
        this.registeredDate = registeredDate;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public String getAccessTime() { return accessTime; }
    public void setAccessTime(String accessTime) { this.accessTime = accessTime; }
}