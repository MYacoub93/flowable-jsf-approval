package com.example.approval.service;

import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.mapper.UserMapper;
import org.flowable.engine.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for User operations.
 * Used by JSF beans and by ApprovalService for assignee resolution.
 */
@Service
@Transactional(readOnly = true)
public class FlowableIdentityService {

    @Autowired
    private FlowableIdentityMapper identityService;







}
