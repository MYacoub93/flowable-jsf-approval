package com.example.approval.mycases;

import com.example.approval.audit.BpmAuditAction;
import com.example.approval.audit.model.BpmAuditLogDtl;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.entity.PageResult;
import com.example.approval.service.ExternalGroupService;
import org.flowable.common.engine.impl.identity.Authentication;
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
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests for the <b>My Cases</b> feature against a REAL Flowable
 * engine (H2, same boot pattern as {@code TaskDelegationEngineTest}): a
 * two-user-task process with candidate groups is deployed and instances are
 * started by two different users; the production {@link MyCasesService}
 * serves the listing and every details section.
 *
 * <p><b>Security matrix under test:</b> user A starts case 1, user B starts
 * case 2 - A sees only case 1, B sees only case 2, and neither can open the
 * other's case by id (server-side
 * {@code HistoricProcessInstanceQuery.startedBy} guard, not UI filtering).
 * The only sanctioned widening is the <b>Cases I Approved</b> section: a
 * case the user personally recorded a BPM audit action on becomes viewable
 * through {@code ENTRY_USER}, everything else stays sealed.</p>
 *
 * <p>Additionally covers process filtering, status filtering, server-side
 * pagination, running AND completed cases, active-tasks (completed tasks
 * excluded, unassigned candidate-group tasks never rendered as an individual
 * assignee), variables (internal {@code _} prefixed ones hidden, values
 * type-safely rendered), the audit-backed timeline (chronological order),
 * the read-only diagram and the <b>My Decisions</b> tab (only the logged-in
 * user's own audit rows).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyCasesEngineTest {

    private static final String USER_A = "111";
    private static final String USER_B = "222";
    private static final String ALICE = "alice";
    private static final String PROCESS_KEY = "myCasesTestProcess";
    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                         xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                         xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                         targetNamespace="http://flowable.org/test">
              <process id="myCasesTestProcess" name="My Cases Test Process" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="hodTask"/>
                <userTask id="hodTask" name="HOD Review"
                          flowable:candidateGroups="HOD"/>
                <sequenceFlow id="f2" sourceRef="hodTask" targetRef="financeTask"/>
                <userTask id="financeTask" name="Finance Review"
                          flowable:candidateGroups="FIN"/>
                <sequenceFlow id="f3" sourceRef="financeTask" targetRef="end"/>
                <endEvent id="end"/>
              </process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_myCases">
                <bpmndi:BPMNPlane bpmnElement="myCasesTestProcess" id="BPMNPlane_myCases">
                  <bpmndi:BPMNShape bpmnElement="start" id="Shape_start">
                    <dc:Bounds height="30.0" width="30.0" x="100.0" y="163.0"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape bpmnElement="hodTask" id="Shape_hodTask">
                    <dc:Bounds height="60.0" width="100.0" x="210.0" y="148.0"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape bpmnElement="financeTask" id="Shape_financeTask">
                    <dc:Bounds height="60.0" width="100.0" x="390.0" y="148.0"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape bpmnElement="end" id="Shape_end">
                    <dc:Bounds height="28.0" width="28.0" x="570.0" y="164.0"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNEdge bpmnElement="f1" id="Edge_f1">
                    <di:waypoint x="130.0" y="178.0"/>
                    <di:waypoint x="210.0" y="178.0"/>
                  </bpmndi:BPMNEdge>
                  <bpmndi:BPMNEdge bpmnElement="f2" id="Edge_f2">
                    <di:waypoint x="310.0" y="178.0"/>
                    <di:waypoint x="390.0" y="178.0"/>
                  </bpmndi:BPMNEdge>
                  <bpmndi:BPMNEdge bpmnElement="f3" id="Edge_f3">
                    <di:waypoint x="490.0" y="178.0"/>
                    <di:waypoint x="570.0" y="178.0"/>
                  </bpmndi:BPMNEdge>
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </definitions>
            """;

    private static ProcessEngine processEngine;
    private static ExternalGroupService groupService;
    private static BpmAuditService auditService;
    private MyCasesService service;

    @BeforeAll
    static void bootEngineAndDeployProcess() {
        groupService = mock(ExternalGroupService.class);
        auditService = mock(BpmAuditService.class);

        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:myCasesTest;DB_CLOSE_DELAY=-1", "sa", "");

        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("my-cases-test");
        processEngine = configuration.buildProcessEngine();

        processEngine.getRepositoryService().createDeployment()
                .addInputStream("my-cases-test.bpmn20.xml",
                        new ByteArrayInputStream(BPMN.getBytes(StandardCharsets.UTF_8)))
                .name("my-cases-test")
                .deploy();
    }

    @AfterAll
    static void shutdownEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(groupService, auditService);
        // username lookup is "down" by default -> display falls back to bare ids
        when(groupService.findUsersByIds(anyList())).thenReturn(List.of());
        // no audit record by default -> only the initiator may view a case
        when(auditService.hasUserActedOnCase(anyString(), anyString())).thenReturn(false);
        service = new MyCasesService(processEngine.getRepositoryService(),
                processEngine.getRuntimeService(), processEngine.getTaskService(),
                processEngine.getHistoryService(), groupService, auditService);
    }

    // ==================================================================
    // Security: a user sees ONLY the instances they started
    // ==================================================================

    @Test
    @DisplayName("User A starts case 1, user B starts case 2: each sees only their own")
    void securityEachUserSeesOnlyOwnCases() {
        String pidA = startInstance(USER_A);
        String pidB = startInstance(USER_B);
        try {
            PageResult<CaseRow> pageA = service.findMyCases(USER_A, "", 1, 10);
            assertThat(pageA.getTotalRows()).isEqualTo(1L);
            assertThat(pageA.getRows()).extracting(CaseRow::getId).containsExactly(pidA);

            PageResult<CaseRow> pageB = service.findMyCases(USER_B, "", 1, 10);
            assertThat(pageB.getTotalRows()).isEqualTo(1L);
            assertThat(pageB.getRows()).extracting(CaseRow::getId).containsExactly(pidB);

            // cross-check: B's case never appears for A and vice versa
            assertThat(pageA.getRows()).extracting(CaseRow::getId).doesNotContain(pidB);
            assertThat(pageB.getRows()).extracting(CaseRow::getId).doesNotContain(pidA);
        } finally {
            deleteInstance(pidA);
            deleteInstance(pidB);
        }
    }

    @Test
    @DisplayName("Requesting another user's case by id returns nothing (details stay sealed)")
    void securityCaseDetailsOfAnotherUserAreSealed() {
        String pidB = startInstance(USER_B);
        try {
            // every details entry point must yield nothing for the wrong user
            assertThat(service.requireOwnedInstance(USER_A, pidB)).isNull();
            assertThat(service.requireViewableInstance(USER_A, ALICE, pidB)).isNull();
            assertThat(service.findCaseOverview(USER_A, ALICE, pidB)).isNull();
            assertThat(service.findActiveTasks(USER_A, ALICE, pidB)).isEmpty();
            assertThat(service.findVariables(USER_A, ALICE, pidB)).isEmpty();
            assertThat(service.findTimeline(USER_A, ALICE, pidB)).isEmpty();
        } finally {
            deleteInstance(pidB);
        }
    }

    @Test
    @DisplayName("Diagram of another user's case is refused server-side")
    void securityDiagramOfAnotherUserIsForbidden() {
        String pidB = startInstance(USER_B);
        try {
            assertThatThrownBy(() -> service.generateDiagramImage(USER_A, ALICE, pidB))
                    .isInstanceOf(SecurityException.class);
        } finally {
            deleteInstance(pidB);
        }
    }

    @Test
    @DisplayName("Not-logged-in requests are rejected before any query")
    void securityRequiresLoggedInUser() {
        assertThatThrownBy(() -> service.findMyCases(null, "", 1, 10))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.findMyCases(" ", "", 1, 10))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.requireOwnedInstance(null, "whatever"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.findApprovedCases(null, 1, 10))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.findApprovedCases(" ", 1, 10))
                .isInstanceOf(SecurityException.class);
    }

    // ==================================================================
    // Listing: process filter + pagination + running/completed
    // ==================================================================

    @Test
    @DisplayName("All Processes vs specific process filter")
    void processFilterNarrowsTheListing() {
        String pid1 = startInstance(USER_A);
        try {
            // no filter -> the user's cases
            assertThat(service.findMyCases(USER_A, "", 1, 10).getTotalRows()).isEqualTo(1L);
            // matching key -> same result
            assertThat(service.findMyCases(USER_A, PROCESS_KEY, 1, 10).getTotalRows())
                    .isEqualTo(1L);
            // unrelated key -> nothing
            assertThat(service.findMyCases(USER_A, "noSuchProcessKey", 1, 10).getTotalRows())
                    .isZero();
        } finally {
            deleteInstance(pid1);
        }
    }

    @Test
    @DisplayName("Server-side pagination: 5 rows page through 7 cases, counts stay exact")
    void serverSidePagination() {
        String[] pids = new String[7];
        for (int i = 0; i < pids.length; i++) {
            pids[i] = startInstance(USER_A);
        }
        try {
            PageResult<CaseRow> page1 = service.findMyCases(USER_A, "", 1, 5);
            assertThat(page1.getTotalRows()).isEqualTo(7L);
            assertThat(page1.getRows()).hasSize(5);

            PageResult<CaseRow> page2 = service.findMyCases(USER_A, "", 2, 5);
            assertThat(page2.getRows()).hasSize(2);

            // requesting a page beyond the end clamps to the last page
            PageResult<CaseRow> clamped = service.findMyCases(USER_A, "", 99, 5);
            assertThat(clamped.getPageNumber()).isEqualTo(2);
            assertThat(clamped.getRows()).hasSize(2);

            // huge page size is capped
            assertThat(service.findMyCases(USER_A, "", 1, 1000).getRows()).hasSize(7);
        } finally {
            for (String pid : pids) {
                deleteInstance(pid);
            }
        }
    }

    @Test
    @DisplayName("Finished cases stay visible with status Completed; running ones In Progress")
    void finishedCasesRemainListed() {
        String pid = startInstance(USER_A);
        try {
            assertThat(service.findMyCases(USER_A, PROCESS_KEY, 1, 10).getRows().get(0)
                    .getStatusKey()).isEqualTo("inprogress");

            completeAllTasks(pid);
            List<CaseRow> rows = service.findMyCases(USER_A, PROCESS_KEY, 1, 10).getRows();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getStatusKey()).isEqualTo("completed");
            assertThat(rows.get(0).getEndTime()).isNotNull();
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("Status filter: server-side segmentation into running/completed/cancelled")
    void statusFilterSegmentsTheListing() {
        String running = startInstance(USER_A);
        String completed = startInstance(USER_A);
        completeAllTasks(completed);
        String cancelled = startInstance(USER_A);
        processEngine.getRuntimeService().deleteProcessInstance(cancelled, "cancelled by user");
        try {
            // no restriction -> everything the user started
            assertThat(service.findMyCases(USER_A, "", CaseStatusFilter.ALL, 1, 10)
                    .getTotalRows()).isEqualTo(3L);

            // In Progress -> only the running instance, badge "inprogress"
            PageResult<CaseRow> inProgress =
                    service.findMyCases(USER_A, "", CaseStatusFilter.IN_PROGRESS, 1, 10);
            assertThat(inProgress.getTotalRows()).isEqualTo(1L);
            assertThat(inProgress.getRows()).extracting(CaseRow::getId).containsExactly(running);
            assertThat(inProgress.getRows().get(0).getStatusKey()).isEqualTo("inprogress");

            // Completed -> the instance that reached the end event, badge "completed"
            PageResult<CaseRow> completedPage =
                    service.findMyCases(USER_A, "", CaseStatusFilter.COMPLETED, 1, 10);
            assertThat(completedPage.getTotalRows()).isEqualTo(1L);
            assertThat(completedPage.getRows()).extracting(CaseRow::getId).containsExactly(completed);
            assertThat(completedPage.getRows().get(0).getStatusKey()).isEqualTo("completed");
            assertThat(completedPage.getRows().get(0).isCancelled()).isFalse();

            // Cancelled -> instance deleted while running keeps its delete reason
            PageResult<CaseRow> cancelledPage =
                    service.findMyCases(USER_A, "", CaseStatusFilter.CANCELLED, 1, 10);
            assertThat(cancelledPage.getTotalRows()).isEqualTo(1L);
            assertThat(cancelledPage.getRows()).extracting(CaseRow::getId).containsExactly(cancelled);
            assertThat(cancelledPage.getRows().get(0).getStatusKey()).isEqualTo("cancelled");
            assertThat(cancelledPage.getRows().get(0).isCancelled()).isTrue();
            assertThat(cancelledPage.getRows().get(0).getDeleteReason())
                    .isEqualTo("cancelled by user");

            // the cancelled case counts as neither completed nor in progress
            assertThat(service.findMyCases(USER_A, "", CaseStatusFilter.COMPLETED, 1, 10)
                    .getRows()).extracting(CaseRow::getId).doesNotContain(cancelled);
            assertThat(service.findMyCases(USER_A, "", CaseStatusFilter.IN_PROGRESS, 1, 10)
                    .getTotalRows()).isEqualTo(1L);

            // null filter behaves like ALL (defensive server-side default)
            assertThat(service.findMyCases(USER_A, "", null, 1, 10).getTotalRows())
                    .isEqualTo(3L);

            // token round-trip of the dropdown vocabulary
            assertThat(CaseStatusFilter.fromToken("cancelled"))
                    .isEqualTo(CaseStatusFilter.CANCELLED);
            assertThat(CaseStatusFilter.fromToken("junk")).isEqualTo(CaseStatusFilter.ALL);
            assertThat(CaseStatusFilter.fromToken(null)).isEqualTo(CaseStatusFilter.ALL);
        } finally {
            deleteInstance(running);
            deleteInstance(completed);
            deleteInstance(cancelled);
        }
    }

    @Test
    @DisplayName("Status filter combines with the process filter server-side")
    void statusFilterCombinesWithProcessFilter() {
        String running = startInstance(USER_A);
        try {
            assertThat(service.findMyCases(USER_A, PROCESS_KEY,
                    CaseStatusFilter.IN_PROGRESS, 1, 10).getTotalRows()).isEqualTo(1L);
            assertThat(service.findMyCases(USER_A, PROCESS_KEY,
                    CaseStatusFilter.COMPLETED, 1, 10).getTotalRows()).isZero();
            assertThat(service.findMyCases(USER_A, "noSuchProcessKey",
                    CaseStatusFilter.IN_PROGRESS, 1, 10).getTotalRows()).isZero();
        } finally {
            deleteInstance(running);
        }
    }

    @Test
    @DisplayName("Cancelled case stays listed for its initiator and sealed for others")
    void cancelledCasesSecurityAndDefaultVisibility() {
        String cancelledByB = startInstance(USER_B);
        processEngine.getRuntimeService().deleteProcessInstance(cancelledByB, "withdrawn");
        try {
            // still visible to the initiator under ALL
            assertThat(service.findMyCases(USER_B, "", CaseStatusFilter.ALL, 1, 10).getRows())
                    .extracting(CaseRow::getId).containsExactly(cancelledByB);
            // ...but never to another user, whatever status they filter by
            for (CaseStatusFilter filter : CaseStatusFilter.values()) {
                assertThat(service.findMyCases(USER_A, "", filter, 1, 10).getTotalRows())
                        .as("user A must not see B's cancelled case via filter %s", filter)
                        .isZero();
            }
        } finally {
            deleteInstance(cancelledByB);
        }
    }

    // ==================================================================
    // Cases I Approved (audit-backed section) + audit-based viewability
    // ==================================================================

    @Test
    @DisplayName("Approved cases: audit-trail ids drive the listing, pagination exact")
    void approvedCasesListedFromAuditTrail() {
        String pidA = startInstance(USER_A);
        String pidB1 = startInstance(USER_B);
        String pidB2 = startInstance(USER_B);
        try {
            // audit says alice acted on three cases - two of them started by B
            when(auditService.findCaseIdsActedByUser(ALICE))
                    .thenReturn(List.of(pidB2, pidA, pidB1));

            // page 1 of 2 -> the first two ids, audit order kept
            PageResult<CaseRow> page1 = service.findApprovedCases(ALICE, 1, 2);
            assertThat(page1.getTotalRows()).isEqualTo(3L);
            assertThat(page1.getRows()).extracting(CaseRow::getId)
                    .containsExactly(pidB2, pidA);
            // a case someone else started is listed BY DESIGN (alice acted on
            // it) and clearly shows the actual initiator
            assertThat(page1.getRows().get(0).getStartedBy()).isEqualTo(USER_B);

            PageResult<CaseRow> page2 = service.findApprovedCases(ALICE, 2, 2);
            assertThat(page2.getRows()).extracting(CaseRow::getId).containsExactly(pidB1);

            // requesting a page beyond the end clamps to the last page
            PageResult<CaseRow> clamped = service.findApprovedCases(ALICE, 99, 2);
            assertThat(clamped.getPageNumber()).isEqualTo(2);
            assertThat(clamped.getRows()).extracting(CaseRow::getId).containsExactly(pidB1);

            // a user without audit records gets an empty result, not an error
            assertThat(service.findApprovedCases("nobody", 1, 10).getTotalRows()).isZero();

            // a broken audit never yields cases
            when(auditService.findCaseIdsActedByUser("bob"))
                    .thenThrow(new IllegalStateException("audit down"));
            assertThat(service.findApprovedCases("bob", 1, 10).getTotalRows()).isZero();
        } finally {
            deleteInstance(pidA);
            deleteInstance(pidB1);
            deleteInstance(pidB2);
        }
    }

    @Test
    @DisplayName("Viewability: only a user who acted on a case may open it besides the initiator")
    void auditParticipationGrantsViewability() {
        String pidB = startInstance(USER_B);
        try {
            // no audit record -> sealed exactly as before
            assertThat(service.findCaseOverview(USER_A, ALICE, pidB)).isNull();
            assertThat(service.findTimeline(USER_A, ALICE, pidB)).isEmpty();

            // audit record for alice -> case becomes viewable, overview intact
            when(auditService.hasUserActedOnCase(ALICE, pidB)).thenReturn(true);
            CaseRow row = service.findCaseOverview(USER_A, ALICE, pidB);
            assertThat(row).isNotNull();
            assertThat(row.getId()).isEqualTo(pidB);
            assertThat(row.getStartedBy()).isEqualTo(USER_B);

            // every read-only section follows the same guard, diagram included
            assertThat(service.findActiveTasks(USER_A, ALICE, pidB)).isNotEmpty();
            assertThat(service.generateDiagramImage(USER_A, ALICE, pidB)).isNotEmpty();

            // other users are still locked out even though alice can see it
            assertThat(service.findCaseOverview(USER_B, "bob", pidB)).isNotNull(); // initiator
            assertThat(service.findCaseOverview("999", "carol", pidB)).isNull();
        } finally {
            deleteInstance(pidB);
        }
    }

    // ==================================================================
    // My Decisions tab (audit rows of the logged-in user)
    // ==================================================================

    @Test
    @DisplayName("My Decisions: only the current user's audit rows, chronological, keyed")
    void myDecisionsShowOnlyCurrentUserRows() {
        String pid = startInstance(USER_A);
        try {
            when(auditService.resolveNumericUserId(ALICE)).thenReturn(Integer.valueOf(111));
            when(auditService.findDetailsOfProcessInstance(pid)).thenReturn(List.of(
                    decisionEntry(pid, 111, BpmAuditAction.HEAD_OF_DEPARTMENT_APPROVAL,
                            LocalDateTime.now().plusMinutes(10), "ok"),
                    decisionEntry(pid, 222, BpmAuditAction.PRESIDENT_REJECTION,
                            LocalDateTime.now().plusMinutes(15), "someone else"),
                    decisionEntry(pid, 111, BpmAuditAction.TASK_RECEIVED_HEAD_OF_DEPARTMENT,
                            LocalDateTime.now().plusMinutes(5), "received")));

            List<MyDecisionRow> decisions = service.findMyDecisions(USER_A, ALICE, pid);
            // the other user's rejection never leaks into alice's tab
            assertThat(decisions).hasSize(2);
            assertThat(decisions).extracting(MyDecisionRow::getActionKey)
                    .containsExactly("received", "approved"); // oldest first
            assertThat(decisions).extracting(MyDecisionRow::getActionDescription)
                    .containsExactly("Head Of Department Task Received",
                            "Head Of Department Approval");
            assertThat(decisions).extracting(MyDecisionRow::getNote)
                    .containsExactly("received", "ok");

            // unknown numeric id -> nothing to show, never an exception
            when(auditService.resolveNumericUserId("carol")).thenReturn(null);
            assertThat(service.findMyDecisions(USER_A, "carol", pid)).isEmpty();

            // a sealed case never reveals decisions
            String pidB = startInstance(USER_B);
            try {
                assertThat(service.findMyDecisions(USER_A, ALICE, pidB)).isEmpty();
            } finally {
                deleteInstance(pidB);
            }
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("Decision keys map the audit vocabulary to stable UI keys")
    void decisionKeyVocabulary() {
        assertThat(MyCasesService.decisionKeyOf(BpmAuditAction.HEAD_OF_DEPARTMENT_APPROVAL.code()))
                .isEqualTo("approved");
        assertThat(MyCasesService.decisionKeyOf(BpmAuditAction.PRESIDENT_REJECTION.code()))
                .isEqualTo("rejected");
        assertThat(MyCasesService.decisionKeyOf(BpmAuditAction.FINANCIAL_DEPARTMENT_REVIEW.code()))
                .isEqualTo("reviewed");
        assertThat(MyCasesService.decisionKeyOf(
                        BpmAuditAction.TASK_RECEIVED_HEAD_OF_DEPARTMENT.code()))
                .isEqualTo("received");
        assertThat(MyCasesService.decisionKeyOf(BpmAuditAction.ENTERED.code()))
                .isEqualTo("other");
        assertThat(MyCasesService.decisionKeyOf(null)).isEqualTo("other");
        assertThat(MyCasesService.decisionKeyOf(Integer.MAX_VALUE)).isEqualTo("other");
    }

    // ==================================================================
    // Details: overview + active tasks + assignee information
    // ==================================================================

    @Test
    @DisplayName("Overview carries id, name, definition key/version, initiator, dates")
    void caseOverviewFields() {
        String pid = startInstance(USER_A);
        try {
            CaseRow row = service.findCaseOverview(USER_A, ALICE, pid);
            assertThat(row).isNotNull();
            assertThat(row.getId()).isEqualTo(pid);
            assertThat(row.getProcessName()).isEqualTo("My Cases Test Process");
            assertThat(row.getProcessDefinitionKey()).isEqualTo(PROCESS_KEY);
            assertThat(row.getProcessDefinitionVersion()).isEqualTo(1);
            assertThat(row.getStartedBy()).isEqualTo(USER_A);
            assertThat(row.getStartTime()).isNotNull();
            assertThat(row.getDefinitionDisplay()).isEqualTo(PROCESS_KEY + " (v1)");
            // current activity is reported for a running case
            assertThat(service.findCurrentActivityIds(USER_A, ALICE, pid))
                    .containsExactly("hodTask");
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("Active tasks: unassigned candidate-group task shows no individual assignee")
    void activeTasksUnassignedCandidateGroupTask() {
        String pid = startInstance(USER_A);
        try {
            List<CaseTaskRow> tasks = service.findActiveTasks(USER_A, ALICE, pid);
            assertThat(tasks).hasSize(1);
            CaseTaskRow task = tasks.get(0);
            assertThat(task.getName()).isEqualTo("HOD Review");
            // candidate-group task -> no individual assignee, group listed
            assertThat(task.getAssignee()).isNull();
            assertThat(task.getCandidateGroups()).isEqualTo("HOD");
            assertThat(task.getCreateTime()).isNotNull();
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("Active tasks: assignee + claim date are reported; completed tasks disappear")
    void activeTasksAssigneeAndCompletedExclusion() {
        String pid = startInstance(USER_A);
        try {
            Task hod = activeTask(pid);
            processEngine.getTaskService().claim(hod.getId(), "mohammad");
            processEngine.getTaskService().complete(hod.getId());

            List<CaseTaskRow> tasks = service.findActiveTasks(USER_A, ALICE, pid);
            // only the second task is still active - the completed HOD task is gone
            assertThat(tasks).hasSize(1);
            CaseTaskRow finance = tasks.get(0);
            assertThat(finance.getName()).isEqualTo("Finance Review");
            assertThat(finance.getCandidateGroups()).isEqualTo("FIN");
            assertThat(finance.getAssignee()).isNull();

            // claim it -> assignee + assignment date appear
            Task financeTask = activeTask(pid);
            processEngine.getTaskService().claim(financeTask.getId(), "ahmad");
            List<CaseTaskRow> claimed = service.findActiveTasks(USER_A, ALICE, pid);
            assertThat(claimed).hasSize(1);
            assertThat(claimed.get(0).getAssignee()).isEqualTo("ahmad");
            assertThat(claimed.get(0).getClaimTime()).isNotNull();
        } finally {
            deleteInstance(pid);
        }
    }

    // ==================================================================
    // Variables
    // ==================================================================

    @Test
    @DisplayName("Variables: readable table of name/value/type, internal _ vars hidden")
    void variablesRenderedAndInternalOnesHidden() {
        String pid = startInstance(USER_A);
        try {
            processEngine.getRuntimeService().setVariable(pid, "facultyNo", "123");
            processEngine.getRuntimeService().setVariable(pid, "deptNo", 45);
            processEngine.getRuntimeService().setVariable(pid, "active", true);
            processEngine.getRuntimeService().setVariable(pid, "_SECRET_INTERNAL", "nope");

            List<CaseVariableRow> vars = service.findVariables(USER_A, ALICE, pid);
            assertThat(vars).extracting(CaseVariableRow::getName)
                    .containsExactly("active", "deptNo", "facultyNo") // sorted, internal hidden
                    .doesNotContain("_SECRET_INTERNAL");
            assertThat(vars).extracting(CaseVariableRow::getValue)
                    .containsExactly("true", "45", "123");
            assertThat(vars).extracting(CaseVariableRow::getType)
                    .containsExactly("boolean", "integer", "string");
        } finally {
            deleteInstance(pid);
        }
    }

    // ==================================================================
    // Diagram (read-only)
    // ==================================================================

    @Test
    @DisplayName("Diagram: PNG of the owning user's case renders with active highlight")
    void diagramRendersForTheOwner() {
        String pid = startInstance(USER_A);
        try {
            byte[] png = service.generateDiagramImage(USER_A, ALICE, pid);
            assertThat(png).isNotNull().isNotEmpty();
            // PNG magic number
            assertThat(png[0]).isEqualTo((byte) 0x89);
            assertThat(png[1]).isEqualTo((byte) 'P');
            assertThat(png[2]).isEqualTo((byte) 'N');
            assertThat(png[3]).isEqualTo((byte) 'G');
        } finally {
            deleteInstance(pid);
        }
    }

    // ==================================================================
    // Timeline
    // ==================================================================

    @Test
    @DisplayName("Timeline: engine + audit events merged in chronological order")
    void timelineIsChronologicalAndIncludesAudit() {
        String pid = startInstance(USER_A);
        try {
            Task hod = activeTask(pid);
            processEngine.getTaskService().claim(hod.getId(), "mohammad");
            processEngine.getTaskService().complete(hod.getId());

            // the existing BPM audit service contributes a business event
            when(auditService.findDetailsOfProcessInstance(pid))
                    .thenReturn(List.of(auditEntry(pid, LocalDateTime.now().plusMinutes(5))));

            List<TimelineEventRow> timeline = service.findTimeline(USER_A, ALICE, pid);
            assertThat(timeline).isNotEmpty();

            // chronological (ascending by timestamp)
            for (int i = 1; i < timeline.size(); i++) {
                assertThat(timeline.get(i).getTimestamp())
                        .isAfterOrEqualTo(timeline.get(i - 1).getTimestamp());
            }

            // starts with the process-start event, ends with the completed task
            assertThat(timeline.get(0).getTypeKey()).isEqualTo("started");
            assertThat(timeline.get(0).getActor()).isEqualTo(USER_A);
            assertThat(timeline).extracting(TimelineEventRow::getTypeKey)
                    .contains("taskCreated", "taskCompleted", "audit");

            // audit row carries the action description of the existing enum
            TimelineEventRow auditRow = timeline.stream()
                    .filter(e -> "audit".equals(e.getTypeKey())).findFirst().orElseThrow();
            assertThat(auditRow.getTitle())
                    .isEqualTo(BpmAuditAction.HEAD_OF_DEPARTMENT_APPROVAL.defaultDescription());
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("Timeline: a broken audit service never breaks the tab")
    void timelineSurvivesAuditFailure() {
        String pid = startInstance(USER_A);
        try {
            when(auditService.findDetailsOfProcessInstance(anyString()))
                    .thenThrow(new IllegalStateException("audit down"));
            List<TimelineEventRow> timeline = service.findTimeline(USER_A, ALICE, pid);
            assertThat(timeline).isNotEmpty(); // engine events still present
            verify(auditService).findDetailsOfProcessInstance(pid);
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("Audit service is only queried for an owned/acted-on case")
    void auditOnlyQueriedForOwnedCases() {
        String pidB = startInstance(USER_B);
        try {
            service.findTimeline(USER_A, ALICE, pidB);
            verify(auditService, never()).findDetailsOfProcessInstance(anyString());
        } finally {
            deleteInstance(pidB);
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private String startInstance(String starterUserId) {
        try {
            Authentication.setAuthenticatedUserId(starterUserId);
            ProcessInstance instance = processEngine.getRuntimeService()
                    .startProcessInstanceByKey(PROCESS_KEY);
            return instance.getId();
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    private Task activeTask(String pid) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .active()
                .singleResult();
    }

    private void completeAllTasks(String pid) {
        for (Task task = activeTask(pid); task != null; task = activeTask(pid)) {
            processEngine.getTaskService().complete(task.getId());
        }
    }

    private void deleteInstance(String pid) {
        if (processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(pid).count() > 0) {
            processEngine.getRuntimeService().deleteProcessInstance(pid, "test cleanup");
        }
        // purge the history so counts stay exact across tests
        processEngine.getHistoryService().deleteHistoricProcessInstance(pid);
    }

    private static BpmAuditLogDtl auditEntry(String caseId, LocalDateTime entryDate) {
        BpmAuditLogDtl detail = new BpmAuditLogDtl();
        detail.setCaseId(caseId);
        detail.setActionCode(BpmAuditAction.HEAD_OF_DEPARTMENT_APPROVAL.code());
        detail.setNote("Task completed by mohammad");
        detail.setEntryDate(entryDate);
        return detail;
    }

    private static BpmAuditLogDtl decisionEntry(String caseId, int entryUser,
                                                BpmAuditAction action,
                                                LocalDateTime entryDate, String note) {
        BpmAuditLogDtl detail = new BpmAuditLogDtl();
        detail.setCaseId(caseId);
        detail.setEntryUser(entryUser);
        detail.setActionCode(action.code());
        detail.setEntryDate(entryDate);
        detail.setNote(note);
        return detail;
    }
}