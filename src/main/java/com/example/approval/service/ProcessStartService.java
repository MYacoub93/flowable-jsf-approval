package com.example.approval.service;

import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.approval.audit.BpmAuditConstants;
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
     * <p>BPM audit case handling runs in two steps around the start:</p>
     * <ol>
     *   <li>the numeric {@code CASE_ID} is reserved before the start and
     *       passed as the {@code bpmCaseId} start variable - the
     *       synchronous part of the start (service tasks, first
     *       task-create listeners) already writes {@code BPM_AUDIT_LOG_DTL}
     *       rows that need the case linkage;</li>
     *   <li>once the instance is running, {@code openCase} inserts the
     *       {@code BPM_AUDIT_LOG} master row for the started instance.</li>
     * </ol>
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

            // 1) reserve the CASE_ID pre-start so listeners / service tasks
            //    running inside the start command can already link to it
            Long bpmCaseId = auditService.allocateCaseId();
            vars.put(BpmAuditConstants.VAR_CASE_ID, bpmCaseId);

            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    processDefinitionKey,
                    processDefinitionKey + "-" + System.currentTimeMillis(),
                    vars);

            // 2) the process is started - now write the BPM_AUDIT_LOG master
            //    row for this instance (uses the bpmCaseId already set)
            auditService.openCase(processDefinitionKey, instance.getId(), initiatorUsername);

            log.info("Started process instance {} (key {}) for initiator {} (BPM case {})",
                    instance.getId(), processDefinitionKey, initiatorUsername, bpmCaseId);
            return instance;
        } finally {
            identityService.setAuthenticatedUserId(null);
        }
    }
}