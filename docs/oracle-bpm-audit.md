# Oracle BPM Audit Tables - DDL, Sequences & Seed Data

The Flowable application writes its **business audit trail** to the
pre-existing Oracle schema (external datasource `app.datasource.external`,
user `MEU`). Audit rows are produced whenever:

- a process instance is created → `BPM_AUDIT_LOG` (master case row),
- a task is assigned / completed / a workflow action happens → `BPM_AUDIT_LOG_DTL`,
- an attachment is uploaded → `BPM_CASE_ATTACHMENTS` (+ an
  `ATTACHMENT_UPLOADED` action in `BPM_AUDIT_LOG_DTL`).

Case linkage: the Oracle tables have no `PROCESS_INSTANCE_ID` column, so the
generated `CASE_ID` is stored as the Flowable process variable
`bpmCaseId` at start time (`ProcessStartService`).

## 1. Tables (reference DDL)

```sql
-- Lookup for action types (ACTION_CODE is NUMBER(4) in BPM_AUDIT_LOG_DTL)
CREATE TABLE BPM_ACTIONS (
    ACTION_CODE   NUMBER(5,0),
    ACTION_DESC   VARCHAR2(200),
    ACTION_DESC_S VARCHAR2(200),
    ENTRY_USER    NUMBER(7,0),
    ENTRY_DATE    DATE,
    TERMINAL      VARCHAR2(200),
    OS_USER       VARCHAR2(200),
    DEPT_DESC     VARCHAR2(200),
    DEPT_DESC_S   VARCHAR2(200)
);

-- Master process record (one row per started process instance)
CREATE TABLE BPM_AUDIT_LOG (
    CASE_ID       NUMBER(9,0),
    REQUESTOR_ID  NUMBER(9,0),
    DOCUMENT_CODE NUMBER(3,0),
    ENTRY_USER    NUMBER(7,0),
    ENTRY_DATE    DATE,
    TERMINAL      VARCHAR2(100),
    OS_USER       VARCHAR2(100)
);

-- Task / action level detail (one row per audited action)
CREATE TABLE BPM_AUDIT_LOG_DTL (
    SERIAL      NUMBER(9,0),
    CASE_ID     NUMBER(9,0),
    ACTION_CODE NUMBER(4,0),
    NOTE        VARCHAR2(500),
    ENTRY_USER  NUMBER(7,0),
    ENTRY_DATE  DATE,
    TERMINAL    VARCHAR2(100),
    OS_USER     VARCHAR2(100),
    IS_FINISHED NUMBER DEFAULT 0
);

-- Uploaded attachments of a case
CREATE TABLE BPM_CASE_ATTACHMENTS (
    SERIAL       NUMBER(9,0),
    CASE_ID      NUMBER(9,0),
    CONTENT_ID   VARCHAR2(1000),
    CONTENT_NAME VARCHAR2(1000),
    ENTRY_USER   NUMBER(6,0),
    ENTRY_DATE   DATE,
    TERMINAL     VARCHAR2(30),
    OS_USER      VARCHAR2(30)
);

-- Lookup for process types
CREATE TABLE BPM_DOCUMENTS (
    DOCUMENT_CODE            NUMBER(3,0),
    DOCUMENT_NAME            VARCHAR2(100),
    DOCUMENT_NAME_S          VARCHAR2(100),
    IS_DOCUMENT              NUMBER(1,0) DEFAULT 1,
    IS_RECEIVED_BY_HAND      NUMBER(1,0) DEFAULT 0,
    IS_RECEIVED_ELECTRONIC   NUMBER(1,0) DEFAULT 0,
    CHECK_BACKOFFICE_SETTINGS NUMBER(1,0) DEFAULT 0,
    FINANCIAL_APPROVAL       NUMBER(1,0) DEFAULT 0,
    PROCESS_SIGNATURE        VARCHAR2(200),
    ENTRY_USER               NUMBER(7,0),
    ENTRY_DATE               DATE,
    TERMINAL                 VARCHAR2(30),
    OS_USER                  VARCHAR2(30),
    IS_PRICE                 NUMBER(1,0) DEFAULT 0,
    ALL_MAJORS               NUMBER(1,0) DEFAULT 0
);
```

## 2. Primary-key generation (`bpm.audit.id-strategy`)

Two strategies, selected in `application.yml`:

- **`MAX_PLUS_ONE` (default)** - keys are allocated as `MAX(id) + 1` per
  table, serialized by a JVM-wide lock (`BpmAuditIdAllocator`). Works on
  the pre-existing schema **without any Oracle sequences**; suitable for
  this single-node JSF application.
- **`SEQUENCE`** - classic `SELECT seq.NEXTVAL FROM DUAL` (ORA-02289 will
  occur if the sequences are missing). Create them first:

```sql
CREATE SEQUENCE BPM_AUDIT_LOG_SEQ     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE BPM_AUDIT_LOG_DTL_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE BPM_CASE_ATTACH_SEQ   START WITH 1 INCREMENT BY 1 NOCACHE;
```

