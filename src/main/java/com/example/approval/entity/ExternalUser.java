package com.example.approval.entity;

/**
 * A user row of the external view {@code FLOWABLE_USERS_VW} (Oracle, external
 * datasource). 31k+ rows - always queried with server-side pagination, never
 * loaded in full.
 *
 * <p>Column mapping (verified against the live view):</p>
 * <pre>
 * ID_        VARCHAR2(40)   numeric DIC/WEB user id as string (PK of the view)
 * USERNAME_  VARCHAR2(100)  LOWER(WEB_NAME) - the login name / Flowable user id
 * FIRST_NAME_ VARCHAR2(100) display name (Arabic) resolved per KEY_TYPE_
 * LAST_NAME_  VARCHAR2(51)  mostly NULL
 * EMAIL_     VARCHAR2(400)
 * KEY_TYPE_  NUMBER(1)      actor kind: 1=web,2=instructor,3=student,4=employee
 * ROLE_CODE_ VARCHAR2(200)  primary/default role code (NULL = none)
 * </pre>
 *
 * <p>{@code PASSWORD_} is intentionally NOT mapped - this feature never needs
 * credentials.</p>
 */
public class ExternalUser {

    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private Integer keyType;
    private String roleCode;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getKeyType() {
        return keyType;
    }

    public void setKeyType(Integer keyType) {
        this.keyType = keyType;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}