package com.example.approval.backing;

import com.example.approval.processadmin.ProcessDefinitionRow;
import com.example.approval.processadmin.ProcessDeploymentService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Backs the Administration &rarr; Process Management screen
 * ({@code /process-management.xhtml}): lists the deployed Flowable
 * process definitions and offers the admin-only Deploy (BPMN 2.0 upload),
 * Enable/Disable (single and bulk), Undeploy (single and bulk) actions.
 *
 * <p>Rendered (and functional) only for group admins ({@code ADM} group,
 * see {@link ProcessDeploymentService#isProcessAdminAllowed(String)}).
 * The backing bean re-checks the guard on every action - the hidden menu
 * entry alone is never the security boundary; every service call is
 * additionally gated server-side in {@link ProcessDeploymentService}.</p>
 *
 * <p>Undeploy of a deployment that still has running process instances
 * requires an explicit second confirmation (cascade); the first attempt
 * is rejected with a dialog explaining the impact, mirroring the careful
 * delete semantics of the existing Clear Data tool. This applies to the
 * single and to the bulk undeploy alike.</p>
 *
 * <p>The selection state (row checkboxes, undeploy selection, cascade
 * pending flags) must survive the postbacks between the row click and
 * the confirmation dialogs, so the bean uses the JoinFaces {@code view}
 * scope ({@code @Scope("view")}) - the same pattern as
 * {@code GroupMembershipBean} - rather than the request scope of the
 * read-only pages.</p>
 */
@Component("processManagementBean")
@Scope("view")
public class ProcessManagementBean extends BaseBackingBean {

    private static final Logger log = LoggerFactory.getLogger(ProcessManagementBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ProcessDeploymentService processDeploymentService;

    /** Rows of the deployed-processes table (loaded for admins only). */
    private List<ProcessDefinitionRow> rows = Collections.emptyList();

    /** Rows checked in the multi-select column of the table. */
    private List<ProcessDefinitionRow> selectedProcesses = new ArrayList<>();

    // ------------------------------------------------------------------
    // Single undeploy selection state (kept across postbacks)
    // ------------------------------------------------------------------

    /** Deployment id chosen by the per-row Undeploy button. */
    private String selectedDeploymentId;

    /** Human label of the selected deployment ("key v1") for messages. */
    private String selectedDeploymentLabel;

    /** True after a single undeploy attempt was blocked by running instances. */
    private boolean cascadePending;

    /** Instance count reported by the blocked single undeploy attempt. */
    private long cascadeRunningCount;

    // ------------------------------------------------------------------
    // Bulk undeploy state (kept across postbacks)
    // ------------------------------------------------------------------

    /** True while the first bulk-delete confirmation dialog is shown. */
    private boolean bulkDeleteConfirmPending;

    /** True after a bulk delete attempt was blocked by running instances. */
    private boolean bulkCascadePending;

    /** Blocked deployments (id + running instances) of the bulk attempt. */
    private List<ProcessDeploymentService.DeploymentInUseInfo> bulkBlocked =
            Collections.emptyList();

    /** Summary message of the last bulk delete attempt (shown in dialogs). */
    private String bulkDeleteSummary = "";

    @PostConstruct
    public void init() {
        if (isPageVisible()) {
            loadRows();
        }
    }

    // ------------------------------------------------------------------
    // Guards (EL-facing, mirror the server-side service checks)
    // ------------------------------------------------------------------

    /** Whether the page content may render at all. */
    public boolean isPageVisible() {
        return loginBean.isLoggedIn()
                && processDeploymentService.isProcessAdminAllowed(loginBean.getCurrentUser().getId());
    }

    private boolean requireActionAllowed() {
        if (isPageVisible()) {
            return true;
        }
        addError("Process Management is only available to ADM users.");
        return false;
    }

    private String currentUserId() {
        return loginBean.getCurrentUser().getId();
    }

    // ------------------------------------------------------------------
    // Table data
    // ------------------------------------------------------------------

    private void loadRows() {
        try {
            rows = processDeploymentService.findDeployedProcesses(currentUserId());
        } catch (SecurityException e) {
            rows = Collections.emptyList();
        }
    }

    /** Reloads the table after a successful operation. */
    public void refresh() {
        loadRows();
    }

    public List<ProcessDefinitionRow> getRows() {
        return rows;
    }

    public int getRowCount() {
        return rows.size();
    }

    public List<ProcessDefinitionRow> getSelectedProcesses() {
        return selectedProcesses;
    }

    public void setSelectedProcesses(List<ProcessDefinitionRow> selectedProcesses) {
        this.selectedProcesses = selectedProcesses != null
                ? new ArrayList<>(selectedProcesses) : new ArrayList<>();
    }

    /** Whether any row checkbox is checked (drives the bulk toolbar). */
    public boolean isHasSelection() {
        return !selectedProcesses.isEmpty();
    }

    /** "N process(es) selected" label of the bulk toolbar. */
    public String getSelectionLabel() {
        int n = selectedProcesses.size();
        return n + (n == 1 ? " process" : " processes") + " selected";
    }

    /** Comma-separated keys of the selection (for confirm dialogs). */
    public String getSelectionNames() {
        return selectedProcesses.stream()
                .map(r -> r.getKey() + " v" + r.getVersion())
                .collect(Collectors.joining(", "));
    }

    /** Deployment ids of the selection (for bulk undeploy). */
    private List<String> selectedDeploymentIds() {
        return selectedProcesses.stream()
                .map(ProcessDefinitionRow::getDeploymentId)
                .distinct()
                .collect(Collectors.toList());
    }

    /** Definition ids of the selection (for bulk enable/disable). */
    private List<String> selectedDefinitionIds() {
        return selectedProcesses.stream()
                .map(ProcessDefinitionRow::getDefinitionId)
                .distinct()
                .collect(Collectors.toList());
    }

    /** Clears the checkbox selection (after every bulk operation). */
    public void clearSelection() {
        selectedProcesses = new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Deploy (BPMN 2.0 upload)
    // ------------------------------------------------------------------

    /**
     * File-upload listener of the Deploy panel: validates and deploys the
     * uploaded {@code .bpmn20.xml} through the shared Flowable engine and
     * refreshes the table.
     */
    public void handleFileUpload(FileUploadEvent event) {
        if (!requireActionAllowed()) {
            return;
        }
        UploadedFile file = event.getFile();
        if (file == null || file.getSize() == 0) {
            addError("No file was uploaded.");
            return;
        }
        String fileName = file.getFileName();
        try {
            ProcessDeploymentService.DeployResult result =
                    processDeploymentService.deployProcess(fileName, file.getContent(),
                            currentUserId());
            String processes = String.join(", ", result.processKeys());
            if (result.reusedExisting()) {
                addInfo("Deployment unchanged: '" + fileName + "' is identical to the already "
                        + "deployed version (deployment " + result.deploymentId()
                        + ", processes: " + processes + "). No new version created.");
            } else {
                addInfo("Process deployed successfully: '" + fileName + "' (deployment "
                        + result.deploymentId() + ", processes: " + processes + ").");
            }
            refresh();
        } catch (ProcessDeploymentService.InvalidBpmnException e) {
            log.warn("Process Management: upload '{}' rejected: {}", fileName, e.getMessage());
            addError("Deployment rejected: " + e.getMessage());
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            log.error("Process Management: unexpected deployment failure for '{}'", fileName, e);
            addError("Deployment failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Enable / Disable (single row)
    // ------------------------------------------------------------------

    /** Definition id chosen by the per-row Enable/Disable button. */
    private String stateChangeDefinitionId;

    /** Human label of the definition being enabled/disabled. */
    private String stateChangeLabel;

    public String getStateChangeLabel() {
        return stateChangeLabel;
    }

    public void setStateChangeLabel(String stateChangeLabel) {
        this.stateChangeLabel = stateChangeLabel;
    }

    /** Enables (activates) one suspended process definition. */
    public void enableProcess() {
        changeStateSingle(false);
    }

    /** Disables (suspends) one active process definition. */
    public void disableProcess() {
        changeStateSingle(true);
    }

    private void changeStateSingle(boolean suspend) {
        if (!requireActionAllowed() || stateChangeDefinitionId == null) {
            return;
        }
        try {
            ProcessDeploymentService.StateChangeResult result = suspend
                    ? processDeploymentService.disableProcessDefinition(stateChangeDefinitionId,
                            currentUserId())
                    : processDeploymentService.enableProcessDefinition(stateChangeDefinitionId,
                            currentUserId());
            if (result.changed()) {
                addInfo("Process '" + stateChangeLabel + "' "
                        + (suspend ? "disabled (suspended)" : "enabled (activated)")
                        + ". Existing instances are not affected.");
            } else {
                addInfo("Process '" + stateChangeLabel + "' is already "
                        + (suspend ? "disabled" : "enabled") + " - nothing to do.");
            }
            refresh();
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            log.warn("Process Management: state change of {} failed: {}",
                    stateChangeDefinitionId, e.getMessage(), e);
            addError("Could not " + (suspend ? "disable" : "enable") + " process '"
                    + stateChangeLabel + "': " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Bulk enable / disable
    // ------------------------------------------------------------------

    /** Activates all selected (suspended) process definitions. */
    public void bulkEnable() {
        bulkChangeState(true);
    }

    /** Suspends all selected (active) process definitions. */
    public void bulkDisable() {
        bulkChangeState(false);
    }

    private void bulkChangeState(boolean enable) {
        if (!requireActionAllowed() || !isHasSelection()) {
            return;
        }
        try {
            ProcessDeploymentService.BulkStateChangeResult result = enable
                    ? processDeploymentService.enableProcessDefinitions(selectedDefinitionIds(),
                            currentUserId())
                    : processDeploymentService.disableProcessDefinitions(selectedDefinitionIds(),
                            currentUserId());
            addInfo(bulkStateMessage(result, enable));
            refresh();
            clearSelection();
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            log.warn("Process Management: bulk state change failed: {}", e.getMessage(), e);
            addError("Bulk " + (enable ? "enable" : "disable") + " failed: " + e.getMessage());
        }
    }

    private static String bulkStateMessage(ProcessDeploymentService.BulkStateChangeResult r,
                                           boolean enable) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.changedCount()).append(r.changedCount() == 1 ? " process" : " processes")
                .append(enable ? " enabled" : " disabled").append(" successfully");
        if (r.skippedCount() > 0) {
            sb.append(", ").append(r.skippedCount()).append(" skipped (already ")
                    .append(enable ? "enabled" : "disabled").append(')');
        }
        if (r.failedCount() > 0) {
            sb.append(", ").append(r.failedCount()).append(" failed: ");
            sb.append(r.failed().stream()
                    .map(f -> f.id() + " (" + f.reason() + ")")
                    .collect(Collectors.joining(", ")));
        }
        sb.append('.');
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Single undeploy
    // ------------------------------------------------------------------

    /**
     * First Undeploy attempt (no cascade). When the deployment still has
     * running process instances the service rejects it and the cascade
     * confirmation dialog is shown instead.
     */
    public void undeploy() {
        if (!requireActionAllowed()) {
            return;
        }
        cascadePending = false;
        try {
            ProcessDeploymentService.UndeployResult result =
                    processDeploymentService.undeployDeployment(selectedDeploymentId, false,
                            currentUserId());
            addInfo("Deployment " + result.deploymentId() + " ("
                    + selectedDeploymentLabel + ") undeployed. Running and finished process "
                    + "data of other definitions is untouched.");
            refresh();
        } catch (ProcessDeploymentService.DeploymentInUseException e) {
            // blocked on purpose: ask for an explicit cascade confirmation
            cascadePending = true;
            cascadeRunningCount = e.getRunningInstances();
            log.info("Process Management: undeploy of {} blocked - {} running instance(s)",
                    selectedDeploymentId, cascadeRunningCount);
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            log.warn("Process Management: undeploy of {} failed: {}", selectedDeploymentId,
                    e.getMessage(), e);
            addError("Could not undeploy deployment '" + selectedDeploymentLabel + "': "
                    + e.getMessage());
        }
    }

    /**
     * Confirmed cascade undeploy (second dialog): deletes the deployment
     * together with its still-running process instances (Flowable
     * {@code deleteDeployment(id, true)} semantics).
     */
    public void undeployWithCascade() {
        if (!requireActionAllowed()) {
            return;
        }
        cascadePending = false;
        try {
            ProcessDeploymentService.UndeployResult result =
                    processDeploymentService.undeployDeployment(selectedDeploymentId, true,
                            currentUserId());
            addInfo("Deployment " + result.deploymentId() + " (" + selectedDeploymentLabel
                    + ") undeployed together with " + result.deletedInstances()
                    + " running process instance(s). Historic data of finished instances "
                    + "remains available.");
            refresh();
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            log.warn("Process Management: cascade undeploy of {} failed: {}",
                    selectedDeploymentId, e.getMessage(), e);
            addError("Could not undeploy deployment '" + selectedDeploymentLabel + "': "
                    + e.getMessage());
        }
    }

    /** Cancels the cascade confirmation dialog. */
    public void cancelCascade() {
        cascadePending = false;
        selectedDeploymentId = null;
        selectedDeploymentLabel = null;
    }

    // ------------------------------------------------------------------
    // Bulk undeploy (two-stage: confirm -> blocked -> cascade confirm)
    // ------------------------------------------------------------------

    /**
     * First stage of "Delete Selected": opens the confirmation dialog
     * listing the selected processes (the actual delete runs after the
     * administrator confirms, in {@link #bulkUndeployConfirmed()}).
     */
    public void confirmBulkDelete() {
        if (!requireActionAllowed()) {
            return;
        }
        if (!isHasSelection()) {
            addError("Please select at least one process first.");
            return;
        }
        bulkDeleteConfirmPending = true;
        bulkCascadePending = false;
    }

    /** Second stage: processes every selected deployment (no cascade). */
    public void bulkUndeployConfirmed() {
        if (!requireActionAllowed()) {
            return;
        }
        bulkDeleteConfirmPending = false;
        try {
            ProcessDeploymentService.BulkUndeployResult result =
                    processDeploymentService.undeployDeployments(selectedDeploymentIds(), false,
                            currentUserId());
            if (result.blockedCount() > 0) {
                // blocked deployments require an explicit cascade decision
                bulkBlocked = new ArrayList<>(result.blocked());
                bulkCascadePending = true;
            }
            addInfo(bulkUndeployMessage(result, false));
            refresh();
            if (!bulkCascadePending) {
                clearSelection();
            }
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            log.warn("Process Management: bulk undeploy failed: {}", e.getMessage(), e);
            addError("Bulk undeploy failed: " + e.getMessage());
        }
    }

    /**
     * Cascade stage: deletes the previously blocked deployments together
     * with their running instances after the explicit confirmation.
     */
    public void bulkUndeployWithCascade() {
        if (!requireActionAllowed()) {
            return;
        }
        bulkCascadePending = false;
        try {
            List<String> blockedIds = bulkBlocked.stream()
                    .map(ProcessDeploymentService.DeploymentInUseInfo::deploymentId)
                    .collect(Collectors.toList());
            ProcessDeploymentService.BulkUndeployResult result =
                    processDeploymentService.undeployDeployments(blockedIds, true,
                            currentUserId());
            addInfo(bulkUndeployMessage(result, true));
            refresh();
            clearSelection();
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            log.warn("Process Management: bulk cascade undeploy failed: {}", e.getMessage(), e);
            addError("Bulk cascade undeploy failed: " + e.getMessage());
        } finally {
            bulkBlocked = Collections.emptyList();
        }
    }

    /** Cancels the bulk cascade confirmation dialog. */
    public void cancelBulkCascade() {
        bulkCascadePending = false;
        bulkBlocked = Collections.emptyList();
        clearSelection();
    }

    /** Cancels the first bulk delete confirmation dialog. */
    public void cancelBulkDelete() {
        bulkDeleteConfirmPending = false;
    }

    private static String bulkUndeployMessage(ProcessDeploymentService.BulkUndeployResult r,
                                              boolean cascade) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.deletedCount()).append(r.deletedCount() == 1 ? " deployment"
                        : " deployments")
                .append(cascade ? " deleted (including running instances)." : " deleted.");
        if (r.blockedCount() > 0) {
            sb.append(' ').append(r.blockedCount()).append(" blocked (running instances): ");
            sb.append(r.blocked().stream()
                    .map(b -> b.deploymentId() + " (" + b.runningInstances() + " instance(s))")
                    .collect(Collectors.joining(", ")));
        }
        if (r.failedCount() > 0) {
            sb.append(' ').append(r.failedCount()).append(" failed: ");
            sb.append(r.failed().stream()
                    .map(f -> f.id() + " (" + f.reason() + ")")
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Getters / setters
    // ------------------------------------------------------------------

    public String getSelectedDeploymentId() {
        return selectedDeploymentId;
    }

    public void setSelectedDeploymentId(String selectedDeploymentId) {
        this.selectedDeploymentId = selectedDeploymentId;
    }

    public String getSelectedDeploymentLabel() {
        return selectedDeploymentLabel;
    }

    public void setSelectedDeploymentLabel(String selectedDeploymentLabel) {
        this.selectedDeploymentLabel = selectedDeploymentLabel;
    }

    public boolean isCascadePending() {
        return cascadePending;
    }

    public long getCascadeRunningCount() {
        return cascadeRunningCount;
    }

    public boolean isBulkDeleteConfirmPending() {
        return bulkDeleteConfirmPending;
    }

    public boolean isBulkCascadePending() {
        return bulkCascadePending;
    }

    public List<ProcessDeploymentService.DeploymentInUseInfo> getBulkBlocked() {
        return bulkBlocked;
    }

    public String getBulkDeleteSummary() {
        return bulkDeleteSummary;
    }

    public String getStateChangeDefinitionId() {
        return stateChangeDefinitionId;
    }

    public void setStateChangeDefinitionId(String stateChangeDefinitionId) {
        this.stateChangeDefinitionId = stateChangeDefinitionId;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }
}