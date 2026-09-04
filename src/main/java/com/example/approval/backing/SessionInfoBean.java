package com.example.approval.backing;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import org.flowable.idm.api.User;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Centralized, session-scoped holder of the currently logged-in user's
 * information ("session info").
 *
 * <p>Populated once by {@link UserLoginBean} right after a successful
 * authentication and cleared on logout (before the HTTP session is
 * invalidated). It reuses the user data already loaded by the existing
 * authentication flow - it performs no lookups and duplicates no
 * authentication logic.</p>
 *
 * <p>Managed exactly like {@code UserLoginBean}: a normal CDI
 * {@code @Named @SessionScoped} bean (Weld, provided by JoinFaces), with a
 * session-scoped Spring facade registered in {@code WebConfig} so the Spring
 * {@code @Component} backing beans (and the JoinFaces EL resolver chain) all
 * share the same per-session instance. Backing beans obtain it through
 * {@link BaseBackingBean} inheritance instead of individual injections.</p>
 *
 * <p>Security note: only non-sensitive identity attributes are kept (id,
 * username, first/last name, email, login time). The password is deliberately
 * NOT copied into this bean. Roles/groups are intentionally not snapshotted
 * here either - they are resolved live by the existing services
 * (e.g. {@code ExternalGroupService.isGroupAdmin}) so authorization decisions
 * always reflect the current database state.</p>
 */
@Named("sessionInfoBean")
@SessionScoped
public class SessionInfoBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Flowable user id (FLOWABLE_USERS_VW.ID_) - the id every service call uses. */
    private String userId;

    /** The login name the user authenticated with (USERNAME_). */
    private String username;

    private String firstName;
    private String lastName;
    private String email;

    /** When the user logged in (informational only). */
    private LocalDateTime loginTime;

    /**
     * Snapshot the authenticated user. Called by {@link UserLoginBean} after
     * a successful login; {@code loginName} is the username that was submitted
     * on the login form (kept here because the Flowable {@link User} object
     * carries no username attribute).
     */
    public void populate(User user, String loginName) {
        if (user == null) {
            clear();
            return;
        }
        this.userId = user.getId();
        this.username = loginName != null && !loginName.isBlank()
                ? loginName.trim()
                : user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.loginTime = LocalDateTime.now();
    }

    /**
     * Clears all session information. Called on logout, before the session is
     * invalidated, so no user data survives the end of the session.
     */
    public void clear() {
        this.userId = null;
        this.username = null;
        this.firstName = null;
        this.lastName = null;
        this.email = null;
        this.loginTime = null;
    }

    /** A user is logged in as soon as the bean was populated with a user id. */
    public boolean isLoggedIn() {
        return userId != null;
    }

    /**
     * Display name: "First Last", falling back to the login username and
     * finally the user id when no name parts are available.
     */
    public String getDisplayName() {
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            name.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(lastName.trim());
        }
        if (name.length() > 0) {
            return name.toString();
        }
        return username != null ? username : userId;
    }

    /**
     * Numeric view of the Flowable user id (FLOWABLE_USERS_VW.ID_ is numeric),
     * or {@code null} when it is not numeric / the user is not logged in.
     */
    public Long getUserIdAsLong() {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Getters / Setters -----------------------------------------------------

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
}