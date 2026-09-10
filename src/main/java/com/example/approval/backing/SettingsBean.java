package com.example.approval.backing;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.Serializable;

/**
 * Request-scoped backing bean for the Settings screen
 * ({@code settings.xhtml}).
 *
 * <p>The screen currently offers exactly one setting: the display language.
 * The language state itself does NOT live here - it lives in the
 * session-scoped {@link SessionInfoBean} (the session's single source of
 * truth for the locale, shared with the login screen's language switcher).
 * This bean only provides the "Apply" action:</p>
 *
 * <ol>
 *   <li>The {@code p:selectOneMenu} on the page posts the selected language
 *       code into {@code sessionInfoBean.language}
 *       ({@code setLanguage} -> {@code setLocale}, which also applies the
 *       locale to the current {@code UIViewRoot}).</li>
 *   <li>{@link #apply()} adds a localized confirmation message and returns
 *       {@code null}; the button uses {@code ajax="false"}, so the page is
 *       fully re-rendered in the new language and direction immediately.</li>
 * </ol>
 *
 * <p>Managed like the other page beans: a Spring {@code @Component} in
 * {@code @RequestScope} extending {@link BaseBackingBean} (same pattern as
 * {@code DashboardBean} etc.).</p>
 */
@Component("settingsBean")
@RequestScope
public class SettingsBean extends BaseBackingBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Confirms the language change made through the Settings screen.
     *
     * <p>The new locale was already stored by the selectOneMenu binding
     * (JSF "Update Model Values" phase ran before this action), so this
     * method only reports success in the (new) current language and lets
     * the non-AJAX submit re-render the page.</p>
     */
    public String apply() {
        addMessage(FacesMessage.SEVERITY_INFO, getLabel("settings.saved"));
        return null; // stay on the page; ajax=false re-renders with new locale + dir
    }

    private void addMessage(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }
}