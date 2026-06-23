package jp.co.housekeeping.person_management.model;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("persons")
public class Person {

    @Id
    private Long id;
    private Integer no;
    private String lastNameKana;
    private String firstNameKana;
    private String lastNameKanji;
    private String firstNameKanji;
    private String postalCode;
    private String address1;
    private String address2;
    private String address3;
    private String nearestLine;
    private String nearestStation;
    private String homePhone;
    private String faxPhone;
    private String mobilePhone;
    private String desiredJob;
    private String desiredType;      // 後方互換用（旧単一値）
    private String desiredTypes;     // 新：複数選択（カンマ区切り）
    private String introducer;
    private Boolean qualNursery;
    private Boolean qualCook;
    private Boolean qualCareWorker;
    private Boolean qualCareHelper;  // 介護ヘルパー資格
    private Boolean animalDogOk;
    private Boolean animalCatOk;
    private Boolean animalDogAllergy;
    private Boolean animalCatAllergy;
    private String cooking;
    private String smoking;
    private String childcareExp;
    private LocalDate birthDate;
    private LocalDate registeredDate;
    private Boolean lineWorks;

    // ─── 就職希望条件（新規追加） ───────────────────────
    /** 就労場所（カンマ区切り: 家庭,病院） */
    private String workLocation;

    /** 職務内容（カンマ区切り: 付添,家事,家庭介護） */
    private String workDuties;

    /** 特定日と希望時間（JSON文字列: {"月":"09:00-17:00",...}） */
    private String specificDays;

    /** 就業可能時間（例: "09:00-18:00"） */
    private String workAvailableHours;

    /** 労働開始時期（文字列） */
    private String workStartPeriod;

    // ─── 緊急連絡先 ─────────────────────────────────
    /** 緊急連絡先：続柄 */
    private String emergencyRelation;

    /** 緊急連絡先：電話番号 */
    private String emergencyPhone;

    // ─── ベビーシッター ──────────────────────────────
    /** ベビーシッター経験 (あり/なし) */
    private String babysitterExp;

    /** ベビーシッター可否 (できる/できない) */
    private String babysitterAvail;

    /** 備考 */
    private String notes;

    public Person() {}

    // ─── getters / setters ──────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getNo() { return no; }
    public void setNo(Integer no) { this.no = no; }
    public String getLastNameKana() { return lastNameKana; }
    public void setLastNameKana(String lastNameKana) { this.lastNameKana = lastNameKana; }
    public String getFirstNameKana() { return firstNameKana; }
    public void setFirstNameKana(String firstNameKana) { this.firstNameKana = firstNameKana; }
    public String getLastNameKanji() { return lastNameKanji; }
    public void setLastNameKanji(String lastNameKanji) { this.lastNameKanji = lastNameKanji; }
    public String getFirstNameKanji() { return firstNameKanji; }
    public void setFirstNameKanji(String firstNameKanji) { this.firstNameKanji = firstNameKanji; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getAddress1() { return address1; }
    public void setAddress1(String address1) { this.address1 = address1; }
    public String getAddress2() { return address2; }
    public void setAddress2(String address2) { this.address2 = address2; }
    public String getAddress3() { return address3; }
    public void setAddress3(String address3) { this.address3 = address3; }
    public String getNearestLine() { return nearestLine; }
    public void setNearestLine(String nearestLine) { this.nearestLine = nearestLine; }
    public String getNearestStation() { return nearestStation; }
    public void setNearestStation(String nearestStation) { this.nearestStation = nearestStation; }
    public String getHomePhone() { return homePhone; }
    public void setHomePhone(String homePhone) { this.homePhone = homePhone; }
    public String getFaxPhone() { return faxPhone; }
    public void setFaxPhone(String faxPhone) { this.faxPhone = faxPhone; }
    public String getMobilePhone() { return mobilePhone; }
    public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }
    public String getDesiredJob() { return desiredJob; }
    public void setDesiredJob(String desiredJob) { this.desiredJob = desiredJob; }
    public String getDesiredType() { return desiredType; }
    public void setDesiredType(String desiredType) { this.desiredType = desiredType; }
    public String getDesiredTypes() { return desiredTypes; }
    public void setDesiredTypes(String desiredTypes) { this.desiredTypes = desiredTypes; }
    public String getIntroducer() { return introducer; }
    public void setIntroducer(String introducer) { this.introducer = introducer; }
    public Boolean getQualNursery() { return qualNursery; }
    public void setQualNursery(Boolean qualNursery) { this.qualNursery = qualNursery; }
    public Boolean getQualCook() { return qualCook; }
    public void setQualCook(Boolean qualCook) { this.qualCook = qualCook; }
    public Boolean getQualCareWorker() { return qualCareWorker; }
    public void setQualCareWorker(Boolean qualCareWorker) { this.qualCareWorker = qualCareWorker; }
    public Boolean getQualCareHelper() { return qualCareHelper; }
    public void setQualCareHelper(Boolean qualCareHelper) { this.qualCareHelper = qualCareHelper; }
    public Boolean getAnimalDogOk() { return animalDogOk; }
    public void setAnimalDogOk(Boolean animalDogOk) { this.animalDogOk = animalDogOk; }
    public Boolean getAnimalCatOk() { return animalCatOk; }
    public void setAnimalCatOk(Boolean animalCatOk) { this.animalCatOk = animalCatOk; }
    public Boolean getAnimalDogAllergy() { return animalDogAllergy; }
    public void setAnimalDogAllergy(Boolean animalDogAllergy) { this.animalDogAllergy = animalDogAllergy; }
    public Boolean getAnimalCatAllergy() { return animalCatAllergy; }
    public void setAnimalCatAllergy(Boolean animalCatAllergy) { this.animalCatAllergy = animalCatAllergy; }
    public String getCooking() { return cooking; }
    public void setCooking(String cooking) { this.cooking = cooking; }
    public String getSmoking() { return smoking; }
    public void setSmoking(String smoking) { this.smoking = smoking; }
    public String getChildcareExp() { return childcareExp; }
    public void setChildcareExp(String childcareExp) { this.childcareExp = childcareExp; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public LocalDate getRegisteredDate() { return registeredDate; }
    public void setRegisteredDate(LocalDate registeredDate) { this.registeredDate = registeredDate; }
    public Boolean getLineWorks() { return lineWorks; }
    public void setLineWorks(Boolean lineWorks) { this.lineWorks = lineWorks; }

    // 就職希望条件
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getWorkDuties() { return workDuties; }
    public void setWorkDuties(String workDuties) { this.workDuties = workDuties; }
    public String getSpecificDays() { return specificDays; }
    public void setSpecificDays(String specificDays) { this.specificDays = specificDays; }
    public String getWorkAvailableHours() { return workAvailableHours; }
    public void setWorkAvailableHours(String workAvailableHours) { this.workAvailableHours = workAvailableHours; }
    public String getWorkStartPeriod() { return workStartPeriod; }
    public void setWorkStartPeriod(String workStartPeriod) { this.workStartPeriod = workStartPeriod; }
    public String getEmergencyRelation() { return emergencyRelation; }
    public void setEmergencyRelation(String emergencyRelation) { this.emergencyRelation = emergencyRelation; }
    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }
    public String getBabysitterExp() { return babysitterExp; }
    public void setBabysitterExp(String babysitterExp) { this.babysitterExp = babysitterExp; }
    public String getBabysitterAvail() { return babysitterAvail; }
    public void setBabysitterAvail(String babysitterAvail) { this.babysitterAvail = babysitterAvail; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
