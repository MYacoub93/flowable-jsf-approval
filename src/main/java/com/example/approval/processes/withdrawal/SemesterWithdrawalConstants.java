package com.example.approval.processes.withdrawal;

import com.example.approval.audit.BpmAuditConstants;

/**
 * Constants of the <b>Semester Withdrawal</b> process
 * ({@code semester-withdrawl}) - mirrors the layout of
 * {@code ClearanceConstants} / {@code StudentProofConstants}.
 *
 * <p>Flow: student (STD) starts the request (reason + semester + note,
 * guarded by the SIS presubmit validation) -> Admission and Registration
 * (REG) approves -> parallel approvals (dynamic Dean of College, Student
 * Affairs, Library, Health Care and - female students only - Internal
 * Housing) behind a synchronization barrier -> any rejection routes to the
 * student Amendment task, all approvals route to FIN -> final REG FYI ->
 * SIS withdrawal procedure -> student result task. Approved parties are
 * tracked cumulatively so an amended request re-circulates only to the
 * parties that did not approve yet (Clearance resubmission pattern).</p>
 */
public final class SemesterWithdrawalConstants {

    private SemesterWithdrawalConstants() {
    }

    /** Flowable process definition key (BPMN id) - exact spelling required. */
    public static final String PROCESS_KEY = "semester-withdrawl";

    public static final String PROCESS_NAME = "Semester Withdrawal";

    /** Only members of the STD candidate group may start this process. */
    public static final String INITIATOR_CANDIDATE_GROUP = "STD";

    /** Candidate groups (role codes) of the approver stages. */
    public static final String GROUP_ADMISSION_AND_REGISTRATION = "REG";
    public static final String GROUP_STUDENT_AFFAIRS = "STD_AFF";
    public static final String GROUP_LIBRARY = "LIB";
    public static final String GROUP_INTERNAL_HOUSING = "Housing";
    public static final String GROUP_HEALTH_CARE = "HC";
    public static final String GROUP_FINANCE = "FIN";

    /** Dean of College parallel task: candidate group DEN + dynamic individual assignee. */
    public static final String GROUP_DEAN = "DEN";

    /** Standalone-task categories (Clearance CATEGORY_* convention). */
    public static final String CATEGORY_RESULT = "WITHDRAWAL_RESULT";
    public static final String CATEGORY_FYI = "WITHDRAWAL_FYI";

    /** Department labels used for audit + notification texts. */
    public static final String DEPT_DEAN = "Dean of College";
    public static final String DEPT_STUDENT_AFFAIRS = "Student Affairs";
    public static final String DEPT_LIBRARY = "Library";
    public static final String DEPT_INTERNAL_HOUSING = "Internal Housing";
    public static final String DEPT_HEALTH_CARE = "Health Care";
    public static final String DEPT_FINANCE = "Financial Department";
    public static final String DEPT_ADMISSION_AND_REGISTRATION = "Admission and Registration Department";

    // ------------------------------------------------------------------
    // Stages (audit free-text codes)
    // ------------------------------------------------------------------

    public static final String STAGE_ADMISSION_AND_REGISTRATION = "ADMISSION_AND_REGISTRATION";
    public static final String STAGE_PARALLEL_APPROVAL = "PARALLEL_APPROVAL";
    public static final String STAGE_AMENDMENT = "AMENDMENT";
    public static final String STAGE_FINANCE = "FINANCE";
    public static final String STAGE_REG_FYI = "REG_FINAL_FYI";
    public static final String STAGE_WITHDRAWAL_EXECUTION = "WITHDRAWAL_EXECUTION";
    public static final String STAGE_STUDENT_RESULT = "STUDENT_RESULT";

    // ------------------------------------------------------------------
    // Task definition keys (BPMN userTask ids)
    // ------------------------------------------------------------------

    public static final String TASK_REG_APPROVAL = "regApprovalTask";
    public static final String TASK_PARALLEL_APPROVAL = "parallelApprovalTask";
    public static final String TASK_AMENDMENT = "amendmentTask";
    public static final String TASK_FINANCE_APPROVAL = "financeApprovalTask";
    public static final String TASK_REG_FYI = "regFyiTask";
    public static final String TASK_STUDENT_RESULT = "studentResultTask";

