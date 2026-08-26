package com.example.approval.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Action types of the audit trail, mapped one-to-one onto the numeric codes
 * of the pre-populated {@code BPM_ACTIONS} lookup table
 * ({@code ACTION_CODE NUMBER(4)} in {@code F_BPM_AUDIT_LOG_DTL}).
 *
 * <p>The table (see {@code docs/bpm_actions.htm}) contains 164 rows:</p>
 * <ul>
 *   <li>0 - case entered; 1-4 - applicant actions;</li>
 *   <li>5-124 - one {@code Approval / Rejection / Review} triplet per
 *       department / approver role (special codes 104, 108 and 121 are
 *       interleaved between the triplets);</li>
 *   <li>125-163 - one {@code Task Received} (استلام مهمة) code per
 *       department.</li>
 * </ul>
 *
 * <p><b>Descriptions:</b> the Arabic column {@code ACTION_DESC} is the
 * authoritative text; several {@code ACTION_DESC_S} rows contain typos
 * (e.g. 57 "TrainingDepartmentApproval" for a rejection, 69/70
 * "Administrative Affairs Approval" for rejection/review, 77-79 "Delegation
 * Authority Approval" three times, "Presidant", "Purshasing"). The English
 * descriptions below are the normalized forms of the intended meaning -
 * the numeric codes, not the strings, are what gets persisted.</p>
 *
 * <p>Use {@link #of(String, ActionType)} to resolve the department-specific
 * code at runtime (it understands both the {@code BPM_ACTIONS} department
 * names and the clearance group ids such as {@code HOD}, {@code DEN},
 * {@code LibraryDepartment}, {@code Finance Department} ...).</p>
 */
public enum BpmAuditAction {

    // ------------------------------------------------------------------
    // 0 - case entered
    // ------------------------------------------------------------------

    /** 0 - case entered / submitted (إدخال). Also the generic fallback. */
    ENTERED(BpmAuditConstants.ACTION_CODE_ENTERED, "Entered"),

    // ------------------------------------------------------------------
    // 1-4 - applicant actions
    // ------------------------------------------------------------------

    /** 1 - applicant continuation (استكمال من المتقدم). */
    APPLICANT_CONTINUATION(BpmAuditConstants.ACTION_CODE_APPLICANT_CONTINUATION, "Applicant Continuation"),
    /** 2 - applicant approval (اعتماد المتقدم). */
    APPLICANT_APPROVAL(BpmAuditConstants.ACTION_CODE_APPLICANT_APPROVAL, "Applicant Approval"),
    /** 3 - applicant rejection (رفض المتقدم). */
    APPLICANT_REJECTION(BpmAuditConstants.ACTION_CODE_APPLICANT_REJECTION, "Applicant Rejection"),
    /** 4 - applicant viewed final result (مشاهدة النتيجة النهائية من المتقدم). */
    APPLICANT_VIEWED_FINAL_RESULT(BpmAuditConstants.ACTION_CODE_APPLICANT_VIEWED_FINAL_RESULT, "Applicant Viewed Final Result"),

    // ------------------------------------------------------------------
    // 5-124 - department Approval / Rejection / Review triplets
    // ------------------------------------------------------------------

    /** 5 - Head Of Department Approval. */
    HEAD_OF_DEPARTMENT_APPROVAL(BpmAuditConstants.ACTION_CODE_HEAD_OF_DEPARTMENT_APPROVAL, "Head Of Department Approval"),
    /** 6 - Head Of Department Rejection. */
    HEAD_OF_DEPARTMENT_REJECTION(BpmAuditConstants.ACTION_CODE_HEAD_OF_DEPARTMENT_REJECTION, "Head Of Department Rejection"),
    /** 7 - Head Of Department Review. */
    HEAD_OF_DEPARTMENT_REVIEW(BpmAuditConstants.ACTION_CODE_HEAD_OF_DEPARTMENT_REVIEW, "Head Of Department Review"),

    /** 8 - Dean Of College Approval. */
    DEAN_OF_COLLEGE_APPROVAL(BpmAuditConstants.ACTION_CODE_DEAN_OF_COLLEGE_APPROVAL, "Dean Of College Approval"),
    /** 9 - Dean Of College Rejection. */
    DEAN_OF_COLLEGE_REJECTION(BpmAuditConstants.ACTION_CODE_DEAN_OF_COLLEGE_REJECTION, "Dean Of College Rejection"),
    /** 10 - Dean Of College Review. */
    DEAN_OF_COLLEGE_REVIEW(BpmAuditConstants.ACTION_CODE_DEAN_OF_COLLEGE_REVIEW, "Dean Of College Review"),

    /** 11 - Registration Dept Approval. */
    REGISTRATION_DEPT_APPROVAL(BpmAuditConstants.ACTION_CODE_REGISTRATION_DEPT_APPROVAL, "Registration Dept Approval"),
    /** 12 - Registration Dept Rejection. */
    REGISTRATION_DEPT_REJECTION(BpmAuditConstants.ACTION_CODE_REGISTRATION_DEPT_REJECTION, "Registration Dept Rejection"),
    /** 13 - Registration Dept Review. */
    REGISTRATION_DEPT_REVIEW(BpmAuditConstants.ACTION_CODE_REGISTRATION_DEPT_REVIEW, "Registration Dept Review"),

    /** 14 - Financial Department Approval. */
    FINANCIAL_DEPARTMENT_APPROVAL(BpmAuditConstants.ACTION_CODE_FINANCIAL_DEPARTMENT_APPROVAL, "Financial Department Approval"),
    /** 15 - Financial Department Rejection. */
    FINANCIAL_DEPARTMENT_REJECTION(BpmAuditConstants.ACTION_CODE_FINANCIAL_DEPARTMENT_REJECTION, "Financial Department Rejection"),
    /** 16 - Financial Department Review. */
    FINANCIAL_DEPARTMENT_REVIEW(BpmAuditConstants.ACTION_CODE_FINANCIAL_DEPARTMENT_REVIEW, "Financial Department Review"),

    /** 17 - Loan Committee Approval. */
    LOAN_COMMITTEE_APPROVAL(BpmAuditConstants.ACTION_CODE_LOAN_COMMITTEE_APPROVAL, "Loan Committee Approval"),
    /** 18 - Loan Committee Rejection. */
    LOAN_COMMITTEE_REJECTION(BpmAuditConstants.ACTION_CODE_LOAN_COMMITTEE_REJECTION, "Loan Committee Rejection"),
    /** 19 - Loan Committee Review. */
    LOAN_COMMITTEE_REVIEW(BpmAuditConstants.ACTION_CODE_LOAN_COMMITTEE_REVIEW, "Loan Committee Review"),

    /** 20 - President Approval (table spells "Presidant"). */
    PRESIDENT_APPROVAL(BpmAuditConstants.ACTION_CODE_PRESIDENT_APPROVAL, "President Approval"),
    /** 21 - President Rejection. */
    PRESIDENT_REJECTION(BpmAuditConstants.ACTION_CODE_PRESIDENT_REJECTION, "President Rejection"),
    /** 22 - President Review. */
    PRESIDENT_REVIEW(BpmAuditConstants.ACTION_CODE_PRESIDENT_REVIEW, "President Review"),

    /** 23 - Internal Audit Approval. */
    INTERNAL_AUDIT_APPROVAL(BpmAuditConstants.ACTION_CODE_INTERNAL_AUDIT_APPROVAL, "Internal Audit Approval"),
    /** 24 - Internal Audit Rejection. */
    INTERNAL_AUDIT_REJECTION(BpmAuditConstants.ACTION_CODE_INTERNAL_AUDIT_REJECTION, "Internal Audit Rejection"),
    /** 25 - Internal Audit Review. */
    INTERNAL_AUDIT_REVIEW(BpmAuditConstants.ACTION_CODE_INTERNAL_AUDIT_REVIEW, "Internal Audit Review"),

    /** 26 - HR Employee Approval. */
    HR_EMPLOYEE_APPROVAL(BpmAuditConstants.ACTION_CODE_HR_EMPLOYEE_APPROVAL, "HR Employee Approval"),
    /** 27 - HR Employee Rejection. */
    HR_EMPLOYEE_REJECTION(BpmAuditConstants.ACTION_CODE_HR_EMPLOYEE_REJECTION, "HR Employee Rejection"),
    /** 28 - HR Employee Review. */
    HR_EMPLOYEE_REVIEW(BpmAuditConstants.ACTION_CODE_HR_EMPLOYEE_REVIEW, "HR Employee Review"),

    /** 29 - HR Manager Approval. */
    HR_MANAGER_APPROVAL(BpmAuditConstants.ACTION_CODE_HR_MANAGER_APPROVAL, "HR Manager Approval"),
    /** 30 - HR Manager Rejection. */
    HR_MANAGER_REJECTION(BpmAuditConstants.ACTION_CODE_HR_MANAGER_REJECTION, "HR Manager Rejection"),
    /** 31 - HR Manager Review. */
    HR_MANAGER_REVIEW(BpmAuditConstants.ACTION_CODE_HR_MANAGER_REVIEW, "HR Manager Review"),

    /** 32 - Direct Manager Approval. */
    DIRECT_MANAGER_APPROVAL(BpmAuditConstants.ACTION_CODE_DIRECT_MANAGER_APPROVAL, "Direct Manager Approval"),
    /** 33 - Direct Manager Rejection. */
    DIRECT_MANAGER_REJECTION(BpmAuditConstants.ACTION_CODE_DIRECT_MANAGER_REJECTION, "Direct Manager Rejection"),
    /** 34 - Direct Manager Review. */
    DIRECT_MANAGER_REVIEW(BpmAuditConstants.ACTION_CODE_DIRECT_MANAGER_REVIEW, "Direct Manager Review"),

    /** 35 - Health Care Approval. */
    HEALTH_CARE_APPROVAL(BpmAuditConstants.ACTION_CODE_HEALTH_CARE_APPROVAL, "Health Care Approval"),
    /** 36 - Health Care Rejection. */
    HEALTH_CARE_REJECTION(BpmAuditConstants.ACTION_CODE_HEALTH_CARE_REJECTION, "Health Care Rejection"),
    /** 37 - Health Care Review. */
    HEALTH_CARE_REVIEW(BpmAuditConstants.ACTION_CODE_HEALTH_CARE_REVIEW, "Health Care Review"),

    /** 38 - Maintenance HOS Approval. */
    MAINTENANCE_HOS_APPROVAL(BpmAuditConstants.ACTION_CODE_MAINTENANCE_HOS_APPROVAL, "Maintenance HOS Approval"),
    /** 39 - Maintenance HOS Rejection. */
    MAINTENANCE_HOS_REJECTION(BpmAuditConstants.ACTION_CODE_MAINTENANCE_HOS_REJECTION, "Maintenance HOS Rejection"),
    /** 40 - Maintenance HOS Review. */
    MAINTENANCE_HOS_REVIEW(BpmAuditConstants.ACTION_CODE_MAINTENANCE_HOS_REVIEW, "Maintenance HOS Review"),

    /** 41 - Maintenance Employee Approval. */
    MAINTENANCE_EMPLOYEE_APPROVAL(BpmAuditConstants.ACTION_CODE_MAINTENANCE_EMPLOYEE_APPROVAL, "Maintenance Employee Approval"),
    /** 42 - Maintenance Employee Rejection. */
    MAINTENANCE_EMPLOYEE_REJECTION(BpmAuditConstants.ACTION_CODE_MAINTENANCE_EMPLOYEE_REJECTION, "Maintenance Employee Rejection"),
    /** 43 - Maintenance Employee Review. */
    MAINTENANCE_EMPLOYEE_REVIEW(BpmAuditConstants.ACTION_CODE_MAINTENANCE_EMPLOYEE_REVIEW, "Maintenance Employee Review"),

    /** 44 - Library Department Approval. */
    LIBRARY_APPROVAL(BpmAuditConstants.ACTION_CODE_LIBRARY_APPROVAL, "Library Department Approval"),
    /** 45 - Library Department Rejection. */
    LIBRARY_REJECTION(BpmAuditConstants.ACTION_CODE_LIBRARY_REJECTION, "Library Department Rejection"),
    /** 46 - Library Department Review. */
    LIBRARY_REVIEW(BpmAuditConstants.ACTION_CODE_LIBRARY_REVIEW, "Library Department Review"),

    /** 47 - Warehouse Approval. */
    WAREHOUSE_APPROVAL(BpmAuditConstants.ACTION_CODE_WAREHOUSE_APPROVAL, "Warehouse Approval"),
    /** 48 - Warehouse Rejection. */
    WAREHOUSE_REJECTION(BpmAuditConstants.ACTION_CODE_WAREHOUSE_REJECTION, "Warehouse Rejection"),
    /** 49 - Warehouse Review. */
    WAREHOUSE_REVIEW(BpmAuditConstants.ACTION_CODE_WAREHOUSE_REVIEW, "Warehouse Review"),

    /** 50 - IT Department Approval. */
    IT_DEPARTMENT_APPROVAL(BpmAuditConstants.ACTION_CODE_IT_DEPARTMENT_APPROVAL, "IT Department Approval"),
    /** 51 - IT Department Rejection. */
    IT_DEPARTMENT_REJECTION(BpmAuditConstants.ACTION_CODE_IT_DEPARTMENT_REJECTION, "IT Department Rejection"),
    /** 52 - IT Department Review. */
    IT_DEPARTMENT_REVIEW(BpmAuditConstants.ACTION_CODE_IT_DEPARTMENT_REVIEW, "IT Department Review"),

    /** 53 - PR Department Approval. */
    PR_DEPARTMENT_APPROVAL(BpmAuditConstants.ACTION_CODE_PR_DEPARTMENT_APPROVAL, "PR Department Approval"),
    /** 54 - PR Department Rejection. */
    PR_DEPARTMENT_REJECTION(BpmAuditConstants.ACTION_CODE_PR_DEPARTMENT_REJECTION, "PR Department Rejection"),
    /** 55 - PR Department Review. */
    PR_DEPARTMENT_REVIEW(BpmAuditConstants.ACTION_CODE_PR_DEPARTMENT_REVIEW, "PR Department Review"),

    /** 56 - Training Department Approval. */
    TRAINING_DEPARTMENT_APPROVAL(BpmAuditConstants.ACTION_CODE_TRAINING_DEPARTMENT_APPROVAL, "Training Department Approval"),
    /** 57 - Training Department Rejection (table English text is a copy-paste of the approval row). */
    TRAINING_DEPARTMENT_REJECTION(BpmAuditConstants.ACTION_CODE_TRAINING_DEPARTMENT_REJECTION, "Training Department Rejection"),
    /** 58 - Training Department Review. */
    TRAINING_DEPARTMENT_REVIEW(BpmAuditConstants.ACTION_CODE_TRAINING_DEPARTMENT_REVIEW, "Training Department Review"),

    /** 59 - Consulting Department Approval. */
    CONSULTING_DEPARTMENT_APPROVAL(BpmAuditConstants.ACTION_CODE_CONSULTING_DEPARTMENT_APPROVAL, "Consulting Department Approval"),
    /** 60 - Consulting Department Rejection. */
    CONSULTING_DEPARTMENT_REJECTION(BpmAuditConstants.ACTION_CODE_CONSULTING_DEPARTMENT_REJECTION, "Consulting Department Rejection"),
    /** 61 - Consulting Department Review. */
    CONSULTING_DEPARTMENT_REVIEW(BpmAuditConstants.ACTION_CODE_CONSULTING_DEPARTMENT_REVIEW, "Consulting Department Review"),

    /** 62 - Dean Of Accreditation Quality Approval. */
    DEAN_OF_ACCREDITATION_QUALITY_APPROVAL(BpmAuditConstants.ACTION_CODE_DEAN_OF_ACCREDITATION_QUALITY_APPROVAL, "Dean Of Accreditation Quality Approval"),
    /** 63 - Dean Of Accreditation Quality Rejection. */
    DEAN_OF_ACCREDITATION_QUALITY_REJECTION(BpmAuditConstants.ACTION_CODE_DEAN_OF_ACCREDITATION_QUALITY_REJECTION, "Dean Of Accreditation Quality Rejection"),
    /** 64 - Dean Of Accreditation Quality Review. */
    DEAN_OF_ACCREDITATION_QUALITY_REVIEW(BpmAuditConstants.ACTION_CODE_DEAN_OF_ACCREDITATION_QUALITY_REVIEW, "Dean Of Accreditation Quality Review"),

    /** 65 - Academic Affairs Approval. */
    ACADEMIC_AFFAIRS_APPROVAL(BpmAuditConstants.ACTION_CODE_ACADEMIC_AFFAIRS_APPROVAL, "Academic Affairs Approval"),
    /** 66 - Academic Affairs Rejection. */
    ACADEMIC_AFFAIRS_REJECTION(BpmAuditConstants.ACTION_CODE_ACADEMIC_AFFAIRS_REJECTION, "Academic Affairs Rejection"),
    /** 67 - Academic Affairs Review. */
    ACADEMIC_AFFAIRS_REVIEW(BpmAuditConstants.ACTION_CODE_ACADEMIC_AFFAIRS_REVIEW, "Academic Affairs Review"),

    /** 68 - Administrative Affairs Approval. */
    ADMINISTRATIVE_AFFAIRS_APPROVAL(BpmAuditConstants.ACTION_CODE_ADMINISTRATIVE_AFFAIRS_APPROVAL, "Administrative Affairs Approval"),
    /** 69 - Administrative Affairs Rejection (table English text is a copy-paste of the approval row). */
    ADMINISTRATIVE_AFFAIRS_REJECTION(BpmAuditConstants.ACTION_CODE_ADMINISTRATIVE_AFFAIRS_REJECTION, "Administrative Affairs Rejection"),
    /** 70 - Administrative Affairs Review (table English text is a copy-paste of the approval row). */
    ADMINISTRATIVE_AFFAIRS_REVIEW(BpmAuditConstants.ACTION_CODE_ADMINISTRATIVE_AFFAIRS_REVIEW, "Administrative Affairs Review"),

    /** 71 - Academic Counselor Approval. */
    ACADEMIC_COUNSELOR_APPROVAL(BpmAuditConstants.ACTION_CODE_ACADEMIC_COUNSELOR_APPROVAL, "Academic Counselor Approval"),
    /** 72 - Academic Counselor Rejection. */
    ACADEMIC_COUNSELOR_REJECTION(BpmAuditConstants.ACTION_CODE_ACADEMIC_COUNSELOR_REJECTION, "Academic Counselor Rejection"),
    /** 73 - Academic Counselor Review. */
    ACADEMIC_COUNSELOR_REVIEW(BpmAuditConstants.ACTION_CODE_ACADEMIC_COUNSELOR_REVIEW, "Academic Counselor Review"),

    /** 74 - Students Services Approval. */
    STUDENTS_SERVICES_APPROVAL(BpmAuditConstants.ACTION_CODE_STUDENTS_SERVICES_APPROVAL, "Students Services Approval"),
    /** 75 - Students Services Rejection. */
    STUDENTS_SERVICES_REJECTION(BpmAuditConstants.ACTION_CODE_STUDENTS_SERVICES_REJECTION, "Students Services Rejection"),
    /** 76 - Students Services Review. */
    STUDENTS_SERVICES_REVIEW(BpmAuditConstants.ACTION_CODE_STUDENTS_SERVICES_REVIEW, "Students Services Review"),

    /** 77 - Delegation Authority Approval. */
    DELEGATION_AUTHORITY_APPROVAL(BpmAuditConstants.ACTION_CODE_DELEGATION_AUTHORITY_APPROVAL, "Delegation Authority Approval"),
    /** 78 - Delegation Authority Rejection (table English text is a copy-paste of the approval row). */
    DELEGATION_AUTHORITY_REJECTION(BpmAuditConstants.ACTION_CODE_DELEGATION_AUTHORITY_REJECTION, "Delegation Authority Rejection"),
    /** 79 - Delegation Authority Review (table English text is a copy-paste of the approval row). */
    DELEGATION_AUTHORITY_REVIEW(BpmAuditConstants.ACTION_CODE_DELEGATION_AUTHORITY_REVIEW, "Delegation Authority Review"),

    /** 80 - Student Deanship Approval. */
    STUDENT_DEANSHIP_APPROVAL(BpmAuditConstants.ACTION_CODE_STUDENT_DEANSHIP_APPROVAL, "Student Deanship Approval"),
    /** 81 - Student Deanship Rejection. */
    STUDENT_DEANSHIP_REJECTION(BpmAuditConstants.ACTION_CODE_STUDENT_DEANSHIP_REJECTION, "Student Deanship Rejection"),
    /** 82 - Student Deanship Review. */
    STUDENT_DEANSHIP_REVIEW(BpmAuditConstants.ACTION_CODE_STUDENT_DEANSHIP_REVIEW, "Student Deanship Review"),

    /** 83 - Purchasing Approval (table spells "Purshasing"). */
    PURCHASING_APPROVAL(BpmAuditConstants.ACTION_CODE_PURCHASING_APPROVAL, "Purchasing Approval"),
    /** 84 - Purchasing Rejection. */
    PURCHASING_REJECTION(BpmAuditConstants.ACTION_CODE_PURCHASING_REJECTION, "Purchasing Rejection"),
    /** 85 - Purchasing Review. */
    PURCHASING_REVIEW(BpmAuditConstants.ACTION_CODE_PURCHASING_REVIEW, "Purchasing Review"),

    /** 86 - Financial Manager Approval. */
    FINANCIAL_MANAGER_APPROVAL(BpmAuditConstants.ACTION_CODE_FINANCIAL_MANAGER_APPROVAL, "Financial Manager Approval"),
    /** 87 - Financial Manager Rejection. */
    FINANCIAL_MANAGER_REJECTION(BpmAuditConstants.ACTION_CODE_FINANCIAL_MANAGER_REJECTION, "Financial Manager Rejection"),
    /** 88 - Financial Manager Review. */
    FINANCIAL_MANAGER_REVIEW(BpmAuditConstants.ACTION_CODE_FINANCIAL_MANAGER_REVIEW, "Financial Manager Review"),

    /** 89 - Accreditation Committee Approval. */
    ACCREDITATION_COMMITTEE_APPROVAL(BpmAuditConstants.ACTION_CODE_ACCREDITATION_COMMITTEE_APPROVAL, "Accreditation Committee Approval"),
    /** 90 - Accreditation Committee Rejection. */
    ACCREDITATION_COMMITTEE_REJECTION(BpmAuditConstants.ACTION_CODE_ACCREDITATION_COMMITTEE_REJECTION, "Accreditation Committee Rejection"),
    /** 91 - Accreditation Committee Review. */
    ACCREDITATION_COMMITTEE_REVIEW(BpmAuditConstants.ACTION_CODE_ACCREDITATION_COMMITTEE_REVIEW, "Accreditation Committee Review"),

    /** 92 - Central Committee Approval. */
    CENTRAL_COMMITTEE_APPROVAL(BpmAuditConstants.ACTION_CODE_CENTRAL_COMMITTEE_APPROVAL, "Central Committee Approval"),
    /** 93 - Central Committee Rejection. */
    CENTRAL_COMMITTEE_REJECTION(BpmAuditConstants.ACTION_CODE_CENTRAL_COMMITTEE_REJECTION, "Central Committee Rejection"),
    /** 94 - Central Committee Review. */
    CENTRAL_COMMITTEE_REVIEW(BpmAuditConstants.ACTION_CODE_CENTRAL_COMMITTEE_REVIEW, "Central Committee Review"),

    /** 95 - Security Center Approval. */
    SECURITY_CENTER_APPROVAL(BpmAuditConstants.ACTION_CODE_SECURITY_CENTER_APPROVAL, "Security Center Approval"),
    /** 96 - Security Center Rejection. */
    SECURITY_CENTER_REJECTION(BpmAuditConstants.ACTION_CODE_SECURITY_CENTER_REJECTION, "Security Center Rejection"),
    /** 97 - Security Center Review. */
    SECURITY_CENTER_REVIEW(BpmAuditConstants.ACTION_CODE_SECURITY_CENTER_REVIEW, "Security Center Review"),

    /** 98 - Service Department Approval. */
    SERVICE_DEPARTMENT_APPROVAL(BpmAuditConstants.ACTION_CODE_SERVICE_DEPARTMENT_APPROVAL, "Service Department Approval"),
    /** 99 - Service Department Rejection. */
    SERVICE_DEPARTMENT_REJECTION(BpmAuditConstants.ACTION_CODE_SERVICE_DEPARTMENT_REJECTION, "Service Department Rejection"),
    /** 100 - Service Department Review. */
    SERVICE_DEPARTMENT_REVIEW(BpmAuditConstants.ACTION_CODE_SERVICE_DEPARTMENT_REVIEW, "Service Department Review"),

    /** 101 - Dean Of PostGraduate Approval. */
    DEAN_OF_POSTGRADUATE_APPROVAL(BpmAuditConstants.ACTION_CODE_DEAN_OF_POSTGRADUATE_APPROVAL, "Dean Of PostGraduate Approval"),
    /** 102 - Dean Of PostGraduate Rejection. */
    DEAN_OF_POSTGRADUATE_REJECTION(BpmAuditConstants.ACTION_CODE_DEAN_OF_POSTGRADUATE_REJECTION, "Dean Of PostGraduate Rejection"),
    /** 103 - Dean Of PostGraduate Review. */
    DEAN_OF_POSTGRADUATE_REVIEW(BpmAuditConstants.ACTION_CODE_DEAN_OF_POSTGRADUATE_REVIEW, "Dean Of PostGraduate Review"),

    /** 104 - special: service payment claim failed. */
    SERVICE_PAYMENT_CLAIM_FAILED(BpmAuditConstants.ACTION_CODE_SERVICE_PAYMENT_CLAIM_FAILED, "Service Payment Claim Failed"),

    /** 105 - Dean Of Higher Education Approval. */
    DEAN_OF_HIGHER_EDUCATION_APPROVAL(BpmAuditConstants.ACTION_CODE_DEAN_OF_HIGHER_EDUCATION_APPROVAL, "Dean Of Higher Education Approval"),
    /** 106 - Dean Of Higher Education Rejection. */
    DEAN_OF_HIGHER_EDUCATION_REJECTION(BpmAuditConstants.ACTION_CODE_DEAN_OF_HIGHER_EDUCATION_REJECTION, "Dean Of Higher Education Rejection"),
    /** 107 - Dean Of Higher Education Review. */
    DEAN_OF_HIGHER_EDUCATION_REVIEW(BpmAuditConstants.ACTION_CODE_DEAN_OF_HIGHER_EDUCATION_REVIEW, "Dean Of Higher Education Review"),

    /** 108 - special: transaction service failure. */
    TRANSACTION_SERVICE_FAILURE(BpmAuditConstants.ACTION_CODE_TRANSACTION_SERVICE_FAILURE, "Transaction Service Failure"),

    /** 109 - Loans And Grants Approval. */
    LOANS_AND_GRANTS_APPROVAL(BpmAuditConstants.ACTION_CODE_LOANS_AND_GRANTS_APPROVAL, "Loans And Grants Approval"),
    /** 110 - Loans And Grants Rejection. */
    LOANS_AND_GRANTS_REJECTION(BpmAuditConstants.ACTION_CODE_LOANS_AND_GRANTS_REJECTION, "Loans And Grants Rejection"),
    /** 111 - Loans And Grants Review. */
    LOANS_AND_GRANTS_REVIEW(BpmAuditConstants.ACTION_CODE_LOANS_AND_GRANTS_REVIEW, "Loans And Grants Review"),

    /** 112 - Cashier Approval. */
    CASHIER_APPROVAL(BpmAuditConstants.ACTION_CODE_CASHIER_APPROVAL, "Cashier Approval"),
    /** 113 - Cashier Rejection. */
    CASHIER_REJECTION(BpmAuditConstants.ACTION_CODE_CASHIER_REJECTION, "Cashier Rejection"),
    /** 114 - Cashier Review. */
    CASHIER_REVIEW(BpmAuditConstants.ACTION_CODE_CASHIER_REVIEW, "Cashier Review"),

    /** 115 - Legal Department Approval. */
    LEGAL_DEPARTMENT_APPROVAL(BpmAuditConstants.ACTION_CODE_LEGAL_DEPARTMENT_APPROVAL, "Legal Department Approval"),
    /** 116 - Legal Department Rejection. */
    LEGAL_DEPARTMENT_REJECTION(BpmAuditConstants.ACTION_CODE_LEGAL_DEPARTMENT_REJECTION, "Legal Department Rejection"),
    /** 117 - Legal Department Review. */
    LEGAL_DEPARTMENT_REVIEW(BpmAuditConstants.ACTION_CODE_LEGAL_DEPARTMENT_REVIEW, "Legal Department Review"),

    /** 118 - Foreign Students Office Approval. */
    FOREIGN_STUDENTS_OFFICE_APPROVAL(BpmAuditConstants.ACTION_CODE_FOREIGN_STUDENTS_OFFICE_APPROVAL, "Foreign Students Office Approval"),
    /** 119 - Foreign Students Office Rejection. */
    FOREIGN_STUDENTS_OFFICE_REJECTION(BpmAuditConstants.ACTION_CODE_FOREIGN_STUDENTS_OFFICE_REJECTION, "Foreign Students Office Rejection"),
    /** 120 - Foreign Students Office Review. */
    FOREIGN_STUDENTS_OFFICE_REVIEW(BpmAuditConstants.ACTION_CODE_FOREIGN_STUDENTS_OFFICE_REVIEW, "Foreign Students Office Review"),

    /** 121 - special: student claim success. */
    STUDENT_CLAIM_SUCCESS(BpmAuditConstants.ACTION_CODE_STUDENT_CLAIM_SUCCESS, "Student Claim Success"),

    /** 122 - Engineering Services Approval. */
    ENGINEERING_SERVICES_APPROVAL(BpmAuditConstants.ACTION_CODE_ENGINEERING_SERVICES_APPROVAL, "Engineering Services Approval"),
    /** 123 - Engineering Services Rejection. */
    ENGINEERING_SERVICES_REJECTION(BpmAuditConstants.ACTION_CODE_ENGINEERING_SERVICES_REJECTION, "Engineering Services Rejection"),
    /** 124 - Engineering Services Review. */
    ENGINEERING_SERVICES_REVIEW(BpmAuditConstants.ACTION_CODE_ENGINEERING_SERVICES_REVIEW, "Engineering Services Review"),

    // ------------------------------------------------------------------
    // 125-163 - Task Received (استلام مهمة) per department
    // ------------------------------------------------------------------

    /** 125 - Engineering Services Task Received. */
    TASK_RECEIVED_ENGINEERING_SERVICES(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_ENGINEERING_SERVICES, "Engineering Services Task Received"),
    /** 126 - Admission and Registration Task Received. */
    TASK_RECEIVED_ADMISSION_AND_REGISTRATION(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_ADMISSION_AND_REGISTRATION, "Admission and Registration Task Received"),
    /** 127 - Head Of Department Task Received. */
    TASK_RECEIVED_HEAD_OF_DEPARTMENT(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_HEAD_OF_DEPARTMENT, "Head Of Department Task Received"),
    /** 128 - Dean Of College Task Received. */
    TASK_RECEIVED_DEAN_OF_COLLEGE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_DEAN_OF_COLLEGE, "Dean Of College Task Received"),
    /** 129 - Financial Department Task Received. */
    TASK_RECEIVED_FINANCIAL_DEPARTMENT(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_FINANCIAL_DEPARTMENT, "Financial Department Task Received"),
    /** 130 - Loan Committee Task Received. */
    TASK_RECEIVED_LOAN_COMMITTEE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_LOAN_COMMITTEE, "Loan Committee Task Received"),
    /** 131 - President Task Received. */
    TASK_RECEIVED_PRESIDENT(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_PRESIDENT, "President Task Received"),
    /** 132 - Internal Audit Task Received. */
    TASK_RECEIVED_INTERNAL_AUDIT(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_INTERNAL_AUDIT, "Internal Audit Task Received"),
    /** 133 - HR Employee Task Received. */
    TASK_RECEIVED_HR_EMPLOYEE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_HR_EMPLOYEE, "HR Employee Task Received"),
    /** 134 - HR Manager Task Received. */
    TASK_RECEIVED_HR_MANAGER(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_HR_MANAGER, "HR Manager Task Received"),
    /** 135 - Direct Manager Task Received. */
    TASK_RECEIVED_DIRECT_MANAGER(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_DIRECT_MANAGER, "Direct Manager Task Received"),
    /** 136 - Health Care Task Received. */
    TASK_RECEIVED_HEALTH_CARE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_HEALTH_CARE, "Health Care Task Received"),
    /** 137 - Maintenance HOS Task Received. */
    TASK_RECEIVED_MAINTENANCE_HOS(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_MAINTENANCE_HOS, "Maintenance HOS Task Received"),
    /** 138 - Maintenance Employee Task Received. */
    TASK_RECEIVED_MAINTENANCE_EMPLOYEE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_MAINTENANCE_EMPLOYEE, "Maintenance Employee Task Received"),
    /** 139 - Library Task Received. */
    TASK_RECEIVED_LIBRARY(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_LIBRARY, "Library Task Received"),
    /** 140 - Warehouse Task Received. */
    TASK_RECEIVED_WAREHOUSE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_WAREHOUSE, "Warehouse Task Received"),
    /** 141 - IT Task Received. */
    TASK_RECEIVED_IT(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_IT, "IT Task Received"),
    /** 142 - Public Relations Task Received. */
    TASK_RECEIVED_PUBLIC_RELATIONS(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_PUBLIC_RELATIONS, "Public Relations Task Received"),
    /** 143 - Training Task Received. */
    TASK_RECEIVED_TRAINING(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_TRAINING, "Training Task Received"),
    /** 144 - Consulting Task Received. */
    TASK_RECEIVED_CONSULTING(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_CONSULTING, "Consulting Task Received"),
    /** 145 - Dean Of Accreditation Quality Task Received. */
    TASK_RECEIVED_DEAN_OF_ACCREDITATION_QUALITY(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_DEAN_OF_ACCREDITATION_QUALITY, "Dean Of Accreditation Quality Task Received"),
    /** 146 - Academic Affairs Task Received. */
    TASK_RECEIVED_ACADEMIC_AFFAIRS(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_ACADEMIC_AFFAIRS, "Academic Affairs Task Received"),
    /** 147 - Administrative Affairs Task Received. */
    TASK_RECEIVED_ADMINISTRATIVE_AFFAIRS(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_ADMINISTRATIVE_AFFAIRS, "Administrative Affairs Task Received"),
    /** 148 - Academic Counselor Task Received. */
    TASK_RECEIVED_ACADEMIC_COUNSELOR(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_ACADEMIC_COUNSELOR, "Academic Counselor Task Received"),
    /** 149 - Students Services Task Received. */
    TASK_RECEIVED_STUDENTS_SERVICES(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_STUDENTS_SERVICES, "Students Services Task Received"),
    /** 150 - Delegation Authority Task Received. */
    TASK_RECEIVED_DELEGATION_AUTHORITY(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_DELEGATION_AUTHORITY, "Delegation Authority Task Received"),
    /** 151 - Student Deanship Task Received. */
    TASK_RECEIVED_STUDENT_DEANSHIP(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_STUDENT_DEANSHIP, "Student Deanship Task Received"),
    /** 152 - Purchasing Task Received. */
    TASK_RECEIVED_PURCHASING(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_PURCHASING, "Purchasing Task Received"),
    /** 153 - Financial Manager Task Received. */
    TASK_RECEIVED_FINANCIAL_MANAGER(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_FINANCIAL_MANAGER, "Financial Manager Task Received"),
    /** 154 - Accreditation Committee Task Received. */
    TASK_RECEIVED_ACCREDITATION_COMMITTEE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_ACCREDITATION_COMMITTEE, "Accreditation Committee Task Received"),
    /** 155 - Central Committee Task Received. */
    TASK_RECEIVED_CENTRAL_COMMITTEE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_CENTRAL_COMMITTEE, "Central Committee Task Received"),
    /** 156 - Security Center Task Received. */
    TASK_RECEIVED_SECURITY_CENTER(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_SECURITY_CENTER, "Security Center Task Received"),
    /** 157 - Service Department Task Received. */
    TASK_RECEIVED_SERVICE_DEPARTMENT(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_SERVICE_DEPARTMENT, "Service Department Task Received"),
    /** 158 - Dean Of PostGraduate Task Received. */
    TASK_RECEIVED_DEAN_OF_POSTGRADUATE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_DEAN_OF_POSTGRADUATE, "Dean Of PostGraduate Task Received"),
    /** 159 - Dean Of Higher Education Task Received. */
    TASK_RECEIVED_DEAN_OF_HIGHER_EDUCATION(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_DEAN_OF_HIGHER_EDUCATION, "Dean Of Higher Education Task Received"),
    /** 160 - Loans And Grants Task Received. */
    TASK_RECEIVED_LOANS_AND_GRANTS(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_LOANS_AND_GRANTS, "Loans And Grants Task Received"),
    /** 161 - Cashier Task Received. */
    TASK_RECEIVED_CASHIER(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_CASHIER, "Cashier Task Received"),
    /** 162 - Legal Task Received. */
    TASK_RECEIVED_LEGAL(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_LEGAL, "Legal Task Received"),
    /** 163 - Foreign Students Office Task Received. */
    TASK_RECEIVED_FOREIGN_STUDENTS_OFFICE(BpmAuditConstants.ACTION_CODE_TASK_RECEIVED_FOREIGN_STUDENTS_OFFICE, "Foreign Students Office Task Received");

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

    // ------------------------------------------------------------------
    // lookups
    // ------------------------------------------------------------------

    private static final Logger LOG = LoggerFactory.getLogger(BpmAuditAction.class);

    /** Fast code -> enum lookup (built once). */
    private static final Map<Integer, BpmAuditAction> BY_CODE = new HashMap<>();
    static {
        for (BpmAuditAction action : values()) {
            BpmAuditAction previous = BY_CODE.put(action.code, action);
            if (previous != null) {
                throw new IllegalStateException("Duplicate BPM_ACTIONS code " + action.code
                        + " (" + previous + " / " + action + ")");
            }
        }
    }

    /**
     * Resolves an {@code ACTION_CODE} read back from
     * {@code F_BPM_AUDIT_LOG_DTL} onto its enum constant.
     *
     * @param code numeric action code
     * @return the matching constant, or {@link #ENTERED} for unknown codes
     */
    public static BpmAuditAction fromCode(int code) {
        BpmAuditAction action = BY_CODE.get(code);
        return action != null ? action : ENTERED;
    }

    // ------------------------------------------------------------------
    // department-based resolution
    // ------------------------------------------------------------------

    /** The four action flavours every {@code BPM_ACTIONS} department offers. */
    public enum ActionType {
        APPROVAL, REJECTION, REVIEW, TASK_RECEIVED
    }

    /**
     * Alias table: normalized department name -> its four action constants
     * {approval, rejection, review, taskReceived}. Covers the
     * {@code BPM_ACTIONS} department names, their common misspellings and
     * the clearance group ids (HOD, DEN, LibraryDepartment, ...).
     */
    private static final Map<String, BpmAuditAction[]> DEPARTMENT_ACTIONS = new HashMap<>();
    static {
        register(new String[]{"hod", "headofdepartment"},
                HEAD_OF_DEPARTMENT_APPROVAL, HEAD_OF_DEPARTMENT_REJECTION, HEAD_OF_DEPARTMENT_REVIEW,
                TASK_RECEIVED_HEAD_OF_DEPARTMENT);
        register(new String[]{"den", "deanofcollege", "dean"},
                DEAN_OF_COLLEGE_APPROVAL, DEAN_OF_COLLEGE_REJECTION, DEAN_OF_COLLEGE_REVIEW,
                TASK_RECEIVED_DEAN_OF_COLLEGE);
        register(new String[]{"registrationdept", "registration", "admissionandregistrationdepartment",
                "admissionandregistration", "admission"},
                REGISTRATION_DEPT_APPROVAL, REGISTRATION_DEPT_REJECTION, REGISTRATION_DEPT_REVIEW,
                TASK_RECEIVED_ADMISSION_AND_REGISTRATION);
        register(new String[]{"financialdepartment", "financedepartment", "finance", "financial"},
                FINANCIAL_DEPARTMENT_APPROVAL, FINANCIAL_DEPARTMENT_REJECTION, FINANCIAL_DEPARTMENT_REVIEW,
                TASK_RECEIVED_FINANCIAL_DEPARTMENT);
        register(new String[]{"loancommittee"},
                LOAN_COMMITTEE_APPROVAL, LOAN_COMMITTEE_REJECTION, LOAN_COMMITTEE_REVIEW,
                TASK_RECEIVED_LOAN_COMMITTEE);
        register(new String[]{"president", "presidant"},
                PRESIDENT_APPROVAL, PRESIDENT_REJECTION, PRESIDENT_REVIEW,
                TASK_RECEIVED_PRESIDENT);
        register(new String[]{"internalaudit", "internalauditdepartment"},
                INTERNAL_AUDIT_APPROVAL, INTERNAL_AUDIT_REJECTION, INTERNAL_AUDIT_REVIEW,
                TASK_RECEIVED_INTERNAL_AUDIT);
        register(new String[]{"hremployee"},
                HR_EMPLOYEE_APPROVAL, HR_EMPLOYEE_REJECTION, HR_EMPLOYEE_REVIEW,
                TASK_RECEIVED_HR_EMPLOYEE);
        register(new String[]{"hrmanager"},
                HR_MANAGER_APPROVAL, HR_MANAGER_REJECTION, HR_MANAGER_REVIEW,
                TASK_RECEIVED_HR_MANAGER);
        register(new String[]{"directmanager"},
                DIRECT_MANAGER_APPROVAL, DIRECT_MANAGER_REJECTION, DIRECT_MANAGER_REVIEW,
                TASK_RECEIVED_DIRECT_MANAGER);
        register(new String[]{"healthcare", "healthcaredept", "healthcaredepartment", "hc"},
                HEALTH_CARE_APPROVAL, HEALTH_CARE_REJECTION, HEALTH_CARE_REVIEW,
                TASK_RECEIVED_HEALTH_CARE);
        register(new String[]{"maintenancehos"},
                MAINTENANCE_HOS_APPROVAL, MAINTENANCE_HOS_REJECTION, MAINTENANCE_HOS_REVIEW,
                TASK_RECEIVED_MAINTENANCE_HOS);
        register(new String[]{"maintenanceemployee"},
                MAINTENANCE_EMPLOYEE_APPROVAL, MAINTENANCE_EMPLOYEE_REJECTION, MAINTENANCE_EMPLOYEE_REVIEW,
                TASK_RECEIVED_MAINTENANCE_EMPLOYEE);
        register(new String[]{"library", "librarydepartment", "lib"},
                LIBRARY_APPROVAL, LIBRARY_REJECTION, LIBRARY_REVIEW,
                TASK_RECEIVED_LIBRARY);
        register(new String[]{"warehouse", "warehouses", "warehousedepartment", "wrh"},
                WAREHOUSE_APPROVAL, WAREHOUSE_REJECTION, WAREHOUSE_REVIEW,
                TASK_RECEIVED_WAREHOUSE);
        register(new String[]{"it", "itdepartment"},
                IT_DEPARTMENT_APPROVAL, IT_DEPARTMENT_REJECTION, IT_DEPARTMENT_REVIEW,
                TASK_RECEIVED_IT);
        register(new String[]{"prdepartment", "pr", "publicrelations", "publicrelationsdepartment"},
                PR_DEPARTMENT_APPROVAL, PR_DEPARTMENT_REJECTION, PR_DEPARTMENT_REVIEW,
                TASK_RECEIVED_PUBLIC_RELATIONS);
        register(new String[]{"training", "trainingdepartment", "mediaandtraining", "mediaandtrainingdepartment",
                "medtrn"},
                TRAINING_DEPARTMENT_APPROVAL, TRAINING_DEPARTMENT_REJECTION, TRAINING_DEPARTMENT_REVIEW,
                TASK_RECEIVED_TRAINING);
        register(new String[]{"consulting", "consultingdepartment"},
                CONSULTING_DEPARTMENT_APPROVAL, CONSULTING_DEPARTMENT_REJECTION, CONSULTING_DEPARTMENT_REVIEW,
                TASK_RECEIVED_CONSULTING);
        register(new String[]{"deanofaccreditationquality", "deanofaccredationquality"},
                DEAN_OF_ACCREDITATION_QUALITY_APPROVAL, DEAN_OF_ACCREDITATION_QUALITY_REJECTION,
                DEAN_OF_ACCREDITATION_QUALITY_REVIEW,
                TASK_RECEIVED_DEAN_OF_ACCREDITATION_QUALITY);
        register(new String[]{"academicaffairs", "academicaffairsdepartment"},
                ACADEMIC_AFFAIRS_APPROVAL, ACADEMIC_AFFAIRS_REJECTION, ACADEMIC_AFFAIRS_REVIEW,
                TASK_RECEIVED_ACADEMIC_AFFAIRS);
        register(new String[]{"administrativeaffairs", "administrativeaffairsdepartment"},
                ADMINISTRATIVE_AFFAIRS_APPROVAL, ADMINISTRATIVE_AFFAIRS_REJECTION, ADMINISTRATIVE_AFFAIRS_REVIEW,
                TASK_RECEIVED_ADMINISTRATIVE_AFFAIRS);
        register(new String[]{"academiccounselor"},
                ACADEMIC_COUNSELOR_APPROVAL, ACADEMIC_COUNSELOR_REJECTION, ACADEMIC_COUNSELOR_REVIEW,
                TASK_RECEIVED_ACADEMIC_COUNSELOR);
        register(new String[]{"studentsservices", "studentservices", "studentservicesdepartment", "stdsrv"},
                STUDENTS_SERVICES_APPROVAL, STUDENTS_SERVICES_REJECTION, STUDENTS_SERVICES_REVIEW,
                TASK_RECEIVED_STUDENTS_SERVICES);
        register(new String[]{"delegationauthority"},
                DELEGATION_AUTHORITY_APPROVAL, DELEGATION_AUTHORITY_REJECTION, DELEGATION_AUTHORITY_REVIEW,
                TASK_RECEIVED_DELEGATION_AUTHORITY);
        register(new String[]{"studentdeanship", "stdden"},
                STUDENT_DEANSHIP_APPROVAL, STUDENT_DEANSHIP_REJECTION, STUDENT_DEANSHIP_REVIEW,
                TASK_RECEIVED_STUDENT_DEANSHIP);
        register(new String[]{"purchasing", "purchasingdepartment", "purshasing", "purshasingdepartment"},
                PURCHASING_APPROVAL, PURCHASING_REJECTION, PURCHASING_REVIEW,
                TASK_RECEIVED_PURCHASING);
        register(new String[]{"financialmanager"},
                FINANCIAL_MANAGER_APPROVAL, FINANCIAL_MANAGER_REJECTION, FINANCIAL_MANAGER_REVIEW,
                TASK_RECEIVED_FINANCIAL_MANAGER);
        register(new String[]{"accreditationcommittee"},
                ACCREDITATION_COMMITTEE_APPROVAL, ACCREDITATION_COMMITTEE_REJECTION, ACCREDITATION_COMMITTEE_REVIEW,
                TASK_RECEIVED_ACCREDITATION_COMMITTEE);
        register(new String[]{"centralcommittee"},
                CENTRAL_COMMITTEE_APPROVAL, CENTRAL_COMMITTEE_REJECTION, CENTRAL_COMMITTEE_REVIEW,
                TASK_RECEIVED_CENTRAL_COMMITTEE);
        register(new String[]{"securitycenter"},
                SECURITY_CENTER_APPROVAL, SECURITY_CENTER_REJECTION, SECURITY_CENTER_REVIEW,
                TASK_RECEIVED_SECURITY_CENTER);
        register(new String[]{"servicedepartment"},
                SERVICE_DEPARTMENT_APPROVAL, SERVICE_DEPARTMENT_REJECTION, SERVICE_DEPARTMENT_REVIEW,
                TASK_RECEIVED_SERVICE_DEPARTMENT);
        register(new String[]{"deanofpostgraduate", "deanofpostgraduates"},
                DEAN_OF_POSTGRADUATE_APPROVAL, DEAN_OF_POSTGRADUATE_REJECTION, DEAN_OF_POSTGRADUATE_REVIEW,
                TASK_RECEIVED_DEAN_OF_POSTGRADUATE);
        register(new String[]{"deanofhighereducation"},
                DEAN_OF_HIGHER_EDUCATION_APPROVAL, DEAN_OF_HIGHER_EDUCATION_REJECTION, DEAN_OF_HIGHER_EDUCATION_REVIEW,
                TASK_RECEIVED_DEAN_OF_HIGHER_EDUCATION);
        register(new String[]{"loansandgrants"},
                LOANS_AND_GRANTS_APPROVAL, LOANS_AND_GRANTS_REJECTION, LOANS_AND_GRANTS_REVIEW,
                TASK_RECEIVED_LOANS_AND_GRANTS);
        register(new String[]{"cashier"},
                CASHIER_APPROVAL, CASHIER_REJECTION, CASHIER_REVIEW,
                TASK_RECEIVED_CASHIER);
        register(new String[]{"legal", "legaldepartment", "lgl"},
                LEGAL_DEPARTMENT_APPROVAL, LEGAL_DEPARTMENT_REJECTION, LEGAL_DEPARTMENT_REVIEW,
                TASK_RECEIVED_LEGAL);
        register(new String[]{"foreignstudentsoffice"},
                FOREIGN_STUDENTS_OFFICE_APPROVAL, FOREIGN_STUDENTS_OFFICE_REJECTION, FOREIGN_STUDENTS_OFFICE_REVIEW,
                TASK_RECEIVED_FOREIGN_STUDENTS_OFFICE);
        register(new String[]{"engineeringservices", "engineeringandservices", "engineringandservices",
                "engineringandservicesdepartment", "engsrv"},
                ENGINEERING_SERVICES_APPROVAL, ENGINEERING_SERVICES_REJECTION, ENGINEERING_SERVICES_REVIEW,
                TASK_RECEIVED_ENGINEERING_SERVICES);
    }

    /**
     * Registers one department under all its aliases. Aliases are normalized
     * exactly like the lookup key in {@link #of(String, ActionType)} (lower
     * case, non-alphanumerics stripped), so registering {@code "LGL"} stores
     * the key {@code "lgl"} and matches the normalized runtime group ids.
     */
    private static void register(String[] aliases, BpmAuditAction... actions) {
        for (String alias : aliases) {
            DEPARTMENT_ACTIONS.put(normalize(alias), actions);
        }
    }

    /**
     * Normalizes a department name / alias for lookup: lower-cased with all
     * characters but {@code a-z0-9} removed, so {@code "LGL"}, {@code "lgl"},
     * {@code "Legal Department"} and {@code "legal-department"} share one key.
     */
    private static String normalize(String department) {
        return department.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Resolves the department-specific action, e.g.
     * {@code of("LibraryDepartment", REJECTION)} -> code 45. Matching is
     * case-insensitive and ignores spaces / punctuation, so both the
     * {@code BPM_ACTIONS} department names and the clearance group ids
     * resolve ("HOD", "Finance Department", "IT Department", ...).
     *
     * @param department department / approver-group name (nullable)
     * @param type       which of the four flavours is needed
     * @return the matching constant, or {@link #ENTERED} (code 0) when the
     *         department is unknown or null - always a code that exists in
     *         {@code BPM_ACTIONS}, so inserts never violate the lookup
     */
    public static BpmAuditAction of(String department, ActionType type) {
        if (department == null || department.isBlank() || type == null) {
            return ENTERED;
        }
        BpmAuditAction[] actions = DEPARTMENT_ACTIONS.get(normalize(department));
        if (actions == null) {
            // never break the workflow over an unknown department, but make
            // the ENTERED (0) fallback visible: it silently replaces the
            // department's real BPM_ACTIONS code (e.g. LGL -> 162).
            LOG.warn("Unknown BPM audit department '{}' (type {}) - falling back to ENTERED (0)",
                    department, type);
            return ENTERED;
        }
        return switch (type) {
            case APPROVAL -> actions[0];
            case REJECTION -> actions[1];
            case REVIEW -> actions[2];
            case TASK_RECEIVED -> actions[3];
        };
    }
}
