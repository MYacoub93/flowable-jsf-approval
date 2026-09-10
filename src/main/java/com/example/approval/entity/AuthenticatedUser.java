package com.example.approval.entity;

import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;

/**
 * Result carrier of the login user query
 * {@code FlowableIdentityMapper.findUserByUsernameForAuth}
 * (view {@code FLOWABLE_USERS_VW}, external Oracle datasource).
 *
 * <p>Flowable's own {@link UserEntityImpl} has no properties for the columns
 * {@code USERNAME_}, {@code DEFAULT_ROLE_} and {@code ROLE_CODE_}, so this
 * subclass adds exactly those three String properties. Everything
 * authentication-related (id, first/last name, e-mail, password) keeps
 * flowing through the inherited setters, so the existing login flow is
 * completely unchanged - the existing query merely selects two more columns
 * onto this type.</p>
 *
 * <p>{@code defaultRole} is the descriptive default role
 * ({@code DEFAULT_ROLE_}), {@code roleCode} the default role code
 * ({@code ROLE_CODE_}). Both may be {@code null} (the view marks them
 * optional) and neither ever influences the authentication result.</p>
 */
public class AuthenticatedUser extends UserEntityImpl {

    private static final long serialVersionUID = 1L;

    /** FLOWABLE_USERS_VW.USERNAME_ - the login name the user authenticated with. */
    private String username;

    /** FLOWABLE_USERS_VW.DEFAULT_ROLE_ - descriptive default role (may be null). */
    private String defaultRole;

    /** FLOWABLE_USERS_VW.ROLE_CODE_ - default role code (may be null). */
    private String roleCode;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}
