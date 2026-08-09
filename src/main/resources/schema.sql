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
