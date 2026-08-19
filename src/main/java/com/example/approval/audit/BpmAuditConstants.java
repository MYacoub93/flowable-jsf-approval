package com.example.approval.audit;

/**
 * Shared constants of the custom business audit subsystem that writes to the
 * pre-existing Oracle {@code BPM_*} tables
 * ({@code BPM_AUDIT_LOG}, {@code BPM_AUDIT_LOG_DTL}, {@code BPM_CASE_ATTACHMENTS}).
 *
 * <p>The Oracle schema has no {@code PROCESS_INSTANCE_ID} column anywhere, so
 * the link between a Flowable process instance and its numeric business
 * {@code CASE_ID} is kept as a process variable named
 * {@link #VAR_CASE_ID}.</p>
 */
public final class BpmAuditConstants {

    private BpmAuditConstants() {
    }

    /**
     * Flowable process variable holding the numeric {@code BPM_AUDIT_LOG.CASE_ID}
     * of the process instance (set when the case is opened).
     */
    public static final String VAR_CASE_ID = "bpmCaseId";

    // ------------------------------------------------------------------
    // Action codes (BPM_ACTIONS lookup - PRE-POPULATED table)
    // ------------------------------------------------------------------

    /**
     * {@code ACTION_CODE} values of the pre-populated {@code BPM_ACTIONS}
     * lookup table ({@code BPM_AUDIT_LOG_DTL.ACTION_CODE NUMBER(4)}).
     *
     * <p>The lookup table already ships with the DBA's own rows, so this
     * block is the single place to align the application with the codes
     * production actually contains - every {@code ACTION_CODE} written by
     * the audit subsystem is sourced from here (the semantic grouping lives
     * in {@link BpmAuditAction}).</p>
     */
    public static final int ACTION_CODE_UNSPECIFIED = 0;
    /** Case opened / process instance created. */
    public static final int ACTION_CODE_CASE_OPENED = 1;
    /** A task was offered to an approver group. */
    public static final int ACTION_CODE_TASK_ASSIGNED = 2;
    /** Approver completed the task with decision = approve. */
    public static final int ACTION_CODE_APPROVED = 3;
    /** Approver completed the task with decision = reject. */
    public static final int ACTION_CODE_REJECTED = 4;
    /** Initiator amended a rejected request. */
    public static final int ACTION_CODE_REQUEST_AMENDED = 5;
    /** Multi-instance completion condition removed an open sibling task. */
    public static final int ACTION_CODE_TASK_CANCELLED = 6;
    /** Process finished successfully. */
    public static final int ACTION_CODE_CASE_FINISHED = 7;
    /** Result / FYI task created for a party. */
    public static final int ACTION_CODE_FYI_CREATED = 8;
    /** Result / FYI task acknowledged. */
    public static final int ACTION_CODE_FYI_ACKNOWLEDGED = 9;
    /** Attachment uploaded to the case. */
    public static final int ACTION_CODE_ATTACHMENT_UPLOADED = 10;
    /** Anything else (departments resolved, generic process action). */
    public static final int ACTION_CODE_GENERIC = 99;

    // ------------------------------------------------------------------
    // Column sizes from the Oracle DDL - used to truncate before insert
    // ------------------------------------------------------------------

    public static final int NOTE_MAX_LENGTH = 500;
    public static final int CONTENT_ID_MAX_LENGTH = 1000;
    public static final int CONTENT_NAME_MAX_LENGTH = 1000;
    public static final int TERMINAL_MAX_LENGTH_DTL = 100;
    public static final int TERMINAL_MAX_LENGTH_ATTACHMENT = 30;
    public static final int OS_USER_MAX_LENGTH_ATTACHMENT = 30;
}