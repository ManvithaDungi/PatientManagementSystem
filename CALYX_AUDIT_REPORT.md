# Calyx — Repository Audit Report

Audit date: 2026-10-18 · Scope: `PatientManagementSystem/` (Spring Boot 3.2.2 / Java 21 backend, React 19 / CRA frontend, PostgreSQL 16, Redis, Docker Compose).
Method: static review of backend and frontend source, plus real execution of the existing test suites, a frontend production build, Docker Compose validation, and read-only runtime probe tests (run against a scratch copy of the backend, not the repo). No source file in the repository was modified; no database, volume or container was touched. No secrets are reproduced here.

---

## 1. Executive summary

**Overall health.** The application is far more integrated than its predecessor audits suggest: every production page calls real endpoints, there is no mock fallback on the clinical pages, and the backend has Flyway migrations, Argon2, refresh-token rotation, DB-level uniqueness for slots and consents, and an invite-based staff onboarding flow. **It is nevertheless not safe to deploy**, and it cannot be started from the current working tree.

**Verified results**

| Check | Result |
|---|---|
| `mvn test` (Docker `maven:3.9.6-eclipse-temurin-21`) | **PASS** — 213 tests, 0 failures. Uses H2, Redis mocked; Flyway SQL not exercised on PostgreSQL. |
| Frontend `react-scripts test` | **PASS** — 65 suites, 354 tests |
| Frontend `react-scripts build` | **PASS** with 4 ESLint warnings; 302 kB gzip main bundle |
| `docker compose config -q` | **PASS** (syntax only) |
| `docker build -f backend/Dockerfile backend` | **FAIL** — "the Dockerfile cannot be empty" |
| Full stack `docker compose up` | **Not run** (backend image cannot build; real PostgreSQL/Redis runtime was not verified) |
| Probe tests (scratch copy, synthetic data) | 10 probes; every one reproduced its suspected defect (see §9) |

**Findings: 40** — Critical 4 · High 12 · Medium 16 · Low 7 · Informational 1. 39 confirmed by code reading and/or an executed probe (10 of them reproduced by probe); CALYX-007's live data divergence and CALYX-040 are the only items needing runtime verification.

**Most serious risks**
1. Credential hashes and OTPs leak in API responses (nurse endpoints, `POST /api/vital-signs`) — executed and confirmed.
2. Consent is not enforced consistently: expired consents never expire, any doctor can list every patient's address and medical history, any provider reads any patient's appointments including doctor notes, any nurse can read/record medication administration for any patient.
3. The patient booking screen uses the wrong ID space for doctors (`doctor_profiles.profile_id` instead of `login.user_id`), so bookings and slot lookups target the wrong user.
4. The lab workflow breaks at the first step because the status vocabulary differs between backend (`PENDING`) and UI (`Pending`).
5. The backend Docker image cannot be built (empty `backend/Dockerfile` in the working tree), and the working tree is mid-restructure with CI/deploy pipelines pointing at paths that no longer exist.

**Remediation update (2026-10-19).** Of the 40 findings, 37 are fixed, 3 are partly fixed and none is untouched: fixed — CALYX-001 … 003, 005 … 037, 039; partly fixed — CALYX-004 (restructure still uncommitted), 038 (tracked artefacts and stale reports listed with proposed commands, not removed), 040 (CRA-toolchain advisories need a major migration) — see §14. Latest results: backend 283 tests, 0 failures, 0 skipped (including migrations V1–V6 on PostgreSQL 16), frontend 72 suites / 388 tests, all passing; `react-scripts build` with `CI=true` (warnings are errors) compiles cleanly.

**End-to-end verdict.** Individual pages are wired to real APIs, but the care-team relationships (nurse ↔ patient, doctor ↔ patient) cannot be created through the application, and several workflows (lab collection, prescription deactivation/refill, medical-record attachments, notifications) are broken or unimplemented. It is **not** end-to-end functional for all roles.

**End-to-end verdict — update 2026-10-19.** The blockers named above now have working, tested code paths (admin care-team screen, one lab status vocabulary with enforced transitions, prescription status and refills, medical-record attachments, in-app notifications), the stack starts from a clean checkout with `docker compose up` and migrates an empty PostgreSQL database, and an API-level patient flow was exercised through the nginx proxy. The application is therefore **verified at unit, integration and API level, but not in a browser**, and the staff two-factor/SMTP path was not exercised; treat it as functionally complete but not yet end-to-end certified.

---

## 2. Repository architecture

```
PatientManagementSystem/
├─ backend/   Spring Boot (com.securehealth.backend): controller / service / repository / model / dto / security / config
│   └─ src/main/resources/db/migration/V1__baseline.sql   (Flyway; ddl-auto=validate)
├─ frontend/  Dockerfile + nginx.conf; app/ = CRA React app (react-router 6, Tailwind)
├─ db/        seed_users.sql, DB_README.md (stale)
├─ docker-compose.yml  postgres_db, redis_cache, backend_api, frontend(nginx:3000→80), prometheus, grafana, adminer
└─ .github/workflows  ci.yml, deployment.yml
```

Flow: Browser → nginx (`/api/` proxied to `backend_api:8080`, same origin) → Spring Security (JWT filter) → controller → service → PostgreSQL. Redis is used **only** for auth support: JWT blacklist (`jwt:blacklist:<token>`), 30-minute idle marker (`session:active:<email>`), login/OTP rate limiting (`auth:*`, `otp:*`). No clinical data is cached; PostgreSQL is the sole source of truth, which is appropriate.

Inconsistencies: product is called Calyx in the UI and SecureHealth in e-mails/package names; legacy `frontend/app/src/services/supabaseAuth.js` is actually the backend auth adapter; ~20 stale report `.md` files, committed logs (`api_logs.txt`, `logs.txt`, test output files), and a duplicated root `package.json`/`src/__tests__`.

---

## 3. Frontend audit

**Data sources.** All production pages fetch from `services/api.js`. `src/mocks/*` is imported only by tests, plus two constant imports (`mocks/auth.js` constants for the 2FA timer, `mocks/consentForm.js` option text). Mock credentials from `mocks/auth.js` are tree-shaken out of the bundle (verified by grepping `build/`). Fake data **is** present in the bundle in two places (F-01, F-02).

**Page-by-page (Matrix A, condensed)**

| Page / feature | Role | Source / endpoint | Status |
|---|---|---|---|
| Login, 2FA, forgot/reset password, accept invite | all | `/auth/*` | Connected; error-message mismatch on reset (F-14) |
| Registration | patient | `POST /auth/register` | Connected; sets a token-less ghost session (F-15) |
| Patient Dashboard, Medical History, Medications, Prescriptions, Lab Results | patient | `/patients/me`, `/medical-records/patient/{id}`, `/prescriptions/…`, `/lab-results/…` | Connected, not runtime-verified |
| Patient Appointments | patient | `/doctors`, `/appointments/*` | **Payload/ID mismatch** (F-03) |
| Patient Profile | patient | `/patients/me`, `/doctors/{id}` | **Wrong doctor shown** (F-03) |
| Consent Management / Grant-Modify | patient | `/consent` | Connected, but fake actions and static log (F-01, F-12) |
| Doctor Dashboard / Patients / Appointments | doctor | `/doctors/{userId}/patients`, `/appointments/doctor/{userId}` | Connected (uses userId correctly) |
| Doctor Patient Detail | doctor | `/patients/{id}` + 4 consent-gated calls | Misleading empty states (F-10) |
| Doctor Prescriptions | doctor | `/prescriptions` | Deactivate toggle not persisted (F-11) |
| Doctor Lab Results | doctor | `/lab-results` | Connected; "Abnormal" filter can never match |
| Doctor Notifications / Header bell | all | none | **Not implemented** — hard-coded "No new notifications" (F-16) |
| Nurse Dashboard / Tasks / Handover | nurse | `/nurse/*` | Connected; leaks credential fields (S-02) |
| Nurse Patients / Detail / Vitals / Medication | nurse | `/nurse/assigned-patients`, `/vital-signs`, `/nurse/medications` | **Always empty** — nothing can assign a nurse (W-01); hard-coded `status:'stable'`, `diagnosis:'Observation'` fallback; pain/notes not persisted (F-13) |
| Lab Orders / Order detail / Upload / History | lab | `/lab-technician/*`, `/files` | **Broken status vocabulary** (W-02) |
| Admin Overview / Compliance / Health / Audit logs / Approvals / Invites | admin | `/admin/*`, `/appointments/pending` | Connected |
| Admin role change, staff removal, patient directory | admin | endpoints exist | **No UI caller** (E-matrix) |
| Session-timeout warning, `PasswordStrengthMeter`, `RestrictedSection`, `SecureField` | — | — | Dead components (never imported) |

**Frontend findings** — see register (F-xx). Additional notes: `api.js` calls `response.json()` on every success, but several backend endpoints return plain text (logout, delete endpoints, `deleteStaff`); those wrappers would throw after a successful server action (currently unused except logout, which is guarded). The 401 handler redirects with `window.location.href` and clears storage but does not clear in-flight state; acceptable.

---

## 4. Backend audit

Inventory (Matrix B, condensed): 16 controllers, ~95 endpoints. Authorization is a mix of `@PreAuthorize`, `/api/admin/**` matcher, and `PatientAccessValidator`. Consent is checked only on `MEDICAL_RECORDS`, `PRESCRIPTIONS`, `LAB_RESULTS`, `VITAL_SIGNS` reads, via `PatientAccessValidator`; **appointments, patient directory, lab queue, nurse endpoints, and all writes bypass it.**

