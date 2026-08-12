package com.example.approval.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntityImpl;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;
import java.util.List;

@Mapper
public interface FlowableIdentityMapper {
    UserEntityImpl findUserByUsernameForAuth(String username);
    UserEntityImpl findUserById(String id);
    UserEntityImpl findUserByUsername(String username);
    List<GroupEntityImpl> findGroupsByUser(String userId);
}