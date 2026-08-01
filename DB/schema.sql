-- =================================================================================
-- PATIENT MANAGEMENT SYSTEM - Entity-Aligned Schema
-- Source of truth: backend JPA entities under backend/Backend/src/main/java/.../model
-- =================================================================================

-- 1. CLEANUP
DROP TABLE IF EXISTS uploaded_files CASCADE;
DROP TABLE IF EXISTS consent_log CASCADE;
DROP TABLE IF EXISTS doctor_working_days CASCADE;
DROP TABLE IF EXISTS patient_consents CASCADE;
DROP TABLE IF EXISTS nurse_tasks CASCADE;
DROP TABLE IF EXISTS handover_notes CASCADE;
DROP TABLE IF EXISTS archived_users CASCADE;
DROP TABLE IF EXISTS password_reset_tokens CASCADE;
DROP TABLE IF EXISTS password_history CASCADE;
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS lab_tests CASCADE;
DROP TABLE IF EXISTS vital_signs CASCADE;
DROP TABLE IF EXISTS prescriptions CASCADE;
DROP TABLE IF EXISTS medical_records CASCADE;
DROP TABLE IF EXISTS appointments CASCADE;
DROP TABLE IF EXISTS doctor_profiles CASCADE;
DROP TABLE IF EXISTS patient_profiles CASCADE;
DROP TABLE IF EXISTS sessions CASCADE;
DROP TABLE IF EXISTS login CASCADE;

DROP TYPE IF EXISTS request_status CASCADE;
DROP TYPE IF EXISTS user_role_type CASCADE;

-- 2. ENUMS (optional app-level compatibility)
CREATE TYPE request_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
CREATE TYPE user_role_type AS ENUM ('PATIENT', 'DOCTOR', 'NURSE', 'ADMIN', 'LAB_TECHNICIAN');

-- =================================================================================
-- CORE IDENTITY & SECURITY
-- =================================================================================

CREATE TABLE login (
    user_id BIGSERIAL PRIMARY KEY,
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    otp VARCHAR(10),
    otp_expiry TIMESTAMP,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    failed_attempts INT DEFAULT 0,
    is_locked BOOLEAN DEFAULT FALSE,
    lockout_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    archived BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    otp_secret VARCHAR(255)
);

CREATE TABLE sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(255) UNIQUE NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE password_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE archived_users (
    id BIGSERIAL PRIMARY KEY,
    original_user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    last_active_at TIMESTAMP,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    details TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================
-- CLINICAL PROFILES
-- =================================================================================

CREATE TABLE patient_profiles (
    profile_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    assigned_doctor_id BIGINT REFERENCES login(user_id),
    assigned_nurse_id BIGINT REFERENCES login(user_id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20),
    contact_number VARCHAR(20),
    address TEXT,
    medical_history TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE doctor_profiles (
    profile_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    specialty VARCHAR(100) NOT NULL,
    contact_number VARCHAR(20),
    department VARCHAR(100),
    shift_start_time TIME NOT NULL DEFAULT '09:00:00',
    shift_end_time TIME NOT NULL DEFAULT '17:00:00',
    slot_duration_minutes INT NOT NULL DEFAULT 30
);

CREATE TABLE doctor_working_days (
    doctor_profile_id BIGINT NOT NULL REFERENCES doctor_profiles(profile_id) ON DELETE CASCADE,
    working_days VARCHAR(20) NOT NULL
);

-- =================================================================================
-- CLINICAL WORKFLOW
-- =================================================================================

CREATE TABLE appointments (
    appointment_id BIGSERIAL PRIMARY KEY,
    patient_profile_id BIGINT NOT NULL REFERENCES patient_profiles(profile_id) ON DELETE CASCADE,
    doctor_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    appointment_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
    reason_for_visit TEXT,
    doctor_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE medical_records (
    record_id BIGSERIAL PRIMARY KEY,
    patient_profile_id BIGINT NOT NULL REFERENCES patient_profiles(profile_id) ON DELETE CASCADE,
    doctor_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    diagnosis VARCHAR(255) NOT NULL,
    symptoms TEXT,
    treatment_provided TEXT,
    attachment_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE prescriptions (
    prescription_id BIGSERIAL PRIMARY KEY,
    patient_profile_id BIGINT NOT NULL REFERENCES patient_profiles(profile_id) ON DELETE CASCADE,
    doctor_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    medication_name VARCHAR(255) NOT NULL,
    dosage VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL,
    duration VARCHAR(100) NOT NULL,
    special_instructions TEXT,
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    refills_remaining INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

CREATE TABLE vital_signs (
    vital_id BIGSERIAL PRIMARY KEY,
    patient_profile_id BIGINT NOT NULL REFERENCES patient_profiles(profile_id) ON DELETE CASCADE,
    nurse_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    blood_pressure VARCHAR(20),
    heart_rate INT,
    temperature DOUBLE PRECISION,
    respiratory_rate INT,
    oxygen_saturation INT,
    weight DOUBLE PRECISION,
    height DOUBLE PRECISION,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lab_tests (
    test_id BIGSERIAL PRIMARY KEY,
    patient_profile_id BIGINT NOT NULL REFERENCES patient_profiles(profile_id) ON DELETE CASCADE,
    ordered_by_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    test_name VARCHAR(255),
    test_category VARCHAR(100),
    result_value VARCHAR(255),
    unit VARCHAR(50),
    reference_range VARCHAR(100),
    remarks TEXT,
    status VARCHAR(50),
    file_url VARCHAR(255),
    ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE patient_consents (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patient_profiles(profile_id) ON DELETE CASCADE,
    granted_to_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    consent_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP,
    reason TEXT
);

CREATE TABLE handover_notes (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    patient_id BIGINT REFERENCES patient_profiles(profile_id) ON DELETE SET NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'general',
    priority VARCHAR(20) NOT NULL DEFAULT 'normal',
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shift_direction VARCHAR(50)
);

CREATE TABLE nurse_tasks (
    id BIGSERIAL PRIMARY KEY,
    assigned_nurse_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    patient_id BIGINT REFERENCES patient_profiles(profile_id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    due_time TIMESTAMP NOT NULL,
    completed BOOLEAN DEFAULT FALSE,
    status VARCHAR(50) DEFAULT 'upcoming',
    previous_status VARCHAR(50)
);

-- Ownership record for encrypted uploads in FileStorageService; the only way
-- to authorize a download before the file is linked to a medical record/lab test.
CREATE TABLE uploaded_files (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) UNIQUE NOT NULL,
    uploaded_by_id BIGINT NOT NULL REFERENCES login(user_id) ON DELETE CASCADE,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Legacy compatibility table still used by older code paths/scripts.
CREATE TABLE consent_log (
    log_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES login(user_id),
    consent_type VARCHAR(50) NOT NULL,
    is_granted BOOLEAN NOT NULL,
    ip_address VARCHAR(45),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
