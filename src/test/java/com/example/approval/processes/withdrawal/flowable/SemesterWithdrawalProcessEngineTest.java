package com.example.approval.processes.withdrawal.flowable;

import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import com.example.approval.processes.withdrawal.service.WithdrawalApproverResolverService;
import com.example.approval.service.CommonService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.h2.Driver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.withdrawal.SemesterWithdrawalConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Engine-level regression tests for the Semester Withdrawal process
 * ({@code semester-withdrawl}) - the mirror of
 * {@code ClearanceProcessSynchronizationTest}.
 *
 * <p>The tests boot a REAL Flowable engine (H2 in-memory) and deploy the
 * production BPMN {@code processes/semester-withdrawl-process.bpmn20.xml}
 * unchanged. Only the four collaborators are test doubles:
 * {@link CommonService} (SIS procedures), {@link NotificationService},
 * {@link BpmAuditService} and {@link WithdrawalApproverResolverService}
 * (dynamic dean).</p>
 *
 * <p>Covered specification requirements:</p>
 * <ul>
 *   <li>female student -> Housing parallel task exists; male -> not;</li>
 *   <li>dean task dynamically assigned to the resolved dean user;</li>
 *   <li>the parallel stage is a SYNCHRONIZATION BARRIER: a rejection never
 *       cancels still-pending sibling tasks and the Amendment task is only
 *       created after EVERY applicable parallel task finished;</li>
 *   <li>all approvals route to FIN, FIN approval to the final REG FYI and
 *       only after the FYI the SIS withdrawal procedure runs;</li>
 *   <li>SIS {@code result == 1} reaches the student success task,
 *       {@code result != 1} never shows a false success (failure notice);</li>
 *   <li>REG / FIN rejections route to the Amendment task and resubmission
 *       skips already-approved parties (cumulative rule);</li>
 *   <li>audit rows (received / approved / rejected incl. reason) and
 *       asynchronous notification requests for every task recipient.</li>
 * </ul>
 */
class SemesterWithdrawalProcessEngineTest {

    private static final String INITIATOR = "student.test";
    private static final String DEAN_USER = "999001";

    private static ProcessEngine processEngine;
    private static NotificationService notificationService;
    private static BpmAuditService auditService;
    private static CommonService commonService;
    private static WithdrawalApproverResolverService approverResolver;

    @BeforeAll
    static void bootEngineAndDeployProcess() {
        notificationService = mock(NotificationService.class);
        auditService = mock(BpmAuditService.class);
        commonService = mock(CommonService.class);
        approverResolver = mock(WithdrawalApproverResolverService.class);
        when(approverResolver.resolveDeanUserId(anyString(), anyString()))
                .thenReturn(DEAN_USER);

        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:withdrawalEngineTest;DB_CLOSE_DELAY=-1", "sa", "");

        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("withdrawal-engine-test");
        processEngine = configuration.buildProcessEngine();

        // The SpringExpressionManager resolves ${...} beans LAZILY via the
        // bean factory, so the handlers can be registered here - after the
        // engine exists - and still receive the real TaskService.
        applicationContext.getBeanFactory().registerSingleton("withdrawalProcessHandler",
                new WithdrawalProcessHandler(commonService, notificationService, auditService,
                        processEngine.getTaskService()));
        applicationContext.getBeanFactory().registerSingleton("withdrawalTaskListener",
                new WithdrawalTaskListener(notificationService, auditService, approverResolver));

        processEngine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/semester-withdrawl-process.bpmn20.xml")
                .name("withdrawal-engine-test")
                .deploy();
    }

    @AfterAll
    static void shutdownEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @BeforeEach
    void resetCollaborators() {
        clearInvocations(notificationService, auditService, commonService, approverResolver);
        // default: the SIS withdrawal procedure succeeds
        when(commonService.processSemesterWithdrawal(anyMap()))
                .thenReturn(Map.of("result", 1, "message", "Semester withdrawn"));
    }

