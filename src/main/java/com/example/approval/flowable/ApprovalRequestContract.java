package com.example.approval.flowable;

import com.example.approval.flowable.contracts.ProcessStartContract;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data contract for the {@code approvalProcess} start / update-request forms.
 *
 * <p>Owns the mapping between the JSF-bound fields (title, description, amount,
 * department) and the Flowable process variables, so both {@code StartProcessBean}
 * (new instance) and {@code UpdateRequestBean} (edit after rejection) share the
 * exact same field<->variable mapping. JSF EL binds directly to the properties,
 * e.g. {@code #{startProcessBean.contract.title}}.</p>
 */
public class ApprovalRequestContract implements ProcessStartContract {

    private static final long serialVersionUID = 1L;

    public static final String PROCESS_KEY = "approvalProcess";

    private String title;
    private String description;
    private Double amount;
    private String department;

    @Override
    public String getProcessDefinitionKey() {
        return PROCESS_KEY;
    }

    @Override
    public Map<String, Object> toVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("title", title);
        variables.put("description", description != null ? description : "");
        variables.put("amount", amount);
        variables.put("department", department);
        // Pre-seed this process control variables (same seeds the former
        // ApprovalService.startProcess set), so gateway expressions and the
        // rejection round-trip behave identically from the very first step.
        variables.put("approved", null);
        variables.put("comments", "");
        variables.put("rejectedBy", null);
        variables.put("manager", null);
        variables.put("financeUser", null);
        return variables;
    }

    @Override
    public void fromVariables(Map<String, Object> variables) {
        Objects.requireNonNull(variables, "variables");
        this.title = (String) variables.get("title");
        this.description = (String) variables.get("description");
        Object amt = variables.get("amount");
        if (amt instanceof Number) {
            this.amount = ((Number) amt).doubleValue();
        } else {
            this.amount = null;
        }
        this.department = (String) variables.get("department");
    }

    // Getters / Setters (bound from JSF EL)

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}