# Full-Stack Integration Audit — Status Register

PatientManagementSystem (React frontend · Spring Boot backend · PostgreSQL).

This is the single document for **frontend ↔ backend integration**. It replaces `FULL_STACK_INTEGRATION_AUDIT.md` (Parts I–III),
`FULL_STACK_INTEGRATION_FIX_REPORT.md`, `FRONTEND_INTEGRATION_ISSUES.md` and `LABTECHNICIAN_INTEGRATION_ISSUES.md`.
Security, infrastructure and platform findings (CALYX-001 … 040) are in **`CALYX_AUDIT_REPORT.md`**; together the two files are the whole audit record.

**Last checked: 2026-10-20 against the code.** Backend 283 tests (migrations V1–V6 on PostgreSQL 16 via Testcontainers); frontend **76 suites / 420 tests**, `CI=true` production build clean.
Not verified: a browser-driven end-to-end run, GitHub Actions on GitHub, SMTP delivery.

---

## 1. Where integration stands

| Role | State |
|---|---|
| Auth | Login, 2FA (pending-token bound), refresh-on-401 (single in-flight refresh), `/auth/me` re-check on load. Access token lives in memory only; refresh cookie is HttpOnly + SameSite=Strict. |
| Patient | Booking (login-id doctors), reschedule, cancel with reason, consent grant / modify / withdraw with reason, real access log, prescriptions with refills, lab results, notifications. |
| Doctor | Chart with real vitals / records / prescriptions (403 → "Access not granted" banner), real dashboard counts, appointments with "View Patient", lab orders and abnormal-result view. |
| Nurse | Assigned patients, vitals (incl. pain, notes, route), tasks, handover notes, medication administration (persisted, audited, repeat-dose guard). |
| Lab technician | Queue, `Pending → Collected → Results Pending → Completed`, encrypted file upload and download, unit / reference range. |
| Admin | Overview, security events, user activity, system health, audit log (paged), staff invites, role change / disable / enable, care-team assignment. |

Every production page calls real endpoints. `src/mocks/*` is imported by tests and by one slide-over for descriptive consent copy (`GrantModifyConsent.jsx`); nothing clinical falls back to mock data.

---

## 2. Status of every original finding (77 issues + SECURITY-1)

Legend: **SH** shared/auth · **PT** patient · **DR** doctor · **NR** nurse · **LA** lab/admin · **DB** data model. *Fixed* = verified in the code today.

### Shared / auth
| ID | Issue | Status | Resolution |
|---|---|---|---|
| SH-1 | Access token never refreshed (logout every 15 min) | Fixed | `apiCall` refreshes once on 401; concurrent 401s share one refresh |
| SH-2 | `resend-otp` endpoint missing | Fixed | `POST /api/auth/resend-otp`, rate limited, needs the pending token |
| SH-3 | `/nurse/medications/record` missing | Fixed | See NR-4 |
| SH-4 | Four `PUT` update endpoints missing | Fixed | Prescription and medical-record updates built; the two never-called wrappers (lab result, vitals) removed |
| SH-5 | `patientAPI.delete` has no endpoint | Fixed | Wrapper removed (records are deactivated, never deleted) |
| SH-6 / SH-7 | Chart data loss; mock fallback on API failure | Fixed | `PatientDetail` uses the real modals; failures show an error with Retry |
| SH-8 | `Prescriptions_Fixed.jsx` dead file | Fixed | Deleted |
| SH-9 | Two auth clients, "supabase" naming | Accepted | Naming only. `supabaseAuth.js` is the cookie/JWT client; renaming touches many imports for no behaviour change |
| SH-10 | `REACT_APP_API_URL` default / example ports disagree | Fixed 2026-10-20 | `src/.env.example` now uses 8081 like `.env` and the compose mapping; the container build uses same-origin `/api` |
| SH-11 | Refresh cookie `Secure` hard-coded false | Fixed | `COOKIE_SECURE` toggle |
| SH-12 | Admin authorization in two places | Accepted | Consistent; informational |

