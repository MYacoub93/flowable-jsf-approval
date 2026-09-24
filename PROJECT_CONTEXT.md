# Workflow Station — Project Context

> Generated from the uploaded repository snapshot on 2026-09-15.
> This file is intended to be the persistent context for an AI coding agent.
> It should be read before making changes to the project.
>
> **Important:** This is a context/architecture document, not the ultimate source of truth.
> Source code, BPMN XML, tests, and actual runtime behavior take precedence over this file.

## 1. Project identity

- Application: **Workflow Station**
- Language: **Java 17**
- Build: **Maven**
- Spring Boot: **3.3.5**
- Flowable: **7.1.0 Community Edition**
- JSF/Jakarta Faces: **Jakarta Faces 4** via JoinFaces **5.3.3**
- PrimeFaces: **14.0.6**, `jakarta` classifier
- MyBatis Spring Boot: **3.0.3**
- Primary workflow DB: **MySQL**
- External SIS/user/audit DB: **Oracle**
- ORM/data access: MyBatis for application/SIS queries; Flowable owns its process-engine persistence
- UI: JSF/Facelets under `src/main/resources/META-INF/resources`
- Application packaging: Spring Boot executable JAR
- Main package: `com.example.approval`

## 2. High-level architecture

The application is layered roughly as:

```text
JSF / PrimeFaces pages
        |
JSF backing beans
        |
process-specific services / shared services
        |
Flowable Engine + MyBatis mappers + external integrations
        |
MySQL Flowable DB / Oracle SIS + BPM audit / Alfresco UCM
```

Important architectural rules:

1. Use constructor injection for Spring services.
2. Keep business/process logic in services or Flowable handlers, not in XHTML.
3. Keep JSF backing beans thin; they orchestrate UI state and call services.
4. Process-specific behavior belongs under `processes/<process-name>/`.
5. Reusable cross-process functionality belongs in shared packages such as:
   - `service`
   - `notification`
   - `audit`
   - `flowable`
   - `ucm`
   - `processadmin`
6. MyBatis SQL belongs in `src/main/resources/mapper/*.xml`.
7. Do not create a second Flowable `ProcessEngine`; use the Spring-managed engine/services.
8. Do not duplicate process rules in Java and BPMN unless there is a deliberate reason and tests protect both.
9. Prefer Flowable process variables for routing/state rather than hard-coded user-specific sequence flows.
10. Never make an external integration failure (email/SIS lookup) unexpectedly roll back the workflow unless that is explicitly a business requirement.

## 3. Datasources

There are two datasources.

### Primary datasource

`spring.datasource` -> MySQL.

This is the primary/default datasource and is used by Flowable.

Current configuration in the uploaded snapshot points to a local MySQL database named `approvaldb` on port `3307`.

### External datasource

`app.datasource.external` -> Oracle.

It is used for SIS/user information and other external business data through MyBatis.

Key Oracle view:

```text
FLOWABLE_USERS_VW
```

This view is the source of Flowable users/groups for the custom identity layer.

There is also an `externalTransactionManager` for operations that must be transactional against Oracle.

### Security rule for configuration

The uploaded `application.yml` contained credentials. **Never copy, expose, hard-code, or repeat those credentials in generated code, prompts, documentation, logs, or commits.**

Use environment variables / deployment configuration for secrets.

## 4. Flowable identity architecture

Flowable identity is customized.

Key classes:

```text
flowable/config/FlowableIdmConfig.java
flowable/identity/CustomUserEntityManager.java
flowable/identity/CustomGroupEntityManager.java
mapper/FlowableIdentityMapper.java
```

`FlowableIdmConfig` replaces the default Flowable user/group entity managers with custom managers backed by the external SIS view.

### Users

`CustomUserEntityManager` reads users through `FlowableIdentityMapper`.

The login query is:

```text
findUserByUsernameForAuth(username)
```

The authentication row can include:

- username
- password
- email
- first/last name
- `DEFAULT_ROLE_`
- `ROLE_CODE_`

