package com.example.approval.clear;

import com.example.approval.config.DevModeProperties;
import com.example.approval.service.ExternalGroupService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Development-only helper that permanently deletes <b>Flowable</b> process
 * instances ("clear data instance"):
 *
 * <ol>
 *   <li>Flowable runtime data - the process instance, its tasks, variables
 *       and jobs ({@code RuntimeService.deleteProcessInstance});</li>
 *   <li>Flowable history - the historic process instance and all historic
 *       activity / task / variable rows
 *       ({@code HistoryService.deleteHistoricProcessInstance}).</li>
 * </ol>
 *
 * <p><b>The Oracle business audit trail is intentionally left untouched:</b>
 * rows in {@code F_BPM_AUDIT_LOG}, {@code F_BPM_AUDIT_LOG_DTL} and
 * {@code F_BPM_CASE_ATTACHMENTS} (and the attachment binaries on disk)
 * survive a clear - this tool only wipes the Flowable engine data of the
 * selected instances.</p>
 *
 * <p><b>Double guard:</b> every public mutating method re-checks that
 * <b>both</b> {@code app.dev-mode.enabled=true} <b>and</b> the acting user
 * is a group admin ({@link ExternalGroupService#isGroupAdmin}, group
 * {@code ADM}) hold. The Administration menu entry is only rendered when
 * dev mode is on, but hiding the link is never the security boundary - the
 * server-side checks are. {@link SecurityException} is thrown otherwise.</p>
 */
@Service
public class ClearDataService {

    private static final Logger log = LoggerFactory.getLogger(ClearDataService.class);

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final DevModeProperties devModeProperties;
    private final ExternalGroupService groupService;

    public ClearDataService(RuntimeService runtimeService,
                            HistoryService historyService,
                            DevModeProperties devModeProperties,
                            ExternalGroupService groupService) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.devModeProperties = devModeProperties;
        this.groupService = groupService;
    }

    // ------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------

    /** Whether the dev-only tooling (incl. this service) is switched on. */
    public boolean isDevMode() {
        return devModeProperties.isEnabled();
    }

    /**
     * Whether the given user may use the clear-data tool: dev mode on AND
     * member of the {@code ADM} group.
     */
    public boolean isClearDataAllowed(String flowableUserId) {
        return devModeProperties.isEnabled() && groupService.isGroupAdmin(flowableUserId);
    }

    /**
     * Server-side gate of every destructive call: dev mode enabled and
     * acting user is a group admin, otherwise {@link SecurityException}.
     */
    private void assertClearDataAllowed(String flowableUserId) {
        if (!devModeProperties.isEnabled()) {
            throw new SecurityException("Clear data is disabled (app.dev-mode.enabled=false)");
        }
        if (!groupService.isGroupAdmin(flowableUserId)) {
            throw new SecurityException("User " + flowableUserId
                    + " is not allowed to clear data (requires group "
                    + ExternalGroupService.ADMIN_ROLE_CODE + ")");
        }
    }

    // ------------------------------------------------------------------
    // Listings (page overview)
    // ------------------------------------------------------------------

    /** All running process instances, newest first. */
    public List<ProcessInstance> findRunningInstances() {
        return runtimeService.createProcessInstanceQuery()
                .orderByStartTime()
                .desc()
                .list();
    }

    /** All finished (historic) process instances, newest first. */
    public List<HistoricProcessInstance> findFinishedInstances() {
        return historyService.createHistoricProcessInstanceQuery()
                .finished()
                .orderByProcessInstanceStartTime()
                .desc()
                .list();
    }

    // ------------------------------------------------------------------
    // Clear operations
    // ------------------------------------------------------------------

    /**
     * Deletes ONE process instance and its Flowable data (runtime + history).
     * The Oracle {@code F_BPM_*} audit rows of the case are NOT touched.
     *
     * @param processInstanceId Flowable process instance id (= business CASE_ID)
     * @param actorUserId       acting Flowable user id (must be ADM + dev mode)
     * @return summary of what was removed
     */
    public ClearResult clearInstance(String processInstanceId, String actorUserId) {
        assertClearDataAllowed(actorUserId);
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("A process instance id is required");
        }
        String id = processInstanceId.trim();
        log.warn("DEV clear-data: user {} deleting Flowable data of process instance {}",
                actorUserId, id);

        ClearResult result = new ClearResult();

        // 1. Flowable runtime (instance, tasks, variables, jobs)
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(id)
                .singleResult();
        if (running != null) {
            runtimeService.deleteProcessInstance(id, "Dev clear-data by " + actorUserId);
            result.runtimeDeleted = true;
        }

        // 2. Flowable history: deleteHistoricProcessInstance also covers the
        //    still-running case (it deletes runtime + history together), so
        //    it is safe to call whenever a historic row exists.
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(id)
                .singleResult();
        if (historic != null) {
            historyService.deleteHistoricProcessInstance(id);
            result.historyDeleted = true;
        }

        log.warn("DEV clear-data: Flowable data of instance {} removed (runtime: {}, history: {})",
                id, result.runtimeDeleted, result.historyDeleted);
        return result;
    }

    /**
     * Deletes the Flowable data (runtime + history) of EVERY process
     * instance. An instance that fails (e.g. locked by a running job) is
     * counted and skipped, the rest continues.
     *
     * @return aggregated summary across all instances
     */
    public ClearResult clearAll(String actorUserId) {
        assertClearDataAllowed(actorUserId);
        log.warn("DEV clear-data: user {} deleting Flowable data of ALL process instances",
                actorUserId);

        List<String> ids = runtimeService.createProcessInstanceQuery().list().stream()
                .map(ProcessInstance::getId)
                .toList();
        List<String> historicIds = historyService.createHistoricProcessInstanceQuery().list().stream()
                .map(HistoricProcessInstance::getId)
                .toList();

        // dedup: an id can appear in both the runtime and the historic query
        Set<String> all = new LinkedHashSet<>(ids);
        all.addAll(historicIds);

        ClearResult total = new ClearResult();
        for (String id : all) {
            try {
                total.add(clearInstance(id, actorUserId));
                total.instancesCleared++;
            } catch (Exception e) {
                total.instancesFailed++;
                log.warn("DEV clear-data: could not clear instance {}: {}", id, e.getMessage());
            }
        }
        log.warn("DEV clear-data: cleared {} instance(s), {} failed",
                total.instancesCleared, total.instancesFailed);
        return total;
    }

    // ------------------------------------------------------------------
    // Result summary
    // ------------------------------------------------------------------

    /** What a clear operation removed; fields are mutated by the service. */
    public static class ClearResult {
        private boolean runtimeDeleted;
        private boolean historyDeleted;
        private int instancesCleared;
        private int instancesFailed;

        void add(ClearResult other) {
            runtimeDeleted |= other.runtimeDeleted;
            historyDeleted |= other.historyDeleted;
        }

        public boolean isRuntimeDeleted() {
            return runtimeDeleted;
        }

        public boolean isHistoryDeleted() {
            return historyDeleted;
        }

        public int getInstancesCleared() {
            return instancesCleared;
        }

        public int getInstancesFailed() {
            return instancesFailed;
        }
    }
}