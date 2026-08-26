package com.example.approval.clearance;

import com.example.approval.audit.BpmAuditConstants;

import java.util.List;

/**
 * Central constants for the <b>Clearance Letter</b> process
 * (process key {@link #PROCESS_KEY}).
 *
 * <p>Group ids, default department catalogue, process variable names, audit
 * actions and stage codes all live here so the BPMN, the listeners and the
 * services share one vocabulary.</p>
 */
public final class ClearanceConstants {

    private ClearanceConstants() {
    }

    // ------------------------------------------------------------------
    // Process identity
    // ------------------------------------------------------------------

    /** Flowable process definition key. */
    public static final String PROCESS_KEY = "clearanceLetterProcess";

    /** Human readable process name used in emails / audit rows. */
    public static final String PROCESS_NAME = "Clearance Letter";

    /** Only users belonging to this candidate group may start the process. */
    public static final String INITIATOR_CANDIDATE_GROUP = "STD";

    // ------------------------------------------------------------------
    // Default department catalogue (group ids used as candidate groups)
    // ------------------------------------------------------------------

    public static final String DEPT_DEN = "DEN";
    public static final String DEPT_HOD = "HOD";
    public static final String DEPT_STUDENT_SERVICES = "STD_SRV";
    public static final String DEPT_WAREHOUSES = "WRH";
    public static final String DEPT_HEALTH_CARE = "HC";
    public static final String DEPT_LEGAL = "LGL";
    public static final String DEPT_STUDENT_DEANSHIP = "STD_DEN";
    public static final String DEPT_MEDIA_AND_TRAINING = "MEDTRN";
    public static final String DEPT_LIBRARY = "LIB";
    public static final String DEPT_IT = "IT";
    public static final String DEPT_ENGINEERING_AND_SERVICES = "ENG_SRV";

    /** Full default catalogue; the resolver may return all or a subset. */
    public static final List<String> ALL_DEPARTMENTS = List.of(
            DEPT_DEN,
            DEPT_HOD,
            DEPT_STUDENT_SERVICES,
            DEPT_WAREHOUSES,
            DEPT_HEALTH_CARE,
            DEPT_LEGAL,
            DEPT_STUDENT_DEANSHIP,
            DEPT_MEDIA_AND_TRAINING,
            DEPT_LIBRARY,
            DEPT_IT,
            DEPT_ENGINEERING_AND_SERVICES);

    // ------------------------------------------------------------------
    // Sequential / FYI approver groups (not part of the dynamic catalogue)
    // ------------------------------------------------------------------

    public static final String GROUP_FINANCE = "Finance Department";
    public static final String GROUP_ADMISSION_AND_REGISTRATION = "Admission and Registration Department";
    public static final String GROUP_INTERNAL_AUDIT = "Internal Audit Department";

    // ------------------------------------------------------------------
    // Process variables
    // ------------------------------------------------------------------

    /** Original process initiator (username), captured at start. */
    public static final String VAR_INITIATOR = "initiator";

    /** Resolved list of departments for the current approval round. */
    public static final String VAR_REQUIRED_DEPARTMENTS = "requiredDepartments";

    /** Multi-instance element variable: department of the current task instance. */
    public static final String VAR_DEPARTMENT = "department";

    /** "approve" | "reject" - set on every approval task completion. */
    public static final String VAR_DECISION = "decision";

    /** Free text comment set on every approval task completion. */
    public static final String VAR_COMMENT = "comment";

    /** Username that completed the task (set by ClearanceService). */
    public static final String VAR_COMPLETED_BY = "completedBy";

    /** true as soon as any parallel department rejected in the current round. */
    public static final String VAR_ANY_DEPARTMENT_REJECTED = "anyDepartmentRejected";

    /** Map<department, DepartmentDecision> of the current approval round. */
    public static final String VAR_DEPARTMENT_DECISIONS = "departmentDecisions";

    /** Free text the initiator added when amending a rejected request. */
    public static final String VAR_AMENDMENT_NOTES = "amendmentNotes";

    /** 1-based counter, incremented every time departments are (re)resolved. */
    public static final String VAR_APPROVAL_ROUND = "approvalRound";

    /** Final outcome variable, e.g. "APPROVED". */
    public static final String VAR_CLEARANCE_RESULT = "clearanceResult";

