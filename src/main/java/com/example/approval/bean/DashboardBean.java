package com.example.approval.bean;

import com.example.approval.service.ApprovalService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Request-scoped bean that loads the current user's tasks and started processes
 * for the dashboard page.
 */
@Named("dashboardBean")
@RequestScoped
@Component
public class DashboardBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ApprovalService approvalService;

    private List<Task> myTasks = Collections.emptyList();
    private List<ProcessInstance> myProcesses = Collections.emptyList();

    @PostConstruct
    public void init() {
        if (loginBean.isLoggedIn()) {
            String username = loginBean.getCurrentUser().getFirstName();
            myTasks = approvalService.getTasksForUser(username);
            myProcesses = approvalService.getStartedByUser(username);
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
}
