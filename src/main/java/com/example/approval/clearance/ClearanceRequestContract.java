package com.example.approval.clearance;

import com.example.approval.flowable.contracts.ProcessStartContract;

import java.util.HashMap;
import java.util.Map;

import static com.example.approval.clearance.ClearanceConstants.*;

/**
 * Field <-> process-variable mapping for the Clearance Letter start form
 * (and the amendment form, which edits the same fields).
 *
 * <p>The dynamic part of the process (which departments approve) is NOT part
 * of the contract - it is resolved by
 * {@code DepartmentResolverService.getRequiredDepartments(initiator)}.</p>
 */
public class ClearanceRequestContract implements ProcessStartContract {

    private static final long serialVersionUID = 1L;

    private String studentFullName;
    private String studentId;
    private String program;
    private String notes;
    private String contactEmail;

    @Override
    public String getProcessDefinitionKey() {
        return PROCESS_KEY;
    }

    @Override
    public Map<String, Object> toVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_STUDENT_FULL_NAME, nullSafe(studentFullName));
        vars.put(VAR_STUDENT_ID, nullSafe(studentId));
        vars.put(VAR_PROGRAM, nullSafe(program));
        vars.put(VAR_NOTES, nullSafe(notes));
        vars.put(VAR_CONTACT_EMAIL, nullSafe(contactEmail));
        return vars;
    }

    @Override
    public void fromVariables(Map<String, Object> variables) {
        this.studentFullName = str(variables.get(VAR_STUDENT_FULL_NAME));
        this.studentId = str(variables.get(VAR_STUDENT_ID));
        this.program = str(variables.get(VAR_PROGRAM));
        this.notes = str(variables.get(VAR_NOTES));
        this.contactEmail = str(variables.get(VAR_CONTACT_EMAIL));
    }

    private static String nullSafe(String value) {
        return value != null ? value.trim() : null;
    }

    private static String str(Object value) {
        return value != null ? value.toString() : null;
    }

    // Getters / Setters -------------------------------------------------

    public String getStudentFullName() {
        return studentFullName;
    }

    public void setStudentFullName(String studentFullName) {
        this.studentFullName = studentFullName;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}