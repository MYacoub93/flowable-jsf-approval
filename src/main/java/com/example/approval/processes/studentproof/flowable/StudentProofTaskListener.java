package com.example.approval.processes.studentproof.flowable;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.example.approval.audit.BpmAuditConstants.DECISION_APPROVE;
import static com.example.approval.processes.studentproof.StudentProofConstants.*;

/**
 * The single TaskListener attached to both user tasks of the
 * <b>Student Proof Certificate Letter</b> process - mirrors
 * {@code ClearanceTaskListener}:
 *
 * <ul>
 *   <li><b>create</b> - writes the pre-task {@code TASK_ASSIGNED} audit row
 *       via {@link BpmAuditService} and sends the department / student
 *       e-mail via {@link NotificationService};</li>
 *   <li><b>complete</b> - writes the post-task {@code APPROVED} audit row
 *       (acting employee, their note, document info for the Admission and
 *       Registration task; acknowledgement for the student document
 *       task).</li>
 * </ul>
 *
 * <p>Attached once per task in the BPMN via
 * {@code delegateExpression="${studentProofTaskListener}"}.</p>
 */
@Component("studentProofTaskListener")
public class StudentProofTaskListener implements TaskListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(StudentProofTaskListener.class);

    private final NotificationService notificationService;
    private final BpmAuditService auditService;

    public StudentProofTaskListener(NotificationService notificationService,
                                    BpmAuditService auditService) {
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String event = delegateTask.getEventName();
        if (EVENTNAME_CREATE.equals(event)) {
            onCreated(delegateTask);
        } else if (EVENTNAME_COMPLETE.equals(event)) {
            onCompleted(delegateTask);
        }
    }

    // ------------------------------------------------------------------
    // create: TASK_ASSIGNED audit + notification e-mail
    // ------------------------------------------------------------------

    private void onCreated(DelegateTask task) {
        String pid = task.getProcessInstanceId();
        String initiator = str(task.getVariable(VAR_INITIATOR));
        String taskDefKey = task.getTaskDefinitionKey();
        boolean isArTask = TASK_ADMISSION_PROCESSING.equals(taskDefKey);

        if (isArTask) {
            // ---- pre-task audit + department notification ----
            auditService.logTaskAssigned(pid, STAGE_ADMISSION_AND_REGISTRATION,
                    GROUP_ADMISSION_AND_REGISTRATION, GROUP_ADMISSION_AND_REGISTRATION,
                    task.getId(), studentRequestNoteOf(task), initiator);

            notificationService.send(NotificationMessage.builder()
                    .type(NotificationMessage.Type.TASK_ASSIGNED)
                    .processKey(PROCESS_KEY)
                    .processName(PROCESS_NAME)
                    .processInstanceId(pid)
                    .stage(STAGE_ADMISSION_AND_REGISTRATION)
                    .department(GROUP_ADMISSION_AND_REGISTRATION)
                    .candidateGroup(GROUP_ADMISSION_AND_REGISTRATION)
                    .taskId(task.getId())
                    .initiator(initiator)
                    .subject("[" + PROCESS_NAME + "] Processing required by "
                            + GROUP_ADMISSION_AND_REGISTRATION)
                    .intro("A " + PROCESS_NAME + " request is waiting for your department.")
                    .additionalInfo(studentInfoLine(task)
                            + " Please review the request, enter a note and upload the certificate document.")
                    .build());
        } else {
            // ---- final student document task: audit + notify the student ----
            auditService.logTaskAssigned(pid, STAGE_STUDENT_DOCUMENT,
                    "Student", null, task.getId(),
                    "Your certificate document is ready for download.", initiator);

            notificationService.send(NotificationMessage.builder()
                    .type(NotificationMessage.Type.RESULT)
                    .processKey(PROCESS_KEY)
                    .processName(PROCESS_NAME)
                    .processInstanceId(pid)
                    .stage(STAGE_STUDENT_DOCUMENT)
                    .recipientUser(initiator)
                    .taskId(task.getId())
                    .initiator(initiator)
                    .subject("[" + PROCESS_NAME + "] Your certificate is ready")
                    .intro("Your " + PROCESS_NAME + " has been processed and your document is ready.")
                    .additionalInfo("Please open the task to view / download your certificate.")
                    .build());
        }
        log.debug("Student Proof task {} created (definition key {})", task.getId(), taskDefKey);
    }

    // ------------------------------------------------------------------
    // complete: APPROVED audit (employee + note [+ document])
    // ------------------------------------------------------------------

    private void onCompleted(DelegateTask task) {
        String pid = task.getProcessInstanceId();
        String initiator = str(task.getVariable(VAR_INITIATOR));
        String taskDefKey = task.getTaskDefinitionKey();
        String completedBy = str(task.getVariable(VAR_COMPLETED_BY));

        if (TASK_ADMISSION_PROCESSING.equals(taskDefKey)) {
            String note = str(task.getVariable(VAR_AR_NOTE));
            String documentName = str(task.getVariable(VAR_DOCUMENT_NAME));
            String documentUrl = str(task.getVariable(VAR_DOCUMENT_URL));
            auditService.logTaskCompleted(pid, STAGE_ADMISSION_AND_REGISTRATION,
                    GROUP_ADMISSION_AND_REGISTRATION, completedBy, DECISION_APPROVE,
                    note + " | document: " + safe(documentName)
                            + (documentUrl == null ? "" : " (" + documentUrl + ")"),
                    task.getId(), initiator);
        } else {
            // student acknowledged the document
            auditService.logProcessAction(pid, ACTION_RESULT_ACKNOWLEDGED,
                    STAGE_STUDENT_DOCUMENT, "Student",
                    completedBy != null ? completedBy : initiator, initiator,
                    "Student downloaded the certificate document");
        }
        log.debug("Student Proof task {} completed (definition key {})", task.getId(), taskDefKey);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String studentRequestNoteOf(DelegateTask task) {
        String note = str(task.getVariable(VAR_INITIATOR_NOTE));
        return note != null ? note : "Student proof certificate request";
    }

    private String studentInfoLine(DelegateTask task) {
        return "Student: " + safe(str(task.getVariable(VAR_STUDENT_NAME)))
                + " (ID " + safe(str(task.getVariable(VAR_STUDENT_ID))) + ").";
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}