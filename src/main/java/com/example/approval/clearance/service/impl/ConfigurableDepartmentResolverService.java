package com.example.approval.clearance.service.impl;

import com.example.approval.clearance.ClearanceConstants;
import com.example.approval.clearance.ClearanceProperties;
import com.example.approval.clearance.service.DepartmentResolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration-driven implementation of {@link DepartmentResolverService}.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>{@code clearance.departments.initiator-overrides[username]} - exact
 *       username match, comma-separated departments ({@code *} acts as a
 *       catch-all);</li>
 *   <li>{@code clearance.departments.initiator-overrides[*]};</li>
 *   <li>{@code clearance.departments.mode}: {@code ALL} returns the full
 *       default catalogue, {@code CONFIGURED} returns
 *       {@code clearance.departments.default-departments}.</li>
 * </ol>
 *
 * <p>Swap in a real SIS/HR-backed implementation (tuition balance, library
 * loans, dorm occupancy...) by providing another bean of this type - the BPMN
 * only ever sees the {@link DepartmentResolverService} contract.</p>
 */
@Service
public class ConfigurableDepartmentResolverService implements DepartmentResolverService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableDepartmentResolverService.class);

    private final ClearanceProperties properties;

    public ConfigurableDepartmentResolverService(ClearanceProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<String> getRequiredDepartments(String initiatorUsername) {
        if (initiatorUsername == null || initiatorUsername.isBlank()) {
            throw new IllegalStateException("Initiator username is required to resolve departments");
        }

        ClearanceProperties.Departments config = properties.getDepartments();

        // 1) exact initiator override
        String override = config.getInitiatorOverrides().get(initiatorUsername);
        // 2) catch-all override
        if (override == null) {
            override = config.getInitiatorOverrides().get("*");
        }
        if (override != null && !override.isBlank()) {
            List<String> departments = split(override);
            log.info("Departments for initiator '{}' resolved from override: {}", initiatorUsername, departments);
            return requireNonEmpty(departments, initiatorUsername);
        }

        // 3) mode based
        List<String> departments = switch (config.getMode()) {
            case CONFIGURED -> new ArrayList<>(config.getDefaultDepartments());
            case ALL -> new ArrayList<>(ClearanceConstants.ALL_DEPARTMENTS);
        };
        log.info("Departments for initiator '{}' resolved via mode {}: {}", initiatorUsername, config.getMode(), departments);
        return requireNonEmpty(departments, initiatorUsername);
    }

    private List<String> requireNonEmpty(List<String> departments, String initiatorUsername) {
        if (departments.isEmpty()) {
            throw new IllegalStateException(
                    "No departments resolved for initiator '" + initiatorUsername + "' - check clearance.departments.* configuration");
        }
        return departments;
    }

    private List<String> split(String commaSeparated) {
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(result::add);
        return new ArrayList<>(result);
    }
}