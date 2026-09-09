package com.example.approval.processes.studentproof;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import com.example.approval.processes.studentproof.flowable.StudentProofTaskListener;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.approval.processes.studentproof.StudentProofConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StudentProofTaskListener} - the pre-task audit +
 * notification and the post-task audit of the Student Proof Certificate
 * Letter process (audit and notification services mocked).
 */
@ExtendWith(MockitoExtension.class)
class StudentProofTaskListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private BpmAuditService auditService;

    @Mock
    private DelegateTask task;

    private StudentProofTaskListener listener;

    @BeforeEach
    void setUp() {
        listener = new StudentProofTaskListener(notificationService, auditService);
    }

    private void stubTask(String taskDefKey) {
        when(task.getEventName()).thenReturn(TaskListener.EVENTNAME_CREATE);
        when(task.getTaskDefinitionKey()).thenReturn(taskDefKey);
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(task.getId()).thenReturn("t-1");
        when(task.getVariable(VAR_INITIATOR)).thenReturn("student1");
    }

    // ------------------------------------------------------------------
    // create on the Admission and Registration task
    // ------------------------------------------------------------------

    @Test
    void create_admissionTask_writesPreTaskAuditAndNotifiesDepartment() {
        stubTask(TASK_ADMISSION_PROCESSING);
        when(task.getVariable(VAR_INITIATOR_NOTE)).thenReturn("Please issue my proof certificate");
        when(task.getVariable(VAR_STUDENT_NAME)).thenReturn("John Doe");
        when(task.getVariable(VAR_STUDENT_ID)).thenReturn("S-123");

        listener.notify(task);

        // pre-task audit: TASK_ASSIGNED row for the Admission and Registration group
        verify(auditService).logTaskAssigned("pi-1", STAGE_ADMISSION_AND_REGISTRATION,
                GROUP_ADMISSION_AND_REGISTRATION, GROUP_ADMISSION_AND_REGISTRATION,
                "t-1", "Please issue my proof certificate", "student1");

        // department notification with the student info line
        ArgumentCaptor<NotificationMessage> msg = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).send(msg.capture());
        NotificationMessage sent = msg.getValue();
        assertThat(sent.getType()).isEqualTo(NotificationMessage.Type.TASK_ASSIGNED);
        assertThat(sent.getProcessKey()).isEqualTo(PROCESS_KEY);
        assertThat(sent.getCandidateGroup()).isEqualTo(GROUP_ADMISSION_AND_REGISTRATION);
        assertThat(sent.getTaskId()).isEqualTo("t-1");
        assertThat(sent.getAdditionalInfo())
                .contains("John Doe")
                .contains("S-123");
    }

    @Test
    void create_admissionTask_withoutNote_usesDefaultRequestText() {
        stubTask(TASK_ADMISSION_PROCESSING);
        when(task.getVariable(VAR_INITIATOR_NOTE)).thenReturn(null);

        listener.notify(task);

        verify(auditService).logTaskAssigned(eq("pi-1"), eq(STAGE_ADMISSION_AND_REGISTRATION),
                eq(GROUP_ADMISSION_AND_REGISTRATION), eq(GROUP_ADMISSION_AND_REGISTRATION),
                eq("t-1"), eq("Student proof certificate request"), eq("student1"));
    }

    // ------------------------------------------------------------------
    // create on the final student document task
    // ------------------------------------------------------------------

    @Test
    void create_studentDocumentTask_notifiesInitiator() {
        stubTask(TASK_STUDENT_DOCUMENT);

        listener.notify(task);

        verify(auditService).logTaskAssigned(eq("pi-1"), eq(STAGE_STUDENT_DOCUMENT),
                eq("Student"), eq(null), eq("t-1"),
                contains("ready"), eq("student1"));

        ArgumentCaptor<NotificationMessage> msg = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).send(msg.capture());
        NotificationMessage sent = msg.getValue();
        assertThat(sent.getType()).isEqualTo(NotificationMessage.Type.RESULT);
        assertThat(sent.getRecipientUser()).isEqualTo("student1");
        assertThat(sent.getStage()).isEqualTo(STAGE_STUDENT_DOCUMENT);
    }

    // ------------------------------------------------------------------
    // complete on the Admission and Registration task
    // ------------------------------------------------------------------

    @Test
    void complete_admissionTask_writesApprovedAuditWithNoteAndDocument() {
        when(task.getEventName()).thenReturn(TaskListener.EVENTNAME_COMPLETE);
        when(task.getTaskDefinitionKey()).thenReturn(TASK_ADMISSION_PROCESSING);
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(task.getId()).thenReturn("t-1");
        when(task.getVariable(VAR_INITIATOR)).thenReturn("student1");
        when(task.getVariable(VAR_COMPLETED_BY)).thenReturn("regEmployee1");
        when(task.getVariable(VAR_AR_NOTE)).thenReturn("Certificate generated");
        when(task.getVariable(VAR_DOCUMENT_NAME)).thenReturn("proof-letter.pdf");
        when(task.getVariable(VAR_DOCUMENT_URL)).thenReturn("/ucm/document/pi-1");

        listener.notify(task);

        verify(auditService).logTaskCompleted(eq("pi-1"), eq(STAGE_ADMISSION_AND_REGISTRATION),
                eq(GROUP_ADMISSION_AND_REGISTRATION), eq("regEmployee1"),
                eq(DECISION_APPROVE),
                contains("Certificate generated"),
                eq("t-1"), eq("student1"));

        // no extra notification on completion - the student notification is
        // sent when the final document task is created
        verify(notificationService, never()).send(org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------------------
    // complete on the final student document task
    // ------------------------------------------------------------------

    @Test
    void complete_studentDocumentTask_writesAcknowledgedAudit() {
        when(task.getEventName()).thenReturn(TaskListener.EVENTNAME_COMPLETE);
        when(task.getTaskDefinitionKey()).thenReturn(TASK_STUDENT_DOCUMENT);
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(task.getId()).thenReturn("t-1");
        when(task.getVariable(VAR_INITIATOR)).thenReturn("student1");
        when(task.getVariable(VAR_COMPLETED_BY)).thenReturn("student1");

        listener.notify(task);

        verify(auditService).logProcessAction(eq("pi-1"),
                eq(ACTION_RESULT_ACKNOWLEDGED), eq(STAGE_STUDENT_DOCUMENT),
                eq("Student"), eq("student1"), eq("student1"),
                contains("downloaded"));
    }
}