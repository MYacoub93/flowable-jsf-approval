package com.example.approval.mapper;

import com.example.approval.origin.beans.DepartmentBean;
import com.example.approval.origin.beans.EmployeeInfoBean;
import com.example.approval.origin.beans.FacultyBean;
import com.example.approval.origin.beans.GroupBean;
import com.example.approval.origin.beans.ReasonsBean;
import com.example.approval.origin.beans.RoleBean;
import com.example.approval.origin.beans.SemesterBean;
import com.example.approval.origin.beans.StaffInfoBean;
import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.origin.beans.UserBean;
import com.example.approval.origin.beans.UserInfoBean;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * MyBatis mapper for {@code CommonMapper.xml}. Signatures here are taken
 * directly from how the existing {@code CommonService} calls this mapper, so
 * this interface is the ground truth to code against - not a guess.
 *
 * <p><b>Two things surfaced while aligning this against {@code CommonMapper.xml}
 * and {@code CommonService}, worth fixing/confirming before relying on this:</b></p>
 * <ul>
 *   <li>{@link #getStaffInfo} / {@link #getStudentInfo} take a bilingual
 *       lookup {@code Map} with keys {@code langS} (Arabic) / {@code lang}
 *       (English) / {@code userName} - matching {@code CommonService}. The
 *       XML as originally shared used {@code #{arabicLang}} / {@code #{englishLang}}
 *       instead of {@code #{langS}} / {@code #{lang}}, which would not have
 *       bound correctly; the accompanying {@code CommonMapper.xml} in this
 *       delivery has been corrected to use {@code langS}/{@code lang}.</li>
 *   <li>{@link #getStaffInfoById}, {@link #getStudentInfoById},
 *       {@link #checkUserAvailability} and {@link #getHrsSectionInfo} are
 *       called by {@code CommonService} but have <b>no matching
 *       {@code <select>}/{@code <insert>} id</b> anywhere in the
 *       {@code CommonMapper.xml} that was shared. They're declared here so
 *       this interface still compiles against {@code CommonService}, but
 *       calling any of them will fail at runtime ("Invalid bound statement")
 *       until the corresponding SQL is added to the XML.</li>
 * </ul>
 */
@Mapper
public interface CommonMapper {

    String pingDB();

    List<GroupBean> getHrsGroups();

    List<RoleBean> getHrsRoles();

    List<RoleBean> getRoles();

    List<UserBean> getSISUsers();

    List<UserInfoBean> getHrsUsers();

    EmployeeInfoBean getEmployeeInfo(@Param("username") String username);

    /**
     * map keys: {@code langS} (Arabic locale), {@code lang} (English locale),
     * {@code userName}. Build with {@code CoreConstants.INT_ARABIC_LOCALE} /
     * {@code CoreConstants.INT_ENGLISH_LOCALE}, same as {@code CommonService}.
     */
    StaffInfoBean getStaffInfo(Map<String, Object> params);

    /**
     * ⚠ No matching statement id in the CommonMapper.xml provided - add the
     * SQL before wiring this up. map keys: {@code langS}, {@code lang},
     * {@code userId}.
     */
    StaffInfoBean getStaffInfoById(Map<String, Object> params);

    /**
     * map keys: {@code langS}, {@code lang}, {@code userName}.
     */
    StudentInfoBean getStudentInfo(Map<String, Object> params);

    /**
     * ⚠ No matching statement id in the CommonMapper.xml provided (the XML
     * only has {@code getStudentById}, which takes {@code studentId} rather
     * than a bilingual-lookup map - confirm whether that's the same query
     * before wiring this up). map keys: {@code langS}, {@code lang},
     * {@code studentId}.
     */
    StudentInfoBean getStudentInfoById(Map<String, Object> params);

    List<DepartmentBean> getDepartments(@Param("facultyNo") String facultyNo);

    List<FacultyBean> getFaculties();

    /**
     * Note: the backing query only filters on facultyNo; campusNo is
     * accepted for call-site symmetry but currently unused by the SQL.
     */
    String getDeanOfCollege(@Param("facultyNo") String facultyNo,
                             @Param("campusNo") String campusNo);

    /**
     * Note: the backing query only filters on facultyNo + deptNo; campusNo
     * is accepted for call-site symmetry with {@link #getDeanOfCollege} but
     * currently unused by the SQL.
     */
    String getHeadOfDepartment(@Param("facultyNo") String facultyNo,
                                @Param("deptNo") String deptNo,
                                @Param("campusNo") String campusNo);

    String getDepartmentCode(@Param("facultyNo") String facultyNo,
                              @Param("deptNo") String deptNo);

    String getCollegeCode(@Param("facultyNo") String facultyNo);

    List<String> getUserDepts(@Param("userId") String userId);

    List<String> getUserFaculties(@Param("userId") String userId);

    UserInfoBean getHrsUserInfo(@Param("username") String username);

    List<UserInfoBean> getAuthorizedHrStaffs();

    /**
     * ⚠ No matching statement id in the CommonMapper.xml provided. The XML's
     * {@code getHrsGroupManager} (keyed by {@code sectionNo}, returns
     * {@code GroupBean}) looks like it might be the same query under a
     * different id - confirm before assuming that's a drop-in match.
     */
    GroupBean getHrsSectionInfo(@Param("sectionNo") String sectionNo);

    String isStaffExists(@Param("staffId") String staffId);

    String isStaffWorking(@Param("staffId") String staffId);

    String getCurrentTime();

    /**
     * ⚠ No matching statement id in the CommonMapper.xml provided - add the
     * SQL before wiring this up.
     */
    UserBean checkUserAvailability(@Param("username") String username);

    void processSynchUser(@Param("username") String username);

    /**
     * Withdrawal / transaction reasons of the Semester Withdrawal process
     * ({@code sis_reasons}, {@code reason_type = 2}). Bilingual row:
     * {@code reasonCode} (stored value), {@code reasonDesc} (English) and
     * {@code reasonDescS} (Arabic).
     */
    List<ReasonsBean> getTransactionReasons();

    /**
     * Semesters selectable for a Semester Withdrawal request: all prepared
     * semesters plus the current SIS semester, each with bilingual
     * descriptions ({@code semesterDesc} English / {@code semesterDescS}
     * Arabic).
     *
     * <p>map keys: {@code arabicLang} / {@code englishLang} (the SIS
     * language codes, e.g. {@code CoreConstants.INT_ARABIC_LOCALE} /
     * {@code INT_ENGLISH_LOCALE} - the distinct keys required by the
     * {@code sis_getters.get_semester_Desc} signature).</p>
     */
    List<SemesterBean> getTransactionSemester(Map<String, Object> params);

    /**
     * Callable wrapper of {@code BPM_PKG.check_presubmit_bpm_service} -
     * the pre-submit validation every student transaction must pass
     * <b>before</b> its Flowable process instance is created.
     *
     * <p>map keys (IN): {@code studentId}, {@code semester},
     * {@code documentCode}, {@code courseNo}, {@code courseEdition},
     * {@code activityCode}, {@code section}, {@code lang};
     * (OUT): {@code status} ({@code 1} = ok) and {@code msg}
     * (localized rejection message to display to the student).</p>
     */
    Map<String, Object> checkBpmPresumbitService(Map<String, Object> params);

    /**
     * Callable wrapper of {@code bpm_pkg.withdrawal_student_semester} -
     * executes the actual SIS semester withdrawal after all approvals and
     * the final Admission and Registration FYI completed.
     *
     * <p>map keys (IN): {@code studentId}, {@code semester}, {@code lang};
     * (OUT): {@code result} ({@code 1} = success) and {@code message}
     * (localized result message).</p>
     */
    Map<String, Object> processSemesterWithdrawal(Map<String, Object> params);
}