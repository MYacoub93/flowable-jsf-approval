package com.example.approval.flowable.identity;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.syncronizer.SISOC;
import org.flowable.idm.api.Group;
import org.flowable.idm.engine.IdmEngineConfiguration;
import org.flowable.idm.engine.impl.GroupQueryImpl;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntity;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntityManagerImpl;
import org.flowable.idm.engine.impl.persistence.entity.data.GroupDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

public class CustomGroupEntityManager extends GroupEntityManagerImpl {

    private static final Logger log = LoggerFactory.getLogger(CustomGroupEntityManager.class);

    private final FlowableIdentityMapper identityMapper;

    public CustomGroupEntityManager(IdmEngineConfiguration idmEngineConfiguration,
                                    GroupDataManager groupDataManager,
                                    FlowableIdentityMapper identityMapper) {
        super(idmEngineConfiguration, groupDataManager);
        this.identityMapper = identityMapper;
    }

    @Override
    public List<Group> findGroupsByUser(String userId) {
        // تحويل List<GroupEntityImpl> إلى List<Group>
        return (List) identityMapper.findGroupsByUser(userId);
    }


    @Override
    public List<Group> findGroupByQueryCriteria(GroupQueryImpl query) {
        if (query.getUserId() != null) {
            List<Group> groups = (List) identityMapper.findGroupsByUser(query.getUserId());
            log.info("findGroupsByUser({}) returned {} rows: {}",
                    query.getUserId(), groups.size(), groups);
            return groups;
        }
        return super.findGroupByQueryCriteria(query);
    }
    @Override
    public long findGroupCountByQueryCriteria(GroupQueryImpl query) {
        return findGroupByQueryCriteria(query).size();
    }

    @Override
    public void insert(GroupEntity entity) {
        throw new UnsupportedOperationException("Groups are managed by SIS View directly.");
    }
}