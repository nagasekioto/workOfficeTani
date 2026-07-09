package jp.co.housekeeping.person_management.model;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("customers")
public class Customer {

    @Id
    private Long id;
    private Integer no;

    // 姓名
    private String lastNameKana;
    private String firstNameKana;
    private String lastNameKanji;
    private String firstNameKanji;

    // 住所
    private String postalCode;
    private String address1;
    private String address2;
    private String address3;

    // 最寄り駅
    private String nearestLine;
    private String nearestStation;
    private String accessTime;

    // 連絡先
    private String homePhone;
    private String faxPhone;
    private String mobilePhone;

    // ─── 担当者 ──────────────────────────────
    private String staffName;       // 担当者名
    private String staffPhone;      // 担当者電話番号
    private String staffNotes;      // 担当者備考

    // ─── 仕事内容（複数可、カンマ区切り） ───
    private String jobContents;

    // ─── 頻度 ────────────────────────────────
    private String freqType;        // "temp" or "weekly"
    private String freqTempDate;    // 臨時の場合の月日
    private String freqWeeklyDays;  // 毎週の場合の曜日（カンマ区切り）
    private String freqWeeklyStart; // 開始時刻
    private String freqWeeklyEnd;   // 終了時刻

    // ─── 家族構成 ─────────────────────────────
    private Integer familyAdults;
    private Integer familyChildren;

    // ─── 紹介先 ──────────────────────────────
    private String introducerName;
    private String introRoute;      // カンマ区切り（インターネット,タウンページ,その他）
    private String introOtherText;

    // ─── ペット ──────────────────────────────
    private String petType;         // none/dog/cat/other
    private String petOtherText;

    // ─── 面接 ────────────────────────────────
    private Boolean interviewNone;
    private String interviewDate1;
    private String interviewDate2;

    private LocalDate registeredDate;
    private LocalDate retiredAt; // null=取引中, 値あり=取引終了日（1-2-4元求人先へ）
    private String notes;           // 備考

    public Customer() {}

    // getter/setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getNo() { return no; }
    public void setNo(Integer no) { this.no = no; }
    public String getLastNameKana() { return lastNameKana; }
    public void setLastNameKana(String v) { this.lastNameKana = v; }
    public String getFirstNameKana() { return firstNameKana; }
    public void setFirstNameKana(String v) { this.firstNameKana = v; }
    public String getLastNameKanji() { return lastNameKanji; }
    public void setLastNameKanji(String v) { this.lastNameKanji = v; }
    public String getFirstNameKanji() { return firstNameKanji; }
    public void setFirstNameKanji(String v) { this.firstNameKanji = v; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String v) { this.postalCode = v; }
    public String getAddress1() { return address1; }
    public void setAddress1(String v) { this.address1 = v; }
    public String getAddress2() { return address2; }
    public void setAddress2(String v) { this.address2 = v; }
    public String getAddress3() { return address3; }
    public void setAddress3(String v) { this.address3 = v; }
    public String getNearestLine() { return nearestLine; }
    public void setNearestLine(String v) { this.nearestLine = v; }
    public String getNearestStation() { return nearestStation; }
    public void setNearestStation(String v) { this.nearestStation = v; }
    public String getAccessTime() { return accessTime; }
    public void setAccessTime(String v) { this.accessTime = v; }
    public String getHomePhone() { return homePhone; }
    public void setHomePhone(String v) { this.homePhone = v; }
    public String getFaxPhone() { return faxPhone; }
    public void setFaxPhone(String v) { this.faxPhone = v; }
    public String getMobilePhone() { return mobilePhone; }
    public void setMobilePhone(String v) { this.mobilePhone = v; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String v) { this.staffName = v; }
    public String getStaffPhone() { return staffPhone; }
    public void setStaffPhone(String v) { this.staffPhone = v; }
    public String getStaffNotes() { return staffNotes; }
    public void setStaffNotes(String v) { this.staffNotes = v; }
    public String getJobContents() { return jobContents; }
    public void setJobContents(String v) { this.jobContents = v; }
    public String getFreqType() { return freqType; }
    public void setFreqType(String v) { this.freqType = v; }
    public String getFreqTempDate() { return freqTempDate; }
    public void setFreqTempDate(String v) { this.freqTempDate = v; }
    public String getFreqWeeklyDays() { return freqWeeklyDays; }
    public void setFreqWeeklyDays(String v) { this.freqWeeklyDays = v; }
    public String getFreqWeeklyStart() { return freqWeeklyStart; }
    public void setFreqWeeklyStart(String v) { this.freqWeeklyStart = v; }
    public String getFreqWeeklyEnd() { return freqWeeklyEnd; }
    public void setFreqWeeklyEnd(String v) { this.freqWeeklyEnd = v; }
    public Integer getFamilyAdults() { return familyAdults; }
    public void setFamilyAdults(Integer v) { this.familyAdults = v; }
    public Integer getFamilyChildren() { return familyChildren; }
    public void setFamilyChildren(Integer v) { this.familyChildren = v; }
    public String getIntroducerName() { return introducerName; }
    public void setIntroducerName(String v) { this.introducerName = v; }
    public String getIntroRoute() { return introRoute; }
    public void setIntroRoute(String v) { this.introRoute = v; }
    public String getIntroOtherText() { return introOtherText; }
    public void setIntroOtherText(String v) { this.introOtherText = v; }
    public String getPetType() { return petType; }
    public void setPetType(String v) { this.petType = v; }
    public String getPetOtherText() { return petOtherText; }
    public void setPetOtherText(String v) { this.petOtherText = v; }
    public Boolean getInterviewNone() { return interviewNone; }
    public void setInterviewNone(Boolean v) { this.interviewNone = v; }
    public String getInterviewDate1() { return interviewDate1; }
    public void setInterviewDate1(String v) { this.interviewDate1 = v; }
    public String getInterviewDate2() { return interviewDate2; }
    public void setInterviewDate2(String v) { this.interviewDate2 = v; }
    public LocalDate getRegisteredDate() { return registeredDate; }
    public void setRegisteredDate(LocalDate v) { this.registeredDate = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public LocalDate getRetiredAt() { return retiredAt; }
    public void setRetiredAt(LocalDate v) { this.retiredAt = v; }
}
