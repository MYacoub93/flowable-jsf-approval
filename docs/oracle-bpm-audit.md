# Oracle BPM Audit (F_BPM_* tables)

The application writes a business-level audit trail of every workflow case
to the pre-existing Oracle schema `MEU`, alongside the Flowable engine
database. All access goes through the **external Oracle datasource** and
the `externalSqlSessionFactory` (see `MyBatisConfig`), so audit rows land
in Oracle even though Flowable itself runs on the primary datasource.

## Global on/off switch

The whole audit trail can be turned off with a single property:

```yaml
bpm:
  audit:
    enabled: false   # default: true
```

When `enabled: false`:

- `AuditServiceImpl` performs **no writes** — `openCase` returns the
  process instance id unchanged (the case-id contract still holds) and
  every `log*` method is a no-op returning `null`;
- `AttachmentAuditService.registerAttachment` skips the
  `F_BPM_CASE_ATTACHMENTS` insert and the `ATTACHMENT_UPLOADED` audit row;
- every endpoint of `BpmAuditRestController` answers
  **`503 SERVICE_UNAVAILABLE`** (uploads, listing, downloads, case and
  detail queries).

The workflow itself (approvals, rejections, process flow) keeps running
normally — only the trail recording stops. The guards live inside the two
audit services, so none of the callers (Flowable listeners/delegates, JSF
backing services, REST layer) need to check the flag themselves.

## Tables

| Table | Purpose | Primary key |
|---|---|---|
| `F_BPM_AUDIT_LOG` | One master row per started case (process instance) | `CASE_ID` |
| `F_BPM_AUDIT_LOG_DTL` | One row per audited action (assigned / approved / rejected / amended / FYI / upload …) | `SERIAL`, `CASE_ID`, `ACTION_CODE` |
| `F_BPM_CASE_ATTACHMENTS` | One row per uploaded attachment | `SERIAL`, `CASE_ID`, `CONTENT_ID` |

### Case linkage: `CASE_ID` is the Flowable process instance id

`CASE_ID` is a `VARCHAR2(64)` that holds the **Flowable process instance id**
of the started case:

- it is **always supplied by the caller** — listeners/delegates read it from
  `DelegateTask`/`DelegateExecution`, `ProcessStartService` passes the id of
  the instance it just started, and the REST layer takes it as a path
  variable;
- it is **never generated** — there is no sequence, serial or auto-increment
  behind it;
- every `F_BPM_*` table references the case by this natural key, which is
  also the sole primary key of `F_BPM_AUDIT_LOG`.

Only the `SERIAL` columns of `F_BPM_AUDIT_LOG_DTL` and
`F_BPM_CASE_ATTACHMENTS` are allocated by `BpmAuditIdAllocator`
(`bpm.audit.id-strategy`):

| Strategy | Behaviour |
|---|---|
| `MAX_PLUS_ONE` (default) | `MAX(SERIAL) + 1` per table, allocation serialized inside the JVM — works on the shipped schema without Oracle sequences |
| `SEQUENCE` | `SELECT seq.NEXTVAL FROM DUAL` once the DBA provides `F_BPM_AUDIT_LOG_DTL_SEQ` / `F_BPM_CASE_ATTACH_SEQ` |

## Reference DDL

```sql
CREATE TABLE "MEU"."F_BPM_AUDIT_LOG"
(
    "CASE_ID"       VARCHAR2(64 BYTE) NOT NULL ENABLE,
    "REQUESTOR_ID"  NUMBER(9,0) NOT NULL ENABLE,
    "DOCUMENT_CODE" NUMBER(3,0) NOT NULL ENABLE,
    "ENTRY_USER"    NUMBER(7,0) NOT NULL ENABLE,
    "ENTRY_DATE"    DATE NOT NULL ENABLE,
    "TERMINAL"      VARCHAR2(100 BYTE) NOT NULL ENABLE,
    "OS_USER"       VARCHAR2(100 BYTE) NOT NULL ENABLE,

    CONSTRAINT "F_BPM_AUDIT_LOG_PK"
        PRIMARY KEY ("CASE_ID")
);

CREATE TABLE "MEU"."F_BPM_AUDIT_LOG_DTL"
(
    "SERIAL"       NUMBER(9,0) NOT NULL ENABLE,
    "CASE_ID"      VARCHAR2(64 BYTE) NOT NULL ENABLE,
    "ACTION_CODE"  NUMBER(4,0) NOT NULL ENABLE,
    "NOTE"         VARCHAR2(500 BYTE),
    "ENTRY_USER"   NUMBER(7,0) NOT NULL ENABLE,
    "ENTRY_DATE"   DATE NOT NULL ENABLE,
    "TERMINAL"     VARCHAR2(100 BYTE) NOT NULL ENABLE,
    "OS_USER"      VARCHAR2(100 BYTE) NOT NULL ENABLE,
    "IS_FINISHED"  NUMBER DEFAULT 0,

    CONSTRAINT "F_BPM_AUDIT_LOG_DTL_PK"
        PRIMARY KEY ("SERIAL", "CASE_ID", "ACTION_CODE")
);

CREATE TABLE "MEU"."F_BPM_CASE_ATTACHMENTS"
(
    "SERIAL"       NUMBER(9,0) NOT NULL ENABLE,
    "CASE_ID"      VARCHAR2(64 BYTE) NOT NULL ENABLE,
    "CONTENT_ID"   VARCHAR2(1000 BYTE) NOT NULL ENABLE,
    "CONTENT_NAME" VARCHAR2(1000 BYTE) NOT NULL ENABLE,
    "ENTRY_USER"   NUMBER(6,0) NOT NULL ENABLE,
    "ENTRY_DATE"   DATE NOT NULL ENABLE,
    "TERMINAL"     VARCHAR2(30 BYTE) NOT NULL ENABLE,
    "OS_USER"      VARCHAR2(30 BYTE) NOT NULL ENABLE,

    CONSTRAINT "F_BPM_CASE_ATTACHMENTS_PK"
        PRIMARY KEY ("SERIAL", "CASE_ID", "CONTENT_ID")
);
```

