/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.origin.beans;

public class RoleBean extends BaseBean {

    private String jobNo;
    private String jobName;
    private String jobNameS;
    private String jobCode;

    public String getJobCode() {
        return jobCode;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public String getJobNo() {
        return jobNo;
    }

    public void setJobNo(String jobNo) {
        this.jobNo = jobNo;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobNameS() {
        return jobNameS;
    }

    public void setJobNameS(String jobNameS) {
        this.jobNameS = jobNameS;
    }

    @Override
    public int getId() {
        return this.hashCode();
    }

}