    // ==================================================================
    // Parallel routing - gender aware
    // ==================================================================

    @Test
    @DisplayName("female student -> 5 parallel tasks including Housing")
    void femaleStudent_fiveParallelTasks_includingHousing() {
        String pid = startProcess("F");
        approveAt(pid, TASK_REG_APPROVAL);

        assertEquals(5, taskCount(pid, TASK_PARALLEL_APPROVAL));
        assertNotNull(partyTask(pid, GROUP_DEAN), "dean task must exist");
        assertNotNull(partyTask(pid, GROUP_STUDENT_AFFAIRS), "STD_AFF task must exist");
        assertNotNull(partyTask(pid, GROUP_LIBRARY), "LIB task must exist");
        assertNotNull(partyTask(pid, GROUP_HEALTH_CARE), "HC task must exist");
        assertNotNull(partyTask(pid, GROUP_INTERNAL_HOUSING),
                "female student -> Housing task must exist");
    }

    @Test
    @DisplayName("male student -> 4 parallel tasks, NO Housing task")
    void maleStudent_fourParallelTasks_noHousing() {
        String pid = startProcess("M");
        approveAt(pid, TASK_REG_APPROVAL);

        assertEquals(4, taskCount(pid, TASK_PARALLEL_APPROVAL));
        assertNull(partyTask(pid, GROUP_INTERNAL_HOUSING),
                "male student -> Housing task must NOT exist");
        assertNotNull(partyTask(pid, GROUP_DEAN));
        assertNotNull(partyTask(pid, GROUP_STUDENT_AFFAIRS));
        assertNotNull(partyTask(pid, GROUP_LIBRARY));
        assertNotNull(partyTask(pid, GROUP_HEALTH_CARE));
    }

    @Test
    @DisplayName("dean task is dynamically assigned to the resolved dean user")
    void deanTask_assignedToResolvedDean() {
        String pid = startProcess("F");
        approveAt(pid, TASK_REG_APPROVAL);

        Task deanTask = partyTask(pid, GROUP_DEAN);
        assertNotNull(deanTask);
        assertEquals(DEAN_USER, deanTask.getAssignee(),
                "the dean task must be assigned to the single dean resolved from the SIS");
        verify(approverResolver).resolveDeanUserId("10", "1");
    }

    // ==================================================================
    // Synchronization barrier - amendment only after ALL tasks finished
    // ==================================================================

    @Test
    @DisplayName("rejection does NOT cancel pending siblings; amendment only after ALL tasks finish")
    void rejectionDoesNotShortCircuit_amendmentOnlyAfterAllApplicableTasksFinished() {
        String pid = startProcess("F");
        approveAt(pid, TASK_REG_APPROVAL);

        // STD_AFF rejects while 4 sibling tasks are still pending
        completeParty(pid, GROUP_STUDENT_AFFAIRS, DECISION_REJECT, "GPA below threshold");

        assertEquals(4, taskCount(pid, TASK_PARALLEL_APPROVAL),
                "the still-pending sibling tasks must remain active");
        assertEquals(0, taskCount(pid, TASK_AMENDMENT),
                "Amendment task must NOT exist while parallel tasks are pending");
        assertEquals(0, taskCount(pid, TASK_FINANCE_APPROVAL));
        assertTrue(processAlive(pid));

        // no sibling was cancelled -> no TASK_CANCELLED audit row
        verify(auditService, never()).logProcessAction(anyString(), anyString(),
                eq(ACTION_TASK_CANCELLED), any(), any(), any(), any());

        completeParty(pid, GROUP_LIBRARY, DECISION_APPROVE, null);
        assertEquals(0, taskCount(pid, TASK_AMENDMENT),
                "still pending tasks -> still no Amendment task");

        completeParty(pid, GROUP_HEALTH_CARE, DECISION_APPROVE, null);
        completeParty(pid, GROUP_DEAN, DECISION_APPROVE, null);
        assertEquals(1, taskCount(pid, TASK_PARALLEL_APPROVAL));
        assertEquals(0, taskCount(pid, TASK_AMENDMENT),
                "Housing still pending -> Amendment task must NOT exist yet");

        // the LAST applicable task releases the barrier
        completeParty(pid, GROUP_INTERNAL_HOUSING, DECISION_APPROVE, null);
        assertEquals(0, taskCount(pid, TASK_PARALLEL_APPROVAL));
        assertEquals(1, taskCount(pid, TASK_AMENDMENT),
                "Amendment task only NOW - after every applicable parallel task finished");
        assertEquals(0, taskCount(pid, TASK_FINANCE_APPROVAL),
                "a rejection routes to amendment, not to Finance");

        Task amendment = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid).taskDefinitionKey(TASK_AMENDMENT).singleResult();
        assertEquals(INITIATOR, amendment.getAssignee(),
                "amendment task belongs to the student");

