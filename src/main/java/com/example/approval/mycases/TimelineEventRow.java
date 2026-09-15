package com.example.approval.mycases;

import java.io.Serializable;
import java.util.Date;

/**
 * One entry of the <b>Timeline</b> tab of the My Cases details dialog: a
 * chronologically ordered event of the selected process instance, merged
 * from the Flowable history (process start/end, task milestones) and the
 * existing BPM audit trail.
 *
 * <p>Immutable view DTO assembled by {@link MyCasesService}. The
 * {@code typeKey} is one of the stable keys {@code started},
 * {@code completed}, {@code taskCreated}, {@code taskCompleted} or
 * {@code audit} - the backing bean localizes it into the event caption of
 * the user's UI language.</p>
 */
public class TimelineEventRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Date timestamp;
    private final String typeKey;
    private final String title;
    private final String actor;
    private final String detail;

    public TimelineEventRow(Date timestamp, String typeKey, String title,
                            String actor, String detail) {
        this.timestamp = timestamp;
        this.typeKey = typeKey;
        this.title = title;
        this.actor = actor;
        this.detail = detail;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    /** Stable i18n key of the event type (started/completed/taskCreated/...). */
    public String getTypeKey() {
        return typeKey;
    }

    /** Event subject: process/task display name or audit action title. */
    public String getTitle() {
        return title;
    }

    /** User id the event is about (initiator/assignee), may be null. */
    public String getActor() {
        return actor;
    }

    /** Additional free-text detail (e.g. the audit note), may be null. */
    public String getDetail() {
        return detail;
    }
}