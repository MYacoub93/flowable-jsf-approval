package com.example.approval.audit;

import com.example.approval.clearance.ClearanceConstants;
import org.junit.jupiter.api.Test;

import static com.example.approval.audit.BpmAuditAction.ActionType.APPROVAL;
import static com.example.approval.audit.BpmAuditAction.ActionType.REJECTION;
import static com.example.approval.audit.BpmAuditAction.ActionType.TASK_RECEIVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regression tests for the department alias resolution of
 * {@link BpmAuditAction#of(String, BpmAuditAction.ActionType)}.
 *
 * <p>Background bug: aliases were stored verbatim while the lookup key was
 * normalized to lower case, so the clearance group id {@code "LGL"} (Legal
 * Department) never matched and audit rows were persisted with
 * {@code ACTION_CODE = 0} (ENTERED) instead of 162 (Legal Task Received).</p>
 */
class BpmAuditActionTest {

    // ------------------------------------------------------------------
    // The reported bug: Legal department persisted as 0 instead of 162
    // ------------------------------------------------------------------

    @Test
    void legalGroupId_resolvesToTaskReceived162() {
        assertEquals(162, BpmAuditAction.of(ClearanceConstants.DEPT_LEGAL, TASK_RECEIVED).code());
        assertEquals(162, BpmAuditAction.of("LGL", TASK_RECEIVED).code());
        assertEquals(162, BpmAuditAction.of("lgl", TASK_RECEIVED).code());
        assertEquals(162, BpmAuditAction.of("Legal Department", TASK_RECEIVED).code());
    }

    @Test
    void legalGroupId_resolvesDecisionCodes115and116() {
        assertEquals(115, BpmAuditAction.of(ClearanceConstants.DEPT_LEGAL, APPROVAL).code());
        assertEquals(116, BpmAuditAction.of(ClearanceConstants.DEPT_LEGAL, REJECTION).code());
    }

    // ------------------------------------------------------------------
    // Every clearance group id must resolve (never silently to 0)
    // ------------------------------------------------------------------

    @Test
    void everyClearanceDepartment_resolvesToNonZeroTaskReceivedCode() {
        for (String department : ClearanceConstants.ALL_DEPARTMENTS) {
            int code = BpmAuditAction.of(department, TASK_RECEIVED).code();
            assertNotEquals(0, code,
                    "department '" + department + "' resolved to ENTERED (0) - alias missing");
        }
    }

    @Test
    void everyClearanceDepartment_resolvesToNonZeroApprovalCode() {
        for (String department : ClearanceConstants.ALL_DEPARTMENTS) {
            int code = BpmAuditAction.of(department, APPROVAL).code();
            assertNotEquals(0, code,
                    "department '" + department + "' resolved to ENTERED (0) - alias missing");
        }
    }

    // ------------------------------------------------------------------
    // Known clearance group ids map onto their BPM_ACTIONS codes
    // ------------------------------------------------------------------

    @Test
    void clearanceGroupIds_mapOntoExpectedTaskReceivedCodes() {
        assertEquals(125, BpmAuditAction.of(ClearanceConstants.DEPT_ENGINEERING_AND_SERVICES, TASK_RECEIVED).code());
        assertEquals(127, BpmAuditAction.of(ClearanceConstants.DEPT_HOD, TASK_RECEIVED).code());
        assertEquals(128, BpmAuditAction.of(ClearanceConstants.DEPT_DEN, TASK_RECEIVED).code());
        assertEquals(136, BpmAuditAction.of(ClearanceConstants.DEPT_HEALTH_CARE, TASK_RECEIVED).code());
        assertEquals(139, BpmAuditAction.of(ClearanceConstants.DEPT_LIBRARY, TASK_RECEIVED).code());
        assertEquals(140, BpmAuditAction.of(ClearanceConstants.DEPT_WAREHOUSES, TASK_RECEIVED).code());
        assertEquals(141, BpmAuditAction.of(ClearanceConstants.DEPT_IT, TASK_RECEIVED).code());
        assertEquals(143, BpmAuditAction.of(ClearanceConstants.DEPT_MEDIA_AND_TRAINING, TASK_RECEIVED).code());
        assertEquals(149, BpmAuditAction.of(ClearanceConstants.DEPT_STUDENT_SERVICES, TASK_RECEIVED).code());
        assertEquals(151, BpmAuditAction.of(ClearanceConstants.DEPT_STUDENT_DEANSHIP, TASK_RECEIVED).code());
    }

    // ------------------------------------------------------------------
    // Sequential stage groups (full display names) still resolve
    // ------------------------------------------------------------------

    @Test
    void sequentialStageGroups_resolve() {
        assertEquals(129, BpmAuditAction.of(ClearanceConstants.GROUP_FINANCE, TASK_RECEIVED).code());
        assertEquals(126, BpmAuditAction
                .of(ClearanceConstants.GROUP_ADMISSION_AND_REGISTRATION, TASK_RECEIVED).code());
        assertEquals(132, BpmAuditAction
                .of(ClearanceConstants.GROUP_INTERNAL_AUDIT, TASK_RECEIVED).code());
    }

    // ------------------------------------------------------------------
    // Unknown departments keep falling back to ENTERED (0) by design
    // ------------------------------------------------------------------

    @Test
    void unknownDepartment_fallsBackToEntered() {
        assertEquals(0, BpmAuditAction.of("Does Not Exist", TASK_RECEIVED).code());
        assertEquals(0, BpmAuditAction.of(null, TASK_RECEIVED).code());
        assertEquals(0, BpmAuditAction.of("  ", APPROVAL).code());
    }
}