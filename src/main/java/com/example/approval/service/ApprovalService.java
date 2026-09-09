package com.example.approval.service;

import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.idm.api.Group;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Core service for querying tasks / process instances of all deployed
 * process definitions, used by the JSF dashboard and task forms.
 *
 * Process starting lives in {@link ProcessStartService}; per-process task
 * completion lives in the process-specific services (e.g. ClearanceService,
 * StudentProofService).
 */
@Service("approvalService")
@Transactional
public class ApprovalService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final IdentityService identityService;

    public ApprovalService(RuntimeService runtimeService,
                           TaskService taskService,
                           IdentityService identityService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.identityService = identityService;
    }

    // -------------------------------------------------------------------------
    // Queries for JSF dashboard / forms
    // -------------------------------------------------------------------------

    /**
     * Tasks visible to the user: assigned directly OR offered to one of their
     * candidate groups (department / Finance / FYI group tasks are group
     * tasks until claimed).
     */
    public List<Task> getTasksForUser(String username) {
        List<String> groupIds = identityService.createGroupQuery()
                .groupMember(username)
                .list()
                .stream()
                .map(Group::getId)
                .toList();
        if (groupIds.isEmpty()) {
            return taskService.createTaskQuery()
                    .taskAssignee(username)
                    .orderByTaskCreateTime()
                    .desc()
                    .list();
        }
        return taskService.createTaskQuery()
                .or()
                .taskAssignee(username)
                .taskCandidateGroupIn(groupIds)
                .endOr()
                .orderByTaskCreateTime()
                .desc()
                .list();
    }

    public Task getTaskById(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    public List<ProcessInstance> getStartedByUser(String username) {
        return runtimeService.createProcessInstanceQuery()
                .startedBy(username)
                .orderByStartTime()
                .desc()
                .list();
    }

    public ProcessInstance getProcessInstance(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }
}