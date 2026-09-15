package com.example.approval.notification;

import com.example.approval.entity.ExternalUser;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationRecipientResolver;
import com.example.approval.notification.service.impl.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Arrays;
import java.util.List;

import static com.example.approval.processes.clearance.ClearanceConstants.GROUP_ADMISSION_AND_REGISTRATION;
import static com.example.approval.processes.clearance.ClearanceConstants.GROUP_ADMISSION_AND_REGISTRATION_ROLE_CODE;
import static com.example.approval.processes.clearance.ClearanceConstants.GROUP_FINANCE;
import static com.example.approval.processes.clearance.ClearanceConstants.GROUP_FINANCE_ROLE_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
 * now sends the right <i>targeting fields</i> (role code {@code FIN} /
 * {@code REG}, {@code recipientUser} for the initiator); these tests prove
 * the e-mail service turns those fields into the right personal addresses -
 * never a shared group mailbox.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationRecipientChainTest {

    private static final String INITIATOR = "student.test";

    @Mock
    private FlowableIdentityMapper identityMapper;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    private EmailNotificationService notificationService;

    @BeforeEach
    void setUp() {
        // EmailNotificationService resolves the sender ONCE in its
        // constructor -> the provider must be stubbed before construction.
        lenient().when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.setAlwaysLog(false);
        properties.setUserEmailDomain(null); // no config fallbacks - DB only
        notificationService = new EmailNotificationService(properties,
                new NotificationRecipientResolver(identityMapper),
                mailSenderProvider);
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

    private String[] sentTo() {
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue().getTo();
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

        assertThat(sentTo()).containsExactlyInAnyOrder(
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

        assertThat(sentTo()).containsExactlyInAnyOrder(
                "shared@example.edu", "fin.clerk@example.edu");
    }

    @Test
    @DisplayName("Finance group WITHOUT members sends no mail and invents NO group mailbox")
    void financeGroupWithoutMembers_sendsNothing() {
        // this was the production symptom when the display name was used:
        // 'Finance Department' has no members under ROLE_CODE_
        when(identityMapper.findMembersByGroup(GROUP_FINANCE_ROLE_CODE)).thenReturn(List.of());

        notificationService.send(taskAssigned(GROUP_FINANCE_ROLE_CODE, GROUP_FINANCE).build());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
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

        assertThat(sentTo()).containsExactlyInAnyOrder(
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

        assertThat(sentTo()).containsExactly("fin.boss@example.edu");
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

        assertThat(sentTo()).containsExactly("student.test@example.edu");
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

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
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

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SMTP failure never propagates to the workflow")
    void smtpFailure_neverThrows() {
        when(identityMapper.findMembersByGroup(GROUP_FINANCE_ROLE_CODE))
                .thenReturn(List.of(member("fin.boss", "fin.boss@example.edu")));

        doThrow(new RuntimeException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        notificationService.send(taskAssigned(GROUP_FINANCE_ROLE_CODE, GROUP_FINANCE).build());
        // no exception expected - e-mail is infrastructure, not workflow
    }
}