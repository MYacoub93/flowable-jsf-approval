package com.example.approval.audit.service;

import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.BpmAuditProperties;
import com.example.approval.audit.model.BpmCaseAttachment;
import com.example.approval.clearance.service.AuditService;
import com.example.approval.mapper.BpmAuditMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Registers uploaded case attachments in the pre-existing Oracle
 * {@code F_BPM_CASE_ATTACHMENTS} table.
 *
 * <p><b>Case linkage:</b> {@code CASE_ID} is the Flowable process instance
 * id supplied by the caller (never generated). The binary itself is stored
 * in a content directory on disk; this service only records the linkage row
 * ({@code CONTENT_ID} + {@code CONTENT_NAME}) plus the standard audit
 * columns ({@code ENTRY_USER}, {@code ENTRY_DATE}, {@code TERMINAL},
 * {@code OS_USER}).</p>
 */
@Service
public class AttachmentAuditService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentAuditService.class);

    /** Fallback for the NOT NULL {@code ENTRY_USER} column. */
    private static final int UNKNOWN_USER_ID = 0;

    /**
     * Semantic action key of the audit row written next to the attachment
     * insert ({@code BPM_ACTIONS} has no dedicated upload code - the impl
     * maps it onto {@code ENTERED} (0) and the note carries the file info).
     */
    private static final String ACTION_ATTACHMENT_UPLOADED = "ATTACHMENT_UPLOADED";

    private final BpmAuditMapper bpmAuditMapper;
    private final BpmAuditProperties properties;
    private final BpmAuditIdAllocator idAllocator;
    private final AuditService auditService;

    public AttachmentAuditService(BpmAuditMapper bpmAuditMapper,
                                  BpmAuditProperties properties,
                                  BpmAuditIdAllocator idAllocator,
                                  AuditService auditService) {
        this.bpmAuditMapper = bpmAuditMapper;
        this.properties = properties;
        this.idAllocator = idAllocator;
        this.auditService = auditService;
    }

    /**
     * Records one uploaded attachment.
     *
     * @param processInstanceId Flowable process instance (used directly as CASE_ID)
     * @param contentId         repository id of the stored content
     * @param contentName       original file name
     * @param uploadedBy        username of the uploader
     * @return the persisted row (serial populated)
     */
    public BpmCaseAttachment registerAttachment(String processInstanceId,
                                                String contentId,
                                                String contentName,
                                                String uploadedBy) {
        if (!properties.isEnabled()) {
            log.debug("BPM audit disabled (bpm.audit.enabled=false) - skipping attachment"
                    + " registration for case {}", processInstanceId);
            return null;
        }
        String caseId = requireCaseId(processInstanceId);

        BpmCaseAttachment attachment = new BpmCaseAttachment();
        attachment.setSerial(idAllocator.nextAttachmentSerial());
        attachment.setCaseId(caseId);
        attachment.setContentId(truncate(contentId, BpmAuditConstants.CONTENT_ID_MAX_LENGTH));
        attachment.setContentName(truncate(contentName, BpmAuditConstants.CONTENT_NAME_MAX_LENGTH));
        Integer entryUser = numericUserOf(uploadedBy);
        attachment.setEntryUser(entryUser != null ? entryUser : UNKNOWN_USER_ID);
        attachment.setEntryDate(LocalDateTime.now());
        attachment.setTerminal(truncate(terminal(), BpmAuditConstants.TERMINAL_MAX_LENGTH_ATTACHMENT));
        attachment.setOsUser(truncate(System.getProperty("user.name", "unknown"),
                BpmAuditConstants.OS_USER_MAX_LENGTH_ATTACHMENT));
        bpmAuditMapper.insertCaseAttachment(attachment);

        // the upload itself is also an audited case action
        auditService.logProcessAction(processInstanceId,
                ACTION_ATTACHMENT_UPLOADED,
                null, null, uploadedBy, null,
                "Attachment '" + contentName + "' uploaded (content id " + contentId + ")");

        log.info("BPM audit: attachment '{}' (content {}) registered on case {}",
                contentName, contentId, caseId);
        return attachment;
    }

    /** All attachments of the case behind a process instance, oldest first. */
    public List<BpmCaseAttachment> findAttachmentsOfProcessInstance(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return List.of();
        }
        return bpmAuditMapper.findAttachmentsByCaseId(processInstanceId);
    }

    /** Single attachment row by its {@code SERIAL} primary key. */
    public BpmCaseAttachment findAttachmentBySerial(Long serial) {
        return bpmAuditMapper.findAttachmentBySerial(serial);
    }

    /**
     * Writes the uploaded binary to the upload directory and returns the
     * generated content id (UUID file name) that is stored in
     * {@code F_BPM_CASE_ATTACHMENTS.CONTENT_ID}.
     */
    public String storeFile(MultipartFile file, String uploadDir) throws IOException {
        String contentId = UUID.randomUUID().toString();
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(contentId),
                StandardCopyOption.REPLACE_EXISTING);
        return contentId;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * CASE_ID must always be the caller-supplied process instance id of the
     * Flowable case that was started - there is nothing to allocate.
     */
    private String requireCaseId(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalStateException("Missing processInstanceId (business CASE_ID) "
                    + "- cannot register attachment");
        }
        return truncate(processInstanceId, BpmAuditConstants.CASE_ID_MAX_LENGTH);
    }

    private Integer numericUserOf(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return bpmAuditMapper.findNumericUserId(username);
        } catch (Exception e) {
            log.warn("Could not resolve numeric user id of '{}' - storing fallback {}", username,
                    UNKNOWN_USER_ID, e);
            return null;
        }
    }

    private String terminal() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}