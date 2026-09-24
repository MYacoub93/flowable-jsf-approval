package com.example.approval.processes.withdrawal.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One parallel approver's decision of the Semester Withdrawal process
 * ({@code semester-withdrawl}) - the mirror of the Clearance
 * {@code DepartmentDecision}. Kept in the {@code approvalResults} map
 * process variable keyed by the party id (dean / STD_AFF / LIB / Housing /
 * HC) so the student's amendment screen can show <b>who</b> approved,
 * <b>who</b> rejected and <b>why</b> (comment) - plus REG and FIN decisions
 * which are stored as plain variables.
 */
public class WithdrawalApprovalResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String party;
    private final String partyName;
    private final String candidateGroup;
    private final String decision;
    private final String completedBy;
    private final String comment;
    private final LocalDateTime decidedAt;

    public WithdrawalApprovalResult(String party,
                                    String partyName,
                                    String candidateGroup,
                                    String decision,
                                    String completedBy,
                                    String comment,
                                    LocalDateTime decidedAt) {
        this.party = party;
        this.partyName = partyName;
        this.candidateGroup = candidateGroup;
        this.decision = decision;
        this.completedBy = completedBy;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    public boolean isApproved() {
        return "approve".equalsIgnoreCase(decision);
    }

    public String getParty() {
        return party;
    }

    public String getPartyName() {
        return partyName;
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

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    @Override
    public String toString() {
        return "WithdrawalApprovalResult{party=" + party
                + ", decision=" + decision
                + ", completedBy=" + completedBy
                + ", comment=" + comment + "}";
    }
}
