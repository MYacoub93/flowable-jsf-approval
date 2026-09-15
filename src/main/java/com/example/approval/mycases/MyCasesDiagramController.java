package com.example.approval.mycases;

import com.example.approval.backing.SessionInfoBean;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Serves the <b>read-only</b> process diagram PNG of the My Cases details
 * dialog ({@code GET /my-cases/diagram/{processInstanceId}}).
 *
 * <p><b>Authorization (server-side, per request):</b> the requesting user
 * must be logged in (checked against the {@link SessionInfoBean} session
 * facade, same pattern as {@code UcmDocumentController}) and the process
 * instance must have been <b>started by that user</b> - re-verified inside
 * {@link MyCasesService#generateDiagramImage} on every request, so a
 * crafted id never renders another user's case. The diagram is generated
 * with Flowable's own {@code DefaultProcessDiagramGenerator} (current
 * activities + taken flows highlighted) and is strictly read-only.</p>
 */
@RestController
public class MyCasesDiagramController {

    private static final Logger log = LoggerFactory.getLogger(MyCasesDiagramController.class);

    /** URL pattern of the diagram image of one case. */
    public static final String DIAGRAM_URL_TEMPLATE = "/my-cases/diagram/";

    private final MyCasesService myCasesService;
    private final SessionInfoBean sessionInfo;

    public MyCasesDiagramController(MyCasesService myCasesService,
                                    SessionInfoBean sessionInfo) {
        this.myCasesService = myCasesService;
        this.sessionInfo = sessionInfo;
    }

    @GetMapping(DIAGRAM_URL_TEMPLATE + "{processInstanceId}")
    public ResponseEntity<byte[]> diagram(@PathVariable("processInstanceId") String processInstanceId) {
        if (sessionInfo == null || !sessionInfo.isLoggedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            byte[] png = myCasesService.generateDiagramImage(
                    sessionInfo.getUserId(), sessionInfo.getUsername(), processInstanceId);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(png);
        } catch (SecurityException e) {
            // exists but was started by someone else - do not leak which
            log.warn("Diagram access denied for user {} to process instance {}",
                    sessionInfo.getUserId(), processInstanceId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Forbidden".getBytes(StandardCharsets.UTF_8));
        } catch (FlowableObjectNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}