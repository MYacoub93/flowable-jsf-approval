package com.example.approval.backing;

import com.example.approval.flowable.WorkflowManager;
import com.example.approval.service.ApprovalService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Request-scoped bean that loads the current user's tasks, started processes and
 * the list of process definitions they are allowed to start, for the dashboard page.
 */
@Component("dashboardBean")
@RequestScope
public class DashboardBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private WorkflowManager workflowManager;

    private List<Task> myTasks = Collections.emptyList();
    private List<ProcessInstance> myProcesses = Collections.emptyList();
    private List<ProcessDefinition> availableProcesses = Collections.emptyList();

    @PostConstruct
    public void init() {
        if (loginBean.isLoggedIn()) {
            String username = loginBean.getCurrentUser().getId();
            myTasks = approvalService.getTasksForUser(username);
            myProcesses = approvalService.getStartedByUser(username);
            availableProcesses = workflowManager.getProcessesUserCanStart(username);
        }
    }

    public String openTask(String taskId) {
        // Navigate to the appropriate form based on task definition key
        Task task = approvalService.getTaskById(taskId);
        if (task == null) {
            return null;
        }
        FacesContext.getCurrentInstance().getExternalContext()
                .getFlash().put("taskId", taskId);

        if ("updateRequestTask".equals(task.getTaskDefinitionKey())) {
            return "/update-request?faces-redirect=true&taskId=" + taskId;
        }
        // managerApprovalTask or financeApprovalTask
        return "/task-form?faces-redirect=true&taskId=" + taskId;
    }

    // Getters

    public List<Task> getMyTasks() {
        return myTasks;
    }

    public List<ProcessInstance> getMyProcesses() {
        return myProcesses;
    }

    public List<ProcessDefinition> getAvailableProcesses() {
        return availableProcesses;
    }

    /** Number of tasks currently assigned to the logged-in user. */
    public int getTaskCount() {
        return myTasks.size();
    }

    /** Number of process definitions the logged-in user is allowed to start. */
    public int getAvailableProcessCount() {
        return availableProcesses.size();
    }

    /** Number of process instances started by the logged-in user. */
    public int getStartedProcessCount() {
        return myProcesses.size();
    }

    /** Welcome banner text shown on the dashboard. */
    public String getWelcomeMessage() {
        if (!loginBean.isLoggedIn() || loginBean.getCurrentUser() == null) {
            return "Welcome";
        }
        return "Welcome, " + loginBean.getCurrentUser().getFirstName();
    }

    // Navigation ------------------------------------------------------------

    public String goToProcesses() {
        return "/processes?faces-redirect=true";
    }

    public String goToStartProcess() {
        return "/start-process?faces-redirect=true";
    }

    public String goToDashboard() {
        return "/dashboard?faces-redirect=true";
    }

    public String logout() {
        return loginBean.logout();
    }
}
