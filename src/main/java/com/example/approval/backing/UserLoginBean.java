package com.example.approval.backing;

import com.example.approval.service.FlowableIdentityService;
import org.flowable.idm.api.User;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;

@Component("loginBean")
@SessionScope
public class UserLoginBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private FlowableIdentityService identityService;

    private String username;
    private String password;
    private User currentUser;

    public String login() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Username and password are required");
            return null;
        }

        // جلب المستخدم مع كلمة المرور مفكوكة التشفير مباشرة من قاعدة البيانات عبر الـ SELECT
        User dbUser = identityService.findUserByUsernameForAuth(username.trim());
        if (dbUser == null) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Invalid username or password");
            return null;
        }

        // مقارنة كلمة المرور المدخلة مع القيمة القادمة من الاستعلام
        if (!password.equals(dbUser.getPassword())) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Invalid username or password");
            return null;
        }

        this.currentUser = dbUser;
        this.username = currentUser.getFirstName();
        addMessage(FacesMessage.SEVERITY_INFO, "Welcome " + currentUser.getFirstName());
        return "/dashboard?faces-redirect=true";
    }

    public String logout() {
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