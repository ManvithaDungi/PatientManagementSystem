-- =================================================================================
-- DEVELOPMENT-ONLY DEMO DATA  (never run this against a real or production database)
--
-- Matches the Flyway schema (V1..V5). Inserts only; it never drops or alters anything.
-- Every account uses @example.test addresses and ONE password hash that you supply, so
-- no password is stored in this repository:
--
--   1. Pick a password and obtain its Argon2id hash, e.g. register once through the app and copy
--      the value:  SELECT password_hash FROM login WHERE email = '<your test user>';
--      (or start the backend with SPRING_PROFILES_ACTIVE=dev, which creates demo accounts with a
--       generated password and logs it once - see DevDataSeeder)
--   2. Run:
--        psql "$DATABASE_URL" -v pw_hash='<the argon2 hash>' -f db/seed_users.sql
--
-- Re-running is safe: every statement is skipped when the row already exists.
-- =================================================================================

\if :{?pw_hash}
\else
  \echo 'Missing variable: pass -v pw_hash=<argon2 hash>'
  \quit
\endif

-- ---------------------------------------------------------------------------------
-- 1. LOGIN ACCOUNTS (2FA is on for doctors and the administrator)
-- ---------------------------------------------------------------------------------
INSERT INTO login (email, password_hash, role, two_factor_enabled, failed_attempts, is_locked, archived, created_at, last_login_at)
SELECT v.email, :'pw_hash', v.role, v.two_fa, 0, FALSE, FALSE, now(), now()
FROM (VALUES
    ('admin@example.test',   'ADMIN',          TRUE),
    ('doctor1@example.test', 'DOCTOR',         TRUE),
    ('doctor2@example.test', 'DOCTOR',         TRUE),
    ('nurse1@example.test',  'NURSE',          FALSE),
    ('lab1@example.test',    'LAB_TECHNICIAN', FALSE),
    ('patient1@example.test','PATIENT',        FALSE),
    ('patient2@example.test','PATIENT',        FALSE)
) AS v(email, role, two_fa)
WHERE NOT EXISTS (SELECT 1 FROM login l WHERE lower(l.email) = lower(v.email));

-- ---------------------------------------------------------------------------------
-- 2. DOCTOR PROFILES (Monday-Friday, 09:00-17:00, 30-minute slots)
-- ---------------------------------------------------------------------------------
INSERT INTO doctor_profiles (user_id, first_name, last_name, specialty, department, contact_number,
                             shift_start_time, shift_end_time, slot_duration_minutes)
SELECT l.user_id, v.first_name, v.last_name, v.specialty, v.department, v.phone, '09:00', '17:00', 30
FROM (VALUES
    ('doctor1@example.test', 'Alex',  'Morgan', 'Cardiology',       'Cardiology',       '+00 000 0001'),
    ('doctor2@example.test', 'Sam',   'Rivera', 'General Practice', 'General Practice', '+00 000 0002')
) AS v(email, first_name, last_name, specialty, department, phone)
JOIN login l ON l.email = v.email
WHERE NOT EXISTS (SELECT 1 FROM doctor_profiles d WHERE d.user_id = l.user_id);

INSERT INTO doctor_working_days (doctor_profile_id, working_days)
SELECT d.profile_id, day
FROM doctor_profiles d
JOIN login l ON l.user_id = d.user_id AND l.email IN ('doctor1@example.test', 'doctor2@example.test')
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY')) AS days(day)
WHERE NOT EXISTS (SELECT 1 FROM doctor_working_days w WHERE w.doctor_profile_id = d.profile_id);

-- ---------------------------------------------------------------------------------
-- 3. PATIENT PROFILES with their care team (assigned doctor and nurse)
-- ---------------------------------------------------------------------------------
INSERT INTO patient_profiles (user_id, first_name, last_name, date_of_birth, gender, contact_number, address,
                              medical_history, assigned_doctor_id, assigned_nurse_id, created_at)
SELECT p.user_id, v.first_name, v.last_name, v.dob::date, v.gender, v.phone, v.address, v.history,
       (SELECT user_id FROM login WHERE email = v.doctor_email),
       (SELECT user_id FROM login WHERE email = 'nurse1@example.test'),
       now()
FROM (VALUES
    ('patient1@example.test', 'Pat',   'Example', '1985-04-12', 'Female', '+00 000 1001', '1 Example Street', 'Penicillin allergy',  'doctor1@example.test'),
    ('patient2@example.test', 'Chris', 'Sample',  '1992-09-30', 'Male',   '+00 000 1002', '2 Example Street', 'Type 2 diabetes',    'doctor2@example.test')
) AS v(email, first_name, last_name, dob, gender, phone, address, history, doctor_email)
JOIN login p ON p.email = v.email
WHERE NOT EXISTS (SELECT 1 FROM patient_profiles x WHERE x.user_id = p.user_id);

-- ---------------------------------------------------------------------------------
-- 4. A scheduled appointment per patient (next weekday at 10:00 would be ideal; here: tomorrow)
-- ---------------------------------------------------------------------------------
INSERT INTO appointments (patient_profile_id, doctor_id, appointment_date, status, reason_for_visit, created_at, active_slot_key)
SELECT pp.profile_id, pp.assigned_doctor_id,
       (date_trunc('day', now()) + interval '1 day 10 hours' + (pp.profile_id * interval '30 minutes')),
       'SCHEDULED', 'Routine check-up', now(),
       pp.assigned_doctor_id || '|' || (date_trunc('day', now()) + interval '1 day 10 hours' + (pp.profile_id * interval '30 minutes'))
FROM patient_profiles pp
JOIN login l ON l.user_id = pp.user_id AND l.email IN ('patient1@example.test', 'patient2@example.test')
WHERE NOT EXISTS (SELECT 1 FROM appointments a WHERE a.patient_profile_id = pp.profile_id);

-- ---------------------------------------------------------------------------------
-- 5. Clinical samples: one vital-sign set and one prescription per patient
-- ---------------------------------------------------------------------------------
INSERT INTO vital_signs (patient_profile_id, nurse_id, blood_pressure, heart_rate, temperature, respiratory_rate,
                         oxygen_saturation, recorded_at)
SELECT pp.profile_id, (SELECT user_id FROM login WHERE email = 'nurse1@example.test'), '120/80', 72, 98.4, 16, 98, now()
FROM patient_profiles pp
JOIN login l ON l.user_id = pp.user_id AND l.email IN ('patient1@example.test', 'patient2@example.test')
WHERE NOT EXISTS (SELECT 1 FROM vital_signs v WHERE v.patient_profile_id = pp.profile_id);

INSERT INTO prescriptions (patient_profile_id, doctor_id, medication_name, dosage, frequency, duration, route,
                           status, issued_at, start_date, refills_remaining)
SELECT pp.profile_id, pp.assigned_doctor_id, 'Metformin', '500mg', 'Twice daily', '30 days', 'Oral',
       'ACTIVE', now(), now(), 2
FROM patient_profiles pp
JOIN login l ON l.user_id = pp.user_id AND l.email = 'patient2@example.test'
WHERE NOT EXISTS (SELECT 1 FROM prescriptions r WHERE r.patient_profile_id = pp.profile_id);
