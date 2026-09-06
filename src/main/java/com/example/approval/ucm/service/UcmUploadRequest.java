package com.example.approval.ucm.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-agnostic upload payload for {@code AlfrescoUcmService}.
 *
 * <p>Each Flowable process fills this in with its own metadata keys (student
 * id, process instance id, document type, ...) - the UCM service stores
 * whatever map it receives.</p>
 */
public class UcmUploadRequest {

    /** Original filename, e.g. {@code proof-certificate.pdf}. */
    private String fileName;

    /** MIME type, e.g. {@code application/pdf}. */
    private String mimeType;

    /** File content. */
    private byte[] content;

    /** Optional sub-folder inside the configured {@code alfresco.folder-path} root. */
    private String folderPath;

    /** User performing the upload (employee username). */
    private String uploadedBy;

    /** Arbitrary metadata stored alongside the document. */
    private Map<String, String> metadata = new LinkedHashMap<>();

    public UcmUploadRequest() {
    }

    public UcmUploadRequest(String fileName, String mimeType, byte[] content, String uploadedBy) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.content = content;
        this.uploadedBy = uploadedBy;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public UcmUploadRequest addMetadata(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }
}