package com.taskmanagement.service.user;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.taskmanagement.exception.AuthenticationStoreUnavailableException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SecurityChangeLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> ATTEMPT = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], 900) end
            return n
            """, Long.class);

    public void check(Long userId) {
        try {
            Long count = redis.execute(ATTEMPT, List.of("auth:security-change:" + userId));
            if (count == null) throw new AuthenticationStoreUnavailableException("Security check unavailable", null);
            if (count > 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many security change attempts. Try again in 15 minutes.");
        } catch (DataAccessException ex) {
            throw new AuthenticationStoreUnavailableException("Security check unavailable", ex);
        }
    }
}
