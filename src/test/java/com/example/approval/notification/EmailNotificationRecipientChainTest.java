package com.example.approval.notification;

import com.example.approval.entity.ExternalUser;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.notification.model.EmailDispatchRequest;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.AsyncEmailDispatcher;
import com.example.approval.notification.service.NotificationRecipientResolver;
import com.example.approval.notification.service.impl.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static com.example.approval.processes.clearance.ClearanceConstants.GROUP_ADMISSION_AND_REGISTRATION;
import static com.example.approval.processes.clearance.ClearanceConstants.GROUP_ADMISSION_AND_REGISTRATION_ROLE_CODE;
import static com.example.approval.processes.clearance.ClearanceConstants.GROUP_FINANCE;
import static com.example.approval.processes.clearance.ClearanceConstants.GROUP_FINANCE_ROLE_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recipient-chain tests for {@link EmailNotificationService}: a
 * {@link NotificationMessage} exactly like the ones
 * {@code ClearanceTaskListener} builds must resolve to the CORRECT e-mail
 * addresses (Group -> Users -> Emails via {@code FLOWABLE_USERS_VW}).
 *
 * <p>This is the second half of the missing-notification regression: the
 * engine tests ({@code ClearanceNotificationEngineTest}) prove the listener
 * sends the right <i>targeting fields</i> (role code {@code FIN} /
 * {@code REG}, {@code recipientUser} for the initiator); these tests prove
 * the e-mail service turns those fields into the right personal addresses -
 * never a shared group mailbox.</p>
 *
 * <p>Since the async refactor the service no longer touches
 * {@code JavaMailSender} itself: it resolves everything on the calling
 * thread and hands an immutable {@link EmailDispatchRequest} to
 * {@link AsyncEmailDispatcher}. The dispatcher is mocked here, so these
 * tests verify SYNCHRONOUSLY what payload crosses the async boundary -
 * recipients, subject and body included. The asynchronous execution itself
 * (thread, non-blocking, SMTP failure isolation) is covered by
 * {@code AsyncEmailDispatcherTest} and
 * {@code EmailNotificationAsyncIntegrationTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationRecipientChainTest {

    private static final String INITIATOR = "student.test";

    @Mock
    private FlowableIdentityMapper identityMapper;

    @Mock
    private AsyncEmailDispatcher asyncEmailDispatcher;

    private EmailNotificationService notificationService;

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.setAlwaysLog(false);
        properties.setUserEmailDomain(null); // no config fallbacks - DB only
        notificationService = new EmailNotificationService(properties,
                new NotificationRecipientResolver(identityMapper),
                asyncEmailDispatcher);
    }

    private static ExternalUser member(String username, String email) {
        ExternalUser user = new ExternalUser();
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    private static NotificationMessage.Builder taskAssigned(
            String candidateGroup, String department) {
        return NotificationMessage.builder()
                .type(NotificationMessage.Type.TASK_ASSIGNED)
                .processKey("clearanceLetterProcess")
                .processName("Clearance Letter")
                .processInstanceId("pid-1")
                .stage("FINANCE")
                .department(department)
                .candidateGroup(candidateGroup)
                .initiator(INITIATOR)
                .subject("[Clearance Letter] Approval required by " + department);
    }

    /** The single dispatched payload (there is exactly one send per test). */
    private EmailDispatchRequest dispatched() {
        ArgumentCaptor<EmailDispatchRequest> captor =
                ArgumentCaptor.forClass(EmailDispatchRequest.class);
        verify(asyncEmailDispatcher).dispatch(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // Finance: FIN role code -> ALL valid member addresses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Finance task: FIN group resolves to every valid member e-mail")
    void financeGroupTask_mailsAllValidMemberEmails() {
        when(identityMapper.findMembersByGroup(GROUP_FINANCE_ROLE_CODE)).thenReturn(Arrays.asList(
                member("fin.boss", "fin.boss@example.edu"),
                member("fin.clerk", "fin.clerk@example.edu"),
                member("fin.noemail", null)));

        notificationService.send(taskAssigned(GROUP_FINANCE_ROLE_CODE, GROUP_FINANCE).build());

        assertThat(dispatched().getRecipients()).containsExactlyInAnyOrder(
                "fin.boss@example.edu", "fin.clerk@example.edu");
    }

    @Test
    @DisplayName("Finance task with duplicate members: addresses are deduplicated")
    void financeGroupTask_deduplicatesMemberEmails() {
        when(identityMapper.findMembersByGroup(GROUP_FINANCE_ROLE_CODE)).thenReturn(Arrays.asList(
                member("fin.boss", "shared@example.edu"),
                member("fin.deputy", "shared@example.edu"),
                member("fin.clerk", "fin.clerk@example.edu")));

        notificationService.send(taskAssigned(GROUP_FINANCE_ROLE_CODE, GROUP_FINANCE).build());

        assertThat(dispatched().getRecipients()).containsExactlyInAnyOrder(
                "shared@example.edu", "fin.clerk@example.edu");
    }

    @Test
    @DisplayName("Finance group WITHOUT members sends no mail and invents NO group mailbox")
    void financeGroupWithoutMembers_sendsNothing() {
        // this was the production symptom when the display name was used:
        // 'Finance Department' has no members under ROLE_CODE_
        when(identityMapper.findMembersByGroup(GROUP_FINANCE_ROLE_CODE)).thenReturn(List.of());

        notificationService.send(taskAssigned(GROUP_FINANCE_ROLE_CODE, GROUP_FINANCE).build());

        verify(asyncEmailDispatcher, never()).dispatch(any());
    }

    // ------------------------------------------------------------------
    // Admission & Registration: REG role code
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Admission task: REG group resolves to every valid member e-mail")
    void admissionGroupTask_mailsAllValidMemberEmails() {
        when(identityMapper.findMembersByGroup(GROUP_ADMISSION_AND_REGISTRATION_ROLE_CODE))
                .thenReturn(Arrays.asList(
                        member("reg.head", "reg.head@example.edu"),
                        member("reg.officer", "reg.officer@example.edu")));

        notificationService.send(taskAssigned(GROUP_ADMISSION_AND_REGISTRATION_ROLE_CODE,
                GROUP_ADMISSION_AND_REGISTRATION).build());

        assertThat(dispatched().getRecipients()).containsExactlyInAnyOrder(
                "reg.head@example.edu", "reg.officer@example.edu");
    }

    // ------------------------------------------------------------------
    // Claimed task -> assignee e-mail only
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Claimed task (assigneeUser): only that person's e-mail is used")
    void claimedTask_mailsOnlyTheAssignee() {
        when(identityMapper.findEmailByUsername("fin.boss"))
                .thenReturn("fin.boss@example.edu");

        notificationService.send(taskAssigned(GROUP_FINANCE_ROLE_CODE, GROUP_FINANCE)
                .assigneeUser("fin.boss")
                .build());

        assertThat(dispatched().getRecipients()).containsExactly("fin.boss@example.edu");
    }

    // ------------------------------------------------------------------
    // Initiator (amendment) notification -> personal e-mail only
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Amendment notification: initiator receives his personal e-mail")
    void amendmentNotification_mailsTheInitiatorPersonally() {
        when(identityMapper.findEmailByUsername(INITIATOR))
                .thenReturn("student.test@example.edu");

        notificationService.send(NotificationMessage.builder()
                .type(NotificationMessage.Type.TASK_ASSIGNED)
                .processKey("clearanceLetterProcess")
                .processName("Clearance Letter")
                .processInstanceId("pid-1")
                .stage("AMENDMENT")
                .taskId("task-9")
                .initiator(INITIATOR)
                .recipientUser(INITIATOR)
                .subject("[Clearance Letter] Action required: amend your clearance request")
                .intro("Your clearance request was rejected and returned to you for amendment.")
                .build());

        EmailDispatchRequest dispatched = dispatched();
        assertThat(dispatched.getRecipients()).containsExactly("student.test@example.edu");
        assertThat(dispatched.getSubject())
                .isEqualTo("[Clearance Letter] Action required: amend your clearance request");
        assertThat(dispatched.getProcessInstanceId()).isEqualTo("pid-1");
        assertThat(dispatched.getTaskId()).isEqualTo("task-9");
        assertThat(dispatched.getNotificationType()).isEqualTo("TASK_ASSIGNED");
        assertThat(dispatched.getBody())
                .contains("Your clearance request was rejected and returned to you for amendment.");
    }

    @Test
    @DisplayName("Initiator without e-mail: no mail, no exception, no fallback mailbox")
    void initiatorWithoutEmail_sendsNothing() {
        when(identityMapper.findEmailByUsername(INITIATOR)).thenReturn(null);

        notificationService.send(NotificationMessage.builder()
                .type(NotificationMessage.Type.TASK_ASSIGNED)
                .processKey("clearanceLetterProcess")
                .processInstanceId("pid-1")
                .stage("AMENDMENT")
                .recipientUser(INITIATOR)
                .subject("[Clearance Letter] amend")
                .build());

        verify(asyncEmailDispatcher, never()).dispatch(any());
    }

    // ------------------------------------------------------------------
    // robustness
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SIS lookup failure during group resolution never throws")
    void sisLookupFailure_isSwallowed() {
        when(identityMapper.findMembersByGroup(GROUP_FINANCE_ROLE_CODE))
                .thenThrow(new RuntimeException("ORA-01017"));

        notificationService.send(taskAssigned(GROUP_FINANCE_ROLE_CODE, GROUP_FINANCE).build());

        verify(asyncEmailDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Queue rejection during scheduling never propagates to the workflow")
    void schedulingRejection_neverThrows() {
        when(identityMapper.findMembersByGroup(GROUP_FINANCE_ROLE_CODE))
                .thenReturn(List.of(member("fin.boss", "fin.boss@example.edu")));

        // bounded executor queue full (SMTP outage) -> rejection surfaces at
        // the dispatch() call and MUST be swallowed by the service
        doThrow(new java.util.concurrent.RejectedExecutionException("queue full"))
                .when(asyncEmailDispatcher).dispatch(any());

        notificationService.send(taskAssigned(GROUP_FINANCE_ROLE_CODE, GROUP_FINANCE).build());
        // no exception expected - e-mail is infrastructure, not workflow
    }
}