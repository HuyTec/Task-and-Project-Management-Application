package com.taskmanagement.service.user;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.taskmanagement.dto.user.AccountSecurityRequest;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.exception.ForbiddenException;
import com.taskmanagement.exception.DuplicatedResourceException;
import com.taskmanagement.model.AuthProvider;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.repository.UserIdentityRepository;
import com.taskmanagement.service.auth.GoogleIdTokenService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountSecurityService {
    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final PasswordEncoder passwords;
    private final GoogleIdTokenService google;
    private final SecurityChangeLimiter limiter;

    public boolean googleLinked(String username) {
        return users.findByUsername(username).map(user -> identities
                .findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE).isPresent()).orElse(false);
    }

    /** Locks the account so concurrent security changes cannot overwrite each other's proof or session stamp. */
    @Transactional
    public void change(String currentUsername, AccountSecurityRequest request) {
        var user = users.findForSecurityUpdate(currentUsername)
                .orElseThrow(() -> new ForbiddenException("Account is unavailable"));
        limiter.check(user.getId());
        if (request.googleCredential() != null && !request.googleCredential().isBlank()
                && request.currentPassword() != null && !request.currentPassword().isBlank()) {
            throw new BadRequestException("Choose one identity verification method");
        }
        if (request.googleCredential() != null && !request.googleCredential().isBlank()) {
            com.taskmanagement.dto.auth.GoogleProfile profile;
            try {
                profile = google.verifyRecent(request.googleCredential());
            } catch (com.taskmanagement.exception.InvalidGoogleCredentialException ex) {
                throw new BadRequestException("Google verification expired or failed. Verify with Google again.");
            }
            var identity = identities.findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)
                    .orElseThrow(() -> new BadRequestException("Google is not linked to this account"));
            if (!identity.getProviderSubject().equals(profile.subject())) {
                throw new BadRequestException("Use the Google account linked to this profile");
            }
        } else if (request.currentPassword() == null || request.currentPassword().isBlank()
                || !passwords.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        boolean changed = false;
        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (!request.username().matches("[A-Za-z0-9_]{3,50}")) throw new BadRequestException("Invalid username");
            if (users.existsByUsername(request.username())) throw new DuplicatedResourceException("Username is already taken");
            user.setUsername(request.username());
            changed = true;
        }
        if (request.newPassword() != null) {
            String value = request.newPassword();
            if (value.codePointCount(0, value.length()) < 15 || value.getBytes(StandardCharsets.UTF_8).length > 72
                    || value.isBlank()) throw new BadRequestException("Use at least 15 characters and at most 72 UTF-8 bytes");
            if (!value.equals(request.confirmPassword())) throw new BadRequestException("Passwords do not match");
            if (passwords.matches(value, user.getPassword())) throw new BadRequestException("Choose a different password");
            user.setPassword(passwords.encode(value));
            changed = true;
        }
        if (!changed) throw new BadRequestException("No security changes to save");
        user.setSecurityStamp(UUID.randomUUID().toString());
        users.saveAndFlush(user);
    }
}
