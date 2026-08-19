package com.example.approval.audit;

/**
 * Action types of the audit trail, mapped onto the numeric codes of the
 * pre-populated {@code BPM_ACTIONS} lookup table
 * ({@code ACTION_CODE NUMBER(4)} in {@code BPM_AUDIT_LOG_DTL}).
 *
 * <p>The application maps every workflow event it audits onto one of these
 * types; the actual numeric codes are defined once in
 * {@code BpmAuditConstants#ACTION_CODE_CASE_OPENED BpmAuditConstants} so
 * they can be aligned with the rows the DBA pre-populated in
 * {@code BPM_ACTIONS} without touching any other Java code.</p>
 */
public enum BpmAuditAction {

    /** Case opened / process instance created (master row in BPM_AUDIT_LOG). */
    CASE_OPENED(BpmAuditConstants.ACTION_CODE_CASE_OPENED, "Case opened (process instance created)"),
    /** A task was offered to an approver group. */
    TASK_ASSIGNED(BpmAuditConstants.ACTION_CODE_TASK_ASSIGNED, "Task assigned to approver group"),
    /** Approver completed the task with decision = approve. */
    APPROVED(BpmAuditConstants.ACTION_CODE_APPROVED, "Approved"),
    /** Approver cancelled the task with decision = reject. */
    REJECTED(BpmAuditConstants.ACTION_CODE_REJECTED, "Rejected"),
    /** Initiator amended a rejected request. */
    REQUEST_AMENDED(BpmAuditConstants.ACTION_CODE_REQUEST_AMENDED, "Request amended by initiator"),
    /** Multi-instance completion condition removed an open sibling task. */
    TASK_CANCELLED(BpmAuditConstants.ACTION_CODE_TASK_CANCELLED, "Task cancelled"),
    /** Process finished successfully. */
    CASE_FINISHED(BpmAuditConstants.ACTION_CODE_CASE_FINISHED, "Case finished successfully"),
    /** Result / FYI task created for a party. */
    FYI_CREATED(BpmAuditConstants.ACTION_CODE_FYI_CREATED, "FYI / result task created"),
    /** Result / FYI task acknowledged. */
    FYI_ACKNOWLEDGED(BpmAuditConstants.ACTION_CODE_FYI_ACKNOWLEDGED, "FYI / result task acknowledged"),
    /** Attachment uploaded to the case. */
    ATTACHMENT_UPLOADED(BpmAuditConstants.ACTION_CODE_ATTACHMENT_UPLOADED, "Attachment uploaded"),
    /** Anything else (departments resolved, generic process action). */
    GENERIC(BpmAuditConstants.ACTION_CODE_GENERIC, "Generic workflow action");

    private final int code;
    private final String defaultDescription;

    BpmAuditAction(int code, String defaultDescription) {
        this.code = code;
        this.defaultDescription = defaultDescription;
    }

    public int code() {
        return code;
    }

    public String defaultDescription() {
        return defaultDescription;
    }
}