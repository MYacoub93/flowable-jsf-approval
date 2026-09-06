package com.example.approval.studentproof.model;

import java.io.Serializable;

/**
 * Read-only student information snapshot of the <b>Student Proof Certificate
 * Letter</b> process ({@code studentProofCertificateProcess}).
 *
 * <p>Loaded by {@code StudentProofService.findStudentInfo} (reusing the
 * existing {@code CommonService.getStudentInfo} Oracle SIS query) for the
 * logged-in student, displayed read-only on
 * the start form and stored as process variables
 * ({@code StudentProofConstants.VAR_STUDENT_*}).</p>
 */
public class StudentProofStudentInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String studentId;
    private String studentName;
    private String email;
    private String gpa;
    private String mobile;
    private String currentSemester;

    public StudentProofStudentInfo() {
    }

    public StudentProofStudentInfo(String studentId,
                                   String studentName,
                                   String email,
                                   String gpa,
                                   String mobile,
                                   String currentSemester) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.email = email;
        this.gpa = gpa;
        this.mobile = mobile;
        this.currentSemester = currentSemester;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGpa() {
        return gpa;
    }

    public void setGpa(String gpa) {
        this.gpa = gpa;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getCurrentSemester() {
        return currentSemester;
    }

    public void setCurrentSemester(String currentSemester) {
        this.currentSemester = currentSemester;
    }
}