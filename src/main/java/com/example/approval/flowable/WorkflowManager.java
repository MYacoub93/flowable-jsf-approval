package com.example.approval.flowable;

import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.idm.api.Group;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkflowManager {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private IdentityService identityService;
    /**
            * جلب العمليات المنشورة والنشطة التي يحق للمستخدم الحالي إطلاقها
     */
    public List<ProcessDefinition> getProcessesUserCanStart(String username) {

        // 1. جلب المجموعات/الأدوار التي ينتمي إليها المستخدم من Flowable Identity Service
        List<String> userGroupIds = identityService.createGroupQuery()
                .groupMember(username)
                .list()
                .stream()
                .map(Group::getId)
                .toList();

        // 2. البحث عن العمليات المتاحة للمستخدم صراحة أو من خلال إحدى مجموعاته
        return repositoryService.createProcessDefinitionQuery()
                .startableByUserOrGroups(username, userGroupIds) // الفلترة حسب المستخدم ومجموعاته
                .latestVersion()                                 // أحدث إصدار منشور
                .active()                                        // العمليات النشطة فقط
                .orderByProcessDefinitionName()
                .asc()
                .list();
    }
}
