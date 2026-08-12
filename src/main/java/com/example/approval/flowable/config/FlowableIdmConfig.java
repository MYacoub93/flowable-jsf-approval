package com.example.approval.flowable.config;

import com.example.approval.flowable.identity.CustomGroupEntityManager;
import com.example.approval.flowable.identity.CustomUserEntityManager;
import com.example.approval.mapper.FlowableIdentityMapper;
import org.flowable.idm.engine.IdmEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlowableIdmConfig implements EngineConfigurationConfigurer<IdmEngineConfiguration> {


    private final FlowableIdentityMapper identityMapper;

    public FlowableIdmConfig(FlowableIdentityMapper identityMapper) {
        this.identityMapper = identityMapper;
    }

    @Override
    public void configure(IdmEngineConfiguration idmConfig) {
        CustomUserEntityManager customUserEntityManager = new CustomUserEntityManager(
                idmConfig,
                idmConfig.getUserDataManager(),
                identityMapper);

        CustomGroupEntityManager customGroupEntityManager = new CustomGroupEntityManager(
                idmConfig,
                idmConfig.getGroupDataManager(),
                identityMapper);

        idmConfig.setUserEntityManager(customUserEntityManager);
        idmConfig.setGroupEntityManager(customGroupEntityManager);
    }
}