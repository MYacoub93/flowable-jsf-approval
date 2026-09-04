package com.example.approval.clearance.backing;

import com.example.approval.backing.BaseBackingBean;
import com.example.approval.backing.UserLoginBean;
import com.example.approval.clearance.model.DepartmentDecision;
import com.example.approval.clearance.service.ClearanceService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.example.approval.clearance.ClearanceConstants.*;

/**
 * Backing bean for {@code clearance-task.xhtml} - the single JSF form that
 * serves every human task of the Clearance Letter process:
 *
 * <ul>
 *   <li>department / Finance / Admission approval (approve + reject with a
 *       mandatory rejection reason),</li>
 *   <li>the initiator's amendment form after a rejection, and</li>
 *   <li>the standalone result / FYI acknowledgement tasks created by
 *       {@code ClearanceProcessHandler.completeClearance}.</li>
 * </ul>
 *
 * The task is located by the {@code taskId} request parameter (or flash
 * attribute put there by {@code DashboardBean.openTask}); the routing to the
 * matching form section happens via {@link #isApprovalTask()},
 * {@link #isAmendmentTask()} and {@link #isNotificationTask()}.
 *
 * Pure Spring bean (like the other backing beans): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works. Do NOT add CDI
 * annotations (@Named/@RequestScoped) — Weld would then create the instance and
 * skip Spring injection, leaving @Autowired fields null.
 */
