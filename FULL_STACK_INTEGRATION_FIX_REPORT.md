# Full-Stack Integration Fix Report
### PatientManagementSystem (React + Spring Boot + PostgreSQL)
Fix pass date: 2026-09-29 — source audit: `FULL_STACK_INTEGRATION_AUDIT.md`

This report documents the outcome of a systematic pass over every issue confirmed in
`FULL_STACK_INTEGRATION_AUDIT.md`. Every issue is accounted for below as **FIXED**,
**PARTIALLY FIXED**, or **NOT ADDRESSED** (with a reason) — none were silently dropped.

**No git commits were made during this work**, per instruction. All changes are in the working tree.

---

## 0. Verification Performed

- **Backend compiles cleanly**: `mvn compile` — `BUILD SUCCESS`, 105 source files, zero errors.
  (Verified with a local JDK 17 by temporarily lowering `pom.xml`'s `<release>` from 21→17,
  since no JDK 21 was available in this environment; the change was fully reverted afterward —
  `pom.xml` carries no net diff other than a trailing newline.)
- **Backend tests pass**: `mvn test` — **164/164 tests, 0 failures, 0 errors, `BUILD SUCCESS`**
  (same JDK 17 substitution). One existing test (`AppointmentServiceTest`) needed updating for
  a changed method signature — see BUG-DB-1/BUG-018 below.
- **Frontend builds cleanly**: `npm run build` — succeeds, only pre-existing lint warnings
  (unused imports in two nurse components, one loose-equality warning in `Appointments.jsx`
  that predates this work and is intentional there for type coercion) — no new warnings introduced.
- **Frontend tests pass**: `npm test` — **353/353 tests, 64/64 suites pass**. One test
  (`MedicalHistoryList.test.jsx`) encoded the exact field-mismatch bug being fixed (DR-14) and
  was updated to assert against the corrected, real DTO shape rather than the old broken one.

---

## 1. Critical & High-Severity Findings

### BUG-001 / LA-1 — Lab result file attachments discarded
**Original Problem:** `UploadResults.jsx` captured a file but always sent `fileUrl: null`; the backend upload endpoint existed but had no frontend caller.
**Status:** ✅ FIXED
**Changes Made:**
- Added `filesAPI.upload(file)` to `api.js` — POSTs `FormData` to `/api/files/upload`, returns the stored filename.
- `apiCall`'s `Content-Type` header is now conditional — skipped for `FormData` bodies so the browser can set its own multipart boundary (this was BUG-002/LA-2, fixed as part of the same change).
- `UploadResults.jsx`'s `handleSubmit` now uploads the selected file first (if present), then passes the real returned filename as `fileUrl` to `uploadResults()`.
- Updated the stale "not supported yet" help text and wired the error banner to show real failure messages.
**Files Modified:** `frontend/app/src/services/api.js`, `frontend/app/src/pages/lab/UploadResults.jsx`
**Verification:** Frontend builds and all tests pass. Backend endpoint (`FileUploadController`) was already correct and unchanged.

### BUG-002 / LA-2 — `apiCall` couldn't send multipart requests
**Status:** ✅ FIXED (as part of BUG-001, above).

### LA-3 — Dead "View Report" links (plain `<a href>` can't carry the bearer token)
**Original Problem:** `GET /api/files/{filename}` requires an `Authorization` header; a plain anchor tag navigation can't supply one, so the link would 401/403 even once BUG-001 was fixed.
**Status:** ✅ FIXED
**Changes Made:** Added `filesAPI.getObjectUrl(filename)` (authenticated fetch → blob → `URL.createObjectURL`). `OrderDetail.jsx` and `History.jsx`'s "View Report" affordances now call this and open the result in a new tab, revoking the object URL after 60s.
**Files Modified:** `frontend/app/src/services/api.js`, `frontend/app/src/pages/lab/OrderDetail.jsx`, `frontend/app/src/pages/lab/History.jsx`

### BUG-003 / SH-1 — Access token never refreshed (forced logout every 15 minutes)
**Status:** ✅ FIXED
**Changes Made:** `apiCall` now attempts `POST /api/auth/refresh-token` once on a 401 (skipped for `/auth/*` endpoints and already-retried requests, to avoid loops), updates the stored access token on success, and retries the original request once. Concurrent 401s share a single in-flight refresh promise rather than each firing their own. Only if the refresh itself fails does the app clear storage and redirect to `/login`.
**Files Modified:** `frontend/app/src/services/api.js`
**Verification:** Logic is unit-testable via the existing auth regression test file; full frontend suite (353 tests) still passes. **UNVERIFIED / NEEDS RUNTIME TEST**: an actual 15-minute-expiry browser session was not run end-to-end in this environment (no way to fast-forward JWT expiry in a static build check) — the refresh call shape was verified to match `AuthController.refreshToken`'s real response (`{accessToken, role, status, userId}`) and cookie behavior exactly.

### BUG-004 / SH-2 — `resend-otp` called a non-existent backend endpoint
**Status:** ✅ FIXED
**Changes Made:** Added `AuthService.resendOtp(email)` (mirrors the OTP-generation branch of `login`) and `POST /api/auth/resend-otp` in `AuthController`. The frontend (`supabaseAuth.resendOtp`) already called this exact path — no frontend change was needed.
**Files Modified:** `backend/Backend/.../service/AuthService.java`, `backend/Backend/.../controller/AuthController.java`

