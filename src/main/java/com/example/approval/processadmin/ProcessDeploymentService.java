package com.example.approval.processadmin;

import com.example.approval.service.ExternalGroupService;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Administration service that deploys, undeploys, enables and disables
 * <b>Flowable BPMN process definitions</b> through the shared engine's
 * {@link RepositoryService} (Administration &rarr; Process Management).
 *
 * <p><b>Layering</b> (no new {@code ProcessEngine}, no custom deployment
 * logic - the shared Spring-managed engine beans are reused):</p>
 * <pre>
 * process-management.xhtml
 *      |
 * ProcessManagementBean        (JSF backing bean)
 *      |
 * ProcessDeploymentService     (this class)
 *      |
 * Flowable RepositoryService   (flowable-spring-boot-starter-process)
 * </pre>
 *
 * <p><b>Security:</b> every public method re-checks that the acting user
 * is a group admin ({@link ExternalGroupService#isGroupAdmin}, group
 * {@code ADM}) - the hidden menu entry is never the security boundary.
 * {@link SecurityException} is thrown otherwise. Read access (the table
 * listing) is also restricted to admins so the administration overview is
 * not leaked to regular users.</p>
 *
 * <p><b>Audit:</b> this application's business audit trail
 * ({@code F_BPM_*} tables) is case-oriented (CASE_ID = a process
 * <i>instance</i> id), so it cannot host definition-level admin actions.
 * Following the pattern of {@code ClearDataService}, deploy/undeploy and
 * enable/disable decisions are therefore recorded in the server log with
 * the acting administrator, action, deployment/definition id and result
 * (ENABLE / DISABLE / BULK_ENABLE / BULK_DISABLE / DELETE /
 * BULK_DELETE / BLOCKED).</p>
 *
 * <p><b>Duplicate handling:</b> deploying byte-identical content does not
 * create a new version - Flowable's {@code enableDuplicateFiltering()}
 * makes {@code deploy()} reuse the existing deployment when the resource
 * bytes are unchanged, so only genuinely changed processes produce a new
 * version.</p>
 */
@Service
public class ProcessDeploymentService {

    private static final Logger log = LoggerFactory.getLogger(ProcessDeploymentService.class);

    /** Accepted upload resource name suffixes (BPMN 2.0 XML convention). */
    private static final String BPMN_XML_SUFFIX = ".bpmn20.xml";
    private static final String BPMN_SUFFIX = ".bpmn";

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final ExternalGroupService groupService;

    public ProcessDeploymentService(RepositoryService repositoryService,
                                    RuntimeService runtimeService,
                                    ExternalGroupService groupService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.groupService = groupService;
    }

    // ------------------------------------------------------------------
    // Guards (admin only - server side, not just hidden UI)
    // ------------------------------------------------------------------

    /** Whether the acting user may use the Process Management screen. */
    public boolean isProcessAdminAllowed(String flowableUserId) {
        return groupService.isGroupAdmin(flowableUserId);
    }

    /**
     * Server-side gate of every administration call: acting user must be a
     * group admin, otherwise {@link SecurityException}.
     */
    private void assertProcessAdminAllowed(String flowableUserId) {
        if (!groupService.isGroupAdmin(flowableUserId)) {
            log.warn("Process administration denied for user {} (requires group {})",
                    flowableUserId, ExternalGroupService.ADMIN_ROLE_CODE);
            throw new SecurityException("User " + flowableUserId
                    + " is not allowed to manage process deployments (requires group "
                    + ExternalGroupService.ADMIN_ROLE_CODE + ")");
        }
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    /**
     * All deployed process definitions (every deployed version is listed
     * so the administrator sees exactly what the engine holds), enriched
     * with the deployment time and the number of running instances per
     * definition.
     *
     * <p>Admin-only on the server side as well.</p>
     */
    public List<ProcessDefinitionRow> findDeployedProcesses(String flowableUserId) {
        assertProcessAdminAllowed(flowableUserId);
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .orderByProcessDefinitionKey()
                .asc()
                .orderByProcessDefinitionVersion()
                .desc()
                .list();

        List<ProcessDefinitionRow> rows = new ArrayList<>(definitions.size());
        for (ProcessDefinition definition : definitions) {
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(definition.getDeploymentId())
                    .singleResult();
            Date deploymentTime = deployment != null ? deployment.getDeploymentTime() : null;
            long running = runtimeService.createProcessInstanceQuery()
                    .processDefinitionId(definition.getId())
                    .count();
            rows.add(new ProcessDefinitionRow(
                    definition.getId(),
                    definition.getKey(),
                    definition.getName() != null ? definition.getName() : definition.getKey(),
                    definition.getVersion(),
                    definition.getDeploymentId(),
                    deploymentTime,
                    definition.isSuspended(),
                    running,
                    definition.getResourceName()));
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Deploy
    // ------------------------------------------------------------------

    /**
     * Result summary of a deployment operation (values for the growl
     * message of the administration UI).
     *
     * @param deploymentId   id of the created (or reused) deployment
     * @param reusedExisting true when duplicate filtering reused an
     *                       identical existing deployment instead of
     *                       creating a new version
     */
    public record DeployResult(String deploymentId, boolean reusedExisting,
                               List<String> processKeys) {
    }

    /**
     * Deploys an uploaded BPMN 2.0 XML file through the shared engine.
     *
     * <p>Validation performed before the engine call:</p>
     * <ul>
     *   <li>file must not be empty / readable;</li>
     *   <li>file name should end with {@code .bpmn20.xml} (or {@code .bpmn});</li>
     *   <li>content must start with a BPMN 2.0 XML document (cheap structural
     *       check - the engine performs the full schema parse).</li>
     * </ul>
     *
     * <p>All {@link FlowableException}s (invalid XML, missing process
     * definition, database failures) are translated into
     * {@link InvalidBpmnException} with a user-friendly message; the
     * technical detail stays in the server log.</p>
     *
     * @param fileName     original upload file name (informational)
     * @param bpmnXml      raw file content, UTF-8 BPMN 2.0 XML
     * @param actorUserId  acting administrator (must be group {@code ADM})
     * @return deployment summary
     * @throws InvalidBpmnException when the file is not a deployable
     *                              BPMN 2.0 process
     * @throws SecurityException    when the actor is not an admin
     */
    public DeployResult deployProcess(String fileName, byte[] bpmnXml, String actorUserId) {
        assertProcessAdminAllowed(actorUserId);
        String safeName = fileName != null ? fileName.trim() : "";
        if (bpmnXml == null || bpmnXml.length == 0) {
            throw new InvalidBpmnException("The uploaded file is empty.");
        }
        if (!safeName.isEmpty() && !safeName.toLowerCase().endsWith(BPMN_XML_SUFFIX)
                && !safeName.toLowerCase().endsWith(BPMN_SUFFIX)) {
            throw new InvalidBpmnException("The file name must end with '" + BPMN_XML_SUFFIX
                    + "' (got '" + safeName + "').");
        }
        String xml = new String(bpmnXml, StandardCharsets.UTF_8);
        if (!looksLikeBpmn20Xml(xml)) {
            throw new InvalidBpmnException("The file does not look like a BPMN 2.0 XML "
                    + "process definition (no definitions element found).");
        }

        String resourceName = safeName.isEmpty() ? defaultResourceName() : safeName;
        log.info("Process administration: user {} deploying BPMN resource '{}' ({} bytes)",
                actorUserId, resourceName, bpmnXml.length);
        try {
            long existing = repositoryService.createDeploymentQuery().count();
            // ByteArrayInputStream.close() is a no-op; a try-with-resources
            // block is intentionally avoided because close() would declare
            // a checked IOException that can never occur here.
            InputStream in = new ByteArrayInputStream(bpmnXml);
            Deployment deployment = repositoryService.createDeployment()
                    .name(deploymentNameOf(resourceName))
                    // duplicate filtering: deploying unchanged content
                    // reuses the existing deployment instead of piling
                    // up identical versions
                    .enableDuplicateFiltering()
                    .addInputStream(resourceName, in)
                    .deploy();
            boolean reused = repositoryService.createDeploymentQuery().count() == existing;
            List<String> keys = deployedProcessKeys(deployment.getId());
            log.info("Process administration: user {} deployed resource '{}' -> deployment {}, "
                            + "processes {}, reusedExisting={}",
                    actorUserId, resourceName, deployment.getId(), keys, reused);
            return new DeployResult(deployment.getId(), reused, keys);
        } catch (FlowableException e) {
            log.warn("Process administration: deployment of '{}' by {} failed: {}",
                    resourceName, actorUserId, e.getMessage(), e);
            throw new InvalidBpmnException("Flowable rejected the file as a BPMN 2.0 process "
                    + "definition: " + rootMessage(e));
        }
    }

    // ------------------------------------------------------------------
    // Undeploy
    // ------------------------------------------------------------------

    /**
     * Result summary of an undeployment operation.
     *
     * @param deploymentId     deleted deployment id
     * @param deletedInstances number of running process instances that were
     *                         cascaded with the delete
     */
    public record UndeployResult(String deploymentId, long deletedInstances) {
    }

    /**
     * Deletes (undeploys) one deployment together with its process
     * definitions.
     *
     * <p><b>Running instances:</b> per the application's existing
     * process-management behaviour (see {@code ClearDataService}), runtime
     * and history data is only removed when explicitly requested. When the
     * deployment still has running instances and the administrator did not
     * opt into cascade deletion, the operation fails with
     * {@link DeploymentInUseException} and the UI asks for an explicit
     * cascade confirmation; with {@code cascade=true} the Flowable
     * {@code deleteDeployment(id, true)} semantics apply (the running
     * instances are deleted together with the deployment, historic data of
     * already finished instances is kept).</p>
     *
     * @param deploymentId  deployment to delete
     * @param cascade       true = also delete the still-running process
     *                      instances of the contained definitions
     * @param actorUserId   acting administrator (must be group {@code ADM})
     * @throws DeploymentInUseException   when {@code cascade=false} but the
     *                                    deployment has running instances
     * @throws FlowableObjectNotFoundException when the deployment does
     *                                    not exist (anymore)
     * @throws SecurityException          when the actor is not an admin
     */
    public UndeployResult undeployDeployment(String deploymentId, boolean cascade, String actorUserId) {
        assertProcessAdminAllowed(actorUserId);
        if (deploymentId == null || deploymentId.isBlank()) {
            throw new IllegalArgumentException("A deployment id is required");
        }
        String id = deploymentId.trim();

        Deployment deployment = repositoryService.createDeploymentQuery()
                .deploymentId(id)
                .singleResult();
        if (deployment == null) {
            throw new FlowableObjectNotFoundException(
                    "Deployment '" + id + "' does not exist (anymore)");
        }

        long runningInstances = countRunningInstancesOfDeployment(id);
        if (runningInstances > 0 && !cascade) {
            log.info("Process administration: user {} BLOCKED from undeploying deployment {} "
                            + "with {} running instance(s) - cascade required",
                    actorUserId, id, runningInstances);
            throw new DeploymentInUseException(runningInstances);
        }

        log.warn("Process administration: user {} DELETE deployment {} (cascade={}, "
                        + "running instances at delete time: {})",
                actorUserId, id, cascade, runningInstances);
        try {
            // cascade=true keeps historic rows of FINISHED instances; the
            // still-running instances are deleted with the deployment
            repositoryService.deleteDeployment(id, cascade);
        } catch (FlowableException e) {
            log.warn("Process administration: undeployment of {} by {} failed: {}",
                    id, actorUserId, e.getMessage(), e);
            throw new IllegalStateException("Could not undeploy deployment '" + id + "': "
                    + rootMessage(e), e);
        }
        log.info("Process administration: user {} undeployed deployment {} (key {}, "
                        + "cascaded instances {})",
                actorUserId, id, deployment.getKey(), cascade ? runningInstances : 0);
        return new UndeployResult(id, cascade ? runningInstances : 0);
    }

    // ------------------------------------------------------------------
    // Enable / Disable (activate / suspend a process definition)
    // ------------------------------------------------------------------

    /**
     * Result of a single state change.
     *
     * @param key     process definition key (for messages)
     * @param changed false when the definition already had the target state
     *                and was therefore skipped
     */
    public record StateChangeResult(String key, boolean changed) {
    }

    /**
     * Activates (enables) a suspended process definition through the shared
     * {@link RepositoryService}. A definition that is already active is
     * skipped without error. <b>Existing instances/history are never
     * touched</b> - activation only allows starting new instances again.
     */
    public StateChangeResult enableProcessDefinition(String processDefinitionId, String actorUserId) {
        return setProcessDefinitionState(processDefinitionId, false, "ENABLE", actorUserId);
    }

    /**
     * Suspends (disables) an active process definition through the shared
     * {@link RepositoryService}. A definition that is already suspended is
     * skipped without error. <b>Existing instances/history are never
     * deleted</b> - suspension only prevents new instances from being
     * started (Flowable suspension semantics).
     */
    public StateChangeResult disableProcessDefinition(String processDefinitionId, String actorUserId) {
        return setProcessDefinitionState(processDefinitionId, true, "DISABLE", actorUserId);
    }

    private StateChangeResult setProcessDefinitionState(String processDefinitionId,
                                                        boolean suspend,
                                                        String action,
                                                        String actorUserId) {
        assertProcessAdminAllowed(actorUserId);
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            throw new IllegalArgumentException("A process definition id is required");
        }
        String id = processDefinitionId.trim();

        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(id)
                .singleResult();
        if (definition == null) {
            throw new FlowableObjectNotFoundException(
                    "Process definition '" + id + "' does not exist (anymore)");
        }

        if (definition.isSuspended() == suspend) {
            log.info("Process administration: user {} {} of process definition {} (key {}) "
                            + "skipped - already {}",
                    actorUserId, action, id, definition.getKey(),
                    suspend ? "suspended" : "active");
            return new StateChangeResult(definition.getKey(), false);
        }

        try {
            if (suspend) {
                // definition-only suspension: running instances and history
                // are kept; only new instance starts are blocked
                repositoryService.suspendProcessDefinitionById(id);
                log.info("Process administration: user {} DISABLE process definition {} "
                                + "(key {}) - suspended",
                        actorUserId, id, definition.getKey());
            } else {
                repositoryService.activateProcessDefinitionById(id);
                log.info("Process administration: user {} ENABLE process definition {} "
                                + "(key {}) - activated",
                        actorUserId, id, definition.getKey());
            }
        } catch (FlowableException e) {
            log.warn("Process administration: {} of process definition {} by {} failed: {}",
                    action, id, actorUserId, e.getMessage(), e);
            throw new IllegalStateException("Could not " + (suspend ? "disable" : "enable")
                    + " process '" + definition.getKey() + "': " + rootMessage(e), e);
        }
        return new StateChangeResult(definition.getKey(), true);
    }

    // ------------------------------------------------------------------
    // Bulk enable / disable
    // ------------------------------------------------------------------

    /**
     * Result summary of a bulk state change.
     *
     * @param changed keys of definitions whose state was changed
     * @param skipped keys of definitions already in the target state
     * @param failed  ids with the reason why the operation failed
     */
    public record BulkStateChangeResult(List<String> changed, List<String> skipped,
                                        List<FailedItem> failed) {

        /** Number of definitions actually changed. */
        public int changedCount() {
            return changed.size();
        }

        /** Number of definitions skipped (already in the target state). */
        public int skippedCount() {
            return skipped.size();
        }

        /** Number of failed definitions. */
        public int failedCount() {
            return failed.size();
        }
    }

    /** One failed item of a bulk operation, with a user-friendly reason. */
    public record FailedItem(String id, String reason) {
    }

    /**
     * Activates all given (currently suspended) process definitions;
     * definitions that are already active are skipped, failures of single
     * definitions do not stop the others.
     */
    public BulkStateChangeResult enableProcessDefinitions(List<String> processDefinitionIds,
                                                          String actorUserId) {
        return changeStateBulk(processDefinitionIds, true, "BULK_ENABLE", actorUserId);
    }

    /**
     * Suspends all given (currently active) process definitions; already
     * suspended definitions are skipped, failures of single definitions do
     * not stop the others. Existing instances and history are never touched.
     */
    public BulkStateChangeResult disableProcessDefinitions(List<String> processDefinitionIds,
                                                           String actorUserId) {
        return changeStateBulk(processDefinitionIds, false, "BULK_DISABLE", actorUserId);
    }

    private BulkStateChangeResult changeStateBulk(List<String> processDefinitionIds,
                                                  boolean enable,
                                                  String action,
                                                  String actorUserId) {
        assertProcessAdminAllowed(actorUserId);
        List<String> changed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();
        if (processDefinitionIds == null || processDefinitionIds.isEmpty()) {
            return new BulkStateChangeResult(changed, skipped, failed);
        }
        List<String> ids = processDefinitionIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        for (String id : ids) {
            try {
                StateChangeResult result = enable
                        ? enableProcessDefinition(id, actorUserId)
                        : disableProcessDefinition(id, actorUserId);
                if (result.changed()) {
                    changed.add(result.key() != null ? result.key() : id);
                } else {
                    skipped.add(result.key() != null ? result.key() : id);
                }
            } catch (FlowableObjectNotFoundException e) {
                failed.add(new FailedItem(id, "does not exist (anymore)"));
                log.warn("Process administration: {} of {} by {} failed: {}",
                        action, id, actorUserId, e.getMessage());
            } catch (Exception e) {
                failed.add(new FailedItem(id, rootMessage(e)));
                log.warn("Process administration: {} of {} by {} failed: {}",
                        action, id, actorUserId, e.getMessage(), e);
            }
        }
        log.info("Process administration: user {} {} -> {} changed, {} skipped, {} failed",
                actorUserId, action, changed.size(), skipped.size(), failed.size());
        return new BulkStateChangeResult(changed, skipped, failed);
    }

    // ------------------------------------------------------------------
    // Bulk undeploy (delete deployments)
    // ------------------------------------------------------------------

    /** A deployment blocked from deletion because instances still run. */
    public record DeploymentInUseInfo(String deploymentId, long runningInstances) {
    }

    /**
     * Result summary of a bulk undeploy.
     *
     * @param deleted deployment ids that were deleted successfully
     * @param blocked deployments still having running instances - only
     *                deleted after an explicit cascade confirmation
     *                ({@link #undeployDeployments(List, boolean, String)}
     *                with {@code cascade=true})
     * @param failed  deployments that could not be processed, with reason
     */
    public record BulkUndeployResult(List<String> deleted,
                                     List<DeploymentInUseInfo> blocked,
                                     List<FailedItem> failed) {

        /** Number of deployments that were successfully deleted. */
        public int deletedCount() {
            return deleted.size();
        }

        /** Number of deployments blocked by running instances. */
        public int blockedCount() {
            return blocked.size();
        }

        /** Number of failed deployments. */
        public int failedCount() {
            return failed.size();
        }
    }

    /**
     * Deletes (undeploys) several deployments, each one independently -
     * the failure of one deployment does not prevent processing of the
     * others.
     *
     * <p>Follows the single-delete semantics: with {@code cascade=false}
     * deployments that still have running instances are <b>not</b> deleted
     * but reported as {@code blocked}; the administrator must explicitly
     * confirm cascade deletion for them (call again with
     * {@code cascade=true}).</p>
     */
    public BulkUndeployResult undeployDeployments(List<String> deploymentIds,
                                                  boolean cascade,
                                                  String actorUserId) {
        assertProcessAdminAllowed(actorUserId);
        List<String> deleted = new ArrayList<>();
        List<DeploymentInUseInfo> blocked = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();
        if (deploymentIds == null || deploymentIds.isEmpty()) {
            return new BulkUndeployResult(deleted, blocked, failed);
        }
        List<String> ids = deploymentIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        for (String id : ids) {
            try {
                undeployDeployment(id, cascade, actorUserId);
                deleted.add(id);
            } catch (DeploymentInUseException e) {
                blocked.add(new DeploymentInUseInfo(id, e.getRunningInstances()));
            } catch (FlowableObjectNotFoundException e) {
                failed.add(new FailedItem(id, "does not exist (anymore)"));
                log.warn("Process administration: BULK_DELETE of {} by {} failed: {}",
                        id, actorUserId, e.getMessage());
            } catch (Exception e) {
                failed.add(new FailedItem(id, rootMessage(e)));
                log.warn("Process administration: BULK_DELETE of {} by {} failed: {}",
                        id, actorUserId, e.getMessage(), e);
            }
        }
        log.warn("Process administration: user {} BULK_DELETE (cascade={}) -> {} deleted, "
                        + "{} blocked, {} failed",
                actorUserId, cascade, deleted.size(), blocked.size(), failed.size());
        return new BulkUndeployResult(deleted, blocked, failed);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Number of running instances across all process definitions of one
     * deployment (used to decide whether a cascade confirmation is needed).
     */
    public long countRunningInstancesOfDeployment(String deploymentId) {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .list();
        long total = 0;
        for (ProcessDefinition definition : definitions) {
            total += runtimeService.createProcessInstanceQuery()
                    .processDefinitionId(definition.getId())
                    .count();
        }
        return total;
    }

    /** Process definition keys contained in one deployment. */
    private List<String> deployedProcessKeys(String deploymentId) {
        return repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .list()
                .stream()
                .map(ProcessDefinition::getKey)
                .toList();
    }

    /**
     * Cheap structural pre-check: the engine does the real XML validation;
     * this only catches obvious non-BPMN uploads (wrong file selected)
     * before a confusing engine stack trace is produced.
     */
    static boolean looksLikeBpmn20Xml(String xml) {
        if (xml == null || xml.isBlank()) {
            return false;
        }
        String compact = xml.replaceAll("\\s+", " ");
        return compact.contains("<bpmn:definitions")
                || compact.contains("<definitions")
                || compact.contains("<bpmn2:definitions");
    }

    private static String defaultResourceName() {
        return "admin-upload-" + System.currentTimeMillis() + BPMN_XML_SUFFIX;
    }

    private static String deploymentNameOf(String resourceName) {
        String stripped = resourceName.toLowerCase().endsWith(BPMN_XML_SUFFIX)
                ? resourceName.substring(0, resourceName.length() - BPMN_XML_SUFFIX.length())
                : resourceName;
        return stripped.isBlank() ? resourceName : stripped;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null ? msg : cur.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------
    // Exceptions carrying user-friendly messages
    // ------------------------------------------------------------------

    /** The uploaded file is not a deployable BPMN 2.0 process definition. */
    public static class InvalidBpmnException extends RuntimeException {
        public InvalidBpmnException(String message) {
            super(message);
        }
    }

    /**
     * The deployment still has running process instances and the caller did
     * not explicitly opt into cascading deletion.
     */
    public static class DeploymentInUseException extends RuntimeException {
        private final long runningInstances;

        public DeploymentInUseException(long runningInstances) {
            super("Deployment still has " + runningInstances + " running process instance(s)");
            this.runningInstances = runningInstances;
        }

        public long getRunningInstances() {
            return runningInstances;
        }
    }
}