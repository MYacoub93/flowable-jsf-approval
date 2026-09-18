package com.example.approval.clearance.flowable;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.entity.ExternalUser;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.notification.NotificationProperties;
import com.example.approval.notification.config.EmailNotificationAsyncConfig;
import com.example.approval.notification.service.AsyncEmailDispatcher;
import com.example.approval.notification.service.NotificationRecipientResolver;
import com.example.approval.notification.service.impl.EmailNotificationService;
import com.example.approval.processes.clearance.flowable.ClearanceProcessHandler;
import com.example.approval.processes.clearance.flowable.ClearanceTaskListener;
import com.example.approval.processes.clearance.service.DepartmentResolverService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.h2.Driver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.approval.processes.clearance.ClearanceConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * THE regression test for the asynchronous e-mail architecture:
 *
 * <pre>
 * Flowable task completion
 *         |
 *         v
 * notification scheduled (emailNotificationExecutor)
 *         |
 *         v
 * Flowable completion SUCCEEDS + workflow advances
 *         |
 *         v
 * SMTP may still be RUNNING or FAILING independently
 * </pre>
 *
 * <p>Same real-engine setup as {@code ClearanceNotificationEngineTest}
 * (production BPMN unchanged, H2, department resolver and audit mocked) -
 * but instead of mocking {@code NotificationService} the REAL
 * {@link EmailNotificationService} is wired with a REAL
 * {@code @Async}-proxied {@link AsyncEmailDispatcher} and a REAL bounded
 * {@code emailNotificationExecutor}. Only the two outermost edges are test
 * doubles: the {@code FLOWABLE_USERS_VW} mapper (Oracle) and
 * {@link JavaMailSender} (SMTP).</p>
 *
 * <p>The SMTP mock can HANG (CountDownLatch) or FAIL (exception); the tests
 * then prove the Flowable thread was never blocked and never saw the
 * failure. All synchronization is deterministic (latches / Mockito
 * {@code timeout()}) - no {@code Thread.sleep()}.</p>
 */
class ClearanceAsyncEmailIsolationEngineTest {

    private static final String INITIATOR = "student.test";

    private static ProcessEngine processEngine;
    private static FixedDepartmentResolver resolver;
    private static BpmAuditService auditService;
    private static FlowableIdentityMapper identityMapper;
    private static JavaMailSender mailSender;
    private static ThreadPoolTaskExecutor emailExecutor;

    /** Hangs every SMTP send until released; failures/sends are counted. */
    private static volatile CountDownLatch smtpBlocker = new CountDownLatch(0);
    private static final AtomicInteger smtpFailures = new AtomicInteger();
    private static final AtomicInteger smtpSends = new AtomicInteger();