| Area | Observation |
|---|---|
| Error handling | `GlobalExceptionHandler.handleRuntime` maps messages containing "not found"/"403"/"409" to status codes by string matching and returns **every** `RuntimeException` message to the client with HTTP 400 — including Hibernate/SQL messages (verified: probe returned `could not execute statement [Value too long for column "DETAILS"…]`). Controllers also hand-roll try/catch. |
| Validation | Several request bodies are raw `Map<String,Object>` (consent, nurse tasks, handover, lab upload) or lack `@Valid` (`PUT` prescription/record/doctor profile). DB columns are `varchar(255)` for `lab_tests.remarks`, `medical_records.diagnosis`, `audit_logs.details`; no `@Size`. |
| Transactions | `AuthService.refreshToken` is `@Transactional`; theft-detection path revokes sessions then throws → rollback (S-07). |
| Persistence gaps | `LabTestRequest.resultValue/unit/referenceRange` silently dropped on create; `unit`/`referenceRange` can never be set anywhere; `MedicalRecord.attachmentUrl` has no write path; `Prescription.refillsRemaining` always 0 with no setter path, `status` has no transition endpoint; appointment `NO_SHOW` unreachable. |
| Dead/orphaned code | `ArchivalService.restoreUser/getArchivedUsers` (no controller); `archived` flag never checked at login; `lastLoginAt` never written; `CatalogController` static lists are not used by the UI except departments; duplicate `micrometer-registry-prometheus` dependency; obsolete `management.metrics.export.prometheus.enabled` property. |
| Performance | `RequestLoggingFilter` writes one `audit_logs` row for **every** authenticated 200 response (unbounded growth, DB write on every GET). `getPatientsByDoctor` + doctor prescriptions page issue N+1 requests (one per patient). `findAll()` used for lab queue, appointments, doctors without pagination. |

---

## 5. Workflow audit (Matrix C)

| Workflow | Result |
|---|---|
| Register → login (patient) | Works. Placeholder DOB = registration date if absent (data quality, F-17). |
| Staff onboarding via invite → login with 2FA (doctor/admin) | Works by code; OTP generated with `java.util.Random`, stored in plaintext (S-08). |
| Patient books appointment | **Broken/incorrect** — wrong doctor ID space (F-03). Server accepts any login as "doctor", past dates, off-shift times (W-05). |
| Admin approves/rejects | Works; reject reason stored with JSON quotes (F-18). |
| Doctor completes/cancels/updates appointment | No state-machine guard: cancel after complete, complete after cancel/reject, doctor edit can move date onto an occupied slot → unique-key violation surfaces as masked 400 (W-05). |
| Doctor creates record / prescription / lab order | Persists, but doctor needs no consent to write and **does** need consent to read it back; no assignment mechanism exists (W-01). |
| Lab: order → collect → result → publish | **Breaks at "collect"** (W-02); upload sets `Completed`; unit/range never stored. |
| Nurse: assigned patients, vitals, medication admin | Assigned list always empty in a fresh install (W-01); admin-less medication administration for any patient (S-05). |
| Consent grant → access → revoke | Grant/revoke work; revoke takes effect on the next request (verified in code). **Expiry is never enforced** (S-03). "Modify" re-grants and fails with "already exists". |
| Notifications / audit events | Notifications not implemented. Audit coverage is thin (S-10). |

---

## 6. PostgreSQL and Redis audit

**PostgreSQL.** Strengths: Flyway baseline generated from entities, `MigrationsMatchEntitiesTest`, partial-unique emulation (`active_slot_key`, `active_key`) preventing double booking and duplicate active consents. Gaps: no `ON DELETE` rules, so deleting a `login` with history fails (D-01); no CHECK/enum on `lab_tests.status` or `prescriptions.status`; no index on `patient_profiles.assigned_*`; `audit_logs` mutable by the app user; Flyway SQL was **not** run against real PostgreSQL in the test suite (H2 only; `flyway.enabled=false`) — runtime verification outstanding. `db/seed_users.sql` targets columns (`is_active`, `is_verified`) that do not exist in V1 and so fails on a clean database; it also embeds real-looking personal e-mail addresses and a shared password documented in a comment.

**Redis.** Used only for the three auth purposes above (key prefixes `jwt:blacklist:`, `session:active:`, `auth:fail/lock:`, `otp:*`). Issues: TTLs are correct; the raw JWT is embedded in the key (R-03); there is no fallback — if Redis is down every authenticated request throws (fail-closed outage) and login fails (R-01, design decision to document); idle timer is per e-mail not per session, so logout on one device ends idle tracking for all and `refresh-token` re-arms it, defeating the 30-minute idle limit (S-06); the Redis container has no password and publishes 6379 on all interfaces (I-02). No cache invalidation concerns exist because nothing is cached.

---

## 7. Security audit

Positive: Argon2id encoder (m=4096, t=3, p=1 — on the low side vs OWASP), HS384 JWT with 15-minute lifetime, refresh token hashed (SHA-256) and in HttpOnly SameSite=Strict cookie, in-memory access token on the client, public registration forced to PATIENT, invite tokens hashed, startup secret validator, AES-GCM file encryption, no SQL concatenation found.

See findings S-01 … S-12 and I-xx in the register. Prioritised remediation is in §11.

---

## 8. Infrastructure and build audit

* `backend/Dockerfile` is a **0-byte file in the working tree** (HEAD has 26 lines, which also reference a now-missing `Backend/` path). `docker compose up --build` cannot succeed (verified).
* The working tree holds 157 deletions, 23 modifications and 19 untracked files — an uncommitted repository restructure (`backend/Backend/` → `backend/`, `DB/` → `db/`).
* `ci.yml` uses `working-directory: backend/Backend`, which no longer exists → CI would fail once committed. `deployment.yml` SSHes to a hard-coded IP on every push to `main` with `appleboy/ssh-action@master` (unpinned), is not gated on CI, and also runs `npm run build` + `pm2 serve` on port 3000, which collides with the Compose frontend (`3000:80`).
* Compose: `depends_on` has no health checks (backend can start before PostgreSQL is ready; `restart: always` masks this); Postgres 5432, Redis 6379, Adminer 8082, Prometheus 9090, Grafana 3001 (default credentials) are published on all interfaces; `postgres_data` volume declared but unused; `TZ=Asia/Kolkata` while the JVM stores `LocalDateTime` without zones.
* Backend image uses `jre-alpine` (HEAD) while `BackupService` shells out to `pg_dump` — it is not installed, so nightly backups fail (logged), and backup files would be written unencrypted into the container filesystem with no volume.
* `frontend/app/.env` is tracked by git despite `.gitignore` (dev URL only; no secrets found).
* Backend runtime: JDK 21 is not installed on the host (the `C:\Program Files\Java\latest` junction is dangling); builds only work through Docker.

---

## 9. Complete bug register (Matrix D)

Confidence: **C** Confirmed (executed or unambiguous code), **L** Likely, **R** Needs runtime verification. "Probe" = reproduced by the scratch tests in `scratchpad/probe`.

### Critical

**CALYX-001 · S-01 · Security · C (Probe)** — Credential hashes and OTPs returned in API responses.
> **Status: FIXED** — see §14 for how it was fixed.
Evidence: `NurseService.getAssignedPatients` returns `List<PatientProfile>` (entity); `NurseService.saveHandoverNote/getHandoverNotes` return `HandoverNote` entities whose `@JsonIgnoreProperties` lists `"password"` while the field is `passwordHash`; `VitalSignController.createVitalSign` returns the `VitalSign` entity. Probes: `GET /api/nurse/assigned-patients`, `GET /api/nurse/handover` (as another nurse) and `POST /api/vital-signs` all returned `passwordHash` and `otp` of the patient, assigned doctor and note author.
Impact: any nurse obtains Argon2 hashes and live OTPs of patients and doctors/admins; a doctor's/admin's OTP lets an attacker with the password complete 2FA.
Fix: return DTOs only; add `@JsonIgnore` on `Login.passwordHash/otp/otpExpiry/failedAttempts/lockoutUntil` as defence in depth; add a contract test that scans JSON of every controller for those keys.
Files: `NurseService`, `NurseController`, `VitalSignController/Service`, `model/Login`, `HandoverNote`, `NurseTask`. Effort: Small.

**CALYX-002 · S-03 · Security · C (Probe)** — Consent expiry never enforced.
> **Status: FIXED** — see §14 for how it was fixed.
Evidence: `ConsentService.hasConsent` checks only `status == ACTIVE`; nothing sets `EXPIRED`; the UI offers 6-month…5-year expirations. Probe: a doctor whose consent expired 30 days ago received 200 with prescriptions (control without consent: 403). Because `active_key` stays set, the patient also cannot re-grant after expiry.
Fix: add `expiresAt` check in `hasConsent` (query `… and (expiresAt is null or expiresAt > now)`), plus a scheduled job marking `EXPIRED` and clearing `active_key`. Tests: expired/not-yet-expired/revoked. Effort: Small.

**CALYX-003 · S-04 · Security · C (Probe)** — Consent bypass for patient directory and appointments.
> **Status: FIXED** — see §14 for how it was fixed.
Evidence: `GET /api/patients` (any DOCTOR) returns every patient's address and `medicalHistory`; `GET /api/appointments/patient/{id}`, `GET /api/appointments`, `GET /api/appointments/{id}` use `validateAccess(patientId, auth)` with no consent type, which returns immediately for DOCTOR/NURSE/LAB_TECHNICIAN; DTO includes `reasonForVisit`, `specialRequirements`, `doctorNotes`. Probe confirmed 200 for an unrelated doctor.
Fix: restrict directory to minimal identifiers (id, name) or require relationship; gate appointment clinical fields on a consent/assignment/own-appointment rule; filter `GET /api/appointments` by doctor. Effort: Medium.

**CALYX-004 · I-01 · Infrastructure · C** — Backend cannot be built/deployed: empty `backend/Dockerfile`; repo mid-restructure; CI/deploy reference removed paths (see §8).
> **Status: PARTIALLY FIXED (Dockerfile, CI, deploy workflow and the restructure check are done; the restructure itself is still uncommitted, commands in §14)** — see §14 for how it was fixed.
Fix: restore a Dockerfile for the new layout (`COPY pom.xml`, `COPY src`, add `postgresql-client` if backups stay), commit the restructure, update `ci.yml`/`deployment.yml`. Verify: `docker compose up --build` healthy. Effort: Small.

