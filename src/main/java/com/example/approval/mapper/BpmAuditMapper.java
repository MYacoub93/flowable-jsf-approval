package com.example.approval.mapper;

import com.example.approval.audit.model.BpmAuditLog;
import com.example.approval.audit.model.BpmAuditLogDtl;
import com.example.approval.audit.model.BpmCaseAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for the Oracle {@code F_BPM_*} business audit tables
 * (external/Oracle datasource, schema {@code MEU}):
 *
 * <ul>
 *   <li>{@code F_BPM_AUDIT_LOG} - one master row per started process
 *       instance (case), keyed by {@code CASE_ID};</li>
 *   <li>{@code F_BPM_AUDIT_LOG_DTL} - one row per audited workflow action,
 *       PK {@code (SERIAL, CASE_ID, ACTION_CODE)};</li>
 *   <li>{@code F_BPM_CASE_ATTACHMENTS} - one row per uploaded attachment,
 *       PK {@code (SERIAL, CASE_ID, CONTENT_ID)};</li>
 *   <li>{@code BPM_DOCUMENTS} / {@code BPM_ACTIONS} - lookups.</li>
 * </ul>
 *
 * <p><b>Case linkage:</b> {@code CASE_ID} is a {@code VARCHAR2(64)} holding
 * the Flowable process instance id - it is always supplied by the caller
 * and never generated. Only the {@code SERIAL} columns are allocated by
 * {@code BpmAuditIdAllocator}: either {@code MAX(id) + 1} (default) or a
 * DBA-provided sequence ({@code bpm.audit.id-strategy=SEQUENCE}).</p>
 */
@Mapper
public interface BpmAuditMapper {

    // ------------------------------------------------------------------
    // Serial allocation (MAX_PLUS_ONE / SEQUENCE strategies)
    // ------------------------------------------------------------------

    /** Current max {@code F_BPM_AUDIT_LOG_DTL.SERIAL} (MAX_PLUS_ONE strategy). */
    Long maxDetailSerial();

    /** Current max {@code F_BPM_CASE_ATTACHMENTS.SERIAL} (MAX_PLUS_ONE strategy). */
    Long maxAttachmentSerial();

    /** Next value of the detail serial sequence (Oracle SELECT seq.NEXTVAL FROM DUAL). */
    Long nextDetailSerial(@Param("sequenceName") String sequenceName);

    /** Next value of the attachment serial sequence. */
    Long nextAttachmentSerial(@Param("sequenceName") String sequenceName);

    // ------------------------------------------------------------------
    // F_BPM_AUDIT_LOG (master case record)
    // ------------------------------------------------------------------

    /**
     * Inserts the master row of a case. {@code CASE_ID} must be the Flowable
     * process instance id supplied by the caller.
     */
    int insertAuditLog(BpmAuditLog record);

    /** Master row of a case; {@code caseId} = process instance id. */
    BpmAuditLog findAuditLogByCaseId(@Param("caseId") String caseId);

    // ------------------------------------------------------------------
    // F_BPM_AUDIT_LOG_DTL (actions)
    // ------------------------------------------------------------------

    int insertAuditLogDtl(BpmAuditLogDtl record);

    /** All actions of a case ({@code caseId} = process instance id), oldest first. */
    List<BpmAuditLogDtl> findDetailsByCaseId(@Param("caseId") String caseId);

    // ------------------------------------------------------------------
    // F_BPM_CASE_ATTACHMENTS
    // ------------------------------------------------------------------

    int insertCaseAttachment(BpmCaseAttachment record);

    /** All attachments of a case ({@code caseId} = process instance id), oldest first. */
    List<BpmCaseAttachment> findAttachmentsByCaseId(@Param("caseId") String caseId);

    /** Single attachment row by its composite key {@code (SERIAL, CASE_ID)}. */
    BpmCaseAttachment findAttachmentBySerialAndCaseId(@Param("serial") Long serial,
                                                      @Param("caseId") String caseId);

    /** Single attachment row by its {@code SERIAL} (table-wide allocated, downloads). */
    BpmCaseAttachment findAttachmentBySerial(@Param("serial") Long serial);

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /** Resolves the numeric user id of a Flowable username (FLOWABLE_USERS_VW.ID_). */
    Integer findNumericUserId(@Param("username") String username);

    /** DOCUMENT_CODE of a process definition key, resolved via BPM_DOCUMENTS mapping. */
    Integer findDocumentCode(@Param("documentName") String documentName);
}