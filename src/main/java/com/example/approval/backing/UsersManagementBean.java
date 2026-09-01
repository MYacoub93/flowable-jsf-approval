package com.example.approval.backing;

import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.PageResult;
import com.example.approval.service.ExternalGroupService;
import com.example.approval.service.FlowableIdentityService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntityImpl;
import org.primefaces.event.SelectEvent;
import org.primefaces.event.UnselectEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Backing bean for the admin "Users Management" page
 * ({@code /users_managment.xhtml}).
 *
 * <p>The page lists all users of {@code FLOWABLE_USERS_VW} by default
 * (server-side {@code OFFSET/FETCH} pagination - the 31k+ rows are never
 * loaded in full), lets the admin filter them with the existing search
 * ({@link ExternalGroupService#findUsers}), and shows the group
 * memberships of the row selected in the table. Groups are resolved live
 * from {@code flowable_groups_vw} through the existing
 * {@code FlowableIdentityMapper.findGroupsByUser} query - no new SQL was
 * added for this feature.</p>
 *
 * <p>Admin authorization follows the existing mechanism: the Administration
 * menu and the page body are rendered only for members of the {@code ADM}
 * Flowable group (see {@link ExternalGroupService#isGroupAdmin}), and the
 * actions re-check the role server-side so a direct URL visit by a
 * non-admin cannot execute any lookup.</p>
 */
@Component("usersManagementBean")
@Scope("view")
public class UsersManagementBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private ExternalGroupService groupService;

    @Autowired
    private FlowableIdentityService identityService;

    @Autowired
    private UserLoginBean loginBean;

    /** Whether the logged-in user is allowed to use this page (ADM group). */
    private boolean groupAdmin;

    // user search / pagination state (same pattern as GroupMembershipBean)
    private String searchTerm;
    private int page = 1;
    private int pageSize = ExternalGroupService.DEFAULT_PAGE_SIZE;
    private long totalCount;
    private int totalPages;
    private List<ExternalUser> users;

    /** The row clicked in the users table - the full row object, not just the id. */
    private ExternalUser selectedUser;

    /** Group memberships of {@link #selectedUser} (id / name / type), never null. */
    private List<GroupEntityImpl> groups = Collections.emptyList();

    @PostConstruct
    public void init() {
        groupAdmin = loginBean.isLoggedIn() && groupService.isGroupAdmin(currentFlowableUserId());
        if (groupAdmin) {
            loadUsers(); // list all users (first page) by default
        }
    }

    // ------------------------------------------------------------------
    // User search + server-side pagination
    // ------------------------------------------------------------------

    /** Search button: back to page 1 with the new term. */
    public void search() {
        if (!groupAdmin) {
            addMessage(FacesMessage.SEVERITY_ERROR, "You are not authorized to manage users");
            return;
        }
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
    // Row selection -> group memberships
    // ------------------------------------------------------------------

    /** Ajax listener for the table row selection: load the user's groups. */
    public void onRowSelect(SelectEvent event) {
        Object object = event.getObject();
        if (object instanceof ExternalUser) {
            selectedUser = (ExternalUser) object;
            loadGroups();
        }
    }

    /** Ajax listener when the row selection is cleared again. */
    public void onRowUnselect(UnselectEvent event) {
        selectedUser = null;
        groups = Collections.emptyList();
    }

    private void loadGroups() {
        groups = Collections.emptyList();
        try {
            groups = identityService.findGroupsOfUser(selectedUser.getId());
            addMessage(FacesMessage.SEVERITY_INFO, "User '" + displayUsername() + "' has "
                    + groups.size() + (groups.size() == 1 ? " group membership" : " group memberships"));
        } catch (Exception e) {
            addMessage(FacesMessage.SEVERITY_ERROR,
                    "Could not load group memberships: " + e.getMessage());
        }
    }

    // helpers ---------------------------------------------------------------

    private String displayUsername() {
        return selectedUser != null && selectedUser.getUsername() != null
                ? selectedUser.getUsername() : selectedUser.getId();
    }

    private String currentFlowableUserId() {
        return loginBean.isLoggedIn() ? loginBean.getCurrentUser().getId() : null;
    }

    private void addMessage(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }

    // Getters / Setters -----------------------------------------------------

    public boolean isGroupAdmin() {
        return groupAdmin;
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

    public ExternalUser getSelectedUser() {
        return selectedUser;
    }

    public void setSelectedUser(ExternalUser selectedUser) {
        this.selectedUser = selectedUser;
    }

    public List<GroupEntityImpl> getGroups() {
        return groups;
    }

    public boolean isHasGroups() {
        return groups != null && !groups.isEmpty();
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}