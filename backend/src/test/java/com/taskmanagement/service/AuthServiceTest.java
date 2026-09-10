package com.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.taskmanagement.dto.auth.AccessInfo;
import com.taskmanagement.dto.auth.GoogleLinkRequest;
import com.taskmanagement.dto.auth.GoogleLoginRequest;
import com.taskmanagement.dto.auth.GoogleProfile;
import com.taskmanagement.dto.auth.LoginRequest;
import com.taskmanagement.dto.auth.RefreshTokenClaims;
import com.taskmanagement.dto.auth.RegisterRequest;
import com.taskmanagement.dto.user.UserResponse;
import com.taskmanagement.exception.AuthenticationStoreUnavailableException;
import com.taskmanagement.exception.ConflictException;
import com.taskmanagement.exception.DuplicatedResourceException;
import com.taskmanagement.exception.InvalidRefreshTokenException;
import com.taskmanagement.mapper.UserMapper;
import com.taskmanagement.model.AuthProvider;
import com.taskmanagement.model.User;
import com.taskmanagement.model.UserIdentity;
import com.taskmanagement.model.UserRole;
import com.taskmanagement.repository.UserIdentityRepository;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.service.auth.AuthService;
import com.taskmanagement.service.auth.AuthSessionService;
import com.taskmanagement.service.auth.GoogleIdTokenService;
import com.taskmanagement.service.auth.JwtService;

