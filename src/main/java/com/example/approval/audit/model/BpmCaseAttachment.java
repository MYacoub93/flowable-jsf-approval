package com.example.approval.audit.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One uploaded attachment of a case, mapped to {@code BPM_CASE_ATTACHMENTS}.
 */
public class BpmCaseAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** NUMBER(9,0) */
    private Long serial;

    /** NUMBER(9,0) - FK to BPM_AUDIT_LOG.CASE_ID. */
    private Long caseId;

    /** VARCHAR2(1000) - content/document repository id. */
    private String contentId;

    /** VARCHAR2(1000) - human readable file name. */
    private String contentName;

    /** NUMBER(6,0) - numeric user id of the uploader (DIC_USERS.USER_ID). */
    private Integer entryUser;

    /** DATE */
    private LocalDateTime entryDate;

    private String terminal;

    private String osUser;

    // Getters / Setters -------------------------------------------------

    public Long getSerial() {
        return serial;
    }

    public void setSerial(Long serial) {
        this.serial = serial;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getContentName() {
        return contentName;
    }

    public void setContentName(String contentName) {
        this.contentName = contentName;
    }

    public Integer getEntryUser() {
        return entryUser;
    }

    public void setEntryUser(Integer entryUser) {
        this.entryUser = entryUser;
    }

    public LocalDateTime getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDateTime entryDate) {
        this.entryDate = entryDate;
    }

    public String getTerminal() {
        return terminal;
    }

    public void setTerminal(String terminal) {
        this.terminal = terminal;
    }

    public String getOsUser() {
        return osUser;
    }

    public void setOsUser(String osUser) {
        this.osUser = osUser;
    }
}