package com.example.approval.clearance.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One row of the clearance audit trail.
 *
 * <p>Written exclusively by {@code AuditService} - never directly by the
 * BPMN - with stable action codes (TASK_ASSIGNED, APPROVED, REJECTED,
 * PROCESS_COMPLETED, ...) so the trail can be queried per process instance,
 * per department and per actor.</p>
 */
public class AuditRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String processInstanceId;
    private String processName;
    private String action;
    private String stage;
    private String department;
    private String user;          // who performed / triggered the action
    private String initiator;     // original process initiator
    private String taskId;
    private String details;
    private LocalDateTime timestamp;

    public AuditRecord() {
    }

    public AuditRecord(String processInstanceId, String processName, String action, String stage,
                       String department, String user, String initiator, String taskId,
                       String details, LocalDateTime timestamp) {
        this.processInstanceId = processInstanceId;
        this.processName = processName;
        this.action = action;
        this.stage = stage;
        this.department = department;
        this.user = user;
        this.initiator = initiator;
        this.taskId = taskId;
        this.details = details;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getInitiator() {
        return initiator;
    }

    public void setInitiator(String initiator) {
        this.initiator = initiator;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditRecord)) {
            return false;
        }
        AuditRecord that = (AuditRecord) o;
        return Objects.equals(id, that.id)
                && Objects.equals(processInstanceId, that.processInstanceId)
                && Objects.equals(action, that.action)
                && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, processInstanceId, action, timestamp);
    }

    @Override
    public String toString() {
        return "AuditRecord{id=" + id
                + ", processInstanceId='" + processInstanceId + '\''
                + ", action='" + action + '\''
                + ", stage='" + stage + '\''
                + ", department='" + department + '\''
                + ", user='" + user + '\''
                + ", timestamp=" + timestamp + '}';
    }
}