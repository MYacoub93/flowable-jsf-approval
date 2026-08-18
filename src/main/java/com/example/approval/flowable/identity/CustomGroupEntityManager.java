package com.example.approval.flowable.identity;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.syncronizer.SISOC;
import org.flowable.idm.api.Group;
import org.flowable.idm.engine.IdmEngineConfiguration;
import org.flowable.idm.engine.impl.GroupQueryImpl;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntity;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntityImpl;
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
            List<GroupEntityImpl> groups = new java.util.ArrayList<>(
                    identityMapper.findGroupsByUser(query.getUserId()));
            // honor an additional groupId filter, e.g. groupMember(u).groupId("STD")
            if (query.getId() != null) {
                groups.removeIf(g -> !query.getId().equals(g.getId()));
            }
            log.info("findGroupsByUser({}) filtered by groupId={} returned {} rows",
                    query.getUserId(), query.getId(), groups.size());
            return (List) groups;
        }
        // groupId-only lookups (e.g. candidate-starter-group checks) are served
        // from the SIS view as well - the engine's own group tables are unused.
        if (query.getId() != null) {
            List<GroupEntityImpl> groups = new java.util.ArrayList<>(
                    identityMapper.findGroupById(query.getId()));
            if (query.getType() != null) {
                groups.removeIf(g -> !query.getType().equals(g.getType()));
            }
            return (List) groups;
        }
        // The identity store is read-only and external; any other combination
        // (untyped listing, name search, ...) yields nothing instead of NPE-ing
        // on the engine dataManager, which is not initialized in this setup.
        log.debug("Unsupported group query (userId={}, groupId={}, name={}) -> empty result",
                query.getUserId(), query.getId(), query.getName());
        return List.of();
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