`DEFAULT_ROLE_` is informational/business-role input and is used by the login flow to decide which SIS profile to load.

### Groups

Groups are **not provisioned/stored in Flowable as the source of truth**.

`CustomGroupEntityManager` reads groups from:

```text
FLOWABLE_USERS_VW
```

The group id is based on:

```text
ROLE_CODE_
```

Important supported queries include:

```text
groupMember(user)
groupMember(user).groupId(group)
groupId(group)
```

Group/user creation through Flowable identity persistence is intentionally unsupported because SIS is the provisioning authority.

### Clearance group requirement

For Clearance to work, the SIS view must contain the appropriate role codes/groups, including the configured department groups plus:

```text
STD
Finance Department / corresponding configured finance group
Admission and Registration Department / corresponding configured admission group
Internal Audit Department / corresponding configured audit group
```

Use the actual constants/BPMN in the repository as the source of truth if names differ from documentation.

## 5. Authentication + JSF session architecture

Important classes:

```text
backing/UserLoginBean.java
backing/SessionInfoBean.java
backing/BaseBackingBean.java
config/SpringCdiBridge.java
config/WebConfig.java
```

### CDI vs Spring backing beans

`UserLoginBean` is CDI-managed:

```java
@Named("loginBean")
@SessionScoped
```

This is intentional.

Other Spring-managed backing beans can access the same login/session state through the application's bridge/facade mechanism.

Do not casually convert the login bean to Spring `@SessionScope` without understanding the JSF/CDI lifecycle implications.

### SessionInfoBean

`SessionInfoBean` is the central session snapshot for:

- logged-in user identity
- display name/email
- login timestamp
- locale
- role/profile information
- student/staff profile

Other backing beans should use the established session mechanism rather than each implementing their own user/session state.

### Localization

UI labels come from:

```text
src/main/resources/labels.properties
src/main/resources/labels_ar.properties
```

The application supports English/Arabic UI localization.

Use the existing `BaseBackingBean#getLabel(...)` approach for server-side user-facing messages.

Do not introduce hard-coded English-only UI strings when an existing label key should be used.

## 6. Shared process-start architecture

`ProcessStartService` is process-agnostic.

Responsibilities:

- set Flowable authenticated user id
- start process by definition key
- add `initiator` if missing
- use a process business key
- open the BPM audit case after the instance starts
- clear authenticated user id in `finally`

Per-process start forms should use process-specific contracts, then call the shared start service.

The business audit `CASE_ID` is the **Flowable process instance id**. It is not generated independently.

## 7. Clearance Letter process

BPMN:

```text
src/main/resources/processes/clearance-letter-process.bpmn20.xml
```

Key Java package:

```text
com.example.approval.processes.clearance
```

Main components:

```text
ClearanceConstants
ClearanceProperties
ClearanceRequestContract

model/DepartmentDecision

service/DepartmentResolverService
service/ClearanceApproverResolverService
service/ClearanceService
service/impl/ConfigurableDepartmentResolverService

flowable/ClearanceProcessHandler
flowable/ClearanceTaskListener

backing/StartClearanceBean
backing/ClearanceTaskBean
```

### Clearance business flow

Conceptually:

```text
STD starts request
      |
      v
Resolve required departments
      |
      v
Parallel department approval tasks
      |
      +---- rejection recorded
      |
      v
Synchronization barrier
      |
      +---- rejected -> amendment task for initiator
      |                     |
      |                     v
      |                resolve departments again
      |
      +---- all approved
              |
              v
        Finance approval
              |
              +---- reject -> amendment
              |
              v
        Admission/Registration approval
              |
              +---- reject -> amendment
              |
              v
        Finalize completion
              |
              +---- initiator result task
              |
              +---- FYI Internal Audit task
              |
              v
             End
```

### Critical current rule: department MI is a synchronization barrier

The **current BPMN in the uploaded repository** contains:

```xml
<completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
```

This means:

