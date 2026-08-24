package com.example.approval.entity;

import java.time.LocalDateTime;

/**
 * A membership row of the external group-manager table {@code WEB_USER_ROLES}
 * (Oracle, external datasource).
 *
 * <p>Column mapping (verified against the live schema):</p>
 * <pre>
 * USER_ID        NUMBER(9)     NOT NULL  PK part 1 (numeric web user id)
 * ROLE_ID        NUMBER(2)     NOT NULL  PK part 2 -> WEB_ROLES.ROLE_ID (FK)
 * USER_WEB_NAME  VARCHAR2(20)  NOT NULL  login name (usually = USERNAME_)
 * IS_ENABLED     NUMBER(1)     NULLABLE  0/1 (check constraint), 1 = active
 * CREATED_BY     NUMBER(9)     NOT NULL
 * CREATED_DATE   DATE          NOT NULL
 * UPDATED_BY     NUMBER(9)     NULLABLE
 * UPDATED_DATE   DATE          NULLABLE
 * </pre>
 */
public class GroupMembership {

    /** Max DB length of USER_WEB_NAME. */
    public static final int USER_WEB_NAME_MAX_LENGTH = 20;

    private Long userId;
    private Long roleId;
    private String userWebName;
    private Integer isEnabled;
    private Long createdBy;
    private LocalDateTime createdDate;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getUserWebName() {
        return userWebName;
    }

    public void setUserWebName(String userWebName) {
        this.userWebName = userWebName;
    }

    public Integer getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Integer isEnabled) {
        this.isEnabled = isEnabled;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }
}