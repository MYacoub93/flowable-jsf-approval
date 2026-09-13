package com.example.approval.delegation;

import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.PageResult;
import com.example.approval.service.ExternalGroupService;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin <b>Task Delegation</b> service (Administration &rarr; Task
 * Delegation, {@code /task-delegation.xhtml}): select a deployed process,
 * browse its running process instances, list the active tasks of one
 * instance and reassign a selected task to another Flowable user.
 *
 * <p><b>Data access follows the two existing conventions of this
 * application:</b></p>
 * <ul>
 *   <li>Process definitions, process instances and tasks are read through
 *       Flowable's own Query API ({@code RepositoryService},
 *       {@code RuntimeService}, {@code TaskService}) with
 *       {@code listPage()/count()} server-side pagination and filtering -
 *       the same pattern as {@code ApprovalService},
 *       {@code ProcessDeploymentService} and {@code ClearDataService}. The
 *       Flowable engine tables are never queried with SQL directly (and
 *       never written to directly); the reassignment itself goes through
 *       {@link TaskService#setAssignee(String, String)}.</li>
 *   <li>Assignable users come from the existing MyBatis queries against the
 *       external Oracle view {@code FLOWABLE_USERS_VW}
 *       ({@code ExternalGroupMapper.findUsersPage/countUsers}) through
 *       {@link ExternalGroupService#findUsers} - server-side pagination
 *       and search, exactly like the admin "Users Management" page. No
 *       user is ever hard-coded.</li>
 * </ul>
 *
 * <p><b>Security:</b> every public method re-checks that the acting user is
 * a group admin ({@link ExternalGroupService#isGroupAdmin}, group
 * {@code ADM}) - the hidden menu entry is never the security boundary (same
 * pattern as {@code ProcessDeploymentService}).</p>
 *
 * <p><b>Audit:</b> each successful delegation is recorded through the
 * existing BPM audit subsystem
 * ({@link BpmAuditService#logProcessAction} with
 * {@link BpmAuditConstants#ACTION_TASK_DELEGATED}), carrying process
 * instance id, task id, previous assignee, new assignee, acting admin and
 * delegation date/time. Candidate groups are deliberately left untouched -
 * this feature delegates/reassigns, it never changes the task's candidate
 * groups.</p>
 */
@Service
public class TaskDelegationService {

    private static final Logger log = LoggerFactory.getLogger(TaskDelegationService.class);

    /** Default rows per page (same default as the other admin listings). */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private static final DateTimeFormatter AUDIT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ExternalGroupService groupService;
    private final BpmAuditService auditService;

    public TaskDelegationService(RepositoryService repositoryService,
                                 RuntimeService runtimeService,
                                 TaskService taskService,
                                 ExternalGroupService groupService,
                                 BpmAuditService auditService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.groupService = groupService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Row DTOs - the EL-exposed rows are top-level immutable JavaBean
    // classes (ProcessDefinitionOption / ProcessInstanceRow / TaskRow);
    // DelegationResult below is only consumed from Java code.
    // ------------------------------------------------------------------

    /** Outcome of a successful delegation. */
    public record DelegationResult(String taskId, String processInstanceId,
                                   String previousAssignee, String newAssignee) {
    }

    // ------------------------------------------------------------------
    // Security
    // ------------------------------------------------------------------

    /** Whether the user may open the Task Delegation page (ADM group). */
    public boolean isDelegationAllowed(String flowableUserId) {
        return groupService.isGroupAdmin(flowableUserId);
    }

    private void assertDelegationAllowed(String actorUserId) {
        if (!groupService.isGroupAdmin(actorUserId)) {
            log.warn("Task delegation denied for user {} (requires group admin role)",
                    actorUserId);
            throw new SecurityException("User " + actorUserId
                    + " is not allowed to delegate tasks (requires group admin role)");
        }
    }

    // ------------------------------------------------------------------
    // Process definitions (dropdown)
    // ------------------------------------------------------------------

    /**
     * The deployed processes for the selection dropdown - latest version of
     * every process definition key, ordered by key (admin only).
     */
    public List<ProcessDefinitionOption> findDeployedProcesses(String actorUserId) {
        assertDelegationAllowed(actorUserId);
        List<ProcessDefinition> definitions =
                repositoryService.createProcessDefinitionQuery()
                        .latestVersion()
                        .orderByProcessDefinitionKey()
                        .asc()
                        .list();
        return definitions.stream()
                .map(d -> new ProcessDefinitionOption(d.getId(), d.getKey(), d.getName()))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Process instances of the selected process
    // ------------------------------------------------------------------

    /**
     * One page of the <b>running</b> process instances of the selected
     * process definition key, optionally filtered by (partial-free exact)
     * process instance id and/or by the creation date (a whole calendar
     * day), newest first.
     *
     * @param createdOn nullable creation-date filter; when set only
     *                  instances started within that day are returned
     *                  (00:00:00.000 - 23:59:59.999 of the JVM default
     *                  time zone)
     */
    public PageResult<ProcessInstanceRow> findProcessInstances(String actorUserId,
                                                               String processDefinitionKey,
                                                               int pageNumber,
                                                               int pageSize,
                                                               String instanceIdFilter,
                                                               Date createdOn) {
        assertDelegationAllowed(actorUserId);
        if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
            return PageResult.empty(Math.max(pageNumber, 1), normalizeSize(pageSize));
        }
        ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey.trim());
        if (instanceIdFilter != null && !instanceIdFilter.isBlank()) {
            query = query.processInstanceId(instanceIdFilter.trim());
        }
        if (createdOn != null) {
            query = query.startedAfter(startOfDay(createdOn))
                    .startedBefore(endOfDay(createdOn));
        }
        final ProcessInstanceQuery filteredQuery = query;
        return page(query.count(), Math.max(pageNumber, 1), normalizeSize(pageSize),
                (offset, size) -> filteredQuery.orderByStartTime()
                        .desc()
                        .listPage((int) offset, size),
                this::toInstanceRow);
    }

    private ProcessInstanceRow toInstanceRow(ProcessInstance instance) {
        return new ProcessInstanceRow(instance.getId(), instance.getName(),
                instance.getStartUserId(), instance.getStartTime());
    }

    // ------------------------------------------------------------------
    // Active tasks of the selected process instance
    // ------------------------------------------------------------------

    /**
     * One page of the <b>active</b> ({@code TaskQuery.active()}) tasks of
     * the selected process instance - completed / cancelled / suspended
     * tasks are never returned - optionally filtered by task id and/or by
     * the task creation date (whole calendar day), newest first. Candidate
     * groups are resolved per task through the existing identity-link API.
     */
    public PageResult<TaskRow> findActiveTasks(String actorUserId,
                                               String processInstanceId,
                                               int pageNumber,
                                               int pageSize,
                                               String taskIdFilter,
                                               Date createdOn) {
        assertDelegationAllowed(actorUserId);
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return PageResult.empty(Math.max(pageNumber, 1), normalizeSize(pageSize));
        }
        TaskQuery query = taskService.createTaskQuery()
                .processInstanceId(processInstanceId.trim())
                .active();
        if (taskIdFilter != null && !taskIdFilter.isBlank()) {
            query = query.taskId(taskIdFilter.trim());
        }
        if (createdOn != null) {
            query = query.taskCreatedAfter(startOfDay(createdOn))
                    .taskCreatedBefore(endOfDay(createdOn));
        }
        final TaskQuery filteredQuery = query;
        PageResult<TaskRow> result = page(query.count(),
                Math.max(pageNumber, 1), normalizeSize(pageSize),
                (offset, size) -> filteredQuery.orderByTaskCreateTime()
                        .desc()
                        .listPage((int) offset, size),
                this::toTaskRow);
        return new PageResult<>(withAssigneeUsernames(result.getRows()),
                result.getTotalRows(), result.getPageNumber(),
                result.getPageSize());
    }

    /**
     * Resolves the assignee ids of the fetched page to usernames through
     * {@code FLOWABLE_USERS_VW} ({@code ExternalGroupService.findUsersByIds})
     * - one batch query per page instead of one query per row, so the table
     * can show {@code username (id)}. A lookup failure never breaks the
     * listing: the bare id is shown instead.
     */
    private List<TaskRow> withAssigneeUsernames(List<TaskRow> rows) {
        List<String> assigneeIds = rows.stream()
                .map(TaskRow::getAssignee)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (assigneeIds.isEmpty()) {
            return rows;
        }
        Map<String, String> usernamesById = new HashMap<>();
        try {
            for (ExternalUser user : groupService.findUsersByIds(assigneeIds)) {
                if (user != null && user.getId() != null
                        && user.getUsername() != null) {
                    usernamesById.putIfAbsent(user.getId(), user.getUsername());
                }
            }
        } catch (Exception e) {
            log.warn("Could not resolve {} assignee username(s) from "
                    + "FLOWABLE_USERS_VW: {}", assigneeIds.size(), e.getMessage());
            return rows;
        }
        if (usernamesById.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(row -> usernamesById.containsKey(row.getAssignee())
                        ? row.withAssigneeUsername(usernamesById.get(row.getAssignee()))
                        : row)
                .collect(Collectors.toList());
    }

    private TaskRow toTaskRow(Task task) {
        return new TaskRow(task.getId(), task.getName(), task.getAssignee(),
                candidateGroupsOf(task.getId()), task.getCreateTime(),
                task.getDueDate(), task.getTaskDefinitionKey());
    }

    /** Comma-joined candidate group ids of one task (never null). */
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
    // Delegation / reassignment
    // ------------------------------------------------------------------

    /**
     * Reassigns an active task to another Flowable user
     * ({@link TaskService#setAssignee(String, String)}): the previous
     * assignee is replaced, candidate groups stay exactly as they are, and
     * the delegation is recorded in the existing BPM audit trail
     * (previous assignee, new assignee, acting admin, date/time).
     *
     * @throws SecurityException            when the actor is not a group admin
     * @throws IllegalArgumentException     when taskId / newAssigneeUserId is blank
     * @throws FlowableObjectNotFoundException when the task does not exist or
     *                                      is no longer active
     */
    public DelegationResult delegateTask(String actorUserId, String taskId,
                                         String newAssigneeUserId) {
        assertDelegationAllowed(actorUserId);
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (newAssigneeUserId == null || newAssigneeUserId.isBlank()) {
            throw new IllegalArgumentException("newAssigneeUserId must not be blank");
        }

        // Only an ACTIVE task can be delegated; .active() rules out
        // completed, cancelled and suspended tasks.
        Task task = taskService.createTaskQuery()
                .taskId(taskId.trim())
                .active()
                .singleResult();
        if (task == null) {
            throw new FlowableObjectNotFoundException(
                    "Task " + taskId + " not found or no longer active");
        }

        String previousAssignee = task.getAssignee();
        String newAssignee = newAssigneeUserId.trim();

        // The one and only engine mutation - through the official API,
        // never by writing Flowable tables directly.
        taskService.setAssignee(task.getId(), newAssignee);
        log.info("Task delegation: admin {} reassigned task {} ('{}') of process "
                        + "instance {} from assignee '{}' to '{}'", actorUserId,
                task.getId(), task.getName(), task.getProcessInstanceId(),
                previousAssignee, newAssignee);

        auditDelegation(task, previousAssignee, newAssignee, actorUserId);

        return new DelegationResult(task.getId(), task.getProcessInstanceId(),
                previousAssignee, newAssignee);
    }

    /**
     * Records the delegation in the existing BPM audit subsystem. An audit
     * failure is logged but never rolls back the (already persisted)
     * assignment - the reassignment is the primary business action.
     */
    private void auditDelegation(Task task, String previousAssignee,
                                 String newAssignee, String actorUserId) {
        try {
            String details = "Task '" + task.getId() + "' ('" + task.getName()
                    + "') delegated from assignee '" + nvl(previousAssignee)
                    + "' to '" + newAssignee + "' by admin '" + actorUserId
                    + "' at " + LocalDateTime.now()
                            .format(AUDIT_TIMESTAMP);
            auditService.logProcessAction(task.getProcessInstanceId(),
                    BpmAuditConstants.ACTION_TASK_DELEGATED,
                    task.getTaskDefinitionKey(), null, actorUserId,
                    previousAssignee, details);
        } catch (Exception e) {
            log.warn("Task delegation audit failed for task {} (assignment itself "
                    + "was successful): {}", task.getId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private interface PageFetcher<R> {
        List<R> fetch(long offset, int size);
    }

    /**
     * Shared count-then-fetch pagination with the same out-of-range page
     * clamping as {@code ExternalGroupService.findUsers}.
     */
    private <F, R> PageResult<R> page(long total, int page, int size,
                                      PageFetcher<F> fetcher,
                                      java.util.function.Function<F, R> mapper) {
        if (total == 0) {
            return PageResult.empty(page, size);
        }
        long offset = (long) (page - 1) * size;
        if (offset >= total) {
            // requested page beyond the end - clamp to last page
            page = (int) ((total + size - 1) / size);
            offset = (long) (page - 1) * size;
        }
        List<R> rows = fetcher.fetch(offset, size).stream()
                .map(mapper)
                .collect(Collectors.toList());
        return new PageResult<>(rows, total, page, size);
    }

    private static int normalizeSize(int pageSize) {
        return pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, 100);
    }

    private static Date startOfDay(Date date) {
        return dayBounds(date, true);
    }

    private static Date endOfDay(Date date) {
        return dayBounds(date, false);
    }

    private static Date dayBounds(Date date, boolean start) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, start ? 0 : 23);
        calendar.set(Calendar.MINUTE, start ? 0 : 59);
        calendar.set(Calendar.SECOND, start ? 0 : 59);
        calendar.set(Calendar.MILLISECOND, start ? 0 : 999);
        return calendar.getTime();
    }

    private static String nvl(String value) {
        return value == null ? "(none)" : value;
    }

    /** Delegation timestamp helper (JVM default zone) for audit notes. */
    static Date nowForAudit() {
        return Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault())
                .toInstant());
    }
}