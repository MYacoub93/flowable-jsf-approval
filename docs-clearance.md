# Clearance Letter Process

A second process, **Clearance Letter** (`clearance-letter-process.bpmn20.xml`),
demonstrates the full pattern set required for a university student clearance
workflow: dynamic parallel department approvals, rejection loops back to the
initiator, sequential final stages, notifications, and a full audit trail.

## BPMN Design

```
Start (candidate group: STD)
  |
  v
Resolve Required Departments   (service task -> DepartmentResolverService)
  |
  v
Department Approval            (multi-instance PARALLEL user task)
  |                              one instance per department, candidateGroups = ${department}
  |                              completionCondition: ${anyDepartmentRejected || nrOfCompletedInstances == nrOfInstances}
  v
All Approved?  --No--> Prepare Amendment (clears round state)
  |                        |
  Yes                      v
  |                   Amend Clearance Request  (assigned to initiator)
  v                        |  completes -> re-resolve departments -> approvals again
Finance Approval           |
  |                        |
  | --Reject--> same amendment loop
  Approve
  v
Admission and Registration Approval
  |
  | --Reject--> same amendment loop
  Approve
  v
Finalize Completion  (sets result variable, notifies initiator by e-mail)
  |
  v
FYI: Clearance Letter Approved   (parallel task for Internal Audit Department)
  |
  v
End
```

### Key Flowable patterns used

| Requirement | Pattern |
|---|---|
| Initiator | `initiator` variable set by `AuthenticatedUserIdHolder` + process `initiator` attribute; every later task uses `${initiator}` as assignee |
| Dynamic departments | Service task calls `departmentResolverService.resolveDepartments(initiator)` and stores `requiredDepartments` (List) + `departmentDecisions` (Map) |
| Parallel approvals | `<userTask>` with `<multiInstanceLoopCharacteristics isSequential="false">`, `flowable:collection="requiredDepartments"`, `flowable:elementVariable="department"` |
| One task per department | `flowable:candidateGroups="${department}"` — department name IS the Flowable group id |
| Any-reject detection | TaskListener sets `anyDepartmentRejected=true`; MI `completionCondition="${anyDepartmentRejected or nrOfCompletedInstances == nrOfInstances}"` cancels remaining instances |
| Before-task logic | Single TaskListener (`${clearanceTaskListener}`) on `create` event: e-mail + `TASK_ASSIGNED` audit |
| After-task logic | Same listener on `complete`: `APPROVED`/`REJECTED` audit + decision stored in `departmentDecisions` map |
| Cancellation audit | Same listener on `delete`: `TASK_CANCELLED` audit for still-open sibling tasks |
| Amendment loop | Gateway `allDepartmentsApproved` / `financeApproved` / `admissionApproved` route to `Amend Clearance Request`; the service task `Prepare Next Approval Round` resets round variables; flow returns to `Resolve Required Departments` |
| All-approved only | MI naturally ends when every instance completes without rejection; then Finance -> Admission sequentially |
| Completion | Service task sets `clearanceResult = Approved`, e-mails initiator; parallel FYI task for `Internal Audit Department` |

## Reusable components (no duplicated logic in the BPMN)

```
com.example.approval.clearance
├── ClearanceConstants            process/group/task/stage/variable names
├── ClearanceProperties           @ConfigurationProperties("clearance") - departments only
├── ClearanceRequestContract      start-form data + validation
├── model/
│   └── DepartmentDecision        department, group, decision, user, comment, timestamp, round
├── service/
│   ├── DepartmentResolverService     List<String> resolveDepartments(initiator)
│   ├── ClearanceService              start / claim / complete / amend / acknowledge facade
│   └── impl/                         ConfigurableDepartmentResolverService
├── flowable/
│   ├── ClearanceProcessHandler   JavaDelegate/ExecutionListener (resolve, reset round,
│   │                              finalize completion, log amendment)
│   └── ClearanceTaskListener     TaskListener (create/complete/delete)
└── backing/
    ├── StartClearanceBean        JSF start form
    └── ClearanceTaskBean         JSF approve/reject/amend/FYI form

com.example.approval.notification (global - usable by every process)
├── NotificationProperties        @ConfigurationProperties("notification")
├── model/
│   └── NotificationMessage       process-agnostic message (builder)
└── service/
    ├── NotificationService       send(NotificationMessage)
    └── impl/EmailNotificationService  SMTP implementation (recipient resolution,
                                       task deep links, failure tolerant)
```

## Process variables

