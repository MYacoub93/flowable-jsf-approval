package com.example.approval.config;

import com.example.approval.backing.UserLoginBean;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.CDI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration.
 *
 * The JSF pages are served by the FacesServlet (configured by JoinFaces) under
 * patterns such as {@code *.xhtml}. The application root ({@code /}), however,
 * is not one of those patterns, and Spring Boot's built-in welcome-page support
 * only auto-detects a static {@code index.html} (this project only ships
 * {@code index.xhtml}). Without an explicit mapping the root URL therefore falls
 * through to Spring Boot's default error handler and shows the White Label Error
 * Page.
 *
 * We fix that by redirecting the root path to the JSF login page.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Issue an HTTP 302 from "/" to the FacesServlet-backed login view.
        registry.addRedirectViewController("/", "/login.xhtml");
    }

    /**
     * Spring bean named {@code loginBean} that exposes the CDI-managed
     * {@link UserLoginBean} to everything that lives in the Spring context.
     *
     * The login bean itself is a CDI {@code @Named @SessionScoped} bean
     * (Weld, provided by JoinFaces). The other backing beans are Spring
     * {@code @Component} beans that inject {@code UserLoginBean} via
     * {@code @Autowired}, and the JoinFaces EL resolver chain also consults
     * the Spring context, so Spring must still offer a bean under the
     * {@code loginBean} name. This factory returns the CDI contextual
     * instance of the current HTTP session, so Spring-backed beans and the
     * XHTML pages all share the same per-session login state; Spring merely
     * stores the CDI instance as its session-scoped target (no CGLIB proxy
     * around the user state itself).
     *
     * Fallback: when the application runs from the packaged fat jar, Weld
     * cannot generate bean definitions for application classes
     * (WELD-000119 "wrong name" BOOT-INF loading errors - a pre-existing
     * limitation of this setup). In that mode {@code CDI.current().select()}
     * finds no bean, and this factory falls back to a plain instance. That
     * works because UserLoginBean keeps no container-managed field state; it
     * reaches its Spring services lazily through {@link SpringCdiBridge}.
     */
    @Bean("loginBean")
    @SessionScope
    public UserLoginBean loginBean() {
        try {
            return CDI.current().select(UserLoginBean.class).get();
        } catch (UnsatisfiedResolutionException | ContextNotActiveException | IllegalStateException e) {
            // No CDI bean definition available (packaged-jar Weld discovery
            // limitation), no active CDI session context, or no CDI runtime
            // at all - fall back to a plain instance managed by Spring's
            // session scope.
            return new UserLoginBean();
        }
    }
}