    // ------------------------------------------------------------------
    // Form keys (JSF views)
    // ------------------------------------------------------------------

    public static final String FORM_KEY_START = "start-semester-withdrawl";
    public static final String FORM_KEY_TASK = "semester-withdrawl-task";

    // ------------------------------------------------------------------
    // Process variables
    // ------------------------------------------------------------------

    public static final String VAR_INITIATOR = "initiator";
    public static final String VAR_STUDENT_ID = "studentId";
    public static final String VAR_STUDENT_NAME = "studentName";
    public static final String VAR_STUDENT_GPA = "gpa";
    public static final String VAR_CURRENT_SEMESTER = "currentSemester";
    public static final String VAR_GENDER = "gender";
    public static final String VAR_FACULTY_NO = "facultyNo";
    public static final String VAR_CAMPUS_NO = "campusNo";
    public static final String VAR_WITHDRAWAL_REASON = "withdrawalReason";
    public static final String VAR_WITHDRAWAL_REASON_DESC = "withdrawalReasonDesc";
    public static final String VAR_WITHDRAWAL_SEMESTER = "withdrawalSemester";
    public static final String VAR_WITHDRAWAL_SEMESTER_DESC = "withdrawalSemesterDesc";
    public static final String VAR_STUDENT_NOTE = "studentNote";
    public static final String VAR_LANG = "lang";

    /**
     * Parallel multi-instance: approver group-id list (element variable
     * {@link #VAR_APPROVAL_GROUP} holds the current party's group id, e.g.
     * {@code DEN}, {@code STD_AFF}, {@code LIB}, {@code Housing}, {@code HC}).
     */
    public static final String VAR_PARALLEL_APPROVERS = "parallelApprovers";
    public static final String VAR_APPROVAL_GROUP = "approvalGroup";
    public static final String VAR_APPROVAL_RESULTS = "approvalResults";
    public static final String VAR_APPROVED_PARTIES = "approvedParties";
    public static final String VAR_APPROVAL_ROUND = "approvalRound";

    /** Shared task-completion bookkeeping (same names as Clearance). */
    public static final String VAR_COMPLETED_BY = "completedBy";
    public static final String VAR_DECISION = "decision";
    public static final String VAR_COMMENT = "comment";

    /** Aggregated approval results. */
    public static final String VAR_ANY_APPROVAL_REJECTED = "anyApprovalRejected";
    public static final String VAR_REG_DECISION = "regDecision";
    public static final String VAR_REG_COMMENT = "regComment";
    public static final String VAR_FIN_DECISION = "finDecision";
    public static final String VAR_FIN_COMMENT = "finComment";

    /** Amendment bookkeeping (Clearance VAR_LAST_REJECTED_* pattern). */
    public static final String VAR_LAST_REJECTED_STAGE = "lastRejectedStage";
    public static final String VAR_LAST_REJECTED_PARTY = "lastRejectedParty";
    public static final String VAR_LAST_REJECTION_COMMENT = "lastRejectionComment";
    public static final String VAR_AMENDMENT_NOTES = "amendmentNotes";

    /** Dynamic dean assignment (resolved via CommonMapper.getDeanOfCollage). */
    public static final String VAR_DEAN_USER = "deanUser";

    /** SIS withdrawal procedure response. */
    public static final String VAR_WITHDRAWAL_RESULT = "withdrawalResult";
    public static final String VAR_WITHDRAWAL_MESSAGE = "withdrawalMessage";

    // ------------------------------------------------------------------
    // Shared audit vocabulary (reuse - never duplicate)
    // ------------------------------------------------------------------

    public static final String DECISION_APPROVE = BpmAuditConstants.DECISION_APPROVE;
    public static final String DECISION_REJECT = BpmAuditConstants.DECISION_REJECT;
    public static final String ACTION_TASK_ASSIGNED = BpmAuditConstants.ACTION_TASK_ASSIGNED;
    public static final String ACTION_TASK_CANCELLED = BpmAuditConstants.ACTION_TASK_CANCELLED;
}