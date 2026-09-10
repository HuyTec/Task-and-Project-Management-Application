package com.taskmanagement.service;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.server.ResponseStatusException;
import com.taskmanagement.service.user.SecurityChangeLimiter;
import com.taskmanagement.exception.AuthenticationStoreUnavailableException;

class SecurityChangeLimiterTest {
    @Test void fifthAttemptAllowedSixthReturns429() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList())).thenReturn(5L, 6L);
        var limiter = new SecurityChangeLimiter(redis);
        limiter.check(1L);
        assertThatThrownBy(() -> limiter.check(1L)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(429));
    }
    @Test void unavailableRedisFailsClosed() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList()))
                .thenThrow(new RedisConnectionFailureException("unavailable"));
        assertThatThrownBy(() -> new SecurityChangeLimiter(redis).check(1L))
                .isInstanceOf(AuthenticationStoreUnavailableException.class);
    }
    @Test void missingResultFailsClosed() {
        var redis = mock(StringRedisTemplate.class);
        assertThatThrownBy(() -> new SecurityChangeLimiter(redis).check(1L))
                .isInstanceOf(AuthenticationStoreUnavailableException.class);
    }
}
