package com.example.approval.config;

import org.springframework.context.annotation.Configuration;
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
}