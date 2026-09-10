package com.taskmanagement.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.taskmanagement.dto.auth.GoogleProfile;
import com.taskmanagement.exception.InvalidGoogleCredentialException;

@ExtendWith(MockitoExtension.class)
class GoogleIdTokenServiceTest {

    @Mock private GoogleIdTokenVerifier verifier;
    @Mock private GoogleIdToken idToken;

    private GoogleIdTokenService service;

    @BeforeEach
    void setUp() {
        service = new GoogleIdTokenService("web-client-id", verifier);
    }

    @Test
    void validCredentialReturnsVerifiedGoogleProfile() throws Exception {
        GoogleIdToken.Payload payload = validPayload();
        payload.set("name", "Alice Example");
        payload.set("picture", "https://example.com/alice.png");

        when(verifier.verify("google-credential")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);

        GoogleProfile result = service.verify("google-credential");

        assertThat(result).isEqualTo(new GoogleProfile(
                "google-subject",
                "alice@example.com",
                "Alice Example",
                "https://example.com/alice.png"
        ));
    }

    @Test
    void invalidSignatureOrAudienceIsRejected() throws Exception {
        when(verifier.verify("invalid-credential")).thenReturn(null);

        assertThatThrownBy(() -> service.verify("invalid-credential"))
                .isInstanceOf(InvalidGoogleCredentialException.class)
                .hasMessage("Invalid Google credential");
    }

    @Test
    void unverifiedEmailIsRejected() throws Exception {
        GoogleIdToken.Payload payload = validPayload();
        payload.setEmailVerified(false);

        when(verifier.verify("google-credential")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);

        assertThatThrownBy(() -> service.verify("google-credential"))
                .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void missingStableSubjectIsRejected() throws Exception {
        GoogleIdToken.Payload payload = validPayload();
        payload.setSubject(" ");

        when(verifier.verify("google-credential")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);

        assertThatThrownBy(() -> service.verify("google-credential"))
                .isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test
    void missingClientIdFailsBeforeCredentialVerification() {
        GoogleIdTokenService unconfiguredService = new GoogleIdTokenService(" ", verifier);

        assertThatThrownBy(() -> unconfiguredService.verify("google-credential"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Google client ID is not configured");

        verifyNoInteractions(verifier);
    }

    private GoogleIdToken.Payload validPayload() {
        return new GoogleIdToken.Payload()
                .setSubject("google-subject")
                .setEmail("alice@example.com")
                .setEmailVerified(true);
    }

    @Test void recentVerifiedTokenIsAcceptedForSecurityChange() throws Exception {
        var payload = validPayload().setIssuedAtTimeSeconds(java.time.Instant.now().getEpochSecond());
        when(verifier.verify("recent")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        assertThat(service.verifyRecent("recent").subject()).isEqualTo("google-subject");
    }

    @Test void oldVerifiedTokenCannotBeReusedForSecurityChange() throws Exception {
        var payload = validPayload().setIssuedAtTimeSeconds(java.time.Instant.now().getEpochSecond() - 360);
        when(verifier.verify("old")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        assertThatThrownBy(() -> service.verifyRecent("old")).isInstanceOf(InvalidGoogleCredentialException.class);
    }

    @Test void missingIssueTimeCannotAuthorizeSecurityChange() throws Exception {
        when(verifier.verify("missing-time")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(validPayload());
        assertThatThrownBy(() -> service.verifyRecent("missing-time")).isInstanceOf(InvalidGoogleCredentialException.class);
    }
}
