package com.example.approval.mycases;

import java.io.Serializable;
import java.util.Date;

/**
 * One read-only row of the <b>My Decisions</b> tab of the My Cases details
 * dialog: a business action the logged-in user personally recorded in the
 * existing BPM audit trail ({@code F_BPM_AUDIT_LOG_DTL},
 * {@code ENTRY_USER = current user}) on the opened case.
 *
 * <p>The {@code actionKey} is a stable vocabulary
 * ({@code approved / rejected / reviewed / received / other}) localized by
 * the backing bean; {@code actionDescription} carries the authoritative
 * description of the existing {@link com.example.approval.audit.BpmAuditAction}
 * enum.</p>
 */
public class MyDecisionRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Date date;
    private final String actionKey;
    private final String actionDescription;
    private final String note;

    public MyDecisionRow(Date date, String actionKey,
                         String actionDescription, String note) {
        this.date = date;
        this.actionKey = actionKey;
        this.actionDescription = actionDescription;
        this.note = note;
    }

    public Date getDate() {
        return date;
    }

    /** Stable key of {@code mc.decision.<actionKey>} label entries. */
    public String getActionKey() {
        return actionKey;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public String getNote() {
        return note;
    }
}