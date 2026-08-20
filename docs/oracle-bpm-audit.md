# Oracle BPM Audit (F_BPM_* tables)

The application writes a business-level audit trail of every workflow case
to the pre-existing Oracle schema `MEU`, alongside the Flowable engine
database. All access goes through the **external Oracle datasource** and
the `externalSqlSessionFactory` (see `MyBatisConfig`), so audit rows land
in Oracle even though Flowable itself runs on the primary datasource.

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
| Action codes | `audit/BpmAuditConstants.java` (`ACTION_CODE_*`, values of `BPM_ACTIONS`) |
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
(through `AuditService`), keyed by the process instance id of the case:

| Event | Action code |
|---|---|
| Task created and offered to an approver group | `TASK_ASSIGNED` (2) |
| Approval task completed | `APPROVED` (3) / `REJECTED` (4) |
| Initiator amended a rejected request | `REQUEST_AMENDED` (5) |
| Multi-instance completion removed an open sibling task | `TASK_CANCELLED` (6) |
| Case finished successfully | `CASE_FINISHED` (7) — `IS_FINISHED = 1` |
| Result / FYI task created | `FYI_CREATED` (8) |
| Result / FYI task acknowledged | `FYI_ACKNOWLEDGED` (9) |
| Attachment uploaded | `ATTACHMENT_UPLOADED` (10) |
| Anything else (departments resolved, generic actions) | `GENERIC` (99) |

The `F_BPM_AUDIT_LOG` master row is inserted by `ProcessStartService`
right after `runtimeService.startProcessInstanceByKey(...)` returns, using
the brand-new process instance id as `CASE_ID`.