### High

**CALYX-005 · S-05 · Security · C (Probe)** — Nurse medication endpoints have no authorization beyond role: any nurse can `POST /api/nurse/medications/{rxId}/administer` and read `…/history` for any prescription (probe 200 both); no prescription-status check, no audit, no duplicate guard. Fix: require assignment/consent, ACTIVE status, audit event. Effort: Medium.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-006 · S-02 · Security · C** — Lab technicians read every patient's orders/results/names and overwrite results with no consent, no ownership and no audit (`LabTechnicianController`). `LabResultController GET /` and `/pending` return all patients' tests to every doctor. Fix: scope to orders routed to lab; consent for doctor reads; audit result writes; block edits after `Completed`. Effort: Medium.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-007 · F-03 · Frontend/API · C** — Doctor ID-space mismatch. `Patient/Appointments.jsx` sends `doctor.id` (= `doctor_profiles.profile_id`) as `doctorId`; backend `createAppointment` and `getAvailableSlots` use `login.user_id`. `Patient/Profile.jsx` calls `/doctors/{assignedDoctorId}` (a `user_id`) against a profile-id lookup. Result: slots fail with "Doctor not found" or, worse, the appointment is created against another login (the server never verifies the role is DOCTOR). Fix: use `doctor.userId` in the UI; validate `role == DOCTOR` in the service; add endpoint `/doctors/by-user/{id}`. Confidence: C for code, R for observed divergence on live data. Effort: Small.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-008 · W-02 · Workflow · C** — Lab status vocabulary mismatch: backend creates `PENDING`, seed uses `PENDING/COMPLETED`, lab UI compares `'Pending'/'Collected'/'Results Pending'/'Completed'`; `findByStatusOrderByOrderedAtAsc` is case-sensitive; `PUT …/status` accepts any string. Effects: orders created by doctors never show a "Mark collected" button or appear under the Pending tab; History (`getOrders('Completed')`) misses seeded rows; "Results Pending" is unreachable. Fix: single enum (`ORDERED, COLLECTED, RESULTS_PENDING, COMPLETED`) with validated transitions and migration. Effort: Medium.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-009 · W-01 · Workflow · C** — No code path assigns `assigned_nurse_id` (only reads) and `assigned_doctor_id` is set only by the practically unused `POST /api/patients`. Nurse patient lists, dashboard counts and vitals picker are empty on a clean install; the assigned-doctor consent shortcut never applies. Fix: admin/appointment-driven assignment endpoint + UI, or redefine nurse access. Effort: Medium.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-010 · F-10 · Frontend · C** — `doctor/PatientDetail.jsx` converts every 403 from prescriptions, records, labs and vitals into empty arrays (`.catch(() => [])`), so a doctor without consent sees "no medications/records" as if the patient had none; and `getById` requires `ALL` consent, so a doctor holding only `MEDICAL_RECORDS` cannot open the chart. Same pattern in `doctor/Prescriptions.jsx`. Fix: distinguish 403 ("access not granted") from empty; align `getPatientById` with per-category consent. Clinical-safety relevant. Effort: Small.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-011 · S-07 · Security · C (code)** — Refresh-token theft detection is rolled back. `AuthService.refreshToken` is `@Transactional`; on a revoked token it calls `revokeAllUserSessions`, writes an audit row, then throws `RuntimeException` → full rollback, so neither the revocation nor the `TOKEN_THEFT_DETECTED` entry persists. Fix: `noRollbackFor`, or perform revocation in a `REQUIRES_NEW` transaction. Test: replay a rotated token and assert other sessions revoked. Effort: Small.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-012 · S-06 · Security · C** — Idle timeout bypass: frontend silently refreshes on 401; `refreshToken → createSession → updateLastActive` re-arms the idle key, so the 30-minute idle limit does not hold while the 7-day refresh cookie lives. Fix: reject refresh when `session:active:<user>` is absent or track last-activity per session. Effort: Small.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-013 · S-09 · Security · C (Probe)** — Admin staff removal has no role guard and no audit: `DELETE /api/admin/staff/{id}` deleted a **PATIENT** login (probe); can also delete the last admin or self; `updateStaffRole` accepts `PATIENT`, creates ADMIN without 2FA flag or DOCTOR without a profile, error text leaks enum names. No audit record for role change/removal. FK constraints without cascade make deletion of any staff with history fail (D-01). Fix: forbid PATIENT/self/last-admin, soft-disable (`archived`/`enabled`) instead of delete, audit, set 2FA on promotion. Effort: Medium.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-014 · S-10 · Security · C** — Audit trail gaps: no audit event for consent grant/revoke, admin role change/removal, lab result upload/status change, appointment approve/reject, nurse medication administration, record reads. `RequestLoggingFilter` logs generic `API_ACTIVITY` per 200 response only. Audit text includes diagnoses (PHI) and `audit_logs.details` is `varchar(255)` — see CALYX-016. Effort: Medium.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-015 · S-11 · Security · C (code)** — Nurse and vital-sign writes need no consent or relationship: any NURSE/DOCTOR/ADMIN can `POST /api/vital-signs` for any patient; any doctor can create records, prescriptions and lab orders for any patient (and `ADMIN` may order labs). Fix: require relationship (appointment/assignment/consent) on writes. Effort: Medium.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-016 · B-01 · API/DB · C (Probe)** — Medical-record creation fails when diagnosis ≳ 200 chars: the audit row `details` (`varchar(255)`) concatenates the diagnosis, the insert fails in the same transaction and the record is rolled back; client receives HTTP 400 with the raw SQL error. Fix: stop writing PHI into audit details; add `@Size` and enlarge column; fix handler to hide internals. Effort: Small.
> **Status: FIXED** — see §14 for how it was fixed.

### Medium

**CALYX-017 · F-01 · Frontend · C** — `ConsentManagement.jsx` (≈ line 1379) shows a hard-coded "who accessed your data" log (fictitious clinicians and dates) in the patient's Data tab; no backend endpoint exists. Remove or build `GET /api/consent/access-log` from audit data.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-018 · F-02 · Frontend · C** — `AppointmentSidePanel.jsx` shows hard-coded phone `555-0123` and e-mail `patient@example.com` as "Patient Contact" for every appointment (comment says "Mock Data"); both strings are in the production bundle. Use the real patient contact via an authorized DTO or remove.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-019 · F-12 · Frontend · C** — Fake success actions in `ConsentManagement`/`GrantModifyConsent`: "Save draft" toast does nothing; "Export as PDF/FHIR/C-CDA … check your email" and "Deletion request form will open" only show toasts; withdrawal reason/effective date and signature options are collected but not sent; consent history/FAQ arrays are always empty; "Modify" is a re-grant that the backend rejects while the consent is active.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-020 · F-11 · Frontend/API · C** — Prescription management: UI sends `{active}` and `refillsAllowed`, backend `PrescriptionRequest` has neither; status can never change and refills can never be set, but the UI updates local state optimistically and reports success; refresh reverts. `refillPrescription` is therefore unusable (always 0).
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-021 · F-16 · Frontend/Workflow · C** — Notifications not implemented (static empty panels in `Header.jsx` and `NotificationsPanel.jsx`; no API, no events). Dead `SessionTimeout` component means no idle warning.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-022 · F-14 · Frontend · C** — `supabaseAuth.js` forgot/validate/reset flows read `data.error`; backend returns `message`. Users never see "password reused", "weak pattern" or "token expired"; only generic fallbacks.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-023 · F-13 · Frontend · C** — Nurse pages: hard-coded `status:'stable'`, `room/bed:'N/A'`, diagnosis fallback `'Observation'`; vitals form drops `painLevel`, `notes`, temperature route (shown in local history, lost on refresh); placeholder patient name "Selected Patient".
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-024 · W-05 · API · C** — Appointment rules: no check that the target login is a DOCTOR, date is in the future, falls on a working day/shift/slot grid (only `available-slots` enforces); `getAvailableSlots` treats `REJECTED` as occupied while `createAppointment` treats it as free; `updateAppointment` (doctor) can change the date without a conflict check; status transitions are unguarded (cancel completed, complete cancelled, reschedule rejected/no-show); `NO_SHOW` unreachable; unconfigured doctor profile with null shift times → NPE.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-025 · S-08 · Security · C** — OTP generated with `new Random()`; stored plaintext in `login.otp`; compared with `equals`. `POST /api/auth/resend-otp` is unauthenticated and overwrites the stored OTP (limit 5/15 min per e-mail), enabling OTP invalidation DoS and e-mail spam. `enable-2fa` is a no-op for non-doctor/admin roles (login only enforces 2FA for DOCTOR/ADMIN) while reporting success. Fix: `SecureRandom`, store hash + expiry, tie OTP to a pending-login token.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-026 · S-12 · Security · C** — Brute-force controls are per-e-mail only: any visitor can lock any account for 30 minutes (5 bad passwords); no per-IP limits; no limiting on register, forgot-password, invite validate. Locked/unknown responses differ ("Account locked" vs "Invalid credentials"), unknown-user path skips Argon2 (timing). Registration reveals "Email already taken". E-mail is case-sensitive in `login` (duplicate accounts by case).
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-027 · S-13 · Security · C** — `archived` and `locked` flags are not checked per request or at refresh; `CustomUserDetailsService` ignores them; deleted user with a live JWT causes an unhandled `UsernameNotFoundException` in the filter (500, not 401). `ArchivalService` never sees real activity (`lastLoginAt` never written) and archive has no effect.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-028 · B-02 · API · C (Probe)** — `GlobalExceptionHandler` returns raw exception text for all `RuntimeException`s as 400 (SQL/constraint details, null-message NPEs), maps by substring ("403", "409", "not found"), and masks real 500s. Replace with typed exceptions (`NotFound`, `Forbidden`, `Conflict`).
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-029 · B-03 · API · C** — Persistence gaps: lab `resultValue/unit/referenceRange` on create ignored; `unit/referenceRange` never writable; medical-record `attachmentUrl` has no input (file download branch for records unreachable); lab technician can set `fileUrl` to any stored filename; role string "firstAuthority" used for logic.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-030 · I-02 · Infrastructure · C** — Postgres, Redis (no auth), Adminer, Prometheus, Grafana (default credentials) published on all interfaces; no health checks; secrets passed via env; Adminer is dev-only but unconditional.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-031 · I-03 · Infrastructure · C** — `deployment.yml`: hard-coded host, unpinned third-party action, not gated on CI, conflicting frontend start (pm2 on 3000 vs Compose 3000:80), `docker-compose` v1 command. `ci.yml`: stale paths, Node 25, `npm install` instead of `npm ci`, no lint/build/Flyway-on-Postgres step.
> **Status: FIXED** — see §14 for how it was fixed.

