# Full-Stack Frontend–Backend Integration Audit
### PatientManagementSystem (React + Spring Boot + PostgreSQL)
Audit date: 2026-09-28

This audit was performed as a coordinated, file-by-file trace of every frontend page/component against its
backend controller, service, DTO, entity and database column, across all five roles (Patient, Doctor, Nurse,
Lab Technician, Admin) plus the shared authentication/API layer and the database/entity/DTO structure itself.
Every finding below is backed by a specific file path and, where applicable, a line number/code snippet read
directly from the repository. Anything that could not be confirmed by static reading alone is explicitly
labeled **UNVERIFIED / NEEDS RUNTIME TEST** rather than presented as a confirmed bug.

---

## 1. Executive Summary

**Scope audited (all read in full or near-full):**
- **Frontend:** `frontend/app/src/` — 9 patient pages, 10 doctor pages + 11 doctor components, 26 nurse pages/components, 6 lab pages, 9 admin pages/components, the shared `services/api.js` (616 lines), `services/supabaseAuth.js`, `contexts/AuthContext.jsx`, `components/auth/ProtectedRoute.jsx`, `App.jsx`, `login.jsx`. ≈ 90+ source files traced end-to-end for API integration; the remaining files under `frontend/app/src` (≈193 total .js/.jsx incl. tests) are test files or pure-presentational leaves not independently re-audited.
- **Backend:** All 15 controllers, all 22 DTOs, all 19 entity models, all 17 repositories, all 19 services, and the global exception handler — 100% of `backend/Backend/src/main/java/com/securehealth/backend/{controller,dto,model,repository,service,exception}` (130 `.java` files) read in full or targeted-in-full for this audit.
- **Database:** `DB/schema.sql`, `DB/seed_users.sql`, `DB/DB_README.md`, `application.properties`.
- **Existing docs cross-checked for staleness:** `API_README.md`, `FRONTEND_INTEGRATION_ISSUES.md`, `PR_SUMMARY.md`, `VITALS_FIX_SUMMARY.md`, `LABTECHNICIAN_INTEGRATION_ISSUES.md`.

**Total confirmed issues: 77** (1 false-alarm item explicitly ruled out and excluded from this count — see LA-11).

| Severity | Count |
|---|---|
| 🔴 CRITICAL | 1 |
| 🟠 HIGH | 17 |
| 🟡 MEDIUM | 29 |
| 🟢 LOW | 30 |

**By category (approximate, an issue may span categories):**
- Missing backend APIs / dead frontend calls to non-existent endpoints: **13**
- Hardcoded/mock data presented as real: **~22**
- Request/response DTO field mismatches: **~14**
- Authentication / current-user / session issues: **4**
- Backend endpoints implemented but never called by any frontend page ("dead API surface"): **~18** distinct endpoints
- Correctly-built frontend components that are never wired into any route ("dead but correct" code): **6** (`PrescriptionModal.jsx`, `MedicalRecordModal.jsx`, `VitalSignModal.jsx`, `VitalsChart.jsx`, `AvailableSlotSelector.jsx`, `PatientDetails.jsx` [nurse])

**Headline finding:** the single biggest pattern across all five roles is not "the backend is missing features" — in most cases the backend endpoint, DTO, and even a correctly-wired frontend component *already exist* — but that the **routed page currently live in the app reimplements the same feature independently, badly, and without calling the working code**. This shows up most starkly in the Doctor role (prescriptions, vitals, medical records, appointment completion) and the Nurse role (medication administration, task/handover widgets duplicated between `Dashboard.jsx` and the correct `Tasks.jsx`/`ShiftHandover.jsx`).

---

## 2. Critical Findings

Ordered most-severe first. Only 🔴 CRITICAL and 🟠 HIGH findings are given the full template; 🟡 MEDIUM/🟢 LOW findings are documented in the Complete Bug Inventory (§3) and the topic-specific reports (§4–§13) with equivalent evidence.

### BUG-001 (LA-1) — Lab result file attachments are silently discarded; "View Report" is permanently dead
**Severity:** 🔴 CRITICAL
**Category:** Broken data flow / file upload
**File:** [frontend/app/src/pages/lab/UploadResults.jsx](PatientManagementSystem/frontend/app/src/pages/lab/UploadResults.jsx) lines 10, 32-36, 53-58
**Problem:** `handleFileChange` captures a real `File` object and shows a "file selected" preview, but `handleSubmit` calls `api.labTechnician.uploadResults(selectedOrder, testValues, remarks, null)` — the 4th argument (`fileUrl`) is hardcoded `null`. The selected file is never sent anywhere.
**Evidence:**
```javascript
await api.labTechnician.uploadResults(
    selectedOrder, testValues.trim(), remarks.trim() || null,
    null  // fileUrl: Not supported yet - requires separate file upload endpoint on backend
);
```
The comment is stale: `POST /api/files/upload` (`FileUploadController.uploadFile`, AES-256-GCM at rest, backed by a dedicated `UploadedFile` entity and `LabTestRepository.findByFileUrl`) **already exists and works**, but `frontend/app/src/services/api.js` has **no `filesAPI` wrapper at all**, so nothing in the frontend can call it.
**Root Cause:** Frontend file-upload wiring was never completed after the backend endpoint was built; the stale code comment misled whoever last touched this file into thinking it was still blocked on the backend.
**Impact:** No lab technician can ever attach a result file to a test in production. `OrderDetail.jsx`/`History.jsx`'s "View Report" links (`<a href={order.fileUrl}>`) are permanently dead since `fileUrl` never gets set (BUG-003/LA-3).
**Recommended Fix:** Add `filesAPI.upload(file)` to `api.js` (multipart `FormData` POST to `/api/files/upload`), call it first in `UploadResults.jsx`'s `handleSubmit`, then pass the returned filename as `fileUrl` to `uploadResults()`. Requires BUG-002 to be fixed first.
**Files needing modification:** `frontend/app/src/services/api.js`, `frontend/app/src/pages/lab/UploadResults.jsx`

---

