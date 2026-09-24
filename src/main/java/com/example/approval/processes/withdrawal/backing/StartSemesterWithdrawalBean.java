package com.example.approval.processes.withdrawal.backing;

import com.example.approval.backing.BaseBackingBean;
import com.example.approval.backing.UserLoginBean;
import com.example.approval.origin.beans.ReasonsBean;
import com.example.approval.origin.beans.SemesterBean;
import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.service.CommonService;
import com.example.approval.processes.withdrawal.service.SemesterWithdrawalService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.withdrawal.SemesterWithdrawalConstants.*;

/**
 * Backing bean for {@code start-semester-withdrawl.xhtml} - the student's
 * initiator form of the Semester Withdrawal process.
 *
 * <p>Mirrors {@code StartStudentProofBean} / {@code StartClearanceBean}:
 * the student information section is <b>read-only</b> (loaded from the
 * Oracle SIS via the existing {@code CommonService.getStudentInfo} for the
 * currently logged-in user), the editable fields are the required
 * withdrawl reason and withdrawl semester dropdowns plus the optional
 * student note.</p>
 *
 * <p>Pure Spring bean (like the other backing beans): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works. Do NOT add
 * CDI annotations (@Named/@ViewScoped) - Weld would then create the
 * instance and skip Spring injection.</p>
 */