**CALYX-032 · I-04 · Infrastructure · C** — `BackupService`: `pg_dump` absent from the runtime image; backups are plaintext on ephemeral container storage; failures only logged.
> **Status: FIXED** — see §14 for how it was fixed.

### Low

**CALYX-033 · F-15 · Frontend · C** — `signup()` calls `saveSession` without an access token, creating a ghost authenticated state until reload; role/identity for routing is read from `localStorage` (cosmetic only — backend enforces — but stale after admin role change).
> **Status: FIXED** — see §14 for how it was fixed.
**CALYX-034 · F-17 · Backend/Data · C** — `registerUser` fabricates first/last name "Unknown" and DOB = today when missing.
> **Status: FIXED** — see §14 for how it was fixed.
**CALYX-035 · F-18 · Frontend/API · C** — `appointments.reject` sends `JSON.stringify(reason)`; backend stores the string with quotes.
> **Status: FIXED** — see §14 for how it was fixed.
**CALYX-036 · R-03 · Redis · C** — JWT stored verbatim in Redis keys (`jwt:blacklist:<token>`); store a hash. Idle key per e-mail not per session.
> **Status: FIXED** — see §14 for how it was fixed.
**CALYX-037 · D-02 · DB · C** — `db/seed_users.sql` incompatible with V1 schema (non-existent columns); contains real-looking personal e-mails and a shared password in a comment; `DB_README.md` stale (says JPA manages schema).
> **Status: FIXED** — see §14 for how it was fixed.
**CALYX-038 · H-01 · Hygiene · C** — Committed artefacts: `api_logs.txt` (810 kB, UTF-16 SQL logs with test e-mails), `logs.txt`, test output files, tracked `frontend/app/.env`, `.idea`, duplicate root `package.json`; ≈ 20 stale audit markdown files that contradict the code.
> **Status: PARTIALLY FIXED (the stale report files and tracked log/IDE files are listed with proposed `git rm` commands in §14 but not removed, by instruction)** — see §14 for how it was fixed.
**CALYX-039 · P-01 · Performance · C** — Per-request audit insert; N+1 patient/prescription loading; unpaginated `findAll()` endpoints; `open-in-view` left on.
> **Status: FIXED** — see §14 for how it was fixed.

### Informational
**CALYX-040 · R-01 · Redis · C** — Redis outage = full authentication outage (no fallback by design); document or add a degraded mode for health endpoints. Also: Argon2 parameters (m=4096 KiB) are below current OWASP minimum (19 MiB); no CSP/HSTS in nginx; nginx forwards `X-Forwarded-For` correctly.
> **Status: PARTIALLY FIXED (the `npm audit` advisories in the Create-React-App toolchain need a major migration, which was out of scope)** — see §14 for how it was fixed.

---

## 10. Recommended fixes
Per finding above (fix, files, tests). Cross-cutting prerequisites:
1. Introduce response DTOs everywhere and a JSON-contract test (CALYX-001) **before** anything else.
2. Introduce one `AccessPolicy` service (relationship + consent + expiry) and route all reads **and writes** through it (CALYX-002/003/005/006/015); then simplify `PatientAccessValidator`.
3. Introduce enums for lab/prescription/appointment statuses with a transition table, migration `V2__status_constraints.sql` (CALYX-008/020/024).
4. Typed exceptions + `@ControllerAdvice` rewrite (CALYX-016/028).

---

## 11. Prioritised roadmap

| Priority | Task | Prereqs | Effort | Acceptance |
|---|---|---|---|---|
| **P0** | CALYX-001 DTOs, hide credential fields | — | S | Contract test: no `passwordHash/otp` in any response; probes p1–p3 return clean |
| **P0** | CALYX-004 Dockerfile + restructure commit + CI paths | — | S | `docker compose up --build` all healthy; CI green |
| **P0** | CALYX-002 consent expiry | — | S | Expired consent → 403; re-grant allowed |
| **P0** | CALYX-003 directory/appointment consent | AccessPolicy | M | Unrelated doctor gets 403/minimal DTO |
| **P1** | CALYX-005/006/015 nurse/lab/write authorization | AccessPolicy | M | Cross-patient IDOR tests pass |
| **P1** | CALYX-007 doctor ID mismatch; role validation | — | S | Booking against doctor B books doctor B (e2e) |
| **P1** | CALYX-008 lab status enum | — | M | Order → collect → result → patient view e2e |
| **P1** | CALYX-009 care-team assignment | — | M | Nurse sees assigned patients on fresh DB |
| **P1** | CALYX-010 PatientDetail 403 handling | — | S | UI shows "access not granted" |
| **P1** | CALYX-011/012 session security | — | S | Replay test revokes all sessions; idle >30 min forces login |
| **P1** | CALYX-013/014/016 admin guard, audit, audit-detail length | — | M | Cannot delete patient/last admin; events recorded |
| **P2** | CALYX-017…032 | various | S–M each | per finding |
| **P3** | CALYX-033…040 | — | S | per finding |

---

## 12. Verification checklist

```bash
# backend (host has no JDK; use Docker)
docker run --rm -v "$PWD/backend:/app" -v ~/.m2:/root/.m2 -w /app maven:3.9.6-eclipse-temurin-21 mvn -B test
# frontend
cd frontend/app && CI=true npx react-scripts test --watchAll=false && npx react-scripts build
# stack
docker compose config -q && docker compose up -d --build && docker compose ps
```
Manual/e2e after fixes: (1) register patient, grant a doctor `PRESCRIPTIONS` with 1-minute expiry, confirm 200 then 403; (2) nurse `GET /api/nurse/assigned-patients` body contains no `passwordHash`; (3) book with doctor B, verify DB `doctor_id`; (4) doctor lab order → lab collect → upload → patient sees result; (5) replay a rotated refresh cookie and confirm all sessions revoked; (6) Flyway on empty PostgreSQL 16 (Testcontainers) and `hibernate validate`.

---

## 13. Remaining uncertainties
*Verified since the original audit (no longer open):* Flyway V1–V6 on an empty PostgreSQL 16 with `ddl-auto=validate` (Testcontainers); lazy-loading behaviour with open-in-view disabled (whole suite); nginx headers and the HSTS-only-over-TLS rule (checked against a running container); the backend and frontend images build; dependency vulnerability scanning was attempted (`npm audit` done, results under CALYX-040).
* No browser-driven end-to-end run was performed; the React screens are covered by unit/integration tests with mocked network calls.
* The GitHub Actions workflows were validated as YAML and their steps were exercised locally (Maven, npm, docker build), but they have not run on GitHub.
* SMTP delivery (OTP, reset and invite e-mail) and the Adminer, Prometheus and Grafana dashboards were not exercised.
* Redis behaviour under load and during an outage was reasoned about and documented (fail-closed) but not load-tested.
* `V5__NormalizeEmails` on real data: duplicates that differ only by case are reported in the log and left in place; they must be resolved manually before the unique index can be created.
* No OWASP dependency-check / Maven CVE database scan (needs an NVD API key); Spring Boot 3.2.12 is the last 3.2.x release and is out of OSS support.
* The live ID divergence between `doctor_profiles.profile_id` and `login.user_id` (CALYX-007) depends on data; the fix removes the dependency.
* The audit worked on an uncommitted working tree; HEAD may differ.

Artifacts created by this audit outside the repo: `scratchpad/probe` (copy + `AuditProbeTest.java`), logs. In the repo only git-ignored build output was produced (`backend/target`, `frontend/app/build`) plus this report.

---

---

## 14. Remediation log (2026-10-18)