## User / document resolution

| Column | Source |
|---|---|
| `REQUESTOR_ID` (`NUMBER(9)`) | numeric id resolved from the initiator username (`FLOWABLE_USERS_VW`, fallback `0`) |
| `DOCUMENT_CODE` (`NUMBER(3)`) | `bpm.audit.document-codes` map keyed by process definition key (`BPM_DOCUMENTS`) |
| `ENTRY_USER` (`NUMBER(7)`/`NUMBER(6)`) | numeric id of the acting user, same lookup as above (fallback `0`) |
| `TERMINAL` / `OS_USER` | server host name / `user.name` (truncated to the column sizes) |

## Code map

| Concern | Class |
|---|---|
| Table access | `mapper/BpmAuditMapper.java` + `resources/mapper/BpmAuditMapper.xml` |
| Entities | `audit/model/BpmAuditLog.java`, `BpmAuditLogDtl.java`, `BpmCaseAttachment.java` |
| Audit facade | `clearance/service/AuditService.java` + `impl/AuditServiceImpl.java` |
| Attachments | `audit/service/AttachmentAuditService.java` + `audit/rest/BpmAuditRestController.java` |
| SERIAL allocation | `audit/service/BpmAuditIdAllocator.java` (`bpm.audit.id-strategy`) |
| Action codes | `audit/BpmAuditConstants.java` (`ACTION_CODE_*`, all 164 rows of `BPM_ACTIONS`) + `audit/BpmAuditAction.java` (enum + department resolver) |
| Configuration | `audit/BpmAuditProperties.java` (`bpm.audit.*` in `application.yml`) |

## REST API

All path variables are the Flowable **process instance id** (used directly
as `CASE_ID`):

```
POST /api/audit/{processInstanceId}/attachments      multipart upload (file, username)
GET  /api/audit/{processInstanceId}                  master case record
GET  /api/audit/{processInstanceId}/details          full action trail (oldest first)
GET  /api/audit/{processInstanceId}/attachments      attachment metadata list
GET  /api/audit/attachments/{serial}/content         download attachment binary
```

## Audit write points

Every workflow mutation writes exactly one `F_BPM_AUDIT_LOG_DTL` row
(through `AuditService`), keyed by the process instance id of the case.
`ACTION_CODE` is always one of the 164 pre-populated `BPM_ACTIONS` rows
(`docs/bpm_actions.htm`): **0** = Entered, **1–4** = applicant actions,
**5–124** = per-department Approval / Rejection / Review triplets
(104, 108, 121 are special payment/claim codes), **125–163** = per-department
*Task Received* (استلام مهمة).

Department-aware events resolve the acting department's own row via
`BpmAuditAction.of(department, ActionType)` — the resolver understands both
the `BPM_ACTIONS` department names and the clearance group ids
(`HOD`, `DEN`, `LibraryDepartment`, `Finance Department`, …). Unknown
departments fall back to `ENTERED` (0), so inserts can never hit a missing
lookup row.

| Event | Action code |
|---|---|
| Task created and offered to an approver group | department's *Task Received* row (125–163) |
| Approval task completed | department's *Approval* row (5–124) |
| Rejection on an approval task | department's *Rejection* row (5–124) |
| Initiator amended a rejected request | `APPLICANT_CONTINUATION` (1) |
| Process completed / applicant acknowledged the result | `APPLICANT_VIEWED_FINAL_RESULT` (4) — `IS_FINISHED = 1` |
| Departments resolved, task cancelled, FYI created/acknowledged, attachment uploaded, unknown actions | `ENTERED` (0) — the `NOTE` column carries the specifics |

The `F_BPM_AUDIT_LOG` master row is inserted by `ProcessStartService`
right after `runtimeService.startProcessInstanceByKey(...)` returns, using
the brand-new process instance id as `CASE_ID`.