package com.example.approval.backing;

import com.example.approval.config.SpringCdiBridge;
import com.example.approval.entity.AuthenticatedUser;
import com.example.approval.origin.beans.StaffInfoBean;
import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.service.CommonService;
import com.example.approval.service.FlowableIdentityService;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import org.flowable.idm.api.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Session-scoped login bean managed by CDI (Weld, provided by JoinFaces).
 *
 * Why CDI instead of Spring @SessionScope: a Spring session-scoped bean is a
 * CGLIB proxy owned by the Spring container, which does not align with the
 * JSF/CDI session lifecycle used by EL. A normal CDI @SessionScoped bean lives
 * directly in the standard CDI session context, so JSF state saving and EL
 * resolution share the same per-session instance without proxies.
 *
 * The Flowable identity lookup stays in a Spring singleton service. This bean
 * obtains it through {@link SpringCdiBridge}, because Spring beans are not
 * natively injectable into CDI beans in this setup. The other (Spring-managed)
 * backing beans keep injecting UserLoginBean by type: WebConfig registers a
 * session-scoped Spring facade that delegates to this CDI bean, so Spring and
 * CDI share the same per-session state.
 *
 * After a successful authentication the bean snapshots the logged-in user's
 * information into the {@link SessionInfoBean} (obtained through
 * {@link BaseBackingBean} inheritance), which every other backing bean reads
 * through the same inheritance instead of a per-bean injection. It then loads
 * the role-specific SIS profile (student vs staff, decided by
 * DEFAULT_ROLE_ - see {@link #loadRoleProfile(AuthenticatedUser, String)})
 * and stores it in the same SessionInfoBean for the whole session. On logout
 * the session info is cleared before the HTTP session is invalidated.
 *
 * All user-visible messages produced here are localized through the
 * {@code labels} resource bundle (see {@link BaseBackingBean#getLabel}) in the
 * locale held by {@link SessionInfoBean}. The authentication logic itself is
 * unchanged.
 */
@Named("loginBean")
@SessionScoped
public class UserLoginBean extends BaseBackingBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(UserLoginBean.class);

    /**
     * Default role value ({@code FLOWABLE_USERS_VW.DEFAULT_ROLE_}) that marks
     * the user as a student. Every other value (including null) means staff -
     * see {@link #isStudent(String)}.
     */
    static final String STUDENT_ROLE = "STD";

    private String username;
    private String password;
    private User currentUser;

    /**
     * Student/user info snapshot of the logged-in user, taken right after a
     * successful login from the same FLOWABLE_USERS_VW row the
     * authentication used. Carries the default role and role code
     * ({@code DEFAULT_ROLE_} / {@code ROLE_CODE_}) - both may be null and
     * never influence the login result.
     */
    private StudentInfoBean studentInfo;

    private FlowableIdentityService identityService() {
        return SpringCdiBridge.getBean(FlowableIdentityService.class);
    }

    public String login() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("login.error.required"));
            return null;
        }

        String loginName = username.trim();

        // Load the user (with password) straight from the database via the mapper SELECT
        AuthenticatedUser dbUser = identityService().findUserByUsernameForAuth(loginName);
        if (dbUser == null) {
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("login.error.invalid"));
            return null;
        }

        // Compare the submitted password with the value returned by the query
        if (!password.equals(dbUser.getPassword())) {
            addMessage(FacesMessage.SEVERITY_ERROR, getLabel("login.error.invalid"));
            return null;
        }

        // The password was only needed for the comparison above - do not keep
        // it in the session-backed user object.
        dbUser.setPassword(null);

        this.currentUser = dbUser;
        this.username = currentUser.getFirstName();

        // Snapshot the authenticated user's information for every backing bean.
        // (SessionInfoBean.populate deliberately keeps the locale chosen on the
        // login screen, so the app stays in the selected language after login.)
        getSessionInfo().populate(dbUser, loginName);

        // Snapshot the same login row (including defaultRole/roleCode from
        // FLOWABLE_USERS_VW) into the student info bean. Values may be null -
        // a missing role never blocks the login.
        this.studentInfo = studentInfoOf(dbUser, loginName);

        // Role-based session profile (business rule: DEFAULT_ROLE_ == "STD" ->
        // student, anything else including null -> staff): loads the matching
        // SIS profile through the existing CommonService queries and stores it
        // in SessionInfoBean for the whole session. Never fails the login.
        loadRoleProfile(dbUser, loginName);

        addMessage(FacesMessage.SEVERITY_INFO, getLabel("login.welcome", currentUser.getFirstName()));
        return "/dashboard?faces-redirect=true";
    }

    @Override
    public String logout() {
        getSessionInfo().clear(); // drop the session information first
        this.currentUser = null;
        this.studentInfo = null;
        this.username = null;
        this.password = null;
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "/login?faces-redirect=true";
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    private void addMessage(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }

    /**
     * Maps one row of the login query ({@link AuthenticatedUser},
     * FLOWABLE_USERS_VW) onto the session's {@link StudentInfoBean} snapshot.
     * {@code defaultRole} / {@code roleCode} come straight from
     * {@code DEFAULT_ROLE_} / {@code ROLE_CODE_} and may both be null -
     * missing values are simply kept as null and never affect login.
     */
    static StudentInfoBean studentInfoOf(AuthenticatedUser user, String loginName) {
        if (user == null) {
            return null;
        }
        StudentInfoBean info = new StudentInfoBean();
        info.setStudentId(user.getId());
        info.setUserName(loginName);
        info.setFirstName(user.getFirstName());
        info.setLastName(user.getLastName());
        info.setEmail(user.getEmail());
        info.setDefaultRole(user.getDefaultRole());
        info.setRoleCode(user.getRoleCode());
        return info;
    }

    /**
     * Single place implementing the user-type business rule. After a
     * successful login the default role ({@code FLOWABLE_USERS_VW.DEFAULT_ROLE_})
     * decides which SIS profile is loaded:
     * <ul>
     *   <li>{@code "STD"} -> student: {@code CommonService.getStudentInfo}
     *       (backed by {@code CommonMapper.xml getStudentInfo})</li>
     *   <li>anything else, including null -> staff:
     *       {@code CommonService.getStaffInfo}
     *       (backed by {@code CommonMapper.xml getStaffInfo})</li>
     * </ul>
     * Both queries are keyed on the web username (SIS {@code WEB_NAME}) -
     * exactly how the existing synchronizer ({@code SISOC}) calls them.
     *
     * <p>The loaded profile is stored in {@link SessionInfoBean} (student ->
     * {@code studentInfoBean}, staff -> {@code staffInfoBean}) while the
     * other property is explicitly nulled, so no stale profile of a previous
     * login survives. A missing SIS row (null result) or a lookup error is
     * logged and leaves the profile null - it never fails the login.</p>
     */
    private void loadRoleProfile(AuthenticatedUser user, String loginName) {
        SessionInfoBean sessionInfo = getSessionInfo();
        if (sessionInfo == null) {
            return;
        }
        loadRoleProfileInto(sessionInfo, user, loginName,
                SpringCdiBridge.getBean(CommonService.class));
    }

    /**
     * Testable core of loadRoleProfile: the session info, the login row and
     * the (already resolved) CommonService are parameters, so the role logic
     * can be verified without a Spring/CDI runtime.
     */
    static void loadRoleProfileInto(SessionInfoBean sessionInfo, AuthenticatedUser user,
                                    String loginName, CommonService commonService) {
        String webName = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername()
                : loginName;
        try {
            if (isStudent(user.getDefaultRole())) {
                StudentInfoBean profile = commonService.getStudentInfo(webName);
                applyLoginRow(profile, user, loginName);
                storeRoleProfile(sessionInfo, profile, null);
                log.info("Student profile for {} loaded (default role {}): {}",
                        loginName, user.getDefaultRole(),
                        profile != null ? profile.getStudentId() : "no SIS record");
            } else {
                StaffInfoBean profile = commonService.getStaffInfo(webName);
                storeRoleProfile(sessionInfo, null, profile);
                log.info("Staff profile for {} loaded (default role {}): {}",
                        loginName, user.getDefaultRole(),
                        profile != null ? profile.getInstructorId() : "no SIS record");
            }
        } catch (Exception e) {
            log.error("Failed to load the SIS profile for {} (default role {})",
                    loginName, user.getDefaultRole(), e);
            storeRoleProfile(sessionInfo, null, null);
        }
    }

    /**
     * The business rule itself: only the exact value {@code "STD"} marks a
     * student. Null and every other value mean staff. The constant is placed
     * first so a null role can never throw a NullPointerException.
     */
    static boolean isStudent(String defaultRole) {
        return STUDENT_ROLE.equals(defaultRole);
    }

    /**
     * Copies the login-row role fields ({@code FLOWABLE_USERS_VW}
     * {@code DEFAULT_ROLE_} / {@code ROLE_CODE_}, plus the web username when
     * the SIS query did not populate it) onto the SIS-loaded student profile,
     * so the session bean carries both the SIS data and the role
     * information. No-op for a null profile (no SIS record).
     */
    static void applyLoginRow(StudentInfoBean profile, AuthenticatedUser user, String loginName) {
        if (profile == null) {
            return;
        }
        profile.setDefaultRole(user.getDefaultRole());
        profile.setRoleCode(user.getRoleCode());
        if (profile.getUserName() == null || profile.getUserName().isBlank()) {
            profile.setUserName(loginName);
        }
    }

    /**
     * Stores the role-specific profile in the session and explicitly clears
     * the other one (student -> staff null, staff -> student null) so no
     * stale information from a previous login survives. Passing null for
     * both simply drops the profiles (used on lookup failures).
     */
    static void storeRoleProfile(SessionInfoBean sessionInfo,
                                 StudentInfoBean studentProfile,
                                 StaffInfoBean staffProfile) {
        if (sessionInfo == null) {
            return;
        }
        sessionInfo.setStudentInfoBean(studentProfile);
        sessionInfo.setStaffInfoBean(staffProfile);
    }

    // Getters / Setters

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public StudentInfoBean getStudentInfo() {
        return studentInfo;
    }

    public void setStudentInfo(StudentInfoBean studentInfo) {
        this.studentInfo = studentInfo;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }
}