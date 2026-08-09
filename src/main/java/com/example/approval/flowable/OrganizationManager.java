package com.example.approval.flowable;


import org.flowable.engine.IdentityService;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrganizationManager {

    public static final String ORG_GROUP_TYPE = "assignment";
    public static final String ROLE_GROUP_TYPE = "security-role";


    private static final String INFO_MANAGER_ID = "managerId";
    private static final String INFO_TITLE = "title";
    private static final String INFO_MOBILE = "mobile";
    private static final String INFO_ADDRESS = "address";

    @Autowired
    private IdentityService identityService;


    //Groups Equivelant
    public Group getGroupByName(String id) {
        return identityService.createGroupQuery().groupId(id).groupType(ORG_GROUP_TYPE).singleResult();
    }

    public Group createGroup(String id, String displayName, String description) {
        Group group = identityService.newGroup(id);
        group.setName(displayName);
        group.setType(ORG_GROUP_TYPE);
        identityService.saveGroup(group);
        return group;
    }



    //Role Equivelant
    public Group getRoleByName(String code) {
        return identityService.createGroupQuery().groupId(code).groupType(ROLE_GROUP_TYPE).singleResult();
    }

    public Group createRole(String code, String displayName) {
        Group role = identityService.newGroup(code);
        role.setName(displayName);
        role.setType(ROLE_GROUP_TYPE);
        identityService.saveGroup(role);
        return role;
    }



    /////////////////////////////Users

    public User getUserByUsername(String username) {
        return identityService.createUserQuery().userId(username).singleResult();
    }

    public User createUser(String username, String password, String firstName, String lastName,
                           String title, String email, String mobile, String address) {
        User user = identityService.newUser(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        // Flowable stores this as-is unless you've configured a password
        // encoder on the IdentityService - wire one up before going to prod.
        user.setPassword(password);
        identityService.saveUser(user);

        if (title != null) {
            identityService.setUserInfo(username, INFO_TITLE, title);
        }
        if (mobile != null) {
            identityService.setUserInfo(username, INFO_MOBILE, mobile);
        }
        if (address != null) {
            identityService.setUserInfo(username, INFO_ADDRESS, address);
        }
        return user;
    }

    public User updateUser(User user, String firstName, String lastName, String title, String email,
                           String mobile, String address) {
        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }
        if (email != null) {
            user.setEmail(email);
        }
        identityService.saveUser(user);

        if (title != null) {
            identityService.setUserInfo(user.getId(), INFO_TITLE, title);
        }
        if (mobile != null) {
            identityService.setUserInfo(user.getId(), INFO_MOBILE, mobile);
        }
        if (address != null) {
            identityService.setUserInfo(user.getId(), INFO_ADDRESS, address);
        }
        return user;
    }

    public void setManager(String userId, String managerUserId) {
        identityService.setUserInfo(userId, INFO_MANAGER_ID, managerUserId);
    }


    // ---------- Memberships ----------

    /**
     * Equivalent of Bonita's assignMembershipToUser(user, group, role): links
     * the user to the org-unit group (if any) and the role group (if any).
     * Either may be null, e.g. a plain role assignment with no specific dept.
     */
    public void assignMembershipToUser(String userId, Group orgGroup, Group roleGroup) {
        if (orgGroup != null && !isMember(userId, orgGroup.getId())) {
            identityService.createMembership(userId, orgGroup.getId());
        }
        if (roleGroup != null && !isMember(userId, roleGroup.getId())) {
            identityService.createMembership(userId, roleGroup.getId());
        }
    }

    public boolean isMember(String userId, String groupId) {
        return identityService.createUserQuery().userId(userId).memberOfGroup(groupId).count() > 0;
    }

}