Verification after the second remediation pass (2026-10-19): backend `mvn test` in Docker **283 tests, 0 failures, 0 skipped**, including `FlywayPostgresTest`, which applies migrations V1–V6 to an empty PostgreSQL 16.14 (Testcontainers) and boots Hibernate with `ddl-auto=validate`, on Spring Boot 3.2.12 with open-in-view disabled; frontend **76 suites / 420 tests, all passing (latest run 2026-10-20); `react-scripts build` with `CI=true` (warnings are errors) compiles cleanly**. New test classes: `AuditP2RegressionTest` (12), `AuditP3ApiRegressionTest` (17), `AuditP3AuthRegressionTest` (9), `ResponseContractTest` (2), `FlywayPostgresTest` (4), `BackupServiceTest` (2), `DevDataSeederTest` (1), `RateLimiterIpLimitTest` (2), `RequestLoggingPolicyTest` (3), `RolesTest` (2), `LabStatusTest` (2); frontend `CareTeamManagement.test.jsx`, `UserManagement.test.jsx`, `NotificationBell.test.jsx`, `ConsentHonesty.test.jsx`, `AppointmentSidePanel.noMock.test.jsx`, `DashboardLayout.idle.test.jsx`, `supabaseAuth.p3.test.js` and additions to `api.test.js`, `tokenStorage.test.js`, `Prescriptions.test.jsx`. Earlier (2026-10-18) pass: 229 backend / 354 frontend tests, `AuditP0RegressionTest` (6), `AuditP1RegressionTest` (6). Still **not verified**: a browser-driven end-to-end run; the GitHub Actions workflows on GitHub; SMTP delivery; Redis under load; the `V5` e-mail migration on real production data. The whole stack was also started from the repository's `docker-compose.yml` in a throwaway Compose project (`calyxaudit`, its own volumes and loopback ports, a generated `.env`): PostgreSQL, Redis, backend, frontend (nginx), Prometheus and Grafana came up and the database, Redis, backend and frontend containers reported healthy (Adminer stays off without `--profile dev`). Flyway applied V1–V6 to the empty PostgreSQL 16 database; the backend created the first administrator. Through the nginx proxy a patient registered (201), a second registration with a different e-mail case answered identically and created no second account, sign-in with the address in another case worked, `/auth/me`, `/patients/me`, `/notifications` and `/consent/access-log` answered, a wrong password returned the uniform "Invalid credentials", and an unauthenticated call returned 401. Redis rejected unauthenticated commands (NOAUTH), only the frontend is published beyond loopback, `ux_login_email_lower` exists, Prometheus reports the backend target `up`, and `pg_dump` is present in the backend image running as a non-root user. The project was then removed with `docker compose -p calyxaudit down -v --rmi local`; no existing container, volume or database was touched. Not exercised in that run: staff two-factor sign-in (needs an SMTP relay) and Adminer.

