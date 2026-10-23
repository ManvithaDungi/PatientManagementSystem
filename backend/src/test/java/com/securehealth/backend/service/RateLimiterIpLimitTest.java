package com.securehealth.backend.service;

import com.securehealth.backend.exception.TooManyRequestsException;
import com.securehealth.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CALYX-026: unauthenticated endpoints are limited per client IP. */
class RateLimiterIpLimitTest {

    private RateLimiterService limiter;
    private StringRedisTemplate redis;
    private final AtomicLong counter = new AtomicLong();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        limiter = new RateLimiterService();
        redis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(i -> counter.incrementAndGet());
        ReflectionTestUtils.setField(limiter, "redisTemplate", redis);
        ReflectionTestUtils.setField(limiter, "auditLogRepository", Mockito.mock(AuditLogRepository.class));
    }

    @Test
    void callsUpToTheLimitPass_theNextOneIsRejected() {
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> limiter.checkIpLimit("login", "10.0.0.1", 3, Duration.ofMinutes(10)));
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.checkIpLimit("login", "10.0.0.1", 3, Duration.ofMinutes(10)));
    }

    @Test
    void theWindowIsSetOnTheFirstCallOnly() {
        limiter.checkIpLimit("register", "10.0.0.2", 5, Duration.ofHours(1));
        limiter.checkIpLimit("register", "10.0.0.2", 5, Duration.ofHours(1));
        verify(redis, Mockito.times(1)).expire("ratelimit:register:10.0.0.2", Duration.ofHours(1));
    }
}
