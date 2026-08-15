package com.example.approval.syncronizer;

import com.example.approval.common.CoreConstants;
import com.example.approval.common.utils.DateTimeUtil;
import com.example.approval.common.utils.PredicateUtil;
import com.example.approval.common.utils.StringUtilities;
import com.example.approval.flowable.OrganizationManager;
import com.example.approval.mapper.CommonMapper;
import com.example.approval.origin.beans.*;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable edition of the SIS -&gt; Organization sync job.
 *
 * <p>Ported from a Bonita BPM implementation - see {@link OrganizationManager}
 * for the modeling notes on how Bonita's Group/Role/UserMembership concepts
 * map onto Flowable's flatter IdentityService model.</p>
 *
 * <p>Restructured to follow the same shape as {@code UserSyncService}:</p>
 * <ul>
 *   <li>Runs once on startup (optional) and then on a fixed schedule.</li>
 *   <li>All SIS reads go through {@link CommonMapper} (MyBatis) - no JDBC
 *       template, no manual {@code ApplicationContext} bean lookup.</li>
 *   <li>Faculties/Departments/Roles/Users are pushed into Flowable's
 *       IdentityService via {@link OrganizationManager}.</li>
 * </ul>
 */
@Service
@Order(20)   // after UserSyncService (10) has refreshed the local users table
public class SISOC implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SISOC.class);

    private final CommonMapper commonMapper;
    private final OrganizationManager flowableApi;

    @Value("${app.sync.sis.enabled:false}")
    private boolean syncEnabled;

    @Value("${app.sync.sis.run-on-startup:false}")
    private boolean runOnStartup;

    public SISOC(CommonMapper commonMapper, OrganizationManager flowableApi) {
        this.commonMapper = commonMapper;
        this.flowableApi = flowableApi;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Full synchronisation: Faculties/Departments -&gt; Groups, Roles -&gt;
     * Roles, SIS Users -&gt; Flowable Users (create or update), driven
     * entirely by {@link CommonMapper} reads and {@link OrganizationManager}
     * writes.
     *
     * Returns the number of SIS user rows processed.
     */
    @Transactional
    public int syncOrganization() {
        if (!syncEnabled) {
            log.debug("SIS organization sync is disabled");
            return 0;
        }

        log.info("Starting SIS -> Flowable organization synchronisation …");

        syncFaculties();
        syncRoles();
        int processed = syncSisUsers();

        log.info(" --- SYNCHRONIZATION FINISHED @ --- {}", DateTimeUtil.getCurrentDateTime());
        return processed;
    }

    // ------------------------------------------------------------------
    // Scheduling & startup
    // ------------------------------------------------------------------

    @Override
    public void run(ApplicationArguments args) {
        if (runOnStartup) {
            try {
                syncOrganization();
            } catch (Exception e) {
                log.error("Startup SIS organization sync failed", e);
            }
        }
    }

    /**
     * Periodic sync controlled by {@code app.sync.sis.fixed-delay-ms}.
     * Set to 0 or a negative value in application.yml to disable the
     * scheduler. Defaults to an hour since org/role/user structure changes
     * far less often than the local user table UserSyncService maintains.
     */
    @Scheduled(fixedDelayString = "${app.sync.sis.fixed-delay-ms:3600000}")
    public void scheduledSync() {
        if (syncEnabled) {
            try {
                syncOrganization();
            } catch (Exception e) {
                log.error("Scheduled SIS organization sync failed", e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Faculties / Departments
    // ------------------------------------------------------------------

    private void syncFaculties() {
        List<FacultyBean> faculties = commonMapper.getFaculties();

        for (FacultyBean faculty : faculties) {
            Group facultyGroup = flowableApi.getGroupByName(faculty.getFacultyCode());
            if (facultyGroup != null) {
                continue;
            }

            String facultyName = StringUtilities.isNotEmpty(faculty.getFacultyName())
                    ? faculty.getFacultyName() : faculty.getFacultyNameS();
            Group createdFacultyGroup = flowableApi.createGroup(faculty.getFacultyCode(), facultyName, facultyName);
            log.info(" --- CREATED FACULTY --- {}", createdFacultyGroup.getId());

            List<DepartmentBean> departments = commonMapper.getDepartments(faculty.getFacultyNo());
            for (DepartmentBean departmentBean : departments) {
                Group deptGroup = flowableApi.getGroupByName(departmentBean.getDeptCode());
                if (deptGroup != null) {
                    continue;
                }
                String deptName = StringUtilities.isNotEmpty(departmentBean.getDeptName())
                        ? departmentBean.getDeptName() : departmentBean.getDeptNameS();
                Group createdDeptGroup = flowableApi.createGroup(departmentBean.getDeptCode(), deptName, deptName);
                log.info(" --- CREATED DEPT --- {}", createdDeptGroup.getId());
            }
        }
    }

    // ------------------------------------------------------------------
    // Roles
    // ------------------------------------------------------------------

    private void syncRoles() {
        List<RoleBean> roles = commonMapper.getRoles();
        for (RoleBean role : roles) {
            Group roleGroup = flowableApi.getRoleByName(role.getJobCode());
            if (roleGroup != null) {
                continue;
            }
            String roleName = StringUtilities.isNotEmpty(role.getJobName()) ? role.getJobName() : role.getJobNameS();
            Group createdRole = flowableApi.createRole(role.getJobCode(), roleName);
            log.info(" --- CREATED ROLE --- {}", createdRole.getId());
        }
    }

    // ------------------------------------------------------------------
    // Users (create or update, + manager assignment)
    // ------------------------------------------------------------------

    /**
     * Builds the same bilingual-lookup {@code Map} shape {@code CommonService}
     * builds for {@code getStaffInfo}/{@code getStudentInfo}: {@code langS}
     * (Arabic), {@code lang} (English), {@code userName}.
     */
    private Map<String, Object> userNameLangParams(String userName) {
        Map<String, Object> params = new HashMap<>();
        params.put("langS", CoreConstants.INT_ARABIC_LOCALE);
        params.put("lang", CoreConstants.INT_ENGLISH_LOCALE);
        params.put("userName", userName);
        return params;
    }

    private int syncSisUsers() {
        List<UserBean> users = commonMapper.getSISUsers();
        String managerId = null;
        UserBean managerUserBean = null;
        PredicateUtil predicateUtil = new PredicateUtil();
        int processed = 0;

        for (UserBean user : users) {
            User existingUser = flowableApi.getUserByUsername(user.getUsername());

            if (existingUser == null) {
                String keyType = user.getKeyType();
                switch (keyType) {
                    case "2": {
                        try {
                            StaffInfoBean staffBean = commonMapper.getStaffInfo(userNameLangParams(user.getUsername()));
                            String title = staffBean.getGender().equals("1") ? "Mr" : "Mrs";
                            User createdUser = flowableApi.createUser(user.getUsername(), user.getPassword(),
                                    staffBean.getInstructorName(), staffBean.getInstructorId(),
                                    title, staffBean.getEmail(), null, null);
                            log.info(" --- CREATED USER --- {}", createdUser.getId());

                            if (user.getDefaultRole().equals("2")) {
                                Group instructorRole = flowableApi.getRoleByName("INS");
                                flowableApi.assignMembershipToUser(createdUser.getId(), null, instructorRole);
                                managerId = commonMapper.getHeadOfDepartment(staffBean.getFacultyNo(), staffBean.getDepartmentNo(), staffBean.getCampusNo());
                                managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                            } else if (user.getDefaultRole().equals("10")) {
                                Group hodRole = flowableApi.getRoleByName("HOD");
                                Group instructorRole = flowableApi.getRoleByName("INS");
                                List<String> userDepts = commonMapper.getUserDepts(user.getUserId());
                                for (String deptCode : userDepts) {
                                    Group deptGroup = flowableApi.getGroupByName(deptCode);
                                    if (deptGroup != null) {
                                        flowableApi.assignMembershipToUser(createdUser.getId(), deptGroup, hodRole);
                                    }
                                }
                                flowableApi.assignMembershipToUser(createdUser.getId(), null, instructorRole);
                                flowableApi.assignMembershipToUser(createdUser.getId(), null, hodRole);
                                managerId = commonMapper.getDeanOfCollege(staffBean.getFacultyNo(), staffBean.getCampusNo());
                                managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                            } else if (user.getDefaultRole().equals("9")) {
                                Group deanRole = flowableApi.getRoleByName("DEN");
                                Group instructorRole = flowableApi.getRoleByName("INS");
                                List<String> userFaculties = commonMapper.getUserFaculties(user.getUserId());
                                for (String facultyCode : userFaculties) {
                                    Group facultyGroup = flowableApi.getGroupByName(facultyCode);
                                    if (facultyGroup != null) {
                                        flowableApi.assignMembershipToUser(createdUser.getId(), facultyGroup, deanRole);
                                    }
                                }
                                flowableApi.assignMembershipToUser(createdUser.getId(), null, instructorRole);
                                flowableApi.assignMembershipToUser(createdUser.getId(), null, deanRole);
                            }

                            if (managerUserBean != null) {
                                User managerEngineUser = flowableApi.getUserByUsername(managerUserBean.getUsername());
                                if (managerEngineUser != null) {
                                    flowableApi.setManager(createdUser.getId(), managerEngineUser.getId());
                                    log.info(" --- USER UPDATED--- {}", createdUser.getId());
                                }
                            }
                        } catch (Exception e) {
                            log.error(" --- ERROR GET STAFF INFO --- {}", user.getUsername(), e);
                        }
                        break;
                    }
                    case "3": {
                        StudentInfoBean studentBean = commonMapper.getStudentInfo(userNameLangParams(user.getUsername()));
                        String title = studentBean.getGender().equals("1") ? "Mr" : "Mrs";
                        User createdUser = flowableApi.createUser(user.getUsername(), user.getPassword(),
                                studentBean.getStudentName(), studentBean.getStudentId(),
                                title, studentBean.getEmail(), studentBean.getMobile(), studentBean.getAddress());
                        Group studentRole = flowableApi.getRoleByName("STD");
                        flowableApi.assignMembershipToUser(createdUser.getId(), null, studentRole);
                        log.info(" --- CREATED USER --- {}", createdUser.getId());

                        managerId = commonMapper.getHeadOfDepartment(studentBean.getFacultyNo(), studentBean.getDeptNo(), studentBean.getCampusNo());
                        managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                        if (managerUserBean != null) {
                            User managerEngineUser2 = flowableApi.getUserByUsername(managerUserBean.getUsername());
                            if (managerEngineUser2 != null) {
                                flowableApi.setManager(createdUser.getId(), managerEngineUser2.getId());
                            }
                        }
                        break;
                    }
                    case "4": {
                        EmployeeInfoBean employeeInfo = commonMapper.getEmployeeInfo(user.getUsername());
                        // NOTE: preserved from the original mapping - username doubles as the
                        // initial password, and firstName/lastName are sourced from
                        // username/userId respectively.
                        User createdUser2 = flowableApi.createUser(user.getUsername(), user.getUsername(),
                                employeeInfo.getUsername(), employeeInfo.getUserId(),
                                null, null, null, null);
                        log.info(" --- CREATED USER --- {}", createdUser2.getId());

                        Group empRole = flowableApi.getRoleByName("EMP");
                        flowableApi.assignMembershipToUser(createdUser2.getId(), null, empRole);
                        break;
                    }
                    default:
                        log.warn(" --- UNKNOWN KEY TYPE --- user={}, keyType={}", user.getUsername(), keyType);
                }
            } else {
                if (user.getKeyType().equals("3")) {
                    StudentInfoBean studentBean2 = commonMapper.getStudentInfo(userNameLangParams(user.getUsername()));
                    flowableApi.updateUser(existingUser, studentBean2.getStudentName(), studentBean2.getStudentId(),
                            null, studentBean2.getEmail(), studentBean2.getMobile(), studentBean2.getAddress());
                    log.info(" --- STUDENT UPDATED --- {}", studentBean2.getStudentId());
                }
                if (user.getKeyType().equals("2")) {
                    try {
                        StaffInfoBean staffBean2 = commonMapper.getStaffInfo(userNameLangParams(user.getUsername()));
                        String title = staffBean2.getGender().equals("1") ? "Mr" : "Mrs";
                        User updatedUser = flowableApi.updateUser(existingUser, staffBean2.getInstructorName(), staffBean2.getInstructorId(),
                                title, staffBean2.getEmail(), null, null);
                        log.info(" --- CREATED USER --- {}", updatedUser.getId());

                        if (user.getDefaultRole().equals("2")) {
                            Group instructorRole = flowableApi.getRoleByName("INS");
                            if (!flowableApi.isMember(updatedUser.getId(), instructorRole.getId())) {
                                flowableApi.assignMembershipToUser(updatedUser.getId(), null, instructorRole);
                                managerId = commonMapper.getHeadOfDepartment(staffBean2.getFacultyNo(), staffBean2.getDepartmentNo(), staffBean2.getCampusNo());
                                managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                            }
                        } else if (user.getDefaultRole().equals("10")) {
                            Group hodRole = flowableApi.getRoleByName("HOD");
                            Group instructorRole = flowableApi.getRoleByName("INS");
                            List<String> userDepts = commonMapper.getUserDepts(user.getUserId());
                            for (String deptCode : userDepts) {
                                Group deptGroup = flowableApi.getGroupByName(deptCode);
                                if (deptGroup == null || flowableApi.isMember(updatedUser.getId(), deptGroup.getId())) {
                                    continue;
                                }
                                flowableApi.assignMembershipToUser(updatedUser.getId(), deptGroup, hodRole);
                                managerId = commonMapper.getHeadOfDepartment(staffBean2.getFacultyNo(), staffBean2.getDepartmentNo(), staffBean2.getCampusNo());
                                managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                            }
                            if (!flowableApi.isMember(updatedUser.getId(), instructorRole.getId())) {
                                flowableApi.assignMembershipToUser(updatedUser.getId(), null, instructorRole);
                            }
                            if (!flowableApi.isMember(updatedUser.getId(), hodRole.getId())) {
                                flowableApi.assignMembershipToUser(updatedUser.getId(), null, hodRole);
                            }
                            managerId = commonMapper.getDeanOfCollege(staffBean2.getFacultyNo(), staffBean2.getCampusNo());
                            managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                        } else if (user.getDefaultRole().equals("9")) {
                            Group deanRole = flowableApi.getRoleByName("DEN");
                            Group instructorRole = flowableApi.getRoleByName("INS");
                            List<String> userFaculties = commonMapper.getUserFaculties(user.getUserId());
                            for (String facultyCode : userFaculties) {
                                Group facultyGroup = flowableApi.getGroupByName(facultyCode);
                                if (facultyGroup == null || flowableApi.isMember(updatedUser.getId(), facultyGroup.getId())) {
                                    continue;
                                }
                                flowableApi.assignMembershipToUser(updatedUser.getId(), facultyGroup, deanRole);
                            }
                            if (!flowableApi.isMember(updatedUser.getId(), instructorRole.getId())) {
                                flowableApi.assignMembershipToUser(updatedUser.getId(), null, instructorRole);
                            }
                            if (!flowableApi.isMember(updatedUser.getId(), deanRole.getId())) {
                                flowableApi.assignMembershipToUser(updatedUser.getId(), null, deanRole);
                            }
                        }

                        if (managerUserBean != null) {
                            User managerEngineUser3 = flowableApi.getUserByUsername(managerUserBean.getUsername());
                            if (managerEngineUser3 != null) {
                                flowableApi.setManager(updatedUser.getId(), managerEngineUser3.getId());
                                log.info(" --- USER UPDATED--- {}", updatedUser.getId());
                            }
                        }
                    } catch (Exception e2) {
                        log.error(" --- ERROR GET STAFF INFO --- {}", user.getUsername(), e2);
                    }
                }
            }
            commonMapper.processSynchUser(user.getUsername());
            processed++;
        }

        return processed;
    }
}