| ID | Status | How it was fixed | Files | Regression test |
|---|---|---|---|---|
| CALYX-001 Credential hashes/OTP in responses | Fixed | Two layers. (1) Added `@JsonIgnore` to `Login.passwordHash`, `otp`, `otpExpiry`, `otpPendingTokenHash`, `failedAttempts`, `lockoutUntil`; corrected the `@JsonIgnoreProperties` typo `"password"` → `"passwordHash"` in `HandoverNote`/`NurseTask`. (2) Follow-up: the nurse, handover, vital-sign and consent endpoints no longer return JPA entities at all; they return `NurseViews` / `ConsentView` DTOs (staff references carry only id, e-mail and role). A **contract test** now calls every `GET` endpoint as every role against seeded data and the echoing `POST`s, and fails if any body contains `passwordHash`, `otp`, `otpExpiry`, `failedAttempts` or `lockoutUntil`. | `model/Login.java`, `model/HandoverNote.java`, `model/NurseTask.java`, `dto/NurseViews.java`, `dto/ConsentView.java`, `controller/NurseController.java`, `controller/VitalSignController.java`, `service/ConsentService.java` | `AuditP0RegressionTest`; `ResponseContractTest` (2 tests, ~220 endpoint/role calls) |
| CALYX-002 Consent expiry not enforced | Fixed | `ConsentService.hasConsent` now calls a new repository query `existsEffective(...)` that requires `status='ACTIVE'` **and** (`expiresAt` null or in the future), for the requested type or `ALL`. Added a scheduled job `expireStaleConsents()` (every 10 min) that flips lapsed consents to `EXPIRED`; `grantConsent` also runs it first, so a patient can re-grant after expiry (the unique `active_key` is cleared by the existing `@PreUpdate`). | `repository/ConsentRepository.java`, `service/ConsentService.java` | `AuditP0RegressionTest.expiredConsent_isDenied_…` — expired → 403, future/open-ended → 200 |
| CALYX-003 Consent bypass on patient directory and appointments | Fixed | `GET /api/patients`: admins unchanged; doctors now receive only patients they have a scheduled/completed appointment with (new `PatientService.getAllPatients(role, email, pageable)`, paginated in memory). Appointments: `GET /api/appointments/patient/{id}` and `/{id}` return 403 to nurses/lab technicians and are filtered/limited to the doctor's own appointments for doctors; `GET /api/appointments` returns only the doctor's own schedule for doctors. Admin and patient-owner behaviour unchanged. | `PatientService.java`, `PatientController.java`, `AppointmentController.java`, `PatientControllerTest.java` (mock signature) | `AuditP0RegressionTest` — unrelated doctor sees no address/doctor notes, gets 403 on detail; nurse gets 403; treating doctor still sees data |
| CALYX-004 Backend cannot be built / CI + deploy broken | Partly fixed | `backend/Dockerfile` rewritten (multi-stage Maven 3.9.6/Temurin 21 build, `eclipse-temurin:21-jre-alpine` runtime with `postgresql16-client`, non-root user, `.dockerignore`). **`ci.yml` rewritten:** paths fixed; jobs `backend` (`./mvnw -B verify`, which includes the Testcontainers PostgreSQL migration test), `frontend` (Node 20, `npm ci`, tests, `CI=true` build) and `docker` (compose validation + both image builds); actions pinned to commit SHAs; least-privilege `permissions`. **`deployment.yml` rewritten:** host/user/key from secrets (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, optional `DEPLOY_PATH`), triggered by `workflow_run` only after CI succeeded on `main`, `environment: production`, SHA-pinned `appleboy/ssh-action`, `docker compose` v2, fast-forward-only update, the conflicting `pm2 serve` removed. **Restructure check:** every file deleted from `backend/Backend/` and `DB/` has a counterpart in `backend/` and `db/` (the only intentional losses: `DB/schema.sql`, replaced by Flyway; and the earlier removal of the unused Messages/Reports/Incident pages); 77 moved files differ in content from HEAD (earlier remediation work, line endings). **Still open:** the restructure is uncommitted (commands in the table below this one). Also fixed on the way: `frontend/app/package-lock.json` was out of sync with `package.json` (`npm ci` failed in the image build and would fail in CI); regenerated with npm 10. | `backend/Dockerfile`, `backend/.dockerignore`, `.github/workflows/ci.yml`, `.github/workflows/deployment.yml`, `frontend/app/package-lock.json` | `docker build` of both images succeeds; workflows parse as valid YAML (not executed on GitHub) |
| CALYX-007 Doctor ID-space mismatch | Fixed | Patient booking now maps the doctor list to `id = userId` (login id), the key the appointment and slot endpoints use; patient profile resolves the assigned doctor by matching `userId` in `GET /doctors` instead of calling `/doctors/{profileId}` with a login id. Server side, `createAppointment` now rejects a target whose role is not `DOCTOR` and any date that is not in the future. | `pages/patient/Appointments.jsx`, `pages/patient/Profile.jsx`, `service/AppointmentService.java` | `AppointmentServiceTest.createAppointment_RejectsNonDoctorLogin`, `…_RejectsPastDates`; patient frontend suites still pass (existing fixtures lacking `role` updated) |
| CALYX-008 Lab status vocabulary | Fixed | Introduced `LabStatus` (`Pending`, `Collected`, `Results Pending`, `Completed`) with case/underscore-insensitive `canonical()` and forward-only `canTransition()`. Doctor-created orders now start as `Pending`; pending/status queries use a new case-insensitive repository method; `updateOrderStatus` rejects unknown values and backwards moves; `uploadResults` refuses to overwrite a `Completed` order and sets the canonical `Completed`. Flyway `V2__normalize_lab_status.sql` rewrites existing rows (e.g. `PENDING`, `COMPLETED`) to the canonical spelling. | `service/LabStatus.java` (new), `LabTestService.java`, `LabTechnicianService.java`, `LabTestRepository.java`, `db/migration/V2__normalize_lab_status.sql`, `LabTechnicianServiceTest.java` | `LabStatusTest`; updated service test. **V2 SQL not yet run on PostgreSQL.** |
| CALYX-009 No way to assign nurse/doctor | Fixed | Backend: `PUT /api/admin/patients/{id}/care-team` (validates DOCTOR/NURSE, audited). **Admin UI:** new *Care Teams* tab in the Admin Console (`CareTeamManagement.jsx`): patient search over the admin patient directory (`GET /api/admin/patients`, which now also returns the current assignments), a doctor picker and a nurse picker (enabled DOCTOR / NURSE logins only), save with success and error states, retry on load failure. `UserManagement.jsx` now has real role-change and disable/enable actions (with confirmation) and shows true status and last login instead of the hard-coded "Active / Just now". | `AdminService.java`, `AdminController.java`, `dto/CareTeamDTO.java`, `dto/PatientDirectoryDTO.java`, `dto/StaffDTO.java`, `services/api.js`, `pages/admin/Dashboard.jsx`, `pages/admin/components/CareTeamManagement.jsx`, `…/UserManagement.jsx` | `AuditP1RegressionTest`; `CareTeamManagement.test.jsx` (6), `UserManagement.test.jsx` (5) |
| CALYX-010 Misleading empty states / chart unreachable | Fixed | `PatientService.getPatientById` additionally allows a doctor who has a scheduled/completed appointment with the patient (consent-free care relationship, consistent with the scoped directory). `api.js` now attaches `status` to thrown errors; `PatientDetail.jsx` wraps each consent-gated call so a **403** records the section as restricted instead of becoming an empty list, and shows an amber "Access not granted for: …" banner. | `PatientService.java`, `services/api.js`, `pages/doctor/PatientDetail.jsx` | `AuditP1RegressionTest.doctorWithAppointment_canOpenPatient_butUnrelatedDoctorCannot`; existing PatientDetail tests pass |
| CALYX-011 Theft detection rolled back | Fixed | `AuthService.refreshToken` is now `@Transactional(noRollbackFor = RuntimeException.class)`, so the `revokeAllUserSessions` update and the `TOKEN_THEFT_DETECTED` audit row are committed even though the method throws. | `service/AuthService.java` | `AuditP1RegressionTest.reusedRefreshToken_revokesAllSessionsAndPersistsIt` (non-transactional test, asserts the live session is revoked in the DB) |
| CALYX-012 Idle-timeout bypass via refresh | Fixed | Before rotating, `refreshToken` checks `tokenBlacklistService.isSessionIdle(email)`; if the idle marker has expired it revokes that session, logs `SESSION_IDLE_EXPIRED` and refuses with 401, so the 7-day refresh cookie can no longer silently renew an idle session. | `service/AuthService.java` | `AuditP1RegressionTest.refresh_isRefusedWhenSessionIsIdle`, `refresh_succeedsForActiveSession` |
| CALYX-005 Nurse medication endpoints unauthorised | Fixed | `NurseService.recordMedicationAdministration` / `getAdministrationHistory` now go through `AccessPolicy` (assigned nurse **or** effective `PRESCRIPTIONS` consent), require the prescription to be `ACTIVE`, reject a repeat within `app.medication.min-interval-minutes` (default 5, configurable; 409), and write a `MEDICATION_ADMINISTERED` audit event. The controller no longer flattens 403/404 to 400. **Decision:** nurses get access by assignment *or* consent, not assignment alone. | `service/NurseService.java`, `controller/NurseController.java`, `security/AccessPolicy.java` | `AuditP2RegressionTest.medicationAdministration_…` (unrelated nurse 403 on both calls, assigned nurse 200, repeat 409, cancelled prescription 400, audit row) |
| CALYX-006 Lab technician scope | Fixed | Doctors only see lab orders for patients `AccessPolicy` lets them read (`GET /api/lab-results`, `/pending`); admins and the lab queue keep the full list, which exposes only patient name, gender and profile id. Status changes and result uploads are audited; edits after `Completed` are refused; `fileUrl` must be a file **uploaded by the same technician** (400 otherwise); `unit` and `referenceRange` are now accepted and stored. Lab order creation is `DOCTOR` only. | `LabResultController.java`, `LabTechnicianController.java`, `LabTechnicianService.java`, `LabTestService.java` | `AuditP2RegressionTest.labResultsList_isScopedForDoctors`, `labUpload_acceptsOnlyOwnFile_…`, `labStatusChange_isAudited` |
| CALYX-013 Admin staff removal | Fixed | `DELETE /api/admin/staff/{id}` is a **soft-disable** (`archived=true`, all sessions revoked, idle markers cleared) and refuses PATIENT targets, self-removal and the last active ADMIN; new `PUT /api/admin/staff/{id}/enable`. `updateStaffRole` rejects PATIENT and unknown roles with a generic message (no enum names), forbids changing your own role or demoting the last admin, forces 2FA on promotion to ADMIN/DOCTOR and creates an empty doctor profile on promotion to DOCTOR. Both are audited (`STAFF_DISABLED`, `STAFF_ENABLED`, `STAFF_ROLE_CHANGED`). **Decision:** disable, not delete (the foreign keys make deletion of anyone with history impossible and it would destroy the audit trail). | `AdminService.java`, `AdminController.java`, `LoginRepository.java` | `AuditP2RegressionTest.staffRemoval_isSoftDisable_…`, `lastActiveAdmin_cannotBeRemovedOrDemoted`, `roleChange_rules` |
| CALYX-014 Audit coverage | Fixed | New `AuditService` (own transaction, ids only, no clinical content). Events: `CONSENT_GRANTED/REVOKED/UPDATED/EXPIRED`, `STAFF_ROLE_CHANGED/DISABLED/ENABLED`, `LAB_STATUS_CHANGED`, `LAB_RESULT_UPLOADED`, `APPOINTMENT_APPROVED/REJECTED`, `MEDICATION_ADMINISTERED`, `CARE_TEAM_UPDATED`, and `PATIENT_DATA_READ` (one per resource read by a non-patient, written by `PatientAccessValidator`). | `service/AuditService.java`, `ConsentService.java`, `AdminService.java`, `AppointmentService.java`, `LabTechnicianService.java`, `NurseService.java`, `security/PatientAccessValidator.java` | `AuditP2RegressionTest.consentAndAppointmentAndReadEvents_areAudited` and the per-feature tests above |
| CALYX-015 Clinical writes lack relationship checks | Fixed | New `AccessPolicy` (the one place that decides read/write access from role, care relationship — assigned doctor/nurse or a doctor with a SCHEDULED/COMPLETED appointment — and effective, unexpired consent for the category or `ALL`); `PatientAccessValidator` now delegates to it. Writes of vital signs, medical records, prescriptions, lab orders and medication administration call `checkWrite`; **ADMIN can no longer write clinical data** (vitals, lab orders). **Decisions:** a doctor with an approved appointment may read and write that patient's clinical categories without a separate consent; ADMIN keeps audited read-only oversight. | `security/AccessPolicy.java`, `PatientAccessValidator.java`, `MedicalRecordService.java`, `PrescriptionService.java`, `VitalSignService.java`, `LabTestService.java`, `VitalSignController.java`, `LabResultController.java` | `AuditP2RegressionTest.clinicalWrites_requireRelationshipOrConsent_andAdminCannotWrite`; `PatientAccessValidatorTest` |
| CALYX-016 Audit details overflow / PHI in audit | Fixed | Audit rows no longer contain diagnoses or drug names (ids and action names only). `V3__widen_text_columns.sql` widens `audit_logs.details` to `text`, `medical_records.diagnosis` and `lab_tests.remarks` to 2000; request DTOs carry matching `@Size` limits and the partial-update paths use `Limits.max`, so an over-limit value is a clean 400. | `V3__widen_text_columns.sql`, `AuditLog.java`, `MedicalRecord.java`, `LabTest.java`, `MedicalRecordRequest/PrescriptionRequest/LabTestRequest.java`, `Limits.java` | `AuditP2RegressionTest.longDiagnosisSaves_overLimitIsCleanBadRequest_andAuditHasNoPhi`; `FlywayPostgresTest.v3Widened…` |
| CALYX-017 Fabricated access log | Fixed | Removed the hard-coded list. New `GET /api/consent/access-log` (patient-owner only) built from `PATIENT_DATA_READ` audit rows; the Data tab renders it with loading, empty and error states and search. | `ConsentController.java`, `ConsentService.java`, `AuditLogRepository.java`, `ConsentManagement.jsx` | `AuditP3ApiRegressionTest.accessLog_…`; `ConsentHonesty.test.jsx` |
| CALYX-018 Fake patient contact | Fixed | The "Patient Contact" block (phone `555-0123`, `patient@example.com`) is removed; no authorized DTO carries patient contact for appointments, so nothing is shown. | `components/AppointmentSidePanel.jsx` | `AppointmentSidePanel.noMock.test.jsx`; neither string appears in the production bundle (grep of `build/`) |
| CALYX-019 Fake consent actions | Fixed | "Save as draft" and its auto-save toast removed; PDF/FHIR/C-CDA export and data-deletion buttons are disabled and labelled "coming soon" (no success toast); the fabricated "Connected Apps" list is removed; the unsupported "end of month / specific date" withdrawal options are removed; the withdrawal reason is sent and stored (`revocation_reason`); **Modify** is a real in-place update (`PUT /api/consent/{id}`: expiry and note of an ACTIVE consent) instead of a failing second grant. | `ConsentManagement.jsx`, `GrantModifyConsent.jsx`, `ConsentController.java`, `ConsentService.java`, `Consent.java`, `V4__…sql` | `AuditP3ApiRegressionTest.consentCanBeModifiedInPlace_andRevokedWithAReason`; `ConsentHonesty.test.jsx` |
| CALYX-020 Prescription status/refills | Fixed | `PrescriptionRequest.refillsAllowed` (0–12) sets the initial refills; new `PUT /api/prescriptions/{id}/status` (ACTIVE → COMPLETED/CANCELLED, final, issuing doctor only, audited). The Manage dialog now saves dosage/frequency, then calls the status endpoint, and updates the row only after the server confirmed. | `PrescriptionService.java`, `PrescriptionController.java`, `PrescriptionRequest.java`, `pages/doctor/Prescriptions.jsx` | `AuditP3ApiRegressionTest.prescription_…`; `Prescriptions.test.jsx` (+2) |
| CALYX-021 Notifications / idle warning | Fixed | Minimal real notifications: `notifications` table (`V4`), `NotificationService`, `GET /api/notifications`, `PUT …/{id}/read`, `PUT …/read-all` (owner-scoped; messages carry no clinical detail). Created on appointment approve/reject and lab result published (patient and ordering doctor). New `NotificationBell` replaces the static panel in `DashboardLayout` (unread badge, mark read, one-minute poll). `SessionTimeout` is now wired: warning 60 s before the 30-minute idle limit, "Extend" pings `/auth/me`, and a keep-alive ping runs every 5 minutes while the user is active. | `Notification.java`, `NotificationRepository/Service/Controller.java`, `AppointmentService.java`, `LabTechnicianService.java`, `components/layout/NotificationBell.jsx`, `layouts/DashboardLayout.jsx` | `AuditP3ApiRegressionTest.notifications_…`, `publishedLabResult_…`; `NotificationBell.test.jsx` (5); `DashboardLayout.idle.test.jsx` (2) |
| CALYX-022 Reset-flow messages | Fixed | The forgot/validate/reset helpers read `data.message` (falling back to `data.error`). | `services/supabaseAuth.js` | `supabaseAuth.p3.test.js` |
| CALYX-023 Nurse placeholders / lost vitals fields | Fixed | Hard-coded `status:'stable'` → `'unknown'`, diagnosis fallback `'Observation'` → "No history recorded", placeholder patient name removed. `pain_level`, `notes`, `temperature_route` are now columns (`V4`), request/DTO fields (validated 0–10, ≤2000, ≤30) and are sent by the Vitals screen. | `VitalSign.java`, `VitalSignRequest/DTO.java`, `VitalSignService.java`, `pages/nurse/Patients.jsx`, `pages/nurse/Vitals.jsx` | `AuditP3ApiRegressionTest.vitalSigns_storePainNotesAndTemperatureRoute` |
| CALYX-024 Appointment rules | Fixed | `createAppointment`, patient reschedule and doctor date-edit validate: future time, working day, inside the shift, on the slot grid, doctor has a configured schedule (clean 400, no NPE). Slot calculation and booking treat REJECTED like CANCELLED. Doctor date edits run the same conflict check (409). `AppointmentStatus.canTransitionTo` defines the lifecycle (PENDING→SCHEDULED/REJECTED/CANCELLED; SCHEDULED→COMPLETED/CANCELLED/NO_SHOW; the rest final); complete/cancel/reschedule enforce it; `PUT /api/appointments/{id}/no-show` makes NO_SHOW reachable (own, SCHEDULED, time passed). | `AppointmentStatus.java`, `AppointmentService.java`, `AppointmentController.java` | `AuditP3ApiRegressionTest.booking_…`, `rejectedSlot_…`, `statusTransitions_…`, `doctorDateEdit_…`, `doctorWithoutConfiguredShift_…` |
| CALYX-025 OTP handling | Fixed | OTPs from `SecureRandom` (6 digits), stored as a salted SHA-256 digest with expiry, compared in constant time, bound to a one-time **pending token** returned by `login` (only its hash is stored); `verify-otp` and `resend-otp` require it, so an anonymous caller who only knows an address can no longer overwrite someone's OTP. `enable-2fa` is now real for every role (login challenges anyone with `twoFactorEnabled`). Frontend carries the token through sign-in. | `AuthService.java`, `AuthController.java`, `LoginResponse.java`, `Login.java`, `V4`, `supabaseAuth.js`, `AuthContext.jsx`, `login.jsx`, `TwoFactorAuth.jsx` | `AuditP3AuthRegressionTest.otp_…`, `resendOtp_…`, `enableTwoFactor_…`; `supabaseAuth.p3.test.js` |
| CALYX-026 Brute force / enumeration / e-mail case | Fixed | Per-IP Redis limits (`checkIpLimit`) on login (30/10 min), register (10/h), forgot-password (10/h) and invite-validate (30/10 min), answered with HTTP 429. Unknown, locked and disabled accounts all return "Invalid credentials" and spend a dummy Argon2 verification (timing). Registration answers identically whether or not the address exists. E-mail is normalised to lower case everywhere; `V5__NormalizeEmails` lower-cases non-conflicting rows, **reports** (never merges or deletes) addresses that differ only by case, and on PostgreSQL creates a unique index on `lower(email)` when none remain. | `RateLimiterService.java`, `AuthService.java`, `StaffInviteController/Service.java`, `V5__NormalizeEmails.java` | `RateLimiterIpLimitTest`; `AuditP3AuthRegressionTest.email_…`, `loginFailures_…`; `FlywayPostgresTest.emailsAreCaseInsensitivelyUniqueOnPostgres` |
| CALYX-027 Archived/locked/deleted users | Fixed | `CustomUserDetailsService` exposes `enabled` (not archived) and `accountNonLocked`; the JWT filter answers 401 for disabled/locked accounts and for a user that no longer exists (was an unhandled 500); login and refresh refuse archived/locked accounts; `lastLoginAt` is written at sign-in and OTP verification; the archival job skips accounts with no recorded login (it previously archived every account older than a year), and V3 backfills `last_login_at`. | `CustomUserDetailsService.java`, `JwtAuthenticationFilter.java`, `AuthService.java`, `ArchivalService.java`, `V3` | `AuditP2RegressionTest.staffRemoval_…`, `deletedUserWithLiveToken_gets401Not500`, `successfulLogin_recordsLastLoginAt_…` |
| CALYX-028 Exception handling | Fixed | `NotFoundException`, `ForbiddenException`, `ConflictException`, `BadRequestException`, `TooManyRequestsException`; all ~120 `new RuntimeException` throw sites converted; `GlobalExceptionHandler` maps by type only (no message sniffing), validation errors → 400 with field messages, integrity violations → 409 with a reference id, everything else → 500 "Reference: <uuid>" with the real error logged only on the server. Hand-written `catch (RuntimeException)` blocks now let typed exceptions through. | `exception/*`, all controllers/services | all existing controller tests updated to typed exceptions; `ResponseContractTest` |
| CALYX-029 Persistence gaps | Fixed | Lab order creation persists `resultValue`, `unit`, `referenceRange`; the technician upload also accepts `unit` and `referenceRange`; medical records accept `attachmentUrl` (must be a file uploaded by the same doctor; blank clears it); role lookups use `Roles.of(auth)` (the known role authority) instead of "first authority". | `LabTestService.java`, `LabTechnicianService.java`, `MedicalRecordService.java`, `MedicalRecordRequest.java`, `security/Roles.java` | `AuditP3ApiRegressionTest.labOrder_persistsResultFields_…`; `RolesTest` |
| CALYX-030 Exposed services | Fixed | `docker-compose.yml`: Postgres, Redis, Prometheus, Grafana, Adminer bound to `127.0.0.1`; Adminer only under `--profile dev`; Redis `--requirepass ${REDIS_PASSWORD}` and the backend reads `spring.data.redis.password`; Grafana admin credentials from `${GRAFANA_ADMIN_USER/PASSWORD}` with no default; healthchecks (pg_isready, redis-cli ping, backend `/actuator/health/liveness`) and `depends_on: condition: service_healthy`; the unused `postgres_data` volume removed. `.env.example` documents the new variables. | `docker-compose.yml`, `application.properties`, `.env.example` | `docker compose config -q`; stack started and probed (see the verification note) |
| CALYX-031 Pipelines | Fixed | See CALYX-004 (CI and deployment rewritten). | `.github/workflows/*` | YAML parses; not run on GitHub |
| CALYX-032 Backups | Fixed | `BackupService` now dumps to a temporary file, encrypts it with AES-256-GCM (`AesGcmFiles`, the application key) and deletes the plaintext; backups live in the `backup_data` volume; failures are exposed as `/actuator/health` component `backup` (status `DEGRADED`, HTTP 200) and the metrics `calyx.backup.failures.total` and `calyx.backup.last.success.timestamp.seconds`. | `BackupService.java`, `BackupHealthIndicator.java`, `util/AesGcmFiles.java`, `docker-compose.yml` | `BackupServiceTest` (encrypt/decrypt round trip, tamper detection, failed run → DEGRADED and no plaintext left) |
| CALYX-033 Ghost session / client-trusted role | Fixed | Registration no longer creates a session. On every page load the role and identity are re-read from `GET /auth/me` with the refreshed token and the stored copy is overwritten; a failed check clears the session. | `services/supabaseAuth.js`, `contexts/AuthContext.jsx` | `tokenStorage.test.js` (+2), `supabaseAuth.p3.test.js`, `AuditP3AuthRegressionTest.authMe_…` |
| CALYX-034 Fabricated identity | Fixed | Full name and a past date of birth are required at registration; the "Unknown" name and today's-date DOB fallbacks are gone (single-word names get "-" as last name). | `RegistrationRequest.java`, `AuthService.java` | `AuditP3ApiRegressionTest.registration_requiresNameAndDateOfBirth` |
| CALYX-035 Quoted rejection reason | Fixed | The client sends `{reason}`; the controller reads a JSON object; `V6__strip_quoted_rejection_reasons.sql` removes the stored quotes from existing rows. | `api.js`, `AppointmentController.java`, `V6` | `AuditP3ApiRegressionTest.rejectReason_isStoredWithoutJsonQuotes`; `api.test.js` |
| CALYX-036 Redis keys / idle tracking | Fixed | Blacklist keys are `jwt:blacklist:<SHA-256 hex>`; access tokens carry a `sid` claim and idle tracking is per session (`s:<id>`), cleared on logout, reset, refresh and staff disable. The CALYX-012 tests were adapted and still pass. | `TokenBlacklistService.java`, `JwtUtil.java`, `JwtAuthenticationFilter.java`, `AuthService.java`, `AdminService.java` | `AuditP3AuthRegressionTest.accessToken_isBoundToItsSession_…`, `blacklistKeys_neverContainTheRawToken`; `AuditP1RegressionTest` |
| CALYX-037 Seed data | Fixed | `db/seed_users.sql` rewritten for the V1–V5 schema: `@example.test` accounts, a care team, a few clinical rows, **no password** (pass your own Argon2 hash with `psql -v pw_hash=…`), idempotent. New `DevDataSeeder` (`dev` profile + `APP_DEV_SEED_ENABLED=true`) creates the same accounts with `APP_DEV_SEED_PASSWORD` or a generated password logged once. `db/DB_README.md` now says Flyway owns the schema and lists V1–V6. | `db/seed_users.sql`, `db/DB_README.md`, `service/DevDataSeeder.java` | `DevDataSeederTest` |
| CALYX-038 Hygiene | Partly fixed | `.gitignore` now ignores logs, test output, IDE folders, `frontend/app/.env`, build output. The stray root `package.json`, `package-lock.json` and `src/__tests__/…` (an import of a non-existent component) were removed. **Not done by instruction:** the files that are already tracked and the stale report files are listed with proposed commands below. | `.gitignore` | `git status` shows no new tracked artefacts |
| CALYX-039 Performance | Fixed | `RequestLoggingFilter` writes only mutations and 403/5xx (reads are audited explicitly); `GET /api/prescriptions/mine` returns all of a doctor's prescriptions in one query with `@EntityGraph` (the page previously made one request per patient); `GET /api/appointments`, `/api/lab-results` and the lab-technician order list are paginated (`page`, `size`; default 500, max 1000), as is the admin patient directory; `spring.jpa.open-in-view=false` with the lazy-loading consequences fixed (services/controllers map to DTOs inside a transaction; `AccessPolicy`, `FileStorageService.loadFile` and the nurse mappings run in read-only transactions); duplicate `micrometer-registry-prometheus` dependency and the obsolete `management.metrics.export.prometheus.*` properties removed. | `RequestLoggingFilter.java`, `PrescriptionRepository/Service/Controller.java`, `PageParams.java`, `application.properties`, `pom.xml` | `RequestLoggingPolicyTest`; `AuditP3ApiRegressionTest.listEndpoints_honourPageAndSize`, `myPrescriptions_…`; the whole suite runs with open-in-view off |
| CALYX-040 Hardening / dependencies | Partly fixed | Argon2id raised to the OWASP minimum (m=19456 KiB, t=2, p=1) with rehash-on-login (`upgradeEncoding`); nginx sends a strict CSP, `X-Frame-Options`, `nosniff`, Referrer/Permissions policies and HSTS only over TLS (`security-headers.conf` included in every location; the image sets `INLINE_RUNTIME_CHUNK=false` so the CSP can forbid inline scripts); README documents the Redis fail-closed behaviour; `/actuator/health` no longer depends on Redis or the mail relay; liveness/readiness probes added. **Dependencies:** Spring Boot 3.2.2 → 3.2.12 and `bcprov-jdk18on` 1.76 → 1.78.1 (patch bumps, full suite green). `npm audit` (production dependency tree): 103 advisories (3 critical — `proxy-addr`, `shell-quote`, `websocket-driver` —, 77 high) almost all transitive through `react-scripts` 5 / `webpack-dev-server` (build and dev-server tooling that is not part of the nginx-served bundle) plus direct `postcss`, `tailwindcss`, `react-router-dom`; clearing them needs a Create-React-App → Vite (or CRA 6) migration, **not done** (no major upgrades). No OWASP dependency-check run (needs an NVD API key). Spring Boot 3.2.x is out of OSS support; a 3.4/3.5 upgrade should be its own change. | `SecurityConfig.java`, `AuthService.java`, `frontend/nginx.conf`, `frontend/security-headers.conf`, `frontend/Dockerfile`, `README.md`, `pom.xml` | `AuditP3AuthRegressionTest.argon2_…`; `AuditP3ApiRegressionTest.health_…`; headers verified against a running container (below) |

