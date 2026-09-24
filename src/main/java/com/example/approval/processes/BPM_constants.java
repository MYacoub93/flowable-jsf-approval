package com.example.approval.processes;

/**
 * Centralized BPM process document codes shared by the process
 * implementations under {@code com.example.approval.processes.*}.
 *
 * <p>Each constant holds the SIS document code that identifies the
 * transaction type of one BPM process. Currently these codes are passed as
 * the {@code documentCode} parameter of the SIS presubmit validation
 * {@code BPM_PKG.check_presubmit_bpm_service} before a process instance is
 * created. Process services reference these constants instead of
 * maintaining their own duplicated hard-coded values; a service may keep a
 * local alias constant (the same aliasing convention used by
 * {@code ClearanceConstants} / {@code SemesterWithdrawalConstants} for the
 * shared {@code BpmAuditConstants}).</p>
 *
 * <p>This class does <b>not</b> cover the Oracle audit
 * {@code BPM_DOCUMENTS.DOCUMENT_CODE} mapping - that stays configurable per
 * process definition key in {@code application.yml}
 * ({@code bpm.audit.document-codes}, see {@code BpmAuditProperties}) and
 * must not be duplicated here.</p>
 */
public final class BPM_constants {

    private BPM_constants() {
    }

    /**
     * Presubmit document code of the <b>Semester Withdrawal</b> process
     * ({@code semester-withdrawl}) in SIS - the
     * {@code BPM_PKG.check_presubmit_bpm_service} parameter
     * {@code documentCode} used by
     * {@code SemesterWithdrawalService#checkPresubmit} before the process
     * instance is created. The document code identifies the transaction
     * type being validated.
     */
    public static final int Withdrawl_Process_DOCUMENT_CODE = 21;
}