package com.example.approval.ucm.model;

import java.io.Serializable;

/**
 * Result of an {@link com.example.approval.ucm.service.AlfrescoUcmService}
 * archive/upload operation: the CMIS identifier of the stored document plus
 * everything the caller needs to fetch it again later.
 *
 * <p>Process-agnostic by design - any Flowable process (Student Proof
 * Certificate, Clearance, ...) stores these values as its own process
 * variables.</p>
 */
public class UcmDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /** CMIS object id of the stored document (e.g. {@code workspace://SpacesStore/...}). */
    private final String documentId;

    /** CMIS object path inside the repository (e.g. {@code /Sites/.../document.pdf}). */
    private final String path;

    /** Name under which the content was stored (original filename). */
    private final String fileName;

    /** MIME type of the stored content. */
    private final String mimeType;

    /** Length of the stored content in bytes. */
    private final long length;

    /** Application-level download URL that proxies the document through this app. */
    private final String downloadUrl;

    public UcmDocument(String documentId, String path, String fileName, String mimeType,
                       long length, String downloadUrl) {
        this.documentId = documentId;
        this.path = path;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.length = length;
        this.downloadUrl = downloadUrl;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getPath() {
        return path;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getLength() {
        return length;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    @Override
    public String toString() {
        return "UcmDocument{documentId='" + documentId + '\'' + ", fileName='" + fileName
                + '\'' + ", mimeType='" + mimeType + "', length=" + length + '}';
    }
}