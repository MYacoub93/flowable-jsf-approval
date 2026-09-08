package com.example.approval.clearance.backing;

import com.example.approval.backing.BaseBackingBean;
import com.example.approval.backing.UserLoginBean;
import com.example.approval.clearance.ClearanceRequestContract;
import com.example.approval.clearance.service.ClearanceService;
import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.service.CommonService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.Serializable;

/**
 * Backing bean for the "Start Clearance Letter" form. Only members of the
 * {@code STD} group may start the process (enforced again in
 * {@link ClearanceService#startClearance}).
 *
 * <p>The student information section is <b>read-only</b> and auto-loaded from
 * the Oracle SIS via {@link CommonService#getStudentInfo(String)} for the
 * logged-in user (mirrors {@code StartStudentProofBean}); the fields are
 * populated into the {@link ClearanceRequestContract} so they are stored as
 * process variables when the process starts. The only editable request field
 * in the student information area is the existing Notes field.</p>
 *
 * Pure Spring bean (like the other backing beans): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works.
 */
@Component("startClearanceBean")
@RequestScope
public class StartClearanceBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(StartClearanceBean.class);

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ClearanceService clearanceService;

    @Autowired
    private CommonService commonService;

    private final ClearanceRequestContract contract = new ClearanceRequestContract();

    /** Read-only student information snapshot loaded from the SIS (may be null). */
    private StudentInfoBean studentInfo;

    @PostConstruct
    public void init() {
        if (loginBean.isLoggedIn()) {
            loadStudentInfo();
        }
    }

    /**
     * Loads the student information snapshot for the logged-in student
     * ({@code CommonService.getStudentInfo} - {@code CommonMapper.xml}
     * against the Oracle SIS) and copies it into the read-only part of the
     * {@link ClearanceRequestContract}.
     */
    private void loadStudentInfo() {
        String username = loginBean.getCurrentUser().getId();
        try {
            studentInfo = commonService.getStudentInfo(username);
        } catch (Exception e) {
            log.error("Failed to load student information for {}", username, e);
            studentInfo = null;
        }
        if (studentInfo != null) {
            contract.setStudentFullName(studentInfo.getStudentName());
            contract.setStudentId(studentInfo.getStudentId());
            contract.setStudentName(studentInfo.getStudentName());
            contract.setStudentEmail(studentInfo.getEmail());
            contract.setStudentGpa(studentInfo.getCumStudentGPA());
            contract.setStudentMobile(studentInfo.getMobile());
            contract.setAcademicYear(studentInfo.getAcademicYear());
        }
    }

    /** True when the SIS returned a student record for the logged-in user. */
    public boolean isStudentInfoLoaded() {
        return studentInfo != null;
    }

    public ClearanceRequestContract getContract() {
        return contract;
    }

    public String submit() {
        if (!loginBean.isLoggedIn()) {
            addError("You must be logged in");
            return null;
        }
        if (studentInfo == null) {
            addError("Your student information could not be loaded - cannot submit");
            return null;
        }
        if (contract.getStudentFullName() == null || contract.getStudentFullName().isBlank()) {
            addError("Student full name is required");
            return null;
        }
        if (contract.getStudentId() == null || contract.getStudentId().isBlank()) {
            addError("Student ID is required");
            return null;
        }
        try {
            ProcessInstance pi = clearanceService.startClearance(
                    loginBean.getCurrentUser().getId(),
                    contract.toVariables());
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Clearance request started. Instance ID: " + pi.getId(), null));
            return "/dashboard?faces-redirect=true";
        } catch (SecurityException e) {
            addError(e.getMessage());
            return null;
        } catch (Exception e) {
            addError("Failed to start clearance process: " + e.getMessage());
            return null;
        }
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }
}