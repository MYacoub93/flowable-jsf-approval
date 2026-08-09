package com.example.approval.bean;

import com.example.approval.service.ApprovalService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;

/**
 * Backing bean for Manager / Finance approval forms.
 * Displays request data and allows Approve / Reject.
 */
@Named("taskBean")
@RequestScoped
@Component
public class TaskBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ApprovalService approvalService;

    private String taskId;
    private Task task;
    private Map<String, Object> variables;

    private String comments;
    private boolean decision; // true = approve

    @PostConstruct
    public void init() {
        // taskId can come from request parameter or flash
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        taskId = params.get("taskId");
        if (taskId == null) {
            Object flashTaskId = FacesContext.getCurrentInstance()
                    .getExternalContext().getFlash().get("taskId");
            if (flashTaskId != null) {
                taskId = flashTaskId.toString();
            }
        }
        if (taskId != null && loginBean.isLoggedIn()) {
            loadTask();
        }
    }

    private void loadTask() {
        task = approvalService.getTaskById(taskId);
        if (task != null) {
            variables = approvalService.getProcessVariables(task.getProcessInstanceId());
        }
    }

    public String approve() {
        return complete(true);
    }

    public String reject() {
        return complete(false);
    }

    private String complete(boolean approved) {
        if (!loginBean.isLoggedIn() || task == null) {
            addError("Invalid state");
            return null;
        }
        try {
            approvalService.completeApprovalTask(
                    taskId,
                    approved,
                    comments,
                    loginBean.getCurrentUser().getUsername());
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            approved ? "Request approved" : "Request rejected", null));
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            addError("Failed to complete task: " + e.getMessage());
            return null;
        }
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // Convenience getters for the form
    public String getTitle() {
        return variables != null ? (String) variables.get("title") : null;
    }

    public String getDescription() {
        return variables != null ? (String) variables.get("description") : null;
    }

    public Object getAmount() {
        return variables != null ? variables.get("amount") : null;
    }

    public String getDepartment() {
        return variables != null ? (String) variables.get("department") : null;
    }

    public String getInitiator() {
        return variables != null ? (String) variables.get("initiator") : null;
    }

    public String getPreviousComments() {
        return variables != null ? (String) variables.get("comments") : null;
    }

    public String getTaskName() {
        return task != null ? task.getName() : null;
    }

    // Getters / Setters

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Task getTask() {
        return task;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public boolean isDecision() {
        return decision;
    }

    public void setDecision(boolean decision) {
        this.decision = decision;
    }
}
