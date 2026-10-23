package com.securehealth.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drift guard between the Flyway migrations and the JPA entities.
 * <p>
 * Applies every script in {@code db/migration} to a fresh database and then
 * boots Hibernate with {@code ddl-auto=validate}. If someone changes an entity
 * (new column, renamed field, new table) without adding a migration, the
 * context fails to start and this test goes red.
 * </p>
 * H2 in PostgreSQL mode stands in for PostgreSQL so this runs everywhere;
 * it exercises the same SQL files that production applies.
 */
@SpringBootTest(classes = {
        com.securehealth.backend.SecureHealthApplication.class,
        TestConfig.class
})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:migrationcheck;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0cy1vbmx5LTEyMzQ1Njc4OTA="
})
class MigrationsMatchEntitiesTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsApplyAndMatchTheEntityModel() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"success\" = true", Integer.class);
        assertTrue(applied != null && applied >= 1, "at least the baseline migration must have run");
    }

    @Test
    void doubleBookingAndDuplicateConsentGuardsAreUniqueColumns() {
        // Both guards must be enforced by the schema, not only by service code.
        Integer unique = jdbc.queryForObject(
                "select count(*) from information_schema.table_constraints tc "
                        + "join information_schema.key_column_usage k "
                        + "  on tc.constraint_name = k.constraint_name and tc.table_name = k.table_name "
                        + "where tc.constraint_type = 'UNIQUE' "
                        + "and lower(k.column_name) in ('active_slot_key','active_key')", Integer.class);
        assertEquals(2, unique, "active_slot_key and active_key must each have a unique index");
    }
}
