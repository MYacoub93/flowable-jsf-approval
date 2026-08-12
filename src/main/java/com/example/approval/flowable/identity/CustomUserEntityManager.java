package com.example.approval.flowable.identity;


import com.example.approval.mapper.FlowableIdentityMapper;
import org.flowable.idm.api.User;
import org.flowable.idm.engine.IdmEngineConfiguration;
import org.flowable.idm.engine.impl.persistence.entity.UserEntity;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityManagerImpl;
import org.flowable.idm.engine.impl.persistence.entity.data.UserDataManager;
import org.springframework.stereotype.Component;

import java.util.List;

public class CustomUserEntityManager extends UserEntityManagerImpl {

    private final FlowableIdentityMapper identityMapper;

    public CustomUserEntityManager(IdmEngineConfiguration idmEngineConfiguration,
                                   UserDataManager userDataManager,
                                   FlowableIdentityMapper identityMapper) {
        super(idmEngineConfiguration, userDataManager);
        this.identityMapper = identityMapper;
    }

    @Override
    public UserEntity findById(String entityId) {
        return identityMapper.findUserById(entityId);
    }

    // تُستخدم عند تسجيل الدخول أو البحث بالاسم
    public UserEntity findUserByQueryCriteria(String username) {
        return identityMapper.findUserByUsername(username);
    }

    // منع الإضافة أو التعديل عبر Flowable (لأن الإدارة تتم من SIS)
    @Override
    public void insert(UserEntity entity) {
        throw new UnsupportedOperationException("User creation is managed by SIS View directly.");
    }

    @Override
    public UserEntity update(UserEntity entity) {
        throw new UnsupportedOperationException("User updates are managed by SIS View directly.");
    }
}