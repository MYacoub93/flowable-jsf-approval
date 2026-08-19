package com.example.approval.audit.service;

import com.example.approval.audit.BpmAuditAction;
import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.BpmAuditProperties;
import com.example.approval.audit.model.BpmCaseAttachment;
import com.example.approval.audit.service.BpmAuditIdAllocator;
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
 * {@code BPM_CASE_ATTACHMENTS} table.
 *
 * <p>The binary itself is stored in a content directory on disk; this
 * service only records the linkage row ({@code CONTENT_ID} +
 * {@code CONTENT_NAME}) plus the standard audit columns ({@code ENTRY_USER},
 * {@code ENTRY_DATE}, {@code TERMINAL}, {@code OS_USER}).</p>
 */
@Service
public class AttachmentAuditService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentAuditService.class);

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
     * @param processInstanceId process instance the file belongs to
     * @param contentId         repository id of the stored content
     * @param contentName       original file name
     * @param uploadedBy        username of the uploader
     * @return the persisted row (serial populated)
     */
    public BpmCaseAttachment registerAttachment(String processInstanceId,
                                                String contentId,
                                                String contentName,
                                                String uploadedBy) {
        Long caseId = auditService.caseIdOfProcessInstance(processInstanceId);
        if (caseId == null) {
            throw new IllegalStateException("No bpmCaseId variable on process instance "
                    + processInstanceId + " - cannot register attachment");
        }

        BpmCaseAttachment attachment = new BpmCaseAttachment();
        attachment.setSerial(idAllocator.nextAttachmentSerial());
        attachment.setCaseId(caseId);
        attachment.setContentId(truncate(contentId, BpmAuditConstants.CONTENT_ID_MAX_LENGTH));
        attachment.setContentName(truncate(contentName, BpmAuditConstants.CONTENT_NAME_MAX_LENGTH));
        attachment.setEntryUser(numericUserOf(uploadedBy));
        attachment.setEntryDate(LocalDateTime.now());
        attachment.setTerminal(truncate(terminal(), BpmAuditConstants.TERMINAL_MAX_LENGTH_ATTACHMENT));
        attachment.setOsUser(truncate(System.getProperty("user.name", "unknown"),
                BpmAuditConstants.OS_USER_MAX_LENGTH_ATTACHMENT));
        bpmAuditMapper.insertCaseAttachment(attachment);

        // the upload itself is also an audited case action
        auditService.logProcessAction(processInstanceId,
                BpmAuditAction.ATTACHMENT_UPLOADED.name(),
                null, null, uploadedBy, null,
                "Attachment '" + contentName + "' uploaded (content id " + contentId + ")");

        log.info("BPM audit: attachment '{}' (content {}) registered on case {}",
                contentName, contentId, caseId);
        return attachment;
    }

    /** All attachments of the case behind a process instance, oldest first. */
    public List<BpmCaseAttachment> findAttachmentsOfProcessInstance(String processInstanceId) {
        Long caseId = auditService.caseIdOfProcessInstance(processInstanceId);
        return caseId != null
                ? bpmAuditMapper.findAttachmentsByCaseId(caseId)
                : List.of();
    }

    /** Single attachment row by its {@code SERIAL} primary key. */
    public BpmCaseAttachment findAttachmentBySerial(Long serial) {
        return bpmAuditMapper.findAttachmentBySerial(serial);
    }

    /**
     * Writes the uploaded binary to the upload directory and returns the
     * generated content id (UUID file name) that is stored in
     * {@code BPM_CASE_ATTACHMENTS.CONTENT_ID}.
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

    private Integer numericUserOf(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return bpmAuditMapper.findNumericUserId(username);
        } catch (Exception e) {
            log.warn("Could not resolve numeric user id of '{}' - storing null", username, e);
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