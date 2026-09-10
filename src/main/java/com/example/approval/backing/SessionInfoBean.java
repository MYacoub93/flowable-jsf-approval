package com.example.approval.backing;

import com.example.approval.origin.beans.StaffInfoBean;
import com.example.approval.origin.beans.StudentInfoBean;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import org.flowable.idm.api.User;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Locale;

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
 *
 * <p>Localization note: this bean is also the single source of truth for the
 * user's current UI {@link Locale} (English by default, Arabic after the user
 * switches). The locale intentionally survives login/logout - it is neither
 * set by {@link #populate(User, String)} nor reset by {@link #clear()} - so a
 * language chosen on the login screen is still active after authentication
 * and stays consistent during navigation. Views pick it up centrally through
 * {@code <f:view locale="#{sessionInfoBean.locale}">}.</p>
 */
@Named("sessionInfoBean")
@SessionScoped
public class SessionInfoBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The application's default/fallback locale. English, i.e. the language
     * the app used before localization existed - the default behavior is
     * unchanged until the user actively switches.
     */
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    /** Locale for Arabic (language-only, no country/variant). */
    private static final Locale ARABIC_LOCALE = new Locale("ar");

    /** Locales backed by a resource bundle (labels[_xx].properties). */
    private static final Locale[] SUPPORTED_LOCALES = {Locale.ENGLISH, ARABIC_LOCALE};

    /** Flowable user id (FLOWABLE_USERS_VW.ID_) - the id every service call uses. */
    private String userId;

    /** The login name the user authenticated with (USERNAME_). */
    private String username;

    private String firstName;
    private String lastName;
    private String email;

    /** When the user logged in (informational only). */
    private LocalDateTime loginTime;

    /** SIS profile when the default role (DEFAULT_ROLE_) is STD (student); loaded once by UserLoginBean via CommonService.getStudentInfo. Null for staff. */
    private StudentInfoBean studentInfoBean;

    /** SIS profile when the default role is anything other than STD (staff); loaded once by UserLoginBean via CommonService.getStaffInfo. Null for students. */
    private StaffInfoBean staffInfoBean;

    /**
     * The current UI locale. Initialized to {@link #DEFAULT_LOCALE} (English)
     * and changed only when the user switches language (login screen or
     * settings screen). Kept across login/logout on purpose.
     */
    private Locale locale = DEFAULT_LOCALE;

    /**
     * Snapshot the authenticated user. Called by {@link UserLoginBean} after
     * a successful login; {@code loginName} is the username that was submitted
     * on the login form (kept here because the Flowable {@link User} object
     * carries no username attribute).
     *
     * <p>Note: deliberately does not touch {@link #locale} - a language chosen
     * on the login screen must survive authentication.</p>
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
        // Fresh authenticated session: drop any profile left over from a
        // previous login; the role-specific profile is loaded right after
        // this call by UserLoginBean.
        this.studentInfoBean = null;
        this.staffInfoBean = null;
    }

    /**
     * Clears all session information. Called on logout, before the session is
     * invalidated, so no user data survives the end of the session.
     *
     * <p>Note: deliberately does not reset {@link #locale} - the language
     * selection is a UI preference, not user identity data.</p>
     */
    public void clear() {
        this.userId = null;
        this.username = null;
        this.firstName = null;
        this.lastName = null;
        this.email = null;
        this.loginTime = null;
        this.studentInfoBean = null;
        this.staffInfoBean = null;
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

    // Student / staff profile -----------------------------------------------

    /**
     * The logged-in student's SIS profile (CommonMapper.getStudentInfo), or
     * null for staff users / anonymous sessions.
     */
    public StudentInfoBean getStudentInfoBean() {
        return studentInfoBean;
    }

    public void setStudentInfoBean(StudentInfoBean studentInfoBean) {
        this.studentInfoBean = studentInfoBean;
    }

    /**
     * The logged-in staff member's SIS profile (CommonMapper.getStaffInfo),
     * or null for students / anonymous sessions.
     */
    public StaffInfoBean getStaffInfoBean() {
        return staffInfoBean;
    }

    public void setStaffInfoBean(StaffInfoBean staffInfoBean) {
        this.staffInfoBean = staffInfoBean;
    }

    // Locale / language -----------------------------------------------------

    /** Current UI locale (never {@code null}; falls back to English). */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Sets the current UI locale and, when called inside a JSF request,
     * immediately applies it to the current {@code UIViewRoot} so the rest of
     * the render response (resource bundle texts, PrimeFaces widgets) already
     * uses the new locale.
     */
    public void setLocale(Locale locale) {
        this.locale = locale != null ? locale : DEFAULT_LOCALE;
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext != null && facesContext.getViewRoot() != null) {
            facesContext.getViewRoot().setLocale(this.locale);
        }
    }

    /** Language code of the current locale ("en" or "ar") - selectOneMenu binding. */
    public String getLanguage() {
        return locale.getLanguage();
    }

    /**
     * Sets the locale from a language code. Unknown/unsupported codes are
     * ignored (the current locale is kept), so a stale browser value can
     * never break rendering.
     */
    public void setLanguage(String language) {
        setLocale(localeFor(language));
    }

    /**
     * Language switcher action (login screen and settings screen): applies
     * the given language code and returns {@code null} so the current view is
     * re-rendered in the new language and direction without navigation.
     */
    public String switchLanguage(String language) {
        setLocale(localeFor(language));
        return null; // re-render current view
    }

    /** Arabic (RTL) or English/anything else (LTR). */
    public boolean isRtl() {
        return ARABIC_LOCALE.getLanguage().equals(locale.getLanguage());
    }

    /** "rtl" when the current locale is Arabic, otherwise "ltr" - html dir binding. */
    public String getDirection() {
        return isRtl() ? "rtl" : "ltr";
    }

    /** Resolves a language code to a supported locale (unknown -> current locale). */
    private Locale localeFor(String language) {
        if (language != null && !language.isBlank()) {
            Locale candidate = Locale.forLanguageTag(language.trim());
            for (Locale supported : SUPPORTED_LOCALES) {
                if (supported.getLanguage().equals(candidate.getLanguage())) {
                    return supported;
                }
            }
        }
        return locale;
    }
}