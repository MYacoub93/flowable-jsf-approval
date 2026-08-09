/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.origin.beans;



public class GroupBean extends BaseBean {

    private String sectionNo;
    private String sectionName;
    private String sectionNameS;
    private String parentSection;
    private String managerId;

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getSectionNo() {
        return sectionNo;
    }

    public void setSectionNo(String sectionNo) {
        this.sectionNo = sectionNo;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public String getSectionNameS() {
        return sectionNameS;
    }

    public void setSectionNameS(String sectionNameS) {
        this.sectionNameS = sectionNameS;
    }

    public String getParentSection() {
        return parentSection;
    }

    public void setParentSection(String parentSection) {
        this.parentSection = parentSection;
    }

    @Override
    public int getId() {
        return this.hashCode();
    }

}
