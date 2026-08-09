package com.example.approval.syncronizer;

/*
 * Flowable edition of the SIS -> Organization sync job.
 * Ported from a Bonita BPM implementation - see FlowableOrgSyncAPI for the
 * modeling notes on how Bonita's Group/Role/UserMembership concepts map onto
 * Flowable's flatter IdentityService model.
 */



import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.approval.common.utils.DateTimeUtil;
import com.example.approval.common.utils.PredicateUtil;
import com.example.approval.common.utils.StringUtilities;
import com.example.approval.flowable.OrganizationManager;
import com.example.approval.origin.beans.*;
import com.example.approval.service.CommonService;
import org.flowable.engine.IdentityService;
import org.flowable.idm.api.Group;

import org.flowable.idm.api.User;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SISOC {

    private static Logger LOG = Logger.getLogger(SISOC.class.getName());

    public static void main(String[] args) {



        // Flowable's IdentityService: if you're on flowable-spring-boot-starter this bean is
        // registered for you automatically. If you're bootstrapping a bare ProcessEngine from
        // flowable.cfg.xml instead, swap this line for:
        //   ProcessEngines.getDefaultProcessEngine().getIdentityService()

        ApplicationContext applicationContext = prepareConfiguration();
        CommonService commonServiceImpl = (CommonService) applicationContext.getBean((Class) CommonService.class);
        List<FacultyBean> faculties = (List<FacultyBean>) commonServiceImpl.getFaculties();

        IdentityService identityService ;
        OrganizationManager flowableApi = new OrganizationManager();


        for (FacultyBean faculty : faculties) {
            Group facultyGroup = flowableApi.getGroupByName(faculty.getFacultyCode());
            if (facultyGroup == null) {
                String facultyName = StringUtilities.isNotEmpty(faculty.getFacultyName()) ? faculty.getFacultyName() : faculty.getFacultyNameS();
                Group createdFacultyGroup = flowableApi.createGroup(faculty.getFacultyCode(), facultyName, facultyName);
                SISOC.LOG.log(Level.INFO, " --- CREATED FACULTY --- {0}", createdFacultyGroup.getId());

                List<DepartmentBean> departments = (List<DepartmentBean>) commonServiceImpl.getDepartments(faculty.getFacultyNo());
                for (DepartmentBean departmentBean : departments) {
                    Group deptGroup = flowableApi.getGroupByName(departmentBean.getDeptCode());
                    if (deptGroup == null) {
                        String deptName = StringUtilities.isNotEmpty(departmentBean.getDeptName()) ? departmentBean.getDeptName() : departmentBean.getDeptNameS();
                        Group createdDeptGroup = flowableApi.createGroup(departmentBean.getDeptCode(), deptName, deptName);
                        SISOC.LOG.log(Level.INFO, " --- CREATED DEPT --- {0}", createdDeptGroup.getId());
                    }
                }
            }
        }

        List<RoleBean> roles = (List<RoleBean>) commonServiceImpl.getRoles();
        for (RoleBean role : roles) {
            Group roleGroup = flowableApi.getRoleByName(role.getJobCode());
            if (roleGroup == null) {
                String roleName = StringUtilities.isNotEmpty(role.getJobName()) ? role.getJobName() : role.getJobNameS();
                Group createdRole = flowableApi.createRole(role.getJobCode(), roleName);
                SISOC.LOG.log(Level.INFO, " --- CREATED ROLE --- {0}", createdRole.getId());
            }
        }

        List<UserBean> users = (List<UserBean>) commonServiceImpl.getSISUsers();
        String managerId = null;
        UserBean managerUserBean = null;
        PredicateUtil predicateUtil = new PredicateUtil();

        for (UserBean user : users) {
            User existingUser = flowableApi.getUserByUsername(user.getUsername());

            if (existingUser == null) {
                String keyType = user.getKeyType();
                switch (keyType) {
                    case "2": {
                        try {
                            StaffInfoBean staffBean = commonServiceImpl.getStaffInfo(user.getUsername());
                            String title = staffBean.getGender().equals("1") ? "Mr" : "Mrs";
                            User createdUser = flowableApi.createUser(user.getUsername(), user.getPassword(),
                                    staffBean.getInstructorName(), staffBean.getInstructorId(),
                                    title, staffBean.getEmail(), null, null);
                            //flowableApi.assignApplicationAccess(createdUser.getId());
                            SISOC.LOG.log(Level.INFO, " --- CREATED USER --- {0}", createdUser.getId());

                            if (user.getDefaultRole().equals("2")) {
                                Group instructorRole = flowableApi.getRoleByName("INS");
                                flowableApi.assignMembershipToUser(createdUser.getId(), null, instructorRole);
                                managerId = commonServiceImpl.getHeadOfDepartment(staffBean.getFacultyNo(), staffBean.getDepartmentNo(), staffBean.getCampusNo());
                                managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                            } else if (user.getDefaultRole().equals("10")) {
                                Group hodRole = flowableApi.getRoleByName("HOD");
                                Group instructorRole = flowableApi.getRoleByName("INS");
                                List<String> userDepts = (List<String>) commonServiceImpl.getUserDepts(user.getUserId());
                                for (String deptCode : userDepts) {
                                    Group deptGroup = flowableApi.getGroupByName(deptCode);
                                    if (deptGroup != null) {
                                        flowableApi.assignMembershipToUser(createdUser.getId(), deptGroup, hodRole);
                                    }
                                }
                                flowableApi.assignMembershipToUser(createdUser.getId(), null, instructorRole);
                                flowableApi.assignMembershipToUser(createdUser.getId(), null, hodRole);
                                managerId = commonServiceImpl.getDeanOfCollege(staffBean.getFacultyNo(), staffBean.getCampusNo());
                                managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                            } else if (user.getDefaultRole().equals("9")) {
                                Group deanRole = flowableApi.getRoleByName("DEN");
                                Group instructorRole = flowableApi.getRoleByName("INS");
                                List<String> userFaculties = (List<String>) commonServiceImpl.getUserFaculties(user.getUserId());
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
                                    SISOC.LOG.log(Level.INFO, " --- USER UPDATED--- {0}", createdUser.getId());
                                }
                            }
                        } catch (Exception e) {
                            SISOC.LOG.log(Level.SEVERE, " --- ERROR GET STAFF INFO --- {0}", user.getUsername());
                        }
                        break;
                    }
                    case "3": {
                        StudentInfoBean studentBean = commonServiceImpl.getStudentInfo(user.getUsername());
                        String title = studentBean.getGender().equals("1") ? "Mr" : "Mrs";
                        User createdUser = flowableApi.createUser(user.getUsername(), user.getPassword(),
                                studentBean.getStudentName(), studentBean.getStudentId(),
                                title, studentBean.getEmail(), studentBean.getMobile(), studentBean.getAddress());
                        Group studentRole = flowableApi.getRoleByName("STD");
                        //flowableApi.assignApplicationAccess(createdUser.getId());
                        flowableApi.assignMembershipToUser(createdUser.getId(), null, studentRole);
                        SISOC.LOG.log(Level.INFO, " --- CREATED USER --- {0}", createdUser.getId());

                        managerId = commonServiceImpl.getHeadOfDepartment(studentBean.getFacultyNo(), studentBean.getDeptNo(), studentBean.getCampusNo());
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
                        EmployeeInfoBean employeeInfo = commonServiceImpl.getEmployeeInfo(user.getUsername());
                        // NOTE: preserved from the original mapping - username doubles as the
                        // initial password, and firstName/lastName are sourced from
                        // username/userId respectively.
                        User createdUser2 = flowableApi.createUser(user.getUsername(), user.getUsername(),
                                employeeInfo.getUsername(), employeeInfo.getUserId(),
                                null, null, null, null);
                        //flowableApi.assignApplicationAccess(createdUser2.getId());
                        SISOC.LOG.log(Level.INFO, " --- CREATED USER --- {0}", createdUser2.getId());

                        Group empRole = flowableApi.getRoleByName("EMP");
                        flowableApi.assignMembershipToUser(createdUser2.getId(), null, empRole);
                        break;
                    }
                }
            } else {
                if (user.getKeyType().equals("3")) {
                    StudentInfoBean studentBean2 = commonServiceImpl.getStudentInfo(user.getUsername());
                    flowableApi.updateUser(existingUser, studentBean2.getStudentName(), studentBean2.getStudentId(),
                            null, studentBean2.getEmail(), studentBean2.getMobile(), studentBean2.getAddress());
                    SISOC.LOG.log(Level.INFO, " --- STUDENT UPDATED --- {0}", studentBean2.getStudentId());
                }
                if (user.getKeyType().equals("2")) {
                    try {
                        StaffInfoBean staffBean2 = commonServiceImpl.getStaffInfo(user.getUsername());
                        String title = staffBean2.getGender().equals("1") ? "Mr" : "Mrs";
                        User updatedUser = flowableApi.updateUser(existingUser, staffBean2.getInstructorName(), staffBean2.getInstructorId(),
                                title, staffBean2.getEmail(), null, null);
                        SISOC.LOG.log(Level.INFO, " --- CREATED USER --- {0}", updatedUser.getId());

                        if (user.getDefaultRole().equals("2")) {
                            Group instructorRole = flowableApi.getRoleByName("INS");
                            if (!flowableApi.isMember(updatedUser.getId(), instructorRole.getId())) {
                                flowableApi.assignMembershipToUser(updatedUser.getId(), null, instructorRole);
                                managerId = commonServiceImpl.getHeadOfDepartment(staffBean2.getFacultyNo(), staffBean2.getDepartmentNo(), staffBean2.getCampusNo());
                                managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                            }
                        } else if (user.getDefaultRole().equals("10")) {
                            Group hodRole = flowableApi.getRoleByName("HOD");
                            Group instructorRole = flowableApi.getRoleByName("INS");
                            List<String> userDepts = (List<String>) commonServiceImpl.getUserDepts(user.getUserId());
                            for (String deptCode : userDepts) {
                                Group deptGroup = flowableApi.getGroupByName(deptCode);
                                if (deptGroup == null || flowableApi.isMember(updatedUser.getId(), deptGroup.getId())) {
                                    continue;
                                }
                                flowableApi.assignMembershipToUser(updatedUser.getId(), deptGroup, hodRole);
                                managerId = commonServiceImpl.getHeadOfDepartment(staffBean2.getFacultyNo(), staffBean2.getDepartmentNo(), staffBean2.getCampusNo());
                                managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                            }
                            if (!flowableApi.isMember(updatedUser.getId(), instructorRole.getId())) {
                                flowableApi.assignMembershipToUser(updatedUser.getId(), null, instructorRole);
                            }
                            if (!flowableApi.isMember(updatedUser.getId(), hodRole.getId())) {
                                flowableApi.assignMembershipToUser(updatedUser.getId(), null, hodRole);
                            }
                            managerId = commonServiceImpl.getDeanOfCollege(staffBean2.getFacultyNo(), staffBean2.getCampusNo());
                            managerUserBean = (UserBean) predicateUtil.selectObjectFromCollection((List) users, "userId", managerId);
                        } else if (user.getDefaultRole().equals("9")) {
                            Group deanRole = flowableApi.getRoleByName("DEN");
                            Group instructorRole = flowableApi.getRoleByName("INS");
                            List<String> userFaculties = (List<String>) commonServiceImpl.getUserFaculties(user.getUserId());
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
                                SISOC.LOG.log(Level.INFO, " --- USER UPDATED--- {0}", updatedUser.getId());
                            }
                        }
                    } catch (Exception e2) {
                        SISOC.LOG.log(Level.SEVERE, " --- ERROR GET STAFF INFO --- {0}", user.getUsername());
                    }
                }
            }
            commonServiceImpl.processSynchUser(user.getUsername());
        }
        SISOC.LOG.log(Level.INFO, " --- SYNCHRONIZATION FINISHED @ --- {0}", DateTimeUtil.getCurrentDateTime());
        System.exit(0);
    }

    private static ApplicationContext prepareConfiguration() {
        return (ApplicationContext) new ClassPathXmlApplicationContext("/engine-context.xml");
    }

}
