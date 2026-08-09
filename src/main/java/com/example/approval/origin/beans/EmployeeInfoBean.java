/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.origin.beans;




public class EmployeeInfoBean extends BaseBean {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String usernameS;
    private String webAdminType;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getWebAdminType() {
        return webAdminType;
    }

    public void setWebAdminType(String webAdminType) {
        this.webAdminType = webAdminType;
    }

    @Override
    public int getId() {
        return this.hashCode();
    }

}

