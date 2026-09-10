package com.taskmanagement.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.taskmanagement.dto.user.AccountSecurityRequest;
import com.taskmanagement.dto.auth.GoogleProfile;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.exception.ForbiddenException;
import com.taskmanagement.model.*;
import com.taskmanagement.repository.*;
import com.taskmanagement.service.auth.GoogleIdTokenService;
import com.taskmanagement.service.user.AccountSecurityService;
import com.taskmanagement.service.user.SecurityChangeLimiter;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {
    @Mock UserRepository users;
    @Mock UserIdentityRepository identities;
    @Mock PasswordEncoder passwords;
    @Mock GoogleIdTokenService google;
    @Mock SecurityChangeLimiter limiter;
    AccountSecurityService service;
    User user;
    @BeforeEach void setup() {
        service = new AccountSecurityService(users, identities, passwords, google, limiter);
        user = new User(); user.setId(1L); user.setUsername("alice"); user.setPassword("hash"); user.setSecurityStamp(null);
        when(users.findForSecurityUpdate("alice")).thenReturn(Optional.of(user));
    }
    @Test void wrongPasswordCannotMutateAccount() {
        assertThatThrownBy(() -> service.change("alice", new AccountSecurityRequest("wrong", null, null, "a long new password", "a long new password")))
                .isInstanceOf(BadRequestException.class);
        verify(users, never()).saveAndFlush(any());
        assertThat(user.getPassword()).isEqualTo("hash");
        verify(limiter).check(1L);
    }
    @Test void successfulChangeRevokesOldSessionsIncludingLegacyTokens() {
        when(passwords.matches("current", "hash")).thenReturn(true);
        when(passwords.encode("a long new password")).thenReturn("newHash");
        service.change("alice", new AccountSecurityRequest("current", null, null, "a long new password", "a long new password"));
        assertThat(user.getPassword()).isEqualTo("newHash");
        assertThat(user.acceptsSession("legacy-session")).isFalse();
        assertThat(user.acceptsSession(user.getSecurityStamp() + ".new-session")).isTrue();
        verify(users).saveAndFlush(user);
    }
    @Test void confirmationMismatchCannotEncodeOrSave() {
        when(passwords.matches("current", "hash")).thenReturn(true);
        assertThatThrownBy(() -> service.change("alice", new AccountSecurityRequest("current", null, null, "a long new password", "different password")))
                .isInstanceOf(BadRequestException.class);
        verify(passwords, never()).encode(any());
        verify(users, never()).saveAndFlush(any());
    }
    @Test void bcryptByteLimitIsEnforcedForUnicode() {
        when(passwords.matches("current", "hash")).thenReturn(true);
        String value = "界".repeat(25);
        assertThatThrownBy(() -> service.change("alice", new AccountSecurityRequest("current", null, null, value, value)))
                .isInstanceOf(BadRequestException.class);
        verify(passwords, never()).encode(any());
    }
    @Test void anotherGoogleIdentityCannotChangePassword() {
        when(google.verifyRecent("credential")).thenReturn(new GoogleProfile("other", "other@example.com", null, null));
        UserIdentity linked = new UserIdentity(); linked.setProviderSubject("linked");
        when(identities.findByUserIdAndProvider(1L, AuthProvider.GOOGLE)).thenReturn(Optional.of(linked));
        assertThatThrownBy(() -> service.change("alice", new AccountSecurityRequest(null, "credential", null, "a long new password", "a long new password")))
                .isInstanceOf(BadRequestException.class);
        verify(users, never()).saveAndFlush(any());
    }
    @Test void linkedGoogleCanSetAppPasswordWithoutKnowingGeneratedPassword() {
        when(google.verifyRecent("credential")).thenReturn(new GoogleProfile("linked", "alice@example.com", null, null));
        UserIdentity linked = new UserIdentity(); linked.setProviderSubject("linked");
        when(identities.findByUserIdAndProvider(1L, AuthProvider.GOOGLE)).thenReturn(Optional.of(linked));
        when(passwords.encode("a long new password")).thenReturn("newHash");
        service.change("alice", new AccountSecurityRequest(null, "credential", null, "a long new password", "a long new password"));
        assertThat(user.getPassword()).isEqualTo("newHash");
        assertThat(user.acceptsSession("old-session")).isFalse();
    }
    @Test void usernameChangePreservesInternalIdentityAndInvalidatesSessions() {
        when(passwords.matches("current", "hash")).thenReturn(true);
        service.change("alice", new AccountSecurityRequest("current", null, "alice_new", null, null));
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getUsername()).isEqualTo("alice_new");
        assertThat(user.acceptsSession("old-session")).isFalse();
    }

    @Test void expiredGoogleProofProducesRecoverableFormError() {
        when(google.verifyRecent("expired")).thenThrow(new com.taskmanagement.exception.InvalidGoogleCredentialException());
        assertThatThrownBy(() -> service.change("alice", new AccountSecurityRequest(null, "expired", null,
                "a long new password", "a long new password"))).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Verify with Google again");
        verify(users, never()).saveAndFlush(any());
    }

    @Test void twoProofMethodsAreRejected() {
        assertThatThrownBy(() -> service.change("alice", new AccountSecurityRequest("current", "credential", null,
                "a long new password", "a long new password"))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(google, passwords);
    }
}
