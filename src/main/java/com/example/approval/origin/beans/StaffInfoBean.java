package com.example.approval.origin.beans;


public class StaffInfoBean extends BaseBean {

    private static final long serialVersionUID = 1L;

    private String instructorId;
    private String instructorName;
    private String instructorNameS;
    private String degreeCode;
    private String facultyNo;
    private String facultyName;
    private String facultyNameS;
    private String gender;
    private String departmentNo;
    private String departmentName;
    private String departmentNameS;
    private String status;
    private String email;
    private String deanOfFaculty;
    private String headOfDepartment;
    private String photo;
    private String resumeId;
    private String username;
    private String campusNo;

    public String getCampusNo() {
        return campusNo;
    }

    public void setCampusNo(String campusNo) {
        this.campusNo = campusNo;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getResumeId() {
        return resumeId;
    }

    public void setResumeId(String resumeId) {
        this.resumeId = resumeId;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(String instructorId) {
        this.instructorId = instructorId;
    }

    public String getInstructorName() {
        return instructorName;
    }

    public void setInstructorName(String instructorName) {
        this.instructorName = instructorName;
    }

    public String getDegreeCode() {
        return degreeCode;
    }

    public void setDegreeCode(String degreeCode) {
        this.degreeCode = degreeCode;
    }

    public String getFacultyNo() {
        return facultyNo;
    }

    public void setFacultyNo(String facultyNo) {
        this.facultyNo = facultyNo;
    }

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDepartmentNo() {
        return departmentNo;
    }

    public void setDepartmentNo(String departmentNo) {
        this.departmentNo = departmentNo;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDeanOfFaculty() {
        return deanOfFaculty;
    }

    public void setDeanOfFaculty(String deanOfFaculty) {
        this.deanOfFaculty = deanOfFaculty;
    }

    public String getHeadOfDepartment() {
        return headOfDepartment;
    }

    public void setHeadOfDepartment(String headOfDepartment) {
        this.headOfDepartment = headOfDepartment;
    }

    public String getInstructorNameS() {
        return instructorNameS;
    }

    public void setInstructorNameS(String instructorNameS) {
        this.instructorNameS = instructorNameS;
    }

    public String getFacultyNameS() {
        return facultyNameS;
    }

    public void setFacultyNameS(String facultyNameS) {
        this.facultyNameS = facultyNameS;
    }

    public String getDepartmentNameS() {
        return departmentNameS;
    }

    public void setDepartmentNameS(String departmentNameS) {
        this.departmentNameS = departmentNameS;
    }

    @Override
    public int getId() {
        return this.hashCode();
    }

}
