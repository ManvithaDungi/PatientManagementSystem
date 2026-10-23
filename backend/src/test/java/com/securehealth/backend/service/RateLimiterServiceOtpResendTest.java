package com.securehealth.backend.service;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceOtpResendTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks private RateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void firstResend_startsA15MinuteWindow() {
        when(valueOps.increment("otp:resend:a@x.test")).thenReturn(1L);

        assertDoesNotThrow(() -> rateLimiter.checkOtpResend("A@x.test ", "1.2.3.4", "UA"));

        verify(redisTemplate).expire("otp:resend:a@x.test", Duration.ofMinutes(15));
    }

    @Test
    void sixthResendWithinTheWindow_isBlockedAndAudited() {
        when(valueOps.increment("otp:resend:a@x.test")).thenReturn((long) RateLimiterService.MAX_OTP_RESENDS);
        assertDoesNotThrow(() -> rateLimiter.checkOtpResend("a@x.test", "1.2.3.4", "UA"));

        when(valueOps.increment("otp:resend:a@x.test")).thenReturn(RateLimiterService.MAX_OTP_RESENDS + 1L);
        assertThrows(RuntimeException.class, () -> rateLimiter.checkOtpResend("a@x.test", "1.2.3.4", "UA"));

        verify(auditLogRepository).save(any(AuditLog.class));
    }
}
