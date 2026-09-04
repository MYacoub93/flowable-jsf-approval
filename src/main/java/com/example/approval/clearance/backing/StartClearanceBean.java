package com.example.approval.clearance.backing;

import com.example.approval.backing.BaseBackingBean;
import com.example.approval.backing.UserLoginBean;
import com.example.approval.clearance.ClearanceRequestContract;
import com.example.approval.clearance.service.ClearanceService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.Serializable;

/**
 * Backing bean for the "Start Clearance Letter" form. Only members of the
 * {@code STD} group may start the process (enforced again in
 * {@link ClearanceService#startClearance}).
 *
 * Pure Spring bean (like the other backing beans): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works.
 */
@Component("startClearanceBean")
@RequestScope
public class StartClearanceBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ClearanceService clearanceService;

    private final ClearanceRequestContract contract = new ClearanceRequestContract();

    public ClearanceRequestContract getContract() {
        return contract;
    }

    public String submit() {
        if (!loginBean.isLoggedIn()) {
            addError("You must be logged in");
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