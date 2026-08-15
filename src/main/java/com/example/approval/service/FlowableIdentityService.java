package com.example.approval.service;


import com.example.approval.mapper.FlowableIdentityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.flowable.idm.api.User;
import java.util.Optional;

/**
 * Service layer for User operations.
 * Used by JSF beans and by ApprovalService for assignee resolution.
 */
@Service
@Transactional(readOnly = true)
public class FlowableIdentityService {

    @Autowired
    private FlowableIdentityMapper identityMapper;

    public User findUserByUsernameForAuth(String username) {


        org.flowable.idm.api.User flowableUser = identityMapper.findUserByUsernameForAuth(username);

        return flowableUser;
        //return Optional.ofNullable(flowableUser);
    }





}