### BUG-005 / SH-4 — Four generic `PUT` endpoints referenced by the frontend didn't exist
**Status:** ⚠️ PARTIALLY FIXED — resolved per-endpoint on its actual merits, not uniformly
**Changes Made:**
- **`PUT /api/prescriptions/{id}`** — **implemented** (`PrescriptionService.updatePrescription`, doctor-owner-checked, mirrors `refillPrescription`'s authorization pattern). This directly fixes **DR-3**.
- **`PUT /api/medical-records/{id}`** — **implemented** (`MedicalRecordService.updateMedicalRecord`, doctor-owner-checked).
- **`PUT /api/lab-results/{id}`** and **`PUT /api/vital-signs/{id}`** — confirmed via grep that **no frontend page anywhere calls these** (`labResultAPI.update`/`vitalSignsAPI.update` were dead code with zero call sites). Per the audit's own "do not create unnecessary APIs" instruction, these two dead frontend functions were **removed** rather than building unused backend endpoints for them. A comment explains why and what would be needed if a real requirement for in-place lab-result/vitals editing emerges.
**Files Modified:** `PrescriptionController.java`, `PrescriptionService.java`, `MedicalRecordController.java`, `MedicalRecordService.java`, `frontend/app/src/services/api.js`
**Notes:** This is the right call architecturally (no dead APIs on either side) but is flagged as "partial" against the audit's literal wording since two of the four were removed rather than implemented.

### SH-5 — `patientAPI.delete` targets a `DELETE /api/patients/{id}` that doesn't exist
**Status:** ✅ FIXED (by removal, not implementation)
**Changes Made:** Confirmed via grep that no frontend page calls `patientAPI.delete`. The backend explicitly documents (in a code comment) that patient deletion is deliberately unsupported — "records are deactivated, not deleted, per hospital policy." Removed the dead frontend function with an explanatory comment rather than building a deletion endpoint that contradicts stated backend policy.
**Files Modified:** `frontend/app/src/services/api.js`

### DR-1 / BUG-006 — Doctor's own profile resolved via the wrong ID space (CRITICAL)
**Original Problem:** `Profile.jsx` passed `user.userId` (`Login.userId`) to `GET /api/doctors/{id}`, which expects `DoctorProfile.profileId` — an independently-incrementing sequence. A doctor could load another doctor's identity.
**Status:** ✅ FIXED
**Changes Made:**
- Added `GET /api/doctors/me` and `PUT /api/doctors/me`, resolved server-side via JWT identity (`Authentication.getName()` → email → `Login` → `DoctorProfile`), exactly matching the pattern every other role's `/me` endpoint already uses (`PatientController.getMyProfile`, etc.).
- Added `DoctorService.getMyProfile(email)` / `updateMyProfile(email, dto)`.
- Added a `userId` field to `DoctorDTO` (alongside the existing `id`, which is now documented as `profileId`) so any future caller can tell the two ID spaces apart explicitly.
- Rewrote `Profile.jsx` to call `api.doctors.getMe()` / `updateMe()` instead of guessing an ID.
- The original `GET/PUT /api/doctors/{id}` (keyed on `profileId`) is left intact and unchanged — it's still needed for admin-driven doctor management — but a doctor viewing their *own* profile no longer goes through it.
**Files Modified:** `DoctorController.java`, `DoctorService.java`, `DoctorDTO.java`, `frontend/app/src/pages/doctor/Profile.jsx`, `frontend/app/src/services/api.js`
**Verification:** Backend compiles and its existing tests pass. This is the single highest-severity fix in this pass.
**Note — DB-1 (the structural root cause) is only partially resolved:** the underlying two-ID-space ambiguity between `DoctorProfile.profileId` and `Login.userId` still exists everywhere else in the codebase (booking, patient lists, etc., which all correctly use `Login.userId` already per the original audit's DB-1 analysis). This fix adds a safe, ambiguity-free path for the one case that was actually broken (a doctor loading their own profile) without attempting the larger, riskier refactor of unifying the ID convention system-wide, which was judged out of scope for this pass.

### DR-2 — Doctor "Save Changes" never persisted
**Status:** ✅ FIXED (as part of DR-1's `Profile.jsx` rewrite — form is now controlled state, `handleSave` calls `api.doctors.updateMe(...)`, shows a saving/error state). Also removed the fabricated "License #MD-12345-NY", "15 Years Experience", "Verified" badge, and two fake non-functional toggle switches ("Accepting New Patients" / "Show Phone Number") that had no backing field or handler — replaced with a real read-only Schedule card sourced from `DoctorDTO`'s actual shift fields.

### DR-3 / BUG-007 — Doctor "Manage Prescription" update always failed
**Status:** ✅ FIXED (backend endpoint added — see BUG-005/SH-4 above). `Prescriptions.jsx`'s existing call to `api.prescriptions.update(...)` now succeeds against a real endpoint; no frontend change was needed there since the call shape already matched.

### DR-4 / BUG-008 — Doctor appointment Cancel/Check-In never persisted
**Status:** ✅ FIXED
**Changes Made:** `confirmCancel()` now calls `api.appointments.cancel(id)`; `handleComplete()` now calls the newly-added `api.appointments.complete(id)` wrapper (backend endpoint `PUT /appointments/{id}/complete` already existed and was DOCTOR-authorized — it simply had no frontend caller). Both now handle failure with a visible error banner instead of silently updating local state regardless of outcome.
**Files Modified:** `frontend/app/src/pages/doctor/Appointments.jsx`, `frontend/app/src/services/api.js`

### DR-5 / BUG-009 — Prescriptions from the patient chart were never saved (highest patient-safety risk in the audit)
**Status:** ✅ FIXED
**Changes Made:** `PatientDetail.jsx` was rewritten to use the already-correctly-built `PrescriptionModal.jsx` (which calls `api.prescriptions.create` for real) instead of its own broken inline form that only updated local React state with a hardcoded `'Dr. Smith'` prescriber. Same treatment applied to vitals (`VitalSignModal.jsx`) and medical records (`MedicalRecordModal.jsx`, which also fixes the "no way to add a medical record from the doctor UI" gap noted in the audit). All three modals now refresh the page's real data from the server after a successful save instead of trusting a locally-fabricated object.
**Files Modified:** `frontend/app/src/pages/doctor/PatientDetail.jsx`, `frontend/app/src/components/doctor/PrescriptionModal.jsx` (route field + real response usage)

### DR-6 / BUG-014 — Hardcoded vitals for every patient
**Status:** ✅ FIXED (as part of the DR-5 `PatientDetail.jsx` rewrite). The "Vitals & Condition" card now fetches `api.vitalSigns.getLatest(id)` and renders the real blood pressure / heart rate / weight / recorded-at timestamp, with an honest "No vitals recorded yet" empty state instead of fabricated numbers.

### DR-13 — `PatientDetail.jsx` silently fell back to mock clinical data on any API error
**Status:** ✅ FIXED (as part of the same rewrite). All mock imports (`mocks/patients.js`, `mocks/records.js`) were removed; a genuine fetch failure now shows a visible error banner with a Retry button instead of substituting fabricated patient data for a clinician to act on.

### DR-9 — `AppointmentDTO` had no `patientId`
**Status:** ⚠️ PARTIALLY FIXED
**Changes Made:** Added `patientId` to `AppointmentDTO` and populated it in the consolidated `toDTO()` mapper (see BUG-018 below) — the backend now supports doctor→patient deep-linking from an appointment.
**Not done:** The doctor-facing `Appointments.jsx`/`Dashboard.jsx` UI was not updated to actually add a "View Patient" link using this new field — that frontend wiring was deprioritized against higher-severity items in the remaining time for this pass.

### DR-14 / BUG-010 — Medical history rendered blank for real data (field-name mismatch)
**Status:** ✅ FIXED
**Changes Made:** `MedicalHistoryList.jsx` now reads the real `MedicalRecordDTO` fields (`diagnosis`, `symptoms`, `treatmentProvided`, `recordDate`, `doctorName`, `recordId`) instead of the never-existent `type`/`note`/`date`/`id`. Updated `PatientDetail.jsx`'s "Recent Activity" summary the same way. Updated the corresponding test (`MedicalHistoryList.test.jsx`) to assert against the corrected, real shape — the old test was asserting the bug itself.
**Files Modified:** `frontend/app/src/components/doctor/MedicalHistoryList.jsx`, `frontend/app/src/components/doctor/MedicalHistoryList.test.jsx`, `frontend/app/src/pages/doctor/PatientDetail.jsx`

### DR-12 / SH-8 — `Prescriptions_Fixed.jsx` dead code
**Status:** ✅ FIXED — file deleted (confirmed zero importers via repo-wide grep; had no associated test file).
**Files Modified:** deleted `frontend/app/src/pages/doctor/Prescriptions_Fixed.jsx`

### NR-1 / BUG-011 — Nurse dashboard crash on "For Next Shift" tab
**Status:** ✅ FIXED (as part of a full rewrite of `pages/nurse/Dashboard.jsx` — see NR-2 below; `assignedPatients` is no longer referenced from a `.map()` call on possibly-undefined state, and the whole duplicated tab UI it belonged to was replaced with a read-only real-data summary).

### NR-2 / BUG-012 — Nurse Dashboard's Tasks/Handover widgets were dead duplicate UI
**Status:** ✅ FIXED
**Changes Made:** `pages/nurse/Dashboard.jsx` was substantially rewritten:
- Tasks are now fetched for real via `api.nurse.getTasks()`; the completion checkbox calls the real `api.nurse.toggleTaskStatus(taskId)` (optimistic update, reverted with a toast on failure) instead of only mutating local state with a `// TODO`.
- Handover notes are now fetched for real via `api.nurse.getHandoverNotes()` and rendered read-only (author, timestamp, content) instead of a fake `generalNotes`/`patientNotes` authoring UI with no backend support for any of its actions (mark-as-read, save draft, add/edit/delete patient note all had `// TODO` comments and no real endpoint).
- Rather than duplicating `Tasks.jsx`/`ShiftHandover.jsx`'s already-correct write flows a second time on the Dashboard, the Dashboard now links out to those pages ("View All Tasks", "View / Add Notes") — the alternative the audit itself proposed as acceptable.
- Removed all hardcoded filler: Task Categories counts (3/2/1/1), "Show Completed (12)", "Last updated Feb 9... by Nurse Sarah Chen", "3 unread notes", "Auto-save every 30 seconds" — none of these were ever computed from real state.
- The 4 Shift Snapshot stat cards are now wired to navigate to the relevant page (`onClick`), fixing **NR-9** in the same pass.
- The non-functional "Call Code Team" `alert('...placeholder')` button (**NR-8**) is now visibly disabled with a tooltip explaining escalation isn't wired to a real paging system, rather than presenting a decorative click as if it did something.
**Files Modified:** `frontend/app/src/pages/nurse/Dashboard.jsx`

### NR-3 — `nurse?.username` field mismatch (always `undefined`)
**Status:** ✅ FIXED — `VitalsEntry.jsx` now reads the real flat `nurseEmail` field from `VitalSignDTO` instead of a `nurse.username` object that was never sent. (The other occurrence of this bug was in `pages/nurse/PatientDetails.jsx`, a confirmed-dead duplicate file — see NR-12 — which was deleted rather than independently patched.)
**Files Modified:** `frontend/app/src/pages/nurse/VitalsEntry.jsx`

### NR-4 / BUG-013 — Medication administration entirely faked (highest patient-safety risk in the nurse role)
**Status:** ✅ FIXED — new backend feature implemented end-to-end
**Changes Made:**
- New `MedicationAdministration` entity (prescription FK, patient FK, nurse FK, `administeredAt`, `notes`), with `@JsonIgnoreProperties` guards on all three associations (see the security note in §2 below).
- New `MedicationAdministrationRepository`, `MedicationAdministrationDTO`.
- New `NurseService.recordMedicationAdministration(prescriptionId, nurseEmail, notes)` and `getAdministrationHistory(prescriptionId)`.
- New endpoints: `POST /api/nurse/medications/{prescriptionId}/administer`, `GET /api/nurse/medications/{prescriptionId}/history`.
- `api.js`'s `recordMedicationAdministration` now calls this real endpoint (previously posted to `/nurse/medications/record`, which never existed).
- `MedicationAdministration.jsx` was rewritten: it now determines "due" vs "administered today" from the real administration history per prescription (not a fabricated `prescription.status === 'Completed'` check that could never become true), and `confirmAdministration()` actually persists the dose via the new endpoint with a real error path shown to the nurse on failure.
**Files Modified (new):** `model/MedicationAdministration.java`, `repository/MedicationAdministrationRepository.java`, `dto/MedicationAdministrationDTO.java`
**Files Modified (existing):** `service/NurseService.java`, `controller/NurseController.java`, `frontend/app/src/services/api.js`, `frontend/app/src/pages/nurse/MedicationAdministration.jsx`
**Verification:** Backend compiles; `NurseServiceTest` (5 tests) still passes unchanged (no existing test covered the new method, so none needed updating).

### NR-5 — Medication `route` field fabricated as "Oral" for every prescription
**Status:** ✅ FIXED — added a genuine `route` column to `Prescription` (defaults to `"Oral"`, consistent with prior fallback behavior but now a real, settable value), exposed on `PrescriptionDTO`/`PrescriptionRequest`, and added a Route selector to the doctor's `PrescriptionModal.jsx` (replacing a dead, unused `quantity` field that was never part of the request contract anyway). `MedicationAdministration.jsx` now reads the real field instead of a hardcoded fallback.
**Files Modified:** `model/Prescription.java`, `dto/PrescriptionDTO.java`, `dto/PrescriptionRequest.java`, `service/PrescriptionService.java`, `frontend/app/src/components/doctor/PrescriptionModal.jsx`

### NR-12 — `pages/nurse/PatientDetails.jsx` (plural) dead duplicate
**Status:** ✅ FIXED — file deleted along with its dangling import in `App.jsx` (confirmed imported but never routed; no test file existed for it).
**Files Modified:** deleted `frontend/app/src/pages/nurse/PatientDetails.jsx`, `frontend/app/src/App.jsx`

### DB-9 / BUG-017 — Password reset didn't revoke existing sessions (security)
**Status:** ✅ FIXED — `AuthService.invalidateAllUserSessions()` was a fully empty method with a comment describing what it *should* do; it now calls the already-existing `sessionRepository.revokeAllUserSessions(user.getUserId())` and clears the idle-session tracker. A stolen session token is now actually invalidated when the legitimate user resets their password.
**Files Modified:** `backend/Backend/.../service/AuthService.java`
**Verification:** `AuthServiceTest` (22 tests) passes unchanged.

### DB-2 / BUG-018 — "Doctor name" fields across 5+ DTOs were actually the doctor's email
**Status:** ✅ FIXED — broad, systemic fix across every affected service
**Changes Made:** Added a `resolveDoctorName(Login)` / `resolveOrderedByName(Login)` helper to each affected service, resolving the real name via `DoctorProfileRepository.findByUser(...)` and falling back to email only if no profile row exists:
- `AppointmentService` — `AppointmentDTO.doctorName`
- `PrescriptionService` — `PrescriptionDTO.doctorName`
- `MedicalRecordService` — `MedicalRecordDTO.doctorName`
- `LabTestService` and `LabTechnicianService` — `LabTestDTO.orderedByName`/`orderedByDoctor` (also fixed the pre-existing bug where these two fields were populated by two different services in two different, mutually-exclusive ways — both are now always set consistently to the same resolved value, so a frontend reading either field gets a real answer regardless of which endpoint it called).
**Files Modified:** `AppointmentService.java`, `PrescriptionService.java`, `MedicalRecordService.java`, `LabTestService.java`, `LabTechnicianService.java`

### DB-3 — `MedicalRecordDTO.notes` was a fabricated duplicate of `symptoms`
**Status:** ✅ FIXED — added a genuine `notes` `TEXT` column to the `MedicalRecord` entity (and `MedicalRecordRequest`), so a doctor's free-text notes are now stored and returned independently of `symptoms`/`treatmentProvided` instead of the DTO silently mirroring `symptoms` into a `notes` field that implied separate data.
**Files Modified:** `model/MedicalRecord.java`, `dto/MedicalRecordDTO.java`, `dto/MedicalRecordRequest.java`, `service/MedicalRecordService.java`

### DB-4 — `MedicalRecordDTO.recordDate` was bound to `updatedAt` (drifted on every edit)
**Status:** ✅ FIXED — `recordDate` is now sourced from `createdAt` (the true encounter date); `updatedAt` is no longer conflated with it.
**Files Modified:** `service/MedicalRecordService.java`

### DB-12 — `AppointmentDTO` never exposed the populated `doctorNotes` column
**Status:** ✅ FIXED — `doctorNotes` (plus `appointmentType`, `specialRequirements`, and `cancellationReason` — see PT-2/PT-4 below) are now real columns on `Appointment` and exposed on `AppointmentDTO`, settable via `updateAppointment` (doctor notes) and `AppointmentRequest` (type/special requirements at booking time).
**Files Modified:** `model/Appointment.java`, `dto/AppointmentDTO.java`, `dto/AppointmentRequest.java`, `service/AppointmentService.java`

---

## 2. Additional Security Finding (discovered during this fix pass, not in the original audit)

### SECURITY-1 (new) — Raw JPA entities leaked `Login.passwordHash`/`otp` in JSON responses
**Problem discovered:** While implementing the new `PUT` endpoints above, it became apparent that `PrescriptionController.getById/createPrescription`, `MedicalRecordController.getById/createMedicalRecord`, `LabResultController.getById/createLabTest`, and `AppointmentController.createAppointment/completeAppointment/updateAppointment/cancelAppointment` all returned the **raw JPA entity** directly. Unlike `Consent`, `HandoverNote`, `NurseTask`, and `PatientProfile` (which all already carry `@JsonIgnoreProperties` guards on their `Login`-typed associations), `Prescription`, `MedicalRecord`, `LabTest`, and `Appointment` had **no such guard** — meaning any of these endpoints would serialize the nested `doctor`/`patient`/`orderedBy` `Login` object's `passwordHash`, `otp`, and `otpExpiry` fields directly into the API response.
**Status:** ✅ FIXED
**Changes Made:**
1. Every one of the affected services was refactored to route all reads *and* writes through a single, consistent `mapToDTO()` (or `toDTO()`) private method — never returning the raw entity. This has the added benefit of eliminating the copy-pasted mapping code the original audit flagged as a duplicate-code issue in `LabTestService` and elsewhere.
2. `@JsonIgnoreProperties` guards were also added directly to `Appointment.patient`/`Appointment.doctor` as defense-in-depth, matching the pattern already used on the other entities.
3. The new `MedicationAdministration` entity was built with these guards from the start.
**Files Modified:** `PrescriptionService.java`, `PrescriptionController.java`, `MedicalRecordService.java`, `MedicalRecordController.java`, `LabTestService.java`, `LabResultController.java`, `LabTechnicianService.java`, `AppointmentService.java`, `AppointmentController.java`, `model/Appointment.java`
**Impact if unfixed:** Any authenticated user able to reach any of these ~10 endpoints could have retrieved the Argon2 password hash and any pending OTP for the doctor/nurse/lab-tech/admin/patient involved in that record. This was a real, exploitable data exposure in the pre-existing code, independent of anything in the original audit.

---

## 3. Confirmed Issues NOT Addressed in This Pass

Per the task's own instruction ("must either be fixed or explicitly documented as blocked"), every remaining confirmed issue from the audit is listed here with a reason. None require architectural changes beyond what's described — they were deprioritized purely by severity and remaining time in this session, not because they are unfixable.

| ID | Issue | Why deferred |
|---|---|---|
| SH-7 (Messages.jsx part) | Doctor `Messages.jsx` is 100% mock with no backend | No backend messaging feature exists at all (not a wiring gap — a missing product feature); building one was out of scope for an integration-fix pass. Recommend either building a minimal `MessageController`+entity or replacing the page with an explicit "Coming soon" state. |
| SH-9 | Two parallel auth client modules (`api.js authAPI` vs `supabaseAuth.js`), misleading naming | Cosmetic/maintainability issue with no functional bug; renaming `supabaseAuth.js` and consolidating touches many import sites — deferred to avoid unnecessary churn under "smallest necessary change" guidance. |
| SH-10 | `REACT_APP_API_URL` has no safe fallback default; 3 env-example files disagree on port | Config/documentation issue, not a code-path bug in the audited flows; low risk given `.env` (the file actually used) is already correct. |
| PT-6 | `GrantModifyConsent.jsx` category-lookup mismatch shows wrong consent-form content | `ConsentManagement.jsx`/`GrantModifyConsent.jsx` together are ~2,300 lines with the heaviest concentration of hardcoded/mock content in the app; a correct fix requires either a full category-mapping rewrite or replacing the mock-driven form content with backend-aligned copy — judged too large to do safely in the remaining time without dedicated review. |
| PT-7 | No UI path to grant a first-time consent to a new provider | Same file as PT-6; requires a new provider-picker UI flow, not just a bug fix. |
| PT-8, PT-9, PT-10, PT-11 | Hardcoded refill date; dead `.active` filter; unused `PatientProfileRequest` DTO; `usePatientProfile` hook duplication | All LOW severity, cosmetic/cleanup items with no functional impact (each falls back correctly to real data already) — deprioritized below every MEDIUM+ item. |
| DR-7, DR-8 | Doctor Dashboard "Pending Labs: 5" and fabricated "Daily Briefing" panel | `Dashboard.jsx` (doctor) was not touched in this pass; both are quick, low-risk fixes (wire `api.labResults.getAll()`/filter by status; remove or compute the briefing panel) recommended as the next quick win. |
| DR-9 (frontend half) | No "View Patient" deep-link from an appointment row using the now-available `patientId` | Backend fix landed (see above); frontend UI wiring deferred. |
| DR-10, DR-11 | Doctor `Messages.jsx` (see SH-7) and `Reports.jsx` are fully fake, no backend | Both require new backend features (messaging, reporting), not integration fixes to existing ones. |
| NR-6 | No room/bed/acuity/code-status/allergy data model exists on `PatientProfile` | This is a genuine, non-trivial schema and product-scope question (what fields, who edits them, migration strategy) rather than a pure bug fix — recommend a dedicated design pass before adding columns. |
| NR-7 | `medicalHistory` free-text field reused as both "diagnosis" and "care instructions" in different nurse pages | LOW severity semantic/labeling issue, no functional bug. |
| NR-11 | Nurse `Profile.jsx` is 100% hardcoded (license #, shift, ward, supervisor, phone) | Requires a new backend nurse-profile data model (mirrors the same gap doctors had before DR-1/DR-2, but nurses have no `DoctorProfile`-equivalent table at all yet) — a schema addition + new endpoint, deferred as its own follow-up. |
| NR-13, NR-14, NR-15 | Vitals `notes`/`painLevel` captured in the UI but dropped before the API call; `weight`/`height` supported by the backend but no nurse form collects them | Requires extending `VitalSignRequest`/`VitalSign`/`VitalSignDTO` with `notes`/`painLevel` columns and updating `Vitals.jsx`'s payload — a contained, low-risk fix recommended as a follow-up but not completed in this pass. |
| LA-4 | Lab status filter/badges don't handle `Processing`/`Cancelled`; backend accepts any string for status | Requires either a `LabTestStatus` enum (schema-level enforcement) or extending the frontend's status-color maps — deferred as MEDIUM-priority polish. |
| LA-5, LA-6 | `AuditLogs.jsx`, `CompliancePanel.jsx`, `SystemOverview.jsx`, `UserManagement.jsx` silently substitute fabricated data on API failure | Each needs its `catch` block changed from "inject fake rows" to "show a visible error state" — mechanically simple but touches 4 separate admin components; deferred behind the higher-severity clinical-safety fixes (NR-4, DR-5, DR-6) that were prioritized instead. |
| LA-7, LA-7a | `SystemHealth.jsx`, `IncidentManagement.jsx`, parts of `SystemOverview.jsx`/`CompliancePanel.jsx` are 100% fabricated with no backend at all | No backend endpoint exists for any of these (server health/incidents/compliance-score/trend-deltas) — this is new-feature work, not an integration fix. |
| LA-8 | `UserManagement.jsx` Edit/More-options buttons have no `onClick` despite working backend endpoints (`updateStaffRole`/`deleteStaff`) | Low-risk, contained fix (wire two buttons to already-correct `api.admin.*` calls) recommended as a quick follow-up; not completed in this pass due to time. |
| LA-9 | `UserManagement.jsx` "Status"/"Last Login" columns are fake for every row, even on the happy path | Requires either removing the columns or adding real fields to `StaffDTO`/`Login` — deferred as LOW severity. |
| LA-10 | Lab/Admin `Profile.jsx` pages are 100% static with no backend endpoint | Same category as NR-11 — needs a new backend profile model for these two roles; deferred as its own follow-up. |
| DB-5 | No `LabTestStatus` enum; lab dashboard "Collected"/"Results Pending" buckets structurally can never be populated | Requires a schema-level enum change across `LabTest.status`; deferred as a moderate-risk schema change outside this pass's scope. |
| DB-6 | No Flyway/Liquibase; `ddl-auto=update` + a manually-maintained `schema.sql` is a schema-drift risk | This is an infrastructure/tooling decision (introducing a migration framework) explicitly beyond "fix the identified bugs" — flagged for a dedicated follow-up, not attempted here per the instruction to avoid unnecessary schema/infra changes. |
| DB-7, DB-8, DB-10, DB-11 | Boxed `Boolean archived`; `isLocked` Lombok-naming fragility; non-portable `LIMIT` in JPQL; dead `consent_log` table | All LOW severity, no confirmed functional bug (each works correctly today) — code-hygiene items deprioritized below every confirmed functional bug. |

**Dead-but-correct components not yet wired to a second call site:** `components/doctor/VitalSignModal.jsx` and `MedicalRecordModal.jsx` are now wired into `PatientDetail.jsx` (this pass); `components/appointments/AvailableSlotSelector.jsx` now has a working `api.appointments.getAvailableSlots` to call (fixed as PT-3) but is still not imported by any patient page — `Appointments.jsx` (patient) was given its own inline real-slot-fetching logic instead of adopting this component, since replacing an entire booking wizard's UI with a different component was judged riskier than extending the existing one in place. Recommend consolidating onto `AvailableSlotSelector` as a follow-up cleanup, not a bug fix.

---

## 4. Files Changed in This Pass

**Backend (Java) — modified:**
`pom.xml` (temporarily, fully reverted), `controller/{AppointmentController,AuthController,DoctorController,LabResultController,MedicalRecordController,NurseController,PrescriptionController}.java`, `dto/{AppointmentDTO,AppointmentRequest,DoctorDTO,MedicalRecordDTO,MedicalRecordRequest,PrescriptionDTO,PrescriptionRequest}.java`, `model/{Appointment,MedicalRecord,Prescription}.java`, `service/{AppointmentService,AuthService,DoctorService,LabTechnicianService,LabTestService,MedicalRecordService,NurseService,PrescriptionService}.java`, `resources/application.properties`, `test/.../AppointmentServiceTest.java`

**Backend (Java) — new:**
`model/MedicationAdministration.java`, `repository/MedicationAdministrationRepository.java`, `dto/MedicationAdministrationDTO.java`

**Frontend (React) — modified:**
`App.jsx`, `services/api.js`, `components/appointments/AvailableSlotSelector.jsx`, `components/doctor/{MedicalHistoryList.jsx,MedicalHistoryList.test.jsx,PrescriptionModal.jsx}`, `pages/doctor/{Appointments.jsx,PatientDetail.jsx,Profile.jsx}`, `pages/lab/{History.jsx,OrderDetail.jsx,UploadResults.jsx}`, `pages/nurse/{Dashboard.jsx,MedicationAdministration.jsx,VitalsEntry.jsx}`, `pages/patient/Appointments.jsx`

**Frontend (React) — deleted (confirmed dead, zero importers, no associated tests):**
`pages/doctor/Prescriptions_Fixed.jsx`, `pages/nurse/PatientDetails.jsx`

---

## 5. Final Verification Matrix

| Bug ID | Original Issue | Fixed | Verified | Files Changed |
|---|---|---|---|---|
| BUG-001 / LA-1 | Lab file attachments discarded | ✅ | ✅ build+tests | api.js, UploadResults.jsx |
| BUG-002 / LA-2 | apiCall can't send multipart | ✅ | ✅ build+tests | api.js |
| LA-3 | Dead "View Report" links (no auth) | ✅ | ✅ build+tests | api.js, OrderDetail.jsx, History.jsx |
| BUG-003 / SH-1 | Access token never refreshed | ✅ | ✅ build+tests / ⚠️ runtime expiry untested | api.js |
| BUG-004 / SH-2 | resend-otp endpoint missing | ✅ | ✅ compile+tests | AuthService.java, AuthController.java |
| BUG-005 / SH-4 | 4 missing PUT endpoints | ⚠️ Partial (2 built, 2 removed) | ✅ compile+tests | Prescription/MedicalRecord Controller+Service, api.js |
| SH-5 | patientAPI.delete targets nonexistent endpoint | ✅ (removed) | ✅ build | api.js |
| DR-1 / BUG-006 | Doctor profile — wrong ID space (CRITICAL) | ✅ | ✅ compile+tests | DoctorController/Service/DTO, Profile.jsx, api.js |
| DR-2 | Doctor Save Changes doesn't persist | ✅ | ✅ build | Profile.jsx |
| DR-3 / BUG-007 | Prescription update endpoint missing | ✅ | ✅ compile+tests | PrescriptionController/Service |
| DR-4 / BUG-008 | Appointment cancel/complete not persisted | ✅ | ✅ compile+build | Appointments.jsx (doctor), api.js |
| DR-5 / BUG-009 | Prescriptions from chart never saved (patient safety) | ✅ | ✅ build | PatientDetail.jsx, PrescriptionModal.jsx |
| DR-6 / BUG-014 | Hardcoded vitals for every patient | ✅ | ✅ build | PatientDetail.jsx |
| DR-9 | AppointmentDTO missing patientId | ⚠️ Partial (backend only) | ✅ compile+tests | AppointmentDTO/Service |
| DR-12 / SH-8 | Prescriptions_Fixed.jsx dead code | ✅ (deleted) | ✅ build | (deleted) |
| DR-13 | Mock fallback masks real API failures | ✅ | ✅ build | PatientDetail.jsx |
| DR-14 / BUG-010 | MedicalHistory field mismatch → blank UI | ✅ | ✅ build+tests | MedicalHistoryList.jsx(+test), PatientDetail.jsx |
| PT-1 | Patient reschedule → 403 | ✅ | ✅ compile+build | AppointmentController/Service, Appointments.jsx (patient), api.js |
| PT-2 | Cancellation reason dropped | ✅ | ✅ compile+build | AppointmentController/Service, api.js |
| PT-3 | getAvailableSlots wrapper missing | ✅ | ✅ build | api.js, Appointments.jsx (patient), AvailableSlotSelector.jsx |
| PT-4 | appointmentType/specialRequirements not sent | ✅ | ✅ compile+build | Appointment model/DTO/Request, Appointments.jsx (patient) |
| PT-5 | Fake duration/location/room on appointment cards | ✅ | ✅ build | Appointments.jsx (patient) |
| PT-12 | Calendar-view status color mismatch | ✅ | ✅ build | Appointments.jsx (patient) |
| NR-1 / BUG-011 | Nurse dashboard crash | ✅ | ✅ build | Dashboard.jsx (nurse) |
| NR-2 / BUG-012 | Dead duplicate task/handover widgets | ✅ | ✅ build | Dashboard.jsx (nurse) |
| NR-3 | nurse.username field mismatch | ✅ | ✅ build | VitalsEntry.jsx |
| NR-4 / BUG-013 | Medication administration faked (patient safety) | ✅ | ✅ compile+tests | new MedicationAdministration entity/repo/DTO, NurseService/Controller, MedicationAdministration.jsx, api.js |
| NR-5 | Medication route fabricated | ✅ | ✅ compile+tests | Prescription model/DTO/Request/Service, PrescriptionModal.jsx |
| NR-8 | "Call Code Team" fake alert | ✅ (disabled honestly) | ✅ build | Dashboard.jsx (nurse) |
| NR-9 | Unwired quick-stat cards | ✅ | ✅ build | Dashboard.jsx (nurse) |
| NR-10 | Hardcoded task/note counts | ✅ (removed) | ✅ build | Dashboard.jsx (nurse) |
| NR-12 | PatientDetails.jsx dead duplicate | ✅ (deleted) | ✅ build | App.jsx, (deleted file) |
| DB-1 | Doctor ID-space ambiguity | ⚠️ Partial (doctor-self path fixed) | ✅ compile+tests | DoctorController/Service/DTO |
| DB-2 / BUG-018 | doctorName fields were actually emails | ✅ | ✅ compile+tests | Appointment/Prescription/MedicalRecord/LabTest Service |
| DB-3 | MedicalRecordDTO.notes fabricated duplicate | ✅ | ✅ compile+tests | MedicalRecord model/DTO/Request/Service |
| DB-4 | recordDate bound to wrong timestamp | ✅ | ✅ compile+tests | MedicalRecordService |
| DB-9 / BUG-017 | Password reset doesn't revoke sessions (security) | ✅ | ✅ compile+tests | AuthService |
| DB-12 | AppointmentDTO missing doctorNotes | ✅ | ✅ compile+tests | Appointment model/DTO/Service |
| SECURITY-1 (new) | Raw entities leaked passwordHash/otp | ✅ | ✅ compile+tests | Prescription/MedicalRecord/LabTest/Appointment Controller+Service, Appointment model |
| SH-3 | nurse medication endpoint self-admitted fake | ✅ | ✅ compile+tests | (same as NR-4) |
| SH-11 | Cookie Secure flag hardcoded false | ✅ | ✅ compile+tests | application.properties, AuthController |
| SH-6 / SH-7 (partial) | Duplicated data-loss logic / mock fallback | ✅ (PatientDetail.jsx part) | ✅ build | PatientDetail.jsx |
| SH-7 (Messages.jsx part), SH-9, SH-10, SH-12 | See §3 | ❌ Not addressed | — | — |
| PT-6, PT-7, PT-8, PT-9, PT-10, PT-11 | See §3 | ❌ Not addressed | — | — |
| DR-7, DR-8, DR-9 (frontend half), DR-10, DR-11 | See §3 | ❌ Not addressed | — | — |
| NR-6, NR-7, NR-11, NR-13, NR-14, NR-15 | See §3 | ❌ Not addressed | — | — |
| LA-4, LA-5, LA-6, LA-7, LA-7a, LA-8, LA-9, LA-10 | See §3 | ❌ Not addressed | — | — |
| DB-5, DB-6, DB-7, DB-8, DB-10, DB-11 | See §3 | ❌ Not addressed | — | — |
| LA-11 | False alarm (excluded from audit's own count) | N/A — confirmed non-issue | — | — |
| SH-12 | Informational only, no functional bug | N/A — no fix needed | — | — |

**Tally:** of the audit's 77 confirmed issues (plus 1 new security finding discovered in this pass, for 78 total): **~35 fully fixed, ~4 partially fixed, ~38 explicitly deferred with reasons, 2 confirmed non-issues requiring no action.**

---

## 6. Recommended Next Steps (in priority order)

1. **LA-5/LA-6** (replace admin mock-fallback-on-error with visible error states) — mechanically simple, prevents an admin from being misled by fake data during a real outage.
2. **LA-8** (wire UserManagement's Edit/Delete buttons to the already-correct backend endpoints) — very low effort, real admin capability gap.
3. **NR-13/NR-14** (persist vitals notes/pain level) — small, contained DTO/entity extension.
4. **DR-7/DR-8** (doctor dashboard real pending-labs count, remove fake daily briefing) — quick, isolated to one file.
5. **PT-6/PT-7** (consent category mapping + new-consent entry point) — larger, needs dedicated review of the ~2,300-line consent management surface before touching it safely.
6. **NR-11/LA-10** (nurse/lab/admin profile backend models) — each needs a small new entity + endpoint, same pattern as the doctor fix in this pass (DR-1/DR-2) — should be quick now that a working template exists.
7. **DB-6** (introduce Flyway/Liquibase) — infrastructure decision, do this before the schema accumulates further drift, but treat as its own project rather than bundling into a future bug-fix pass.
