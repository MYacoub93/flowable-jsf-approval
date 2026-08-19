package com.example.approval.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration of the BPM audit subsystem ({@code bpm.audit.*} in
 * application.yml).
 *
 * <pre>
 * bpm:
 *   audit:
 *     id-strategy: MAX_PLUS_ONE   # or SEQUENCE
 *     document-codes:
 *       clearanceLetterProcess: 1
 *       expenseProcess: 2
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "bpm.audit")
public class BpmAuditProperties {

    /**
     * Flowable process definition key -> {@code BPM_DOCUMENTS.DOCUMENT_CODE}.
     * Processes without an entry fall back to {@link #defaultDocumentCode}.
     */
    private Map<String, Integer> documentCodes = new HashMap<>();

    /** Used when a process key has no explicit mapping. */
    private Integer defaultDocumentCode = 1;

    /**
     * How the numeric primary keys ({@code CASE_ID} / {@code SERIAL}) are
     * generated. The pre-existing schema ships without Oracle sequences, so
     * {@link IdStrategy#MAX_PLUS_ONE MAX_PLUS_ONE} (allocation guarded by a
     * JVM lock) is the default. Switch to {@link IdStrategy#SEQUENCE
     * SEQUENCE} once the DBA provides the sequences.
     */
    private IdStrategy idStrategy = IdStrategy.MAX_PLUS_ONE;

    /** Oracle sequence backing {@code BPM_AUDIT_LOG.CASE_ID} (SEQUENCE strategy only). */
    private String caseIdSequence = "BPM_AUDIT_LOG_SEQ";

    /** Oracle sequence backing {@code BPM_AUDIT_LOG_DTL.SERIAL} (SEQUENCE strategy only). */
    private String detailSerialSequence = "BPM_AUDIT_LOG_DTL_SEQ";

    /** Oracle sequence backing {@code BPM_CASE_ATTACHMENTS.SERIAL} (SEQUENCE strategy only). */
    private String attachmentSerialSequence = "BPM_CASE_ATTACH_SEQ";

    /**
     * Directory the uploaded binaries are written to; only the resulting
     * content id / name are stored in {@code BPM_CASE_ATTACHMENTS}.
     */
    private String uploadDir = System.getProperty("java.io.tmpdir", ".");

    public Map<String, Integer> getDocumentCodes() {
        return documentCodes;
    }

    public void setDocumentCodes(Map<String, Integer> documentCodes) {
        this.documentCodes = documentCodes;
    }

    public Integer getDefaultDocumentCode() {
        return defaultDocumentCode;
    }

    public void setDefaultDocumentCode(Integer defaultDocumentCode) {
        this.defaultDocumentCode = defaultDocumentCode;
    }

    public IdStrategy getIdStrategy() {
        return idStrategy;
    }

    public void setIdStrategy(IdStrategy idStrategy) {
        this.idStrategy = idStrategy;
    }

    public String getCaseIdSequence() {
        return caseIdSequence;
    }

    public void setCaseIdSequence(String caseIdSequence) {
        this.caseIdSequence = caseIdSequence;
    }

    public String getDetailSerialSequence() {
        return detailSerialSequence;
    }

    public void setDetailSerialSequence(String detailSerialSequence) {
        this.detailSerialSequence = detailSerialSequence;
    }

    public String getAttachmentSerialSequence() {
        return attachmentSerialSequence;
    }

    public void setAttachmentSerialSequence(String attachmentSerialSequence) {
        this.attachmentSerialSequence = attachmentSerialSequence;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /** Resolves the {@code BPM_DOCUMENTS.DOCUMENT_CODE} of a process key. */
    public Integer documentCodeOf(String processDefinitionKey) {
        return documentCodes.getOrDefault(processDefinitionKey, defaultDocumentCode);
    }

    /** Primary-key generation strategy of the {@code BPM_*} audit tables. */
    public enum IdStrategy {

        /**
         * {@code MAX(id) + 1} per table. Works on the pre-existing schema
         * without any Oracle sequences; allocation is serialized inside the
         * JVM ({@code BpmAuditIdAllocator}). Suitable for single-instance
         * deployments - which this JSF monolith is.
         */
        MAX_PLUS_ONE,

        /** Classic {@code seq.NEXTVAL FROM DUAL} (DBA-provided sequences). */
        SEQUENCE
    }
}