### 2026-10-19 — follow-up: lab view and order detail
Two gaps left by the lab-technician review were closed afterwards: the doctor lab view's *Abnormal* filter (it could never match) and the red error styling of `Collected` / `Results Pending`, and the missing *Mark Results Pending* button on `OrderDetail.jsx`. A published result is now flagged abnormal when its value lies outside its reference range (`frontend/app/src/utils/labRange.js`); the page filters by every status; the order page can advance Collected → Results Pending and reports a rejected transition. Tests: `labRange.test.js`, `LabResults.abnormal.test.jsx`, `OrderDetail.test.jsx`. Frontend: **75 suites / 418 tests**, `CI=true` build clean. Backend unchanged (283 tests).

### 2026-10-20 — third pass: every audit document re-checked against the code, and condensed
All audit documents were walked item by item against the current code. The audit set is now **two files**: this report (security, platform, infrastructure; CALYX-001 … 040 plus Appendix A) and `FULL_STACK_INTEGRATION_AUDIT.md` (integration status register for the 77 original findings, with the lab and frontend documents folded in). Merged and removed: `FRONTEND_INTEGRATION_ISSUES.md`, `LABTECHNICIAN_INTEGRATION_ISSUES.md`, `SECURITY_GAPS_REMEDIATION_REPORT.md` (now Appendix A), `PR_SUMMARY.md`, `frontend/CODE_QUALITY_REPORT.md`, `frontend/app/MERGE_CONFLICT_RESOLVED.md` (one-off session reports with stale counts).
Findings that were still true and are now fixed (minimal changes):
* **DR-7 / DR-8** — doctor dashboard: "Pending Labs" was the literal `5` and the Daily Briefing was invented (fixed date, "3 high-priority", "2 inquiries"). It now counts real lab orders and abnormal results and shows today's date (`pages/doctor/Dashboard.jsx`).
* **DR-9** — appointment rows have a *View Patient* button using `AppointmentDTO.patientId` (`pages/doctor/Appointments.jsx`).
* **PT-6** — the consent slide-over always showed the *research* form because its id map matched ids real consents never have; it now uses the health-information-exchange copy (`pages/patient/ConsentManagement.jsx`).
* **PT-8 / PT-9** — "Next Refill: In 5 days" replaced by the real refills remaining; dead `.active` filter removed (`pages/patient/Prescriptions.jsx`, `Dashboard.jsx`).
* **NR-6 (display)** — nurse screens no longer show a green "stable" status or `N/A-N/A` for room/bed; they say "unknown" / "Not recorded" (`pages/nurse/PatientDetail.jsx`, `Patients.jsx`).
* **SH-10** — `frontend/app/src/.env.example` port aligned with the real backend port (8081).
Accepted without change (reason in the register): SH-9 naming, PT-11 repeated `getMe()`, DB-7/DB-8 field types, SH-12, NR-7. **Still open:** PT-10 (unused `PatientProfileRequest.java`; deleting it was not permitted in the session: `git rm backend/src/main/java/com/securehealth/backend/dto/PatientProfileRequest.java`) and the NR-6 data model (product decision).
Tests: new `pages/doctor/OpenItems.test.jsx` (dashboard counts, View Patient). Frontend **76 suites / 420 tests**, `CI=true` build clean. Backend untouched (283 tests, last run 2026-10-19). PT-6, PT-8/9 and NR-6 have no dedicated test; the existing page suites stay green.

