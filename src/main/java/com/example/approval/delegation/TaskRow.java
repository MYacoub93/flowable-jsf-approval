package com.example.approval.delegation;

import java.io.Serializable;
import java.util.Date;

/**
 * One row of the active tasks table of the Administration &rarr; Task
 * Delegation page: an <b>active</b> Flowable task of the selected process
 * instance with its candidate groups joined for display.
 *
 * <p>Immutable view DTO assembled by {@link TaskDelegationService} - no
 * engine entity is ever exposed to the JSF layer directly (same convention
 * as {@code ProcessDefinitionRow} of the Process Management admin page).</p>
 */
public class TaskRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String assignee;
    private final String assigneeUsername;
    private final String candidateGroups;
    private final Date createTime;
    private final Date dueDate;
    private final String taskDefinitionKey;

    public TaskRow(String id, String name, String assignee, String candidateGroups,
                   Date createTime, Date dueDate, String taskDefinitionKey) {
        this(id, name, assignee, null, candidateGroups, createTime, dueDate,
                taskDefinitionKey);
    }

    public TaskRow(String id, String name, String assignee, String assigneeUsername,
                   String candidateGroups, Date createTime, Date dueDate,
                   String taskDefinitionKey) {
        this.id = id;
        this.name = name;
        this.assignee = assignee;
        this.assigneeUsername = assigneeUsername;
        this.candidateGroups = candidateGroups;
        this.createTime = createTime;
        this.dueDate = dueDate;
        this.taskDefinitionKey = taskDefinitionKey;
    }

    /** Copy of this row with the assignee's username resolved. */
    public TaskRow withAssigneeUsername(String username) {
        return new TaskRow(id, name, assignee, username, candidateGroups,
                createTime, dueDate, taskDefinitionKey);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAssignee() {
        return assignee;
    }

    /** Username of the assignee resolved from FLOWABLE_USERS_VW (may be null). */
    public String getAssigneeUsername() {
        return assigneeUsername;
    }

    /**
     * Assignee for display: {@code username (id)} when the username could be
     * resolved from {@code FLOWABLE_USERS_VW}, the bare id otherwise, null
     * when the task is unassigned.
     */
    public String getAssigneeDisplay() {
        if (assignee == null || assignee.isBlank()) {
            return null;
        }
        if (assigneeUsername != null && !assigneeUsername.isBlank()) {
            return assigneeUsername + " (" + assignee + ")";
        }
        return assignee;
    }

    public String getCandidateGroups() {
        return candidateGroups;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public String getTaskDefinitionKey() {
        return taskDefinitionKey;
    }
}