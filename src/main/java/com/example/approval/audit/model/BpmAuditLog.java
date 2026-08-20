package com.example.approval.audit.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Maps to Oracle table F_BPM_AUDIT_LOG (master record of a business case).
 *
 * <pre>
 * CASE_ID       VARCHAR2(64) -- primary key = Flowable process instance id (caller-supplied)
 * REQUESTOR_ID  NUMBER(9)    -- numeric id of the employee who initiated the case
 * DOCUMENT_CODE NUMBER(3)    -- process/document type (FK to BPM_DOCUMENTS)
 * ENTRY_USER    NUMBER(7)    -- numeric id of the user who inserted the row
 * ENTRY_DATE    DATE         -- insertion timestamp
 * TERMINAL      VARCHAR2(100)
 * OS_USER       VARCHAR2(100)
 * </pre>
 */
public class BpmAuditLog {

    /** Flowable process instance id supplied by the caller (never generated). */
    private String caseId;
    private Long requestorId;
    private Integer documentCode;
    private Integer entryUser;
    private LocalDateTime entryDate;
    private String terminal;
    private String osUser;

    public BpmAuditLog() {
    }

    public BpmAuditLog(String caseId, Long requestorId, Integer documentCode,
                       Integer entryUser, LocalDateTime entryDate, String terminal, String osUser) {
        this.caseId = caseId;
        this.requestorId = requestorId;
        this.documentCode = documentCode;
        this.entryUser = entryUser;
        this.entryDate = entryDate;
        this.terminal = terminal;
        this.osUser = osUser;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public Long getRequestorId() {
        return requestorId;
    }

    public void setRequestorId(Long requestorId) {
        this.requestorId = requestorId;
    }

    public Integer getDocumentCode() {
        return documentCode;
    }

    public void setDocumentCode(Integer documentCode) {
        this.documentCode = documentCode;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BpmAuditLog that = (BpmAuditLog) o;
        return Objects.equals(caseId, that.caseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId);
    }

    @Override
    public String toString() {
        return "BpmAuditLog{caseId='" + caseId + '\''
                + ", requestorId=" + requestorId
                + ", documentCode=" + documentCode
                + ", entryUser=" + entryUser
                + ", entryDate=" + entryDate
                + ", terminal='" + terminal + '\''
                + ", osUser='" + osUser + '\'' + '}';
    }
}