### 2026-10-19 — second pass: what is still open, decisions, commands

**Still open**
* **CALYX-038 (partly):** nothing was untracked or deleted by instruction. Proposed, for you to run if you agree:
  * Tracked logs and test output: `git rm --cached api_logs.txt logs.txt test_payload.json frontend/app/medtest.log frontend/app/test-output.txt frontend/app/test-results.txt frontend/app/test.log frontend/app/test_output.txt frontend/app/test_results.txt`
  * Tracked IDE files and the local env file: `git rm --cached -r .idea backend/Backend/.idea backend/Backend/Backend.iml` and `git rm --cached frontend/app/.env`
  * Stale report files: merged or removed on 2026-10-20 (see the third-pass note below); nothing further to delete there.
* **CALYX-040 (partly):** the Create-React-App toolchain advisories (needs a major migration); Spring Boot 3.2.x is out of OSS support.
* **CALYX-004 (partly):** the repository restructure is not committed (commands below).
* Not verified: see the header paragraph above and §13.

**Product decisions made (most conservative secure choice)**
1. *Nurse access* = assigned nurse **or** effective consent (never "any nurse").
2. *Doctor access* = assigned doctor **or** a SCHEDULED/COMPLETED appointment **or** effective consent; nothing else.
3. *ADMIN* keeps read-only oversight of clinical data through `AccessPolicy` (every such read is audited as `PATIENT_DATA_READ`) but can no longer write clinical records, vitals or lab orders.
4. *Staff removal* = soft-disable (account kept, sessions revoked, cannot sign in); a separate enable action restores it.
5. *Lab orders* can be created by doctors only.
6. *Registration* never reveals whether an address is already registered.
7. *Two-factor* applies to every account that has it enabled (staff roles always do).
8. *Withdrawal* takes effect immediately; scheduled withdrawal options were removed rather than faked.
9. *Notifications* are in-app only and never contain clinical details.

**Proposed commit commands (nothing was committed or pushed)**
```bash
cd PatientManagementSystem
git add -A backend db frontend docker-compose.yml .env.example .gitignore .github README.md \
  CALYX_AUDIT_REPORT.md FULL_STACK_INTEGRATION_AUDIT.md prometheus.yml
git add -u                      # records the deletions (backend/Backend/*, DB/*, root package files, merged audit and report files)
git commit -m "Fix the Calyx audit findings: access policy, typed errors, DTOs, hardening, CI/deploy, tests"
# optional follow-ups for CALYX-038 (see the list above):
# git rm --cached api_logs.txt logs.txt test_payload.json frontend/app/.env ...
```
Before committing, add the new variables `REDIS_PASSWORD`, `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` to your local `.env` (`docker compose` refuses to start without them), and create the GitHub secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` (optional `DEPLOY_PATH`).

---

## Appendix A — Earlier gap remediation (2026-10-12, 11 gaps)

Carried over from the former `SECURITY_GAPS_REMEDIATION_REPORT.md`; all 11 were re-checked during this audit and still hold.

| # | Gap | Fix now in place |
|---|---|---|
| 1 | Public `register` took the role from the body | Always creates a `PATIENT`; staff join through single-use, hashed, expiring invite links; first admin from `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` on first start only |
| 2 | `enable-2fa` took the e-mail from the body | Identity comes from the JWT |
| 3 | `GET /appointments/status/{status}` unguarded | Role-guarded and filtered per caller |
| 4 | Literal JWT secret / default keys | No defaults; `StartupSecretsValidator` refuses weak or published values |
| 5 | No indexes / migrations / drifted `schema.sql` | Flyway V1–V6, `ddl-auto=validate`, drift tests |
| 6 | Double booking was check-then-insert | Unique `active_slot_key` (409 on a lost race); same pattern for consents (`active_key`) |
| 7 | Cookie lacked `SameSite`; token in `localStorage` | `HttpOnly; SameSite=Strict; Path=/api/auth`; access token in memory |
| 8 | Unpaginated audit logs, debug prints | Paged audit list (default 50, max 200); SLF4J only |
| 9 | Mock endpoints / panels / profiles | Real security-events, user-activity and system-health endpoints; mock panels and fake profiles removed; consent grant validated |
| 10 | No assigned-doctor exemption / consent uniqueness | Superseded by `AccessPolicy` (CALYX-015) |
| 11 | No frontend container | Multi-stage nginx image, `/api` proxy, health check; backend port loopback-only |

Known limits that remain: actuator `health,info,prometheus` are unauthenticated at the backend (not proxied by nginx, port loopback-only, reachable on the Docker network); `GET /api/doctors` exposes names and specialties to any authenticated user (needed for booking); the consent provider picker lists doctors only.