    /** Stage that produced the most recent rejection. */
    public static final String VAR_LAST_REJECTED_STAGE = "lastRejectedStage";

    /** Department that produced the most recent rejection (may be null). */
    public static final String VAR_LAST_REJECTED_DEPARTMENT = "lastRejectedDepartment";

    /** Comment of the most recent rejection, shown on the amendment form. */
    public static final String VAR_LAST_REJECTION_COMMENT = "lastRejectionComment";

    /** Optional contact email captured on the start form (notifications). */
    public static final String VAR_CONTACT_EMAIL = "contactEmail";

    // Business fields
    public static final String VAR_STUDENT_FULL_NAME = "studentFullName";
    public static final String VAR_STUDENT_ID = "studentId";
    public static final String VAR_PROGRAM = "program";
    public static final String VAR_NOTES = "notes";

    // ------------------------------------------------------------------
    // Decision values (shared with every other process)
    // ------------------------------------------------------------------

    public static final String DECISION_APPROVE = BpmAuditConstants.DECISION_APPROVE;
    public static final String DECISION_REJECT = BpmAuditConstants.DECISION_REJECT;

    // ------------------------------------------------------------------
    // Audit actions (process-agnostic keys - see BpmAuditConstants)
    // ------------------------------------------------------------------

    public static final String ACTION_PROCESS_STARTED = BpmAuditConstants.ACTION_PROCESS_STARTED;
    public static final String ACTION_DEPARTMENTS_RESOLVED = BpmAuditConstants.ACTION_DEPARTMENTS_RESOLVED;
    public static final String ACTION_TASK_ASSIGNED = BpmAuditConstants.ACTION_TASK_ASSIGNED;
    public static final String ACTION_APPROVED = BpmAuditConstants.ACTION_APPROVED;
    public static final String ACTION_REJECTED = BpmAuditConstants.ACTION_REJECTED;
    public static final String ACTION_REQUEST_AMENDED = BpmAuditConstants.ACTION_REQUEST_AMENDED;
    public static final String ACTION_TASK_CANCELLED = BpmAuditConstants.ACTION_TASK_CANCELLED;
    public static final String ACTION_PROCESS_COMPLETED = BpmAuditConstants.ACTION_PROCESS_COMPLETED;
    public static final String ACTION_FYI_CREATED = BpmAuditConstants.ACTION_FYI_CREATED;
    public static final String ACTION_FYI_ACKNOWLEDGED = BpmAuditConstants.ACTION_FYI_ACKNOWLEDGED;
    public static final String ACTION_RESULT_ACKNOWLEDGED = BpmAuditConstants.ACTION_RESULT_ACKNOWLEDGED;

    // ------------------------------------------------------------------
    // Stage codes (passed to the shared task/process handlers)
    // ------------------------------------------------------------------

    public static final String STAGE_DEPARTMENT_RESOLUTION = "DEPARTMENT_RESOLUTION";
    public static final String STAGE_DEPARTMENT_APPROVAL = "DEPARTMENT_APPROVAL";
    public static final String STAGE_FINANCE = "FINANCE";
    public static final String STAGE_ADMISSION_AND_REGISTRATION = "ADMISSION_AND_REGISTRATION";
    public static final String STAGE_AMENDMENT = "AMENDMENT";
    public static final String STAGE_INTERNAL_AUDIT = "INTERNAL_AUDIT";

    // ------------------------------------------------------------------
    // Task definition keys (used for stage mapping + JSF form routing)
    // ------------------------------------------------------------------

    public static final String TASK_DEPARTMENT_APPROVAL = "departmentApprovalTask";
    public static final String TASK_FINANCE_APPROVAL = "financeApprovalTask";
    public static final String TASK_ADMISSION_APPROVAL = "admissionApprovalTask";
    public static final String TASK_AMEND = "amendClearanceRequestTask";

    // ------------------------------------------------------------------
    // Categories stamped on the programmatically created FYI / result tasks
    // (they have no BPMN task definition key, so routing keys off the category)
    // ------------------------------------------------------------------

    public static final String CATEGORY_FYI = "CLEARANCE_FYI";
    public static final String CATEGORY_RESULT = "CLEARANCE_RESULT";

    /** Final result value written when the clearance is fully approved. */
    public static final String RESULT_APPROVED = "APPROVED";
}