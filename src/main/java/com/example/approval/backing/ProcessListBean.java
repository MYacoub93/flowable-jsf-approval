package com.example.approval.backing;

import com.example.approval.flowable.WorkflowManager;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Backs the "Processes" screen: lists every process definition the current
 * user is allowed to start and provides a per-row Start action.
 *
 * The single deployed process (approvalProcess) is started via the dedicated
 * Start Request form, so Start navigates there; for any future process without
 * a dedicated form we fall back to a generic start and return to the dashboard.
 */
@Component("processListBean")
@RequestScope
public class ProcessListBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The process key that has its own dedicated start form. */
    private static final String APPROVAL_PROCESS_KEY = "approvalProcess";

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private WorkflowManager workflowManager;

    private List<ProcessDefinition> availableProcesses = Collections.emptyList();

    @PostConstruct
    public void init() {
        if (loginBean.isLoggedIn()) {
            String username = loginBean.getCurrentUser().getId();
            availableProcesses = workflowManager.getProcessesUserCanStart(username);
        }
    }

    /**
     * Start a process definition chosen from the table.
     * - For the known approval process, redirect to the dedicated Start Request form.
     * - For any other process, navigate back to the processes list with an info message,
     *   since a generic start UI is not yet available.
     */
    public String start(String processDefinitionKey) {
        if (!loginBean.isLoggedIn()) {
            addError("You must be logged in to start a process.");
            return "/login?faces-redirect=true";
        }

        if (APPROVAL_PROCESS_KEY.equalsIgnoreCase(processDefinitionKey)) {
            return "/start-process?faces-redirect=true";
        }

        addInfo("No dedicated start form for process '" + processDefinitionKey
                + "'. Please configure a form first.");
        return null;
    }

    /** Number of available processes, used for the page badge/summary. */
    public int getAvailableProcessCount() {
        return availableProcesses.size();
    }

    public List<ProcessDefinition> getAvailableProcesses() {
        return availableProcesses;
    }

    public String goToDashboard() {
        return "/dashboard?faces-redirect=true";
    }

    public String logout() {
        return loginBean.logout();
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }
}