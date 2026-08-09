/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.example.approval.common.CoreConstants;
import com.example.approval.mapper.CommonMapper;
import com.example.approval.origin.beans.*;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author ayaseen
 */
@Service
@Transactional(readOnly = true)
public class CommonService{

    @Autowired
    private CommonMapper commonMapper;

    public String pingDB() {
        return commonMapper.pingDB();
    }

    public List<GroupBean> getHrsGroups() {
        return commonMapper.getHrsGroups();
    }

    public List<RoleBean> getHrsRoles() {
        return commonMapper.getHrsRoles();
    }

    public List<RoleBean> getRoles() {
        return commonMapper.getRoles();
    }

    public List<UserBean> getSISUsers() {
        return commonMapper.getSISUsers();
    }

    public List<UserInfoBean> getUserInfo() {
        return commonMapper.getHrsUsers();
    }



    public EmployeeInfoBean getEmployeeInfo(String username) {
        return commonMapper.getEmployeeInfo(username);
    }

    public StaffInfoBean getStaffInfo(String userName) {
        Map<String, Object> map = new HashMap<>();
        map.put("langS", CoreConstants.INT_ARABIC_LOCALE);
        map.put("lang", CoreConstants.INT_ENGLISH_LOCALE);
        map.put("userName", userName);
        return commonMapper.getStaffInfo(map);
    }

    public StaffInfoBean getStaffInfoById(String userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("langS", CoreConstants.INT_ARABIC_LOCALE);
        map.put("lang", CoreConstants.INT_ENGLISH_LOCALE);
        map.put("userId", userId);
        return commonMapper.getStaffInfoById(map);
    }

    public StudentInfoBean getStudentInfo(String userName) {
        Map<String, Object> map = new HashMap<>();
        map.put("langS", CoreConstants.INT_ARABIC_LOCALE);
        map.put("lang", CoreConstants.INT_ENGLISH_LOCALE);
        map.put("userName", userName);
        return commonMapper.getStudentInfo(map);
    }

    public StudentInfoBean getStudentInfoById(String studentId) {
        Map<String, Object> map = new HashMap<>();
        map.put("langS", CoreConstants.INT_ARABIC_LOCALE);
        map.put("lang", CoreConstants.INT_ENGLISH_LOCALE);
        map.put("studentId", studentId);
        return commonMapper.getStudentInfoById(map);
    }



    public List<DepartmentBean> getDepartments(String facultyNo) {
        return commonMapper.getDepartments(facultyNo);
    }

    public List<FacultyBean> getFaculties() {
        return commonMapper.getFaculties();
    }

    public String getDeanOfCollege(String facultyNo, String campusNo) {
        return commonMapper.getDeanOfCollege(facultyNo, campusNo);
    }

    public String getHeadOfDepartment(String facultyNo, String deptNo, String campusNo) {
        return commonMapper.getHeadOfDepartment(facultyNo, deptNo, campusNo);
    }

    public String getDepartmentCode(String facultyNo, String deptNo) {
        return commonMapper.getDepartmentCode(facultyNo, deptNo);
    }

    public String getCollegeCode(String facultyNo) {
        return commonMapper.getCollegeCode(facultyNo);
    }

    public List<String> getUserDepts(String userId) {
        return commonMapper.getUserDepts(userId);
    }

    public List<String> getUserFaculties(String userId) {
        return commonMapper.getUserFaculties(userId);
    }

    public UserInfoBean getHrsUserInfo(String username) {
        return commonMapper.getHrsUserInfo(username);
    }

    public List<UserInfoBean> getAuthorizedHrStaffs() {
        return commonMapper.getAuthorizedHrStaffs();
    }



    public GroupBean getHrsSectionInfo(String sectionNo) {
        return commonMapper.getHrsSectionInfo(sectionNo);
    }



    public String isStaffExists(String staffId) {
        return commonMapper.isStaffExists(staffId);
    }

    public String isStaffWorking(String staffId) {
        return commonMapper.isStaffWorking(staffId);
    }



    public String getCurrentTime() {
        return commonMapper.getCurrentTime();
    }


    public UserBean checkUserAvailability(String username) {

        return commonMapper.checkUserAvailability(username);
    }
    public void processSynchUser(String username) {
         commonMapper.processSynchUser(username);

    }


//    public void processSynchUser(String username) {
//
//        try ( SqlSession sqlSession = super.getSQLSession(CommonMapper.class, DataSourceSqlSessionFactory.DataSourceEnvironment.GENERIC)) {
//            CommonMapper mapper = sqlSession.getMapper(CommonMapper.class);
//            mapper.processSynchUser(username);
//            sqlSession.commit();
//        }
//    }



    
}
