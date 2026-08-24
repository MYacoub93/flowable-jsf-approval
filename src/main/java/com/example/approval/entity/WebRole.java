package com.example.approval.entity;

import java.time.LocalDateTime;

/**
 * A role/group row of the external group manager table {@code WEB_ROLES}
 * (Oracle, external datasource).
 *
 * <p>Column mapping (verified against the live schema):</p>
 * <pre>
 * ROLE_ID       NUMBER(2)      NOT NULL  PK (no sequence/trigger - MAX+1)
 * ROLE_CODE     VARCHAR2(200)  NULLABLE  logical code used by Flowable (e.g. ADM)
 * ROLE_NAME     VARCHAR2(30)   NOT NULL  Arabic display name
 * ROLE_NAME_S   VARCHAR2(30)   NULLABLE  secondary (English) display name
 * HOME_MSG      VARCHAR2(4000) NULLABLE  home-page message (primary language)
 * HOME_MSG_S    VARCHAR2(4000) NULLABLE  home-page message (secondary language)
 * IS_FIXED      NUMBER(1)      NULLABLE  1 = shipped system role, do not delete
 * KEY_TYPE      NUMBER(1)      NULLABLE  FK -> WEB_KEY_TYPES.KEY_CODE (0..4)
 * CREATED_BY    NUMBER(9)      NOT NULL  numeric id (DIC_USERS.USER_ID) of creator
 * CREATED_DATE  DATE           NOT NULL
 * UPDATED_BY    NUMBER(9)      NULLABLE
 * UPDATED_DATE  DATE           NULLABLE
 * </pre>
 */
public class WebRole {

    /** Max DB length of ROLE_CODE. */
    public static final int ROLE_CODE_MAX_LENGTH = 200;
    /** Max DB length of ROLE_NAME. */
    public static final int ROLE_NAME_MAX_LENGTH = 30;
    /** Max DB length of ROLE_NAME_S. */
    public static final int ROLE_NAME_S_MAX_LENGTH = 30;
    /** Max DB length of HOME_MSG / HOME_MSG_S. */
    public static final int HOME_MSG_MAX_LENGTH = 4000;

    /** key_type value meaning "generic / no specific actor kind" (WEB_KEY_TYPES 0). */
    public static final int KEY_TYPE_GENERIC = 0;

    private Long roleId;
    private String roleCode;
    private String roleName;
    private String roleNameS;
    private String homeMsg;
    private String homeMsgS;
    private Integer isFixed;
    private Integer keyType;
    private Long createdBy;
    private LocalDateTime createdDate;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleNameS() {
        return roleNameS;
    }

    public void setRoleNameS(String roleNameS) {
        this.roleNameS = roleNameS;
    }

    public String getHomeMsg() {
        return homeMsg;
    }

    public void setHomeMsg(String homeMsg) {
        this.homeMsg = homeMsg;
    }

    public String getHomeMsgS() {
        return homeMsgS;
    }

    public void setHomeMsgS(String homeMsgS) {
        this.homeMsgS = homeMsgS;
    }

    public Integer getIsFixed() {
        return isFixed;
    }

    public void setIsFixed(Integer isFixed) {
        this.isFixed = isFixed;
    }

    public Integer getKeyType() {
        return keyType;
    }

    public void setKeyType(Integer keyType) {
        this.keyType = keyType;
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