package com.example.approval.clearance.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable record of one department's decision inside the parallel
 * multi-instance approval stage.
 *
 * <p>Instances are collected in the {@code departmentDecisions} process
 * variable (a {@code Map<department, DepartmentDecision>}) so the final
 * result, the amendment form and the audit trail can all show <i>who</i>
 * decided <i>what</i> and <i>when</i> - even after Flowable's own task
 * history has been compacted.</p>
 */
public class DepartmentDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String department;
    private final String candidateGroup;
    private final String decision;      // ClearanceConstants.DECISION_APPROVE / _REJECT
    private final String completedBy;
    private final String comment;
    private final LocalDateTime completedAt;
    private final int approvalRound;

    public DepartmentDecision(String department,
                              String candidateGroup,
                              String decision,
                              String completedBy,
                              String comment,
                              LocalDateTime completedAt,
                              int approvalRound) {
        this.department = department;
        this.candidateGroup = candidateGroup;
        this.decision = decision;
        this.completedBy = completedBy;
        this.comment = comment;
        this.completedAt = completedAt;
        this.approvalRound = approvalRound;
    }

    public boolean isApproved() {
        return "approve".equalsIgnoreCase(decision);
    }

    public String getDepartment() {
        return department;
    }

    public String getCandidateGroup() {
        return candidateGroup;
    }

    public String getDecision() {
        return decision;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public int getApprovalRound() {
        return approvalRound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DepartmentDecision that)) {
            return false;
        }
        return approvalRound == that.approvalRound
                && Objects.equals(department, that.department)
                && Objects.equals(completedBy, that.completedBy)
                && Objects.equals(completedAt, that.completedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(department, completedBy, completedAt, approvalRound);
    }

    @Override
    public String toString() {
        return "DepartmentDecision{department='" + department + '\'' +
                ", decision='" + decision + '\'' +
                ", completedBy='" + completedBy + '\'' +
                ", completedAt=" + completedAt +
                ", approvalRound=" + approvalRound + '}';
    }
}