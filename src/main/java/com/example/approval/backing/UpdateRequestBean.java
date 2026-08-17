package com.example.approval.backing;

import com.example.approval.service.ApprovalService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;

/**
 * Backing bean for the "Update Request" form shown to the initiator after a rejection.
 */
@Named("updateRequestBean")
@RequestScoped
@Component
public class UpdateRequestBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ApprovalService approvalService;

    private String taskId;
    private Task task;
    private Map<String, Object> variables;

    private String title;
    private String description;
    private Double amount;
    private String department;

    @PostConstruct
    public void init() {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        taskId = params.get("taskId");
        if (taskId == null) {
            Object flash = FacesContext.getCurrentInstance()
                    .getExternalContext().getFlash().get("taskId");
            if (flash != null) {
                taskId = flash.toString();
            }
        }
        if (taskId != null && loginBean.isLoggedIn()) {
            load();
        }
    }

    private void load() {
        task = approvalService.getTaskById(taskId);
        if (task != null) {
            variables = approvalService.getProcessVariables(task.getProcessInstanceId());
            title = (String) variables.get("title");
            description = (String) variables.get("description");
            Object amt = variables.get("amount");
            if (amt instanceof Number) {
                amount = ((Number) amt).doubleValue();
            }
            department = (String) variables.get("department");
        }
    }

    public String resubmit() {
        if (!loginBean.isLoggedIn() || task == null) {
            addError("Invalid state");
            return null;
        }
        try {
            approvalService.completeUpdateRequest(
                    taskId,
                    title,
                    description,
                    amount,
                    department,
                    loginBean.getCurrentUser().getFirstName());
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Request updated and resubmitted", null));
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            addError("Failed to resubmit: " + e.getMessage());
            return null;
        }
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    public String getRejectedBy() {
        return variables != null ? (String) variables.get("rejectedBy") : null;
    }

    public String getPreviousComments() {
        return variables != null ? (String) variables.get("comments") : null;
    }

    // Getters / Setters

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

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