- Department approvals run in parallel.
- If one department rejects, the remaining department tasks are **not immediately cancelled**.
- The process waits until every department task has been submitted.
- Only after all departments submit is the rejection outcome evaluated.
- If any department rejected, the process goes to the amendment task.
- If all approved, the process proceeds to Finance.

This is a critical regression-sensitive rule.

There is a stale/inconsistent description in older documentation/comments that describes `anyDepartmentRejected` as the MI completion condition. **Do not restore that behavior. The BPMN and synchronization tests are the current authority.**

Relevant regression test:

```text
src/test/java/com/example/approval/clearance/flowable/ClearanceProcessSynchronizationTest.java
```

### Clearance variables

Important variables include:

```text
initiator
requestId
requiredDepartments
department
departmentDecisions
approvedDepartments
approvalRound
decision
comment
anyDepartmentRejected
lastRejectedStage
lastRejectedDepartment
financeApproved
admissionApproved
clearanceResult
```

Not every variable is necessarily present on every process instance.

### Department resolution

`ConfigurableDepartmentResolverService` uses `ClearanceProperties`.

The current default configuration is `ALL`, with the configured department list.

The resolver is called again after amendment/resubmission. Do not hard-code the department routing into the BPMN.

### Resubmission rule

Approved departments must be remembered **across approval rounds**.

The current design uses cumulative state such as:

```text
approvedDepartments
```

because per-round decision maps are reset.

On resubmission, already-approved departments should not unnecessarily receive another approval task.

The resolve process should subtract previously approved departments from the newly resolved required set.

This behavior is covered by:

```text
ClearanceProcessResubmissionTest.java
```

Do not replace cumulative state with a per-round-only map.

### Clearance listeners

`ClearanceTaskListener` handles task lifecycle events.

Conceptually:

- `create`
  - send notification
  - write task-assignment audit
- `assignment`
  - notify the individual claimer
- `complete`
  - store approval/rejection decision
  - write approval/rejection audit
- `delete`
  - audit cancellation when applicable

Be careful with Flowable 7 APIs. Older Flowable examples may refer to APIs removed in Flowable 7.

The project documentation specifically notes that `DelegateTask#getExecution()` and `DelegateTask#getCandidateGroups()` are not available in the expected Flowable 7 usage. Use supported task variables / known element variables and current engine APIs.

## 8. Notification architecture

Package:

```text
com.example.approval.notification
```

Main classes:

```text
NotificationProperties
model/NotificationMessage
model/GroupEmailResolution
service/NotificationService
service/NotificationRecipientResolver
service/impl/EmailNotificationService
```

### Critical recipient rule

**Never use a shared group mailbox for workflow task notifications.**

For an unclaimed candidate-group task:

```text
Group
  -> FLOWABLE_USERS_VW members
  -> each member's personal email
  -> deduplicate/validate
  -> send to all valid members
```

For a claimed task:

```text
assignee username
  -> FLOWABLE_USERS_VW
  -> that user's personal email only
```

For an initiator notification:

```text
initiator username
  -> FLOWABLE_USERS_VW
  -> initiator email
```

Static configured user mailbox/domain fallbacks are for **individual users only**. They are not a group-mailbox fallback.

### Recipient group ids are SIS role codes (2026-09)

A notification candidateGroup MUST be the SIS group id (ROLE_CODE_ in FLOWABLE_USERS_VW), never a display name like Finance Department: a display name matches no ROLE_CODE_, the group resolves to zero members and the e-mail is silently skipped (WARN log only). The Clearance BPMN already uses FIN / REG as flowable:candidateGroups; ClearanceTaskListener#candidateGroupOf maps the Finance / Admission stages back to those role codes while keeping the display name in department for subject/audit. Amendment notifications target the initiator via recipientUser (personal address lookup), never a group lookup.

### Notification failure behavior

Email/SIS failures should normally be logged and should not roll back the Flowable workflow transaction.

The existing notification resolver also:

