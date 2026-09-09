package com.example.approval.notification;

import com.example.approval.entity.ExternalUser;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.notification.model.GroupEmailResolution;
import com.example.approval.notification.service.NotificationRecipientResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Group → Users → Emails → Recipients resolution of
 * {@link NotificationRecipientResolver} (mapper mocked - no real Oracle /
 * SMTP calls). A group must resolve to the personal addresses of its members,
 * never to a shared group mailbox.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRecipientResolverTest {

    @Mock
    private FlowableIdentityMapper identityMapper;

    private NotificationRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NotificationRecipientResolver(identityMapper);
    }

    private static ExternalUser member(String username, String email) {
        ExternalUser user = new ExternalUser();
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    // ------------------------------------------------------------------
    // group resolution
    // ------------------------------------------------------------------

    @Test
    void group_withMultipleUsers_resolvesAllMemberEmails() {
        when(identityMapper.findMembersByGroup("IT")).thenReturn(Arrays.asList(
                member("usera", "usera@example.com"),
                member("userb", "userb@example.com"),
                member("userc", "userc@example.com")));

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("IT");

        assertThat(resolution.getRecipients()).containsExactlyInAnyOrder(
                "usera@example.com", "userb@example.com", "userc@example.com");
        assertThat(resolution.getResolvedUsers()).isEqualTo(3);
        assertThat(resolution.getSkippedUsers()).isZero();
        assertThat(resolution.isLookupFailed()).isFalse();
        verify(identityMapper).findMembersByGroup("IT");
    }

    @Test
    void group_withOneUser_resolvesThatUserOnly() {
        when(identityMapper.findMembersByGroup("HOD")).thenReturn(List.of(
                member("solo", "solo@example.com")));

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("HOD");

        assertThat(resolution.getRecipients()).containsExactly("solo@example.com");
        assertThat(resolution.getResolvedUsers()).isEqualTo(1);
        assertThat(resolution.getSkippedUsers()).isZero();
    }

    @Test
    void group_memberWithoutEmail_isSkipped() {
        when(identityMapper.findMembersByGroup("DEN")).thenReturn(Arrays.asList(
                member("usera", "usera@example.com"),
                member("noemail", null),
                member("blank", "   ")));

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("DEN");

        assertThat(resolution.getRecipients()).containsExactly("usera@example.com");
        assertThat(resolution.getResolvedUsers()).isEqualTo(3);
        assertThat(resolution.getSkippedUsers()).isEqualTo(2);
    }

    @Test
    void group_memberWithInvalidEmail_isSkipped() {
        when(identityMapper.findMembersByGroup("LGL")).thenReturn(Arrays.asList(
                member("usera", "usera@example.com"),
                member("broken", "not-an-email")));

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("LGL");

        assertThat(resolution.getRecipients()).containsExactly("usera@example.com");
        assertThat(resolution.getSkippedUsers()).isEqualTo(1);
    }

    @Test
    void group_duplicateEmails_areDeduplicated() {
        when(identityMapper.findMembersByGroup("FIN")).thenReturn(Arrays.asList(
                member("usera", "shared@example.com"),
                member("userb", "shared@example.com"),
                member("userc", "SHARED@example.com")));

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("FIN");

        assertThat(resolution.getRecipients()).containsExactly("shared@example.com");
        assertThat(resolution.getResolvedUsers()).isEqualTo(3);
        assertThat(resolution.getSkippedUsers()).isZero();
    }

    @Test
    void group_duplicateUsernames_areResolvedOnce() {
        // a user carrying the same role twice (multi-role SIS row)
        when(identityMapper.findMembersByGroup("LIB")).thenReturn(Arrays.asList(
                member("usera", "usera@example.com"),
                member("usera", "usera@example.com"),
                member("userb", "userb@example.com")));

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("LIB");

        assertThat(resolution.getResolvedUsers()).isEqualTo(2);
        assertThat(resolution.getRecipients()).containsExactlyInAnyOrder(
                "usera@example.com", "userb@example.com");
    }

    @Test
    void emptyGroup_yieldsNoRecipients_withoutError() {
        when(identityMapper.findMembersByGroup("EMPTY")).thenReturn(Collections.emptyList());

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("EMPTY");

        assertThat(resolution.getRecipients()).isEmpty();
        assertThat(resolution.getResolvedUsers()).isZero();
        assertThat(resolution.isLookupFailed()).isFalse();
    }

    @Test
    void unknownGroup_isHandledGracefully() {
        // unknown and empty groups are indistinguishable in FLOWABLE_USERS_VW
        when(identityMapper.findMembersByGroup("NO_SUCH_GROUP")).thenReturn(Collections.emptyList());

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("NO_SUCH_GROUP");

        assertThat(resolution.getRecipients()).isEmpty();
        assertThat(resolution.isLookupFailed()).isFalse();
    }

    @Test
    void lookupFailure_isHandledGracefully() {
        when(identityMapper.findMembersByGroup("IT")).thenThrow(new RuntimeException("ORA-01017"));

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("IT");

        assertThat(resolution.getRecipients()).isEmpty();
        assertThat(resolution.getResolvedUsers()).isZero();
        assertThat(resolution.isLookupFailed()).isTrue();
    }

    @Test
    void nullMembersResult_isHandledGracefully() {
        when(identityMapper.findMembersByGroup("IT")).thenReturn(null);

        GroupEmailResolution resolution = resolver.resolveGroupRecipients("IT");

        assertThat(resolution.getRecipients()).isEmpty();
        assertThat(resolution.isLookupFailed()).isFalse();
    }

    @Test
    void blankGroupId_doesNotTouchTheDatabase() {
        GroupEmailResolution blank = resolver.resolveGroupRecipients("");
        GroupEmailResolution nullGroup = resolver.resolveGroupRecipients(null);

        assertThat(blank.getRecipients()).isEmpty();
        assertThat(nullGroup.getRecipients()).isEmpty();
        verify(identityMapper, never()).findMembersByGroup(anyString());
    }

    // ------------------------------------------------------------------
    // individual user resolution (unchanged behavior)
    // ------------------------------------------------------------------

    @Test
    void resolveUserEmail_returnsTrimmedAddress() {
        when(identityMapper.findEmailByUsername("usera")).thenReturn(" usera@example.com ");

        assertThat(resolver.resolveUserEmail("usera")).isEqualTo("usera@example.com");
    }

    @Test
    void resolveUserEmail_unknownUser_returnsNull() {
        when(identityMapper.findEmailByUsername("ghost")).thenReturn(null);

        assertThat(resolver.resolveUserEmail("ghost")).isNull();
    }

    @Test
    void resolveUserEmail_lookupFailure_returnsNull() {
        when(identityMapper.findEmailByUsername("usera")).thenThrow(new RuntimeException("ORA-03113"));

        assertThat(resolver.resolveUserEmail("usera")).isNull();
    }
}