@Component("startSemesterWithdrawalBean")
@Scope("view")
public class StartSemesterWithdrawalBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(StartSemesterWithdrawalBean.class);

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private SemesterWithdrawalService withdrawalService;

    @Autowired
    private CommonService commonService;

    /** Read-only student information loaded from the SIS. */
    private StudentInfoBean studentInfo;

    /** Required withdrawl reason ({@code withdrawalReason} process variable). */
    private String withdrawalReason;

    /** Required withdrawl semester ({@code withdrawalSemester} process variable). */
    private String withdrawalSemester;

    /** Optional student note ({@code studentNote} process variable). */
    private String studentNote;

    @PostConstruct
    public void init() {
        if (loginBean.isLoggedIn()) {
            loadStudentInfo();
            loadReasons();
            loadSemesters();
        }
    }

    private void loadStudentInfo() {
        try {
            studentInfo = commonService.getStudentInfo(loginBean.getCurrentUser().getId());
        } catch (Exception e) {
            log.error("Failed to load student information for {}",
                    loginBean.getCurrentUser().getId(), e);
            studentInfo = null;
        }
    }

    private void loadReasons() {
        // reasons are cached by the view (getReasons lazily loads once)
    }

    private void loadSemesters() {
        // semesters are cached by the view (getSemesters lazily loads once)
    }

    /** True when the SIS returned no row for the logged-in user. */
    public boolean isStudentInfoMissing() {
        return studentInfo == null;
    }

    public boolean isCanStart() {
        return loginBean.isLoggedIn() && !isStudentInfoMissing();
    }

    // ------------------------------------------------------------------
    // Dropdown data
    // ------------------------------------------------------------------

    /**
     * Withdrawl reason options (sis_reasons, reason_type = 2) - the Arabic
     * or English description follows the current UI language.
     */
    public List<ReasonsBean> getReasons() {
        return commonService.getTransactionReasons();
    }

    /** Withdrawl semester options (prepared semesters + current semester). */
    public List<SemesterBean> getSemesters() {
        return commonService.getTransactionSemester();
    }

    // ------------------------------------------------------------------
    // Action
    // ------------------------------------------------------------------

    /**
     * Submit sequence (spec section 7):
     *
     * <ol>
     *   <li>required-field validation (reason + semester);</li>
     *   <li><b>presubmit validation</b> {@code checkBpmPresumbitService} -
     *       status 0 shows the SIS message and NEVER creates the process;</li>
     *   <li>only on status 1 the Flowable instance is started.</li>
     * </ol>
     */
    public String submit() {
        if (!loginBean.isLoggedIn()) {
            addError(getLabel("withdrawal.error.notLoggedIn"));
            return null;
        }
        if (isStudentInfoMissing()) {
            addError(getLabel("withdrawal.error.infoMissing"));
            return null;
        }
        if (withdrawalReason == null || withdrawalReason.isBlank()) {
            addError(getLabel("withdrawal.error.reasonRequired"));
            return null;
        }
        if (withdrawalSemester == null || withdrawalSemester.isBlank()) {
            addError(getLabel("withdrawal.error.semesterRequired"));
            return null;
        }
        String username = loginBean.getCurrentUser().getId();
        try {
            // 1. SIS presubmit validation - BEFORE the process is created
            SemesterWithdrawalService.PresubmitResult presubmit =
                    withdrawalService.checkPresubmit(username, withdrawalSemester, lang());
            if (!presubmit.isAllowed()) {
                // status == 0: display the returned msg, do NOT create the process
                addError(presubmit.msg() != null && !presubmit.msg().isBlank()
                        ? presubmit.msg()
                        : getLabel("withdrawal.error.presubmitRejected"));
                return null;
            }

            // 2. presubmit OK: create the process instance
            Map<String, Object> vars = new HashMap<>();
            vars.put(VAR_WITHDRAWAL_REASON, withdrawalReason);
            vars.put(VAR_WITHDRAWAL_REASON_DESC, reasonDescOf(withdrawalReason));
            vars.put(VAR_WITHDRAWAL_SEMESTER, withdrawalSemester);
            vars.put(VAR_WITHDRAWAL_SEMESTER_DESC, semesterDescOf(withdrawalSemester));
            vars.put(VAR_STUDENT_NOTE, studentNote != null ? studentNote : "");
            vars.put(VAR_LANG, lang());
            org.flowable.engine.runtime.ProcessInstance pi =
                    withdrawalService.startWithdrawal(username, vars);

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            getLabel("withdrawal.submitted", pi.getId()), null));
            FacesContext.getCurrentInstance().getExternalContext().getFlash()
                    .setKeepMessages(true);
            return "/dashboard?faces-redirect=true";
        } catch (SecurityException e) {
            addError(e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to start Semester Withdrawal process", e);
            addError(getLabel("withdrawal.error.submitFailed") + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Localization helpers
    // ------------------------------------------------------------------

    /** Current UI language flag: 1 = Arabic, 2 = English (SIS convention). */
    private String lang() {
        return "ar".equals(resolveUiLocale().getLanguage()) ? "1" : "2";
    }

    private String reasonDescOf(String reasonCode) {
        for (ReasonsBean reason : getReasons()) {
            if (reason.getReasonCode() != null && reason.getReasonCode().equals(reasonCode)) {
                return "1".equals(lang()) ? reason.getReasonDesc() : reason.getReasonDescS();
            }
        }
        return null;
    }

    private String semesterDescOf(String semester) {
        for (SemesterBean s : getSemesters()) {
            if (s.getSemester() != null && s.getSemester().equals(semester)) {
                return "1".equals(lang()) ? s.getSemesterDesc() : s.getSemesterDescS();
            }
        }
        return null;
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // ------------------------------------------------------------------
    // Read helpers for the form (all read-only)
    // ------------------------------------------------------------------

    public String getStudentId() {
        return studentInfo != null ? studentInfo.getStudentId() : null;
    }

    public String getStudentName() {
        return studentInfo != null ? studentInfo.getStudentName() : null;
    }

    public String getGpa() {
        return studentInfo != null ? studentInfo.getCumStudentGPA() : null;
    }

    public String getCurrentSemester() {
        return studentInfo != null ? studentInfo.getAcademicYear() : null;
    }

    public String getWithdrawalReason() {
        return withdrawalReason;
    }

    public void setWithdrawalReason(String withdrawalReason) {
        this.withdrawalReason = withdrawalReason;
    }

    public String getWithdrawalSemester() {
        return withdrawalSemester;
    }

    public void setWithdrawalSemester(String withdrawalSemester) {
        this.withdrawalSemester = withdrawalSemester;
    }

    public String getStudentNote() {
        return studentNote;
    }

    public void setStudentNote(String studentNote) {
        this.studentNote = studentNote;
    }
}