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
-- Clearance Letter audit trail (one row per audited action)
CREATE TABLE IF NOT EXISTS CLRT_AUDIT_LOG (
    ID                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    PROCESS_INSTANCE_ID VARCHAR(64)  NOT NULL,
    PROCESS_NAME        VARCHAR(128) NOT NULL,
    ACTION              VARCHAR(32)  NOT NULL,
    STAGE               VARCHAR(64),
    DEPARTMENT          VARCHAR(128),
    USER_ID             VARCHAR(64),
    INITIATOR           VARCHAR(64),
    TASK_ID             VARCHAR(64),
    DETAILS             VARCHAR(2000),
    TIMESTAMP           DATETIME     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_clrt_audit_pid ON CLRT_AUDIT_LOG (PROCESS_INSTANCE_ID, TIMESTAMP);