### Patient
| ID | Issue | Status | Resolution |
|---|---|---|---|
| PT-1 | Reschedule returned 403 | Fixed | Patient-scoped reschedule |
| PT-2 | Cancellation reason dropped | Fixed | Stored in `cancellationReason` |
| PT-3 | `getAvailableSlots` wrapper missing | Fixed | Wrapper added; booking uses real slots |
| PT-4 | Appointment type / special requirements not sent | Fixed | Request, entity and DTO fields |
| PT-5 | Fake duration / location / room | Fixed | Removed |
| PT-6 | Consent form content wrong for every consent | Fixed 2026-10-20 | The id map matched ids real consents never have, so every slide-over showed the research form. All consents are provider data-access grants, so the health-information-exchange copy is used. |
| PT-7 | No first-time consent path | Fixed | "Grant new consent" modal (provider, category, expiry, acknowledgement) |
| PT-8 | "Next Refill: In 5 days" hard-coded | Fixed 2026-10-20 | Shows "Refills remaining" from the prescription |
| PT-9 | Dead `.active` filter | Fixed 2026-10-20 | Filters use `status === 'ACTIVE'` only |
| PT-10 | Unused `PatientProfileRequest` DTO | Open (cleanup) | Still in `dto/`; no references. Proposed: `git rm backend/src/main/java/com/securehealth/backend/dto/PatientProfileRequest.java` (deletion was not permitted in this session) |
| PT-11 | `getMe()` repeated across pages | Accepted | Works; a shared hook is refactoring, not a defect |
| PT-12 | Calendar status colours never matched | Fixed | Compares upper-case status |

### Doctor
| ID | Issue | Status | Resolution |
|---|---|---|---|
| DR-1 | Own profile loaded via the wrong id space | Fixed | `GET/PUT /api/doctors/me`; `DoctorDTO.userId` added |
| DR-2 | Profile "Save" did nothing | Fixed | Controlled form calling `updateMe` |
| DR-3 | Prescription update endpoint missing | Fixed | `PUT /api/prescriptions/{id}` |
| DR-4 | Cancel / Check-In local only | Fixed | Calls cancel / complete with visible errors |
| DR-5 / DR-6 | Chart prescriptions unsaved; identical fake vitals | Fixed | Real modals; latest real vitals with an empty state |
| DR-7 | "Pending Labs: 5" literal | Fixed 2026-10-20 | Counts orders not yet `Completed` from `GET /api/lab-results` |
| DR-8 | Fabricated "Daily Briefing" | Fixed 2026-10-20 | Real date, abnormal-result count and awaiting-results count; invented lines removed |
| DR-9 | No deep link from an appointment to the chart | Fixed 2026-10-20 | `AppointmentDTO.patientId` plus a "View Patient" button |
| DR-10 / DR-11 | Messages and Reports pages fake | Fixed | Pages and sidebar entry removed (no backend feature exists) |
| DR-12 / DR-13 | Dead file; silent mock fallback | Fixed | See SH-8, SH-7 |
| DR-14 | Medical history rendered blank | Fixed | Reads the real DTO fields; test updated |

### Nurse
| ID | Issue | Status | Resolution |
|---|---|---|---|
| NR-1 / NR-2 | Dashboard crash; dead task / handover widgets | Fixed | Dashboard rewritten on real endpoints |
| NR-3 | `nurse.username` mismatch | Fixed | Reads `nurseEmail` |
| NR-4 | Medication administration faked | Fixed | `MedicationAdministration` entity, endpoints, audit, access policy, repeat-dose guard |
| NR-5 | Route fabricated as "Oral" | Fixed | Real `route` column |
| NR-6 | Room / bed / acuity / allergies do not exist | Accepted, honest UI | No such data model. Screens now say "Not recorded" and status "unknown" instead of `N/A-N/A` and a green "stable" (fixed 2026-10-20). Adding the fields is a product decision (see §5). |
| NR-7 | `medicalHistory` labelled two ways | Accepted | Labelling only |
| NR-8 / NR-9 / NR-10 | Fake "Call Code Team", unwired cards, static counts | Fixed | Disabled with explanation / wired / removed |
| NR-11 | Nurse profile hard-coded | Fixed | One shared account page showing only server data |
| NR-12 | Duplicate `PatientDetails.jsx` | Fixed | Deleted |
| NR-13 / NR-14 | Pain level and notes dropped | Fixed | `pain_level`, `notes`, `temperature_route` columns (V4) and form fields |
| NR-15 | Weight / height not collected | Fixed | `VitalsEntry.jsx` collects them |

