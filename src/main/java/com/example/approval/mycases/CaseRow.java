package com.example.approval.mycases;

import java.io.Serializable;
import java.util.Date;

/**
 * One row of the <b>My Cases</b> table ({@code /my-cases.xhtml}): a process
 * instance (running or already finished) that was started by the currently
 * logged-in user.
 *
 * <p>Immutable view DTO assembled by {@link MyCasesService} from Flowable's
 * {@code HistoricProcessInstanceQuery} - no engine entity is ever exposed
 * to the JSF layer directly (same convention as the row DTOs of the Task
 * Delegation admin page).</p>
 */
public class CaseRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String processName;
    private final String processDefinitionKey;
    private final Integer processDefinitionVersion;
    private final String businessKey;
    private final String startedBy;
    private final String startedByDisplay;
    private final Date startTime;
    private final Date endTime;
    private final boolean finished;
    private final String deleteReason;

    public CaseRow(String id, String processName, String processDefinitionKey,
                   Integer processDefinitionVersion, String businessKey,
                   String startedBy, String startedByDisplay,
                   Date startTime, Date endTime, boolean finished) {
        this(id, processName, processDefinitionKey, processDefinitionVersion,
                businessKey, startedBy, startedByDisplay,
                startTime, endTime, finished, null);
    }

    public CaseRow(String id, String processName, String processDefinitionKey,
                   Integer processDefinitionVersion, String businessKey,
                   String startedBy, String startedByDisplay,
                   Date startTime, Date endTime, boolean finished,
                   String deleteReason) {
        this.id = id;
        this.processName = processName;
        this.processDefinitionKey = processDefinitionKey;
        this.processDefinitionVersion = processDefinitionVersion;
        this.businessKey = businessKey;
        this.startedBy = startedBy;
        this.startedByDisplay = startedByDisplay;
        this.startTime = startTime;
        this.endTime = endTime;
        this.finished = finished;
        this.deleteReason = deleteReason;
    }

    /** Copy of this row with the initiator's username resolved. */
    public CaseRow withStartedByDisplay(String display) {
        return new CaseRow(id, processName, processDefinitionKey,
                processDefinitionVersion, businessKey, startedBy, display,
                startTime, endTime, finished, deleteReason);
    }

    public String getId() {
        return id;
    }

    public String getProcessName() {
        return processName;
    }

    public String getProcessDefinitionKey() {
        return processDefinitionKey;
    }

    public Integer getProcessDefinitionVersion() {
        return processDefinitionVersion;
    }

    /** User-friendly "key (vN)" label of the process definition column. */
    public String getDefinitionDisplay() {
        String key = processDefinitionKey == null ? "" : processDefinitionKey;
        return processDefinitionVersion == null
                ? key : key + " (v" + processDefinitionVersion + ")";
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getStartedBy() {
        return startedBy;
    }

    /**
     * Initiator for display: {@code username (id)} when the username could
     * be resolved from {@code FLOWABLE_USERS_VW}, the bare id otherwise.
     */
    public String getStartedByDisplay() {
        return startedByDisplay == null || startedByDisplay.isBlank()
                ? startedBy : startedByDisplay;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Engine delete reason of a cancelled case, or null when none. */
    public String getDeleteReason() {
        return deleteReason;
    }

    /** True when the case was deleted/cancelled while running. */
    public boolean isCancelled() {
        return finished && deleteReason != null && !deleteReason.isBlank();
    }

    /**
     * CSS suffix of the status badge - one vocabulary with
     * {@link CaseStatusFilter}: {@code cancelled} when deleted while
     * running, {@code completed} when finished normally, otherwise
     * {@code inprogress}.
     */
    public String getStatusKey() {
        if (isCancelled()) {
            return "cancelled";
        }
        return finished ? "completed" : "inprogress";
    }
}
