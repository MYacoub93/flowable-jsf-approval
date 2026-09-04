package com.example.approval.backing;

import com.example.approval.flowable.ApprovalRequestContract;
import com.example.approval.service.ApprovalService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.Serializable;
import java.util.Map;

/**
 * Backing bean for the "Update Request" form shown to the initiator after a rejection.
 *
 * The editable fields (title, description, amount, department) live on the
 * {@link ApprovalRequestContract} shared with {@code StartProcessBean}, so the
 * field-to-variable mapping exists in exactly one place: the contract loads them
 * from the process instance's variables via {@code fromVariables} and produces
 * them back via {@code toVariables}.
 *
 * Pure Spring bean (like DashboardBean/ProcessListBean): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works. Do NOT add CDI
 * annotations (@Named/@RequestScoped) — Weld would then create the instance and
 * skip Spring injection, leaving @Autowired fields null.
 */
@Component("updateRequestBean")
@RequestScope
public class UpdateRequestBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ApprovalService approvalService;

    private String taskId;
    private Task task;
    private Map<String, Object> variables;

    private final ApprovalRequestContract contract = new ApprovalRequestContract();

    public ApprovalRequestContract getContract() {
        return contract;
    }

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
            contract.fromVariables(variables);
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
                    contract.getTitle(),
                    contract.getDescription(),
                    contract.getAmount(),
                    contract.getDepartment(),
                    loginBean.getCurrentUser().getId());
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
}