### Lab / admin
| ID | Issue | Status | Resolution |
|---|---|---|---|
| LA-1 / LA-2 / LA-3 | Attachments discarded; no multipart; dead "View Report" | Fixed | `filesAPI.upload` / `getObjectUrl`; `GET /api/files/{name}` decrypts for the uploader, admin and permitted users |
| LA-4 / DB-5 | Status vocabulary; empty "Collected" buckets | Fixed | `LabStatus` (forward-only), V2 migration, filters and badges for every status |
| LA-5 / LA-6 | Admin panels hid failures behind fake data | Fixed | Error states; no fallback numbers |
| LA-7 / LA-7a | Fabricated health / incidents / deltas | Fixed | Real system-health, security-events, user-activity endpoints; Incidents tab and deltas removed |
| LA-8 / LA-9 | User management buttons dead; fake status / last login | Fixed | Role change, disable / enable; real status and `lastLoginAt` |
| LA-10 | Lab / admin profiles static | Fixed | Shared account page (see NR-11) |
| LA-11 | `orderedByName` unused | Non-issue | — |

### Data model
| ID | Issue | Status | Resolution |
|---|---|---|---|
| DB-1 | Two "doctor id" spaces | Mitigated | Booking uses login ids everywhere; `/doctors/me` for self; server rejects non-doctor targets. `GET /doctors/{id}` still takes the profile id (admin use) |
| DB-2 | "doctorName" fields held e-mails | Fixed | Real names resolved from the profile |
| DB-3 / DB-4 / DB-12 | Fabricated `notes`; `recordDate` from `updatedAt`; `doctorNotes` not exposed | Fixed | Real column; `createdAt`; DTO field |
| DB-6 | No migration tool | Fixed | Flyway V1–V6, `ddl-auto=validate` |
| DB-7 / DB-8 | Boxed `Boolean archived`; `isLocked` naming | Accepted | Work correctly and are covered by tests; changing the type needs a migration for no behaviour gain |
| DB-9 | Password reset left sessions alive | Fixed | `revokeAllUserSessions` called |
| DB-10 | `LIMIT` in JPQL | Non-issue | Hibernate 6 HQL feature; exercised on PostgreSQL 16 |
| DB-11 | Dead `consent_log` table | Fixed | Not in the Flyway baseline |
| SECURITY-1 | Entities leaked `passwordHash` / `otp` | Fixed | Responses are DTOs / views; `ResponseContractTest` guards every endpoint (CALYX-001) |

---

## 3. Lab technician workflow (reference)

`GET /api/lab-technician/dashboard` · `GET …/orders` · `PUT …/orders/{id}/status` (forward-only: Pending → Collected → Results Pending → Completed) · `PUT …/orders/{id}/upload` (`{resultValue, unit, referenceRange, remarks, fileUrl}`; `fileUrl` must be a file the same technician uploaded) · `POST /api/files/upload` · `GET /api/files/{filename}`. Doctors create orders with `POST /api/lab-results`.
UI: `lab/Dashboard`, `Orders`, `OrderDetail` (Mark Sample Collected → Mark Results Pending → Upload Results), `UploadResults`, `History`. The doctor lab view filters by every status and flags a published result as abnormal when its value lies outside its reference range (`utils/labRange.js`: `4-6`, `4.5 - 11.0`, `<5`, `<=5`, `>3`, `>=3`).

## 4. Facts that supersede older documents

* Tokens: in memory only (`services/tokenStore.js`); `localStorage('authToken')` is obsolete.
* Real endpoints: `/api/vital-signs`, `/api/admin/audit-logs` (paged), `/api/prescriptions`, `/api/auth/register`.
* Nurse patient lists stay empty until an admin assigns a care team (Admin Console → Care Teams).

## 5. Open items and decisions for you

| Item | Why it is still open |
|---|---|
| PT-10 unused DTO | One-line `git rm` (command above); the session could not delete files |
| NR-6 room / bed / acuity / allergies | Needs a product decision on fields, who edits them and a migration |
| Browser end-to-end, GitHub Actions run, SMTP, Redis under load | Not executable here |

Everything else from the original audit is fixed, accepted with a reason, or a non-issue.
