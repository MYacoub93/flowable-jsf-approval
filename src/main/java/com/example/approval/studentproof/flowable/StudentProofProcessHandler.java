package com.example.approval.studentproof.flowable;

import org.flowable.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.example.approval.studentproof.StudentProofConstants.PROCESS_KEY;
import static com.example.approval.studentproof.StudentProofConstants.VAR_INITIATOR;

/**
 * Execution-level handler of the <b>Student Proof Certificate Letter</b>
 * process, referenced from the BPMN via the Spring bean expression
 * {@code ${studentProofProcessHandler.processStarted(execution)}} - mirrors
 * {@code ClearanceProcessHandler} of the Clearance Letter process.
 *
 * <p>It stays deliberately lean: the {@code F_BPM_AUDIT_LOG} master row and
 * the opening {@code ENTERED} detail row of the business audit trail are
 * already written by the shared
 * {@code ProcessStartService.startProcess(...)} right after the process
 * instance is started, and all task-level audit / notification logic lives
 * in {@link StudentProofTaskListener}. This handler therefore only performs
 * process-level bookkeeping that must run inside the Flowable execution
 * context.</p>
 */
@Component("studentProofProcessHandler")
public class StudentProofProcessHandler {

    private static final Logger log = LoggerFactory.getLogger(StudentProofProcessHandler.class);

    /**
     * ExecutionListener (start of process): logs the start of the case.
     * The audit master row (case) is opened by {@code ProcessStartService}
     * after {@code startProcessInstanceByKey} returns, so no duplicate audit
     * record is written here.
     */
    public void processStarted(DelegateExecution execution) {
        String initiator = (String) execution.getVariable(VAR_INITIATOR);
        log.info("{} process instance {} started by student {}",
                PROCESS_KEY, execution.getProcessInstanceId(), initiator);
    }
}