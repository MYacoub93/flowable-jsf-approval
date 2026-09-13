package com.example.approval.delegation;

import java.io.Serializable;
import java.util.Date;

/**
 * One row of the process instances table of the Administration &rarr; Task
 * Delegation page: a running Flowable process instance of the selected
 * process definition.
 *
 * <p>Immutable view DTO assembled by {@link TaskDelegationService} - no
 * engine entity is ever exposed to the JSF layer directly (same convention
 * as {@code ProcessDefinitionRow} of the Process Management admin page).</p>
 */
public class ProcessInstanceRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String startedBy;
    private final Date startTime;

    public ProcessInstanceRow(String id, String name, String startedBy, Date startTime) {
        this.id = id;
        this.name = name;
        this.startedBy = startedBy;
        this.startTime = startTime;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public Date getStartTime() {
        return startTime;
    }
}