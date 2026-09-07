package com.example.approval.ucm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the reusable Alfresco/UCM integration
 * ({@code alfresco.*} in application.yml).
 *
 * <p>Target: <b>Alfresco Community/Enterprise 5.2 GA (July 2017)</b>, which
 * speaks CMIS 1.1 via the Browser Binding (AtomPub also available).</p>
 *
 * <p>Credentials should be supplied via environment variables
 * (e.g. {@code ALFRESCO_USERNAME} / {@code ALFRESCO_PASSWORD}) or any other
 * Spring Boot externalized-config mechanism - never hardcoded in the
 * application.yml that is committed to the repository.</p>
 */
@Component
@ConfigurationProperties(prefix = "alfresco")
public class AlfrescoProperties {

    /** Alfresco/UCM server base URL, e.g. {@code http://ucm.example.com:8080/alfresco}. */
    private String baseUrl = "http://localhost:8080/alfresco";

    /** CMIS AtomPub service-document path appended to {@link #baseUrl}. */
    private String cmisPath = "/cmisatom";

    /**
     * CMIS binding to use: {@code atompub} (default; {@code /cmisatom}
     * service document) or {@code browser} (CMIS 1.1 browser binding).
     */
    private String binding = "atompub";

    /**
     * Repository id (or alias) the CMIS session binds to. Alfresco 5.2 GA
     * exposes its single main repository under the reserved alias
     * {@code Repository}; override only for multi-repository setups.
     */
    private String repositoryId = "Repository";

    /** Alfresco admin/service account used for the CMIS session. */
    private String username = "admin";

    /** Password of {@link #username} - override via {@code ALFRESCO_PASSWORD}. */
    private String password = "";

    /** Site short-name (Share site) whose document library is used; blank = repository root. */
    private String site = "";

    /** Folder path inside the site document library, e.g. {@code /StudentProofCertificates}. */
    private String folderPath = "/BPM";

    /** Connect timeout for the CMIS session in milliseconds. */
    private int connectTimeout = 10000;

    /** Message/read timeout for the CMIS session in milliseconds. */
    private int readTimeout = 30000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCmisPath() {
        return cmisPath;
    }

    public void setCmisPath(String cmisPath) {
        this.cmisPath = cmisPath;
    }

    public String getBinding() {
        return binding;
    }

    public void setBinding(String binding) {
        this.binding = binding;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    /** Full CMIS endpoint URL: {@code baseUrl + cmisPath}. */
    public String getCmisUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = cmisPath == null || cmisPath.isBlank() ? "" : cmisPath.trim();
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
}