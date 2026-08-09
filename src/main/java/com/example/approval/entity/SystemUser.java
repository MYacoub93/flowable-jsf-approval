package com.example.approval.entity;

import lombok.Data;

@Data
public class SystemUser {
    private Long id;
    private int defaultRole;
    private int keyType;
    private String username;
    private String password;
    private int webEnabled;
    private int userID;
}
