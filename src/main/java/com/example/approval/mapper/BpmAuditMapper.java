package com.example.approval.mapper;

import com.example.approval.audit.model.BpmAuditLog;
import com.example.approval.audit.model.BpmAuditLogDtl;
import com.example.approval.audit.model.BpmCaseAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for the pre-existing Oracle {@code BPM_*} business audit
 * tables (external/Oracle datasource):
 *
 * <ul>
 *   <li>{@code BPM_AUDIT_LOG} - one master row per process instance (case);</li>
 *   <li>{@code BPM_AUDIT_LOG_DTL} - one row per audited workflow action;</li>
 *   <li>{@code BPM_CASE_ATTACHMENTS} - one row per uploaded attachment;</li>
 *   <li>{@code BPM_DOCUMENTS} / {@code BPM_ACTIONS} - lookups.</li>
 * </ul>
 *
 * <p>Primary keys ({@code CASE_ID}, {@code SERIAL}) are allocated by
 * {@code BpmAuditIdAllocator}: either {@code MAX(id) + 1} (default - the
 * pre-existing schema has no Oracle sequences) or a DBA-provided sequence
 * ({@code bpm.audit.id-strategy=SEQUENCE}, see BpmAuditProperties).</p>
 */
@Mapper
public interface BpmAuditMapper {

    // ------------------------------------------------------------------
    // Id allocation
    // ------------------------------------------------------------------

    /** Current max {@code BPM_AUDIT_LOG.CASE_ID} (MAX_PLUS_ONE strategy). */
    Long maxCaseId();

    /** Current max {@code BPM_AUDIT_LOG_DTL.SERIAL} (MAX_PLUS_ONE strategy). */
    Long maxDetailSerial();

    /** Current max {@code BPM_CASE_ATTACHMENTS.SERIAL} (MAX_PLUS_ONE strategy). */
    Long maxAttachmentSerial();

    /** Next value of the case-id sequence (Oracle SELECT seq.NEXTVAL FROM DUAL). */
    Long nextCaseId(@Param("sequenceName") String sequenceName);

    /** Next value of the detail serial sequence. */
    Long nextDetailSerial(@Param("sequenceName") String sequenceName);

    /** Next value of the attachment serial sequence. */
    Long nextAttachmentSerial(@Param("sequenceName") String sequenceName);

    int insertAuditLog(BpmAuditLog record);

    BpmAuditLog findAuditLogByCaseId(@Param("caseId") Long caseId);

    // ------------------------------------------------------------------
    // BPM_AUDIT_LOG_DTL (actions)
    // ------------------------------------------------------------------

    int insertAuditLogDtl(BpmAuditLogDtl record);

    /** All actions of a case, oldest first. */
    List<BpmAuditLogDtl> findDetailsByCaseId(@Param("caseId") Long caseId);

    // ------------------------------------------------------------------
    // BPM_CASE_ATTACHMENTS
    // ------------------------------------------------------------------

    int insertCaseAttachment(BpmCaseAttachment record);

    /** All attachments of a case, oldest first. */
    List<BpmCaseAttachment> findAttachmentsByCaseId(@Param("caseId") Long caseId);

    /** Single attachment row by its SERIAL primary key. */
    BpmCaseAttachment findAttachmentBySerial(@Param("serial") Long serial);

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /** Resolves the numeric user id of a Flowable username (FLOWABLE_USERS_VW.ID_). */
    Integer findNumericUserId(@Param("username") String username);

    /** DOCUMENT_CODE of a process definition key, resolved via BPM_DOCUMENTS mapping. */
    Integer findDocumentCode(@Param("documentName") String documentName);
}