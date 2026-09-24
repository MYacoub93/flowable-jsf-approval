package com.example.approval.origin.beans;

import java.io.Serializable;

/**
 * One row of the {@code sis_semesters} selection
 * ({@code CommonMapper.getTransactionSemester}): a semester that may be
 * selected for a semester-withdrawal request, with its bilingual
 * descriptions.
 *
 * <p>{@code semester} is the stored value (numeric SIS semester key);
 * {@code semesterDesc} is the English and {@code semesterDescS} the Arabic
 * description ({@code _S} suffix follows the existing SIS bilingual naming
 * convention).</p>
 */
public class SemesterBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private String semester;
    private String semesterDesc;
    private String semesterDescS;

    public SemesterBean() {
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getSemesterDesc() {
        return semesterDesc;
    }

    public void setSemesterDesc(String semesterDesc) {
        this.semesterDesc = semesterDesc;
    }

    public String getSemesterDescS() {
        return semesterDescS;
    }

    public void setSemesterDescS(String semesterDescS) {
        this.semesterDescS = semesterDescS;
    }

    @Override
    public String toString() {
        return "SemesterBean{semester=" + semester
                + ", semesterDesc=" + semesterDesc + "}";
    }
}