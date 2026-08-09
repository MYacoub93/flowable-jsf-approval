package com.example.approval.service;

import com.example.approval.entity.SystemUser;
import com.example.approval.entity.User;
import com.example.approval.mapper.UserMapper;
import org.flowable.engine.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for User operations.
 * Used by JSF beans and by ApprovalService for assignee resolution.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    @Autowired
    private IdentityService identityService;

    @Autowired
    private UserMapper userMapper;


    public Optional<SystemUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.findByUsername(username.trim()));
    }

    /**
     * مزامنة المستخدم من قاعدة بيانات MyBatis إلى نظام IdentityService الخاص بـ Flowable
     */
    public void syncUserToFlowable(SystemUser appUser) {
        // التحقق مما إذا كان المستخدم موجوداً بالفعل في Flowable
        org.flowable.idm.api.User flowableUser = identityService.createUserQuery()
                .userId(appUser.getUsername())
                .singleResult();

        if (flowableUser == null) {
            // إنشاء مستخدم جديد في Flowable Identity Database
            flowableUser = identityService.newUser(appUser.getUsername());
//            flowableUser.setFirstName(appUser.getFirstName());
//            flowableUser.setLastName(appUser.getLastName());
//            flowableUser.setEmail(appUser.getEmail());

            identityService.saveUser(flowableUser);
        }
    }


}
