package com.example.approval.ucm.service;

import com.example.approval.ucm.config.AlfrescoProperties;
import com.example.approval.ucm.exception.AlfrescoUcmException;
import com.example.approval.ucm.model.UcmDocument;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reusable Alfresco/UCM integration service (process-agnostic).
 *
 * <p>Target platform: <b>Alfresco Community/Enterprise 5.2 GA (July 2017)</b>.
 * 5.2 GA exposes a full CMIS 1.1 endpoint at
 * {@code /alfresco/api/-default-/public/cmis/versions/1.1/browser}, which is
 * what this client uses through Apache Chemistry OpenCMIS ("Browser
 * Binding"). No newer Alfresco REST API (post-5.2) is used, so the service
 * also works against older 5.x releases.</p>
 *
 * <p>Any Flowable process (Student Proof Certificate, Clearance, future
 * ones) calls {@link #archiveDocument(UcmUploadRequest)} and receives a
 * {@link UcmDocument} whose values it stores as its own process variables.
 * Nothing in this class references a specific process.</p>
 *
 * <p>The {@link UcmDocument#getDownloadUrl()} returned to callers is an
 * <b>application-level</b> URL ({@code /ucm/document/{id}}) served by
 * {@code UcmDocumentController}, which re-checks authorization before
 * proxying the content - the raw Alfresco endpoint/credentials are never
 * exposed to the JSF layer.</p>
 */
@Service
public class AlfrescoUcmService {

    private static final Logger log = LoggerFactory.getLogger(AlfrescoUcmService.class);

    /** Path of the application-level document download endpoint. */
    public static final String APP_DOCUMENT_URL_TEMPLATE = "/ucm/document/";

    /** Alfresco 5.2 aspect providing {@code cm:title}/{@code cm:description}. */
    private static final String ASPECT_TITLED = "P:cm:titled";

    private static final SessionFactory SESSION_FACTORY = SessionFactoryImpl.newInstance();
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final AlfrescoProperties properties;

    public AlfrescoUcmService(AlfrescoProperties properties) {
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Session handling
    // ------------------------------------------------------------------

    /**
     * Creates a new CMIS session per call. This keeps the service stateless
     * (the browser binding caches the authentication token inside the
     * session, so a shared session would need extra synchronization); the
     * connect/read timeouts are applied through the session parameters.
     */
    private Session createSession() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(SessionParameter.BINDING_TYPE, BindingType.BROWSER.value());
        parameters.put(SessionParameter.BROWSER_URL, properties.getCmisUrl());
        parameters.put(SessionParameter.USER, properties.getUsername());
        parameters.put(SessionParameter.PASSWORD, properties.getPassword());
        parameters.put(SessionParameter.CONNECT_TIMEOUT,
                String.valueOf(properties.getConnectTimeout()));
        parameters.put(SessionParameter.READ_TIMEOUT,
                String.valueOf(properties.getReadTimeout()));
        parameters.put(SessionParameter.LOCALE_ISO639_LANGUAGE, "en");
        try {
            Session session = SESSION_FACTORY.createSession(parameters);
            if (session == null) {
                throw new AlfrescoUcmException("CMIS session could not be created (null).");
            }
            return session;
        } catch (CmisBaseException e) {
            throw new AlfrescoUcmException("Could not connect to Alfresco/UCM at "
                    + properties.getCmisUrl() + ": " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Upload / archive
    // ------------------------------------------------------------------

    /**
     * Archives (uploads) a document into the configured Alfresco/UCM folder
     * preserving metadata, and returns the document descriptor.
     *
     * <p>Metadata (student id, process instance id, document type, uploader,
     * upload timestamp, original filename, MIME type, ...) is preserved via
     * the Alfresco {@code cm:titled} aspect ({@code cm:description} summary)
     * and, if that aspect is not available in the target folder's model,
     * via a retry without the aspect so the upload still succeeds.</p>
     *
     * @param request upload payload (filename, mime, content, metadata)
     * @return descriptor with document id, path and the application-level
     *         download URL
     * @throws AlfrescoUcmException on any Alfresco failure
     */
    public UcmDocument archiveDocument(UcmUploadRequest request) {
        require(request.getFileName(), "fileName");
        require(request.getMimeType(), "mimeType");
        require(request.getUploadedBy(), "uploadedBy");
        if (request.getContent() == null || request.getContent().length == 0) {
            throw new AlfrescoUcmException("content is required");
        }

        Session session = createSession();
        Folder targetFolder = resolveTargetFolder(session, request.getFolderPath());
        String name = uniqueName(request.getFileName());
        String description = buildDescription(request);
        long length = request.getContent().length;
        ContentStream contentStream = session.getObjectFactory().createContentStream(
                name, length, request.getMimeType(),
                new ByteArrayInputStream(request.getContent()));

        Document doc;
        try {
            // first attempt: with the cm:titled aspect so the metadata is
            // queryable/visible in Share
            Map<String, Object> withAspect = baseProperties(name);
            withAspect.put("cm:title", request.getFileName());
            withAspect.put("cm:description", description);
            List<String> aspects = new ArrayList<>();
            aspects.add(ASPECT_TITLED);
            withAspect.put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS, aspects);
            doc = targetFolder.createDocument(withAspect, contentStream,
                    VersioningState.MAJOR);
        } catch (CmisBaseException aspectFailure) {
            // retry without the aspect - some folder types/custom models do
            // not accept it; the metadata then lives in the audit log only
            log.warn("Archive with {} aspect failed ({}), retrying without it",
                    ASPECT_TITLED, aspectFailure.getMessage());
            try {
                doc = targetFolder.createDocument(baseProperties(name), contentStream,
                        VersioningState.MAJOR);
            } catch (CmisBaseException e) {
                throw new AlfrescoUcmException("Failed to archive document '"
                        + request.getFileName() + "' to Alfresco/UCM: " + e.getMessage(), e);
            }
        }
        log.info("Archived {} ({} bytes) to Alfresco/UCM folder {} as {} [{}]",
                request.getFileName(), length, targetFolder.getPath(), name, doc.getId());
        return toUcmDocument(doc);
    }

    // ------------------------------------------------------------------
    // Fetch / link generation
    // ------------------------------------------------------------------

    /**
     * Loads a stored document by CMIS id. Authorization must be checked by
     * the caller (see {@code UcmDocumentController}).
     *
     * @return the document descriptor, or {@code null} when not found
     */
    public UcmDocument getDocument(String documentId) {
        require(documentId, "documentId");
        try {
            Session session = createSession();
            CmisObject obj = session.getObject(documentId);
            if (!(obj instanceof Document doc)) {
                return null;
            }
            return toUcmDocument(doc);
        } catch (CmisObjectNotFoundException e) {
            return null;
        } catch (CmisBaseException e) {
            throw new AlfrescoUcmException("Failed to load document " + documentId
                    + " from Alfresco/UCM: " + e.getMessage(), e);
        }
    }

    /**
     * Streams the content of a stored document. Authorization must be
     * checked by the caller.
     *
     * @return the content stream, or {@code null} when the document does not exist
     */
    public InputStream getDocumentContent(String documentId) {
        require(documentId, "documentId");
        try {
            Session session = createSession();
            Document doc = (Document) session.getObject(documentId);
            ContentStream stream = doc.getContentStream();
            return stream != null ? stream.getStream() : null;
        } catch (CmisObjectNotFoundException e) {
            return null;
        } catch (CmisBaseException e) {
            throw new AlfrescoUcmException("Failed to read content of document "
                    + documentId + ": " + e.getMessage(), e);
        }
    }

    /** Application-level URL under which a stored document can be fetched. */
    public String getDocumentLink(String documentId) {
        return APP_DOCUMENT_URL_TEMPLATE
                + URLEncoder.encode(documentId, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Map<String, Object> baseProperties(String name) {
        Map<String, Object> props = new HashMap<>();
        props.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
        props.put(PropertyIds.NAME, name);
        return props;
    }

    private UcmDocument toUcmDocument(Document doc) {
        String mime = doc.getContentStreamMimeType();
        long length = doc.getContentStreamLength();
        String path = doc.getPaths().isEmpty() ? null : doc.getPaths().get(0);
        return new UcmDocument(doc.getId(), path, doc.getName(), mime, length,
                getDocumentLink(doc.getId()));
    }

    private Folder resolveTargetFolder(Session session, String subFolder) {
        String rootPath = properties.getFolderPath();
        if (rootPath == null || rootPath.isBlank()) {
            rootPath = "/";
        }
        String target = rootPath;
        if (subFolder != null && !subFolder.isBlank()) {
            target = joinPath(rootPath, subFolder);
        }
        try {
            CmisObject obj = session.getObjectByPath(target);
            if (obj instanceof Folder folder) {
                return folder;
            }
            throw new AlfrescoUcmException("Alfresco/UCM path '" + target
                    + "' is not a folder.");
        } catch (CmisObjectNotFoundException e) {
            throw new AlfrescoUcmException("Alfresco/UCM folder '" + target
                    + "' does not exist (configure alfresco.folder-path).", e);
        }
    }

    private String joinPath(String root, String sub) {
        String r = root.endsWith("/") ? root : root + "/";
        String s = sub.startsWith("/") ? sub.substring(1) : sub;
        String joined = r + s;
        return joined.endsWith("/") && joined.length() > 1
                ? joined.substring(0, joined.length() - 1) : joined;
    }

    /**
     * Alfresco forbids duplicate names within one folder - make the stored
     * name unique while keeping the original filename in the metadata.
     */
    private String uniqueName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        return base + "-" + LocalDateTime.now().format(TIMESTAMP_FORMAT)
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
    }

    /** Compact one-line description carrying the whole metadata map. */
    private String buildDescription(UcmUploadRequest request) {
        StringBuilder sb = new StringBuilder("Archived by ");
        sb.append(request.getUploadedBy())
                .append(" at ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        Map<String, String> meta = request.getMetadata();
        if (meta != null && !meta.isEmpty()) {
            sb.append(" | ");
            boolean first = true;
            for (Map.Entry<String, String> entry : meta.entrySet()) {
                if (!first) {
                    sb.append("; ");
                }
                sb.append(entry.getKey()).append('=')
                        .append(entry.getValue() == null ? "" : entry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AlfrescoUcmException(field + " is required");
        }
    }
}