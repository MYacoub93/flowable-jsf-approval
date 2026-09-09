package com.example.approval.processes.studentproof.backing;

import com.example.approval.backing.BaseBackingBean;
import com.example.approval.backing.UserLoginBean;
import com.example.approval.processes.studentproof.model.StudentProofStudentInfo;
import com.example.approval.processes.studentproof.service.StudentProofService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Backing bean for {@code start-student-proof.xhtml} - the student's
 * initiator form of the Student Proof Certificate Letter process.
 *
 * <p>Mirrors {@code StartClearanceBean}: the student information section is
 * <b>read-only</b> (loaded from the Oracle SIS via the existing
 * {@code CommonService.getStudentInfo} / {@code CommonMapper.getStudentInfo}
 * for the currently logged-in user), the only editable field is the required
 * request note ({@code initiatorNote} process variable).</p>
 *
 * <p>Pure Spring bean (like the other backing beans): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works. Do NOT add
 * CDI annotations (@Named/@ViewScoped) - Weld would then create the
 * instance and skip Spring injection.</p>
 */
@Component("startStudentProofBean")
@Scope("view")
public class StartStudentProofBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(StartStudentProofBean.class);

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private StudentProofService studentProofService;

    /** Read-only student information loaded from the SIS. */
    private StudentProofStudentInfo studentInfo;

    /** Required request note (stored as {@code initiatorNote}). */
    private String note;

    @PostConstruct
    public void init() {
        if (loginBean.isLoggedIn()) {
            loadStudentInfo();
        }
    }

    /**
     * Loads the student information snapshot for the logged-in student
     * ({@code CommonService.getStudentInfo} - {@code CommonMapper.xml}
     * against the Oracle SIS).
     */
    private void loadStudentInfo() {
        try {
            studentInfo = studentProofService.findStudentInfo(
                    loginBean.getCurrentUser().getId());
        } catch (Exception e) {
            log.error("Failed to load student information for {}",
                    loginBean.getCurrentUser().getId(), e);
            studentInfo = null;
        }
    }

    /** True when the SIS returned no row for the logged-in user. */
    public boolean isStudentInfoMissing() {
        return studentInfo == null;
    }

    public boolean isCanStart() {
        return loginBean.isLoggedIn() && !isStudentInfoMissing();
    }

    // ------------------------------------------------------------------
    // Action
    // ------------------------------------------------------------------

    public String submit() {
        if (!loginBean.isLoggedIn()) {
            addError("You must be logged in");
            return null;
        }
        if (isStudentInfoMissing()) {
            addError("Your student information could not be loaded - cannot submit");
            return null;
        }
        if (note == null || note.isBlank()) {
            addError("A note is required");
            return null;
        }
        try {
            ProcessInstance pi = studentProofService.startStudentProof(
                    loginBean.getCurrentUser().getId(), note.trim(), null);
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Request submitted successfully. Instance ID: " + pi.getId(), null));
            FacesContext.getCurrentInstance().getExternalContext().getFlash()
                    .setKeepMessages(true);
            return "/dashboard?faces-redirect=true";
        } catch (SecurityException e) {
            addError(e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to start Student Proof Certificate process", e);
            addError("Failed to submit: " + e.getMessage());
            return null;
        }
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

    public String getStudentEmail() {
        return studentInfo != null ? studentInfo.getEmail() : null;
    }

    public String getStudentGpa() {
        return studentInfo != null ? studentInfo.getGpa() : null;
    }

    public String getStudentMobile() {
        return studentInfo != null ? studentInfo.getMobile() : null;
    }

    public String getCurrentSemester() {
        return studentInfo != null ? studentInfo.getCurrentSemester() : null;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}