        // the rejection details are available for the amendment form
        assertEquals(STAGE_PARALLEL_APPROVAL,
                processEngine.getRuntimeService().getVariable(pid, VAR_LAST_REJECTED_STAGE));
        assertEquals(DEPT_STUDENT_AFFAIRS,
                processEngine.getRuntimeService().getVariable(pid, VAR_LAST_REJECTED_PARTY));
        assertEquals("GPA below threshold",
                processEngine.getRuntimeService().getVariable(pid, VAR_LAST_REJECTION_COMMENT));
    }

    // ==================================================================
    // Sequential routing - REG / FIN / FYI / SIS execution
    // ==================================================================

    @Test
    @DisplayName("REG rejection routes to the Amendment task (no parallel stage)")
    void regRejection_routesToAmendment() {
        String pid = startProcess("M");

        completeAt(pid, TASK_REG_APPROVAL, DECISION_REJECT, "wrong semester");

        assertEquals(1, taskCount(pid, TASK_AMENDMENT));
        assertEquals(0, taskCount(pid, TASK_PARALLEL_APPROVAL),
                "REG rejection must not start the parallel stage");
        assertEquals(DEPT_ADMISSION_AND_REGISTRATION,
                processEngine.getRuntimeService().getVariable(pid, VAR_LAST_REJECTED_PARTY));
    }

    @Test
    @DisplayName("all parallel approvals approved -> FIN task; SIS runs only after final REG FYI")
    void allApprovals_routeToFinance_thenRegFyi_thenSisWithdrawal() {
        String pid = startProcess("F");
        approveAt(pid, TASK_REG_APPROVAL);

        completeParty(pid, GROUP_DEAN, DECISION_APPROVE, null);
        completeParty(pid, GROUP_STUDENT_AFFAIRS, DECISION_APPROVE, null);
        completeParty(pid, GROUP_LIBRARY, DECISION_APPROVE, null);
        completeParty(pid, GROUP_HEALTH_CARE, DECISION_APPROVE, null);
        completeParty(pid, GROUP_INTERNAL_HOUSING, DECISION_APPROVE, null);

        assertEquals(0, taskCount(pid, TASK_AMENDMENT), "all approved -> no amendment");
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL), "all approved -> FIN reached");
        verify(commonService, never()).processSemesterWithdrawal(anyMap());

        // FIN approves -> final REG FYI task
        completeAt(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE, null);
        assertEquals(1, taskCount(pid, TASK_REG_FYI),
                "FIN approval routes to the final REG FYI task");
        verify(commonService, never()).processSemesterWithdrawal(anyMap());

        // the FYI completion triggers the SIS withdrawal procedure
        completeAt(pid, TASK_REG_FYI, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(commonService, times(1)).processSemesterWithdrawal(params.capture());
        Map<String, Object> call = params.getValue();
        assertEquals("S-123", call.get("studentId"));
        assertEquals("20261", call.get("semester"));
        assertEquals("2", call.get("lang"));

        // result == 1 -> final student success task
        assertEquals(1, taskCount(pid, TASK_STUDENT_RESULT));
        Task result = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid).taskDefinitionKey(TASK_STUDENT_RESULT).singleResult();
        assertEquals(INITIATOR, result.getAssignee());
        assertEquals(Integer.valueOf(1),
                processEngine.getRuntimeService().getVariable(pid, VAR_WITHDRAWAL_RESULT));

        // completing it ends the process
        processEngine.getTaskService().complete(result.getId());
        assertFalse(processAlive(pid), "process must end after the student acknowledged the result");
    }

    @Test
    @DisplayName("failed SIS result (result != 1) -> NO false success; audited failure notice")
    void failedSisResult_noFalseSuccess_failureNoticeForStudent() {
        when(commonService.processSemesterWithdrawal(anyMap()))
                .thenReturn(Map.of("result", 0, "message", "ORA-20001: holds exist"));

        String pid = startProcess("M");
        approveAt(pid, TASK_REG_APPROVAL);
        completeParty(pid, GROUP_DEAN, DECISION_APPROVE, null);
        completeParty(pid, GROUP_STUDENT_AFFAIRS, DECISION_APPROVE, null);
        completeParty(pid, GROUP_LIBRARY, DECISION_APPROVE, null);
        completeParty(pid, GROUP_HEALTH_CARE, DECISION_APPROVE, null);
        completeAt(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE, null);
        completeAt(pid, TASK_REG_FYI, null, null);

        // the process ended via the failure path - no success task ever existed
        assertFalse(processAlive(pid));
        assertEquals(0, taskCount(pid, TASK_STUDENT_RESULT),
                "a failed SIS withdrawal must never show the success task");

        // auditable failure + standalone failure notice for the student
        verify(auditService, times(1)).logProcessAction(anyString(),
                eq(BpmAuditConstants.ACTION_TRANSACTION_SERVICE_FAILURE),
                eq(STAGE_WITHDRAWAL_EXECUTION), isNull(), anyString(), anyString(), anyString());
        Task failureNotice = processEngine.getTaskService().createTaskQuery()
                .taskAssignee(INITIATOR).taskCategory(CATEGORY_RESULT).singleResult();
        assertNotNull(failureNotice, "the student must receive a visible failure notice task");
    }

    @Test
    @DisplayName("FIN rejection -> Amendment; resubmission skips already-approved parties")
    void finRejection_amendment_resubmissionSkipsApprovedParties() {
        String pid = startProcess("M");
        approveAt(pid, TASK_REG_APPROVAL);
        completeParty(pid, GROUP_DEAN, DECISION_APPROVE, null);
        completeParty(pid, GROUP_STUDENT_AFFAIRS, DECISION_APPROVE, null);
        completeParty(pid, GROUP_LIBRARY, DECISION_APPROVE, null);
        completeParty(pid, GROUP_HEALTH_CARE, DECISION_APPROVE, null);

        completeAt(pid, TASK_FINANCE_APPROVAL, DECISION_REJECT, "fees outstanding");
        assertEquals(1, taskCount(pid, TASK_AMENDMENT),
                "FIN rejection routes to the Amendment task");

        // student amends and resubmits
        processEngine.getTaskService().complete(
                processEngine.getTaskService().createTaskQuery()
                        .processInstanceId(pid).taskDefinitionKey(TASK_AMENDMENT)
                        .singleResult().getId());

        // cumulative rule: all four parties already approved -> parallel stage
        // skipped entirely, the request goes straight back to FIN
        assertEquals(0, taskCount(pid, TASK_PARALLEL_APPROVAL),
                "already-approved parties must not be asked again");
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));
        assertEquals(2, ((Number) processEngine.getRuntimeService()
                .getVariable(pid, VAR_APPROVAL_ROUND)).intValue(),
                "resubmission increments the approval round");
    }

    // ==================================================================
    // Audit + notification coverage over a full happy run
    // ==================================================================

    @Test
    @DisplayName("audit: RECEIVED row per task, APPROVED/REJECTED rows with preserved reason")
    void auditRows_writtenForTaskLifecycle() {
        String pid = startProcess("F");
        approveAt(pid, TASK_REG_APPROVAL);

        completeParty(pid, GROUP_DEAN, DECISION_APPROVE, null);
        completeParty(pid, GROUP_STUDENT_AFFAIRS, DECISION_REJECT, "GPA below threshold");
        completeParty(pid, GROUP_LIBRARY, DECISION_APPROVE, null);
        completeParty(pid, GROUP_HEALTH_CARE, DECISION_APPROVE, null);
        completeParty(pid, GROUP_INTERNAL_HOUSING, DECISION_APPROVE, null);

        // RECEIVED (task assigned) rows: REG + 5 parallel parties + amendment
        // the amendment task carries NO candidate group -> any() also matches null
        verify(auditService, times(7)).logTaskAssigned(anyString(), anyString(), anyString(),
                any(), anyString(), any(), anyString());

        // APPROVED rows: REG + 4 approving parties (amendment not completed yet)
        verify(auditService, times(5)).logTaskCompleted(anyString(), anyString(), anyString(),
                anyString(), eq(DECISION_APPROVE), any(), anyString(), anyString());

        // REJECTED row with the preserved rejection reason in the comment
        verify(auditService, times(1)).logTaskCompleted(anyString(),
                eq(STAGE_PARALLEL_APPROVAL), eq(DEPT_STUDENT_AFFAIRS), anyString(),
                eq(DECISION_REJECT), eq("GPA below threshold"), anyString(), anyString());
    }

    @Test
    @DisplayName("notifications: async request for every task recipient (REG/dean/parties/FIN/FYI/student)")
    void notifications_sentForEveryTaskRecipient() {
        String pid = startProcess("F");
        approveAt(pid, TASK_REG_APPROVAL);
        completeParty(pid, GROUP_DEAN, DECISION_APPROVE, null);
        completeParty(pid, GROUP_STUDENT_AFFAIRS, DECISION_APPROVE, null);
        completeParty(pid, GROUP_LIBRARY, DECISION_APPROVE, null);
        completeParty(pid, GROUP_HEALTH_CARE, DECISION_APPROVE, null);
        completeParty(pid, GROUP_INTERNAL_HOUSING, DECISION_APPROVE, null);
        completeAt(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE, null);
        completeAt(pid, TASK_REG_FYI, null, null);

        ArgumentCaptor<NotificationMessage> captor =
                ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService, atLeastOnce()).send(captor.capture());
        List<NotificationMessage> messages = captor.getAllValues();

        // REG + 5 parallel parties + FIN + REG FYI + final student result
        assertEquals(9, messages.size(),
                "one async notification per created task recipient");
        assertTrue(messages.stream()
                        .anyMatch(m -> GROUP_ADMISSION_AND_REGISTRATION.equals(m.getCandidateGroup())
                                && STAGE_ADMISSION_AND_REGISTRATION.equals(m.getStage())
                                && m.getStage() != null),
                "REG must be notified (approval AND final FYI)");
        for (String group : List.of(GROUP_DEAN, GROUP_STUDENT_AFFAIRS, GROUP_LIBRARY,
                GROUP_INTERNAL_HOUSING, GROUP_HEALTH_CARE, GROUP_FINANCE)) {
            assertTrue(messages.stream().anyMatch(m -> group.equals(m.getCandidateGroup())),
                    "candidate group " + group + " must receive a notification");
        }
        assertTrue(messages.stream().anyMatch(m -> DEAN_USER.equals(m.getAssigneeUser())),
                "the dean task notification carries the resolved single approver");
        assertTrue(messages.stream().anyMatch(m -> INITIATOR.equals(m.getRecipientUser())
                        && NotificationMessage.Type.RESULT.equals(m.getType())),
                "the student must receive the final result notification");
        // the notification SERVICE is called - no synchronous SMTP anywhere
        messages.forEach(m -> assertEquals(PROCESS_KEY, m.getProcessKey()));
    }

    // ==================================================================
    // Static BPMN guards
    // ==================================================================

    @Test
    @DisplayName("BPMN model: key 'semester-withdrawl', parallel MI, all-instances barrier")
    void bpmnModel_processKeyAndSynchronizationBarrier() {
        ProcessDefinition definition = processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("semester-withdrawl")
                .singleResult();
        assertNotNull(definition, "process key must be exactly 'semester-withdrawl'");

        BpmnModel model = processEngine.getRepositoryService().getBpmnModel(definition.getId());
        FlowElement element = model.getProcessById("semester-withdrawl")
                .getFlowElement(TASK_PARALLEL_APPROVAL);
        UserTask userTask = assertInstanceOf(UserTask.class, element);

        MultiInstanceLoopCharacteristics mi = userTask.getLoopCharacteristics();
        assertNotNull(mi, "the parallel approval stage must be multi-instance");
        assertFalse(mi.isSequential(), "approvals must run in PARALLEL");

        String condition = mi.getCompletionCondition().trim();
        assertEquals("${nrOfCompletedInstances == nrOfInstances}", condition,
                "the stage must only complete when EVERY applicable task finished");
        assertFalse(condition.contains(VAR_ANY_APPROVAL_REJECTED),
                "a single rejection must never short-circuit the barrier");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private String startProcess(String gender) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(VAR_INITIATOR, INITIATOR);
        variables.put(VAR_STUDENT_ID, "S-123");
        variables.put(VAR_STUDENT_NAME, "Test Student");
        variables.put(VAR_GENDER, gender);
        variables.put(VAR_FACULTY_NO, "10");
        variables.put(VAR_CAMPUS_NO, "1");
        variables.put(VAR_WITHDRAWAL_SEMESTER, "20261");
        variables.put(VAR_LANG, "2");
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey(PROCESS_KEY, variables);
        return instance.getId();
    }

    /** Approves the active task with the given definition key. */
    private void approveAt(String pid, String taskDefinitionKey) {
        completeAt(pid, taskDefinitionKey, DECISION_APPROVE, null);
    }

    /** Completes the active task with the given definition key / decision. */
    private void completeAt(String pid, String taskDefinitionKey, String decision, String comment) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
        assertNotNull(task, "expected active task " + taskDefinitionKey);
        complete(task.getId(), decision, comment);
    }

    /** Completes the still-active parallel task of {@code party}. */
    private void completeParty(String pid, String party, String decision, String comment) {
        Task task = partyTask(pid, party);
        assertNotNull(task, "expected active approval task for party " + party);
        complete(task.getId(), decision, comment);
    }

    private void complete(String taskId, String decision, String comment) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_DECISION, decision);
        vars.put(VAR_COMMENT, comment != null ? comment : "");
        vars.put(VAR_COMPLETED_BY, "tester");
        processEngine.getTaskService().complete(taskId, vars);
    }

    private Task partyTask(String pid, String party) {
        TaskQuery query = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(TASK_PARALLEL_APPROVAL);
        if (GROUP_DEAN.equals(party)) {
            // spec 9.1: the dean task is an INDIVIDUAL task assigned to the
            // resolved dean user - assigned tasks are not returned by the
            // candidate-group query
            return query.taskAssignee(DEAN_USER).singleResult();
        }
        return query.taskCandidateGroup(party).singleResult();
    }

    private long taskCount(String pid, String taskDefinitionKey) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .count();
    }

    private boolean processAlive(String pid) {
        return processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(pid).count() == 1;
    }
}