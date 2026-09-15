package com.example.approval.clearance.flowable;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import com.example.approval.processes.clearance.flowable.ClearanceProcessHandler;
import com.example.approval.processes.clearance.flowable.ClearanceTaskListener;
import com.example.approval.processes.clearance.service.DepartmentResolverService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
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

import static com.example.approval.processes.clearance.ClearanceConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Engine-level regression tests for the Clearance Letter
 * <b>NOTIFICATION</b> rules (the three historically missing e-mails):
 *
 * <ol>
 *   <li><b>Finance</b> - the Finance task notification must carry the SIS
 *       role code {@code FIN} as candidate group (NOT the display name
 *       "Finance Department"): {@code FLOWABLE_USERS_VW} can only resolve
 *       members for {@code ROLE_CODE_} ids, a display name resolves to zero
 *       members and the e-mail was silently skipped;</li>
 *   <li><b>Admission & Registration</b> - same rule for {@code REG};</li>
 *   <li><b>Amendment / resubmission</b> - when a rejection returns the
 *       request to the initiator, the initiator must receive an e-mail;
 *       after resubmission only still-pending departments are notified
 *       again (already-approved departments never), and the Finance /
 *       Admission stages reached afterwards notify again.</li>
 * </ol>
 *
 * <p>Same setup as {@code ClearanceProcessResubmissionTest}: a REAL
 * Flowable engine (H2) runs the production BPMN unchanged; the department
 * resolver, the notification service and the audit service are test
 * doubles. The notifications are verified down to the exact recipient
 * targeting fields ({@code candidateGroup} / {@code recipientUser}) that
 * {@code EmailNotificationService} turns into e-mail addresses.</p>
 */
class ClearanceNotificationEngineTest {

    private static final String INITIATOR = "student.test";

    private static ProcessEngine processEngine;
    private static FixedDepartmentResolver resolver;
    private static NotificationService notificationService;
    private static BpmAuditService auditService;

    @BeforeAll
    static void bootEngineAndDeployProcess() {
        resolver = new FixedDepartmentResolver();
        notificationService = mock(NotificationService.class);
        auditService = mock(BpmAuditService.class);

        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:clearanceNotifyTest;DB_CLOSE_DELAY=-1", "sa", "");

        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("clearance-notify-test");
        processEngine = configuration.buildProcessEngine();

        applicationContext.getBeanFactory().registerSingleton("clearanceProcessHandler",
                new ClearanceProcessHandler(resolver, notificationService, auditService,
                        processEngine.getTaskService()));
        applicationContext.getBeanFactory().registerSingleton("clearanceTaskListener",
                new ClearanceTaskListener(notificationService, auditService));

        processEngine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/clearance-letter-process.bpmn20.xml")
                .name("clearance-notify-test")
                .deploy();
    }

    @AfterAll
    static void shutdownEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @BeforeEach
    void resetStubs() {
        resolver.set(List.of());
        clearInvocations(notificationService);
    }

    // ==================================================================
    // 1) Finance task notification
    // ==================================================================

