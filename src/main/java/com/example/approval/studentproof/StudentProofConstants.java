package com.example.approval.studentproof;

import com.example.approval.audit.BpmAuditConstants;

/**
 * Constants of the <b>Student Proof Certificate Letter</b> process
 * ({@code studentProofCertificateProcess}) - mirrors the layout of
 * {@code ClearanceConstants} of the Clearance Letter process.
 *
 * <p>Flow: student (STD) starts the request -> Admission and Registration
 * employee processes it (mandatory note + document upload archived to
 * Alfresco/UCM) -> the document link is returned to the initiator through a
 * final student task.</p>
 */
public final class StudentProofConstants {

    private StudentProofConstants() {
    }

    // ------------------------------------------------------------------
    // Process
    // ------------------------------------------------------------------

    /** Flowable process definition key (BPMN id). */
    public static final String PROCESS_KEY = "studentProofCertificateProcess";

    /** Human-readable process name (notifications, audit texts). */
    public static final String PROCESS_NAME = "Student Proof Certificate Letter";

    /** Only members of the STD candidate group may start this process. */
    public static final String INITIATOR_CANDIDATE_GROUP = "STD";

    /**
     * Flowable candidate group (role code) of the Admission and Registration
     * department task, resolved from the external identity (FLOWABLE_USERS_VW).
     */
    public static final String GROUP_ADMISSION_AND_REGISTRATION = "ADR";

    // ------------------------------------------------------------------
    // Stages (audit free-text codes)
    // ------------------------------------------------------------------

    public static final String STAGE_ADMISSION_AND_REGISTRATION = "ADMISSION_AND_REGISTRATION";
    public static final String STAGE_STUDENT_DOCUMENT = "STUDENT_DOCUMENT";

    // ------------------------------------------------------------------
    // Task definition keys (BPMN userTask ids)
    // ------------------------------------------------------------------

    public static final String TASK_ADMISSION_PROCESSING = "admissionProcessingTask";
    public static final String TASK_STUDENT_DOCUMENT = "studentDocumentTask";

    // ------------------------------------------------------------------
    // Form keys (JSF views)
    // ------------------------------------------------------------------

    public static final String FORM_KEY_START = "start-student-proof";
    public static final String FORM_KEY_TASK = "student-proof-task";

    // ------------------------------------------------------------------
    // Process variables
    // ------------------------------------------------------------------

    /** Initiator username (set by ProcessStartService / start service). */
    public static final String VAR_INITIATOR = "initiator";

    /** Student information snapshot (loaded read-only from the SIS query). */
    public static final String VAR_STUDENT_ID = "studentId";
    public static final String VAR_STUDENT_NAME = "studentName";
    public static final String VAR_STUDENT_EMAIL = "studentEmail";
    public static final String VAR_STUDENT_GPA = "studentGpa";
    public static final String VAR_STUDENT_MOBILE = "studentMobile";
    public static final String VAR_CURRENT_SEMESTER = "currentSemester";

    /** Mandatory note entered by the student on the start form. */
    public static final String VAR_INITIATOR_NOTE = "initiatorNote";

    /** Mandatory note entered by the Admission and Registration employee. */
    public static final String VAR_AR_NOTE = "admissionRegistrationNote";

    /** Shared task-completion bookkeeping (same names as Clearance). */
    public static final String VAR_COMPLETED_BY = "completedBy";
    public static final String VAR_DECISION = "decision";

    /** Archived document (Alfresco/UCM) exposed to the initiator. */
    public static final String VAR_DOCUMENT_ID = "documentId";
    public static final String VAR_DOCUMENT_NAME = "documentName";
    public static final String VAR_DOCUMENT_URL = "documentUrl";
    public static final String VAR_DOCUMENT_MIME_TYPE = "documentMimeType";

    // ------------------------------------------------------------------
    // Shared audit vocabulary (reuse - never duplicate)
    // ------------------------------------------------------------------

    public static final String DECISION_APPROVE = BpmAuditConstants.DECISION_APPROVE;
    public static final String ACTION_TASK_ASSIGNED = BpmAuditConstants.ACTION_TASK_ASSIGNED;
    public static final String ACTION_RESULT_ACKNOWLEDGED = BpmAuditConstants.ACTION_RESULT_ACKNOWLEDGED;
    public static final String ACTION_ATTACHMENT_UPLOADED = BpmAuditConstants.ACTION_ATTACHMENT_UPLOADED;

    /** Document-type marker stored as metadata of the archived document. */
    public static final String DOCUMENT_TYPE = "STUDENT_PROOF_CERTIFICATE";
}