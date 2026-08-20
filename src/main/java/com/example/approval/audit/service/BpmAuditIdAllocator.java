package com.example.approval.audit.service;

import com.example.approval.audit.BpmAuditProperties;
import com.example.approval.audit.BpmAuditProperties.IdStrategy;
import com.example.approval.mapper.BpmAuditMapper;
import org.springframework.stereotype.Component;

/**
 * Allocates the {@code SERIAL} primary-key columns of the {@code F_BPM_*}
 * audit tables ({@code F_BPM_AUDIT_LOG_DTL.SERIAL} and
 * {@code F_BPM_CASE_ATTACHMENTS.SERIAL}).
 *
 * <p><b>Note:</b> {@code CASE_ID} is <b>not</b> allocated here - it is the
 * Flowable process instance id supplied by the caller (a natural key,
 * {@code VARCHAR2(64)}).</p>
 *
 * <p>Two strategies ({@code bpm.audit.id-strategy}):</p>
 * <ul>
 *   <li>{@code MAX_PLUS_ONE} (default) - {@code MAX(SERIAL) + 1} per table.
 *       The schema ships without Oracle sequences, so this works out of the
 *       box. Allocation is serialized JVM-wide to keep concurrent inserts
 *       collision-free; sufficient for this single-node JSF application.</li>
 *   <li>{@code SEQUENCE} - {@code SELECT seq.NEXTVAL FROM DUAL} against
 *       DBA-provided sequences ({@code bpm.audit.*-serial-sequence}).</li>
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

    /** Next {@code F_BPM_AUDIT_LOG_DTL.SERIAL}. */
    public Long nextDetailSerial() {
        if (properties.getIdStrategy() == IdStrategy.SEQUENCE) {
            return mapper.nextDetailSerial(properties.getDetailSerialSequence());
        }
        synchronized (allocationLock) {
            return mapper.maxDetailSerial() + 1;
        }
    }

    /** Next {@code F_BPM_CASE_ATTACHMENTS.SERIAL}. */
    public Long nextAttachmentSerial() {
        if (properties.getIdStrategy() == IdStrategy.SEQUENCE) {
            return mapper.nextAttachmentSerial(properties.getAttachmentSerialSequence());
        }
        synchronized (allocationLock) {
            return mapper.maxAttachmentSerial() + 1;
        }
    }
}