    @Test
    @DisplayName("Finance task notification carries the FIN role code as candidate group")
    void financeTaskNotification_usesFinanceRoleCodeGroup() {
        resolver.set(List.of(DEPT_IT));
        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));

        NotificationMessage finance = capturedMessageOfStage(STAGE_FINANCE);
        assertEquals(NotificationMessage.Type.TASK_ASSIGNED, finance.getType());
        assertEquals(GROUP_FINANCE_ROLE_CODE, finance.getCandidateGroup(),
                "recipient resolution requires the SIS role code FIN - the display name "
                        + "resolves to zero members and the mail is skipped");
        assertEquals(GROUP_FINANCE, finance.getDepartment(),
                "the display name stays on the message for the subject / audit context");
        assertNull(finance.getAssigneeUser(), "Finance is a pure group task");
    }

    // ==================================================================
    // 2) Admission & Registration task notification
    // ==================================================================

    @Test
    @DisplayName("Admission task notification carries the REG role code as candidate group")
    void admissionTaskNotification_usesAdmissionRoleCodeGroup() {
        resolver.set(List.of(DEPT_IT));
        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_ADMISSION_APPROVAL));

        NotificationMessage admission = capturedMessageOfStage(STAGE_ADMISSION_AND_REGISTRATION);
        assertEquals(NotificationMessage.Type.TASK_ASSIGNED, admission.getType());
        assertEquals(GROUP_ADMISSION_AND_REGISTRATION_ROLE_CODE, admission.getCandidateGroup(),
                "recipient resolution requires the SIS role code REG");
        assertEquals(GROUP_ADMISSION_AND_REGISTRATION, admission.getDepartment());
        assertNull(admission.getAssigneeUser());
    }

    // ==================================================================
    // 3) Amendment notification to the initiator
    // ==================================================================

    @Test
    @DisplayName("Department rejection -> amendment task notifies the initiator personally")
    void departmentRejection_notifiesInitiatorOfAmendmentTask() {
        resolver.set(List.of(DEPT_IT));
        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_REJECT);
        assertEquals(1, taskCount(pid, TASK_AMEND));

        NotificationMessage amendment = capturedMessageOfStage(STAGE_AMENDMENT);
        assertEquals(NotificationMessage.Type.TASK_ASSIGNED, amendment.getType());
        assertEquals(INITIATOR, amendment.getRecipientUser(),
                "the amendment task belongs to the initiator - the mail must resolve "
                        + "through the personal FLOWABLE_USERS_VW address");
        assertNull(amendment.getCandidateGroup(),
                "the initiator is a user, not a group - a group lookup would find nothing");
        assertNull(amendment.getDepartment());
        assertTrue(amendment.getSubject().toLowerCase().contains("amend"),
                "subject must tell the initiator to amend: " + amendment.getSubject());
    }

    @Test
    @DisplayName("Finance rejection -> amendment task also notifies the initiator")
    void financeRejection_notifiesInitiatorOfAmendmentTask() {
        resolver.set(List.of(DEPT_IT));
        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_REJECT);
        assertEquals(1, taskCount(pid, TASK_AMEND));

        NotificationMessage amendment = capturedMessageOfStage(STAGE_AMENDMENT);
        assertEquals(INITIATOR, amendment.getRecipientUser());
        assertNull(amendment.getCandidateGroup());
    }

    // ==================================================================
    // 4) Resubmission - only still-pending departments are notified again
    // ==================================================================

    @Test
    @DisplayName("Resubmission: rejected IT is notified again, approved DEN is NOT")
    void resubmission_notifiesOnlyPendingDepartments() {
        resolver.set(List.of(DEPT_DEN, DEPT_IT));
        String pid = startProcess();

        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeDepartment(pid, DEPT_IT, DECISION_REJECT);
        assertEquals(1, taskCount(pid, TASK_AMEND));

        // the amendment notification fired when the rejection created the
        // amendment task - i.e. BEFORE the resubmission below
        NotificationMessage amendment = capturedMessageOfStage(STAGE_AMENDMENT);
        assertEquals(INITIATOR, amendment.getRecipientUser());

        clearInvocations(notificationService);
        completeAmendment(pid);

        // round 2: exactly ONE new department notification - for IT;
        // already-approved DEN is never notified again
        List<NotificationMessage> departmentMails = capturedMessagesOfStage(
                STAGE_DEPARTMENT_APPROVAL);
        assertEquals(1, departmentMails.size(),
                "only the department that did not approve yet may be notified again");
        assertEquals(DEPT_IT, departmentMails.get(0).getCandidateGroup());
        assertEquals(DEPT_IT, departmentMails.get(0).getDepartment());
        assertEquals(0, capturedMessagesOfStage(STAGE_AMENDMENT).size(),
                "no new amendment notification - nothing was rejected in round 2");
    }

    @Test
    @DisplayName("Resubmission after Finance rejection: Finance task is notified again (FIN)")
    void resubmissionAfterFinanceRejection_notifiesFinanceAgain() {
        resolver.set(List.of(DEPT_DEN));
        String pid = startProcess();
        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));
        assertEquals(1, capturedMessagesOfStage(STAGE_FINANCE).size(),
                "the first Finance task notified the FIN group");

        clearInvocations(notificationService);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_REJECT);
        completeAmendment(pid);

        // department stage skipped (DEN already approved), flow returns to Finance
        assertEquals(0, taskCount(pid, TASK_DEPARTMENT_APPROVAL));
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));

        NotificationMessage finance = capturedMessageOfStage(STAGE_FINANCE);
        assertEquals(GROUP_FINANCE_ROLE_CODE, finance.getCandidateGroup(),
                "the re-created Finance task must notify the FIN group again");
    }

    @Test
    @DisplayName("Resubmission reaching Admission: Admission task is notified again (REG)")
    void resubmissionReachingAdmission_notifiesAdmissionAgain() {
        resolver.set(List.of(DEPT_DEN));
        String pid = startProcess();
        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_REJECT);
        completeAmendment(pid);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_ADMISSION_APPROVAL));

        NotificationMessage admission = capturedMessageOfStage(STAGE_ADMISSION_AND_REGISTRATION);
        assertEquals(GROUP_ADMISSION_AND_REGISTRATION_ROLE_CODE, admission.getCandidateGroup());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private String startProcess() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(VAR_INITIATOR, INITIATOR);
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey(PROCESS_KEY, variables);
        return instance.getId();
    }

    private void completeDepartment(String pid, String department, String decision) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(TASK_DEPARTMENT_APPROVAL)
                .taskCandidateGroup(department)
                .singleResult();
        assertNotNull(task, "expected active approval task for department " + department);
        complete(task.getId(), decision);
    }

    private void completeTask(String pid, String taskDefinitionKey, String decision) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
        assertNotNull(task, "expected active task " + taskDefinitionKey);
        complete(task.getId(), decision);
    }

    private void completeAmendment(String pid) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(TASK_AMEND)
                .singleResult();
        assertNotNull(task, "expected active amendment task");
        processEngine.getTaskService().complete(task.getId());
    }

    private void complete(String taskId, String decision) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_DECISION, decision);
        vars.put(VAR_COMMENT, "test comment");
        vars.put(VAR_COMPLETED_BY, "tester");
        processEngine.getTaskService().complete(taskId, vars);
    }

    private long taskCount(String pid, String taskDefinitionKey) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .count();
    }

    /** All notifications captured so far (at least zero are expected). */
    private static List<NotificationMessage> capturedMessages() {
        ArgumentCaptor<NotificationMessage> captor =
                ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    private static List<NotificationMessage> capturedMessagesOfStage(String stage) {
        return capturedMessages().stream()
                .filter(m -> stage.equals(m.getStage()))
                .toList();
    }

    private static NotificationMessage capturedMessageOfStage(String stage) {
        List<NotificationMessage> messages = capturedMessagesOfStage(stage);
        assertEquals(1, messages.size(), "expected exactly one notification of stage " + stage);
        return messages.get(0);
    }

    /** Deterministic department resolver for tests (no SIS/HR lookup). */
    private static final class FixedDepartmentResolver implements DepartmentResolverService {
        private volatile List<String> departments = List.of();

        void set(List<String> departments) {
            this.departments = departments;
        }

        @Override
        public List<String> getRequiredDepartments(String initiatorUsername) {
            return departments;
        }
    }
}