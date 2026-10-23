package com.securehealth.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs every Flyway migration (V1, V2, V3, ...) against a real, empty PostgreSQL 16 database and then
 * boots Hibernate with {@code ddl-auto=validate}. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {com.securehealth.backend.SecureHealthApplication.class, TestConfig.class})
class FlywayPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsApplyToEmptyPostgres_andHibernateValidates() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        int sql = new java.io.File("src/main/resources/db/migration").listFiles(
                (d, n) -> n.matches("V\\d+__.*\\.sql")).length;
        int java = new java.io.File("src/main/java/db/migration").listFiles(
                (d, n) -> n.matches("V\\d+__.*\\.java")).length;
        Integer expected = sql + java;
        assertNotNull(applied);
        assertEquals(expected, applied, "every migration script must have been applied");
        assertTrue(applied >= 5, "V1 to V5 must exist");
    }

    @Test
    void v3WidenedTheOverflowingColumns() {
        assertEquals("text", jdbc.queryForObject(
                "select data_type from information_schema.columns where table_name='audit_logs' and column_name='details'",
                String.class));
        assertEquals(2000, jdbc.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_name='medical_records' and column_name='diagnosis'", Integer.class));
        assertEquals(2000, jdbc.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_name='lab_tests' and column_name='remarks'", Integer.class));
    }

    @Test
    void emailsAreCaseInsensitivelyUniqueOnPostgres() {
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from pg_indexes where indexname = 'ux_login_email_lower'", Integer.class));
    }

    @Test
    void uniqueGuardsAreEnforcedByPostgres() {
        Integer unique = jdbc.queryForObject(
                "select count(*) from pg_indexes where indexdef ilike '%unique%' "
                        + "and (indexdef ilike '%active_slot_key%' or indexdef ilike '%active_key%')", Integer.class);
        assertEquals(2, unique);
    }
}
