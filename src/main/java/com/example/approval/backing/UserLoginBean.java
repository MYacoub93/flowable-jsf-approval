package com.example.approval.backing;

import com.example.approval.config.SpringCdiBridge;
import com.example.approval.service.FlowableIdentityService;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import org.flowable.idm.api.User;

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
 * through the same inheritance instead of a per-bean injection. On logout the
 * session info is cleared before the HTTP session is invalidated.
 */
@Named("loginBean")
@SessionScoped
public class UserLoginBean extends BaseBackingBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private User currentUser;

    private FlowableIdentityService identityService() {
        return SpringCdiBridge.getBean(FlowableIdentityService.class);
    }

    public String login() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Username and password are required");
            return null;
        }

        String loginName = username.trim();

        // Load the user (with password) straight from the database via the mapper SELECT
        User dbUser = identityService().findUserByUsernameForAuth(loginName);
        if (dbUser == null) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Invalid username or password");
            return null;
        }

        // Compare the submitted password with the value returned by the query
        if (!password.equals(dbUser.getPassword())) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Invalid username or password");
            return null;
        }

        // The password was only needed for the comparison above - do not keep
        // it in the session-backed user object.
        dbUser.setPassword(null);

        this.currentUser = dbUser;
        this.username = currentUser.getFirstName();

        // Snapshot the authenticated user's information for every backing bean
        getSessionInfo().populate(dbUser, loginName);

        addMessage(FacesMessage.SEVERITY_INFO, "Welcome " + currentUser.getFirstName());
        return "/dashboard?faces-redirect=true";
    }

    @Override
    public String logout() {
        getSessionInfo().clear(); // drop the session information first
        this.currentUser = null;
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

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }
}