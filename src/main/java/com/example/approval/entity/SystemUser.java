package com.example.approval.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class SystemUser   implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private int defaultRole;
    private int keyType;
    private String username;
    private String password;
    private int webEnabled;
    private int userID;
}
