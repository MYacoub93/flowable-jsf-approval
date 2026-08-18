package com.example.approval.clearance.service;

import java.util.List;

/**
 * Dynamically determines which departments must approve a clearance request
 * for a given initiator.
 *
 * <p>Called from the BPMN via a Spring bean expression
 * ({@code ${clearanceProcessHandler.resolveRequiredDepartments(execution)}})
 * right before the parallel multi-instance approval stage is created, so the
 * department list is <b>never</b> hardcoded in the BPMN. Implementations may
 * query HR/SIS data, tuition status, library loans etc. - the BPMN does not
 * care where the list comes from.</p>
 */
public interface DepartmentResolverService {

    /**
     * Resolve the departments that must approve the clearance request started
     * by {@code initiatorUsername}.
     *
     * @param initiatorUsername the user who started (or amended) the process
     * @return ordered, distinct, non-empty list of department group ids; never
     *         {@code null} (throw {@link IllegalStateException} instead if no
     *         department can be determined)
     */
    List<String> getRequiredDepartments(String initiatorUsername);
}