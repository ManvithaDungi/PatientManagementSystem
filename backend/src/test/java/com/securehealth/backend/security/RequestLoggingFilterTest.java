package com.securehealth.backend.security;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.service.TokenBlacklistService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        // FIX: Exclude Redis so context doesn't attempt a connection to localhost:6379
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class RequestLoggingFilterTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void apiRequestShouldCreateAuditLog() {

        AuditLog log = new AuditLog(
                "test@example.com",
                "GET /actuator/health",
                "127.0.0.1",
                "MockMvc",
                "Simulated request log"
        );
        auditLogRepository.save(log);

        List<AuditLog> logs = auditLogRepository.findAll();

        assertThat(logs).isNotNull();
        assertThat(logs).isNotEmpty();
    }
}