- handles null/empty groups
- deduplicates usernames
- validates email addresses
- deduplicates email addresses
- logs resolution counts
- skips users without valid addresses

Relevant test:

```text
src/test/java/com/example/approval/notification/NotificationRecipientResolverTest.java
```

## 9. Audit architecture

Package:

```text
com.example.approval.audit
```

Key classes:

```text
BpmAuditService
BpmAuditServiceImpl
BpmAuditIdAllocator
AttachmentAuditService
BpmAuditRestController
BpmAuditAction
```

Oracle audit tables are externally owned:

```text
F_BPM_AUDIT_LOG
F_BPM_AUDIT_LOG_DTL
F_BPM_CASE_ATTACHMENTS
```

Important rule:

```text
CASE_ID == Flowable process instance id
```

The application does not generate an independent case id.

The serial columns for audit details/attachments have configurable allocation strategy.

Do not assume the MySQL `schema.sql` owns the Oracle audit schema; the shipped `schema.sql` is primarily explanatory/reference content.

## 10. Student Proof Certificate process

BPMN:

```text
src/main/resources/processes/student-proof-certificate-process.bpmn20.xml
```

Package:

```text
com.example.approval.processes.studentproof
```

Flow:

```text
STD starts
   |
   v
Admission/Registration processing task
   |
   | mandatory note + document upload
   | document archived to Alfresco/UCM
   v
Student document/result task
   |
   v
End
```

Important classes:

```text
StudentProofProcessHandler
StudentProofTaskListener
StudentProofService
StartStudentProofBean
StudentProofTaskBean
StudentProofConstants
```

The Admission/Registration processing task uses group `REG` in the current BPMN.

The final task is assigned to `${initiator}`.

Alfresco/UCM integration is implemented through the reusable:

```text
com.example.approval.ucm.service.AlfrescoUcmService
```

using Apache Chemistry OpenCMIS.

The current tests include:

```text
StudentProofTaskListenerTest
AlfrescoUcmServiceLinkTest
```

## 11. My Cases

Package:

```text
com.example.approval.mycases
```

Key classes:

```text
MyCasesService
CaseRow
CaseTaskRow
CaseVariableRow
MyDecisionRow
TimelineEventRow
CaseStatusFilter
MyCasesDiagramController
```

UI:

```text
src/main/resources/META-INF/resources/my-cases.xhtml
```

Recent work added status filtering and security-oriented engine tests.

Relevant test:

```text
src/test/java/com/example/approval/mycases/MyCasesEngineTest.java
```

When changing My Cases:

- preserve user/case visibility boundaries
- do not leak another user's cases
- distinguish active/running vs completed/history states correctly
- keep the status filter behavior covered by tests
- prefer Flowable engine/history queries over brittle UI-side inference

## 12. Administration / Process Management

Package:

```text
com.example.approval.processadmin
```

Key classes:

```text
ProcessDeploymentService
ProcessDefinitionRow
```

UI:

```text
process-management.xhtml
```

Features currently implemented:

- list deployed process definitions
- deploy BPMN uploads
- duplicate filtering for byte-identical deployments
- enable/suspend one process definition
- disable/suspend one process definition
- bulk enable
- bulk disable
- multi-selection
- single undeploy
- bulk undeploy
- protection/confirmation when running instances exist
- optional cascade undeploy
- server-side ADM authorization
- logging of administration actions

Admin group:

```text
ADM
```

The UI visibility is not the security boundary. The service re-checks authorization.

Do not bypass `ProcessDeploymentService` by manipulating Flowable repository state directly from JSF.

Relevant test:

```text
src/test/java/com/example/approval/processadmin/ProcessDeploymentServiceTest.java
```

## 13. Other major reusable areas

### Delegation

```text
delegation/TaskDelegationService
delegation/TaskDelegationBean
```

Tests:

```text
TaskDelegationServiceTest
TaskDelegationEngineTest
```

### Common SIS access

```text
service/CommonService
mapper/CommonMapper.xml
origin/beans/*
```

