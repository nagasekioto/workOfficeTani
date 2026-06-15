	package jp.co.housekeeping.person_management.model;
	
	import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
	
	/**
	 * 求職者（家政婦）エンティティ
	 */
	@Table("persons")
	public class Person {
	    
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
	    
	    // 希望条件
	    private String desiredJob;      // 家事 or 介護
	    private String desiredType;     // 通勤 or パート or 住込
	    
	    // その他情報
	    private String introducer;
	    
	    // 資格（複数選択可）
	    private Boolean qualNursery;       // 保育士
	    private Boolean qualCook;          // 調理師
	    private Boolean qualCareWorker;    // 介護福祉士
	    private Boolean qualCareHelper;    // 介護ヘルパー
	    
	    // 動物（複数選択可）
	    private Boolean animalDogOk;
	    private Boolean animalCatOk;
	    private Boolean animalDogAllergy;
	    private Boolean animalCatAllergy;
	    
	    // 単一選択項目
	    private String cooking;         // 好き / 普通 / 嫌い
	    private String smoking;         // 吸う / 吸わない
	    private String childcareExp;    // 有 / 無
	    
	    private LocalDate birthDate;
	    private LocalDate registeredDate;
	    
	    // コンストラクタ
	    public Person() {
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
	    
	    public String getDesiredJob() {
	        return desiredJob;
	    }
	    
	    public void setDesiredJob(String desiredJob) {
	        this.desiredJob = desiredJob;
	    }
	    
	    public String getDesiredType() {
	        return desiredType;
	    }
	    
	    public void setDesiredType(String desiredType) {
	        this.desiredType = desiredType;
	    }
	    
	    public String getIntroducer() {
	        return introducer;
	    }
	    
	    public void setIntroducer(String introducer) {
	        this.introducer = introducer;
	    }
	    
	    public Boolean getQualNursery() {
	        return qualNursery;
	    }
	    
	    public void setQualNursery(Boolean qualNursery) {
	        this.qualNursery = qualNursery;
	    }
	    
	    public Boolean getQualCook() {
	        return qualCook;
	    }
	    
	    public void setQualCook(Boolean qualCook) {
	        this.qualCook = qualCook;
	    }
	    
	    public Boolean getQualCareWorker() {
	        return qualCareWorker;
	    }
	    
	    public void setQualCareWorker(Boolean qualCareWorker) {
	        this.qualCareWorker = qualCareWorker;
	    }
	    
	    public Boolean getQualCareHelper() {
	        return qualCareHelper;
	    }
	    
	    public void setQualCareHelper(Boolean qualCareHelper) {
	        this.qualCareHelper = qualCareHelper;
	    }
	    
	    public Boolean getAnimalDogOk() {
	        return animalDogOk;
	    }
	    
	    public void setAnimalDogOk(Boolean animalDogOk) {
	        this.animalDogOk = animalDogOk;
	    }
	    
	    public Boolean getAnimalCatOk() {
	        return animalCatOk;
	    }
	    
	    public void setAnimalCatOk(Boolean animalCatOk) {
	        this.animalCatOk = animalCatOk;
	    }
	    
	    public Boolean getAnimalDogAllergy() {
	        return animalDogAllergy;
	    }
	    
	    public void setAnimalDogAllergy(Boolean animalDogAllergy) {
	        this.animalDogAllergy = animalDogAllergy;
	    }
	    
	    public Boolean getAnimalCatAllergy() {
	        return animalCatAllergy;
	    }
	    
	    public void setAnimalCatAllergy(Boolean animalCatAllergy) {
	        this.animalCatAllergy = animalCatAllergy;
	    }
	    
	    public String getCooking() {
	        return cooking;
	    }
	    
	    public void setCooking(String cooking) {
	        this.cooking = cooking;
	    }
	    
	    public String getSmoking() {
	        return smoking;
	    }
	    
	    public void setSmoking(String smoking) {
	        this.smoking = smoking;
	    }
	    
	    public String getChildcareExp() {
	        return childcareExp;
	    }
	    
	    public void setChildcareExp(String childcareExp) {
	        this.childcareExp = childcareExp;
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
	}