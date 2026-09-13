package com.example.approval.backing;

import com.example.approval.delegation.TaskDelegationService;
import com.example.approval.delegation.ProcessDefinitionOption;
import com.example.approval.delegation.ProcessInstanceRow;
import com.example.approval.delegation.TaskRow;
import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.PageResult;
import com.example.approval.service.ExternalGroupService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.primefaces.PrimeFaces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Backing bean for the admin <b>Task Delegation</b> page
 * ({@code /task-delegation.xhtml}).
 *
 * <p>Implements the page's drill-down workflow:</p>
 * <pre>
 * Select Process -> Process Instances -> Select Instance
 *      -> Active Tasks -> Select Task -> Select User -> Assign
 * </pre>
 *
 * <p><b>Data flow:</b> the process dropdown, the two server-side paginated
 * tables (process instances / active tasks) and the reassignment come from
 * {@link TaskDelegationService} (Flowable Query API +
 * {@code TaskService.setAssignee}); the user-selection dialog reuses the
 * existing {@link ExternalGroupService#findUsers} server-side search over
 * {@code FLOWABLE_USERS_VW} (same as the Users Management page).</p>
 *
 * <p><b>Security:</b> page body rendered only for group admins
 * ({@code ADM}); every action method re-checks the role through the service
 * ({@code SecurityException} otherwise) so a direct URL / crafted request
 * cannot bypass the menu hiding.</p>
 */
@Component("taskDelegationBean")
@Scope("view")
public class TaskDelegationBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    @Autowired
    private TaskDelegationService delegationService;

    @Autowired
    private ExternalGroupService groupService;

    @Autowired
    private UserLoginBean loginBean;

    /** Whether the logged-in user is allowed to use this page (ADM group). */
    private boolean groupAdmin;

    // ------------------------------------------------------------------
    // Step 1: process dropdown
    // ------------------------------------------------------------------

    private List<ProcessDefinitionOption> processes = Collections.emptyList();

    /** Selected process definition key (kept internally in the dropdown). */
    private String selectedProcessKey;

    // ------------------------------------------------------------------
    // Step 2: process instances of the selected process
    // ------------------------------------------------------------------

    private String instanceIdFilter;
    private Date instanceCreatedOn;      // datePicker (whole-day filter)
    private int instancePage = 1;
    private int instancePageSize = TaskDelegationService.DEFAULT_PAGE_SIZE;
    private long instanceTotalCount;
    private int instanceTotalPages;
    private List<ProcessInstanceRow> instances = Collections.emptyList();

    /** The process instance chosen with the "Select" action of a row. */
    private ProcessInstanceRow selectedInstance;

    // ------------------------------------------------------------------
    // Step 3: active tasks of the selected process instance
    // ------------------------------------------------------------------

    private String taskIdFilter;
    private Date taskCreatedOn;          // datePicker (whole-day filter)
    private int taskPage = 1;
    private int taskPageSize = TaskDelegationService.DEFAULT_PAGE_SIZE;
    private long taskTotalCount;
    private int taskTotalPages;
    private List<TaskRow> tasks = Collections.emptyList();

    // ------------------------------------------------------------------
    // Step 4: delegation dialog (task info + paginated user table)
    // ------------------------------------------------------------------

    /** Task chosen with the "Delegate" action of an active-tasks row. */
    private TaskRow selectedTask;
    private boolean dialogVisible;
    private String userSearchTerm;
    private int userPage = 1;
    private int userPageSize = ExternalGroupService.DEFAULT_PAGE_SIZE;
    private long userTotalCount;
    private int userTotalPages;
    private List<ExternalUser> users = Collections.emptyList();

    /** The user row selected in the dialog's table. */
    private ExternalUser selectedUser;

    @PostConstruct
    public void init() {
        groupAdmin = loginBean.isLoggedIn()
                && groupService.isGroupAdmin(currentFlowableUserId());
        if (groupAdmin) {
            loadProcesses();
        }
    }

    // ------------------------------------------------------------------
    // Step 1: process selection
    // ------------------------------------------------------------------

    private void loadProcesses() {
        try {
            processes = delegationService.findDeployedProcesses(currentFlowableUserId());
        } catch (Exception e) {
            processes = Collections.emptyList();
            addMessage(FacesMessage.SEVERITY_ERROR,
                    getLabel("td.error.loadProcesses"));
        }
    }

    /**
     * Dropdown change: load the first page of the selected process's
     * instances. Nothing is loaded before a process is selected.
     */
    public void onProcessChange() {
        selectedInstance = null;
        tasks = Collections.emptyList();
        taskTotalCount = 0;
        taskTotalPages = 0;
        taskPage = 1;
        clearTaskFilters();
        if (selectedProcessKey != null && !selectedProcessKey.isBlank()) {
            instancePage = 1;
            loadInstances();
        } else {
            instances = Collections.emptyList();
            instanceTotalCount = 0;
            instanceTotalPages = 0;
        }
    }

    public String getSelectedProcessLabel() {
        return processes.stream()
                .filter(p -> Objects.equals(p.getKey(), selectedProcessKey))
                .map(ProcessDefinitionOption::getLabel)
                .findFirst()
                .orElse(selectedProcessKey);
    }

    // ------------------------------------------------------------------
    // Step 2: process instances (server-side pagination + filters)
    // ------------------------------------------------------------------

    public void searchInstances() {
        instancePage = 1;
        loadInstances();
    }

    public void resetInstanceFilters() {
        instanceIdFilter = null;
        instanceCreatedOn = null;
        instancePage = 1;
        loadInstances();
    }

    public void firstInstancePage() {
        instancePage = 1;
        loadInstances();
    }

    public void previousInstancePage() {
        if (instancePage > 1) {
            instancePage--;
            loadInstances();
        }
    }

    public void nextInstancePage() {
        if (instancePage < instanceTotalPages) {
            instancePage++;
            loadInstances();
        }
    }

    public void lastInstancePage() {
        instancePage = instanceTotalPages;
        loadInstances();
    }

    private void loadInstances() {
        try {
            PageResult<ProcessInstanceRow> result = delegationService.findProcessInstances(
                    currentFlowableUserId(), selectedProcessKey, instancePage,
                    instancePageSize, instanceIdFilter, instanceCreatedOn);
            instances = result.getRows();
            instanceTotalCount = result.getTotalRows();
            instanceTotalPages = result.getLastPage();
            instancePage = result.getPageNumber(); // service clamps pages
        } catch (SecurityException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("common.notAuthorized.title"));
        } catch (Exception e) {
            instances = Collections.emptyList();
            instanceTotalCount = 0;
            instanceTotalPages = 0;
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("td.error.loadInstances"));
        }
    }

    public boolean isHasInstances() {
        return instances != null && !instances.isEmpty();
    }

    public boolean isInstanceHasPrevious() {
        return instancePage > 1;
    }

    public boolean isInstanceHasNext() {
        return instancePage < instanceTotalPages;
    }

    public String getInstanceRangeLabel() {
        return rangeLabel(instancePage, instancePageSize, instanceTotalCount,
                instanceTotalPages, isHasInstances());
    }

    // ------------------------------------------------------------------
    // Step 3: select instance -> active tasks
    // ------------------------------------------------------------------

    /** Row action: select one process instance and load its active tasks. */
    public void selectInstance(String processInstanceId) {
        selectedInstance = instances.stream()
                .filter(i -> i.getId().equals(processInstanceId))
                .findFirst()
                .orElse(null);
        if (selectedInstance != null) {
            taskPage = 1;
            clearTaskFilters();
            loadTasks();
        }
    }

    private void clearTaskFilters() {
        taskIdFilter = null;
        taskCreatedOn = null;
    }

    public String getInstanceDisplayName(ProcessInstanceRow instance) {
        if (instance == null) {
            return "";
        }
        return instance.getName() != null && !instance.getName().isBlank()
                ? instance.getId() + " - " + instance.getName()
                : instance.getId();
    }

    // ------------------------------------------------------------------
    // Step 4: active tasks (server-side pagination + filters)
    // ------------------------------------------------------------------

    public void searchTasks() {
        taskPage = 1;
        loadTasks();
    }

    public void resetTaskFilters() {
        clearTaskFilters();
        taskPage = 1;
        loadTasks();
    }

    public void firstTaskPage() {
        taskPage = 1;
        loadTasks();
    }

    public void previousTaskPage() {
        if (taskPage > 1) {
            taskPage--;
            loadTasks();
        }
    }

    public void nextTaskPage() {
        if (taskPage < taskTotalPages) {
            taskPage++;
            loadTasks();
        }
    }

    public void lastTaskPage() {
        taskPage = taskTotalPages;
        loadTasks();
    }

    private void loadTasks() {
        if (selectedInstance == null) {
            return;
        }
        try {
            PageResult<TaskRow> result = delegationService.findActiveTasks(
                    currentFlowableUserId(), selectedInstance.getId(), taskPage,
                    taskPageSize, taskIdFilter, taskCreatedOn);
            tasks = result.getRows();
            taskTotalCount = result.getTotalRows();
            taskTotalPages = result.getLastPage();
            taskPage = result.getPageNumber();
        } catch (SecurityException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("common.notAuthorized.title"));
        } catch (Exception e) {
            tasks = Collections.emptyList();
            taskTotalCount = 0;
            taskTotalPages = 0;
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("td.error.loadTasks"));
        }
    }

    public boolean isHasTasks() {
        return tasks != null && !tasks.isEmpty();
    }

    public boolean isTaskHasPrevious() {
        return taskPage > 1;
    }

    public boolean isTaskHasNext() {
        return taskPage < taskTotalPages;
    }

    public String getTaskRangeLabel() {
        return rangeLabel(taskPage, taskPageSize, taskTotalCount, taskTotalPages,
                isHasTasks());
    }

    // ------------------------------------------------------------------
    // Step 5: delegation dialog
    // ------------------------------------------------------------------

    /** Row action: open the delegation dialog for one active task. */
    public void openDelegationDialog(String taskId) {
        selectedTask = tasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElse(null);
        selectedUser = null;
        userSearchTerm = null;
        userPage = 1;
        dialogVisible = selectedTask != null;
        if (dialogVisible) {
            loadUsers();
        }
    }

    public void closeDelegationDialog() {
        dialogVisible = false;
        selectedTask = null;
        selectedUser = null;
    }

    public void searchUsers() {
        userPage = 1;
        loadUsers();
    }

    public void firstUserPage() {
        userPage = 1;
        loadUsers();
    }

    public void previousUserPage() {
        if (userPage > 1) {
            userPage--;
            loadUsers();
        }
    }

    public void nextPageUsers() {
        if (userPage < userTotalPages) {
            userPage++;
            loadUsers();
        }
    }

    public void lastUserPage() {
        userPage = userTotalPages;
        loadUsers();
    }

    private void loadUsers() {
        try {
            PageResult<ExternalUser> result = groupService.findUsers(
                    userPage, userPageSize, userSearchTerm);
            users = result.getRows();
            userTotalCount = result.getTotalRows();
            userTotalPages = result.getLastPage();
            userPage = result.getPageNumber();
        } catch (Exception e) {
            users = Collections.emptyList();
            userTotalCount = 0;
            userTotalPages = 0;
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("td.error.loadUsers"));
        }
    }

    public boolean isHasUsers() {
        return users != null && !users.isEmpty();
    }

    public boolean isUserHasPrevious() {
        return userPage > 1;
    }

    public boolean isUserHasNext() {
        return userPage < userTotalPages;
    }

    public String getUserRangeLabel() {
        return rangeLabel(userPage, userPageSize, userTotalCount, userTotalPages,
                isHasUsers());
    }

    // ------------------------------------------------------------------
    // Step 6: assign
    // ------------------------------------------------------------------

    /**
     * Assign button of the dialog: validates task + user selection,
     * delegates through {@code TaskService.setAssignee} (service layer),
     * closes the dialog on success and refreshes the active-tasks table so
     * the new assignee is visible without a manual page reload.
     */
    public void assign() {
        if (!groupAdmin) {
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("td.error.notAuthorized"));
            return;
        }
        if (selectedTask == null) {
            addMessage(FacesMessage.SEVERITY_WARN, getLabel("td.error.noTaskSelected"));
            return;
        }
        if (selectedUser == null) {
            addMessage(FacesMessage.SEVERITY_WARN, getLabel("td.error.noUserSelected"));
            return;
        }
        try {
            TaskDelegationService.DelegationResult result = delegationService.delegateTask(
                    currentFlowableUserId(), selectedTask.getId(), selectedUser.getId());
            dialogVisible = false;
            selectedTask = null;
            selectedUser = null;
            taskPage = 1; // start over at page 1 of the refreshed table
            loadTasks();
            hideDialogClientSide();
            addMessage(FacesMessage.SEVERITY_INFO,
                    getLabel("td.assign.success", result.previousAssignee(),
                            result.newAssignee()));
        } catch (SecurityException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("td.error.notAuthorized"));
        } catch (IllegalArgumentException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("td.error.validation"));
        } catch (Exception e) {
            addMessage(FacesMessage.SEVERITY_ERROR,
                    getLabel("td.error.assignFailed", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Closes the delegation dialog client-side after a successful assign
     * (the bean-side flags alone do not hide an open PrimeFaces dialog).
     */
    private void hideDialogClientSide() {
        try {
            PrimeFaces.current().executeScript(
                    "PF('delegationDialogWidget').hide()");
        } catch (Exception ignored) {
            // no Faces/PrimeFaces context (e.g. unit test) - nothing to close
        }
    }

    private String currentFlowableUserId() {
        return loginBean.isLoggedIn() ? loginBean.getCurrentUser().getId() : null;
    }

    /** Pager footer label, e.g. "1-10 of 42 (page 1 of 5)". */
    private static String rangeLabel(int page, int pageSize, long totalCount,
                                     int totalPages, boolean hasRows) {
        if (!hasRows) {
            return "0 of 0";
        }
        long first = (long) (page - 1) * pageSize + 1;
        long last = Math.min((long) page * pageSize, totalCount);
        return String.format("%d-%d of %d (page %d of %d)", first, last, totalCount,
                page, totalPages);
    }

    private void addMessage(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }

    // Getters / Setters -------------------------------------------------

    public boolean isGroupAdmin() {
        return groupAdmin;
    }

    public List<ProcessDefinitionOption> getProcesses() {
        return processes;
    }

    public String getSelectedProcessKey() {
        return selectedProcessKey;
    }

    public void setSelectedProcessKey(String selectedProcessKey) {
        this.selectedProcessKey = selectedProcessKey;
    }

    public String getInstanceIdFilter() {
        return instanceIdFilter;
    }

    public void setInstanceIdFilter(String instanceIdFilter) {
        this.instanceIdFilter = instanceIdFilter;
    }

    public Date getInstanceCreatedOn() {
        return instanceCreatedOn;
    }

    public void setInstanceCreatedOn(Date instanceCreatedOn) {
        this.instanceCreatedOn = instanceCreatedOn;
    }

    public List<ProcessInstanceRow> getInstances() {
        return instances;
    }

    public ProcessInstanceRow getSelectedInstance() {
        return selectedInstance;
    }

    public String getTaskIdFilter() {
        return taskIdFilter;
    }

    public void setTaskIdFilter(String taskIdFilter) {
        this.taskIdFilter = taskIdFilter;
    }

    public Date getTaskCreatedOn() {
        return taskCreatedOn;
    }

    public void setTaskCreatedOn(Date taskCreatedOn) {
        this.taskCreatedOn = taskCreatedOn;
    }

    public List<TaskRow> getTasks() {
        return tasks;
    }

    public TaskRow getSelectedTask() {
        return selectedTask;
    }

    public boolean isDialogVisible() {
        return dialogVisible;
    }

    public String getUserSearchTerm() {
        return userSearchTerm;
    }

    public void setUserSearchTerm(String userSearchTerm) {
        this.userSearchTerm = userSearchTerm;
    }

    public List<ExternalUser> getUsers() {
        return users;
    }

    public ExternalUser getSelectedUser() {
        return selectedUser;
    }

    public void setSelectedUser(ExternalUser selectedUser) {
        this.selectedUser = selectedUser;
    }
}