### External groups

```text
service/ExternalGroupService
mapper/ExternalGroupMapper.xml
backing/ExternalGroupCreateBean
backing/GroupMembershipBean
```

### Dashboard / task querying

```text
service/ApprovalService
backing/DashboardBean
```

`ApprovalService#getTasksForUser` combines:

- directly assigned tasks
- candidate-group tasks for groups the user belongs to

Group membership comes from the custom Flowable identity layer.

## 14. UI files

Current Facelets include:

```text
login.xhtml
dashboard.xhtml
processes.xhtml
my-cases.xhtml
process-management.xhtml
start-clearance.xhtml
clearance-task.xhtml
start-student-proof.xhtml
student-proof-task.xhtml
settings.xhtml
users_managment.xhtml
task-delegation.xhtml
external-group-create.xhtml
group-memberships.xhtml
clear-data.xhtml
```

Shared templates:

```text
templates/portal.xhtml
templates/side-menu.xhtml
templates/footer.xhtml
```

Static CSS:

```text
css/portal.css
```

## 15. Testing strategy

The repository contains unit and engine-level tests covering:

```text
audit
login/student info
clearance resubmission
clearance synchronization/barrier
clearance approver resolution
clearance student info
delegation
My Cases
notifications
process administration
student proof
external groups
UCM
```

Important test philosophy:

- For Flowable behavior, prefer a real engine-level H2 test where practical.
- For external Oracle/SIS integrations, mock the mapper/service when testing pure resolution logic.
- Add a regression test for every business-rule bug that is fixed.
- Do not claim tests pass unless they were actually executed in the current environment.

The uploaded repository could not be executed here because Maven (`mvn`) was not installed in the inspection environment. Therefore, this snapshot was **inspected statically; tests were not run by this review**.

## 16. Known repository/documentation discrepancy

The current BPMN and synchronization test explicitly require:

```text
${nrOfCompletedInstances == nrOfInstances}
```

for the department multi-instance completion condition.

Some older documentation describes rejection as immediately completing/cancelling the MI.

Treat the current BPMN + `ClearanceProcessSynchronizationTest` as authoritative.

This is exactly the kind of subtle regression that an AI coding agent must not "fix" back to the older behavior.

## 17. Agent operating rules

Before modifying code:

1. Read this file.
2. Inspect the relevant current source/BPMN/test files.
3. Do not assume README/docs are newer than code/tests.
4. Identify the business rule being changed.
5. Find existing tests related to that rule.
6. Make the smallest coherent change.
7. Add/update regression tests.
8. Run the relevant tests, then the full suite when practical.
9. Review the diff for accidental architecture changes.
10. Update this context file if the architecture, business rules, versions, or important implementation decisions changed.

### Do not:

- invent classes that already exist
- duplicate shared services
- create another Flowable engine
- replace MyBatis with a new ORM without explicit approval
- move JSF/CDI/Spring lifecycle code casually
- hard-code SIS users/groups/emails
- introduce a group mailbox for workflow notifications
- expose secrets in source, prompts, logs, tests, or documentation
- "simplify" Flowable 7 APIs using obsolete Flowable 6 examples
- alter a BPMN business rule without checking its engine-level tests
- declare success without actually building/testing

### Source-of-truth priority

When sources conflict, generally use:

```text
1. Current Java/BPMN implementation
2. Current automated tests
3. Current application configuration
4. Current architecture/context documentation
5. Older README/docs/comments
```

If behavior is ambiguous, stop and explain the ambiguity rather than silently choosing a behavior that can change business semantics.

## 18. Change log / context maintenance

The coding agent should append concise entries here when it makes durable architectural or business-rule changes:

```text
### YYYY-MM-DD — <change>
- What changed:
- Why:
- Important files:
- Tests:
- New constraints:
```

Keep this file concise enough that an agent can read it at the start of every task.

