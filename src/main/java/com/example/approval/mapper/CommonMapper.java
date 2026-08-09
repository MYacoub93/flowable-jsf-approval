/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.mapper;



import java.util.List;
import java.util.Map;

import com.example.approval.origin.beans.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@org.apache.ibatis.annotations.Mapper
public interface CommonMapper  {

    public String pingDB();

    public List<GroupBean> getHrsGroups();

    public List<RoleBean> getHrsRoles();

    public List<UserInfoBean> getHrsUsers();

    public StudentInfoBean getStudentInfo(Map<String, Object> map);

    //public List<SemesterBean> getStudentAcademicSemester(Map<String, Object> map);

    //public List<SemestersLookupBean> getSemesterInfo(Map<String, Object> map);

    public List<DepartmentBean> getDepartments(@Param("facultyNo") String facultyNo);

    public List<String> getUserDepts(@Param("userId") String userId);

    public List<String> getUserFaculties(@Param("userId") String userId);

    public List<FacultyBean> getFaculties();

    public List<RoleBean> getRoles();

    public List<UserBean> getSISUsers();

    public EmployeeInfoBean getEmployeeInfo(@Param("username") String username);

    public UserBean checkUserAvailability(@Param("username") String username);

    public StaffInfoBean getStaffInfoById(Map<String, Object> map);

    public StaffInfoBean getStaffInfo(Map<String, Object> map);

    public String getDeanOfCollege(@Param("facultyNo") String facultyNo, @Param("campusNo") String campusNo);

    public String getCollegeCode(@Param("facultyNo") String facultyNo);

    public String getHeadOfDepartment(@Param("facultyNo") String facultyNo,
            @Param("deptNo") String departmentNo,
            @Param("campusNo") String campusNo);

    public String getDepartmentCode(@Param("facultyNo") String facultyNo,
            @Param("deptNo") String departmentNo);

    public String getWorkFlowRequestNo();

    public UserInfoBean getHrsUserInfo(@Param("username") String username);

    public List<UserInfoBean> getAuthorizedHrStaffs();

    public String isStaffExists(@Param("staffId") String staffId);

    public String isStaffWorking(@Param("staffId") String staffId);

    //public List<CountryBean> getCountries();

    public GroupBean getHrsSectionInfo(@Param("sectionNo") String sectionNo);

    public String getTransactionSeqNo();

    //public List<CityBean> getCities(@Param("countryNo") String countryNo);

    //public List<DaysBean> getDaysLookup();

    public void insertStaffTimeSheet(Map map);

    public String getCurrentTime();

    public String sendSMS(@Param("mobileNo") String mobileNo, @Param("text") String text);

    public String checkCaseRecord(@Param("caseId") String caseId);

    //public void auditCase(AuditLogBean auditLogBean);

    //public void auditCaseDTL(AuditLogBean auditLogBean);

//    public void insertCaseAttachments(@Param("caseId") String caseId,
//            @Param("contentId") String contentId,
//            @Param("contentName") String contentName,
//            @Param("entryUser") String entryUser,
//            @Param("osUser") String osUser,
//            @Param("terminal") String terminal);

    /*public List<PermittedDocument> getPermittedDocument(@Param("requesterId") String requesterId,
            @Param("yearCode") String yearCode,
            @Param("semester") String semester);*/

//    public void getDocumentReportLink(Map map);
//
//    public void checkBpmServiceRules(Map map);
//
//    public void checkBpmPresumbitService(Map map);

//    public List<AttachmentsBean> getAttachments();
//
//    public List<TimeLookupBean> getTimeLookup();
//
//    public List<PermittedDocument> getServiceDocumentInfo(@Param("documentCode") String documentCode);
//
//    public List<AcademicTransactionBean> getStudentAcademicTransactions(String studentId);
//
//    public List<HighSchoolInfoBean> getStudentHighSchoolInfo(String studentId);
//
//    public List<ProbationInfoBean> getStudentProbations(String studentId);
//
//    public List<ExemptionBean> getStudentExemptions(String studentId);

//    public String getStudentTotalBalance(String studentId);
//
//    public void processServicePaymentClaim(Map<String, Object> map);
//
//    public void insertStudentsApplications(Map<String, Object> map);
//
//    public String checkIfStudentHasApplication(Map<String, Object> map);
//
//    public StudentNameBean getStudentFullName(@Param("studentId") String studentId);
//
    public void processSynchUser(@Param("username") String username);
//
//    public String checkServicePeriod(Map<String, Object> map);
//
      public StudentInfoBean getStudentInfoById(Map<String, Object> map);

}
