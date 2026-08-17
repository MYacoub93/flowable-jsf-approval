package com.example.approval.backing;

import com.example.approval.flowable.WorkflowManager;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.engine.FormService;
import org.flowable.engine.RepositoryService;
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
 * Start routing is entirely driven by the {@code flowable:formKey} declared on
 * the process definition's start event: if a form key is present, the user is
 * redirected to {@code /<formKey>.xhtml}; otherwise an info message is shown.
 * No process-key-specific branching lives here, so new processes with their
 * own forms are picked up automatically.
 */
@Component("processListBean")
@RequestScope
public class ProcessListBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private WorkflowManager workflowManager;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private FormService formService;

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
     * Looks up the latest version of the definition and routes to the JSF view
     * named by its {@code flowable:formKey} (e.g. form key {@code start-process}
     * redirects to {@code /start-process.xhtml}). Definitions without a form key
     * get the generic fallback message.
     */
    public String start(String processDefinitionKey) {
        if (!loginBean.isLoggedIn()) {
            addError("You must be logged in to start a process.");
            return "/login?faces-redirect=true";
        }

        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionKey)
                .latestVersion()
                .singleResult();
        if (definition == null) {
            addError("Process definition not found: " + processDefinitionKey);
            return null;
        }

        String formKey = formService.getStartFormKey(definition.getId());
        if (formKey == null || formKey.isBlank()) {
            addInfo("No dedicated start form for process '" + processDefinitionKey
                    + "'. Please configure a form first.");
            return null;
        }

        return "/" + formKey + "?faces-redirect=true";
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