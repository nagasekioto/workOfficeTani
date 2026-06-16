package jp.co.housekeeping.person_management.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("customer_requests")
public class CustomerRequest {
    @Id private Long id;
    private Long customerId;
    private String postalCode;
    private String address;
    private String workAddress;

    // 仕事内容
    private Boolean jobCooking = false;
    private Boolean jobLaundry = false;
    private Boolean jobCleaning = false;
    private Boolean jobIroning = false;
    private Boolean jobBabysitting = false;
    private Boolean jobNursing = false;
    private Boolean jobOther = false;
    private String jobOtherText;

    // 頻度
    private String freqType; // "temp" or "weekly"
    private LocalDate freqTempDate;
    private String freqWeeklyDays; // "月,火,水"
    private LocalTime freqWeeklyStart;
    private LocalTime freqWeeklyEnd;

    // 家族構成
    private Integer familyAdults = 0;
    private Integer familyChildren = 0;

    // 紹介先
    private String introducerName;
    private Boolean introInternet = false;
    private Boolean introTownpage = false;
    private Boolean introOther = false;
    private String introOtherText;

    // ペット
    private Boolean petNone = true;
    private Boolean petDog = false;
    private Boolean petCat = false;
    private Boolean petOther = false;
    private String petOtherText;

    // 備考
    private String remarks;

    // 面接
    private Boolean interviewNone = true;
    private LocalDateTime interviewDate1;
    private LocalDateTime interviewDate2;

    // 連絡履歴（JSON: [{date:"2024-01-01",note:"電話した"}]）
    private String contactHistory;

    // 候補者
    private Long candidatePersonId;

    // 印刷フラグ
    private Boolean printed = false;

    private LocalDateTime createdAt;

    public CustomerRequest() {}

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getWorkAddress() { return workAddress; }
    public void setWorkAddress(String workAddress) { this.workAddress = workAddress; }
    public Boolean getJobCooking() { return jobCooking; }
    public void setJobCooking(Boolean jobCooking) { this.jobCooking = jobCooking; }
    public Boolean getJobLaundry() { return jobLaundry; }
    public void setJobLaundry(Boolean jobLaundry) { this.jobLaundry = jobLaundry; }
    public Boolean getJobCleaning() { return jobCleaning; }
    public void setJobCleaning(Boolean jobCleaning) { this.jobCleaning = jobCleaning; }
    public Boolean getJobIroning() { return jobIroning; }
    public void setJobIroning(Boolean jobIroning) { this.jobIroning = jobIroning; }
    public Boolean getJobBabysitting() { return jobBabysitting; }
    public void setJobBabysitting(Boolean jobBabysitting) { this.jobBabysitting = jobBabysitting; }
    public Boolean getJobNursing() { return jobNursing; }
    public void setJobNursing(Boolean jobNursing) { this.jobNursing = jobNursing; }
    public Boolean getJobOther() { return jobOther; }
    public void setJobOther(Boolean jobOther) { this.jobOther = jobOther; }
    public String getJobOtherText() { return jobOtherText; }
    public void setJobOtherText(String jobOtherText) { this.jobOtherText = jobOtherText; }
    public String getFreqType() { return freqType; }
    public void setFreqType(String freqType) { this.freqType = freqType; }
    public LocalDate getFreqTempDate() { return freqTempDate; }
    public void setFreqTempDate(LocalDate freqTempDate) { this.freqTempDate = freqTempDate; }
    public String getFreqWeeklyDays() { return freqWeeklyDays; }
    public void setFreqWeeklyDays(String freqWeeklyDays) { this.freqWeeklyDays = freqWeeklyDays; }
    public LocalTime getFreqWeeklyStart() { return freqWeeklyStart; }
    public void setFreqWeeklyStart(LocalTime freqWeeklyStart) { this.freqWeeklyStart = freqWeeklyStart; }
    public LocalTime getFreqWeeklyEnd() { return freqWeeklyEnd; }
    public void setFreqWeeklyEnd(LocalTime freqWeeklyEnd) { this.freqWeeklyEnd = freqWeeklyEnd; }
    public Integer getFamilyAdults() { return familyAdults; }
    public void setFamilyAdults(Integer familyAdults) { this.familyAdults = familyAdults; }
    public Integer getFamilyChildren() { return familyChildren; }
    public void setFamilyChildren(Integer familyChildren) { this.familyChildren = familyChildren; }
    public String getIntroducerName() { return introducerName; }
    public void setIntroducerName(String introducerName) { this.introducerName = introducerName; }
    public Boolean getIntroInternet() { return introInternet; }
    public void setIntroInternet(Boolean introInternet) { this.introInternet = introInternet; }
    public Boolean getIntroTownpage() { return introTownpage; }
    public void setIntroTownpage(Boolean introTownpage) { this.introTownpage = introTownpage; }
    public Boolean getIntroOther() { return introOther; }
    public void setIntroOther(Boolean introOther) { this.introOther = introOther; }
    public String getIntroOtherText() { return introOtherText; }
    public void setIntroOtherText(String introOtherText) { this.introOtherText = introOtherText; }
    public Boolean getPetNone() { return petNone; }
    public void setPetNone(Boolean petNone) { this.petNone = petNone; }
    public Boolean getPetDog() { return petDog; }
    public void setPetDog(Boolean petDog) { this.petDog = petDog; }
    public Boolean getPetCat() { return petCat; }
    public void setPetCat(Boolean petCat) { this.petCat = petCat; }
    public Boolean getPetOther() { return petOther; }
    public void setPetOther(Boolean petOther) { this.petOther = petOther; }
    public String getPetOtherText() { return petOtherText; }
    public void setPetOtherText(String petOtherText) { this.petOtherText = petOtherText; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Boolean getInterviewNone() { return interviewNone; }
    public void setInterviewNone(Boolean interviewNone) { this.interviewNone = interviewNone; }
    public LocalDateTime getInterviewDate1() { return interviewDate1; }
    public void setInterviewDate1(LocalDateTime interviewDate1) { this.interviewDate1 = interviewDate1; }
    public LocalDateTime getInterviewDate2() { return interviewDate2; }
    public void setInterviewDate2(LocalDateTime interviewDate2) { this.interviewDate2 = interviewDate2; }
    public String getContactHistory() { return contactHistory; }
    public void setContactHistory(String contactHistory) { this.contactHistory = contactHistory; }
    public Long getCandidatePersonId() { return candidatePersonId; }
    public void setCandidatePersonId(Long candidatePersonId) { this.candidatePersonId = candidatePersonId; }
    public Boolean getPrinted() { return printed; }
    public void setPrinted(Boolean printed) { this.printed = printed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
