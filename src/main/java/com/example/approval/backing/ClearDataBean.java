package com.example.approval.backing;

import com.example.approval.clear.ClearDataService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Backs the dev-only "Clear Data" administration screen
 * ({@code /clear-data.xhtml}): lists running and finished process instances
 * and offers per-row and global delete actions that wipe the
 * <b>Flowable</b> data (runtime + history) of the selected instances.
 *
 * <p>Rendered (and functional) only when <b>both</b> guards hold:</p>
 * <ul>
 *   <li>{@code app.dev-mode.enabled=true} (see
 *       {@link ClearDataService#isDevMode()}), and</li>
 *   <li>the logged-in user is a group admin ({@code ADM} group, see
 *       {@link ClearDataService#isClearDataAllowed(String)}).</li>
 * </ul>
 *
 * <p>The backing bean re-checks the same guards on every action - the
 * hidden menu entry alone is never the security boundary. The Oracle
 * {@code F_BPM_*} business audit trail is intentionally NOT touched by
 * this tool.</p>
 */
@Component("clearDataBean")
@RequestScope
public class ClearDataBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ClearDataService clearDataService;

    /** Selected instance id of the per-row delete button. */
    private String selectedInstanceId;

    private List<ProcessInstance> runningInstances = Collections.emptyList();
    private List<HistoricProcessInstance> finishedInstances = Collections.emptyList();

    @PostConstruct
    public void init() {
        if (isPageVisible()) {
            runningInstances = clearDataService.findRunningInstances();
            finishedInstances = clearDataService.findFinishedInstances();
        }
    }

    // ------------------------------------------------------------------
    // Guards (EL-facing)
    // ------------------------------------------------------------------

    /** Whether the whole page may render at all. */
    public boolean isPageVisible() {
        return loginBean.isLoggedIn() && clearDataService.isDevMode();
    }

    /** Whether the acting user may perform the destructive actions. */
    public boolean isActionAllowed() {
        return isPageVisible()
                && clearDataService.isClearDataAllowed(loginBean.getCurrentUser().getId());
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Per-row action: delete the Flowable data of one process instance. */
    public void clearInstance() {
        if (!requireActionAllowed()) {
            return;
        }
        try {
            ClearDataService.ClearResult result =
                    clearDataService.clearInstance(selectedInstanceId, loginBean.getCurrentUser().getId());
            addInfo("Flowable data of instance '" + selectedInstanceId + "' deleted (runtime: "
                    + yesNo(result.isRuntimeDeleted()) + ", history: "
                    + yesNo(result.isHistoryDeleted()) + "). Oracle audit rows kept.");
            refresh();
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            addError("Could not clear instance '" + selectedInstanceId + "': " + e.getMessage());
        }
    }

    /** Global action: delete the Flowable data of ALL process instances. */
    public void clearAll() {
        if (!requireActionAllowed()) {
            return;
        }
        try {
            ClearDataService.ClearResult total =
                    clearDataService.clearAll(loginBean.getCurrentUser().getId());
            addInfo("Cleared " + total.getInstancesCleared() + " instance(s)"
                    + (total.getInstancesFailed() > 0
                            ? ", " + total.getInstancesFailed() + " failed (see log)"
                            : "")
                    + ". Oracle audit rows kept.");
            refresh();
        } catch (SecurityException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            addError("Could not clear all instances: " + e.getMessage());
        }
    }

    private boolean requireActionAllowed() {
        if (isActionAllowed()) {
            return true;
        }
        addError("Clear data is only available to ADM users in dev mode.");
        return false;
    }

    /** Reloads the two tables after a successful clear. */
    private void refresh() {
        runningInstances = clearDataService.findRunningInstances();
        finishedInstances = clearDataService.findFinishedInstances();
    }

    // ------------------------------------------------------------------
    // Table data
    // ------------------------------------------------------------------

    public List<ProcessInstance> getRunningInstances() {
        return runningInstances;
    }

    public List<HistoricProcessInstance> getFinishedInstances() {
        return finishedInstances;
    }

    public int getRunningCount() {
        return runningInstances.size();
    }

    public int getFinishedCount() {
        return finishedInstances.size();
    }

    public String getSelectedInstanceId() {
        return selectedInstanceId;
    }

    public void setSelectedInstanceId(String selectedInstanceId) {
        this.selectedInstanceId = selectedInstanceId;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String yesNo(boolean b) {
        return b ? "yes" : "no";
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }
}