### BUG-002 (LA-2) — Shared `apiCall` helper cannot send multipart requests at all
**Severity:** 🟠 HIGH
**Category:** Frontend infrastructure gap
**File:** [frontend/app/src/services/api.js](PatientManagementSystem/frontend/app/src/services/api.js) lines 18-21
**Problem:** `apiCall()` unconditionally sets `'Content-Type': 'application/json'` and every wrapper does `JSON.stringify(body)`. There is no path to send a `FormData` body (required by `FileUploadController.uploadFile`'s `@RequestParam("file") MultipartFile file`).
**Root Cause:** The client was built JSON-only; file upload was never designed in.
**Recommended Fix:** In `apiCall`, skip setting `Content-Type` when `options.body instanceof FormData` (the browser will set the correct multipart boundary itself).

---

### BUG-003 (SH-1) — Access token is never refreshed; users are force-logged-out every 15 minutes
**Severity:** 🟠 HIGH
**Category:** Auth / session management
**File:** [frontend/app/src/services/api.js](PatientManagementSystem/frontend/app/src/services/api.js) lines 34-53; [frontend/app/src/services/supabaseAuth.js](PatientManagementSystem/frontend/app/src/services/supabaseAuth.js) (entire file)
**Backend:** `AuthController.java` lines 769-810 (`POST /api/auth/refresh-token`), `application.properties` line 42 (`jwt.expiration=900000`, 15 min)
**Problem:** The backend issues a 15-minute access token plus a 7-day HttpOnly refresh cookie and exposes a working refresh endpoint. No frontend code ever calls it. Every `401` is treated by `apiCall` as "session expired" — `localStorage` is cleared and the user is redirected to `/login`.
**Evidence:**
```js
if (response.status === 401) {
   console.warn('Session expired. Redirecting to login...');
   localStorage.removeItem('secure_health_user');
   window.location.href = '/login';
}
```
**Impact:** Every logged-in user of every role is silently kicked back to the login screen every 15 minutes of API activity, despite holding a valid 7-day refresh cookie.
**Recommended Fix:** On a 401, attempt `POST /api/auth/refresh-token` once (cookie sent automatically via `credentials:'include'`), retry the original request with the new token, and only redirect to `/login` if the refresh call itself fails.

---

### BUG-004 (SH-2) — `resendOtp` calls a backend endpoint that does not exist
**Severity:** 🟠 HIGH
**Category:** API contract mismatch
**File:** [frontend/app/src/services/supabaseAuth.js](PatientManagementSystem/frontend/app/src/services/supabaseAuth.js) lines 326-343, consumed by `pages/TwoFactorAuth.jsx` line 8
**Problem:** `resendOtp` calls `POST /api/auth/resend-otp`. `AuthController.java` (full file read) has no such mapping — only `/me, /register, /login, /verify-otp, /logout, /enable-2fa, /forgot-password, /validate-reset-token, /reset-password, /refresh-token`.
**Recommended Fix:** Implement `POST /api/auth/resend-otp` server-side (re-trigger the same OTP-send logic used in `login`), or disable the resend button with a "not yet supported" state.

---

### BUG-005 (SH-4) — Four generic `PUT .../{id}` "update" API calls target endpoints that don't exist
**Severity:** 🟠 HIGH
**Category:** API contract mismatch
**File:** [frontend/app/src/services/api.js](PatientManagementSystem/frontend/app/src/services/api.js) — `medicalRecordAPI.update` (257-262), `prescriptionAPI.update` (300-305), `labResultAPI.update` (350-355), `vitalSignsAPI.update` (435-440)
**Problem:** None of `MedicalRecordController`, `LabResultController`, `VitalSignController` implements any `PUT` mapping at all; `PrescriptionController` only implements `PUT /{id}/refill`, not a generic `PUT /{id}`. All four frontend "update" functions will 404/405 if ever called.
**Impact:** This is the root backend cause behind DR-3 (doctor's "Manage Prescription" always fails).
**Recommended Fix:** Add the missing `@PutMapping("/{id}")` handlers, or remove/guard the corresponding frontend calls.

---

### BUG-006 (DR-1) — Doctor's own profile is fetched using the wrong ID space
**Severity:** 🔴 CRITICAL (data-integrity — a doctor can load a *different* doctor's identity)
**Category:** ID/data flow mismatch
**File:** [frontend/app/src/pages/doctor/Profile.jsx](PatientManagementSystem/frontend/app/src/pages/doctor/Profile.jsx) lines 17-20
**Backend:** `DoctorController.java` lines 58-61 → `DoctorService.getDoctorById` → `doctorProfileRepository.findById(id)` (keyed on `doctor_profiles.profile_id`)
**Problem:** `Profile.jsx` passes `user.userId` (the logged-in doctor's `Login.user_id`) as the ID to `GET /api/doctors/{id}`, but that endpoint expects `DoctorProfile.profile_id` — an independently auto-incremented sequence. `DB-1` (database audit) confirms this same ID-space collision exists structurally across `AppointmentService`/`DoctorService`. Once more than one non-doctor account exists ahead of a doctor in the shared `login` sequence, these two IDs diverge.
**Impact:** A doctor viewing "My Profile" can see **another doctor's** name, specialty, department, and schedule, or get a 404. This is the most severe integration bug found in the entire audit — it is a data-integrity/identity bug in a healthcare app, not merely a missing feature.
**Recommended Fix:** Add `GET /api/doctors/me` (resolves via JWT identity like every other `/me` endpoint in the app) and use it instead of guessing an ID space; standardize doctor-booking endpoints on `Login.userId` per `DB-1`'s recommendation.

---

### BUG-007 (DR-3) — Doctor "Manage Prescription → Update" always fails silently
**Severity:** 🟠 HIGH
**Category:** Missing API / dead write path
**File:** [frontend/app/src/pages/doctor/Prescriptions.jsx](PatientManagementSystem/frontend/app/src/pages/doctor/Prescriptions.jsx) line 142
**Problem:** Calls `api.prescriptions.update(id, editRxData)` → `PUT /prescriptions/{id}`, which per BUG-005 does not exist. The UI optimistically updates local state regardless, so the doctor believes the edit succeeded; a page refresh reverts it.
**Recommended Fix:** Add `PUT /api/prescriptions/{id}` (doctor-only, ownership-checked, like `refillPrescription`) for dosage/frequency/status edits.

---

### BUG-008 (DR-4) — Doctor appointment Cancel/Check-In are local-state only; never persisted
**Severity:** 🟠 HIGH
**Category:** Missing API wiring — patient-safety relevant
**File:** [frontend/app/src/pages/doctor/Appointments.jsx](PatientManagementSystem/frontend/app/src/pages/doctor/Appointments.jsx) lines 89-104
**Problem:** `confirmCancel()` and `handleComplete()` only call `setAppointments(...)`. The working backend endpoints (`PUT /appointments/{id}/cancel`, `PUT /appointments/{id}/complete`) are never called; `api.js` doesn't even wrap `.complete()`.
**Impact:** A doctor "cancelling" or "checking in" a patient sees the UI update, but the appointment is unchanged in the database — a refresh reverts it. A cancelled appointment is not actually cancelled.
**Recommended Fix:** Call `api.appointments.cancel(appt.id)` in `confirmCancel`; add and call an `appointmentAPI.complete(id)` wrapper.

---

### BUG-009 (DR-5) — Prescriptions/treatments entered from the primary Patient Detail screen are never saved
**Severity:** 🟠 HIGH
**Category:** Missing API wiring / data loss — the most patient-safety-critical finding in the audit
**File:** [frontend/app/src/pages/doctor/PatientDetail.jsx](PatientManagementSystem/frontend/app/src/pages/doctor/PatientDetail.jsx) lines 94-133
**Problem:** `handleAddRx`/`handleRenewRx` build a local prescription object with hardcoded `prescribedBy: 'Dr. Smith'` and only call `setPrescriptions([rx, ...prescriptions])` — **`api.prescriptions.create` is never called**, even though it works correctly and is used by the also-unused `components/doctor/PrescriptionModal.jsx` (see §13, dead-but-correct code).
**Impact:** A doctor can "prescribe medication" from a patient's chart, see it appear, and it does not exist in the database. This is the highest patient-safety-risk finding in this audit.
**Recommended Fix:** Replace the inline form with the already-correctly-built `PrescriptionModal.jsx`, wired to `api.prescriptions.create`; remove the hardcoded `'Dr. Smith'`.

---

### BUG-010 (DR-14) — Medical history renders blank for all real data (field-name mismatch)
**Severity:** 🟠 HIGH
**Category:** DTO/frontend field mismatch
**File:** [frontend/app/src/components/doctor/MedicalHistoryList.jsx](PatientManagementSystem/frontend/app/src/components/doctor/MedicalHistoryList.jsx) lines 26, 29; `pages/doctor/PatientDetail.jsx` line 215
**Problem:** Components read `record.type`/`record.note`. `MedicalRecordDTO` has `diagnosis, symptoms, treatmentProvided, recordDate` — no `type`/`note` field exists. `PatientDetail.jsx` stores the API response with zero field mapping (`setMedicalHistory(historyData)`).
**Impact:** Every medical-history entry a doctor views will show blank/`undefined` diagnosis and notes once real (non-mock) data is returned — this bug is invisible today only because `PatientDetail.jsx` silently falls back to mock data on any fetch hiccup (DR-13).
**Recommended Fix:** Map `{id: r.recordId, date: r.recordDate, type: r.diagnosis, note: `${r.symptoms} — ${r.treatmentProvided}`}` before storing, or update the components to read the real DTO field names directly.

---

### BUG-011 (NR-1) — Nurse dashboard crashes the first time "For Next Shift" is opened
**Severity:** 🟠 HIGH
**Category:** Runtime crash risk
**File:** [frontend/app/src/pages/nurse/Dashboard.jsx](PatientManagementSystem/frontend/app/src/pages/nurse/Dashboard.jsx) lines 119-154 (initial state), line 795
**Problem:** `overview` state is initialized without an `assignedPatients` key at all. `overview.assignedPatients.map(...)` at line 795 throws `TypeError: Cannot read properties of undefined (reading 'map')` on first click.
**Recommended Fix:** Add `assignedPatients: []` to initial state and fetch via `api.nurse.getAssignedPatients()`.

---

### BUG-012 (NR-2) — Nurse Dashboard's Tasks/Handover widgets are dead duplicate UI (never call the backend)
**Severity:** 🟠 HIGH
**Category:** Missing integration (dead feature) — confusing given the *same* backend calls work correctly elsewhere
**File:** [frontend/app/src/pages/nurse/Dashboard.jsx](PatientManagementSystem/frontend/app/src/pages/nurse/Dashboard.jsx) lines 145-153, 257-362
**Problem:** `handleTaskToggle`, `handleMarkNoteRead`, `handleSaveHandover`, `handleMarkAllNotesRead` all contain literal `// TODO` comments and only mutate local state. `overview.tasks`/`overview.handoverNotes` are never populated from `api.nurse.getTasks()`/`getHandoverNotes()` anywhere in the file. Meanwhile the separately-routed `Tasks.jsx`/`ShiftHandover.jsx` pages implement the *identical* feature correctly against the *same* endpoints.
**Recommended Fix:** Wire Dashboard's widgets to the same `api.nurse.*` calls, or remove the duplicated inline UI and deep-link to those pages instead.

---

### BUG-013 (NR-4) — Medication administration is entirely faked; no backend endpoint exists
**Severity:** 🟠 HIGH
**Category:** Missing backend / false-positive success state — patient-safety relevant
**File:** [frontend/app/src/pages/nurse/MedicationAdministration.jsx](PatientManagementSystem/frontend/app/src/pages/nurse/MedicationAdministration.jsx) lines 86-100
**Problem:** `confirmAdministration()` never calls any API — the source comment admits it: `// Update UI optimistically — no dedicated backend recording endpoint exists`. `api.js`'s `nurseAPI.recordMedicationAdministration` posts to `/nurse/medications/record`, a route that exists nowhere in `NurseController.java` (confirmed independently by the shared-infra audit as well, SH-3).
**Impact:** A nurse believes a medication was "administered & signed"; nothing is persisted. A page refresh loses the record entirely — a genuine patient-safety false-positive in a hospital medication-administration workflow.
**Recommended Fix:** Add a `MedicationAdministration` entity/table (prescriptionId, patientId, nurseId, administeredAt, notes) + `POST /api/nurse/medications/{prescriptionId}/administer`, and wire the frontend to call it with a real error path.

---

### BUG-014 (SH-6, DR-6 duplicate root cause) — Vitals are entirely absent from the doctor's real workflow
**Severity:** 🟠 HIGH
**Category:** Hardcoded data / dead correct components
**File:** [frontend/app/src/pages/doctor/PatientDetail.jsx](PatientManagementSystem/frontend/app/src/pages/doctor/PatientDetail.jsx) lines 192-203
**Problem:** Every patient's "Vitals & Condition" card hardcodes `120/80`, `72 bpm`, `70 kg` regardless of real data, even though `GET /api/vital-signs/patient/{id}/latest` works and is DOCTOR-authorized, and a fully correct, ready-to-use `components/doctor/VitalSignModal.jsx` + `VitalsChart.jsx` already exist and are simply never imported anywhere.
**Impact:** Every patient shows identical, fake vital signs — clinically misleading.
**Recommended Fix:** Fetch `api.vitalSigns.getLatest(id)` in `PatientDetail.jsx`'s data-loading effect; add a "Record Vitals" button wired to `VitalSignModal`; render `VitalsChart` from `getByPatient`.

---

### BUG-015 (PT-1) — Patient appointment reschedule is rejected by the backend (403)
**Severity:** 🟠 HIGH
**Category:** Broken workflow / authorization mismatch
**File:** [frontend/app/src/pages/patient/Appointments.jsx](PatientManagementSystem/frontend/app/src/pages/patient/Appointments.jsx) lines 318-344
**Problem:** The only way a patient can change an appointment's time is `api.appointments.update(id, {appointmentDate})` → `PUT /api/appointments/{id}`, which is `@PreAuthorize("hasAuthority('DOCTOR')")`. A patient calling this always gets `403 Forbidden`.
**Recommended Fix:** Add a patient-scoped `PUT /api/appointments/{id}/reschedule` (date-only, ownership-checked), or extend `updateAppointment`'s authorization with a restricted field set for `PATIENT`.

---

### BUG-016 (PT-2) — Cancellation reason is collected but silently dropped by the backend
**Severity:** 🟠 HIGH
**Category:** Data loss
**File:** [frontend/app/src/pages/patient/Appointments.jsx](PatientManagementSystem/frontend/app/src/pages/patient/Appointments.jsx) line 276; `AppointmentController.cancelAppointment` (backend)
**Problem:** The frontend sends `{status:'CANCELLED', reason}` in the cancel body, but `cancelAppointment(@PathVariable Long id, Authentication auth)` has **no `@RequestBody` parameter at all**, and `Appointment.java` has no `cancellationReason` column even if it were read. The patient sees a "Confirmation email sent" success message; the reason is never stored.
**Recommended Fix:** Add `@RequestBody(required=false)` to `cancelAppointment`, add a `cancellationReason` column, and persist it.

---

### BUG-017 (DB-9) — Password reset does not revoke existing sessions
**Severity:** 🟠 HIGH (security)
**Category:** Silent no-op — security-relevant
**File:** [backend/Backend/src/main/java/com/securehealth/backend/service/AuthService.java](PatientManagementSystem/backend/Backend/src/main/java/com/securehealth/backend/service/AuthService.java) lines 539-553
**Problem:** `invalidateAllUserSessions()`'s Javadoc claims it "forces re-authentication on all devices" after a password reset, but the method body is empty — only a comment. `SessionRepository.revokeAllUserSessions(userId)` already exists and is used elsewhere (`refreshToken`), so the fix is one line.
**Impact:** A stolen/compromised session remains valid even after the legitimate user resets their password specifically to invalidate it.
**Recommended Fix:** `sessionRepository.revokeAllUserSessions(user.getUserId());` in the empty method body.

---

### BUG-018 (DB-2) — "Doctor name" fields across 5+ DTOs are actually the doctor's email
**Severity:** 🟠 HIGH
**Category:** Data correctness — broad, user-facing
**File:** `AppointmentService.java` (toDTO + 3 list methods), `MedicalRecordService.java` L81, `PrescriptionService.java` L91/119, `LabTestService.java` L71/95/113, `LabTechnicianService.java` L128
**Problem:** Every place that populates a "doctor name" field does `doctor.getEmail()` (the `Login` entity has no name field). `AppointmentDTO.doctorName`, `MedicalRecordDTO.doctorName`, `PrescriptionDTO.doctorName`, `LabTestDTO.orderedByName`/`orderedByDoctor` are all populated with an email address wherever the code claims to show a name.
**Impact:** Any frontend page rendering these fields as-is shows raw email addresses where "Dr. Firstname Lastname" is expected (confirmed independently in DR data-flow matrix: `Prescriptions.jsx` line 225 renders `rx.doctorName` and gets an email).
**Recommended Fix:** In each mapping method, resolve `DoctorProfileRepository.findByUser_UserId(doctor.getUserId())` and set the name from `firstName + " " + lastName`, falling back to email only if no profile exists.

---

## 3. Complete Bug Inventory

Legend: **SH** = Shared Infra/Auth, **PT** = Patient, **DR** = Doctor, **NR** = Nurse, **LA** = Lab/Admin, **DB** = Database/Backend structural.

| ID | Severity | Category | Frontend File | Backend File/API | Problem | Status |
|---|---|---|---|---|---|---|
| SH-1 (BUG-003) | 🟠 | Auth/session | `api.js`, `supabaseAuth.js` | `AuthController.refreshToken` (unused) | Users force-logged-out every 15 min; refresh token never used | Confirmed |
| SH-2 (BUG-004) | 🟠 | API mismatch | `supabaseAuth.resendOtp` | none | `POST /api/auth/resend-otp` doesn't exist | Confirmed |
| SH-3 | 🟡 | API mismatch | `api.js` `nurseAPI.recordMedicationAdministration` | none | `/nurse/medications/record` doesn't exist (=NR-4 root cause) | Confirmed |
| SH-4 (BUG-005) | 🟠 | API mismatch | `api.js` 4 `.update()` fns | `MedicalRecordController`/`PrescriptionController`/`LabResultController`/`VitalSignController` | No PUT endpoint (only `/refill` PUT) | Confirmed |
| SH-5 | 🟡 | API mismatch | `api.js patientAPI.delete` | `PatientController` | No DELETE endpoint (explicitly deferred by comment) | Confirmed |
| SH-6 (=DR-5/6) | 🟠 | Data loss | `pages/doctor/PatientDetail.jsx` | n/a | Prescriptions/treatments never persisted | Confirmed (dup of DR-5) |
| SH-7 | 🟡 | Mock/prod boundary | `pages/doctor/PatientDetail.jsx`, `Messages.jsx`, `Prescriptions_Fixed.jsx` | n/a | Production pages silently fall back to mock data on API failure | Confirmed |
| SH-8 | 🟢 | Dead code | `pages/doctor/Prescriptions_Fixed.jsx` | n/a | Unrouted dead file duplicating `Prescriptions.jsx` | Confirmed |
| SH-9 | 🟢 | Maintainability | `AuthContext.jsx`, `supabaseAuth.js`, `api.js authAPI` | n/a | Two parallel, divergent auth client implementations; misleading "supabase" naming | Confirmed |
| SH-10 | 🟡 | Env/config | `api.js`, `supabaseAuth.js` | n/a | `REACT_APP_API_URL` has no safe default (`''`); 3 example files disagree on port (8080 vs 8081) | Confirmed |
| SH-11 | 🟢 | Security config | `AuthController.java` (4 sites) | n/a | Refresh cookie `Secure` hardcoded `false`, no env toggle | Confirmed |
| SH-12 | 🟢 | Security config | `SecurityConfig.java`, `AuditLogController.java` | n/a | Admin authorization split across 2 independently-maintained mechanisms (currently consistent, fragile) | Confirmed |
| PT-1 (BUG-015) | 🟠 | Authorization | `pages/patient/Appointments.jsx` | `AppointmentController.updateAppointment` | Patient reschedule → 403 (DOCTOR-only endpoint reused) | Confirmed |
| PT-2 (BUG-016) | 🟠 | Data loss | `pages/patient/Appointments.jsx` | `AppointmentController.cancelAppointment` | Cancellation reason sent, never read/stored | Confirmed |
| PT-3 | 🟡 | Missing wrapper | `components/appointments/AvailableSlotSelector.jsx` | `AppointmentController.getAvailableSlots` (exists, unused) | `api.appointments.getAvailableSlots` doesn't exist in `api.js`; falls back to fake slots | Confirmed |
| PT-4 | 🟡 | Data loss | `pages/patient/Appointments.jsx` | `AppointmentRequest` DTO | Appointment "type"/"special requirements" collected, never sent | Confirmed |
| PT-5 | 🟡 | DTO/UI mismatch | `pages/patient/Appointments.jsx` | `AppointmentDTO` | Cards render `duration`/`location`/`room` — none exist on DTO, always `undefined`/fallback | Confirmed |
| PT-6 | 🟡 | Logic bug | `pages/patient/GrantModifyConsent.jsx`, `ConsentManagement.jsx` | n/a | Consent category lookup broken — always shows wrong mock consent-form content; signature stored into `reason` field | Confirmed |
| PT-7 | 🟡 | Missing workflow | `pages/patient/ConsentManagement.jsx` | `ConsentController.grantConsent` (exists, unreachable) | No UI path to grant a first-time consent to a new provider | Confirmed |
| PT-8 | 🟢 | Hardcoded data | `pages/patient/Prescriptions.jsx` | `PrescriptionDTO.endDate` (unused) | "Next Refill: In 5 days" hardcoded for every prescription | Confirmed |
| PT-9 | 🟢 | DTO mismatch (benign) | `pages/patient/Dashboard.jsx`, `Prescriptions.jsx` | `PrescriptionDTO` | Dead `.active` filter checks (no such field; falls back to `.status` correctly) | Confirmed |
| PT-10 | 🟢 | Backend dead code | `dto/PatientProfileRequest.java` | n/a | Unused DTO; `PUT /patients/{id}` instead requires the full `PatientDTO` with all required fields | Confirmed |
| PT-11 | 🟢 | Design smell | 6 patient pages | n/a | `getMe()` pattern duplicated 6× instead of a shared hook | Confirmed |
| PT-12 | 🟡 | Status mismatch | `pages/patient/Appointments.jsx` | `AppointmentStatus` enum | Calendar view compares title-case strings (`'Confirmed'`) against uppercase `statusRaw` — never matches, mini-badges always wrong color | Confirmed (found during file-by-file pass) |
| DR-1 (BUG-006) | 🔴 | ID mismatch | `pages/doctor/Profile.jsx` | `DoctorController.getDoctorById` | Wrong ID space — doctor can load another doctor's profile | Confirmed |
| DR-2 | 🟠 | Broken write path | `pages/doctor/Profile.jsx` | `DoctorController.updateDoctorProfile` (exists, unused) | "Save Changes" only toggles UI state, never calls the API | Confirmed |
| DR-3 (BUG-007) | 🟠 | Missing API | `pages/doctor/Prescriptions.jsx` | none | "Manage Prescription" update hits nonexistent endpoint | Confirmed |
| DR-4 (BUG-008) | 🟠 | Missing wiring | `pages/doctor/Appointments.jsx` | `AppointmentController.cancel`/`complete` (exist, unused) | Cancel/Check-In are local-state only | Confirmed |
| DR-5 (BUG-009) | 🟠 | Data loss | `pages/doctor/PatientDetail.jsx` | `PrescriptionController.createPrescription` (exists, unused here) | Prescriptions from chart never persisted | Confirmed |
| DR-6 (BUG-014) | 🟠 | Hardcoded data | `pages/doctor/PatientDetail.jsx` | `VitalSignController` (exists, unused here) | Every patient shows identical fake vitals | Confirmed |
| DR-7 | 🟡 | Hardcoded data | `pages/doctor/Dashboard.jsx` | `LabResultController.getPending` (exists, unused) | "Pending Labs: 5" hardcoded literal | Confirmed |
| DR-8 | 🟡 | Hardcoded data | `pages/doctor/Dashboard.jsx` | n/a | "Daily Briefing" panel fully fabricated | Confirmed |
| DR-9 | 🟡 | DTO gap | `AppointmentDTO.java` | n/a | No `patientId` on appointments — no deep-link doctor→patient chart | Confirmed |
| DR-10 | 🟡 | Missing backend | `pages/doctor/Messages.jsx` | none (no messaging feature exists) | 100% mock conversation; Send button non-functional | Confirmed |
| DR-11 | 🟡 | Missing backend | `pages/doctor/Reports.jsx` | none | Fake client-side report "generation"; Download button dead | Confirmed |
| DR-12 | 🟢 | Dead code / broken call | `pages/doctor/Prescriptions_Fixed.jsx` | none | Calls `api.prescriptions.getAll()`, which doesn't exist; unrouted dead file | Confirmed |
| DR-13 | 🟢 | Error handling | `pages/doctor/PatientDetail.jsx` | n/a | Silently falls back to mock clinical data on ANY fetch error, no visible warning | Confirmed |
| DR-14 (BUG-010) | 🟠 | DTO/frontend mismatch | `components/doctor/MedicalHistoryList.jsx` | `MedicalRecordDTO` | Reads `type`/`note`; DTO has `diagnosis`/`symptoms`/`treatmentProvided` — real data renders blank | Confirmed |
| NR-1 (BUG-011) | 🟠 | Crash risk | `pages/nurse/Dashboard.jsx` | n/a | `overview.assignedPatients` undefined → `.map` throws | Confirmed |
| NR-2 (BUG-012) | 🟠 | Dead feature | `pages/nurse/Dashboard.jsx` | `NurseController` task/handover endpoints (exist, unused here) | Tasks/Handover widgets are local-only, marked with `// TODO` | Confirmed |
| NR-3 | 🟡 | Field mismatch | `pages/nurse/PatientDetails.jsx`, `VitalsEntry.jsx` | `VitalSignDTO.nurseEmail` | Reads `nurse.username` (nested object that doesn't exist); should read flat `nurseEmail` | Confirmed |
| NR-4 (BUG-013) | 🟠 | Missing backend | `pages/nurse/MedicationAdministration.jsx` | none | Medication administration never persisted; fake success | Confirmed |
| NR-5 | 🟡 | Incorrect DTO usage | `pages/nurse/MedicationAdministration.jsx` | `PrescriptionDTO` | `route` always fabricated "Oral"; `status`/`administeredTime` fields don't exist, checks always false | Confirmed |
| NR-6 | 🟡 | Hardcoded fallback / data-model gap | `Patients.jsx`, `PatientDetail.jsx`, `Vitals.jsx` | `PatientProfile` entity | No room/bed/acuity/vitals-status/med-status/code-status/allergies fields exist at all — entire triage UI is decorative | Confirmed |
| NR-7 | 🟢 | Semantic mismatch | `Patients.jsx`, `PatientDetail.jsx` | `PatientProfile.medicalHistory` | Same free-text field relabeled "diagnosis" in one place, "care instructions" in another | Confirmed |
| NR-8 | 🟢 | Non-functional UI | `pages/nurse/Dashboard.jsx` | none | "Call Code Team" emergency button is a literal `alert()` placeholder | Confirmed |
| NR-9 | 🟢 | Unwired UI | `pages/nurse/Dashboard.jsx` | n/a | 4 Shift Snapshot stat cards styled as clickable, `onClick: () => {}` | Confirmed |
| NR-10 | 🟢 | Hardcoded data | `pages/nurse/Dashboard.jsx` | n/a | Task-category counts, "12 completed", "3 unread notes" all static, contradict the (always-empty) real lists below them | Confirmed |
| NR-11 (=BUG in inventory) | 🟡 | 100% mock page | `pages/nurse/Profile.jsx` | none | Entire nurse profile hardcoded; Edit/Save discards changes silently | Confirmed |
| NR-12 | 🟢 | Dead/duplicate | `pages/nurse/PatientDetails.jsx` | n/a | Imported in `App.jsx` but never routed; whole duplicate patient-detail screen | Confirmed |
| NR-13 | 🟢 | Fake persisted text | `components/VitalsEntryForm.jsx` | `VitalSignRequest` | UI claims notes are "saved to the handover log"; DTO has no `notes`/`painLevel` field | Confirmed |
| NR-14 | 🟢 | Payload dropped | `pages/nurse/Vitals.jsx` | `VitalSignRequest` | Pain-level slider captured, never sent (no DTO field) | Confirmed |
| NR-15 | 🟢 | Backend ahead of frontend | nurse vitals forms | `VitalSignRequest.weight/height` | Backend supports weight/height; no nurse form collects them | Confirmed |
| LA-1 (BUG-001) | 🔴 | Broken file upload | `pages/lab/UploadResults.jsx` | `FileUploadController` (exists, unused) | `fileUrl` always `null`; files discarded | Confirmed |
| LA-2 (BUG-002) | 🟠 | Infra gap | `api.js` | `FileUploadController.uploadFile` | `apiCall` cannot send multipart | Confirmed |
| LA-3 | 🟡 | Dead UI | `pages/lab/OrderDetail.jsx`, `History.jsx` | `LabTestDTO.fileUrl` | "View Report" links permanently dead (consequence of LA-1); plain `<a href>` likely can't carry the bearer token anyway | Confirmed (+ UNVERIFIED auth detail) |
| LA-4 | 🟡 | Status enum gap | `pages/lab/Orders.jsx`, `OrderDetail.jsx` | `LabTechnicianService.updateOrderStatus` (no validation) | Status filter/badges omit `Processing`/`Cancelled`; backend accepts any string | Confirmed |
| LA-5 | 🟡 | Mock fallback masks failures | `admin/components/AuditLogs.jsx`, `CompliancePanel.jsx` | `AuditLogController` | Silent fabricated fallback data on API failure, no visible error | Confirmed |
| LA-6 | 🟡 | Mock fallback masks failures | `admin/components/SystemOverview.jsx` | `AdminController.getMetrics` | Same pattern — hardcoded `{5,2,3,5}` on failure | Confirmed |
| LA-7 | 🟢 | 100% fabricated, no API at all | `SystemHealth.jsx`, `IncidentManagement.jsx`, parts of `SystemOverview.jsx`/`CompliancePanel.jsx` | none exists | Server health/incidents/compliance-score/device-usage entirely fictional | Confirmed |
| LA-7a | 🟢 | Fabricated trend deltas | `admin/components/SystemOverview.jsx` | none | Every stat card's "+12%/-5%" change indicator is fake, no historical backend support | Confirmed |
| LA-8 | 🟢 | Dead UI wired to working backend | `admin/components/UserManagement.jsx` | `AdminController.updateStaffRole`/`removeStaffMember` (exist, unused) | Edit/More-options buttons have no `onClick` at all | Confirmed |
| LA-9 | 🟢 | Hardcoded data | `admin/components/UserManagement.jsx` | `StaffDTO` (no such fields) | "Status"/"Last Login" columns always fake, even on the happy path | Confirmed |
| LA-10 | 🟢 | Missing backend | `pages/lab/Profile.jsx`, `pages/admin/Profile.jsx` | none | Both profile pages 100% static; Save Changes doesn't persist | Confirmed |
| LA-11 | — | False alarm (excluded from count) | n/a | `LabTestDTO.orderedByName` | Dead DTO field, never read by any lab/admin frontend file — no user impact | Verified non-issue |
| DB-1 | 🟠 | ID ambiguity | `DoctorService.java`, `AppointmentService.java` | `DoctorDTO.id` vs booking `doctorId` | Two independent PK sequences both called "doctor id" (root cause of DR-1) | Confirmed |
| DB-2 (BUG-018) | 🟠 | Data correctness | 5 services | `AppointmentDTO`/`MedicalRecordDTO`/`PrescriptionDTO`/`LabTestDTO` `.doctorName`/`orderedByDoctor` | Always populated with email, never real name | Confirmed |
| DB-3 | 🟡 | Fabricated field | `MedicalRecordService.java` | `MedicalRecordDTO.notes` | Always a duplicate copy of `symptoms`, not real independent data | Confirmed |
| DB-4 | 🟡 | Wrong source field | `MedicalRecordService.java` | `MedicalRecordDTO.recordDate` | Bound to `updatedAt`, drifts forward on every edit instead of reflecting encounter date | Confirmed |
| DB-5 | 🟡 | Data completeness | `LabTechnicianService.java`, `LabTestService.java` | Lab dashboard status buckets | "Collected"/"Results Pending" buckets always read 0 — no code path ever sets those strings; no `LabTestStatus` enum | Confirmed |
| DB-6 | 🟡 | Schema drift risk | `application.properties`, `DB/schema.sql` | n/a | `ddl-auto=update` + hand-maintained `schema.sql`, no Flyway/Liquibase; dead `otp_secret` column and unused Postgres ENUM types already observed | Confirmed |
| DB-7 | 🟢 | Type fragility | `Login.java` | `archived` field | Boxed `Boolean` instead of primitive, inconsistent with every other flag in the codebase | Confirmed |
| DB-8 | 🟢 | Naming fragility | `Login.java` | `isLocked` field | Field name collides with Lombok's `is`-getter convention (works today, version-fragile) | Confirmed, low risk |
| DB-9 (BUG-017) | 🟠 | Security no-op | `AuthService.java` | `invalidateAllUserSessions` | Password reset doesn't revoke sessions | Confirmed |
| DB-10 | 🟢 | Non-portable JPQL | `PasswordHistoryRepository.java` | `findRecentPasswords` | Uses `LIMIT` in JPQL — Hibernate-6-specific extension, not portable | Confirmed, low risk |
| DB-11 | 🟢 | Dead schema | `DB/schema.sql` | `consent_log` table | No entity/repo/service references it anywhere in this module | Confirmed |
| DB-12 | 🟢 | DTO gap | `AppointmentDTO.java` | `Appointment.doctorNotes` | Column populated in seed data, never exposed via DTO — no frontend can ever show doctor's appointment notes | Confirmed |

**Additional structural/exception-handling findings** (not itemized with their own ID above, documented in §12 and the database_backend sub-report): string-sniffing of `RuntimeException` messages to infer HTTP status (auth failures return 400 instead of 401), no custom exception types anywhere in the codebase, `GlobalExceptionHandler`'s own `logger` field is declared but never used (500s vanish with zero server-side trace), no handling for `DataIntegrityViolationException` (TOCTOU race on `email` uniqueness would surface as an opaque 500 instead of 409).

---

## 4. Frontend File-by-File Audit

Full per-file detail (API calls, status, hardcoded data, notes) for all ~90 audited frontend files lives in the six source working papers this report was compiled from (patient, doctor, nurse, lab/admin, shared infra). Status summary by role:

### Patient (`pages/patient/`) — 9 files
| File | Status | Key Issues |
|---|---|---|
| Dashboard.jsx | ⚠️ | PT-9 (dead `.active` check) |
| Profile.jsx | ⚠️ | PT-10 (unused DTO / full-PatientDTO PUT contract) |
| Appointments.jsx | ❌ | PT-1, PT-2, PT-4, PT-5, PT-12 — heaviest concentration of hardcoded data in this role |
| Prescriptions.jsx | ⚠️ | PT-8, PT-9 |
| Medications.jsx | ✅ | None found |
| LabResults.jsx | ✅ | None found |
| MedicalHistory.jsx | ✅ | None found — clean, minimal, matches DTO directly |
| ConsentManagement.jsx | ❌ | PT-6, PT-7 — largest concentration of hardcoded content in this role (stat numbers, access log, connected apps) |
| GrantModifyConsent.jsx | ❌ | PT-6 (category mismatch + signature-as-reason bug) |

### Doctor (`pages/doctor/` + `components/doctor/`) — 21 files
| File | Status | Key Issues |
|---|---|---|
| Dashboard.jsx | ⚠️ | DR-7, DR-8, DR-9 |
| Profile.jsx | ❌ | DR-1 (critical), DR-2 |
| Patients.jsx | ✅ | None significant |
| PatientDetail.jsx | ❌ | DR-5, DR-6, DR-13 — worst file in doctor scope |
| Appointments.jsx | ❌ | DR-4, DR-9 |
| LabResults.jsx | ✅ | None major |
| Messages.jsx | ❌ | DR-10 |
| Prescriptions.jsx | ⚠️ | DR-3, N+1 fetch pattern |
| Prescriptions_Fixed.jsx | ❌ dead code | DR-12 |
| Reports.jsx | ❌ | DR-11 |
| `components/doctor/LabResultsList.jsx` | ✅ | None |
| `components/doctor/LabTestModal.jsx` | ✅ | Best-integrated component in doctor scope |
| `components/doctor/MedicalHistoryList.jsx` | ⚠️ | DR-14 |
| `components/doctor/MedicalRecordModal.jsx` | ❌ dead-but-correct | Fully working, never imported |
| `components/doctor/PrescriptionModal.jsx` | ❌ dead-but-correct | Fully working, never imported |
| `components/doctor/TreatmentModal.jsx` | ⚠️ | Used, but no backend `Treatment` concept exists at all |
| `components/doctor/VitalSignModal.jsx` | ❌ dead-but-correct | Fully working, never imported |
| `pages/doctor/components/VitalsChart.jsx` | ❌ dead code | Never imported |
| `pages/doctor/components/NotificationsPanel.jsx` | ⚠️ likely orphaned | Static placeholder, no backend concept |
| `pages/doctor/components/PatientSearch.jsx` | ✅ | Presentational only |
| `pages/doctor/components/TreatmentModal.jsx` | ⚠️ duplicate | Second, differently-shaped `TreatmentModal`, appears unused |

### Nurse (`pages/nurse/`) — 26 files
| File | Status | Key Issues |
|---|---|---|
| Dashboard.jsx | ⚠️ | NR-1, NR-2, NR-8, NR-9, NR-10 |
| Profile.jsx | ❌ | NR-11 (entirely mock) |
| Patients.jsx | ⚠️ | NR-6, NR-7 |
| PatientDetail.jsx | ⚠️ | NR-6, NR-7 |
| PatientDetails.jsx (plural) | ❌ dead code | NR-12, NR-3 |
| MedicationAdministration.jsx | ❌ | NR-4, NR-5 |
| ShiftHandover.jsx | ✅ | Correctly wired, no issues |
| Tasks.jsx | ✅ | Correctly wired; minor cosmetic gaps only |
| Vitals.jsx | ⚠️ | NR-6, NR-13, NR-14 |
| VitalsEntry.jsx | ⚠️ | NR-3 |
| `components/AssignedPatientsPanel.jsx` | ⚠️ | `mrn`/`admissionDate` never supplied; action buttons unwired |
| `components/VitalsAlertBanner.jsx` | ✅ | None |
| `components/VitalsEntryForm.jsx` | ⚠️ | NR-13 |
| `components/VitalsLogTable.jsx` | ⚠️ | Explicit TODO — history is local-only, lost on reload |
| `components/VitalsOverviewCard.jsx` | ✅ presentational | Fed only by local optimistic state, blank on page load |
| `components/VitalsSectionHeader.jsx` | ⚠️ likely dead | Not imported by `Vitals.jsx` |
| `components/VitalsTrendCard.jsx` | ✅ presentational | Fed by session-only data |

### Lab (`pages/lab/`) — 6 files
| File | Status | Key Issues |
|---|---|---|
| Dashboard.jsx | ✅ | Correctly integrated |
| Orders.jsx | ✅ | Minor loading/error-state gaps |
| OrderDetail.jsx | ⚠️ | LA-3; inefficient fetch-all pattern |
| UploadResults.jsx | ❌ | LA-1, LA-2 (critical) |
| History.jsx | ✅ | Correctly integrated |
| Profile.jsx | ❌ | LA-10 |

### Admin (`pages/admin/` + `components/admin/`) — 9 files
| File | Status | Key Issues |
|---|---|---|
| Dashboard.jsx | ✅ | Simple tab shell, no direct issues |
| Profile.jsx | ❌ | LA-10 |
| `components/AuditLogs.jsx` | ⚠️ | LA-5 |
| `components/CompliancePanel.jsx` | ❌ | LA-5, LA-7 |
| `components/IncidentManagement.jsx` | ❌ | LA-7 |
| `components/SystemHealth.jsx` | ❌ | LA-7 |
| `components/SystemOverview.jsx` | ⚠️ | LA-6, LA-7, LA-7a |
| `components/UserManagement.jsx` | ⚠️ | LA-8, LA-9 |
| `components/admin/AppointmentApprovalQueue.jsx` | ✅ | Best-implemented admin component in scope |

### Shared / Core
| File | Status | Key Issues |
|---|---|---|
| `services/api.js` | ⚠️ | SH-1 through SH-6 originate here |
| `services/supabaseAuth.js` | ⚠️ | SH-2, SH-9 |
| `contexts/AuthContext.jsx` | ⚠️ | SH-9 (no server-side session re-validation on load) |
| `components/auth/ProtectedRoute.jsx` | ✅ | Well-written, no issues |
| `App.jsx` | ✅ | Role naming consistent end-to-end |
| `pages/login.jsx` | ✅ | Role mapping matches backend `Role` enum exactly |

---

## 5. Backend File-by-File Audit

All 15 controllers, 22 DTOs, 19 entities, 17 repositories, 19 services, and the global exception handler were read in full. Controller-level findings are captured in §6 (API Contract Matrix); entity/DTO/repository findings are captured in §3 (DB-1 through DB-12) and §12. Highlights not already itemized as a numbered bug:

- **`AdminController.java`** — 3 real endpoints, plus 3 explicitly self-commented mock endpoints (`/user-activity`, `/security-events`, `/audit-report`) that admit `// Mock implemented for now` — none are called by any frontend file, so no user-facing impact, but they should not be mistaken for live functionality if ever wired up.
- **`AppointmentController.java`** — the most thorough and fully-implemented controller; every mutating method has both `@PreAuthorize` and a duplicate manual authority check (defense-in-depth, appears intentional for unit-test coverage where method-security AOP isn't active in slice tests).
- **`CatalogController.java`** — fully static reference data (no persistence layer), no `@PreAuthorize`, but still behind `SecurityConfig`'s blanket `anyRequest().authenticated()` — meaning these "public-looking" dropdown endpoints actually require a valid JWT, which could surprise a developer wanting them callable from a pre-login signup form.
- **`FileUploadController.java`** — fully implemented (AES-256-GCM at rest) but zero frontend caller (root cause of LA-1/LA-2).
- **`GlobalExceptionHandler.java`** — single, consistent `@RestControllerAdvice`; correct `ErrorResponse` shape; but relies on substring-matching exception messages for status codes (§12) and its own `logger` field is dead code — every unexpected 500 vanishes with no server-side trace.
- **`SecurityConfig.java`** — Argon2 password hashing (reasonable parameters), stateless JWT sessions, correct `hasAuthority(...)` (no `ROLE_` prefix bug) matching `CustomUserDetailsService`'s un-prefixed authorities exactly — this integration point was specifically checked and is correct.
- **`JwtAuthenticationFilter.java`** — functionally correct, but contains multiple `System.out.println("DEBUG JWT: ...")` calls left in, including logging the extracted email on every single request — a logging-hygiene and PII-exposure concern worth cleaning up (not itemized as a numbered bug given scope, flagged here for follow-up).

---

## 6. API Contract Matrix

Base path for every controller is `/api/...`. Full matrix (every endpoint × every frontend caller) below; **bold** = broken/missing, plain = OK.

| API | Method | Frontend Caller | Backend Controller | Request Match | Response Match | Auth | Status |
|---|---|---|---|---|---|---|---|
| /api/auth/register | POST | `supabaseAuth.signup` | `AuthController.registerUser` | ✅ | ✅ | public | OK |
| /api/auth/login | POST | `supabaseAuth.login` | `AuthController.login` | ✅ | ✅ | public | OK |
| /api/auth/logout | POST | `supabaseAuth.logout` | `AuthController.logout` | ✅ | ✅ | Bearer | OK |
| /api/auth/me | GET | `authAPI.getCurrentUser` | `AuthController.getCurrentUser` | ✅ | ✅ | Bearer | **Defined but never called (SH-9)** |
| /api/auth/verify-otp | POST | `supabaseAuth.verifyOtp` | `AuthController.verifyOtp` | ✅ | ✅ | public | OK |
| /api/auth/resend-otp | POST | `supabaseAuth.resendOtp` | **none** | — | — | — | **BROKEN (SH-2)** |
| /api/auth/enable-2fa | POST | not called | `AuthController.enableTwoFactorAuth` | — | — | — | Unused by frontend |
| /api/auth/forgot-password | POST | `supabaseAuth.forgotPassword` | `AuthController.forgotPassword` | ✅ | ✅ | public | OK |
| /api/auth/validate-reset-token | GET | `supabaseAuth.validateResetToken` | `AuthController.validateResetToken` | ✅ | ✅ | public | OK |
| /api/auth/reset-password | POST | `supabaseAuth.resetPassword` | `AuthController.resetPassword` | ✅ | ✅ | public | OK |
| /api/auth/refresh-token | POST | **none** | `AuthController.refreshToken` | — | — | — | **Unused by frontend (SH-1)** |
| /api/patients/me | GET | `patientAPI.getMe` | `PatientController.getMyProfile` | ✅ | ✅ | Bearer | OK |
| /api/patients | GET | `patientAPI.getAll` | `PatientController.getAllPatients` | ✅ | ✅ | Bearer | OK |
| /api/patients/{id} | GET | `patientAPI.getById` | `PatientController.getPatientById` | ✅ | ✅ | Bearer | OK |
| /api/patients | POST | `patientAPI.create` | `PatientController.createPatient` | ✅ | ✅ | Bearer | OK |
| /api/patients/{id} | PUT | `patientAPI.update` | `PatientController.updatePatient` | ✅ | ✅ | Bearer | OK |
| /api/patients/{id} | DELETE | `patientAPI.delete` | **none** | — | — | Bearer | **BROKEN (SH-5)** |
| /api/appointments | GET | `appointmentAPI.getAll` | `AppointmentController.getAllAppointments` | ✅ | ✅ | Bearer | OK |
| /api/appointments/doctor/{id}/available-slots | GET | **no wrapper** (used ad hoc by `AvailableSlotSelector`) | `AppointmentController.getAvailableSlots` | — | — | — | **Unwrapped (PT-3)** |
| /api/appointments | POST | `appointmentAPI.create` | `AppointmentController.createAppointment` | ✅ | ✅ | Bearer | OK |
| /api/appointments/{id} | PUT | `appointmentAPI.update` | `AppointmentController.updateAppointment` (DOCTOR only) | ✅ | ✅ | Bearer | OK for doctors; **403 for patients (PT-1)** |
| /api/appointments/{id}/cancel | PUT | `appointmentAPI.cancel` | `AppointmentController.cancelAppointment` | ⚠️ body ignored | ✅ | Bearer | **Reason dropped (PT-2)** |
| /api/appointments/pending | GET | `appointmentAPI.getPending` | `AppointmentController.getPendingAppointments` | ✅ | ✅ | Bearer | OK |
| /api/appointments/{id}/approve, /reject | PUT | `appointmentAPI.approve/reject` | `AppointmentController` | ✅ | ✅ | Bearer | OK |
| /api/appointments/{id}/complete | PUT | **no wrapper** | `AppointmentController.completeAppointment` | — | — | — | **Unwrapped (DR-4)** |
| /api/appointments/stats | GET | not called | `AppointmentController.getStats` (global, not doctor-scoped) | — | — | — | Unused; needs scoping to be useful (DR-7/9) |
| /api/medical-records/{id} | PUT | `medicalRecordAPI.update` | **none** | — | — | Bearer | **BROKEN (SH-4)** |
| /api/prescriptions/{id} | PUT | `prescriptionAPI.update` | **none** (only `/refill`) | — | — | Bearer | **BROKEN (SH-4, DR-3)** |
| /api/lab-results/{id} | PUT | `labResultAPI.update` | **none** | — | — | Bearer | **BROKEN (SH-4)** |
| /api/vital-signs/{id} | PUT | `vitalSignsAPI.update` | **none** | — | — | Bearer | **BROKEN (SH-4)** |
| /api/doctors/{id} | GET | `doctorAPI.getById` | `DoctorController.getDoctorById` | ⚠️ ID-space mismatch | ✅ | public | **Wrong ID used by Profile.jsx (DR-1)** |
| /api/doctors/me | GET | **doesn't exist** | **doesn't exist** | — | — | — | **Missing (root fix for DR-1)** |
| /api/nurse/medications/record | POST | `nurseAPI.recordMedicationAdministration` | **none** | — | — | Bearer | **BROKEN (NR-4/SH-3)** |
| /api/lab-technician/orders/{id}/upload | PUT | `labTechnicianAPI.uploadResults` | `LabTechnicianController.uploadResults` | ✅ shape, ⚠️ `fileUrl` always null | ✅ | Bearer | Works, but never receives a real file (LA-1) |
| /api/files/upload | POST | **no `filesAPI` wrapper at all** | `FileUploadController.uploadFile` | — | — | — | **Unreachable from frontend (LA-1/LA-2)** |
| /api/files/{filename} | GET | **no wrapper**, raw `<a href>` used | `FileUploadController.getFile` | — | — | — | **Likely 401 via plain anchor nav (LA-3, unverified)** |
| /api/admin/staff/{id}/role, DELETE /staff/{id} | PUT/DELETE | `adminAPI.updateStaffRole`/`deleteStaff` (wrapped, unused) | `AdminController` | ✅ | ✅ | Bearer | **Unwired from UI (LA-8)** |
| /api/admin/user-activity, /security-events, /audit-report | GET/POST | not called | `AdminController` (self-admitted mocks) | — | — | — | Unused, backend-stub-only |
| /api/consent | GET/POST | `consentAPI.getMyConsents`/`grantConsent` | `ConsentController` | ✅ | ✅ | Bearer | OK |
| /api/hospital-departments, /api/medications, etc. | GET | not called by patient booking flow | `CatalogController` | — | — | — | Unused (PT hardcodes `mockDepartments` instead) |

*(Every remaining GET/read endpoint across all 15 controllers — patients, appointments, medical records, prescriptions, lab results, doctors, vital signs, nurse, lab-technician, admin, consent — was confirmed to match its frontend caller correctly and is omitted here for brevity; the full per-endpoint matrix with 80+ rows is preserved in the shared-infra working paper.)*

---

## 7. Frontend → Backend Data Flow Matrix (key fields, cross-role)

| UI Field | Frontend Source | API | Backend DTO | DB Field | Status |
|---|---|---|---|---|---|
| Patient full name | `patient.firstName/lastName` | `GET /patients/me` | `PatientDTO` | `patient_profiles.first_name/last_name` | ✅ |
| Doctor's own profile | `user.userId` passed as doctor ID | `GET /doctors/{id}` | `DoctorDTO` | `doctor_profiles.profile_id` (wrong ID space) | ❌ DR-1/DB-1 |
| Doctor display name (everywhere) | `x.doctorName` | multiple | `AppointmentDTO`/`PrescriptionDTO`/etc. | `login.email` (not a name) | ❌ DB-2/BUG-018 |
| Appointment duration/location/room | `appt.duration/location/room` | `GET /appointments/patient/{id}` | `AppointmentDTO` (no such fields) | not modeled | ❌ PT-5 |
| Appointment doctor notes | not rendered anywhere | — | `AppointmentDTO` (missing field) | `appointments.doctor_notes` (populated in seed data) | ❌ DB-12 |
| Cancellation reason | `cancellationReason` | `PUT /appointments/{id}/cancel` | ignored — no `@RequestBody` | not modeled | ❌ PT-2 |
| Medical record "notes" | `record.notes` | `GET /medical-records/patient/{id}` | `MedicalRecordDTO.notes` = duplicate of `symptoms` | not a real column | ❌ DB-3 |
| Medical record "type"/"note" (doctor view) | `record.type`/`record.note` | same | DTO has `diagnosis`/`symptoms`/`treatmentProvided` | `medical_records.*` | ❌ DR-14 |
| Medical record date | `record.recordDate` | same | bound to `updatedAt`, drifts on edit | `medical_records.updated_at` | ❌ DB-4 |
| Prescription route | `p.route` | `GET /prescriptions/patient/{id}` | not in `PrescriptionDTO` | not modeled | ❌ NR-5 |
| Prescription administered status | `p.status === 'Completed'` | same | always `"ACTIVE"`, never transitions | `prescriptions.status` | ❌ NR-5 |
| Vitals recorder name | `latestVitals.nurse?.username` | `GET /vital-signs/.../latest` | `VitalSignDTO.nurseEmail` (flat string) | `vital_signs.nurse_id` → `login.email` | ❌ NR-3 |
| Vitals notes/pain level | captured in nurse form | — | not in `VitalSignRequest`/`VitalSignDTO` | not modeled | ❌ NR-13/14 |
| Vitals weight/height | not captured by any nurse form | — | `VitalSignRequest.weight/height` exist | `vital_signs.weight/height` | ⚠️ NR-15 (backend ahead) |
| Patient room/bed/acuity/allergies | fabricated fallback constants | — | not on `PatientProfile` at all | not modeled | ❌ NR-6 |
| Lab test file attachment | `order.fileUrl` | `PUT /lab-technician/orders/{id}/upload` | `LabTestDTO.fileUrl` (correctly wired end-to-end) | `lab_tests.file_url` | ❌ never populated (LA-1) |
| Lab "ordered by" name | `order.orderedByDoctor`/`orderedByName` | two different endpoints, two different fields, only one ever populated | `LabTestDTO` (redundant dual fields) | `lab_tests.ordered_by_id` → `login.email` | ❌ redundant + email-not-name (DB-2) |
| Admin staff status/last-login | `user.status`/`user.lastLogin` | `GET /admin/staff` | `StaffDTO` has neither field | not modeled | ❌ LA-9 |
| Consent granted-to provider | `c.grantedTo` (raw `Login`, password/otp hidden) | `GET /consent` | raw entity, not a DTO | `patient_consents.granted_to_id` | ⚠️ works, but API-hygiene smell |

---

## 8. Missing API Report

### MISSING-API-001 — Patient-scoped appointment reschedule
**Feature:** `pages/patient/Appointments.jsx` reschedule flow | **Data required:** update `appointmentDate` only, initiated by the owning patient | **Current situation:** reuses the DOCTOR-only update endpoint → 403 | **Recommended:** `PUT /api/appointments/{id}/reschedule`, `@PreAuthorize("hasAnyAuthority('PATIENT')")`, body `{appointmentDate}`, ownership-validated, resets status to `PENDING_APPROVAL` | **Backend files:** `AppointmentController.java`, `AppointmentService.java`

### MISSING-API-002 — Cancellation reason persistence
Extend `PUT /api/appointments/{id}/cancel` to accept `{reason}` and persist it. **Backend files:** `AppointmentController.cancelAppointment`, `Appointment.java` (new column), `AppointmentService.cancelAppointment`.

### MISSING-API-003 — `GET /api/doctors/me`
Resolve the calling doctor's own profile via JWT identity instead of forcing the frontend to guess an ID space (root fix for DR-1/DB-1). **Backend files:** `DoctorController.java`, `DoctorService.java`.

### MISSING-API-004 — Medication administration recording
**Frontend feature:** Nurse "Administer Medication" | **Data required:** prescriptionId, patientId, nurseId, administeredAt, notes | **Current situation:** no backend model/table/endpoint exists at all | **Recommended:** `POST /api/nurse/medications/{prescriptionId}/administer` returning the created record | **Backend files:** new `MedicationAdministration` entity + repository + DTO, `NurseController.java`, `NurseService.java`.

### MISSING-API-005 — Generic prescription update
`PUT /api/prescriptions/{id}` for dosage/frequency/status edits (distinct from the existing `/refill`). **Backend files:** `PrescriptionController.java`, `PrescriptionService.java`.

### MISSING-API-006 — Multipart file upload wrapper + wiring
`filesAPI.upload(file)` in `api.js`, `Content-Type` conditional skip in `apiCall`, wired into `UploadResults.jsx`. Backend already complete. **Frontend files:** `services/api.js`, `pages/lab/UploadResults.jsx`.

### MISSING-API-007 — Doctor-scoped dashboard aggregate stats
Real per-doctor counts (patients, today's appointments, pending labs, unread messages) — `AppointmentController.getStats()` exists but returns global counts, not scoped to the caller. **Backend files:** `AppointmentController.java`, `AppointmentService.java` (add `WHERE doctor_id = ...`).

### MISSING-API-008 — Nurse profile CRUD
No backend model exists for license #, shift, ward, supervisor, phone. **Backend files:** new columns/table + `NurseController.java` GET/PUT `/profile`.

### MISSING-API-009 — Patient bed/room/acuity/allergy data
No columns exist on `PatientProfile` for room, bed, acuity level, code status, or allergies. **Backend files:** new columns/tables on `patient_profiles` (or `patient_bed_assignments`/`patient_allergies`), server-computed `vitalsStatus`/`medicationStatus` from `vital_signs`/`nurse_tasks` timestamps rather than trusting client fallback constants.

### MISSING-API-010 — Handover note read/unread tracking
`HandoverNote.isRead` exists in the DB but no endpoint ever mutates it. **Backend files:** `NurseController.java` (`PUT /handover/{id}/read`, bulk variant), `NurseService.java`.

### MISSING-API-011 — Lab/Admin "update own profile"
No endpoint exists for a lab technician or admin to update their own profile. **Backend files:** new endpoints on `LabTechnicianController.java`/`AdminController.java`.

### MISSING-API-012 — Consent history / audit trail
`GET /api/consent/history` and a patient-scoped `GET /api/patients/me/access-log` (derivable from `AuditLog`, which today is admin-only). **Backend files:** `ConsentController.java`, `AuditLogController.java`/`PatientController.java`.

### MISSING-API-013 — Messaging backend
No `MessageController`/entity/table exists at all; `Messages.jsx` (doctor) is permanently mock-only by construction, not by integration gap. Requires new backend feature if this is a real product requirement, or the page should be relabeled "Coming soon."

---

## 9. Mock / Hardcoded Data Report

Consolidated from all five role audits (file, snippet, expected source — abbreviated; full line numbers are in §2/§3 and the per-role sections above):

| File | Hardcoded value | Should come from |
|---|---|---|
| `patient/Appointments.jsx` | `mockDepartments`, `mockTimeSlots`, `availableTimeSlots`, `appointmentTypes` | `CatalogController` (departments exist, unused) / real available-slots endpoint (exists, unused) |
| `patient/Prescriptions.jsx` | `"In 5 days"` refill text | `PrescriptionDTO.endDate`/`refillsRemaining` |
| `patient/ConsentManagement.jsx` | Stat cards `47/3/5/12`, 5 fake access-log rows, fake "Connected Apps" list | Real record counts / `AuditLog` (patient-scoped) / no backend concept exists for connected apps |
| `patient/GrantModifyConsent.jsx` | `IP: 192.168.1.100` | Should come from request metadata captured server-side, or removed |
| `doctor/Dashboard.jsx` | "Pending Labs: 5", entire "Daily Briefing" panel | `labResults.getAll()`/pending count; real date + real counts |
| `doctor/Profile.jsx` | License #, "15 Years Experience", fake toggle switches | Not modeled anywhere in backend |
| `doctor/PatientDetail.jsx` | BP 120/80, HR 72, Weight 70kg for every patient; `'Dr. Smith'` ×2 | `vitalSigns.getLatest()`; `useAuth()`'s real doctor name |
| `doctor/Messages.jsx` | Entire mock conversation, fake canned reply | No backend exists (by design gap, not integration gap) |
| `doctor/Reports.jsx` | Fake `setTimeout` report generation, "1.2 MB" mock size, "15%"/"+2%" stats | No backend exists |
| `nurse/Profile.jsx` | Entire page: title, license #, phone, shift, ward, supervisor | New nurse-profile backend model (missing entirely) |
| `nurse/Dashboard.jsx` | Task-category counts, "Show Completed (12)", "3 unread notes", "Auto-save every 30 seconds" | Should derive from real `overview.tasks`/`handoverNotes` (currently always empty due to NR-2) |
| `nurse/Vitals.jsx`, `Patients.jsx`, `PatientDetail.jsx` | Room "101"/Bed "A", "stable" acuity, "done" vitals-status, "Full Code", empty allergies | No backend fields exist at all (NR-6) |
| `lab/Profile.jsx` | Phone, "Central Pathology Lab - Room 302", 3 certifications, badges | No lab-profile endpoint exists |
| `admin/Profile.jsx` | "Server: US-East-1", "backup.admin@medicare.com", permission grid | No admin-profile endpoint exists |
| `admin/components/AuditLogs.jsx` | 2 fake audit rows on API failure | Should show a visible error state instead |
| `admin/components/CompliancePanel.jsx` | 24h login-chart defaults, "HIPAA 98%", "AES-256 Active", "Security Score A+", 2 fake pending actions | No backend source exists for any of these |
| `admin/components/SystemOverview.jsx` | `{5,2,3,5}` stats fallback, Device Usage 65/25/10% | Fallback on error; Device Usage has zero backing data source |
| `admin/components/SystemHealth.jsx` | Server Load, Memory, API latency, DB status, CPU — entire file | No backend endpoint exists at all |
| `admin/components/IncidentManagement.jsx` | 3 fake incidents | No incidents table/controller exists |
| `admin/components/UserManagement.jsx` | 4 fake users on failure; "Active"/"Just now" always, every row | `StaffDTO` has no status/last-login fields |

No production page in any role imports Mock Service Worker handlers — there is no MSW setup in this codebase at all; `mocks/*` are plain static fixture files, some legitimately test-only, others (flagged above) reachable from real routed pages with no build-time guard separating them from production.

---

## 10. Request/Response Mismatch Report

```
Frontend Request                          Backend DTO                         Mismatch                                    Recommended Contract
─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
Appointments.jsx cancel body:              cancelAppointment(id, auth) —        Body is never bound to anything;            Add @RequestBody(required=false)
{status:'CANCELLED', reason}               no @RequestBody parameter            reason silently discarded                   Map<String,Object> body

Appointments.jsx create payload:           AppointmentRequest                   type/specialRequirements collected in       Add appointmentType, specialRequirements
{doctorId, appointmentDate,                {doctorId, appointmentDate,          UI, dropped before the POST                 fields to AppointmentRequest/Appointment/
 reasonForVisit}  (type/special            reasonForVisit}                                                                   AppointmentDTO
 requirements omitted)

Doctor Prescriptions.jsx "Manage":         PrescriptionController has           PUT /prescriptions/{id} 404s;               Add PUT /api/prescriptions/{id}
PUT /prescriptions/{id}                    only POST, GET, PUT /{id}/refill,    doctor sees false-success UI                (doctor-only, ownership-checked)
                                            DELETE

Nurse Vitals.jsx persistVitals() payload:  VitalSignRequest {patientId,         notes/painLevel captured in the UI,         Add notes (TEXT), painLevel (Integer)
{patientId,bloodPressure,heartRate,        bloodPressure,heartRate,             never included in the outgoing payload      to VitalSignRequest/VitalSign/VitalSignDTO
 temperature,respiratoryRate,              temperature,respiratoryRate,
 oxygenSaturation}                         oxygenSaturation,weight,height}

MedicalHistoryList.jsx read shape:         MedicalRecordDTO {recordId,          Component expects `type`/`note`,            Map API response → {type: diagnosis,
{type, date, note}                         patientId, doctorName, diagnosis,    DTO has diagnosis/symptoms/                 note: symptoms + treatmentProvided,
                                            symptoms, treatmentProvided,         treatmentProvided/recordDate —              date: recordDate} before storing
                                            notes, recordDate, createdAt}        real data renders blank

UploadResults.jsx uploadResults() body:    LabTechnicianController.             fileUrl parameter always null;              filesAPI.upload(file) first, then
{resultValue, remarks, fileUrl: null}      uploadResults expects                selected file discarded                     pass its returned filename as fileUrl
                                            Map<String,String>{resultValue,
                                            remarks,fileUrl}

VitalsEntry.jsx / PatientDetails.jsx read: VitalSignDTO.nurseEmail              Component expects nested                    Read `latestVitals.nurseEmail` directly
latestVitals.nurse?.username               (flat string, no nested object)      `nurse.username` object — always
                                                                                  undefined, silently no-ops
```

---

## 11. Authentication & User Identity Audit

Full step-by-step trace (login.jsx → AuthContext → supabaseAuth → AuthController → JWT → role-scoped dashboards) is documented in the shared-infra working paper; the end-to-end chain is **structurally correct** — role names line up exactly (`Role.java`'s 5-value enum ↔ `App.jsx`'s `allowedRoles` ↔ `login.jsx`'s `roleMap` ↔ `ProtectedRoute.jsx`'s case-insensitive compare), and no `ROLE_` prefix bug exists (`CustomUserDetailsService` and every `@PreAuthorize("hasAuthority(...)")` consistently omit the prefix).

**Confirmed issues in this area:**
- **SH-1/BUG-003 (HIGH):** the refresh-token endpoint is fully built server-side and never called client-side — every session dies every 15 minutes regardless of the 7-day refresh cookie's validity.
- **SH-9 (LOW):** `AuthContext`'s session bootstrap on page load is a pure `localStorage` read with **no server round-trip to validate the token is still good** — a token blacklisted server-side (e.g., after logout on another device) will appear valid client-side until the next API call 401s.
- **Login response has no display name field** (`LoginResponse.java`: `{accessToken, refreshToken, role, status, userId}`) — `fullName` shown in the UI is sourced entirely from a client-side `localStorage` cache populated at registration time; a first-time login on a new device shows no name until that cache is warmed. **UNVERIFIED** whether any UI depends on this being always-present.
- **DR-1/DB-1 (CRITICAL):** although the *authentication* identity resolution (email → JWT → role) is correct, the *downstream* resolution of "which doctor profile is this" is broken due to the `Login.userId` vs `DoctorProfile.profileId` ID-space collision — this is a post-authentication identity bug, not an authentication bug per se, but it means "who am I" answers incorrectly for the Doctor role specifically once the ID spaces diverge.
- **DB-9/BUG-017 (HIGH, security):** password reset does not revoke existing sessions — a stolen session token survives a password reset.
- Every role-scoped `/me`-style endpoint (`patients/me`, `nurse/dashboard`, etc.) correctly resolves "who is calling" from `Authentication.getName()` (the JWT-embedded email) rather than trusting a client-supplied ID — this pattern is consistent and correct across `PatientController`, `NurseController`, `LabTechnicianController`. The one exception is `DoctorController.getDoctorById`, which is not identity-scoped at all (DR-1).

---

## 12. Database/API/Frontend Relationship Audit

- **Schema source of truth is ambiguous.** `spring.jpa.hibernate.ddl-auto=update` means the *live* database is generated from JPA entity annotations, but `DB/schema.sql` is a separately hand-maintained snapshot (its own header says "Source of truth: backend JPA entities" — i.e., it self-admits to being secondary). `ddl-auto=update` never drops orphaned columns, so drift only accumulates in one direction. **Already-observed evidence of drift:** `login.otp_secret` column exists in `schema.sql` with no entity field at all; the Postgres-native `request_status`/`user_role_type` ENUM types declared in `schema.sql` are never used by Hibernate (all enums map to plain `VARCHAR(EnumType.STRING)`); a `consent_log` table exists with zero code references anywhere in the backend module (DB-11).
- **No Flyway/Liquibase migrations exist** — for a healthcare application, this is a meaningful risk (DB-6): there is no auditable history of schema changes, and `ddl-auto=update`'s behavior around constraint/index changes is less reliable than explicit DDL.
- **ID-space collision is the single most consequential DB↔API relationship bug** (DB-1/DR-1): `DoctorProfile.profile_id` and `Login.user_id` are independently-incrementing sequences that are both informally called "doctor id" by different layers of the same codebase, with no DTO field disambiguating them.
- **Several DTOs expose fields with no reliable source column, or drop columns that do exist:** `MedicalRecordDTO.notes` is a fabricated duplicate of `symptoms` (DB-3); `MedicalRecordDTO.recordDate` is bound to the wrong timestamp column (DB-4); `AppointmentDTO` never exposes the populated `doctor_notes` column at all (DB-12); `LabTestDTO` carries two redundant, never-simultaneously-populated "ordered by" name fields depending which service builds it (DB-2 detail).
- **Several status-like columns have no enum enforcement at the database or Java level** — `LabTest.status`, `Consent.consentType`/`status`, `NurseTask.category`/`priority`/`status`, `HandoverNote.shiftDirection` are all free-text `String` fields validated nowhere; a typo persists silently and (for `Consent.consentType`) can silently deny legitimate access with no error surfaced.
- **Ordering methods exist on 3+ repositories but the service layer calls the unordered variant instead** (`AppointmentRepository`, `PrescriptionRepository`, `LabTestRepository`, `VitalSignRepository`) — likely-live bug: appointment/prescription/lab-test lists probably return in arbitrary database order rather than chronological, despite a correctly-named ordered query method sitting unused right next to the one actually called.

---

## 13. Duplicate / Dead / Unused Integration Code

**Frontend — dead files (unrouted, confirmed via `App.jsx` import/route grep):**
- `pages/doctor/Prescriptions_Fixed.jsx` — calls a non-existent `api.prescriptions.getAll()`; superseded by `Prescriptions.jsx`.
- `pages/nurse/PatientDetails.jsx` (plural) — imported in `App.jsx` but never assigned a route; a whole second patient-detail screen that independently reproduces the same `nurse?.username` bug (NR-3) found in the live `PatientDetail.jsx`.
- `pages/doctor/components/VitalsChart.jsx` — not imported by any routed page (a separate top-level `components/VitalsChart.jsx` also appears unused, not further audited).
- `pages/doctor/components/TreatmentModal.jsx` — a second, differently-shaped `TreatmentModal` component sitting alongside the one actually used (`components/doctor/TreatmentModal.jsx`).
- `pages/nurse/components/VitalsSectionHeader.jsx` — not imported by `Vitals.jsx`.

**Frontend — dead-but-correct components** (fully working, wired to the right endpoint and DTO shape, simply never imported by any routed page — the highest-value, lowest-effort fixes in this entire audit):
- `components/doctor/PrescriptionModal.jsx` — correctly calls `api.prescriptions.create`; would directly fix DR-5 if wired into `PatientDetail.jsx`/`Prescriptions.jsx`.
- `components/doctor/MedicalRecordModal.jsx` — correctly calls `api.medicalRecords.create`; there is currently **no "Add Record" button anywhere in the doctor UI** despite this component existing.
- `components/doctor/VitalSignModal.jsx` — correctly calls `api.vitalSigns.create`; would fix DR-6.
- `components/appointments/AvailableSlotSelector.jsx` — correctly structured, but calls a non-existent `api.appointments.getAvailableSlots` wrapper (PT-3) and is itself unused by any patient page; fixing both would resolve the entire "fake booking time slots" problem.

**Backend — self-admitted mock endpoints** (`AdminController.getUserActivity`, `getSecurityEvents`, `generateAuditReport`) — explicitly commented `// Mock implemented for now`, never called by any frontend file.

**Backend — unused repository methods** (likely leftover from refactors, see DB report §"Duplicate/Dead" for full list): `ConsentRepository.findByPatient_ProfileIdAndStatus`, `LoginRepository.findByRoleNot(String)` (a redundant `String`-typed overload beside the `Role`-typed one actually used), `VitalSignRepository`'s two entity-typed lookup methods, `PasswordResetTokenRepository.deleteExpiredTokens` (no scheduled job ever calls it — expired tokens accumulate forever).

**Backend — copy-pasted mapping code** — `LabTestService` repeats an identical 12-line DTO-mapping lambda in three separate methods instead of extracting a private helper (as `LabTechnicianService` already correctly does for its own DTO); the same pattern repeats in `PrescriptionService` and `AppointmentService` (which even has an existing `toDTO` helper that its own list methods don't call).

**Unused backend endpoints (implemented, zero frontend caller) — 18 confirmed across all roles:**
`GET /auth/me`, `POST /auth/refresh-token`, `DELETE /patients/{id}` *(doesn't exist — see SH-5)*, `GET /appointments/status/{status}`, `GET /appointments/stats`, `PUT /appointments/{id}/complete` *(exists, unwired — DR-4)*, `PUT /prescriptions/{id}/refill` *(exists, doctor UI tries a broken generic update instead — DR-3)*, `GET /lab-results/pending`, `GET /doctors/department/{department}`, `GET /hospital-departments` + 4 more `CatalogController` endpoints (unused by patient booking flow), `GET /admin/patients` (no "Patient Directory" view exists in the admin console despite full backend support), `GET /admin/audit-logs/{email}`, `PUT /admin/staff/{id}/role`, `DELETE /admin/staff/{id}` *(LA-8)*, `POST /files/upload`, `GET /files/{filename}` *(LA-1/LA-2)*.

---

## 14. Recommended Fix Order

```
1. Fix authentication/session-layer issues (SH-1 token refresh, DB-9 session revocation on
   password reset, SH-2 resend-otp)
        ↓  Everything downstream depends on a stable, correctly-scoped session; a 15-minute
           forced-logout bug makes every other fix hard to verify manually.

2. Fix the doctor ID-space collision (DR-1 / DB-1) — add GET /api/doctors/me
        ↓  This is the single highest-severity data-integrity bug found (a doctor can load
           another doctor's identity). It also blocks safely fixing DR-2 (profile save).

3. Fix the shared API-layer contract gaps (SH-4 generic PUT endpoints, SH-5 patient delete,
   BUG-018/DB-2 doctorName-is-email across 5 DTOs)
        ↓  These are broad, mechanical, low-risk fixes that unblock several role-specific
           bugs at once (DR-3 depends on SH-4; every "doctorName" display across all roles
           depends on DB-2).

4. Wire the already-correct-but-unused frontend components into their routed pages
   (PrescriptionModal→PatientDetail.jsx fixes DR-5; VitalSignModal+VitalsChart→PatientDetail.jsx
   fixes DR-6; MedicalRecordModal→PatientDetail.jsx adds the missing "Add Record" affordance;
   AvailableSlotSelector+missing getAvailableSlots wrapper→Appointments.jsx fixes PT-3)
        ↓  Highest fix-value-to-effort ratio in the whole audit — the backend and the
           component both already work; this is pure wiring.

5. Fix patient-safety-relevant nurse gaps (NR-1 dashboard crash, NR-4 medication
   administration false-success, NR-2 dead task/handover duplicate UI)
        ↓  These affect a role that directly administers care; NR-4 in particular is a
           false-positive-success bug that should not ship as-is.

6. Fix the lab result file-upload flow end-to-end (LA-1, LA-2)
        ↓  Backend is 100% ready; this is purely a frontend `api.js` + UploadResults.jsx fix,
           and unblocks LA-3 (dead "View Report" links) as a side effect.

7. Replace silent mock-data fallbacks with visible error states across admin panels
   (LA-5, LA-6) and remove/relabel the entirely-fictional system-health/incidents/
   compliance widgets (LA-7, LA-7a) so admins are never shown fabricated numbers as if real
        ↓  Lower urgency than functional bugs, but directly affects operational trust in the
           admin console during a real incident — the worst time to be silently shown fake data.

8. Clean up dead code (Prescriptions_Fixed.jsx, PatientDetails.jsx-plural, duplicate
   TreatmentModal, orphaned repository methods, dead AdminController mock endpoints)
   and add integration tests around the fixed contracts above
        ↓  Do this last so cleanup doesn't churn files that are still being actively fixed
           in steps 1-7; integration tests should be written against the corrected contracts,
           not the current broken ones.
```

---

## 15. Final Integration Health Summary

### Authentication
Structurally sound (role naming, JWT authority mapping, and route guards are all internally consistent and correctly wired end-to-end), but operationally broken by the missing token-refresh call (SH-1) and the password-reset session-revocation no-op (DB-9). **Needs immediate attention before either of the two remaining findings would matter in production.**

### Patient
Read paths (Medications, LabResults, MedicalHistory) are clean and fully correct. The two most-used interactive flows — appointment booking/rescheduling and consent management — both have concrete, patient-facing broken behavior (403 on reschedule, wrong consent form content shown, dropped cancellation reason). **Partially functional; core write-paths need fixes before this role is production-ready.**

### Doctor
The role with the largest gap between "backend capability" and "what actually ships." Nearly every core clinical write action (prescribe, record vitals, complete an appointment, add a medical record) either silently fails or was never wired to the correctly-built component that already exists for it. The doctor's own profile can resolve to the wrong doctor. **This is the least production-ready role in the audit and should be prioritized.**

### Nurse
Task and shift-handover management work correctly when accessed through their dedicated pages, but the Dashboard duplicates the same features badly (dead code, one confirmed crash). Medication administration — arguably the single most safety-critical nurse action in the app — is entirely unpersisted. The triage/acuity UI (bed, room, code status, allergies) is 100% decorative because the underlying data model doesn't exist yet. **Functional for basic task/vitals workflows; not safe to rely on for medication administration or acuity triage as currently built.**

### Lab Technician
The best-integrated non-admin role after the Patient read-paths — dashboard, orders, and history are all correctly wired. The one serious gap (file-upload attachment) is a complete, well-understood, single-flow fix since the backend is already done. **Close to production-ready; one clear blocking fix remaining.**

### Admin
Core data views (staff list, metrics, pending-appointment approvals) are correctly wired and among the best-implemented components in the entire audit (`AppointmentApprovalQueue.jsx` in particular). However, a large fraction of the "System Health"/"Incidents"/"Compliance" surface area is entirely fabricated with no backend behind it at all, and several silent-mock-fallback patterns risk showing an admin fake data during a real outage — exactly the wrong time. **Core admin functions work; monitoring/compliance panels should be clearly labeled as placeholders or built out before being trusted operationally.**

### API Layer
Internally consistent in its conventions (role names, auth header handling, base URL pattern) but has real contract gaps: 4 generic `PUT` update endpoints referenced by the frontend don't exist on 4 different controllers, and the reverse also happens — multiple fully-implemented, correctly-authorized backend endpoints (appointment completion, doctor's own profile lookup, staff role management, file upload) have no frontend caller at all. **The API surface is more complete than what the frontend currently uses — most remaining work is frontend wiring, not backend development.**

### Database Integration
No migration tooling and a `ddl-auto=update` + hand-maintained-but-secondary `schema.sql` combination has already produced observable drift (a dead column, unused Postgres enum types, an orphaned legacy table). The most consequential structural issue — two independently-incrementing ID sequences both informally called "doctor id" — is a landmine that "works" only by coincidence in the current small seed dataset. **Needs a schema-migration strategy and the doctor-ID disambiguation fix before scaling past the current seed data.**

### Overall
This is not a system with a broken backend — the backend is, in most areas, ahead of what the frontend actually uses. The dominant failure pattern across every role is **duplicated, disconnected frontend implementations**: a correct component or endpoint exists, and the page that's actually routed reimplements the same feature independently, without calling it. Fixing this audit's highest-priority items is therefore largely a wiring exercise, not a rebuild — with the exception of the doctor-ID ambiguity (DR-1/DB-1), the medication-administration data model gap (NR-4), and the file-upload frontend gap (LA-1/LA-2), which need small but real additions to either the frontend or the data model before they can be wired up at all.
