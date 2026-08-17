package com.example.approval.flowable.contracts;

import java.io.Serializable;
import java.util.Map;

/**
 * Per-process data contract between a JSF start form and Flowable.
 *
 * <p>Each process definition that has a dedicated hand-built start form provides one
 * implementation of this interface. The implementation owns the mapping between the
 * JSF-bound form fields and the process variables map, so that field<->variable
 * mapping logic lives in exactly one place. It is used both when starting a process
 * ({@link #toVariables()}) and when re-loading / editing an existing instance
 * ({@link #fromVariables(Map)}).</p>
 *
 * <p>Adding a new process requires a new BPMN with its own {@code flowable:formKey},
 * a new .xhtml form, a new implementation of this interface and a lightweight JSF
 * bean calling {@code ProcessStartService} - no changes to shared routing or start
 * service code.</p>
 */
public interface ProcessStartContract extends Serializable {

    /** The Flowable process definition key this contract belongs to. */
    String getProcessDefinitionKey();

    /** Build the process variables map for starting a new process instance. */
    Map<String, Object> toVariables();

    /** Populate the form fields from an existing process instance's variables. */
    void fromVariables(Map<String, Object> variables);
}