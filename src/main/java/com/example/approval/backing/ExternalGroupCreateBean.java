package com.example.approval.backing;

import com.example.approval.entity.WebRole;
import com.example.approval.service.ExternalGroupService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.Serializable;

/**
 * Backing bean for the "Create External Group" page
 * ({@code /external-group-create.xhtml}).
 *
 * <p>Only members of the {@code ADM} external role see the page at all
 * ({@code isGroupAdmin}); the service re-checks before writing.</p>
 */
@Component("externalGroupCreateBean")
@RequestScope
public class ExternalGroupCreateBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    @Autowired
    private ExternalGroupService groupService;

    @Autowired
    private UserLoginBean loginBean;

    private WebRole role = new WebRole();

    /** Guard against double-click double submits. */
    private boolean submitted;

    private boolean groupAdmin;

    @PostConstruct
    public void init() {
        groupAdmin = loginBean.isLoggedIn() && groupService.isGroupAdmin(currentFlowableUserId());
    }

    /** Creates the role; stays on the page with a success message and a reset form. */
    public String save() {
        if (!groupAdmin) {
            addMessage(FacesMessage.SEVERITY_ERROR,
                    "You are not allowed to manage external groups");
            return null;
        }
        if (submitted) {
            return null; // double submit guard
        }
        submitted = true;
        try {
            WebRole created = groupService.createRole(role, actorUserId(), currentFlowableUserId());
            addMessage(FacesMessage.SEVERITY_INFO, "Group '" + created.getRoleName()
                    + "' created successfully (id " + created.getRoleId() + ")");
            role = new WebRole(); // reset the form
        } catch (ExternalGroupService.DuplicateRoleCodeException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, e.getMessage());
        } catch (IllegalArgumentException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, e.getMessage());
        } catch (SecurityException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, e.getMessage());
        } catch (Exception e) {
            addMessage(FacesMessage.SEVERITY_ERROR,
                    "Could not create the group: " + e.getMessage());
        }
        submitted = false;
        return null; // postback - stay on page, message in flash for redirect
    }

    /** Navigation to the membership page after a successful create. */
    public String goToMemberships() {
        return "/group-memberships?faces-redirect=true";
    }

    // helpers ------------------------------------------------------------

    private Long actorUserId() {
        try {
            return Long.valueOf(loginBean.getCurrentUser().getId());
        } catch (Exception e) {
            return null;
        }
    }

    private String currentFlowableUserId() {
        return loginBean.isLoggedIn() ? loginBean.getCurrentUser().getId() : null;
    }

    private void addMessage(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }

    // Getters / Setters ---------------------------------------------------

    public WebRole getRole() {
        return role;
    }

    public void setRole(WebRole role) {
        this.role = role;
    }

    public boolean isGroupAdmin() {
        return groupAdmin;
    }
}