package com.example.approval.mycases;

import java.io.Serializable;
import java.util.Date;

/**
 * One row of the <b>Active Tasks</b> table of the My Cases details dialog:
 * an active ({@code TaskQuery.active()}) task of the selected process
 * instance. Completed tasks are never mapped to this DTO.
 *
 * <p>Immutable view DTO assembled by {@link MyCasesService}.</p>
 */
public class CaseTaskRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String assignee;
    private final String assigneeDisplay;
    private final Date claimTime;
    private final Date createTime;
    private final Date dueDate;
    private final String candidateGroups;

    public CaseTaskRow(String id, String name, String assignee, String assigneeDisplay,
                       Date claimTime, Date createTime, Date dueDate,
                       String candidateGroups) {
        this.id = id;
        this.name = name;
        this.assignee = assignee;
        this.assigneeDisplay = assigneeDisplay;
        this.claimTime = claimTime;
        this.createTime = createTime;
        this.dueDate = dueDate;
        this.candidateGroups = candidateGroups;
    }

    public CaseTaskRow withAssigneeDisplay(String display) {
        return new CaseTaskRow(id, name, assignee, display, claimTime,
                createTime, dueDate, candidateGroups);
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

    /** {@code username (id)} or the bare id; null when unassigned. */
    public String getAssigneeDisplay() {
        return assigneeDisplay == null || assigneeDisplay.isBlank()
                ? assignee : assigneeDisplay;
    }

    /** Date the task was assigned/claimed, or null (creation date fallback). */
    public Date getClaimTime() {
        return claimTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Date getDueDate() {
        return dueDate;
    }

    /** Comma-joined candidate group ids or null when none. */
    public String getCandidateGroups() {
        return candidateGroups;
    }
}