import io.jsonwebtoken.MalformedJwtException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authManager;
    @Mock private UserMapper userMapper;
    @Mock private AuthSessionService authSessionService;
    @Mock private GoogleIdTokenService googleIdTokenService;
    @Mock private UserIdentityRepository userIdentityRepository;

    @InjectMocks
    private AuthService authService;

    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setSecurityStamp(null); // Existing accounts retain their legacy sessions until a security change.
        user.setId(7L);
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setEmail("alice@example.com");
        user.setPassword("encoded-password");
        user.setRole(UserRole.USER);

        userResponse = new UserResponse(
                7L,
                "alice",
                "Alice",
                "alice@example.com",
                null,
                UserRole.USER,
                false,
                null,
                null
        );
    }

    @Test
    void loginAuthenticatesAndCreatesRefreshSession() {
        LoginRequest request = new LoginRequest("alice", "Password123!");
        when(userRepository.findByUsernameAndIsDeactivatedFalse("alice"))
                .thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq("alice"), anyString()))
                .thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq("alice"), anyString(), anyString()))
                .thenReturn("refresh-token");
        when(userMapper.toUserResponse(user)).thenReturn(userResponse);

        AccessInfo result = authService.login(request);

        assertThat(result).isEqualTo(new AccessInfo("access-token", "refresh-token", userResponse));
        verify(authManager).authenticate(new UsernamePasswordAuthenticationToken(
                "alice",
                "Password123!"
        ));

        ArgumentCaptor<String> accessSessionId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refreshSessionId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenId = ArgumentCaptor.forClass(String.class);
        verify(jwtService).generateAccessToken(eq("alice"), accessSessionId.capture());
        verify(jwtService).generateRefreshToken(
                eq("alice"),
                refreshSessionId.capture(),
                tokenId.capture()
        );

        assertThat(accessSessionId.getValue()).isEqualTo(refreshSessionId.getValue());
        verify(authSessionService).create(accessSessionId.getValue(), tokenId.getValue());
    }

    @Test
    void googleLoginUsesLinkedIdentityAndIssuesApplicationTokens() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-credential");
        GoogleProfile profile = googleProfile();
        UserIdentity identity = new UserIdentity();
        identity.setUser(user);

        when(googleIdTokenService.verify("google-credential")).thenReturn(profile);
        when(userIdentityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.of(identity));
        stubIssuedSession();

        AccessInfo result = authService.loginWithGoogle(request);

        assertThat(result).isEqualTo(new AccessInfo("access-token", "refresh-token", userResponse));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void googleLoginRequiresPasswordConfirmationForExistingEmail() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-credential");
        GoogleProfile profile = googleProfile();
        when(googleIdTokenService.verify("google-credential")).thenReturn(profile);
        when(userIdentityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.loginWithGoogle(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Confirm your current password to link this Google account");

        verify(userIdentityRepository, never()).save(any(UserIdentity.class));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void googleLoginCreatesNewUserAndIdentityBeforeIssuingApplicationTokens() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-credential");
        GoogleProfile profile = googleProfile();
        when(googleIdTokenService.verify("google-credential")).thenReturn(profile);
        when(userIdentityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("alice@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-random-password");
        when(userMapper.toUserResponse(any(User.class))).thenReturn(userResponse);
        when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(anyString(), anyString(), anyString()))
                .thenReturn("refresh-token");

        AccessInfo result = authService.loginWithGoogle(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(userCaptor.getValue().getUsername()).startsWith("alice_");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("unusable-random-password");

        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getUser()).isSameAs(userCaptor.getValue());
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(identityCaptor.getValue().getProviderSubject()).isEqualTo("google-subject");
    }

    @Test
    void confirmGoogleLinkAuthenticatesOldPasswordBeforeSavingIdentity() {
        GoogleLinkRequest request = new GoogleLinkRequest("google-credential", "Password123!");
        GoogleProfile profile = googleProfile();
        when(googleIdTokenService.verify("google-credential")).thenReturn(profile);
        when(userIdentityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(user));
        when(userIdentityRepository.findByUserIdAndProvider(7L, AuthProvider.GOOGLE))
                .thenReturn(Optional.empty());
        stubIssuedSession();

        AccessInfo result = authService.confirmGoogleLink(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(authManager).authenticate(new UsernamePasswordAuthenticationToken(
                "alice",
                "Password123!"
        ));
        verify(userIdentityRepository).save(any(UserIdentity.class));
    }

    @Test
    void confirmGoogleLinkRejectsASecondGoogleIdentityForTheSameUser() {
        GoogleLinkRequest request = new GoogleLinkRequest("second-google-credential", "Password123!");
        GoogleProfile profile = new GoogleProfile(
                "second-google-subject",
                "alice@example.com",
                "Alice",
                null
        );
        UserIdentity existingIdentity = new UserIdentity();
        existingIdentity.setProviderSubject("first-google-subject");

        when(googleIdTokenService.verify("second-google-credential")).thenReturn(profile);
        when(userIdentityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "second-google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(user));
        when(userIdentityRepository.findByUserIdAndProvider(7L, AuthProvider.GOOGLE))
                .thenReturn(Optional.of(existingIdentity));

        assertThatThrownBy(() -> authService.confirmGoogleLink(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This account is already linked to another Google account");

        verify(userIdentityRepository, never()).save(any(UserIdentity.class));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void registerRejectsDuplicateUsernameBeforeWritingUserOrSession() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicatedResourceException.class)
                .hasMessage("Username is existed");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder, jwtService, userMapper, authSessionService);
    }

    @Test
    void registerRejectsDuplicateEmailBeforeWritingUserOrSession() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicatedResourceException.class)
                .hasMessage("Email is existed");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder, jwtService, userMapper, authSessionService);
    }

    @Test
    void refreshRotatesTokenIdWhileKeepingSessionId() {
        RefreshTokenClaims claims = new RefreshTokenClaims("alice", "session-1", "token-1");
        when(jwtService.parseRefreshToken("old-refresh-token")).thenReturn(claims);
        when(userRepository.findByUsernameAndIsDeactivatedFalse("alice"))
                .thenReturn(Optional.of(user));
        when(authSessionService.rotate(eq("session-1"), eq("token-1"), anyString()))
                .thenReturn(true);
        when(jwtService.generateAccessToken("alice", "session-1"))
                .thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(eq("alice"), eq("session-1"), anyString()))
                .thenReturn("new-refresh-token");
        when(userMapper.toUserResponse(user)).thenReturn(userResponse);

        AccessInfo result = authService.refresh("old-refresh-token");

        assertThat(result).isEqualTo(new AccessInfo(
                "new-access-token",
                "new-refresh-token",
                userResponse
        ));
        ArgumentCaptor<String> newTokenId = ArgumentCaptor.forClass(String.class);
        verify(authSessionService).rotate(eq("session-1"), eq("token-1"), newTokenId.capture());
        verify(jwtService).generateRefreshToken("alice", "session-1", newTokenId.getValue());
    }

    @Test
    void refreshRejectsReusedTokenWhenAtomicRotationLoses() {
        RefreshTokenClaims claims = new RefreshTokenClaims("alice", "session-1", "stale-token");
        when(jwtService.parseRefreshToken("reused-refresh-token")).thenReturn(claims);
        when(userRepository.findByUsernameAndIsDeactivatedFalse("alice"))
                .thenReturn(Optional.of(user));
        when(authSessionService.rotate(eq("session-1"), eq("stale-token"), anyString()))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("reused-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(jwtService, never()).generateAccessToken(anyString(), anyString());
        verify(jwtService, never()).generateRefreshToken(anyString(), anyString(), anyString());
        verifyNoInteractions(userMapper);
    }

    @Test
    void refreshRejectsDeactivatedOrMissingUserBeforeRotatingSession() {
        RefreshTokenClaims claims = new RefreshTokenClaims("alice", "session-1", "token-1");
        when(jwtService.parseRefreshToken("refresh-token")).thenReturn(claims);
        when(userRepository.findByUsernameAndIsDeactivatedFalse("alice"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(authSessionService);
    }

    @Test
    void refreshPreservesStoreUnavailableErrorForHttp503Mapping() {
        RefreshTokenClaims claims = new RefreshTokenClaims("alice", "session-1", "token-1");
        AuthenticationStoreUnavailableException unavailable =
                new AuthenticationStoreUnavailableException("Redis unavailable", null);
        when(jwtService.parseRefreshToken("refresh-token")).thenReturn(claims);
        when(userRepository.findByUsernameAndIsDeactivatedFalse("alice"))
                .thenReturn(Optional.of(user));
        when(authSessionService.rotate(eq("session-1"), eq("token-1"), anyString()))
                .thenThrow(unavailable);

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isSameAs(unavailable);
    }

    @Test
    void logoutRevokesSessionFromValidRefreshToken() {
        when(jwtService.parseRefreshToken("refresh-token"))
                .thenReturn(new RefreshTokenClaims("alice", "session-1", "token-1"));

        authService.logout("refresh-token");

        verify(authSessionService).revoke("session-1");
    }

    @Test
    void logoutIgnoresMalformedTokenBecauseControllerStillClearsCookie() {
        when(jwtService.parseRefreshToken("malformed"))
                .thenThrow(new MalformedJwtException("invalid"));

        authService.logout("malformed");

        verifyNoInteractions(authSessionService);
    }

    private RegisterRequest registerRequest() {
        return new RegisterRequest("Alice", "alice", "Password123!", "alice@example.com");
    }

    @Test
    void refreshRejectsSessionFromBeforePasswordChange() {
        user.setSecurityStamp("new-stamp");
        when(jwtService.parseRefreshToken("old-token"))
                .thenReturn(new RefreshTokenClaims("alice", "old-session", "token-1"));
        when(userRepository.findByUsernameAndIsDeactivatedFalse("alice")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> authService.refresh("old-token")).isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(authSessionService);
    }

    private GoogleProfile googleProfile() {
        return new GoogleProfile(
                "google-subject",
                "Alice@Example.com",
                "Alice Example",
                "https://example.com/alice.png"
        );
    }

    private void stubIssuedSession() {
        when(jwtService.generateAccessToken(eq("alice"), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq("alice"), anyString(), anyString()))
                .thenReturn("refresh-token");
        when(userMapper.toUserResponse(user)).thenReturn(userResponse);
    }
}
