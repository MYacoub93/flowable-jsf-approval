package com.example.approval.ucm.rest;

import com.example.approval.backing.SessionInfoBean;
import com.example.approval.ucm.exception.AlfrescoUcmException;
import com.example.approval.ucm.model.UcmDocument;
import com.example.approval.ucm.service.AlfrescoUcmService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.List;

/**
 * Serves documents archived in Alfresco/UCM under the application-level
 * links produced by {@link AlfrescoUcmService#getDocumentLink(String)}
 * ({@code GET /ucm/document/{ref}}), where {@code ref} is the Base64url
 * form of the CMIS document id.
 *
 * <p><b>Authorization (server-side, per request):</b> the requesting user
 * must be logged in (checked against the {@link SessionInfoBean} session
 * facade) and must be <b>involved</b> in the Flowable process instance that
 * owns the document - i.e. either the process starter (the student who
 * initiated the request) or the assignee of one of its (historic) tasks
 * (the department employee who processed the request). The owning instance
 * is located through the <b>process-variable convention</b>
 * {@code documentId}: every process that archives through
 * {@link AlfrescoUcmService} stores the returned CMIS id under this
 * variable name (see {@code StudentProofConstants.VAR_DOCUMENT_ID}), so the
 * lookup stays process-agnostic and reusable by future processes.</p>
 *
 * <p>No internal Alfresco URL or credential is ever exposed - the content
 * is proxied through this application endpoint.</p>
 */
@RestController
public class UcmDocumentController {

    private static final Logger log = LoggerFactory.getLogger(UcmDocumentController.class);

    /**
     * Process-variable convention under which archived documents are
     * registered to their owning process instance.
     */
    public static final String DOCUMENT_ID_VARIABLE = "documentId";

    private final AlfrescoUcmService alfrescoUcmService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final SessionInfoBean sessionInfo;

    public UcmDocumentController(AlfrescoUcmService alfrescoUcmService,
                                 RuntimeService runtimeService,
                                 HistoryService historyService,
                                 SessionInfoBean sessionInfo) {
        this.alfrescoUcmService = alfrescoUcmService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.sessionInfo = sessionInfo;
    }

    @GetMapping(AlfrescoUcmService.APP_DOCUMENT_URL_TEMPLATE + "{ref}")
    public ResponseEntity<Resource> download(@PathVariable("ref") String ref) {
        // 1) authentication - same session model the JSF layer uses
        if (!sessionInfo.isLoggedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2) resolve the CMIS id from the URL-safe reference
        String documentId;
        try {
            documentId = alfrescoUcmService.decodeDocumentRef(ref);
        } catch (AlfrescoUcmException e) {
            return ResponseEntity.badRequest().build();
        }

        // 3) authorization - involvement in the owning process instance
        if (!isAuthorizedForDocument(documentId)) {
            log.warn("User {} ({}) was denied access to document ref {}",
                    sessionInfo.getUsername(), sessionInfo.getUserId(), ref);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 4) fetch metadata + content and proxy it
        try {
            UcmDocument meta = alfrescoUcmService.getDocument(documentId);
            if (meta == null) {
                return ResponseEntity.notFound().build();
            }
            InputStream content = alfrescoUcmService.getDocumentContent(documentId);
            if (content == null) {
                return ResponseEntity.notFound().build();
            }
            String mime = meta.getMimeType() == null || meta.getMimeType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE : meta.getMimeType();
            // show PDFs/images inline so the "View" link opens them in the
            // browser; everything else is offered as a download
            String disposition = (mime.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)
                    || mime.toLowerCase().startsWith("image/"))
                    ? "inline" : "attachment";
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename=\"" + meta.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(mime));
            if (meta.getLength() > 0) {
                builder.contentLength(meta.getLength());
            }
            return builder.body(new InputStreamResource(content));
        } catch (AlfrescoUcmException e) {
            log.error("Failed to serve document ref {}", ref, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    // ------------------------------------------------------------------
    // Authorization helpers
    // ------------------------------------------------------------------

    /**
     * A document may be downloaded only by users involved in the process
     * instance it belongs to (found via the {@code documentId} process
     * variable, in runtime first and in history second so ended instances
     * keep working).
     */
    private boolean isAuthorizedForDocument(String documentId) {
        List<ProcessInstance> running = runtimeService.createProcessInstanceQuery()
                .variableValueEquals(DOCUMENT_ID_VARIABLE, documentId)
                .list();
        for (ProcessInstance instance : running) {
            if (isUserInvolved(instance.getId())) {
                return true;
            }
        }
        List<HistoricProcessInstance> historic = historyService.createHistoricProcessInstanceQuery()
                .variableValueEquals(DOCUMENT_ID_VARIABLE, documentId)
                .list();
        for (HistoricProcessInstance instance : historic) {
            if (isUserInvolved(instance.getId())) {
                return true;
            }
        }
        return false;
    }

    /** Starter of the instance or assignee of any of its (historic) tasks. */
    private boolean isUserInvolved(String processInstanceId) {
        HistoricProcessInstance historicInstance = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicInstance != null
                && matchesCurrentUser(historicInstance.getStartUserId())) {
            return true;
        }
        // Historic task instances also cover still-active tasks, so this
        // one query is enough for running and ended instances alike.
        return countAssignedTasks(processInstanceId, sessionInfo.getUserId()) > 0
                || countAssignedTasks(processInstanceId, sessionInfo.getUsername()) > 0;
    }

    private long countAssignedTasks(String processInstanceId, String assignee) {
        if (assignee == null || assignee.isBlank()) {
            return 0;
        }
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(assignee)
                .count();
    }

    private boolean matchesCurrentUser(String candidate) {
        return candidate != null
                && (candidate.equals(sessionInfo.getUserId())
                        || candidate.equals(sessionInfo.getUsername()));
    }
}