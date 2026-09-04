package com.example.approval.backing;

import com.example.approval.config.SpringCdiBridge;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.CDI;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;

/**
 * Common base class of all JSF backing beans.
 *
 * <p>It gives every backing bean access to the centralized
 * {@link SessionInfoBean} (the current user's session information) through
 * inheritance, so no backing bean has to inject the session info on its own:</p>
 *
 * <pre>{@code
 * public class SomeBackingBean extends BaseBackingBean {
 *     public void someAction() {
 *         String username = getSessionInfo().getUsername();
 *         String userId   = getSessionInfo().getUserId();
 *         // application logic
 *     }
 * }
 * }</pre>
 *
 * <p>Resolution strategy (matches how this application bridges Spring and
 * CDI/JoinFaces):</p>
 * <ol>
 *   <li>The Spring-managed backing beans ({@code @Component} with
 *       {@code @RequestScope}/{@code @Scope("view")}) get the field injected by
 *       Spring. Spring also injects {@code @Autowired} fields declared in a
 *       superclass, and {@code WebConfig} registers a session-scoped Spring
 *       facade of {@code SessionInfoBean} that delegates to the CDI contextual
 *       instance of the current HTTP session - the same instance
 *       {@code UserLoginBean} populated at login.</li>
 *   <li>CDI-managed beans (such as {@code UserLoginBean} itself, created by
 *       Weld) do not get Spring {@code @Autowired} fields injected; for them
 *       {@link #getSessionInfo()} resolves the CDI contextual instance lazily
 *       through {@code CDI.current()}.</li>
 *   <li>Fallback for the packaged-jar mode, where Weld cannot create bean
 *       definitions for application classes: the Spring facade from
 *       {@code WebConfig} (a plain Spring session-scoped instance).</li>
 * </ol>
 *
 * The class is intentionally abstract and carries no annotations of its own,
 * so neither container tries to instantiate it.
 */
public abstract class BaseBackingBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Injected by Spring into every Spring-managed backing bean. Remains
     * {@code null} in CDI-managed beans (Weld ignores Spring annotations);
     * those resolve the session info lazily in {@link #getSessionInfo()}.
     */
    @Autowired
    private SessionInfoBean sessionInfo;

    /**
     * The session information of the currently logged-in user - available to
     * every backing bean through inheritance.
     */
    protected SessionInfoBean getSessionInfo() {
        if (sessionInfo != null) {
            return sessionInfo;
        }
        return resolveSessionInfo();
    }

    private static SessionInfoBean resolveSessionInfo() {
        try {
            return CDI.current().select(SessionInfoBean.class).get();
        } catch (UnsatisfiedResolutionException | ContextNotActiveException | IllegalStateException e) {
            // No CDI bean definition (packaged-jar Weld discovery limitation),
            // no active CDI session context, or no CDI runtime at all - fall
            // back to the Spring facade registered in WebConfig.
            return SpringCdiBridge.getBean(SessionInfoBean.class);
        }
    }

    /**
     * Convenience: whether a user is logged in in the current session.
     * (Public because {@code UserLoginBean} already exposes the same public
     * EL-facing property and overrides it.)
     */
    public boolean isLoggedIn() {
        SessionInfoBean info = getSessionInfo();
        return info != null && info.isLoggedIn();
    }

    /**
     * Convenience: the Flowable user id of the logged-in user
     * ({@code FLOWABLE_USERS_VW.ID_}), or {@code null} when not logged in.
     */
    protected String getCurrentUserId() {
        SessionInfoBean info = getSessionInfo();
        return info != null ? info.getUserId() : null;
    }

    /**
     * Centralized logout: delegates to the existing {@code UserLoginBean}
     * action, which clears the session information and invalidates the HTTP
     * session. Declared once here so backing beans (and the EL expressions
     * that reference their {@code logout} action) share one implementation.
     */
    public String logout() {
        return resolveLoginBean().logout();
    }

    private static UserLoginBean resolveLoginBean() {
        try {
            return CDI.current().select(UserLoginBean.class).get();
        } catch (UnsatisfiedResolutionException | ContextNotActiveException | IllegalStateException e) {
            return SpringCdiBridge.getBean(UserLoginBean.class);
        }
    }
}