package com.example.approval.delegation;

import java.io.Serializable;

/**
 * One entry of the process dropdown of the Administration &rarr; Task
 * Delegation page. The user-friendly {@link #getLabel() label} (process name
 * plus technical key) is shown to the admin, while the {@link #getKey() key}
 * is kept internally as the value of the dropdown selection.
 *
 * <p>Immutable view DTO assembled by {@link TaskDelegationService} - no
 * engine entity is ever exposed to the JSF layer directly (same convention
 * as {@code ProcessDefinitionRow} of the Process Management admin page).</p>
 */
public class ProcessDefinitionOption implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String key;
    private final String name;

    public ProcessDefinitionOption(String id, String key, String name) {
        this.id = id;
        this.key = key;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    /** User-friendly dropdown label: process name plus the technical key. */
    public String getLabel() {
        return name == null || name.isBlank() ? key : name + " (" + key + ")";
    }
}