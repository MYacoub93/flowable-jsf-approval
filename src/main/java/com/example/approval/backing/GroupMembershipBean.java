package com.example.approval.backing;

import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.GroupMembership;
import com.example.approval.entity.PageResult;
import com.example.approval.entity.WebRole;
import com.example.approval.service.ExternalGroupService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

/**
 * Backing bean for the "Group Memberships" page
 * ({@code /group-memberships.xhtml}).
 *
 * <p>State (selected group, selected user, search term, current page) must
 * survive the pagination postbacks, so the bean uses the JoinFaces
 * {@code view} scope ({@code @Scope("view")}) rather than the request scope
 * of the read-only pages.</p>
 *
 * <p>Users come from {@code FLOWABLE_USERS_VW} strictly page-by-page
 * (server-side {@code OFFSET/FETCH}); the 31k+ rows are never loaded in full.</p>
 */
@Component("groupMembershipsBean")
@Scope("view")
public class GroupMembershipBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private ExternalGroupService groupService;

    @Autowired
    private UserLoginBean loginBean;

    private boolean groupAdmin;
    private List<WebRole> roles;
    private Long selectedRoleId;

    // user search / pagination state
    private String searchTerm;
    private int page = 1;
    private int pageSize = ExternalGroupService.DEFAULT_PAGE_SIZE;
    private long totalCount;
    private int totalPages;
    private List<ExternalUser> users;

    /** View ID_ is a numeric string (VARCHAR2) - kept as String for EL. */
    private String selectedUserId;

    // members of the selected group
    private List<GroupMembership> members;

    /** Guard against double-click double submits. */
    private boolean submitting;

    @PostConstruct
    public void init() {
        groupAdmin = loginBean.isLoggedIn() && groupService.isGroupAdmin(currentFlowableUserId());
        if (groupAdmin) {
            roles = groupService.findAllRoles();
            loadUsers();
        }
    }

    // ------------------------------------------------------------------
    // Group selection
    // ------------------------------------------------------------------

    /** Ajax listener for the group dropdown: refresh the members table. */
    public void onGroupChange(AjaxBehaviorEvent event) {
        loadMembers();
    }

    private void loadMembers() {
        members = groupService.findMembershipsOfRole(selectedRoleId);
    }

    // ------------------------------------------------------------------
    // User search + server-side pagination
    // ------------------------------------------------------------------

    /** Search button: back to page 1 with the new term. */
    public void search() {
        page = 1;
        loadUsers();
    }

    public void firstPage() {
        page = 1;
        loadUsers();
    }

    public void previousPage() {
        if (page > 1) {
            page--;
            loadUsers();
        }
    }

    public void nextPage() {
        if (page < totalPages) {
            page++;
            loadUsers();
        }
    }

    public void lastPage() {
        page = totalPages;
        loadUsers();
    }

    private void loadUsers() {
        PageResult<ExternalUser> result = groupService.findUsers(page, pageSize, searchTerm);
        users = result.getRows();
        totalCount = result.getTotalRows();
        totalPages = result.getLastPage();
        page = result.getPageNumber(); // service clamps out-of-range pages
    }

    public boolean isHasUsers() {
        return users != null && !users.isEmpty();
    }

    public boolean isHasPrevious() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < totalPages;
    }

    /** Footer label, e.g. "1-10 of 31,540 (page 1 of 3,154)". */
    public String getRangeLabel() {
        if (!isHasUsers()) {
            return "0 of 0";
        }
        long first = (long) (page - 1) * pageSize + 1;
        long last = Math.min((long) page * pageSize, totalCount);
        return String.format("%d-%d of %d (page %d of %d)", first, last, totalCount, page, totalPages);
    }

    // ------------------------------------------------------------------
    // User selection
    // ------------------------------------------------------------------

    /**
     * Row "Select" button action. The value itself is set by the
     * {@code f:setPropertyActionListener} on the button; this method just
     * confirms the selection to the user.
     */
    public void selectUser() {
        if (selectedUserId != null) {
            addMessage(FacesMessage.SEVERITY_INFO, "User " + selectedUserId + " selected");
        }
    }

    // ------------------------------------------------------------------
    // Add membership
    // ------------------------------------------------------------------

    /** "Add Selected User to Group" - validates selection, delegates to service. */
    public String addUserToGroup() {
        if (!groupAdmin) {
            addMessage(FacesMessage.SEVERITY_ERROR, "You are not allowed to manage external groups");
            return null;
        }
        if (submitting) {
            return null; // double submit guard
        }
        submitting = true;
        try {
            if (selectedRoleId == null) {
                addMessage(FacesMessage.SEVERITY_WARN, "Please select a group first");
                return null;
            }
            Long userId = parseUserId(selectedUserId);
            if (userId == null) {
                addMessage(FacesMessage.SEVERITY_WARN, "Please select a user first");
                return null;
            }
            groupService.addMembership(selectedRoleId, userId, actorUserId(), currentFlowableUserId());
            addMessage(FacesMessage.SEVERITY_INFO, "User added to the group successfully");
            selectedUserId = null; // clear selection
            loadMembers();        // show the new member immediately
        } catch (ExternalGroupService.MembershipExistsException e) {
            addMessage(FacesMessage.SEVERITY_WARN, e.getMessage());
        } catch (IllegalArgumentException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, e.getMessage());
        } catch (SecurityException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, e.getMessage());
        } catch (Exception e) {
            addMessage(FacesMessage.SEVERITY_ERROR,
                    "Could not add the user to the group: " + e.getMessage());
        } finally {
            submitting = false;
        }
        return null; // postback - stay on the page
    }

    // helpers ------------------------------------------------------------

    private static Long parseUserId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long actorUserId() {
        try {
            return Long.valueOf(loginBean.getCurrentUser().getId());
        } catch (Exception e) {
            return null; // Flowable ids are usernames here; service resolves numeric id
        }
    }

    private String currentFlowableUserId() {
        return loginBean.isLoggedIn() ? loginBean.getCurrentUser().getId() : null;
    }

    private void addMessage(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }

    // Getters / Setters ---------------------------------------------------

    public boolean isGroupAdmin() {
        return groupAdmin;
    }

    public List<WebRole> getRoles() {
        return roles;
    }

    public Long getSelectedRoleId() {
        return selectedRoleId;
    }

    public void setSelectedRoleId(Long selectedRoleId) {
        this.selectedRoleId = selectedRoleId;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    public List<ExternalUser> getUsers() {
        return users;
    }

    public String getSelectedUserId() {
        return selectedUserId;
    }

    public void setSelectedUserId(String selectedUserId) {
        this.selectedUserId = selectedUserId;
    }

    public List<GroupMembership> getMembers() {
        return members;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}
