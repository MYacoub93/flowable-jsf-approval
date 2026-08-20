package com.example.approval.audit.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One audited action of a case, mapped to {@code F_BPM_AUDIT_LOG_DTL}
 * (primary key: {@code SERIAL}, {@code CASE_ID}, {@code ACTION_CODE}).
 * Every task completion / rejection / amendment etc. adds one row here.
 */
public class BpmAuditLogDtl implements Serializable {

    private static final long serialVersionUID = 1L;

    /** NUMBER(9,0) */
    private Long serial;

    /** VARCHAR2(64) - Flowable process instance id supplied by the caller. */
    private String caseId;

    /** NUMBER(4,0) - FK to BPM_ACTIONS.ACTION_CODE. */
    private Integer actionCode;

    /** VARCHAR2(500) */
    private String note;

    /** NUMBER(7,0) - numeric user id of the actor (DIC_USERS.USER_ID). */
    private Integer entryUser;

    private LocalDateTime entryDate;

    private String terminal;

    private String osUser;

    /** NUMBER DEFAULT 0 - 1 when this detail row closes the case. */
    private Integer isFinished;

    // Getters / Setters -------------------------------------------------

    public Long getSerial() {
        return serial;
    }

    public void setSerial(Long serial) {
        this.serial = serial;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public Integer getActionCode() {
        return actionCode;
    }

    public void setActionCode(Integer actionCode) {
        this.actionCode = actionCode;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getEntryUser() {
        return entryUser;
    }

    public void setEntryUser(Integer entryUser) {
        this.entryUser = entryUser;
    }

    public LocalDateTime getEntryDate() {
        entryDate = entryDate == null ? LocalDateTime.now() : entryDate;
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

    public Integer getIsFinished() {
        return isFinished;
    }

    public void setIsFinished(Integer isFinished) {
        this.isFinished = isFinished;
    }
}