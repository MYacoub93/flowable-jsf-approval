package com.example.approval.mycases;

import java.io.Serializable;

/**
 * One row of the <b>Process Variables</b> table of the My Cases details
 * dialog: a Flowable (historic) variable of the selected process instance.
 *
 * <p>Immutable view DTO assembled by {@link MyCasesService} - the value is
 * already rendered to a display-safe string and technical/internal variables
 * are filtered out in the service, so no raw engine object is ever exposed
 * to the JSF layer.</p>
 */
public class CaseVariableRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final String value;
    private final String type;

    public CaseVariableRow(String name, String value, String type) {
        this.name = name;
        this.value = value;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    /** Display-safe value string ("-" when null). */
    public String getValue() {
        return value == null ? "-" : value;
    }

    public String getType() {
        return type;
    }
}