### 2026-09-15 - Fixed missing Clearance notifications (Finance / Admission & Registration / amendment)
- What changed: ClearanceTaskListener now (1) maps the Finance and Admission & Registration stages to their SIS role codes (FIN, REG - new constants in ClearanceConstants) as notification candidate groups and (2) e-mails the initiator (recipientUser, no group/department) when the amendment task is created; previously the amendment task sent no notification at all.
- Why (root cause): those stages carried the department display name (Finance Department / Admission and Registration Department) as the notification candidate group; FLOWABLE_USERS_VW resolves members only by ROLE_CODE_, so the group resolved to zero members and EmailNotificationService silently skipped the mail (WARN only). The amendment task was explicitly excluded from create notifications, so initiators never heard about rejections.
- Important files: processes/clearance/ClearanceConstants.java, processes/clearance/flowable/ClearanceTaskListener.java.
- Tests: clearance/flowable/ClearanceNotificationEngineTest.java (7 engine tests: FIN/REG targeting, initiator amendment mail, resubmission re-notify rules), notification/EmailNotificationRecipientChainTest.java (9 chain tests: FIN/REG to all valid member addresses, dedup, no mailbox on empty group, assignee-only, initiator, users without e-mail, SIS/SMTP failure isolation). Full suite: 177 tests, 0 failures.
- New constraints: notification recipient groups for Finance/Admission are the role codes FIN/REG (already the BPMN candidateGroups); never pass display names as notification groups. EmailNotificationService resolves JavaMailSender once via ObjectProvider#getIfAvailable() in its constructor. No BPMN/business-routing change: cumulative approvedDepartments and the nrOfCompletedInstances == nrOfInstances barrier are untouched.

### 2026-09-15 - Post-Admission submission extension hook in ClearanceTaskListener
- What changed: onCompleted now ends with `if (STAGE_ADMISSION_AND_REGISTRATION.equals(stage)) handleAdmissionSubmission(task);` calling a new empty protected placeholder method - a pure extension point for future post-Admission processing (e.g. downstream SIS integration).
- Why: the Admission & Registration completion is the final departmental submission of the Clearance flow; the hook runs only after decision + audit are safely recorded, and re-fires on every later round that reaches Admission again after amendment/resubmission. Any decision (approve/reject) counts.
- Important files: processes/clearance/flowable/ClearanceTaskListener.java.
- Tests: clearance/flowable/ClearanceAdmissionSubmissionHookTest.java (4 real-engine tests: fires once after Admission submission incl. variables; does NOT fire on Finance/department completion, task creation or assignment; fires again in a second round after amendment).
- New constraints: keep the invocation at the very end of onCompleted; do not move completion/audit logic into the hook; the method is protected so engine tests can subclass and observe it.

