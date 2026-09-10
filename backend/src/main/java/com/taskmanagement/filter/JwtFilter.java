package com.taskmanagement.filter;

import com.taskmanagement.service.auth.JwtService;
import com.taskmanagement.service.auth.AuthSessionService;
import com.taskmanagement.dto.auth.AccessTokenClaims;
import com.taskmanagement.exception.AuthenticationStoreUnavailableException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import io.jsonwebtoken.JwtException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailService;
    private final AuthSessionService authSessionService;
    private final com.taskmanagement.repository.UserRepository userRepository;

    JwtFilter(JwtService jwtService, UserDetailsService userDetailService, AuthSessionService authSessionService,
            com.taskmanagement.repository.UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userDetailService = userDetailService;
        this.authSessionService = authSessionService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractBearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        AccessTokenClaims claims;
        try {
            claims = jwtService.parseAccessToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid access tokens leave the context empty so Spring Security returns 401.
            filterChain.doFilter(request, response);
            return;
        }

        boolean sessionActive;
        try {
            sessionActive = authSessionService.exists(claims.sessionId());
        } catch (AuthenticationStoreUnavailableException ex) {
            // Session state cannot be verified, so authentication fails closed.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Service temporarily unavailable\"}");
            return;
        }

        if (!sessionActive) {
            // Revoked sessions leave the context empty so protected endpoints return 401.
            filterChain.doFilter(request, response);
            return;
        }

        var account = userRepository.findByUsernameAndIsDeactivatedFalse(claims.username());
        if (account.isEmpty() || !account.get().acceptsSession(claims.sessionId())) {
            filterChain.doFilter(request, response);
            return;
        }
        UserDetails userDetails = userDetailService.loadUserByUsername(claims.username());
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
}
