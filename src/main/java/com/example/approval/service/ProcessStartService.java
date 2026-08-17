package com.example.approval.service;

import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified, process-agnostic service for starting process instances.
 *
 * <p>Any process's JSF backing bean can call {@link #startProcess(String, String, Map)}
 * with its {@code ProcessStartContract}-supplied variables; nothing in this class knows
 * about a specific process definition. The per-process field<->variable mapping lives
 * in the {@code ProcessStartContract} implementations instead.</p>
 */
@Service
@Transactional
public class ProcessStartService {

    private static final Logger log = LoggerFactory.getLogger(ProcessStartService.class);

    private final RuntimeService runtimeService;
    private final IdentityService identityService;

    public ProcessStartService(RuntimeService runtimeService, IdentityService identityService) {
        this.runtimeService = runtimeService;
        this.identityService = identityService;
    }

    /**
     * Start a new process instance by definition key.
     *
     * @param processDefinitionKey Flowable process definition key (from the contract)
     * @param initiatorUsername    authenticated user id recorded as the initiator
     * @param variables            process variables (from the contract's {@code toVariables()})
     */
    public ProcessInstance startProcess(String processDefinitionKey,
                                        String initiatorUsername,
                                        Map<String, Object> variables) {
        identityService.setAuthenticatedUserId(initiatorUsername);
        try {
            Map<String, Object> vars = new HashMap<>(variables);
            vars.putIfAbsent("initiator", initiatorUsername);
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    processDefinitionKey,
                    processDefinitionKey + "-" + System.currentTimeMillis(),
                    vars);
            log.info("Started process instance {} (key {}) for initiator {}",
                    instance.getId(), processDefinitionKey, initiatorUsername);
            return instance;
        } finally {
            identityService.setAuthenticatedUserId(null);
        }
    }
}