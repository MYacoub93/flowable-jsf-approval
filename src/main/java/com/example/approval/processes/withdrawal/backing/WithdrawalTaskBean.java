package com.example.approval.processes.withdrawal.backing;

import com.example.approval.backing.BaseBackingBean;
import com.example.approval.backing.UserLoginBean;
import com.example.approval.origin.beans.ReasonsBean;
import com.example.approval.origin.beans.SemesterBean;
import com.example.approval.processes.withdrawal.model.WithdrawalApprovalResult;
import com.example.approval.processes.withdrawal.service.SemesterWithdrawalService;
import com.example.approval.service.CommonService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.withdrawal.SemesterWithdrawalConstants.*;

/**
 * Backing bean for {@code semester-withdrawl-task.xhtml} - the single task
 * view of the Semester Withdrawal process. Covers every task definition of
 * {@code semester-withdrawl-process.bpmn20.xml}:
 *
 * <ul>
 *   <li>{@code regApprovalTask} / {@code financeApprovalTask} - Approve / Reject</li>
 *   <li>{@code parallelApprovalTask} - Approve / Reject (dean, STD_AFF, LIB, HC, Housing)</li>
 *   <li>{@code regFyiTask} - REG final FYI acknowledgement</li>
 *   <li>{@code amendmentTask} - student amendment / resubmission</li>
 *   <li>{@code studentResultTask} - final student success confirmation</li>
 * </ul>
 *
 * <p>Pure Spring bean (like the other backing beans): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works. Do NOT add
 * CDI annotations (@Named/@ViewScoped) - Weld would then create the
 * instance and skip Spring injection.</p>
 */