@Component("clearanceTaskBean")
@Scope("view")
public class ClearanceTaskBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private ClearanceService clearanceService;

    private String taskId;
    private Task task;
    private Map<String, Object> variables;

    // form fields
    private String comments;
    private String notes;
    private String studentFullName;
    private String studentId;
    private String program;

    private List<DecisionView> decisionHistory;

    @PostConstruct
    public void init() {
        // taskId can come from the request parameter or the flash scope
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        taskId = params.get("taskId");
        if (taskId == null) {
            Object flashTaskId = FacesContext.getCurrentInstance()
                    .getExternalContext().getFlash().get("taskId");
            if (flashTaskId != null) {
                taskId = flashTaskId.toString();
            }
        }
        if (taskId != null && loginBean.isLoggedIn()) {
            loadTask();
        }
    }

    private void loadTask() {
        task = clearanceService.getTaskById(taskId);
        if (task == null) {
            return;
        }
        // process variables for BPMN tasks, task-local variables for the
        // standalone result / FYI tasks (they carry "processInstanceId")
        variables = clearanceService.getTaskVariables(taskId);

        // pre-fill the amendment form from the current request data
        studentFullName = str(variables.get(VAR_STUDENT_FULL_NAME));
        studentId = str(variables.get(VAR_STUDENT_ID));
        program = str(variables.get(VAR_PROGRAM));
        notes = str(variables.get(VAR_NOTES));

        decisionHistory = buildDecisionHistory(variables.get(VAR_DEPARTMENT_DECISIONS));
    }

    // ------------------------------------------------------------------
    // Task type routing (drives the rendered form sections)
    // ------------------------------------------------------------------

    /** Department multi-instance, Finance and Admission approval tasks. */
    public boolean isApprovalTask() {
        if (task == null) {
            return false;
        }
        String key = task.getTaskDefinitionKey();
        return TASK_DEPARTMENT_APPROVAL.equals(key)
                || TASK_FINANCE_APPROVAL.equals(key)
                || TASK_ADMISSION_APPROVAL.equals(key);
    }

    /** Initiator's "Amend Clearance Request" task after a rejection. */
    public boolean isAmendmentTask() {
        return task != null && TASK_AMEND.equals(task.getTaskDefinitionKey());
    }

    /** Standalone result / FYI tasks (no task definition key, category set). */
    public boolean isNotificationTask() {
        if (task == null) {
            return false;
        }
        return CATEGORY_FYI.equals(task.getCategory())
                || CATEGORY_RESULT.equals(task.getCategory());
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    public String approve() {
        return completeDecision(DECISION_APPROVE);
    }

    public String reject() {
        if (comments == null || comments.isBlank()) {
            addError("A rejection reason is required");
            return null;
        }
        return completeDecision(DECISION_REJECT);
    }

    private String completeDecision(String decision) {
        if (!loginBean.isLoggedIn() || task == null) {
            addError("Invalid state");
            return null;
        }
        try {
            clearanceService.completeApprovalTask(taskId, decision, comments,
                    loginBean.getCurrentUser().getId());
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            DECISION_APPROVE.equals(decision)
                                    ? "Request approved"
                                    : "Request rejected", null));
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            addError("Failed to complete task: " + e.getMessage());
            return null;
        }
    }

    public String submitAmendment() {
        if (!loginBean.isLoggedIn() || task == null) {
            addError("Invalid state");
            return null;
        }
        if (studentFullName == null || studentFullName.isBlank()) {
            addError("Student full name is required");
            return null;
        }
        if (studentId == null || studentId.isBlank()) {
            addError("Student ID is required");
            return null;
        }
        try {
            clearanceService.completeAmendment(taskId, studentFullName, studentId,
                    program, notes, comments, loginBean.getCurrentUser().getId());
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Amendment submitted - the request is circulating again", null));
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            addError("Failed to submit amendment: " + e.getMessage());
            return null;
        }
    }

    public String acknowledge() {
        if (!loginBean.isLoggedIn() || task == null) {
            addError("Invalid state");
            return null;
        }
        try {
            clearanceService.acknowledgeTask(taskId, loginBean.getCurrentUser().getId());
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Notification acknowledged", null));
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            addError("Failed to acknowledge task: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Read helpers for the form
    // ------------------------------------------------------------------

    public String getInitiator() {
        return variables != null ? str(variables.get(VAR_INITIATOR)) : null;
    }

    public Integer getApprovalRound() {
        Object value = variables != null ? variables.get(VAR_APPROVAL_ROUND) : null;
        return value instanceof Number n ? n.intValue() : null;
    }

    public String getLastRejectedStage() {
        return variables != null ? str(variables.get(VAR_LAST_REJECTED_STAGE)) : null;
    }

    public String getLastRejectedDepartment() {
        return variables != null ? str(variables.get(VAR_LAST_REJECTED_DEPARTMENT)) : null;
    }

    public String getLastRejectionComment() {
        return variables != null ? str(variables.get(VAR_LAST_REJECTION_COMMENT)) : null;
    }

    /**
     * Decisions of the current approval round as display rows: decision label
     * "Approved"/"Rejected" and {@link LocalDateTime} date for
     * {@code <f:convertDateTime type="localDateTime"/>}.
     */
    public List<DecisionView> getDecisionHistory() {
        return decisionHistory != null ? decisionHistory : List.of();
    }

    private List<DecisionView> buildDecisionHistory(Object decisionsVariable) {
        List<DecisionView> rows = new ArrayList<>();
        if (decisionsVariable instanceof Map<?, ?> decisions) {
            for (Object value : decisions.values()) {
                if (value instanceof DepartmentDecision d) {
                    rows.add(new DecisionView(d));
                }
            }
        }
        rows.sort(Comparator.comparing(DecisionView::getDecisionDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // Getters / Setters -----------------------------------------------------

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Task getTask() {
        return task;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStudentFullName() {
        return studentFullName;
    }

    public void setStudentFullName(String studentFullName) {
        this.studentFullName = studentFullName;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    // ------------------------------------------------------------------
    // View model for the decisions table
    // ------------------------------------------------------------------

    /**
     * Display row for the "Department decisions (current round)" table.
     * Adapts the persisted {@link DepartmentDecision} (lowercase
     * "approve"/"reject", {@code completedAt}) to the labels and the
     * {@code decisionDate} property used by {@code clearance-task.xhtml}.
     */
    public static class DecisionView implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String department;
        private final String decision;
        private final String completedBy;
        private final String comment;
        private final LocalDateTime decisionDate;

        public DecisionView(DepartmentDecision d) {
            this.department = d.getDepartment();
            this.decision = d.isApproved() ? "Approved" : "Rejected";
            this.completedBy = d.getCompletedBy();
            this.comment = d.getComment();
            this.decisionDate = d.getCompletedAt();
        }

        public String getDepartment() {
            return department;
        }

        public String getDecision() {
            return decision;
        }

        public String getCompletedBy() {
            return completedBy;
        }

        public String getComment() {
            return comment;
        }

        public LocalDateTime getDecisionDate() {
            return decisionDate;
        }
    }
}