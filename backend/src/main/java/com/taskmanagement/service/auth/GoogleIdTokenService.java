package com.taskmanagement.service.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.taskmanagement.dto.auth.GoogleProfile;
import com.taskmanagement.exception.InvalidGoogleCredentialException;

@Service
public class GoogleIdTokenService {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleIdTokenService(@Value("${app.google.client-id:}") String clientId) {
        this(clientId, buildVerifier(clientId));
    }

    GoogleIdTokenService(String clientId, GoogleIdTokenVerifier verifier) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.verifier = verifier;
    }

    /**
     * Verifies the Google signature, audience, issuer and expiration before
     * exposing identity claims to the application authentication layer.
     */
    public GoogleProfile verify(String credential) {
        return verify(credential, false);
    }

    private GoogleProfile verify(String credential, boolean requireRecent) {
        if (clientId.isBlank()) {
            throw new IllegalStateException("Google client ID is not configured");
        }
        if (credential == null || credential.isBlank()) {
            throw new InvalidGoogleCredentialException();
        }

        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new InvalidGoogleCredentialException();
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (requireRecent) {
                Long issuedAt = payload.getIssuedAtTimeSeconds();
                long now = java.time.Instant.now().getEpochSecond();
                if (issuedAt == null || issuedAt < now - 300 || issuedAt > now + 30) {
                    throw new InvalidGoogleCredentialException();
                }
            }
            String subject = requireClaim(payload.getSubject());
            String email = requireClaim(payload.getEmail());

            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new InvalidGoogleCredentialException();
            }

            return new GoogleProfile(
                    subject,
                    email,
                    optionalClaim(payload.get("name")),
                    optionalClaim(payload.get("picture"))
            );
        } catch (GeneralSecurityException | IOException ex) {
            throw new InvalidGoogleCredentialException(ex);
        }
    }

    private static GoogleIdTokenVerifier buildVerifier(String clientId) {
        String normalizedClientId = clientId == null ? "" : clientId.trim();
        return new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(List.of(normalizedClientId))
                .build();
    }

    public GoogleProfile verifyRecent(String credential) {
        return verify(credential, true);
    }

    private static String requireClaim(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidGoogleCredentialException();
        }
        return value;
    }

    private static String optionalClaim(Object value) {
        if (!(value instanceof String claim) || claim.isBlank()) {
            return null;
        }
        return claim;
    }
}
