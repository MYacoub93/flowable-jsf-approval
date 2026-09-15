package com.example.approval.backing;

import com.example.approval.delegation.ProcessDefinitionOption;
import com.example.approval.entity.PageResult;
import com.example.approval.mycases.CaseRow;
import com.example.approval.mycases.CaseStatusFilter;
import com.example.approval.mycases.CaseTaskRow;
import com.example.approval.mycases.CaseVariableRow;
import com.example.approval.mycases.MyDecisionRow;
import com.example.approval.mycases.MyCasesService;
import com.example.approval.mycases.TimelineEventRow;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.primefaces.PrimeFaces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Backing bean for the <b>My Cases</b> page ({@code /my-cases.xhtml}): a
 * read-only follow-up of the process instances the logged-in user started.
 *
 * <p><b>Data flow:</b> the process filter dropdown and the server-side
 * paginated case table come from {@link MyCasesService}
 * ({@code HistoricProcessInstanceQuery.startedBy(userId)} - running
 * <i>and</i> finished cases). The details dialog content (overview, active
 * tasks, variables, timeline) is loaded <b>lazily</b> - only when a case is
 * selected, never for every row of the table.</p>
 *
 * <p><b>Security:</b> the page is rendered only for logged-in users; every
 * action re-verifies ownership inside the service
 * ({@code SecurityException} / empty result otherwise), so a tampered
 * process-instance id can never open another user's case.</p>
 */
