package com.example.approval.backing;

import com.example.approval.service.ApprovalService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Backing bean for the "Start Approval Process" form.
 */
@Named("startProcessBean")
@RequestScoped
@Component
public class StartProcessBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ApprovalService approvalService;

    private String title;
    private String description;
    private Double amount;
    private String department;

    public String submit() {
        if (!loginBean.isLoggedIn()) {
            addError("You must be logged in");
            return null;
        }
        if (title == null || title.isBlank()) {
            addError("Title is required");
            return null;
        }
        if (amount == null || amount <= 0) {
            addError("Amount must be positive");
            return null;
        }
        if (department == null || department.isBlank()) {
            addError("Department is required");
            return null;
        }

        try {
            ProcessInstance pi = approvalService.startProcess(
                    loginBean.getCurrentUser().getFirstName(),
                    title.trim(),
                    description != null ? description.trim() : "",
                    amount,
                    department.trim());

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

    // Getters / Setters

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
