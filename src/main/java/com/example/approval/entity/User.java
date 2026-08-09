package com.example.approval.entity;

import java.io.Serializable;

/**
 * User entity mapped to the users table via MyBatis.
 * Used for dynamic task assignment in the Flowable process.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String fullName;
    private String department;
    private String role;       // INITIATOR, MANAGER, FINANCE
    private String email;
    private boolean active;

    public User() {
    }

    public User(Long id, String username, String fullName, String department, String role, String email, boolean active) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.department = department;
        this.role = role;
        this.email = email;
        this.active = active;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", department='" + department + '\'' +
                ", role='" + role + '\'' +
                ", active=" + active +
                '}';
    }
}
