package com.taskmanagement.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.OptimisticLockingFailureException;
import com.taskmanagement.model.User;
import com.taskmanagement.model.UserRole;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.service.user.AccountSecurityService;
import com.taskmanagement.service.user.SecurityChangeLimiter;
import com.taskmanagement.service.auth.GoogleIdTokenService;
import com.taskmanagement.dto.user.AccountSecurityRequest;
import com.taskmanagement.exception.BadRequestException;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(AccountSecurityService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountSecurityPersistenceTest {
    @Autowired UserRepository users;
    @Autowired AccountSecurityService service;
    @MockitoBean PasswordEncoder passwords;
    @MockitoBean GoogleIdTokenService google;
    @MockitoBean SecurityChangeLimiter limiter;

    private User fixture() {
        var user = new User();
        user.setUsername("user_" + UUID.randomUUID());
        user.setDisplayName("Profile test"); user.setEmail(user.getUsername() + "@example.com");
        user.setPassword("oldHash"); user.setRole(UserRole.USER);
        return users.saveAndFlush(user);
    }

    @Test void detachedProfileCannotRestoreOldPasswordAndStamp() {
        var original = fixture();
        var staleProfile = users.findById(original.getId()).orElseThrow();
        when(passwords.matches("current", "oldHash")).thenReturn(true);
        when(passwords.encode("a long new password")).thenReturn("newHash");
        service.change(original.getUsername(), new AccountSecurityRequest("current", null, null,
                "a long new password", "a long new password"));
        staleProfile.setDisplayName("Stale update");
        assertThatThrownBy(() -> users.saveAndFlush(staleProfile)).isInstanceOf(OptimisticLockingFailureException.class);
        var latest = users.findById(original.getId()).orElseThrow();
        assertThat(latest.getPassword()).isEqualTo("newHash");
        assertThat(latest.getSecurityStamp()).isNotEqualTo(original.getSecurityStamp());
    }

    @Test void invalidPasswordRollsBackUsernameChangeAndStamp() {
        var original = fixture();
        when(passwords.matches("current", "oldHash")).thenReturn(true);
        assertThatThrownBy(() -> service.change(original.getUsername(), new AccountSecurityRequest("current", null,
                "renamed_user", "a long new password", "does not match"))).isInstanceOf(BadRequestException.class);
        var latest = users.findById(original.getId()).orElseThrow();
        assertThat(latest.getUsername()).isEqualTo(original.getUsername());
        assertThat(latest.getPassword()).isEqualTo("oldHash");
        assertThat(latest.getSecurityStamp()).isEqualTo(original.getSecurityStamp());
    }
}
