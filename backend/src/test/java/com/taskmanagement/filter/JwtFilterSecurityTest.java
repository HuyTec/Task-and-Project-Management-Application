package com.taskmanagement.filter;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.service.auth.JwtService;
import com.taskmanagement.service.auth.AuthSessionService;
import com.taskmanagement.dto.auth.AccessTokenClaims;
import jakarta.servlet.FilterChain;

class JwtFilterSecurityTest {
    @Test void oldAccessTokenCannotAuthenticateEvenWhenRedisSessionStillExists() throws Exception {
        SecurityContextHolder.clearContext();
        var jwt = mock(JwtService.class);
        var details = mock(UserDetailsService.class);
        var sessions = mock(AuthSessionService.class);
        var users = mock(UserRepository.class);
        User user = new User(); user.setSecurityStamp("new-stamp");
        when(jwt.parseAccessToken("old-access")).thenReturn(new AccessTokenClaims("alice", "old-session"));
        when(sessions.exists("old-session")).thenReturn(true);
        when(users.findByUsernameAndIsDeactivatedFalse("alice")).thenReturn(Optional.of(user));
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer old-access");
        var chain = mock(FilterChain.class);
        new JwtFilter(jwt, details, sessions, users).doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(details);
    }
}