@Component("withdrawalTaskBean")
@Scope("view")
public class WithdrawalTaskBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(WithdrawalTaskBean.class);

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private SemesterWithdrawalService withdrawalService;

    @Autowired
    private CommonService commonService;

    /** Task id from the request parameter {@code id}. */
    private String taskId;

    /** Loaded task (null when not found / not visible). */
    private Task task;

    /** Decision of the approval actions: approve / reject. */
    private String decision;

    /** Approver comment / rejection reason. */
    private String comment;

    /** Amendment form fields (editable). */
    private String withdrawalReason;
    private String withdrawalReasonDesc;
    private String withdrawalSemester;
    private String withdrawalSemesterDesc;
    private String studentNote;
    private String amendmentNotes;

    @PostConstruct
    public void init() {
        // taskId can come from the request parameter (e-mail deep link uses
        // ?taskId=) or the flash scope (in-app navigation) - same convention
        // as StudentProofTaskBean / ClearanceTaskBean.
        var params = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap();
        taskId = params.get("taskId");
        if (taskId == null) {
            Object flashTaskId = FacesContext.getCurrentInstance()
                    .getExternalContext().getFlash().get("taskId");
            if (flashTaskId != null) {
                taskId = flashTaskId.toString();
            }
        }
        if (loginBean.isLoggedIn() && taskId != null && !taskId.isBlank()) {
            loadTask();
        }
    }

    private void loadTask() {
        task = withdrawalService.getTaskById(taskId);
        if (task == null) {
            return;
        }
        Map<String, Object> vars = withdrawalService.getTaskVariables(taskId);
        withdrawalReason = str(vars.get(VAR_WITHDRAWAL_REASON));
        withdrawalReasonDesc = str(vars.get(VAR_WITHDRAWAL_REASON_DESC));
        withdrawalSemester = str(vars.get(VAR_WITHDRAWAL_SEMESTER));
        withdrawalSemesterDesc = str(vars.get(VAR_WITHDRAWAL_SEMESTER_DESC));
        studentNote = str(vars.get(VAR_STUDENT_NOTE));
    }

    // ------------------------------------------------------------------
    // Task type detection (task definition key)
    // ------------------------------------------------------------------

    public boolean isApprovalTask() {
        return task != null && (TASK_REG_APPROVAL.equals(task.getTaskDefinitionKey())
                || TASK_PARALLEL_APPROVAL.equals(task.getTaskDefinitionKey())
                || TASK_FINANCE_APPROVAL.equals(task.getTaskDefinitionKey()));
    }

    public boolean isRegApprovalTask() {
        return task != null && TASK_REG_APPROVAL.equals(task.getTaskDefinitionKey());
    }

    public boolean isParallelApprovalTask() {
        return task != null && TASK_PARALLEL_APPROVAL.equals(task.getTaskDefinitionKey());
    }

    public boolean isFinanceApprovalTask() {
        return task != null && TASK_FINANCE_APPROVAL.equals(task.getTaskDefinitionKey());
    }

    public boolean isFyiTask() {
        return task != null && TASK_REG_FYI.equals(task.getTaskDefinitionKey());
    }

    public boolean isAmendmentTask() {
        return task != null && TASK_AMENDMENT.equals(task.getTaskDefinitionKey());
    }

    public boolean isResultTask() {
        return task != null && TASK_STUDENT_RESULT.equals(task.getTaskDefinitionKey());
    }

    /** True when the task is assigned to the logged-in user (or still unassigned). */
    public boolean isMineOrUnassigned() {
        if (task == null) {
            return false;
        }
        String me = loginBean.getCurrentUser().getId();
        return task.getAssignee() == null || me.equals(task.getAssignee());
    }

    public boolean isClaimable() {
        return task != null && task.getAssignee() == null && isApprovalTask();
    }

    // ------------------------------------------------------------------
    // Read-only display helpers (process variables)
    // ------------------------------------------------------------------

    public String getStudentId() {
        return var(VAR_STUDENT_ID);
    }

    public String getStudentName() {
        return var(VAR_STUDENT_NAME);
    }

    public String getGpa() {
        return var(VAR_STUDENT_GPA);
    }

    public String getCurrentSemester() {
        return var(VAR_CURRENT_SEMESTER);
    }

    /** Localized reason description for display (falls back to the code). */
    public String getWithdrawalReason() {
        return withdrawalReasonDesc != null && !withdrawalReasonDesc.isBlank()
                ? withdrawalReasonDesc : withdrawalReason;
    }

    /** Localized semester description for display (falls back to the code). */
    public String getWithdrawalSemester() {
        return withdrawalSemesterDesc != null && !withdrawalSemesterDesc.isBlank()
                ? withdrawalSemesterDesc : withdrawalSemester;
    }

    public String getStudentNote() {
        return studentNote;
    }

    public String getWithdrawalResult() {
        Object v = varObj(VAR_WITHDRAWAL_RESULT);
        return v != null ? String.valueOf(v) : null;
    }

    public String getWithdrawalMessage() {
        return var(VAR_WITHDRAWAL_MESSAGE);
    }

    /**
     * All recorded approval decisions ordered by decision time: REG and FIN
     * (plain variables) plus every parallel party result (approvalResults
     * map). The student's amendment / result screen shows exactly this list
     * - who approved, who rejected, why.
     */
    public List<WithdrawalApprovalResult> getApprovalResults() {
        List<WithdrawalApprovalResult> results = new ArrayList<>();
        if (task == null) {
            return results;
        }
        Map<String, Object> vars = withdrawalService.getTaskVariables(taskId);
        addStageDecision(results, vars, STAGE_ADMISSION_AND_REGISTRATION,
                DEPT_ADMISSION_AND_REGISTRATION, GROUP_ADMISSION_AND_REGISTRATION,
                VAR_REG_DECISION, VAR_REG_COMMENT);
        Object raw = vars.get(VAR_APPROVAL_RESULTS);
        if (raw instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (item instanceof WithdrawalApprovalResult result) {
                    results.add(result);
                }
            }
        }
        addStageDecision(results, vars, STAGE_FINANCE,
                DEPT_FINANCE, GROUP_FINANCE,
                VAR_FIN_DECISION, VAR_FIN_COMMENT);
        return results;
    }

    /** Adds the REG / FIN stage decision as a result row when present. */
    private void addStageDecision(List<WithdrawalApprovalResult> results,
                                  Map<String, Object> vars,
                                  String stage,
                                  String partyName,
                                  String candidateGroup,
                                  String decisionVar,
                                  String commentVar) {
        Object decision = vars.get(decisionVar);
        if (decision != null) {
            results.add(new WithdrawalApprovalResult(
                    stage, partyName, candidateGroup,
                    String.valueOf(decision),
                    str(vars.get(VAR_COMPLETED_BY)),
                    str(vars.get(commentVar)),
                    null));
        }
    }

    /** Display label of the current approval party (localized). */
    public String getApprovalPartyLabel() {
        if (task == null) {
            return null;
        }
        String key = task.getTaskDefinitionKey();
        if (TASK_REG_APPROVAL.equals(key)) {
            return getLabel("withdrawal.party.reg");
        }
        if (TASK_PARALLEL_APPROVAL.equals(key)) {
            return partyLabelOf(var(VAR_APPROVAL_GROUP));
        }
        if (TASK_FINANCE_APPROVAL.equals(key)) {
            return getLabel("withdrawal.party.fin");
        }
        return task.getName();
    }

    private String partyLabelOf(String group) {
        if (group == null) {
            return null;
        }
        return switch (group) {
            case GROUP_DEAN -> getLabel("withdrawal.party.dean");
            case GROUP_STUDENT_AFFAIRS -> getLabel("withdrawal.party.stdAff");
            case GROUP_LIBRARY -> getLabel("withdrawal.party.lib");
            case GROUP_INTERNAL_HOUSING -> getLabel("withdrawal.party.housing");
            case GROUP_HEALTH_CARE -> getLabel("withdrawal.party.hc");
            default -> group;
        };
    }

    // ------------------------------------------------------------------
    // Dropdown data (amendment form)
    // ------------------------------------------------------------------

    /** Withdrawl reason options for the amendment form. */
    public List<ReasonsBean> getReasons() {
        return commonService.getTransactionReasons();
    }

    /** Withdrawl semester options for the amendment form. */
    public List<SemesterBean> getSemesters() {
        return commonService.getTransactionSemester();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Claim an unassigned group approval task. */
    public String claim() {
        if (task == null) {
            return null;
        }
        try {
            withdrawalService.claimTask(taskId, loginBean.getCurrentUser().getId());
            addInfo(getLabel("withdrawal.task.claimed"));
            loadTask();
        } catch (Exception e) {
            addError(e.getMessage());
        }
        return null;
    }

    /** Submit the approval decision (approve / reject) of the current approver. */
    public String submitDecision() {
        if (task == null) {
            return null;
        }
        if (decision == null || decision.isBlank()) {
            addError(getLabel("withdrawal.error.decisionRequired"));
            return null;
        }
        if (DECISION_REJECT.equals(decision) && (comment == null || comment.isBlank())) {
            addError(getLabel("withdrawal.error.rejectionReasonRequired"));
            return null;
        }
        try {
            withdrawalService.completeApprovalTask(taskId, decision, comment,
                    loginBean.getCurrentUser().getId());
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            log.error("Failed to complete withdrawal task {}", taskId, e);
            addError(e.getMessage());
            return null;
        }
    }

    /** Complete the REG final FYI acknowledgement. */
    public String completeFyi() {
        if (task == null) {
            return null;
        }
        try {
            withdrawalService.completeApprovalTask(taskId, DECISION_APPROVE, comment,
                    loginBean.getCurrentUser().getId());
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            log.error("Failed to complete REG FYI task {}", taskId, e);
            addError(e.getMessage());
            return null;
        }
    }

    /** Amend + resubmit (student) - only editable fields are updated. */
    public String submitAmendment() {
        if (task == null) {
            return null;
        }
        if (withdrawalReason == null || withdrawalReason.isBlank()) {
            addError(getLabel("withdrawal.error.reasonRequired"));
            return null;
        }
        if (withdrawalSemester == null || withdrawalSemester.isBlank()) {
            addError(getLabel("withdrawal.error.semesterRequired"));
            return null;
        }
        try {
            withdrawalService.completeAmendment(taskId,
                    withdrawalReason,
                    reasonDescOf(withdrawalReason),
                    withdrawalSemester,
                    semesterDescOf(withdrawalSemester),
                    studentNote,
                    amendmentNotes,
                    loginBean.getCurrentUser().getId());
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            log.error("Failed to complete amendment task {}", taskId, e);
            addError(e.getMessage());
            return null;
        }
    }

    /** Acknowledge the final successful-withdrawal result (student). */
    public String acknowledgeResult() {
        if (task == null) {
            return null;
        }
        try {
            withdrawalService.acknowledgeResult(taskId, loginBean.getCurrentUser().getId());
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            log.error("Failed to acknowledge withdrawal result task {}", taskId, e);
            addError(e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Current UI language flag: 1 = Arabic, 2 = English (SIS convention). */
    private String lang() {
        return "ar".equals(resolveUiLocale().getLanguage()) ? "1" : "2";
    }

    private String reasonDescOf(String reasonCode) {
        for (ReasonsBean reason : getReasons()) {
            if (reason.getReasonCode() != null && reason.getReasonCode().equals(reasonCode)) {
                return "1".equals(lang()) ? reason.getReasonDesc() : reason.getReasonDescS();
            }
        }
        return null;
    }

    private String semesterDescOf(String semester) {
        for (SemesterBean s : getSemesters()) {
            if (s.getSemester() != null && s.getSemester().equals(semester)) {
                return "1".equals(lang()) ? s.getSemesterDesc() : s.getSemesterDescS();
            }
        }
        return null;
    }

    private String var(String name) {
        if (task == null) {
            return null;
        }
        Map<String, Object> vars = withdrawalService.getTaskVariables(taskId);
        return str(vars.get(name));
    }

    private Object varObj(String name) {
        if (task == null) {
            return null;
        }
        Map<String, Object> vars = withdrawalService.getTaskVariables(taskId);
        return vars.get(name);
    }

    private String str(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // getters/setters ------------------------------------------------------

    public Task getTask() {
        return task;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /** Selected reason CODE of the amendment dropdown. */
    public String getWithdrawalReasonCode() {
        return withdrawalReason;
    }

    public void setWithdrawalReasonCode(String withdrawalReason) {
        this.withdrawalReason = withdrawalReason;
    }

    /** Selected semester CODE of the amendment dropdown. */
    public String getWithdrawalSemesterCode() {
        return withdrawalSemester;
    }

    public void setWithdrawalSemesterCode(String withdrawalSemester) {
        this.withdrawalSemester = withdrawalSemester;
    }

    public void setStudentNote(String studentNote) {
        this.studentNote = studentNote;
    }

    public String getAmendmentNotes() {
        return amendmentNotes;
    }

    public void setAmendmentNotes(String amendmentNotes) {
        this.amendmentNotes = amendmentNotes;
    }
}