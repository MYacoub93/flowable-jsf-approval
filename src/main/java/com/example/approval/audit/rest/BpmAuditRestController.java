package com.example.approval.audit.rest;

import com.example.approval.audit.BpmAuditProperties;
import com.example.approval.audit.model.BpmAuditLog;
import com.example.approval.audit.model.BpmAuditLogDtl;
import com.example.approval.audit.model.BpmCaseAttachment;
import com.example.approval.audit.service.AttachmentAuditService;
import com.example.approval.clearance.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for the BPM business audit tables:
 * <ul>
 *   <li>{@code POST /api/audit/{processInstanceId}/attachments} - upload a file,
 *       store it on disk and insert the {@code BPM_CASE_ATTACHMENTS} row + an
 *       {@code ATTACHMENT_UPLOADED} action in {@code BPM_AUDIT_LOG_DTL};</li>
 *   <li>{@code GET  /api/audit/{processInstanceId}} - master case record;</li>
 *   <li>{@code GET  /api/audit/{processInstanceId}/details} - full action trail;</li>
 *   <li>{@code GET  /api/audit/{processInstanceId}/attachments} - attachment list;</li>
 *   <li>{@code GET  /api/audit/attachments/{serial}/content} - download binary.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/audit")
public class BpmAuditRestController {

    private static final Logger log = LoggerFactory.getLogger(BpmAuditRestController.class);

    private final AttachmentAuditService attachmentAuditService;
    private final AuditService auditService;
    private final BpmAuditProperties properties;

    public BpmAuditRestController(AttachmentAuditService attachmentAuditService,
                                  AuditService auditService,
                                  BpmAuditProperties properties) {
        this.attachmentAuditService = attachmentAuditService;
        this.auditService = auditService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------

    @PostMapping(value = "/{processInstanceId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BpmCaseAttachment> uploadAttachment(@PathVariable String processInstanceId,
                                                              @RequestParam("file") MultipartFile file,
                                                              @RequestParam("username") String username) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String contentId = attachmentAuditService.storeFile(file, properties.getUploadDir());
            BpmCaseAttachment saved = attachmentAuditService.registerAttachment(
                    processInstanceId, contentId, file.getOriginalFilename(), username);
            return ResponseEntity.ok(saved);
        } catch (IOException | IllegalStateException e) {
            log.warn("Attachment upload rejected for {}: {}", processInstanceId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{processInstanceId}/attachments")
    public List<BpmCaseAttachment> listAttachments(@PathVariable String processInstanceId) {
        return attachmentAuditService.findAttachmentsOfProcessInstance(processInstanceId);
    }

    @GetMapping("/attachments/{serial}/content")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long serial) {
        BpmCaseAttachment meta = attachmentAuditService.findAttachmentBySerial(serial);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = Paths.get(properties.getUploadDir(), meta.getContentId());
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = meta.getContentName() != null && meta.getContentName().toLowerCase().endsWith(".pdf")
                ? MediaType.APPLICATION_PDF_VALUE : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getContentName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    // ------------------------------------------------------------------
    // Audit trail
    // ------------------------------------------------------------------

    @GetMapping("/{processInstanceId}")
    public ResponseEntity<BpmAuditLog> getCase(@PathVariable String processInstanceId) {
        BpmAuditLog master = auditService.findCaseOfProcessInstance(processInstanceId);
        return master != null ? ResponseEntity.ok(master) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{processInstanceId}/details")
    public List<BpmAuditLogDtl> getDetails(@PathVariable String processInstanceId) {
        return auditService.findDetailsOfProcessInstance(processInstanceId);
    }
}