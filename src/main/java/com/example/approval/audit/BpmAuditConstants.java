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
    // Shared workflow vocabulary (process-agnostic)
    // ------------------------------------------------------------------

    /**
     * Decision values written by <b>every</b> approval task of <b>every</b>
     * process into the {@code decision} process variable. Shared so the
     * {@code BPM_ACTIONS} mapping of {@code BpmAuditServiceImpl} stays
     * uniform across processes.
     */
    public static final String DECISION_APPROVE = "approve";
    public static final String DECISION_REJECT = "reject";

    /**
     * Semantic action keys of the workflow audit trail, used by
     * <b>all</b> processes (not just one specific process). Any listener,
     * delegate, JSF bean or REST controller passes these keys to
     * {@code BpmAuditService.logProcessAction(...)}; the implementation
     * maps them onto the pre-populated {@code BPM_ACTIONS} codes below.
     */
    public static final String ACTION_PROCESS_STARTED = "PROCESS_STARTED";
    public static final String ACTION_DEPARTMENTS_RESOLVED = "DEPARTMENTS_RESOLVED";
    public static final String ACTION_TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String ACTION_APPROVED = "APPROVED";
    public static final String ACTION_REJECTED = "REJECTED";
    public static final String ACTION_REQUEST_AMENDED = "REQUEST_AMENDED";
    public static final String ACTION_TASK_CANCELLED = "TASK_CANCELLED";
    public static final String ACTION_PROCESS_COMPLETED = "PROCESS_COMPLETED";
    public static final String ACTION_FYI_CREATED = "FYI_CREATED";
    public static final String ACTION_FYI_ACKNOWLEDGED = "FYI_ACKNOWLEDGED";
    public static final String ACTION_RESULT_ACKNOWLEDGED = "RESULT_ACKNOWLEDGED";

    /**
     * Attachment uploads have no dedicated {@code BPM_ACTIONS} row - the
     * implementation maps this key onto {@code ENTERED} (0) and the note
     * column carries the file info.
     */
    public static final String ACTION_ATTACHMENT_UPLOADED = "ATTACHMENT_UPLOADED";

    // ------------------------------------------------------------------
    // Action codes (BPM_ACTIONS lookup - PRE-POPULATED table, codes 0-163)
    // ------------------------------------------------------------------

    /**
     * {@code ACTION_CODE} values of the pre-populated {@code BPM_ACTIONS}
     * lookup table ({@code F_BPM_AUDIT_LOG_DTL.ACTION_CODE NUMBER(4)}),
     * mirroring the DBA's rows one-to-one (see {@code docs/bpm_actions.htm}).
     *
     * <p>Layout of the table:</p>
     * <ul>
     *   <li>0 - case entered; 1-4 - applicant actions;</li>
     *   <li>5-124 - per department / approver role triplets
     *       {@code Approval / Rejection / Review} (three special codes
     *       104, 108, 121 are interleaved);</li>
     *   <li>125-163 - per department {@code Task Received}
     *       (استلام مهمة) codes.</li>
     * </ul>
     *
     * <p>This block is the single place to align the application with the
     * codes production actually contains - every {@code ACTION_CODE} written
     * by the audit subsystem is sourced from here (the semantic grouping and
     * the department-name resolution live in {@link BpmAuditAction}).</p>
     */
    public static final int ACTION_CODE_ENTERED = 0;

    // --- applicant (1-4) ---
    public static final int ACTION_CODE_APPLICANT_CONTINUATION = 1;
    public static final int ACTION_CODE_APPLICANT_APPROVAL = 2;
    public static final int ACTION_CODE_APPLICANT_REJECTION = 3;
    public static final int ACTION_CODE_APPLICANT_VIEWED_FINAL_RESULT = 4;

    // --- Head Of Department (5-7) ---
    public static final int ACTION_CODE_HEAD_OF_DEPARTMENT_APPROVAL = 5;
    public static final int ACTION_CODE_HEAD_OF_DEPARTMENT_REJECTION = 6;
    public static final int ACTION_CODE_HEAD_OF_DEPARTMENT_REVIEW = 7;

    // --- Dean Of College (8-10) ---
    public static final int ACTION_CODE_DEAN_OF_COLLEGE_APPROVAL = 8;
    public static final int ACTION_CODE_DEAN_OF_COLLEGE_REJECTION = 9;
    public static final int ACTION_CODE_DEAN_OF_COLLEGE_REVIEW = 10;

    // --- Registration Dept (11-13) ---
    public static final int ACTION_CODE_REGISTRATION_DEPT_APPROVAL = 11;
    public static final int ACTION_CODE_REGISTRATION_DEPT_REJECTION = 12;
    public static final int ACTION_CODE_REGISTRATION_DEPT_REVIEW = 13;

    // --- Financial Department (14-16) ---
    public static final int ACTION_CODE_FINANCIAL_DEPARTMENT_APPROVAL = 14;
    public static final int ACTION_CODE_FINANCIAL_DEPARTMENT_REJECTION = 15;
    public static final int ACTION_CODE_FINANCIAL_DEPARTMENT_REVIEW = 16;

    // --- Loan Committee (17-19) ---
    public static final int ACTION_CODE_LOAN_COMMITTEE_APPROVAL = 17;
    public static final int ACTION_CODE_LOAN_COMMITTEE_REJECTION = 18;
    public static final int ACTION_CODE_LOAN_COMMITTEE_REVIEW = 19;

    // --- President (20-22) ---
    public static final int ACTION_CODE_PRESIDENT_APPROVAL = 20;
    public static final int ACTION_CODE_PRESIDENT_REJECTION = 21;
    public static final int ACTION_CODE_PRESIDENT_REVIEW = 22;

    // --- Internal Audit (23-25) ---
    public static final int ACTION_CODE_INTERNAL_AUDIT_APPROVAL = 23;
    public static final int ACTION_CODE_INTERNAL_AUDIT_REJECTION = 24;
    public static final int ACTION_CODE_INTERNAL_AUDIT_REVIEW = 25;

    // --- HR Employee (26-28) ---
    public static final int ACTION_CODE_HR_EMPLOYEE_APPROVAL = 26;
    public static final int ACTION_CODE_HR_EMPLOYEE_REJECTION = 27;
    public static final int ACTION_CODE_HR_EMPLOYEE_REVIEW = 28;

    // --- HR Manager (29-31) ---
    public static final int ACTION_CODE_HR_MANAGER_APPROVAL = 29;
    public static final int ACTION_CODE_HR_MANAGER_REJECTION = 30;
    public static final int ACTION_CODE_HR_MANAGER_REVIEW = 31;

    // --- Direct Manager (32-34) ---
    public static final int ACTION_CODE_DIRECT_MANAGER_APPROVAL = 32;
    public static final int ACTION_CODE_DIRECT_MANAGER_REJECTION = 33;
    public static final int ACTION_CODE_DIRECT_MANAGER_REVIEW = 34;

    // --- Health Care (35-37) ---
    public static final int ACTION_CODE_HEALTH_CARE_APPROVAL = 35;
    public static final int ACTION_CODE_HEALTH_CARE_REJECTION = 36;
    public static final int ACTION_CODE_HEALTH_CARE_REVIEW = 37;

    // --- Maintenance HOS (38-40) ---
    public static final int ACTION_CODE_MAINTENANCE_HOS_APPROVAL = 38;
    public static final int ACTION_CODE_MAINTENANCE_HOS_REJECTION = 39;
    public static final int ACTION_CODE_MAINTENANCE_HOS_REVIEW = 40;

    // --- Maintenance Employee (41-43) ---
    public static final int ACTION_CODE_MAINTENANCE_EMPLOYEE_APPROVAL = 41;
    public static final int ACTION_CODE_MAINTENANCE_EMPLOYEE_REJECTION = 42;
    public static final int ACTION_CODE_MAINTENANCE_EMPLOYEE_REVIEW = 43;

    // --- Library (44-46) ---
    public static final int ACTION_CODE_LIBRARY_APPROVAL = 44;
    public static final int ACTION_CODE_LIBRARY_REJECTION = 45;
    public static final int ACTION_CODE_LIBRARY_REVIEW = 46;

    // --- Warehouse (47-49) ---
    public static final int ACTION_CODE_WAREHOUSE_APPROVAL = 47;
    public static final int ACTION_CODE_WAREHOUSE_REJECTION = 48;
    public static final int ACTION_CODE_WAREHOUSE_REVIEW = 49;

    // --- IT Department (50-52) ---
    public static final int ACTION_CODE_IT_DEPARTMENT_APPROVAL = 50;
    public static final int ACTION_CODE_IT_DEPARTMENT_REJECTION = 51;
    public static final int ACTION_CODE_IT_DEPARTMENT_REVIEW = 52;

    // --- PR Department (53-55) ---
    public static final int ACTION_CODE_PR_DEPARTMENT_APPROVAL = 53;
    public static final int ACTION_CODE_PR_DEPARTMENT_REJECTION = 54;
    public static final int ACTION_CODE_PR_DEPARTMENT_REVIEW = 55;

    // --- Training Department (56-58) ---
    public static final int ACTION_CODE_TRAINING_DEPARTMENT_APPROVAL = 56;
    public static final int ACTION_CODE_TRAINING_DEPARTMENT_REJECTION = 57;
    public static final int ACTION_CODE_TRAINING_DEPARTMENT_REVIEW = 58;

    // --- Consulting Department (59-61) ---
    public static final int ACTION_CODE_CONSULTING_DEPARTMENT_APPROVAL = 59;
    public static final int ACTION_CODE_CONSULTING_DEPARTMENT_REJECTION = 60;
    public static final int ACTION_CODE_CONSULTING_DEPARTMENT_REVIEW = 61;

    // --- Dean Of Accreditation Quality (62-64) ---
    public static final int ACTION_CODE_DEAN_OF_ACCREDITATION_QUALITY_APPROVAL = 62;
    public static final int ACTION_CODE_DEAN_OF_ACCREDITATION_QUALITY_REJECTION = 63;
    public static final int ACTION_CODE_DEAN_OF_ACCREDITATION_QUALITY_REVIEW = 64;

    // --- Academic Affairs (65-67) ---
    public static final int ACTION_CODE_ACADEMIC_AFFAIRS_APPROVAL = 65;
    public static final int ACTION_CODE_ACADEMIC_AFFAIRS_REJECTION = 66;
    public static final int ACTION_CODE_ACADEMIC_AFFAIRS_REVIEW = 67;

    // --- Administrative Affairs (68-70) ---
    public static final int ACTION_CODE_ADMINISTRATIVE_AFFAIRS_APPROVAL = 68;
    public static final int ACTION_CODE_ADMINISTRATIVE_AFFAIRS_REJECTION = 69;
    public static final int ACTION_CODE_ADMINISTRATIVE_AFFAIRS_REVIEW = 70;

    // --- Academic Counselor (71-73) ---
    public static final int ACTION_CODE_ACADEMIC_COUNSELOR_APPROVAL = 71;
    public static final int ACTION_CODE_ACADEMIC_COUNSELOR_REJECTION = 72;
    public static final int ACTION_CODE_ACADEMIC_COUNSELOR_REVIEW = 73;

    // --- Students Services (74-76) ---
    public static final int ACTION_CODE_STUDENTS_SERVICES_APPROVAL = 74;
    public static final int ACTION_CODE_STUDENTS_SERVICES_REJECTION = 75;
    public static final int ACTION_CODE_STUDENTS_SERVICES_REVIEW = 76;

    // --- Delegation Authority (77-79) ---
    public static final int ACTION_CODE_DELEGATION_AUTHORITY_APPROVAL = 77;
    public static final int ACTION_CODE_DELEGATION_AUTHORITY_REJECTION = 78;
    public static final int ACTION_CODE_DELEGATION_AUTHORITY_REVIEW = 79;

    // --- Student Deanship (80-82) ---
    public static final int ACTION_CODE_STUDENT_DEANSHIP_APPROVAL = 80;
    public static final int ACTION_CODE_STUDENT_DEANSHIP_REJECTION = 81;
    public static final int ACTION_CODE_STUDENT_DEANSHIP_REVIEW = 82;

    // --- Purchasing (83-85; table spells it "Purshasing") ---
    public static final int ACTION_CODE_PURCHASING_APPROVAL = 83;
    public static final int ACTION_CODE_PURCHASING_REJECTION = 84;
    public static final int ACTION_CODE_PURCHASING_REVIEW = 85;

    // --- Financial Manager (86-88) ---
    public static final int ACTION_CODE_FINANCIAL_MANAGER_APPROVAL = 86;
    public static final int ACTION_CODE_FINANCIAL_MANAGER_REJECTION = 87;
    public static final int ACTION_CODE_FINANCIAL_MANAGER_REVIEW = 88;

    // --- Accreditation Committee (89-91) ---
    public static final int ACTION_CODE_ACCREDITATION_COMMITTEE_APPROVAL = 89;
    public static final int ACTION_CODE_ACCREDITATION_COMMITTEE_REJECTION = 90;
    public static final int ACTION_CODE_ACCREDITATION_COMMITTEE_REVIEW = 91;

    // --- Central Committee (92-94) ---
    public static final int ACTION_CODE_CENTRAL_COMMITTEE_APPROVAL = 92;
    public static final int ACTION_CODE_CENTRAL_COMMITTEE_REJECTION = 93;
    public static final int ACTION_CODE_CENTRAL_COMMITTEE_REVIEW = 94;

    // --- Security Center (95-97) ---
    public static final int ACTION_CODE_SECURITY_CENTER_APPROVAL = 95;
    public static final int ACTION_CODE_SECURITY_CENTER_REJECTION = 96;
    public static final int ACTION_CODE_SECURITY_CENTER_REVIEW = 97;

    // --- Service Department (98-100) ---
    public static final int ACTION_CODE_SERVICE_DEPARTMENT_APPROVAL = 98;
    public static final int ACTION_CODE_SERVICE_DEPARTMENT_REJECTION = 99;
    public static final int ACTION_CODE_SERVICE_DEPARTMENT_REVIEW = 100;

    // --- Dean Of PostGraduate (101-103) ---
    public static final int ACTION_CODE_DEAN_OF_POSTGRADUATE_APPROVAL = 101;
    public static final int ACTION_CODE_DEAN_OF_POSTGRADUATE_REJECTION = 102;
    public static final int ACTION_CODE_DEAN_OF_POSTGRADUATE_REVIEW = 103;

    /** Special code (104): service payment claim failed. */
    public static final int ACTION_CODE_SERVICE_PAYMENT_CLAIM_FAILED = 104;

    // --- Dean Of Higher Education (105-107) ---
    public static final int ACTION_CODE_DEAN_OF_HIGHER_EDUCATION_APPROVAL = 105;
    public static final int ACTION_CODE_DEAN_OF_HIGHER_EDUCATION_REJECTION = 106;
    public static final int ACTION_CODE_DEAN_OF_HIGHER_EDUCATION_REVIEW = 107;

    /** Special code (108): transaction service failure. */
    public static final int ACTION_CODE_TRANSACTION_SERVICE_FAILURE = 108;

    // --- Loans And Grants (109-111) ---
    public static final int ACTION_CODE_LOANS_AND_GRANTS_APPROVAL = 109;
    public static final int ACTION_CODE_LOANS_AND_GRANTS_REJECTION = 110;
    public static final int ACTION_CODE_LOANS_AND_GRANTS_REVIEW = 111;

    // --- Cashier (112-114) ---
    public static final int ACTION_CODE_CASHIER_APPROVAL = 112;
    public static final int ACTION_CODE_CASHIER_REJECTION = 113;
    public static final int ACTION_CODE_CASHIER_REVIEW = 114;

    // --- Legal Department (115-117) ---
    public static final int ACTION_CODE_LEGAL_DEPARTMENT_APPROVAL = 115;
    public static final int ACTION_CODE_LEGAL_DEPARTMENT_REJECTION = 116;
    public static final int ACTION_CODE_LEGAL_DEPARTMENT_REVIEW = 117;

    // --- Foreign Students Office (118-120) ---
    public static final int ACTION_CODE_FOREIGN_STUDENTS_OFFICE_APPROVAL = 118;
    public static final int ACTION_CODE_FOREIGN_STUDENTS_OFFICE_REJECTION = 119;
    public static final int ACTION_CODE_FOREIGN_STUDENTS_OFFICE_REVIEW = 120;

    /** Special code (121): student claim success. */
    public static final int ACTION_CODE_STUDENT_CLAIM_SUCCESS = 121;

    // --- Engineering Services (122-124) ---
    public static final int ACTION_CODE_ENGINEERING_SERVICES_APPROVAL = 122;
    public static final int ACTION_CODE_ENGINEERING_SERVICES_REJECTION = 123;
    public static final int ACTION_CODE_ENGINEERING_SERVICES_REVIEW = 124;

    // --- Task Received (استلام مهمة) codes (125-163) ---
    public static final int ACTION_CODE_TASK_RECEIVED_ENGINEERING_SERVICES = 125;
    public static final int ACTION_CODE_TASK_RECEIVED_ADMISSION_AND_REGISTRATION = 126;
    public static final int ACTION_CODE_TASK_RECEIVED_HEAD_OF_DEPARTMENT = 127;
    public static final int ACTION_CODE_TASK_RECEIVED_DEAN_OF_COLLEGE = 128;
    public static final int ACTION_CODE_TASK_RECEIVED_FINANCIAL_DEPARTMENT = 129;
    public static final int ACTION_CODE_TASK_RECEIVED_LOAN_COMMITTEE = 130;
    public static final int ACTION_CODE_TASK_RECEIVED_PRESIDENT = 131;
    public static final int ACTION_CODE_TASK_RECEIVED_INTERNAL_AUDIT = 132;
    public static final int ACTION_CODE_TASK_RECEIVED_HR_EMPLOYEE = 133;
    public static final int ACTION_CODE_TASK_RECEIVED_HR_MANAGER = 134;
    public static final int ACTION_CODE_TASK_RECEIVED_DIRECT_MANAGER = 135;
    public static final int ACTION_CODE_TASK_RECEIVED_HEALTH_CARE = 136;
    public static final int ACTION_CODE_TASK_RECEIVED_MAINTENANCE_HOS = 137;
    public static final int ACTION_CODE_TASK_RECEIVED_MAINTENANCE_EMPLOYEE = 138;
    public static final int ACTION_CODE_TASK_RECEIVED_LIBRARY = 139;
    public static final int ACTION_CODE_TASK_RECEIVED_WAREHOUSE = 140;
    public static final int ACTION_CODE_TASK_RECEIVED_IT = 141;
    public static final int ACTION_CODE_TASK_RECEIVED_PUBLIC_RELATIONS = 142;
    public static final int ACTION_CODE_TASK_RECEIVED_TRAINING = 143;
    public static final int ACTION_CODE_TASK_RECEIVED_CONSULTING = 144;
    public static final int ACTION_CODE_TASK_RECEIVED_DEAN_OF_ACCREDITATION_QUALITY = 145;
    public static final int ACTION_CODE_TASK_RECEIVED_ACADEMIC_AFFAIRS = 146;
    public static final int ACTION_CODE_TASK_RECEIVED_ADMINISTRATIVE_AFFAIRS = 147;
    public static final int ACTION_CODE_TASK_RECEIVED_ACADEMIC_COUNSELOR = 148;
    public static final int ACTION_CODE_TASK_RECEIVED_STUDENTS_SERVICES = 149;
    public static final int ACTION_CODE_TASK_RECEIVED_DELEGATION_AUTHORITY = 150;
    public static final int ACTION_CODE_TASK_RECEIVED_STUDENT_DEANSHIP = 151;
    public static final int ACTION_CODE_TASK_RECEIVED_PURCHASING = 152;
    public static final int ACTION_CODE_TASK_RECEIVED_FINANCIAL_MANAGER = 153;
    public static final int ACTION_CODE_TASK_RECEIVED_ACCREDITATION_COMMITTEE = 154;
    public static final int ACTION_CODE_TASK_RECEIVED_CENTRAL_COMMITTEE = 155;
    public static final int ACTION_CODE_TASK_RECEIVED_SECURITY_CENTER = 156;
    public static final int ACTION_CODE_TASK_RECEIVED_SERVICE_DEPARTMENT = 157;
    public static final int ACTION_CODE_TASK_RECEIVED_DEAN_OF_POSTGRADUATE = 158;
    public static final int ACTION_CODE_TASK_RECEIVED_DEAN_OF_HIGHER_EDUCATION = 159;
    public static final int ACTION_CODE_TASK_RECEIVED_LOANS_AND_GRANTS = 160;
    public static final int ACTION_CODE_TASK_RECEIVED_CASHIER = 161;
    public static final int ACTION_CODE_TASK_RECEIVED_LEGAL = 162;
    public static final int ACTION_CODE_TASK_RECEIVED_FOREIGN_STUDENTS_OFFICE = 163;

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
