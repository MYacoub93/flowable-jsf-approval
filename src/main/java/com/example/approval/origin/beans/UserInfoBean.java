/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.origin.beans;

import com.example.approval.common.utils.DateTimeUtil;
import com.example.approval.common.utils.StringUtilities;

import java.util.Date;

public class UserInfoBean extends BaseBean {

    private String staffId;
    private String staffName;
    private String staffNameS;
    private String firstName;
    private String lastName;

    private String workStartDate;
    private String samAccountName;
    private String userType;
    private String roleDesc;
    private String roleDescS;
    private String sectionDesc;
    private String sectionDescS;
    private String managerId;
    private String sectionNo;

    private String jobName;
    private String jobNameS;
    private String statusDesc;
    private String statusDescS;
    private String email;
    private String martialStatus;
    private String bloodGroup;
    private String marriageDate;
    private Date marriageDateTransient;

    private String gender;

    private String homeTel;
    private String zipCode;
    private String poBox;
    private String mobile;
    private String address;
    private String fixMobile;
    private String fixPoBox;
    private String fixZipCode;
    private String fixAddress;
    private String fixHomeTel;
    private String userId;
    private String nationality;
    private String nationalityS;
    private String assignmentDate;
    private String passportNo;
    private String beginDate;
    private String endDate;

    public String getSectionNo() {
        return sectionNo;
    }

    public void setSectionNo(String sectionNo) {
        this.sectionNo = sectionNo;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getHomeTel() {
        return homeTel;
    }

    public void setHomeTel(String homeTel) {
        this.homeTel = homeTel;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getPoBox() {
        return poBox;
    }

    public void setPoBox(String poBox) {
        this.poBox = poBox;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getFixMobile() {
        return fixMobile;
    }

    public void setFixMobile(String fixMobile) {
        this.fixMobile = fixMobile;
    }

    public String getFixPoBox() {
        return fixPoBox;
    }

    public void setFixPoBox(String fixPoBox) {
        this.fixPoBox = fixPoBox;
    }

    public String getFixZipCode() {
        return fixZipCode;
    }

    public void setFixZipCode(String fixZipCode) {
        this.fixZipCode = fixZipCode;
    }

    public String getFixAddress() {
        return fixAddress;
    }

    public void setFixAddress(String fixAddress) {
        this.fixAddress = fixAddress;
    }

    public String getFixHomeTel() {
        return fixHomeTel;
    }

    public void setFixHomeTel(String fixHomeTel) {
        this.fixHomeTel = fixHomeTel;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMartialStatus() {
        return martialStatus;
    }

    public void setMartialStatus(String martialStatus) {
        this.martialStatus = martialStatus;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getMarriageDate() {
        return marriageDate;
    }

    public void setMarriageDate(String marriageDate) {
        this.marriageDate = marriageDate;
    }

    public Date getMarriageDateTransient() {
        if (StringUtilities.isNotEmpty(marriageDate)) {
            marriageDateTransient = DateTimeUtil.convertStringToDate(marriageDate);
        }
        return marriageDateTransient;
    }

    public void setMarriageDateTransient(Date marriageDateTransient) {
        if (marriageDateTransient != null) {
            marriageDate = DateTimeUtil.getFormatedDateLocale(marriageDateTransient);
        }
        this.marriageDateTransient = marriageDateTransient;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public String getWorkStartDate() {
        return workStartDate;
    }

    public void setWorkStartDate(String workStartDate) {
        this.workStartDate = workStartDate;
    }

    public String getSamAccountName() {
        return samAccountName;
    }

    public void setSamAccountName(String samAccountName) {
        this.samAccountName = samAccountName;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getRoleDesc() {
        return roleDesc;
    }

    public void setRoleDesc(String roleDesc) {
        this.roleDesc = roleDesc;
    }

    public String getSectionDesc() {
        return sectionDesc;
    }

    public void setSectionDesc(String sectionDesc) {
        this.sectionDesc = sectionDesc;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getStatusDesc() {
        return statusDesc;
    }

    public void setStatusDesc(String statusDesc) {
        this.statusDesc = statusDesc;
    }

    @Override
    public String toString() {
        return "HRSessionInfo{" + "staffId=" + staffId + ", staffName=" + staffName + ", workStartDate=" + workStartDate + ", samAccountName=" + samAccountName + ", userType=" + userType + ", roleDesc=" + roleDesc + ", sectionDesc=" + sectionDesc + ", jobName=" + jobName + ", statusDesc=" + statusDesc + '}';
    }

    public String getStaffNameS() {
        return staffNameS;
    }

    public void setStaffNameS(String staffNameS) {
        this.staffNameS = staffNameS;
    }

    public String getRoleDescS() {
        return roleDescS;
    }

    public void setRoleDescS(String roleDescS) {
        this.roleDescS = roleDescS;
    }

    public String getSectionDescS() {
        return sectionDescS;
    }

    public void setSectionDescS(String sectionDescS) {
        this.sectionDescS = sectionDescS;
    }

    public String getJobNameS() {
        return jobNameS;
    }

    public void setJobNameS(String jobNameS) {
        this.jobNameS = jobNameS;
    }

    public String getStatusDescS() {
        return statusDescS;
    }

    public void setStatusDescS(String statusDescS) {
        this.statusDescS = statusDescS;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getPassportNo() {
        return passportNo;
    }

    public void setPassportNo(String passportNo) {
        this.passportNo = passportNo;
    }

    public String getNationalityS() {
        return nationalityS;
    }

    public void setNationalityS(String nationalityS) {
        this.nationalityS = nationalityS;
    }

    public String getAssignmentDate() {
        return assignmentDate;
    }

    public void setAssignmentDate(String assignmentDate) {
        this.assignmentDate = assignmentDate;
    }

    public String getBeginDate() {
        return beginDate;
    }

    public void setBeginDate(String begsinDate) {
        this.beginDate = beginDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    @Override
    public int getId() {
        return this.hashCode();
    }

}
