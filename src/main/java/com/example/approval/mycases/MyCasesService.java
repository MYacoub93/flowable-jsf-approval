package com.example.approval.mycases;

import com.example.approval.audit.BpmAuditAction;
import com.example.approval.audit.model.BpmAuditLogDtl;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.delegation.ProcessDefinitionOption;
import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.PageResult;
import com.example.approval.service.ExternalGroupService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <b>My Cases</b> service ({@code /my-cases.xhtml}): read-only follow-up of
 * the process instances the currently logged-in user has started - running
 * and already finished ones.
 *
 * <p><b>Data access follows the existing conventions of this
 * application:</b></p>
 * <ul>
 *   <li>All engine data is read through Flowable's own Query API
 *       ({@code RepositoryService}, {@code RuntimeService},
 *       {@code TaskService}, {@code HistoryService}) with
 *       {@code listPage()/count()} server-side pagination - the same
 *       pattern as {@code ApprovalService} and
 *       {@code TaskDelegationService}. Flowable engine tables are never
 *       queried with SQL directly.</li>
 *   <li>The listing uses {@code HistoricProcessInstanceQuery} so finished
 *       cases are included; the business-level timeline events come from
 *       the existing BPM audit service
 *       ({@link BpmAuditService#findDetailsOfProcessInstance}), merged with
 *       the Flowable history - no second history mechanism is created.</li>
 *   <li>User ids are resolved to usernames through the existing
 *       {@code FLOWABLE_USERS_VW} lookup
 *       ({@link ExternalGroupService#findUsersByIds}) in one batch query
 *       per page/section. No user is ever hard-coded.</li>
 * </ul>
 *
 * <p><b>Security (server-side, every method):</b> the initiator restriction
 * is enforced <b>inside the Flowable queries</b> -
 * {@code .startedBy(userId)} for the listing and for the single-instance
 * guard {@link #requireOwnedInstance} - never only in the UI. A user
 * tampering with a process-instance id in a request gets a
 * {@link SecurityException}, not another user's case. The feature is purely
 * read-only: no mutation API of the engine is called anywhere.</p>
 */
@Service
public class MyCasesService {

    private static final Logger log = LoggerFactory.getLogger(MyCasesService.class);

    /** Default rows per page (same default as the other listings). */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    /** Max length of a rendered variable value (defensive truncation). */
    private static final int MAX_VARIABLE_TEXT = 500;

    /** Internal Flowable variable prefix (never shown to the user). */
    private static final String INTERNAL_VARIABLE_PREFIX = "_";

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ExternalGroupService groupService;
    private final BpmAuditService auditService;

    public MyCasesService(RepositoryService repositoryService,
                          RuntimeService runtimeService,
                          TaskService taskService,
                          HistoryService historyService,
                          ExternalGroupService groupService,
                          BpmAuditService auditService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.groupService = groupService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Process filter dropdown
    // ------------------------------------------------------------------

    /**
     * The deployed processes (latest version of every key, ordered by key)
     * offered by the "Process" dropdown - the same source as the
     * applications' Processes page, available to every logged-in user.
     */
    public List<ProcessDefinitionOption> findAvailableProcesses() {
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey()
                .asc()
                .list()
                .stream()
                .map(d -> new ProcessDefinitionOption(d.getId(), d.getKey(), d.getName()))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Case listing (server-side paginated, initiator enforced in query)
    // ------------------------------------------------------------------

    /**
     * One page of the <b>historic</b> process instances started by the given
     * user - running <i>and</i> finished cases - optionally filtered by
     * process definition key, newest first.
     *
     * <p>The initiator restriction ({@code START_USER_ = userId}) is part of
     * the Flowable query itself, so the database only ever returns the
     * user's own cases; pagination ({@code listPage}/{@code count}) happens
     * in the engine, not in memory.</p>
     *
     * @param processDefinitionKey nullable filter; blank = all processes
     */
    public PageResult<CaseRow> findMyCases(String userId,
                                           String processDefinitionKey,
                                           int pageNumber,
                                           int pageSize) {
        return findMyCases(userId, processDefinitionKey, CaseStatusFilter.ALL,
                pageNumber, pageSize);
    }

    /**
     * One page of the <b>historic</b> process instances started by the given
     * user, optionally filtered by process definition key <i>and</i> by
     * status ({@link CaseStatusFilter}), newest first.
     *
     * <p>The initiator restriction ({@code START_USER_ = userId}) and the
     * status criteria ({@code finished()/unfinished()/deleted()}) are part
     * of the Flowable query itself, so the database only ever returns the
     * user's own cases in the requested state; pagination
     * ({@code listPage}/{@code count}) happens in the engine, not in
     * memory.</p>
     *
     * @param processDefinitionKey nullable filter; blank = all processes
     * @param statusFilter         nullable status restriction; null = all
     */
    public PageResult<CaseRow> findMyCases(String userId,
                                           String processDefinitionKey,
                                           CaseStatusFilter statusFilter,
                                           int pageNumber,
                                           int pageSize) {
        requireLoggedIn(userId);
        int page = Math.max(pageNumber, 1);
        int size = normalizeSize(pageSize);

        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .startedBy(userId);
        if (processDefinitionKey != null && !processDefinitionKey.isBlank()) {
            query = query.processDefinitionKey(processDefinitionKey.trim());
        }
        final HistoricProcessInstanceQuery filtered = applyStatusCriteria(query, statusFilter)
                .orderByProcessInstanceStartTime()
                .desc();

        long total = filtered.count();
        if (total == 0) {
            return PageResult.empty(page, size);
        }
        long offset = (long) (page - 1) * size;
        if (offset >= total) {
            // requested page beyond the end - clamp to the last page
            page = (int) ((total + size - 1) / size);
            offset = (long) (page - 1) * size;
        }
        List<CaseRow> rows = filtered.listPage((int) offset, size)
                .stream()
                .map(this::toCaseRow)
                .collect(Collectors.toList());
        rows = withInitiatorUsername(rows);
        return new PageResult<>(rows, total, page, size);
    }

    /**
     * Applies the status restriction inside the query (server-side, never
     * UI-filtered): {@code unfinished()} = still running,
     * {@code finished().notDeleted()} = completed normally,
     * {@code finished().deleted()} = cancelled/deleted while running.
     */
    private static HistoricProcessInstanceQuery applyStatusCriteria(
            HistoricProcessInstanceQuery query, CaseStatusFilter statusFilter) {
        CaseStatusFilter status = statusFilter == null ? CaseStatusFilter.ALL : statusFilter;
        switch (status) {
            case IN_PROGRESS:
                return query.unfinished();
            case COMPLETED:
                return query.finished().notDeleted();
            case CANCELLED:
                return query.finished().deleted();
            case ALL:
            default:
                return query;
        }
    }

    private CaseRow toCaseRow(HistoricProcessInstance instance) {
        String name = instance.getName() != null && !instance.getName().isBlank()
                ? instance.getName()
                : instance.getProcessDefinitionName();
        return new CaseRow(instance.getId(), name,
                instance.getProcessDefinitionKey(),
                instance.getProcessDefinitionVersion(),
                instance.getBusinessKey(), instance.getStartUserId(),
                null, instance.getStartTime(), instance.getEndTime(),
                instance.getEndTime() != null, instance.getDeleteReason());
    }

    /**
     * Resolves the initiator ids of one page to {@code username (id)}
     * through {@code FLOWABLE_USERS_VW} - one batch query per page. A
     * lookup failure never breaks the listing; the bare id is shown.
     */
    private List<CaseRow> withInitiatorUsername(List<CaseRow> rows) {
        List<String> ids = rows.stream()
                .map(CaseRow::getStartedBy)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> displayById = resolveUserDisplays(ids);
        if (displayById.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(row -> displayById.containsKey(row.getStartedBy())
                        ? row.withStartedByDisplay(displayById.get(row.getStartedBy()))
                        : row)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Ownership guard - the security boundary for every details section
    // ------------------------------------------------------------------

    /**
     * Loads the historic process instance and verifies <b>server-side</b>
     * that it was started by the given user. Returns {@code null} when the
     * instance does not exist at all (indistinguishable from "not yours" by
     * design) so a tampered id can never leak another user's case.
     */
    public HistoricProcessInstance requireOwnedInstance(String userId,
                                                        String processInstanceId) {
        requireLoggedIn(userId);
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId.trim())
                .startedBy(userId)
                .singleResult();
    }

    private static void requireLoggedIn(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new SecurityException("No logged-in user");
        }
    }

    /**
     * Loads the historic process instance and verifies <b>server-side</b>
     * that the user may view it: either they <b>started</b> it
     * ({@code startedBy(userId)}, the original guard) <i>or</i> they
     * recorded at least one action on it in the existing BPM audit trail
     * ({@link BpmAuditService#hasUserActedOnCase}). Returns {@code null}
     * when the instance does not exist or is neither (indistinguishable by
     * design), so a tampered id can never leak another user's case. An
     * audit-store outage never locks the owner out: the ownership branch
     * runs first and an audit exception degrades to owner-only access.
     */
    public HistoricProcessInstance requireViewableInstance(String userId,
                                                           String username,
                                                           String processInstanceId) {
        HistoricProcessInstance owned = requireOwnedInstance(userId, processInstanceId);
        if (owned != null) {
            return owned;
        }
        if (username == null || username.isBlank()
                || processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        try {
            if (auditService.hasUserActedOnCase(username, processInstanceId.trim())) {
                return historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstanceId.trim())
                        .singleResult();
            }
        } catch (Exception e) {
            log.warn("Could not check the audit trail for case {} of user {}: {}",
                    processInstanceId, username, e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Cases I Approved (audit-backed listing, read-only)
    // ------------------------------------------------------------------

    /**
     * One page of the distinct cases the given username recorded at least
     * one audit action on - the <b>Cases I Approved</b> tab - most recently
     * acted-on case first. The case ids come from the existing BPM audit
     * service ({@code ENTRY_USER} restriction in SQL); only the <i>ids of
     * the requested page</i> are then hydrated through the Flowable history
     * query API, so the tab never loads whole cases for every row. Cases
     * whose engine history was purged are skipped silently.
     */
    public PageResult<CaseRow> findApprovedCases(String username,
                                                 int pageNumber,
                                                 int pageSize) {
        requireLoggedIn(username);
        int page = Math.max(pageNumber, 1);
        int size = normalizeSize(pageSize);

        List<String> ids;
        try {
            ids = auditService.findCaseIdsActedByUser(username);
        } catch (Exception e) {
            log.warn("Could not load the acted-on case ids of user {}: {}",
                    username, e.getMessage());
            return PageResult.empty(page, size);
        }
        if (ids == null || ids.isEmpty()) {
            return PageResult.empty(page, size);
        }
        long total = ids.size();
        if (page > (total + size - 1) / size) {
            page = (int) ((total + size - 1) / size);
        }
        int from = (int) Math.min((long) (page - 1) * size, total);
        int to = (int) Math.min((long) page * size, total);
        List<String> pageIds = ids.subList(from, to);
        if (pageIds.isEmpty()) {
            return PageResult.empty(page, size);
        }
        Map<String, CaseRow> rowById = historyService.createHistoricProcessInstanceQuery()
                .processInstanceIds(new LinkedHashSet<>(pageIds))
                .list()
                .stream()
                .collect(Collectors.toMap(HistoricProcessInstance::getId, this::toCaseRow));
        // keep the audit order (most recently acted-on first)
        List<CaseRow> rows = new ArrayList<>();
        for (String id : pageIds) {
            CaseRow row = rowById.get(id);
            if (row != null) {
                rows.add(row);
            }
        }
        return new PageResult<>(withInitiatorUsername(rows), total, page, size);
    }

    /**
     * The <b>My Decisions</b> of the logged-in user on one viewable case:
     * exactly the audit rows of that case the user personally recorded
     * ({@code ENTRY_USER = user}), oldest first, each carrying a stable
     * decision key ({@code approved / rejected / reviewed / received /
     * other}) derived from the existing {@link BpmAuditAction} code.
     */
    public List<MyDecisionRow> findMyDecisions(String userId,
                                               String username,
                                               String processInstanceId) {
        HistoricProcessInstance instance = requireViewableInstance(userId, username, processInstanceId);
        if (instance == null || username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        Integer numericUserId;
        try {
            numericUserId = auditService.resolveNumericUserId(username);
        } catch (Exception e) {
            log.warn("Could not resolve the numeric id of user {}: {}", username, e.getMessage());
            return Collections.emptyList();
        }
        if (numericUserId == null) {
            return Collections.emptyList();
        }
        List<BpmAuditLogDtl> details;
        try {
            details = auditService.findDetailsOfProcessInstance(instance.getId());
        } catch (Exception e) {
            log.warn("Could not load the audit details of case {}: {}",
                    instance.getId(), e.getMessage());
            return Collections.emptyList();
        }
        if (details == null) {
            return Collections.emptyList();
        }
        return details.stream()
                .filter(d -> d != null && numericUserId.equals(d.getEntryUser()))
                .filter(d -> d.getEntryDate() != null)
                .sorted(Comparator.comparing(BpmAuditLogDtl::getEntryDate))
                .map(d -> new MyDecisionRow(toDate(d.getEntryDate()),
                        decisionKeyOf(d.getActionCode()), auditActionTitle(d), d.getNote()))
                .collect(Collectors.toList());
    }

    /**
     * Stable decision key of one audit action code for the
     * {@code mc.decision.<key>} label entries.
     */
    static String decisionKeyOf(Integer actionCode) {
        BpmAuditAction action;
        try {
            action = actionCode == null ? null : BpmAuditAction.fromCode(actionCode);
        } catch (Exception e) {
            action = null;
        }
        if (action == null) {
            return "other";
        }
        String name = action.name();
        if (name.endsWith("_APPROVAL")) {
            return "approved";
        }
        if (name.endsWith("_REJECTION")) {
            return "rejected";
        }
        if (name.endsWith("_REVIEW")) {
            return "reviewed";
        }
        if (name.startsWith("TASK_RECEIVED")) {
            return "received";
        }
        return "other";
    }

    // ------------------------------------------------------------------
    // Case overview
    // ------------------------------------------------------------------

    /**
     * Overview data of one case the user may view - started by them, or
     * acted on by them in the audit trail ({@code null} otherwise;
     * "not found" and "not yours" stay indistinguishable).
     */
    public CaseRow findCaseOverview(String userId, String username, String processInstanceId) {
        HistoricProcessInstance instance = requireViewableInstance(userId, username, processInstanceId);
        if (instance == null) {
            return null;
        }
        return withInitiatorUsername(List.of(toCaseRow(instance))).get(0);
    }

    /**
     * The ids of the currently active activities of the case (empty for a
     * finished case) - used for the "current state" line of the overview.
     */
    public List<String> findCurrentActivityIds(String userId, String username, String processInstanceId) {
        HistoricProcessInstance instance = requireViewableInstance(userId, username, processInstanceId);
        if (instance == null || instance.getEndTime() != null) {
            return Collections.emptyList();
        }
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        if (runtime == null) {
            return Collections.emptyList();
        }
        return runtimeService.getActiveActivityIds(instance.getId());
    }

    // ------------------------------------------------------------------
    // Active tasks
    // ------------------------------------------------------------------

    /**
     * The <b>active</b> ({@code TaskQuery.active()}) tasks of the case -
     * completed, cancelled and suspended tasks are never returned. The
     * assignee of each row is resolved to {@code username (id)} in one batch
     * query; a candidate group is never rendered as an individual assignee
     * (unassigned tasks keep a null assignee and the UI shows
     * "Unassigned").
     */
    public List<CaseTaskRow> findActiveTasks(String userId, String username, String processInstanceId) {
        HistoricProcessInstance instance = requireViewableInstance(userId, username, processInstanceId);
        if (instance == null || instance.getEndTime() != null) {
            return Collections.emptyList();
        }
        List<CaseTaskRow> rows = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .map(this::toCaseTaskRow)
                .collect(Collectors.toList());
        return withAssigneeDisplays(rows);
    }

    private CaseTaskRow toCaseTaskRow(Task task) {
        return new CaseTaskRow(task.getId(), task.getName(), task.getAssignee(),
                null, task.getClaimTime(), task.getCreateTime(),
                task.getDueDate(), candidateGroupsOf(task.getId()));
    }

    private List<CaseTaskRow> withAssigneeDisplays(List<CaseTaskRow> rows) {
        List<String> ids = rows.stream()
                .map(CaseTaskRow::getAssignee)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> displayById = resolveUserDisplays(ids);
        if (displayById.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(row -> displayById.containsKey(row.getAssignee())
                        ? row.withAssigneeDisplay(displayById.get(row.getAssignee()))
                        : row)
                .collect(Collectors.toList());
    }

    /** Comma-joined candidate group ids of one task (null when none). */
    private String candidateGroupsOf(String taskId) {
        List<String> groups = taskService.getIdentityLinksForTask(taskId).stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()))
                .map(IdentityLink::getGroupId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return groups.isEmpty() ? null : String.join(", ", groups);
    }

    // ------------------------------------------------------------------
    // Process variables
    // ------------------------------------------------------------------

    /**
     * The process variables of the case, read through the Flowable
     * <b>history</b> API (works for running and finished cases; the engine
     * tables are never queried directly). Internal variables (null/blank
     * names or the Flowable-internal {@code _} prefix) are hidden; values
     * are rendered type-safely (Date/number/boolean/JSON/plain text) and
     * defensively truncated.
     */
    public List<CaseVariableRow> findVariables(String userId, String username, String processInstanceId) {
        HistoricProcessInstance instance = requireViewableInstance(userId, username, processInstanceId);
        if (instance == null) {
            return Collections.emptyList();
        }
        List<HistoricVariableInstance> variables = historyService
                .createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .list();
        return variables.stream()
                .filter(v -> v.getVariableName() != null
                        && !v.getVariableName().isBlank()
                        && !v.getVariableName().startsWith(INTERNAL_VARIABLE_PREFIX))
                .sorted(Comparator.comparing(HistoricVariableInstance::getVariableName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(v -> new CaseVariableRow(v.getVariableName(),
                        renderVariableValue(v.getValue()),
                        v.getVariableTypeName()))
                .collect(Collectors.toList());
    }

    /** Type-safe, display-safe rendering of one variable value. */
    static String renderVariableValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return "<binary>";
        }
        String text;
        if (value instanceof Date date) {
            text = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
        } else {
            text = String.valueOf(value);
        }
        return text.length() > MAX_VARIABLE_TEXT
                ? text.substring(0, MAX_VARIABLE_TEXT) + "…"
                : text;
    }

    // ------------------------------------------------------------------
    // Process diagram (read-only)
    // ------------------------------------------------------------------

    /**
     * Renders the BPMN diagram of the case's process definition as a PNG,
     * highlighting the current/active activities (blue) and the already
     * taken sequence flows - reusing Flowable's own
     * {@link DefaultProcessDiagramGenerator}. Read-only by design: the
     * generator only reads the {@link BpmnModel}, no BPMN source is
     * modified.
     *
     * @throws SecurityException               when the case is not viewable by the user
     * @throws org.flowable.common.engine.api.FlowableObjectNotFoundException
     *                                           when the case/definition is gone
     */
    public byte[] generateDiagramImage(String userId, String username, String processInstanceId) {
        HistoricProcessInstance instance = requireViewableInstance(userId, username, processInstanceId);
        if (instance == null) {
            // distinguish "not viewable" (403) from "gone" (404) for the caller
            boolean exists = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId.trim())
                    .count() > 0;
            if (exists) {
                throw new SecurityException("Process instance " + processInstanceId
                        + " is not viewable by user " + userId);
            }
            throw new org.flowable.common.engine.api.FlowableObjectNotFoundException(
                    "Process instance " + processInstanceId + " not found");
        }

        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(instance.getProcessDefinitionId())
                .singleResult();
        if (definition == null) {
            throw new org.flowable.common.engine.api.FlowableObjectNotFoundException(
                    "Process definition " + instance.getProcessDefinitionId() + " not found");
        }
        BpmnModel model = repositoryService.getBpmnModel(definition.getId());
        if (model.getLocationMap().isEmpty()) {
            // same guard as Flowable's own diagram REST resources: a model
            // without graphical (DI) information cannot be rendered
            throw new org.flowable.common.engine.api.FlowableObjectNotFoundException(
                    "Process definition " + definition.getId()
                            + " has no graphical information - no diagram available");
        }

        List<String> highlightedActivities = instance.getEndTime() == null
                ? runtimeService.getActiveActivityIds(instance.getId())
                : Collections.emptyList();

        // already-taken sequence flows, derived from the history
        List<String> highlightedFlows = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instance.getId())
                .activityType("sequenceFlow")
                .finished()
                .list()
                .stream()
                .map(HistoricActivityInstance::getActivityId)
                .distinct()
                .collect(Collectors.toList());

        ProcessDiagramGenerator generator = new DefaultProcessDiagramGenerator();
        try (InputStream in = generator.generateDiagram(model, "png",
                highlightedActivities, highlightedFlows, 1.0, true)) {
            return readAll(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not render the process diagram", e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    // ------------------------------------------------------------------
    // Timeline
    // ------------------------------------------------------------------

    /**
     * The chronological timeline of the case, merged from three existing
     * sources (no second history mechanism):
     * <ol>
     *   <li>the Flowable <b>historic process instance</b> (started /
     *       completed events),</li>
     *   <li>the existing <b>BPM audit service</b>
     *       ({@link BpmAuditService#findDetailsOfProcessInstance}) for the
     *       business-level actions (assigned, approved, rejected,
     *       delegated, ...),</li>
     *   <li>the Flowable <b>historic task instances</b> (task created /
     *       completed milestones with the actual assignees).</li>
     * </ol>
     * Rows carry a stable {@code typeKey} (localized in the backing bean)
     * and are sorted by timestamp ascending.
     */
    public List<TimelineEventRow> findTimeline(String userId, String username, String processInstanceId) {
        HistoricProcessInstance instance = requireViewableInstance(userId, username, processInstanceId);
        if (instance == null) {
            return Collections.emptyList();
        }

        List<TimelineEventRow> events = new ArrayList<>();

        // 1. process started / completed (engine history)
        events.add(new TimelineEventRow(instance.getStartTime(), "started",
                displayNameOf(instance), instance.getStartUserId(), null));
        if (instance.getEndTime() != null) {
            events.add(new TimelineEventRow(instance.getEndTime(), "completed",
                    displayNameOf(instance), instance.getStartUserId(), null));
        }

        // 2. business-level audit trail (existing BPM audit service)
        try {
            List<BpmAuditLogDtl> details =
                    auditService.findDetailsOfProcessInstance(instance.getId());
            if (details != null) {
                for (BpmAuditLogDtl detail : details) {
                    if (detail == null || detail.getEntryDate() == null) {
                        continue;
                    }
                    events.add(new TimelineEventRow(
                            toDate(detail.getEntryDate()), "audit",
                            auditActionTitle(detail), null, detail.getNote()));
                }
            }
        } catch (Exception e) {
            // the audit trail is a nice-to-have source; never break the tab
            log.warn("Could not load the BPM audit trail of process instance {}: {}",
                    instance.getId(), e.getMessage());
        }

        // 3. task milestones (engine history, chronological facts)
        List<HistoricTaskInstance> historicTasks = historyService
                .createHistoricTaskInstanceQuery()
                .processInstanceId(instance.getId())
                .orderByHistoricTaskInstanceStartTime()
                .asc()
                .list();
        for (HistoricTaskInstance task : historicTasks) {
            if (task.getStartTime() != null) {
                events.add(new TimelineEventRow(task.getStartTime(), "taskCreated",
                        task.getName(), null, task.getId()));
            }
            if (task.getEndTime() != null) {
                events.add(new TimelineEventRow(task.getEndTime(), "taskCompleted",
                        task.getName(), task.getAssignee(), task.getId()));
            }
        }

        events.sort(Comparator.comparing(TimelineEventRow::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return events;
    }

    private static String displayNameOf(HistoricProcessInstance instance) {
        if (instance.getName() != null && !instance.getName().isBlank()) {
            return instance.getName();
        }
        return instance.getProcessDefinitionName() != null
                ? instance.getProcessDefinitionName()
                : instance.getProcessDefinitionKey();
    }

    /** Best-effort title of one audit row from the existing lookup. */
    private static String auditActionTitle(BpmAuditLogDtl detail) {
        try {
            if (detail.getActionCode() != null) {
                BpmAuditAction action = BpmAuditAction.fromCode(detail.getActionCode());
                if (action != null) {
                    String description = action.defaultDescription();
                    if (description != null && !description.isBlank()) {
                        return description;
                    }
                }
            }
        } catch (Exception ignored) {
            // unknown/department-specific code - fall through to the note
        }
        return detail.getNote() != null && !detail.getNote().isBlank()
                ? firstSentence(detail.getNote()) : "Audit event";
    }

    private static String firstSentence(String text) {
        int cut = text.indexOf('.');
        return cut > 0 && cut < 80 ? text.substring(0, cut) : text;
    }

    private static Date toDate(java.time.LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Batch-resolves Flowable user ids to {@code username (id)} display
     * strings through {@code FLOWABLE_USERS_VW}. A lookup failure never
     * breaks a listing - an empty map is returned and ids are shown raw.
     */
    private Map<String, String> resolveUserDisplays(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> displayById = new HashMap<>();
        try {
            for (ExternalUser user : groupService.findUsersByIds(userIds)) {
                if (user != null && user.getId() != null && user.getUsername() != null) {
                    displayById.putIfAbsent(user.getId(),
                            user.getUsername() + " (" + user.getId() + ")");
                }
            }
        } catch (Exception e) {
            log.warn("Could not resolve {} user id(s) from FLOWABLE_USERS_VW: {}",
                    userIds.size(), e.getMessage());
            return Collections.emptyMap();
        }
        return displayById;
    }

    private static int normalizeSize(int pageSize) {
        return pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }
}