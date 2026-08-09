/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.origin.beans;




public class FacultyBean extends BaseBean {

    private String facultyNo;
    private String facultyCode;
    private String facultyName;
    private String facultyNameS;
    private String managerId;

    public String getFacultyNo() {
        return facultyNo;
    }

    public void setFacultyNo(String facultyNo) {
        this.facultyNo = facultyNo;
    }

    public String getFacultyCode() {
        return facultyCode;
    }

    public void setFacultyCode(String facultyCode) {
        this.facultyCode = facultyCode;
    }

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    public String getFacultyNameS() {
        return facultyNameS;
    }

    public void setFacultyNameS(String facultyNameS) {
        this.facultyNameS = facultyNameS;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    @Override
    public int getId() {
        return this.hashCode();
    }

}
