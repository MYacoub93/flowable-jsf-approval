package com.example.approval.backing;

import com.example.approval.flowable.ApprovalRequestContract;
import com.example.approval.service.ProcessStartService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.Serializable;

/**
 * Backing bean for the "Start Approval Process" form (approvalProcess).
 *
 * The form fields live on the {@link ApprovalRequestContract}, which owns the
 * field-to-variable mapping; starting the instance is delegated to the unified
 * {@link ProcessStartService}. This bean stays thin: validation, wiring and
 * navigation only.
 *
 * Pure Spring bean (like DashboardBean/ProcessListBean): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works. Do NOT add CDI
 * annotations (@Named/@RequestScoped) — Weld would then create the instance and
 * skip Spring injection, leaving @Autowired fields null.
 */
@Component("startProcessBean")
@RequestScope
public class StartProcessBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ProcessStartService processStartService;

    private final ApprovalRequestContract contract = new ApprovalRequestContract();

    public ApprovalRequestContract getContract() {
        return contract;
    }

    public String submit() {
        if (!loginBean.isLoggedIn()) {
            addError("You must be logged in");
            return null;
        }
        if (contract.getTitle() == null || contract.getTitle().isBlank()) {
            addError("Title is required");
            return null;
        }
        if (contract.getAmount() == null || contract.getAmount() <= 0) {
            addError("Amount must be positive");
            return null;
        }
        if (contract.getDepartment() == null || contract.getDepartment().isBlank()) {
            addError("Department is required");
            return null;
        }
        // Trim the editable fields before handing them to the contract
        contract.setTitle(contract.getTitle().trim());
        contract.setDepartment(contract.getDepartment().trim());
        contract.setDescription(
                contract.getDescription() != null ? contract.getDescription().trim() : null);

        try {
            ProcessInstance pi = processStartService.startProcess(
                    contract.getProcessDefinitionKey(),
                    loginBean.getCurrentUser().getId(),
                    contract.toVariables());

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Process started successfully. Instance ID: " + pi.getId(), null));
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            addError("Failed to start process: " + e.getMessage());
            return null;
        }
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }
}