### 2026-09-15 - Asynchronous e-mail dispatch (durable architecture rule)
- Architecture: ALL workflow e-mail is asynchronous. Chain: Flowable task event -> ClearanceTaskListener -> EmailNotificationService (resolves recipients via NotificationRecipientResolver and builds subject/body on the Flowable thread) -> EmailDispatchRequest (immutable payload: processInstanceId, taskId, type, recipients, subject, body) -> AsyncEmailDispatcher.dispatch() annotated `@Async("emailNotificationExecutor")` -> JavaMailSender/SMTP on a dedicated thread. The Flowable/JSF request thread never waits for SMTP.
- Executor: one centrally configured `ThreadPoolTaskExecutor` bean `emailNotificationExecutor` in notification/config/EmailNotificationAsyncConfig (@EnableAsync), properties bound via EmailNotificationAsyncProperties from `app.notification.email.async.*` (core-pool-size 2, max-pool-size 4, queue-capacity 200, thread-name-prefix `email-`, keep-alive 60s). Bounded queue + Abort policy: EmailNotificationService catches the TaskRejectedException and logs ERROR with identifiers (no rethrow, no rollback). Spring manages executor shutdown; never add per-caller executors or `new Thread`.
- Async boundary rules: recipient resolution (FLOWABLE_USERS_VW / SIS state) happens BEFORE the boundary; the async method receives only self-contained value parameters (no DelegateTask, no entities, no FacesContext/CDI/session/request/Flowable-auth state, no transaction-bound proxies). AsyncEmailDispatcher is a separate bean from EmailNotificationService so the @Async proxy is never bypassed by self-invocation.
- Error isolation: SMTP failures (and scheduling rejections) are caught and logged inside the dispatcher/service with processInstanceId, taskId, notification type, recipient count and subject; they never propagate into the Flowable transaction, never fail task completion and never roll back the workflow. Do not log passwords/credentials/mail content.
- Thread safety: EmailNotificationService and AsyncEmailDispatcher are stateless singletons; every invocation carries its own immutable EmailDispatchRequest (recipients copied into an unmodifiable list).
- Files: notification/config/EmailNotificationAsyncConfig.java, notification/config/EmailNotificationAsyncProperties.java, notification/model/EmailDispatchRequest.java, notification/service/AsyncEmailDispatcher.java, notification/service/impl/EmailNotificationService.java, application.yml (app.notification.email.async).
- Tests: notification/AsyncEmailDispatcherTest (7), notification/EmailNotificationAsyncIntegrationTest (7: not-synchronous, runs on `email-` thread, caller-not-blocked via CountDownLatch, SMTP failure isolation, concurrency with per-mail payload correctness, bounded-queue rejection logged not thrown, FIN group path), clearance/flowable/ClearanceAsyncEmailIsolationEngineTest (3 real-engine regression: Flowable completion succeeds while SMTP fails/succeeds independently, FIN/REG/IT group mails scheduled async). Full suite after change: 198 tests, 0 failures, 0 errors.
- Unchanged on purpose: recipient resolution rules (group -> FLOWABLE_USERS_VW members -> personal e-mails, dedup, invalid/empty filtering, assignee/initiator individual mail, NO group mailbox), all subjects/bodies, all triggers, BPMN routing, approvedDepartments, amendment behavior, department synchronization barrier, Finance/REG routing, audit behavior.
### 2026-09-19 - New Semester Withdrawal process (semester-withdrawl)
- What changed: new student-initiated Flowable process `semester-withdrawl` (exact spelling). STD-only start (BPMN `flowable:candidateStarterGroups=STD` + SemesterWithdrawalService identity check). Flow: REG approval -> dynamic parallel approvals (dean DEN resolved via CommonMapper.getDeanOfCollage and ASSIGNED individually to that one Flowable user; STD_AFF; LIB; HC; + Housing only for female students) -> parallel MI SYNCHRONIZATION BARRIER `${nrOfCompletedInstances == nrOfInstances}` (a rejection never cancels siblings; the STD Amendment task exists only after EVERY applicable task finished) -> any reject -> STD amendment -> loop back with cumulative `approvedParties` (already-approved parties are never re-routed, Clearance pattern); all approved -> FIN approval -> final REG FYI -> `bpm_pkg.withdrawal_student_semester` (result==1 -> student success task; otherwise audited TRANSACTION_SERVICE_FAILURE + standalone failure-notice task for the student, never a false success).
- Presubmit gate: `BPM_PKG.check_presubmit_bpm_service` runs in SemesterWithdrawalService.submit() BEFORE the instance is created; status==0 shows the returned msg and NO process is started (documentCode configured in application.yml via WithdrawalProperties - not hard-coded).
- Important files: processes/withdrawal/SemesterWithdrawalConstants.java, flowable/WithdrawalProcessHandler.java (resolveApprovers / evaluateParallelStage / recordStageRejection / recordAmendment / executeWithdrawal / completeWithdrawalFailure), flowable/WithdrawalTaskListener.java (create/complete/delete: TASK_ASSIGNED/APPROVED/REJECTED/TASK_CANCELLED audit + async NotificationService mails incl. dean-assignee, amendment and result mails to initiator), service/WithdrawalApproverResolverService.java, service/SemesterWithdrawalService.java, backing/StartSemesterWithdrawalBean.java + WithdrawalTaskBean.java, resources/processes/semester-withdrawl-process.bpmn20.xml, start-semester-withdrawl.xhtml + semester-withdrawl-task.xhtml, labels*.properties (withdrawal.* EN/AR).
- Mapper changes: CommonMapper.xml/java gained getTransactionReasons (ReasonsBean, sis_reasons reason_type=2), getTransactionSemester (SemesterBean), checkBpmPresumbitService (CALLABLE, msg/status OUT), processSemesterWithdrawal (CALLABLE, result/message OUT); CommonService wrappers; audit gained ACTION_TASK_CLAIMED (CLAIMED wired in BpmAuditServiceImpl).
- Variables: studentId/studentName/gpa/currentSemester/gender/facultyNo/campusNo (SIS snapshot, read-only), withdrawalReason(+desc)/withdrawalSemester(+desc)/studentNote, approvalResults (party -> WithdrawalApprovalResult: decision/comment/completedBy), approvedParties, approvalRound, anyApprovalRejected, lastRejectedStage/Party/Comment, regDecision/finDecision(+comments), withdrawalResult/withdrawalMessage.
- Tests: processes/withdrawal/flowable/SemesterWithdrawalProcessEngineTest (11 real-engine H2 tests: female=5/male=4 parallel tasks, dean individual assignment, barrier - amendment only after ALL tasks finish, REG/FIN rejection -> amendment, resubmission skips approved parties, FIN -> REG FYI -> SIS call params/result routing, failed result no false success, audit lifecycle rows, async notifications per recipient, BPMN key/barrier guards) + SemesterWithdrawalServiceTest (11: presubmit 0/1 gate, STD-only start, snapshot vars, CLAIMED audit). Full suite: 220 tests, 0 failures.
- New constraints: process key stays exactly `semester-withdrawl`; presubmit validation ALWAYS precedes instance creation; never short-circuit the parallel barrier on first rejection; dean task individually assigned while keeping candidate group DEN; Housing only for female gender; notifications only through the async NotificationService chain.
### 2026-09-23 - Centralized process document codes in processes/BPM_constants
- What changed: new shared constants class `com.example.approval.processes.BPM_constants` (final class + private constructor, same convention as other constants classes) centralizing the SIS presubmit document codes passed as `documentCode` to `BPM_PKG.check_presubmit_bpm_service`. First entry: `Withdrawl_Process_DOCUMENT_CODE = 21` (int; the value formerly hard-coded in `SemesterWithdrawalService.PRESUBMIT_DOCUMENT_CODE`). The local constant is now an alias to `BPM_constants.Withdrawl_Process_DOCUMENT_CODE` - same aliasing convention already used for `BpmAuditConstants` - so callers/tests referencing `SemesterWithdrawalService.PRESUBMIT_DOCUMENT_CODE` keep compiling unchanged.
- Why: each process was carrying its own hard-coded document code; centralization prevents drift between the process implementations. Names deliberately follow the requested `<ProcessTitle>_DOCUMENT_CODE` pattern with the intentionally preserved misspelling `Withdrawl` (matching the process key `semester-withdrawl`).
- Scope: `semester-withdrawl` is currently the ONLY process that owns a presubmit document code; `clearance` and `student-proof` do not presubmit at all, and their audit `BPM_DOCUMENTS.DOCUMENT_CODE` values remain yml-configured per process definition key (`bpm.audit.document-codes`, `BpmAuditProperties`) - untouched, must not be duplicated in `BPM_constants`.
- Files: processes/BPM_constants.java (new), processes/withdrawal/service/SemesterWithdrawalService.java (alias only).
- Tests: full suite 220 tests, 0 failures, 0 errors (incl. SemesterWithdrawalServiceTest 11/11 presubmit gate checks unchanged).
- New constraints: process presubmit document codes live in `BPM_constants`; do not re-introduce hard-coded document codes in process services. Audit document-code mapping stays in application.yml.
