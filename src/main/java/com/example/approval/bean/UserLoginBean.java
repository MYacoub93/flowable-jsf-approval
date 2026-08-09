package com.example.approval.bean;

import com.example.approval.entity.SystemUser;
import com.example.approval.entity.User;
import com.example.approval.service.UserService;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

/**
 * JSF session-scoped bean for simple username-based login.
 * Stores the authenticated User in the HTTP session for the whole conversation.
 *
 * Note: JoinFaces + CDI (Weld) is used; @Named + @SessionScoped works together with Spring.
 * We also expose it as a Spring component for potential injection elsewhere.
 */
@Named("loginBean")
@SessionScoped
@Component
public class UserLoginBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private UserService userService;

    private String username;
    private SystemUser currentUser;

    public String login() {
        if (username == null || username.isBlank()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Username is required");
            return null;
        }
        Optional<SystemUser> userOpt = userService.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Unknown user: " + username);
            return null;
        }
        this.currentUser = userOpt.get();
        this.username = currentUser.getUsername();
        addMessage(FacesMessage.SEVERITY_INFO, "Welcome " + currentUser.getUsername());
        return "/dashboard?faces-redirect=true";
    }

    public String logout() {
        this.currentUser = null;
        this.username = null;
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

    public SystemUser getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(SystemUser currentUser) {
        this.currentUser = currentUser;
    }
}