| Variable | Type | Set by | Purpose |
|---|---|---|---|
| `initiator` | String | start | original initiator username |
| `requestId` | String | start | business key / clearance request id |
| `requiredDepartments` | List<String> | `resolveRequiredDepartments` | drives MI instances |
| `department` | String | MI element variable | current instance's department/group |
| `departmentDecisions` | Map<String, DepartmentDecision> | task listener | per-department decisions |
| `anyDepartmentRejected` | Boolean | task listener | MI completion condition |
| `approvalRound` | Integer | process handler | 1, 2, 3… loop counter |
| `decision` | String | task form | `Approve` / `Reject` per task |
| `comment` | String | task form | approver comment |
| `financeApproved` / `admissionApproved` | Boolean | gateways | sequential stage outcomes |
| `clearanceResult` | String | `finalizeCompletion` | `Approved` for the initiator |

## Configuration (`application.yml`)

```yaml
notification:                              # global - shared by every process
  enabled: true
  from: noreply@example.edu
  task-link-base: http://localhost:8080
  always-log: true
  user-email-domain: students.example.edu  # username -> username@domain
  group-mailboxes:
    IT Department: it@example.edu          # one address per candidate group
  user-mailboxes:                          # explicit mailbox per username
    student.john: john@example.edu
  task-link-paths:                         # process key -> JSF task page
    clearanceLetterProcess: /clearance-task.xhtml

clearance:
  departments:
    mode: ALL                              # ALL | CONFIGURED
    default-departments: [DEN, HOD, ...]
    initiator-overrides:
      some.student: [DEN, HOD, IT Department]   # per-student subsets
```

- `notification.*` – global settings, group mailboxes and deep-link paths
  used by every process (see `com.example.approval.notification`).
- `mode: ALL` – all 11 departments for every initiator.
- `initiator-overrides` – comma-separated or list values select a subset
  for a specific initiator (re-evaluated after every amendment).

## Audit trail

Every action is stored in `CLRT_AUDIT_LOG` (MySQL, via `AuditLogMapper`):

- `TASK_ASSIGNED` – before a task becomes available (with department + initiator)
- `APPROVED` / `REJECTED` – on completion (user, comment, timestamp)
- `TASK_CANCELLED` – open sibling tasks removed by the MI completion condition
- `PROCESS_STARTED` / `AMENDED` / `PROCESS_COMPLETED` – via `AuditService.logProcessAction`

## Identity & groups (SIS-backed)

Groups are **not** stored in Flowable — `CustomGroupEntityManager` serves them
read-only from the Oracle `FLOWABLE_USERS_VW` view (`ROLE_CODE_` = group id).
For the Clearance process to work, the SIS view must contain the clearance
groups as role codes: the resolved departments, `Finance Department`,
`Admission and Registration Department`, `Internal Audit Department` and the
starter role `STD`.

Supported group queries (all others safely return empty instead of NPE-ing on
the uninitialized engine dataManager):

| Query | Source |
|---|---|
| `createGroupQuery().groupMember(user)` | `findGroupsByUser` (SIS view) |
| `createGroupQuery().groupMember(user).groupId(g)` | `findGroupsByUser` + in-memory filter |
| `createGroupQuery().groupId(g)` | `findGroupById` (SIS view, honors `groupType`) |

Group/user creation via `identityService.saveGroup/saveUser` is intentionally
unsupported (`insert()` throws) — provisioning happens in SIS.

## UI

- `start-clearance.xhtml` – student start form (STD group members): read-only
  requester info (username, name, email) + student/program/contact fields and
  a free-text note
- `clearance-task.xhtml` – single form for department / Finance / Admission
  approve+reject (with comment), initiator amendment and the FYI acknowledgement
- Dashboard shows group tasks for each department a user belongs to

## Known Flowable-specific notes

- **MI completion & cancellation**: the `completionCondition`
  `${anyDepartmentRejected or nrOfCompletedInstances == nrOfInstances}` is the
  supported way to abort remaining parallel instances; deleted instances fire
  the task `delete` event, which we audit as `TASK_CANCELLED`.
- **Variable scope**: the task listener writes `anyDepartmentRejected` at the
  process-instance scope so the MI completion condition (evaluated on the MI
  execution) always sees it.
- **Flowable 7**: `DelegateTask#getExecution()` / `#getCandidateGroups()` were
  removed — use `DelegateTask#setVariable/getVariable` and the known
  element variable instead.
- **Looping**: amendment loops back through the resolve service task, so the
  department set is re-computed every round (never a hardcoded branch).