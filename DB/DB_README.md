# Database

The schema is owned by **Flyway**. The single source of truth is
`backend/src/main/resources/db/migration/` (SQL) and `backend/src/main/java/db/migration/` (Java migrations):

- `V1__baseline.sql` – generated from the JPA entities (PostgreSQL dialect).
- `V2__normalize_lab_status.sql` – one lab-order status vocabulary.
- `V3__widen_text_columns.sql` – audit details / diagnosis / remarks no longer overflow; last-login backfill.
- `V4__consent_notifications_vitals_otp.sql` – consent withdrawal reason, notifications table, richer vital signs, pending-2FA token.
- `V5__NormalizeEmails.java` – e-mail addresses become case-insensitive (lower-cased; duplicates by case are *reported*, never merged or deleted).
- `V6__strip_quoted_rejection_reasons.sql` – removes the JSON quotes older clients stored around appointment rejection reasons.

Rules:

1. Never edit a migration that has been applied anywhere; add a new one.
2. Changing an entity (column, table, index) requires a migration in the same
   change. `MigrationsMatchEntitiesTest` (H2) and `FlywayPostgresTest` (real PostgreSQL 16 via Testcontainers)
   fail the build if they disagree.
3. Hibernate runs with `ddl-auto=validate`; it never alters the schema.

Rebuilding from scratch (development): remove the database volume and start the
backend; Flyway recreates everything.

## Demo data (development only)

- `seed_users.sql` – psql script with `@example.test` accounts, a care team and a few clinical rows.
  It contains **no password**: pass an Argon2 hash yourself (`psql -v pw_hash='...' -f db/seed_users.sql`).
- Alternatively start the backend with `SPRING_PROFILES_ACTIVE=dev` and `APP_DEV_SEED_ENABLED=true`; `DevDataSeeder`
  creates the same kind of accounts using `APP_DEV_SEED_PASSWORD`, or a random password that is logged once.

Never run either against a production database.

The previous hand-written `schema.sql` was removed because it had drifted from the entities.
