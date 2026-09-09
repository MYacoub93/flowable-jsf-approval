package com.example.approval.processadmin;

import java.io.Serializable;
import java.util.Date;

/**
 * One row of the Administration &rarr; Process Management table: a deployed
 * Flowable <b>process definition</b> together with the deployment-level
 * metadata shown next to it (deployment id / time, running instance count).
 *
 * <p>Immutable view DTO assembled by {@link ProcessDeploymentService} - no
 * engine entity is ever exposed to the JSF layer directly.</p>
 */
public class ProcessDefinitionRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String definitionId;
    private final String key;
    private final String name;
    private final int version;
    private final String deploymentId;
    private final Date deploymentTime;
    private final boolean suspended;
    private final long runningInstanceCount;
    private final String resourceName;

    public ProcessDefinitionRow(String definitionId,
                                String key,
                                String name,
                                int version,
                                String deploymentId,
                                Date deploymentTime,
                                boolean suspended,
                                long runningInstanceCount,
                                String resourceName) {
        this.definitionId = definitionId;
        this.key = key;
        this.name = name;
        this.version = version;
        this.deploymentId = deploymentId;
        this.deploymentTime = deploymentTime;
        this.suspended = suspended;
        this.runningInstanceCount = runningInstanceCount;
        this.resourceName = resourceName;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public Date getDeploymentTime() {
        return deploymentTime;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public long getRunningInstanceCount() {
        return runningInstanceCount;
    }

    public String getResourceName() {
        return resourceName;
    }

    /**
     * Identity by process definition id - the engine guarantees it is
     * unique, and PrimeFaces checkbox-selection compares row objects with
     * {@code equals} when re-rendering the table after a refresh.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProcessDefinitionRow other)) {
            return false;
        }
        return definitionId != null && definitionId.equals(other.definitionId);
    }

    @Override
    public int hashCode() {
        return definitionId != null ? definitionId.hashCode() : 0;
    }
}
