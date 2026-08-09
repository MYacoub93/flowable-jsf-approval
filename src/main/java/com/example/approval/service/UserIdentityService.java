package com.example.approval.service;

import com.example.approval.entity.User;
import org.flowable.engine.IdentityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for User operations.
 * Used by JSF beans and by ApprovalService for assignee resolution.
 */
@Service
@Transactional(readOnly = true)
public class UserIdentityService {

    private final IdentityService identityService;

    public UserIdentityService(IdentityService identityService) {
        this.identityService = identityService;
    }

    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        org.flowable.idm.api.User flowableUser = identityService.createUserQuery()
                .userId(username.trim())
                .singleResult();

        return Optional.ofNullable(mapToEntity(flowableUser));
    }

    public User findManagerByDepartment(String department) {
        // بافتراض أن مدراء الأقسام مسجلون في Flowable كمجموعة بصيغة: DEPARTMENT_MANAGER
        String groupId = department.toUpperCase() + "_MANAGER";

        org.flowable.idm.api.User manager = identityService.createUserQuery()
                .memberOfGroup(groupId)
                .singleResult();

        if (manager == null) {
            throw new IllegalStateException("No active manager found for department: " + department);
        }
        return mapToEntity(manager);
    }

    public User findFinanceApprover() {
        // بافتراض وجود مجموعة خاصة بالموافقات المالية في Flowable باسم FINANCE_APPROVER
        org.flowable.idm.api.User finance = identityService.createUserQuery()
                .memberOfGroup("FINANCE_APPROVER")
                .singleResult();

        if (finance == null) {
            throw new IllegalStateException("No active finance approver found");
        }
        return mapToEntity(finance);
    }

    public List<User> findAllActive() {
        // Flowable لا يمتلك حقل "نشط/غير نشط" افتراضياً، لذا نستدعي جميع المستخدمين
        List<org.flowable.idm.api.User> flowableUsers = identityService.createUserQuery().list();

        return flowableUsers.stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
    }

    public List<User> findByRole(String role) {
        List<org.flowable.idm.api.User> flowableUsers = identityService.createUserQuery()
                .memberOfGroup(role)
                .list();

        return flowableUsers.stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
    }

    /**
     * دالة مساعدة لتحويل مستخدم Flowable إلى كيان المستخدم الخاص بالتطبيق (Entity)
     */
    private User mapToEntity(org.flowable.idm.api.User flowableUser) {
        if (flowableUser == null) {
            return null;
        }
        User user = new User();
        // عادة ما يُمثل الـ ID في Flowable اسم المستخدم (Username)
        user.setUsername(flowableUser.getId());
        user.setFullName(flowableUser.getFirstName() + flowableUser.getLastName());

        user.setEmail(flowableUser.getEmail());

        return user;
    }
}