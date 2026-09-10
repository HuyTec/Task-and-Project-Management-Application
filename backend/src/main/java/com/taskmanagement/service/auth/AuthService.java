package com.taskmanagement.service.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.taskmanagement.dto.auth.AccessInfo;
import com.taskmanagement.dto.auth.GoogleLinkRequest;
import com.taskmanagement.dto.auth.GoogleLoginRequest;
import com.taskmanagement.dto.auth.GoogleProfile;
import com.taskmanagement.dto.auth.LoginRequest;
import com.taskmanagement.dto.auth.RefreshTokenClaims;
import com.taskmanagement.dto.auth.RegisterRequest;
import com.taskmanagement.dto.user.UserResponse;
import com.taskmanagement.exception.DuplicatedResourceException;
import com.taskmanagement.exception.ConflictException;
import com.taskmanagement.exception.InvalidRefreshTokenException;
import com.taskmanagement.mapper.UserMapper;
import com.taskmanagement.model.AuthProvider;
import com.taskmanagement.model.User;
import com.taskmanagement.model.UserIdentity;
import com.taskmanagement.model.UserRole;
import com.taskmanagement.repository.UserIdentityRepository;
import com.taskmanagement.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;

@Service
@Validated
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;
    private final UserMapper userMapper;
    private final AuthSessionService authSessionService;
    private final GoogleIdTokenService googleIdTokenService;
    private final UserIdentityRepository userIdentityRepository;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authManager,
            UserMapper userMapper,
            AuthSessionService authSessionService,
            GoogleIdTokenService googleIdTokenService,
            UserIdentityRepository userIdentityRepository
    ) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.authSessionService = authSessionService;
        this.googleIdTokenService = googleIdTokenService;
        this.userIdentityRepository = userIdentityRepository;
    }

    public AccessInfo login(@Valid LoginRequest request) {
        String username = request.username();

        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.password())
        );

        

        User user = userRepository.findByUsernameAndIsDeactivatedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found " + username));

        return issueSession(user, UUID.randomUUID().toString());
    }

    @Transactional
    public AccessInfo loginWithGoogle(@Valid GoogleLoginRequest request) {
        GoogleProfile profile = googleIdTokenService.verify(request.credential());

        return userIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, profile.subject())
                .map(UserIdentity::getUser)
                .map(this::issueSessionForActiveUser)
                .orElseGet(() -> createGoogleUserOrRequireLink(profile));
    }

    @Transactional
    public AccessInfo confirmGoogleLink(@Valid GoogleLinkRequest request) {
        GoogleProfile profile = googleIdTokenService.verify(request.credential());

        return userIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, profile.subject())
                .map(UserIdentity::getUser)
                .map(this::issueSessionForActiveUser)
                .orElseGet(() -> linkExistingUser(profile, request.password()));
    }

    @Transactional
    public AccessInfo register(@Valid RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicatedResourceException("Username is existed");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicatedResourceException("Email is existed");
        }

        User user = new User();
        user.setDisplayName(request.displayName());
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);

        userRepository.save(user);
        return issueSession(user, UUID.randomUUID().toString());
    }

    public AccessInfo refresh(String refreshToken) {
        RefreshTokenClaims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findByUsernameAndIsDeactivatedFalse(claims.username())
                .orElseThrow(InvalidRefreshTokenException::new);

        String newTokenId = UUID.randomUUID().toString();
        if (!user.acceptsSession(claims.sessionId())) throw new InvalidRefreshTokenException();
        boolean rotated = authSessionService.rotate(
                claims.sessionId(),
                claims.tokenId(),
                newTokenId
        );
        if (!rotated) {
            throw new InvalidRefreshTokenException();
        }

        String accessToken = jwtService.generateAccessToken(claims.username(), claims.sessionId());
        String newRefreshToken = jwtService.generateRefreshToken(
                claims.username(),
                claims.sessionId(),
                newTokenId
        );
        UserResponse userResponse = userMapper.toUserResponse(user);

        return new AccessInfo(accessToken, newRefreshToken, userResponse);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            RefreshTokenClaims claims = jwtService.parseRefreshToken(refreshToken);
            authSessionService.revoke(claims.sessionId());
        } catch (JwtException | IllegalArgumentException ignored) {
            // The client cookie is still cleared by the controller.
        }
    }

    private AccessInfo issueSession(User user, String sessionId) {
        if (user.getSecurityStamp() != null) sessionId = user.getSecurityStamp() + "." + sessionId;
        String tokenId = UUID.randomUUID().toString();
        String accessToken = jwtService.generateAccessToken(user.getUsername(), sessionId);
        String refreshToken = jwtService.generateRefreshToken(
                user.getUsername(),
                sessionId,
                tokenId
        );

        authSessionService.create(sessionId, tokenId);

        UserResponse userResponse = userMapper.toUserResponse(user);
        return new AccessInfo(accessToken, refreshToken, userResponse);
    }

    private AccessInfo createGoogleUserOrRequireLink(GoogleProfile profile) {
        String email = normalizeEmail(profile.email());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("Confirm your current password to link this Google account");
        }

        User user = new User();
        user.setUsername(generateGoogleUsername(email, profile.subject()));
        user.setDisplayName(defaultDisplayName(profile, email));
        user.setEmail(email);
       user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setProfilePictureUrl(profile.profilePictureUrl());
        user.setRole(UserRole.USER);
        userRepository.save(user);

        saveGoogleIdentity(user, profile, email);
        return issueSession(user, UUID.randomUUID().toString());
    }

    private AccessInfo linkExistingUser(GoogleProfile profile, String password) {
        String email = normalizeEmail(profile.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.isDeactivated())
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                        "Invalid username or password"
                ));

        authManager.authenticate(new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                password
        ));

        if (userIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE).isPresent()) {
            throw new ConflictException("This account is already linked to another Google account");
        }
        saveGoogleIdentity(user, profile, email);
        if ((user.getProfilePictureUrl() == null || user.getProfilePictureUrl().isBlank())
                && profile.profilePictureUrl() != null) {
            user.setProfilePictureUrl(profile.profilePictureUrl());
        }

        return issueSession(user, UUID.randomUUID().toString());
    }

    private AccessInfo issueSessionForActiveUser(User user) {
        if (user.isDeactivated()) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Invalid Google credential"
            );
        }
        return issueSession(user, UUID.randomUUID().toString());
    }

    private void saveGoogleIdentity(User user, GoogleProfile profile, String normalizedEmail) {
        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.GOOGLE);
        identity.setProviderSubject(profile.subject());
        identity.setProviderEmail(normalizedEmail);
        userIdentityRepository.save(identity);
    }

    private String generateGoogleUsername(String email, String subject) {
        String localPart = email.substring(0, email.indexOf('@'))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (localPart.isBlank()) {
            localPart = "google_user";
        }

        String suffix = Integer.toUnsignedString(subject.hashCode(), 36);
        String candidate = localPart + "_" + suffix;
        int attempt = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = localPart + "_" + suffix + "_" + attempt++;
        }
        return candidate;
    }

    private String defaultDisplayName(GoogleProfile profile, String email) {
        if (profile.displayName() != null && !profile.displayName().isBlank()) {
            return profile.displayName().trim();
        }
        return email.substring(0, email.indexOf('@'));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