When switching to `SEQUENCE` on a table that already contains rows, start
the sequences above the current `MAX(id)` of each table.

## 3. Recommended constraints (optional but advised)

```sql
ALTER TABLE BPM_AUDIT_LOG     ADD CONSTRAINT PK_BPM_AUDIT_LOG PRIMARY KEY (CASE_ID);
ALTER TABLE BPM_AUDIT_LOG_DTL ADD CONSTRAINT PK_BPM_AUDIT_LOG_DTL PRIMARY KEY (SERIAL);
ALTER TABLE BPM_CASE_ATTACHMENTS ADD CONSTRAINT PK_BPM_CASE_ATTACH PRIMARY KEY (SERIAL);
ALTER TABLE BPM_AUDIT_LOG_DTL ADD CONSTRAINT FK_BPM_DTL_CASE
    FOREIGN KEY (CASE_ID) REFERENCES BPM_AUDIT_LOG (CASE_ID);
ALTER TABLE BPM_CASE_ATTACHMENTS ADD CONSTRAINT FK_BPM_ATT_CASE
    FOREIGN KEY (CASE_ID) REFERENCES BPM_AUDIT_LOG (CASE_ID);
CREATE INDEX IX_BPM_DTL_CASE ON BPM_AUDIT_LOG_DTL (CASE_ID, ENTRY_DATE);
CREATE INDEX IX_BPM_ATT_CASE ON BPM_CASE_ATTACHMENTS (CASE_ID);
```

## 4. Seed data

### 4.1 `BPM_DOCUMENTS` - process types

`DOCUMENT_CODE` is what the application writes into
`BPM_AUDIT_LOG.DOCUMENT_CODE`. The mapping from the Flowable process
definition key is configured in `application.yml`:

```yaml
bpm:
  audit:
    document-codes:
      clearanceLetterProcess: 1
```

```sql
INSERT INTO BPM_DOCUMENTS (DOCUMENT_CODE, DOCUMENT_NAME, DOCUMENT_NAME_S, IS_DOCUMENT)
VALUES (1, 'Clearance Letter Request', 'طلب إخلاء طرف', 0);
COMMIT;
```

### 4.2 `BPM_ACTIONS` - action type lookup

The `BPM_ACTIONS` table is **pre-populated** by the DBA - do **not** seed it.
The application writes the `ACTION_CODE` values configured in
`com.example.approval.audit.BpmAuditConstants` (`ACTION_CODE_*`, referenced by
the `BpmAuditAction` enum). Align those constants with the rows that already
exist in the table; the defaults the application currently ships with:

| ACTION_CODE | Meaning |
|-------------|---------|
| 1  | Case opened (process started) |
| 2  | Task assigned |
| 3  | Task completed - approved |
| 4  | Task completed - rejected |
| 5  | Request amended |
| 6  | Task cancelled |
| 7  | Case finished (process completed) |
| 8  | FYI task created |
| 9  | FYI / result acknowledged |
| 10 | Attachment uploaded |
| 99 | Generic action |

Reference seed (only needed on an empty / scratch schema - **not** on the
pre-populated production table):

```sql
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (1,  'Case opened');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (2,  'Task assigned');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (3,  'Approved');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (4,  'Rejected');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (5,  'Request amended');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (6,  'Task cancelled');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (7,  'Case finished');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (8,  'FYI created');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (9,  'FYI acknowledged');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (10, 'Attachment uploaded');
INSERT INTO BPM_ACTIONS (ACTION_CODE, ACTION_DESC) VALUES (99, 'Generic action');
COMMIT;
```

## 5. Numeric user ids (`ENTRY_USER`, `REQUESTOR_ID`)

The audit columns `ENTRY_USER` / `REQUESTOR_ID` are numeric ids. The app
resolves the current Flowable username through the mapper query
`findNumericUserId` (see `BpmAuditMapper.xml`) - keep it pointed at your
business user table / view (e.g. `DIC_USERS` or `FLOWABLE_USERS_VW`).
If the lookup fails, `null` is stored and a warning is logged.

## 6. Configuration summary (`application.yml`)

```yaml
bpm:
  audit:
    document-codes:
      clearanceLetterProcess: 1
    default-document-code: 1
    # MAX_PLUS_ONE (default, no sequences needed) or SEQUENCE
    id-strategy: MAX_PLUS_ONE
    case-id-sequence: BPM_AUDIT_LOG_SEQ
    detail-serial-sequence: BPM_AUDIT_LOG_DTL_SEQ
    attachment-serial-sequence: BPM_CASE_ATTACH_SEQ
    upload-dir: ${java.io.tmpdir}/bpm-attachments
```

## 7. REST API

| Method | URL | Purpose |
|--------|-----|---------|
| POST | `/api/audit/{processInstanceId}/attachments` | upload file (multipart `file`, `username`) |
| GET  | `/api/audit/{processInstanceId}` | master case record |
| GET  | `/api/audit/{processInstanceId}/details` | action trail |
| GET  | `/api/audit/{processInstanceId}/attachments` | attachment list |
| GET  | `/api/audit/attachments/{serial}/content` | download attachment binary |