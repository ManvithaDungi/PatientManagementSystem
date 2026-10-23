package com.securehealth.backend.service;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
class AuditLogServiceTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void auditLogRepositoryShouldSaveLog() {

        AuditLog log = new AuditLog(
                "test@example.com",
                "TEST_ACTION",
                "127.0.0.1",
                "JUnit",
                "Test log entry"
        );

        auditLogRepository.save(log);

        assertThat(auditLogRepository.count()).isGreaterThan(0);
    }
}
