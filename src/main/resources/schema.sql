-- Users table for dynamic assignment (MyBatis managed)
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    full_name   VARCHAR(128) NOT NULL,
    department  VARCHAR(64),
    role        VARCHAR(32)  NOT NULL,
    email       VARCHAR(128),
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_users_department_role ON users (department, role);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);

-- NOTE: the workflow audit trail no longer lives in this (H2/primary)
-- database. Audit data is written to the pre-existing Oracle F_BPM_* tables
-- (F_BPM_AUDIT_LOG, F_BPM_AUDIT_LOG_DTL, F_BPM_CASE_ATTACHMENTS) via the
-- externalSqlSessionFactory / external Oracle datasource. See
-- docs/oracle-bpm-audit.md for the reference DDL and seed data.
--
-- CASE_ID of every F_BPM_* table is VARCHAR2(64) and holds the Flowable
-- process instance id of the started case - it is always supplied by the
-- caller and NEVER generated (no sequence / serial). Reference DDL
-- (schema MEU):
--
--   CREATE TABLE "MEU"."F_BPM_AUDIT_LOG"
--   (
--       "CASE_ID"       VARCHAR2(64 BYTE) NOT NULL ENABLE,
--       "REQUESTOR_ID"  NUMBER(9,0) NOT NULL ENABLE,
--       "DOCUMENT_CODE" NUMBER(3,0) NOT NULL ENABLE,
--       "ENTRY_USER"    NUMBER(7,0) NOT NULL ENABLE,
--       "ENTRY_DATE"    DATE NOT NULL ENABLE,
--       "TERMINAL"      VARCHAR2(100 BYTE) NOT NULL ENABLE,
--       "OS_USER"       VARCHAR2(100 BYTE) NOT NULL ENABLE,
--       CONSTRAINT "F_BPM_AUDIT_LOG_PK" PRIMARY KEY ("CASE_ID")
--   );
--
--   CREATE TABLE "MEU"."F_BPM_AUDIT_LOG_DTL"
--   (
--       "SERIAL"       NUMBER(9,0) NOT NULL ENABLE,
--       "CASE_ID"      VARCHAR2(64 BYTE) NOT NULL ENABLE,
--       "ACTION_CODE"  NUMBER(4,0) NOT NULL ENABLE,
--       "NOTE"         VARCHAR2(500 BYTE),
--       "ENTRY_USER"   NUMBER(7,0) NOT NULL ENABLE,
--       "ENTRY_DATE"   DATE NOT NULL ENABLE,
--       "TERMINAL"     VARCHAR2(100 BYTE) NOT NULL ENABLE,
--       "OS_USER"      VARCHAR2(100 BYTE) NOT NULL ENABLE,
--       "IS_FINISHED"  NUMBER DEFAULT 0,
--       CONSTRAINT "F_BPM_AUDIT_LOG_DTL_PK"
--           PRIMARY KEY ("SERIAL", "CASE_ID", "ACTION_CODE")
--   );
--
--   CREATE TABLE "MEU"."F_BPM_CASE_ATTACHMENTS"
--   (
--       "SERIAL"       NUMBER(9,0) NOT NULL ENABLE,
--       "CASE_ID"      VARCHAR2(64 BYTE) NOT NULL ENABLE,
--       "CONTENT_ID"   VARCHAR2(1000 BYTE) NOT NULL ENABLE,
--       "CONTENT_NAME" VARCHAR2(1000 BYTE) NOT NULL ENABLE,
--       "ENTRY_USER"   NUMBER(6,0) NOT NULL ENABLE,
--       "ENTRY_DATE"   DATE NOT NULL ENABLE,
--       "TERMINAL"     VARCHAR2(30 BYTE) NOT NULL ENABLE,
--       "OS_USER"      VARCHAR2(30 BYTE) NOT NULL ENABLE,
--       CONSTRAINT "F_BPM_CASE_ATTACHMENTS_PK"
--           PRIMARY KEY ("SERIAL", "CASE_ID", "CONTENT_ID")
--   );