    @BeforeAll
    static void bootEngineAndDeployProcess() {
        resolver = new FixedDepartmentResolver();
        auditService = mock(BpmAuditService.class);
        identityMapper = mock(FlowableIdentityMapper.class);
        mailSender = mock(JavaMailSender.class);

        // FLOWABLE_USERS_VW: every group member resolves to a personal
        // address exactly as in production (FIN -> two members)
        when(identityMapper.findMembersByGroup(any())).thenReturn(List.of(
                user("fin.boss", "fin.boss@example.edu"),
                user("fin.clerk", "fin.clerk@example.edu")));
        when(identityMapper.findEmailByUsername(INITIATOR))
                .thenReturn("student.test@example.edu");

        // SMTP edge: hangs while smtpBlocker is held, counts sends/failures
        doAnswer(invocation -> {
            smtpSends.incrementAndGet();
            smtpBlocker.await(30, TimeUnit.SECONDS);
            if (smtpFailures.get() > 0) {
                throw new RuntimeException("SMTP connection refused");
            }
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        // the real dedicated bounded e-mail executor
        emailExecutor = new ThreadPoolTaskExecutor();
        emailExecutor.setCorePoolSize(2);
        emailExecutor.setMaxPoolSize(4);
        emailExecutor.setQueueCapacity(50);
        emailExecutor.setThreadNamePrefix("email-");
        emailExecutor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        emailExecutor.setWaitForTasksToCompleteOnShutdown(true);
        emailExecutor.setAwaitTerminationSeconds(10);
        emailExecutor.initialize();

        // the real @Async boundary, proxied exactly like @EnableAsync does
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.setFrom("noreply@example.edu");
        properties.setAlwaysLog(false);
        properties.setUserEmailDomain(null);

        ObjectProvider<JavaMailSender> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        AsyncEmailDispatcher rawDispatcher = new AsyncEmailDispatcher(properties, provider);

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton(
                EmailNotificationAsyncConfig.EXECUTOR_BEAN_NAME, emailExecutor);
        AsyncAnnotationBeanPostProcessor bpp = new AsyncAnnotationBeanPostProcessor();
        bpp.setBeanFactory(beanFactory);
        AsyncEmailDispatcher proxiedDispatcher = (AsyncEmailDispatcher) bpp
                .postProcessAfterInitialization(rawDispatcher, "asyncEmailDispatcher");

        EmailNotificationService notificationService = new EmailNotificationService(
                properties, new NotificationRecipientResolver(identityMapper),
                proxiedDispatcher);

        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:clearanceAsyncEmailTest;DB_CLOSE_DELAY=-1", "sa", "");

        org.flowable.spring.SpringProcessEngineConfiguration configuration =
                new org.flowable.spring.SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("clearance-async-email-test");
        processEngine = configuration.buildProcessEngine();

        applicationContext.getBeanFactory().registerSingleton("clearanceProcessHandler",
                new ClearanceProcessHandler(resolver, notificationService, auditService,
                        processEngine.getTaskService()));
        applicationContext.getBeanFactory().registerSingleton("clearanceTaskListener",
                new ClearanceTaskListener(notificationService, auditService));

        processEngine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/clearance-letter-process.bpmn20.xml")
                .name("clearance-async-email-test")
                .deploy();
    }

    @AfterAll
    static void shutdownEngine() {
        smtpBlocker = new CountDownLatch(0); // release any hanging worker
        if (processEngine != null) {
            processEngine.close();
        }
        if (emailExecutor != null) {
            emailExecutor.shutdown();
        }
    }

    @BeforeEach
    void resetState() {
        resolver.set(List.of(DEPT_IT));
        smtpBlocker = new CountDownLatch(0);
        smtpFailures.set(0);
        smtpSends.set(0);
        clearInvocations(auditService);
    }

    // ==================================================================
    // 1) completion succeeds while SMTP is still hanging
    // ==================================================================

    @Test
    @DisplayName("Task completion succeeds + workflow advances while SMTP is still hanging")
    void taskCompletion_isNotBlockedByHangingSmtp() {
        // hold EVERY smtp send hostage for the whole test
        CountDownLatch hold = new CountDownLatch(1);
        smtpBlocker = hold;

        String pid = startProcess();

        long before = System.nanoTime();
        // completing the IT department task fires the (group) notification
        // synchronously in the listener and schedules the FINANCE mail
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        // 1. the Flowable transaction completed FAST - SMTP never blocked it
        assertTrue(elapsedMs < 3000,
                "task completion must not wait for SMTP, took " + elapsedMs + "ms");

        // 2. the workflow ADVANCED: the Finance task exists already
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL),
                "the workflow must advance while SMTP is still hanging");

        // 3. the notification really entered SMTP in the background and is
        //    still hanging there (that is the whole point)
        verify(mailSender, timeout(2000).atLeast(1)).send(any(SimpleMailMessage.class));
        assertEquals(1, hold.getCount(),
                "SMTP is still hanging - the workflow moved on without it");

        // 4. the Finance (next stage) notification was scheduled as well and
        //    is queued/hanging - everything isolated from the engine thread
        hold.countDown(); // release the background workers
        verify(mailSender, timeout(2000).atLeast(2)).send(any(SimpleMailMessage.class));
    }

    // ==================================================================
    // 2) completion succeeds even when SMTP FAILS in the background
    // ==================================================================

    @Test
    @DisplayName("Task completion + workflow advance survive a FAILING SMTP server")
    void taskCompletion_survivesFailingSmtp() {
        smtpFailures.set(1); // every background send throws

        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);

        // the workflow advanced despite SMTP throwing in the background
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));
        // and the process instance is alive and queryable
        assertNotNull(processEngine.getRuntimeService()
                .createProcessInstanceQuery().processInstanceId(pid).singleResult());

        // the failure happened on the email- thread, not here
        verify(mailSender, timeout(2000).atLeast(1)).send(any(SimpleMailMessage.class));

        // the flow can even finish its next stage while SMTP keeps failing
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_ADMISSION_APPROVAL));
    }

    // ==================================================================
    // 3) recipients stay correct through the whole chain under the engine
    // ==================================================================

    @Test
    @DisplayName("Finance notification still resolves to all FIN member addresses (async chain)")
    void financeNotification_resolvesAllFinMembers_throughAsyncChain() {
        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);

        // capture everything the background workers actually sent; the
        // IT-department mail and the Finance mail must both appear
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000).atLeast(2)).send(captor.capture());
        SimpleMailMessage finance = captor.getAllValues().stream()
                .filter(m -> m.getSubject() != null
                        && m.getSubject().toLowerCase().contains("finance"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no Finance mail was sent; got subjects: "
                                + captor.getAllValues().stream()
                                        .map(SimpleMailMessage::getSubject).toList()));

        assertNotNull(finance.getTo(), "FIN members must have been resolved");
        assertEquals(2, finance.getTo().length,
                "both FIN members receive the mail - no group mailbox");
        assertTrue(List.of(finance.getTo()).containsAll(List.of(
                "fin.boss@example.edu", "fin.clerk@example.edu")),
                "unexpected recipients: " + List.of(finance.getTo()));
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

    private static ExternalUser user(String username, String email) {
        ExternalUser user = new ExternalUser();
        user.setUsername(username);
        user.setEmail(email);
        return user;
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