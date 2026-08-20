package com.example.approval.audit;

/**
 * Shared constants of the custom business audit subsystem that writes to the
 * Oracle {@code F_BPM_*} tables
 * ({@code F_BPM_AUDIT_LOG}, {@code F_BPM_AUDIT_LOG_DTL},
 * {@code F_BPM_CASE_ATTACHMENTS}).
 *
 * <p>{@code CASE_ID} is a {@code VARCHAR2(64)} that holds the Flowable
 * process instance id of the audited case - it is always supplied by the
 * caller and never generated (no sequence / serial involved).</p>
 */
public final class BpmAuditConstants {

    private BpmAuditConstants() {
    }

    /**
     * Max length of {@code CASE_ID VARCHAR2(64)} - the Flowable process
     * instance id passed in by the caller (used to truncate before insert).
     */
    public static final int CASE_ID_MAX_LENGTH = 64;

    // ------------------------------------------------------------------
    // Action codes (BPM_ACTIONS lookup - PRE-POPULATED table)
    // ------------------------------------------------------------------

    /**
     * {@code ACTION_CODE} values of the pre-populated {@code BPM_ACTIONS}
     * lookup table ({@code F_BPM_AUDIT_LOG_DTL.ACTION_CODE NUMBER(4)}).
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