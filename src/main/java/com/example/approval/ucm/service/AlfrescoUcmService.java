package com.example.approval.ucm.service;

import com.example.approval.ucm.config.AlfrescoProperties;
import com.example.approval.ucm.exception.AlfrescoUcmException;
import com.example.approval.ucm.model.UcmDocument;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.CmisVersion;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reusable Alfresco/UCM integration service (process-agnostic).
 *
 * <p>Target platform: <b>Alfresco Community/Enterprise 5.2 GA (July 2017)</b>.
 * 5.2 GA exposes CMIS both at {@code /alfresco/cmisatom} (AtomPub binding,
 * the default) and at the CMIS 1.1 browser-binding endpoint; the client
 * talks to it through Apache Chemistry OpenCMIS with the binding selected
 * via {@code alfresco.binding}. No newer Alfresco REST API (post-5.2) is
 * used, so the service also works against older 5.x releases.</p>
 *
 * <p>Any Flowable process (Student Proof Certificate, Clearance, future
 * ones) calls {@link #archiveDocument(UcmUploadRequest)} and receives a
 * {@link UcmDocument} whose values it stores as its own process variables.
 * Nothing in this class references a specific process.</p>
 *
 * <p>The {@link UcmDocument#getDownloadUrl()} returned to callers is an
 * <b>application-level</b> URL ({@code /ucm/document/{ref}}) served by
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

    /**
     * Filename-safe timestamp (no ':' characters - the Alfresco cm:name
     * constraint rejects colons and they are illegal on Windows too).
     */
    private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AlfrescoProperties properties;

    public AlfrescoUcmService(AlfrescoProperties properties) {
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Session handling
    // ------------------------------------------------------------------

    /**
     * Creates a new CMIS session per call (stateless; connect/read timeouts
     * are applied through the session parameters). The concrete repository
     * id is resolved from the CMIS service document because the AtomPub
     * binding advertises the real store id instead of the "-default-"
     * alias, so the alias must not be passed as REPOSITORY_ID.
     */
    private Session createSession() {
        Map<String, String> parameters = new HashMap<>();
        if ("browser".equalsIgnoreCase(properties.getBinding())) {
            parameters.put(SessionParameter.BINDING_TYPE, BindingType.BROWSER.value());
            parameters.put(SessionParameter.BROWSER_URL, properties.getCmisUrl());
        } else {
            parameters.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
            parameters.put(SessionParameter.ATOMPUB_URL, properties.getCmisUrl());
        }
        parameters.put(SessionParameter.USER, properties.getUsername());
        parameters.put(SessionParameter.PASSWORD, properties.getPassword());
        parameters.put(SessionParameter.CONNECT_TIMEOUT,
                String.valueOf(properties.getConnectTimeout()));
        parameters.put(SessionParameter.READ_TIMEOUT,
                String.valueOf(properties.getReadTimeout()));
        parameters.put(SessionParameter.LOCALE_ISO639_LANGUAGE, "en");
        try {
            List<Repository> repositories = SESSION_FACTORY.getRepositories(parameters);
            if (repositories == null || repositories.isEmpty()) {
                throw new AlfrescoUcmException("No CMIS repository advertised by "
                        + properties.getCmisUrl() + ".");
            }
            Repository target = repositories.get(0);
            String configured = properties.getRepositoryId();
            if (configured != null && !configured.isBlank()
                    && !"-default-".equals(configured)) {
                for (Repository repo : repositories) {
                    if (configured.equals(repo.getId())) {
                        target = repo;
                        break;
                    }
                }
            }
            log.debug("Connecting to Alfresco/UCM repository '{}' ({}) via {} binding",
                    target.getId(), target.getName(), properties.getBinding());
            Session session = target.createSession();
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

        // The cm:titled aspect (cm:title / cm:description metadata) requires
        // CMIS 1.1 secondary types. The legacy /cmisatom AtomPub endpoint
        // speaks CMIS 1.0, where P:cm:titled is not a secondary type and the
        // upload is rejected - so the aspect is only applied when the
        // repository reports CMIS 1.1; otherwise the metadata stays in the
        // audit log and the (unique) stored filename.
        boolean supportsAspects =
                session.getRepositoryInfo().getCmisVersion() == CmisVersion.CMIS_1_1;

        Document doc;
        try {
            Map<String, Object> properties = baseProperties(name);
            if (supportsAspects) {
                properties.put("cm:title", request.getFileName());
                properties.put("cm:description", description);
                List<String> aspects = new ArrayList<>();
                aspects.add(ASPECT_TITLED);
                properties.put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS, aspects);
            }
            doc = targetFolder.createDocument(properties, contentStream,
                    VersioningState.MAJOR);
        } catch (CmisBaseException aspectFailure) {
            // retry without the aspect - some folder types/custom models do
            // not accept it; the metadata then lives in the audit log only
            log.warn("Archive with {} aspect failed ({}), retrying without it",
                    ASPECT_TITLED, aspectFailure.getMessage());
            try {
                ContentStream retryStream = session.getObjectFactory().createContentStream(
                        name, length, request.getMimeType(),
                        new ByteArrayInputStream(request.getContent()));
                doc = targetFolder.createDocument(baseProperties(name), retryStream,
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

    /**
     * Application-level URL under which a stored document can be fetched.
     *
     * <p>The CMIS id is carried as a <b>Base64url</b> token (no padding)
     * because the raw id contains {@code / : ;} characters: percent-encoded
     * slashes are rejected by Tomcat and a semicolon is stripped as a path
     * parameter, so the raw/percent-encoded form can never round-trip
     * through a URL path. Base64url uses only {@code A-Z a-z 0-9 - _}.</p>
     */
    public String getDocumentLink(String documentId) {
        require(documentId, "documentId");
        return APP_DOCUMENT_URL_TEMPLATE + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(documentId.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Resolves the CMIS document id carried by an application-level document
     * URL path segment. Understands both the current Base64url token and
     * (for links issued before this scheme existed) a percent-encoded CMIS
     * id.
     *
     * @throws AlfrescoUcmException when the reference is malformed
     */
    public String decodeDocumentRef(String ref) {
        require(ref, "ref");
        String token = ref.contains("%")
                ? URLDecoder.decode(ref, StandardCharsets.UTF_8)
                : ref;
        if (token.contains("://")) {
            return token; // legacy percent-encoded (or raw) CMIS id
        }
        String id;
        try {
            id = new String(Base64.getUrlDecoder().decode(token),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new AlfrescoUcmException("Not a valid document reference", e);
        }
        if (id.isBlank() || !id.contains("://")) {
            throw new AlfrescoUcmException("Not a valid document reference");
        }
        return id;
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
        // strip characters the Alfresco cm:name constraint rejects
        // (backslash, slash, colon, asterisk, question mark, quote,
        // angle brackets and pipe are not valid in file names)
        String safe = fileName.replaceAll("[\\\\\\\\/:*?\"<>|]", "_").trim();
        if (safe.isEmpty()) {
            safe = "document";
        }
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        return base + "-" + LocalDateTime.now().format(FILENAME_TIMESTAMP_FORMAT)
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
