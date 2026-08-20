package com.example.approval.service;

import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.approval.clearance.service.AuditService;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified, process-agnostic service for starting process instances.
 *
 * <p>Any process's JSF backing bean can call {@link #startProcess(String, String, Map)}
 * with its {@code ProcessStartContract}-supplied variables; nothing in this class knows
 * about a specific process definition. The per-process field<->variable mapping lives
 * in the {@code ProcessStartContract} implementations instead.</p>
 *
 * <p><b>BPM audit linkage:</b> the business {@code CASE_ID} of the
 * {@code F_BPM_*} audit tables is simply the Flowable process instance id
 * of the started case - it is never allocated or reserved. Listeners and
 * delegates running synchronously inside the start command read the id
 * straight off their {@code DelegateExecution}/{@code DelegateTask}, and
 * once the instance is running {@code openCase} inserts the
 * {@code F_BPM_AUDIT_LOG} master row using the same id.</p>
 */
@Service
@Transactional
public class ProcessStartService {

    private static final Logger log = LoggerFactory.getLogger(ProcessStartService.class);

    private final RuntimeService runtimeService;
    private final IdentityService identityService;
    private final AuditService auditService;

    public ProcessStartService(RuntimeService runtimeService,
                               IdentityService identityService,
                               AuditService auditService) {
        this.runtimeService = runtimeService;
        this.identityService = identityService;
        this.auditService = auditService;
    }

    /**
     * Start a new process instance by definition key.
     *
     * <p>After the instance was started, {@code openCase} inserts the
     * {@code F_BPM_AUDIT_LOG} master row with the process instance id as
     * the business {@code CASE_ID} (caller-supplied key, no sequence).</p>
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

            // the process is started - write the F_BPM_AUDIT_LOG master row
            // for this instance; CASE_ID == process instance id (no allocation)
            auditService.openCase(processDefinitionKey, instance.getId(), initiatorUsername);

            log.info("Started process instance {} (key {}) for initiator {} - BPM CASE_ID = process instance id",
                    instance.getId(), processDefinitionKey, initiatorUsername);
            return instance;
        } finally {
            identityService.setAuthenticatedUserId(null);
        }
    }
}