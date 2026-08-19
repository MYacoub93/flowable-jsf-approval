package com.example.approval.audit.service;

import com.example.approval.audit.BpmAuditProperties;
import com.example.approval.audit.BpmAuditProperties.IdStrategy;
import com.example.approval.mapper.BpmAuditMapper;
import org.springframework.stereotype.Component;

/**
 * Allocates the numeric primary keys of the {@code BPM_*} audit tables
 * ({@code BPM_AUDIT_LOG.CASE_ID}, {@code BPM_AUDIT_LOG_DTL.SERIAL},
 * {@code BPM_CASE_ATTACHMENTS.SERIAL}).
 *
 * <p>Two strategies ({@code bpm.audit.id-strategy}):</p>
 * <ul>
 *   <li>{@code MAX_PLUS_ONE} (default) - {@code MAX(id) + 1} per table. The
 *       pre-existing schema ships without Oracle sequences, so this works
 *       out of the box. Allocation is serialized JVM-wide to keep
 *       concurrent inserts collision-free; sufficient for this single-node
 *       JSF application.</li>
 *   <li>{@code SEQUENCE} - {@code SELECT seq.NEXTVAL FROM DUAL} against
 *       DBA-provided sequences ({@code bpm.audit.*-sequence}).</li>
 * </ul>
 */
@Component
public class BpmAuditIdAllocator {

    private final BpmAuditMapper mapper;
    private final BpmAuditProperties properties;

    /** Single lock shared by all MAX_PLUS_ONE allocations (audit volume is low). */
    private final Object allocationLock = new Object();

    public BpmAuditIdAllocator(BpmAuditMapper mapper, BpmAuditProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /** Next {@code BPM_AUDIT_LOG.CASE_ID}. */
    public Long nextCaseId() {
        if (properties.getIdStrategy() == IdStrategy.SEQUENCE) {
            return mapper.nextCaseId(properties.getCaseIdSequence());
        }
        synchronized (allocationLock) {
            Long next = mapper.maxCaseId() + 1;
            return next;
        }
    }

    /** Next {@code BPM_AUDIT_LOG_DTL.SERIAL}. */
    public Long nextDetailSerial() {
        if (properties.getIdStrategy() == IdStrategy.SEQUENCE) {
            return mapper.nextDetailSerial(properties.getDetailSerialSequence());
        }
        synchronized (allocationLock) {
            return mapper.maxDetailSerial() + 1;
        }
    }

    /** Next {@code BPM_CASE_ATTACHMENTS.SERIAL}. */
    public Long nextAttachmentSerial() {
        if (properties.getIdStrategy() == IdStrategy.SEQUENCE) {
            return mapper.nextAttachmentSerial(properties.getAttachmentSerialSequence());
        }
        synchronized (allocationLock) {
            return mapper.maxAttachmentSerial() + 1;
        }
    }
}