@Component("myCasesBean")
@Scope("view")
public class MyCasesBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    @Autowired
    private MyCasesService myCasesService;

    // ---- filter + listing state -----------------------------------------

    private List<ProcessDefinitionOption> processes = Collections.emptyList();
    private String selectedProcessKey = "";
    private CaseStatusFilter selectedStatus = CaseStatusFilter.ALL;
    private int pageNumber = 1;
    private int pageSize = MyCasesService.DEFAULT_PAGE_SIZE;
    private PageResult<CaseRow> cases = PageResult.empty(1, MyCasesService.DEFAULT_PAGE_SIZE);

    // ---- "Cases I Approved" tab state (audit-backed listing) ------------

    private int approvedPageNumber = 1;
    private PageResult<CaseRow> approvedCases = PageResult.empty(1, MyCasesService.DEFAULT_PAGE_SIZE);

    // ---- details dialog state (loaded lazily on select) ------------------

    private CaseRow selectedCase;
    private List<CaseTaskRow> activeTasks = Collections.emptyList();
    private List<CaseVariableRow> variables = Collections.emptyList();
    private List<TimelineEventRow> timeline = Collections.emptyList();
    private List<String> currentActivityIds = Collections.emptyList();
    private List<MyDecisionRow> myDecisions = Collections.emptyList();

    @PostConstruct
    public void init() {
        if (!isLoggedIn()) {
            return;
        }
        reloadCases();
        reloadApprovedCases();
        try {
            processes = myCasesService.findAvailableProcesses();
        } catch (Exception e) {
            processes = Collections.emptyList();
            addError("mc.error.loadProcesses");
        }
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    private void reloadCases() {
        try {
            cases = myCasesService.findMyCases(currentUserIdOrThrow(),
                    selectedProcessKey, selectedStatus, pageNumber, pageSize);
            pageNumber = cases.getPageNumber();
        } catch (SecurityException e) {
            cases = PageResult.empty(pageNumber, pageSize);
            addError("mc.error.notAuthorized");
        } catch (Exception e) {
            cases = PageResult.empty(pageNumber, pageSize);
            addError("mc.error.loadCases");
        }
    }

    /** Re-runs the listing after the process dropdown changed. */
    public void onProcessChange() {
        pageNumber = 1;
        reloadCases();
    }

    /** Re-runs the listing after the status dropdown changed. */
    public void onStatusChange() {
        pageNumber = 1;
        reloadCases();
    }

    public void refresh() {
        reloadCases();
        reloadApprovedCases();
    }

    // pagination actions ---------------------------------------------------

    public void firstPage() {
        pageNumber = 1;
        reloadCases();
    }

    public void previousPage() {
        pageNumber = Math.max(1, pageNumber - 1);
        reloadCases();
    }

    public void nextPage() {
        pageNumber = pageNumber + 1;
        reloadCases();
    }

    public void lastPage() {
        pageNumber = cases.getLastPage();
        reloadCases();
    }

    // "Cases I Approved" tab -----------------------------------------------

    private void reloadApprovedCases() {
        try {
            approvedCases = myCasesService.findApprovedCases(
                    currentUsernameOrThrow(), approvedPageNumber, pageSize);
            approvedPageNumber = approvedCases.getPageNumber();
        } catch (SecurityException e) {
            approvedCases = PageResult.empty(approvedPageNumber, pageSize);
        } catch (Exception e) {
            approvedCases = PageResult.empty(approvedPageNumber, pageSize);
            addError("mc.error.loadCases");
        }
    }

    public void approvedFirstPage() {
        approvedPageNumber = 1;
        reloadApprovedCases();
    }

    public void approvedPreviousPage() {
        approvedPageNumber = Math.max(1, approvedPageNumber - 1);
        reloadApprovedCases();
    }

    public void approvedNextPage() {
        approvedPageNumber = approvedPageNumber + 1;
        reloadApprovedCases();
    }

    public void approvedLastPage() {
        approvedPageNumber = approvedCases.getLastPage();
        reloadApprovedCases();
    }

    // ------------------------------------------------------------------
    // Details dialog (lazy-loaded on select)
    // ------------------------------------------------------------------

    /**
     * Opens the case-details dialog for the given process instance:
     * ownership is re-verified server-side (the initiator restriction of
     * {@code MyCasesService.requireOwnedInstance}); tasks, variables,
     * timeline and the current activities are loaded only now - the main
     * table never pays for them.
     */
    public void selectCase(String processInstanceId) {
        String userId = currentUserIdOrThrow();
        String username = currentUsernameOrNull();
        try {
            CaseRow overview = myCasesService.findCaseOverview(userId, username, processInstanceId);
            if (overview == null) {
                // not found / not started by the user / never acted on by
                // them - never disclose which of the three it is
                selectedCase = null;
                addError("mc.error.caseNotFound");
                return;
            }
            selectedCase = overview;
            currentActivityIds = myCasesService.findCurrentActivityIds(userId, username, processInstanceId);
            activeTasks = myCasesService.findActiveTasks(userId, username, processInstanceId);
            variables = myCasesService.findVariables(userId, username, processInstanceId);
            timeline = myCasesService.findTimeline(userId, username, processInstanceId);
            myDecisions = myCasesService.findMyDecisions(userId, username, processInstanceId);
            if (PrimeFaces.current().isAjaxRequest()) {
                PrimeFaces.current().executeScript("PF('caseDetailsDialog').show()");
            }
        } catch (SecurityException e) {
            selectedCase = null;
            addError("mc.error.notAuthorized");
        } catch (Exception e) {
            selectedCase = null;
            addError("mc.error.loadDetails");
        }
    }

    /** Closes the dialog and drops the lazily loaded details state. */
    public void closeCase() {
        selectedCase = null;
        activeTasks = Collections.emptyList();
        variables = Collections.emptyList();
        timeline = Collections.emptyList();
        currentActivityIds = Collections.emptyList();
        myDecisions = Collections.emptyList();
    }

    // ------------------------------------------------------------------
    // EL-facing helpers
    // ------------------------------------------------------------------

    /** Localized status caption ("In Progress" / "Completed"). */
    public String statusOf(CaseRow row) {
        return row == null ? "" : getLabel("mc.status." + row.getStatusKey());
    }

    /** Localized caption of one timeline event type. */
    public String typeCaptionOf(TimelineEventRow event) {
        return event == null || event.getTypeKey() == null
                ? "" : getLabel("mc.timeline.type." + event.getTypeKey());
    }

    /** Comma-joined current activity ids (or "-") for the overview tab. */
    public String getCurrentActivitiesDisplay() {
        return currentActivityIds.isEmpty() ? "-" : String.join(", ", currentActivityIds);
    }

    /** Read-only diagram image URL of the selected case. */
    public String getDiagramUrl() {
        if (selectedCase == null) {
            return null;
        }
        FacesContext facesContext = FacesContext.getCurrentInstance();
        String contextPath = facesContext != null
                ? facesContext.getExternalContext().getRequestContextPath()
                : "";
        return contextPath + "/my-cases/diagram/" + selectedCase.getId();
    }

    /** "1 - 10 / 42"-style range label of the current page. */
    public String getRangeLabel() {
        long total = cases.getTotalRows();
        if (total == 0) {
            return getLabel("mc.noCases");
        }
        long first = (long) (cases.getPageNumber() - 1) * cases.getPageSize() + 1;
        long last = Math.min(first + cases.getPageSize() - 1, total);
        return getLabel("mc.pager.range", first, last, total);
    }

    public boolean isHasNext() {
        return cases.getPageNumber() < cases.getLastPage();
    }

    public boolean isHasPrevious() {
        return cases.getPageNumber() > cases.getFirstPage();
    }

    public List<String> getCurrentActivityIds() {
        return currentActivityIds;
    }

    private String currentUserIdOrThrow() {
        String userId = getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new SecurityException("No logged-in user");
        }
        return userId;
    }

    /**
     * The logged-in user's <b>username</b> (Flowable
     * {@code FLOWABLE_USERS_VW.USERNAME_}) or {@code null}; the audit-based
     * guards treat {@code null} as "audit check impossible" and fall back
     * to owner-only access.
     */
    private String currentUsernameOrNull() {
        return getSessionInfo() != null ? getSessionInfo().getUsername() : null;
    }

    /**
     * The logged-in user's <b>username</b> or a {@link SecurityException}
     * when the session carries none (audit-backed queries key on the
     * username, never on a hard-coded one).
     */
    private String currentUsernameOrThrow() {
        String username = currentUsernameOrNull();
        if (username == null || username.isBlank()) {
            throw new SecurityException("No logged-in user");
        }
        return username;
    }

    private void addError(String key) {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext != null) {
            facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    getLabel(key), null));
        }
    }

    // ------------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------------

    public List<ProcessDefinitionOption> getProcesses() {
        return processes;
    }

    public String getSelectedProcessKey() {
        return selectedProcessKey;
    }

    public void setSelectedProcessKey(String selectedProcessKey) {
        this.selectedProcessKey = selectedProcessKey;
    }

    public CaseStatusFilter getSelectedStatus() {
        return selectedStatus;
    }

    public void setSelectedStatus(CaseStatusFilter selectedStatus) {
        this.selectedStatus = selectedStatus == null ? CaseStatusFilter.ALL : selectedStatus;
    }

    /** Dropdown options: the {@link CaseStatusFilter} values, ALL first. */
    public CaseStatusFilter[] getStatusOptions() {
        return CaseStatusFilter.values();
    }

    /** Localized caption of one status-filter option. */
    public String statusCaptionOf(CaseStatusFilter filter) {
        return filter == null ? "" : getLabel("mc.status." + filter.getToken());
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<CaseRow> getCases() {
        return cases.getRows();
    }

    public boolean isHasCases() {
        return !cases.getRows().isEmpty();
    }

    public CaseRow getSelectedCase() {
        return selectedCase;
    }

    public List<CaseTaskRow> getActiveTasks() {
        return activeTasks;
    }

    public List<CaseVariableRow> getVariables() {
        return variables;
    }

    public List<TimelineEventRow> getTimeline() {
        return timeline;
    }

    public List<MyDecisionRow> getMyDecisions() {
        return myDecisions;
    }

    /** Localized caption of one decision key ("Approved"/"Rejected"/...). */
    public String decisionCaptionOf(MyDecisionRow decision) {
        return decision == null || decision.getActionKey() == null
                ? "" : getLabel("mc.decision." + decision.getActionKey());
    }

    /** Timeline rows (newest first display uses the same chronological list). */
    public List<TimelineEventRow> getTimelineDescending() {
        return timeline.stream()
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    Collections.reverse(list);
                    return list;
                }));
    }

    // ---- "Cases I Approved" tab EL accessors -----------------------------

    public List<CaseRow> getApprovedCases() {
        return approvedCases.getRows();
    }

    public boolean isHasApprovedCases() {
        return !approvedCases.getRows().isEmpty();
    }

    public boolean isApprovedHasNext() {
        return approvedCases.getPageNumber() < approvedCases.getLastPage();
    }

    public boolean isApprovedHasPrevious() {
        return approvedCases.getPageNumber() > approvedCases.getFirstPage();
    }

    /** "1 - 10 / 42"-style range label of the current approved-cases page. */
    public String getApprovedRangeLabel() {
        long total = approvedCases.getTotalRows();
        if (total == 0) {
            return getLabel("mc.noApprovedCases");
        }
        long first = (long) (approvedCases.getPageNumber() - 1) * approvedCases.getPageSize() + 1;
        long last = Math.min(first + approvedCases.getPageSize() - 1, total);
        return getLabel("mc.pager.range", first, last, total);
    }
}
