# SecureHealth - Patient Management System
## A HIPAA-Aware Healthcare Platform with Regulatory Compliance

**Status:** Production-Ready (with noted improvements for scaling)  
**Last Updated:** April 20, 2026  
**Architecture:** React 19 + Spring Boot 3.2 + PostgreSQL + Docker

---

# Table of Contents
1. [Overview](#overview)
2. [Technology Stack](#technology-stack)
3. [Key Features](#key-features)
4. [User Roles & Workflows](#user-roles--workflows)
5. [Getting Started](#getting-started)
6. [Project Structure](#project-structure)
7. [Security Architecture](#security-architecture)
8. [API Documentation](#api-documentation)
9. [Database Schema](#database-schema)
10. [Deployment](#deployment)
11. [Testing](#testing)
12. [Known Issues & Improvements](#known-issues--improvements)
13. [Developer Notes](#developer-notes)

---

# Overview

**SecureHealth** is a comprehensive healthcare information management system that demonstrates enterprise-level security practices for handling sensitive medical data. The platform orchestrates complex workflows among multiple stakeholder roles while maintaining strict HIPAA-aligned access controls, audit trails, and data governance.

**Problem It Solves:**
- Healthcare systems need to manage interactions across patients, doctors, nurses, lab technicians, and administrators
- Each role needs different data visibility (patient can't see other patients, doctor can only see assigned patients)
- Every access must be logged for compliance
- Data must be encrypted, backed up, and retain-policy compliant

**Why It Matters:**
- Demonstrates compliance-driven architecture (not feature-first)
- Shows secure-by-default design (not bolted-on security)
- Handles real-world healthcare scenarios (double-booking prevention, consent tracking, shift handovers)

---

# Technology Stack

## Frontend
- **Framework:** React 19.2.3 (Latest, with improved performance)
- **Routing:** React Router v6.30.3 (client-side, with role-based guards)
- **Styling:** Tailwind CSS 3.4.19 (utility-first, with custom theme)
- **State Management:** React Context API (AuthContext, ThemeContext)
- **Data Visualization:** Recharts (appointment calendars, vital signs charts)
- **HTTP Client:** Fetch API with centralized error handling
- **Icons:** Lucide-react (professional healthcare icons)
- **Animations:** Framer Motion (smooth transitions)
- **Testing:** React Testing Library + Jest
- **API Mocking:** MSW (Mock Service Worker)

## Backend
- **Framework:** Spring Boot 3.2.2 (Latest stable LTS)
- **Language:** Java 21 (LTS release)
- **Database:** PostgreSQL 16 (with ACID transactions)
- **ORM:** Hibernate with Spring Data JPA
- **Authentication:** JWT (JJWT library) + Spring Security
- **Password Encoding:** Argon2 (NIST-recommended)
- **Email Service:** Spring Mail (OTP delivery)
- **Caching:** Redis (active defense, session storage)
- **Build Tool:** Maven (reproducible builds)
- **Testing:** JUnit 5 + Mockito + H2 in-memory DB
- **Monitoring:** Spring Actuator + Prometheus metrics

## Infrastructure
- **Containerization:** Docker (both frontend and backend)
- **Orchestration:** Docker Compose (PostgreSQL, Redis, Backend, Adminer)
- **CI/CD:** GitHub Actions (tests on push/PR)
- **Database GUI:** Adminer (development database inspection)

---

# Key Features

## Authentication & Security
- Email/password registration with 12-character minimum
- 2FA via email OTP for doctors and admins
- JWT access tokens (15-minute expiry) + refresh tokens (7-day)
- Account lockout after 5 failed attempts
- Password history (can't reuse last 5 passwords)
- Password reset with time-limited tokens
- Session management (max 3 concurrent sessions per user)
- Argon2 password hashing (GPU-resistant)

## Clinical Workflows
- **Appointment Scheduling:** Patients request appointments, doctors manage availability, admins approve
- **Prescriptions:** Doctors create prescriptions (medication, dosage, frequency, duration)
- **Vital Signs Recording:** Nurses enter BP, HR, temperature, O2 saturation, weight, height
- **Medical Records:** Doctors document diagnosis, symptoms, treatment provided
- **Lab Test Ordering:** Doctors order tests, lab techs process and upload results
- **Medication Administration:** Nurses track medication administration with timestamps
- **Shift Handover:** Nurses document handover notes for shift changes

## Role-Based Access Control
- **Patient:** Book appointments, view own records, manage medications, grant consents
- **Doctor:** Manage schedules, write prescriptions, access assigned patients
- **Nurse:** Record vitals, administer medications, document tasks
- **Lab Technician:** Process test orders, upload results
- **Admin:** Approve appointments, manage users, view audit logs

## Data Governance
- **Audit Logging:** Every access logged (who, what, when, where, why)
- **Consent Tracking:** Patient consent for data sharing tracked and logged
- **Automatic Backups:** Daily at 2 AM (configurable retention)
- **Data Archival:** Inactive users archived after 365 days
- **Password History:** Prevents password reuse (last 5 passwords)
- **IDOR Protection:** Patients can only access their own data

## Security Practices
- CORS configured (localhost:3000 default, configurable per environment)
- CSRF protection via stateless JWT (not vulnerable)
- Input validation on all DTOs
- Rate limiting on sensitive endpoints
- Token blacklist service (logout invalidates tokens)
- Audit logs encrypted in storage
- Patient record access protected by validator

---

# User Roles & Workflows

## 1. Patient Workflow
```
Register/Login → Select Role (Patient) → Set Profile → 
  Dashboard (upcoming appointments, vitals, medications) →
    Book Appointment (select doctor/date/reason) →
      View Prescriptions → 
        Track Medications → 
          View Lab Results → 
            Manage Consents
```

**Can Access:**
- Own appointment history
- Own medical records
- Own prescriptions
- Own vital signs
- Own lab results
- Data sharing consents

**Cannot Access:**
- Other patients' data
- Doctor schedules
- Lab infrastructure

---

## 2. Doctor Workflow
```
Register/Login (2FA required) → Set Profile (specialty, shifts) →
  Dashboard (assigned patients, appointment requests, metrics) →
    Manage Appointments (approve/reject requests) →
      View Patient Details (medical history, vitals, previous prescriptions) →
        Write Prescription → 
          Order Lab Tests →
            Review Lab Results →
              Create Medical Record
```

**Can Access:**
- Assigned patients' full medical history
- Appointment requests
- Prescription history
- Lab orders and results
- Vital signs

**Can Perform:**
- Schedule appointments
- Write prescriptions
- Order lab tests
- Document diagnoses
- Review patient vitals

---

## 3. Nurse Workflow
```
Login (optional 2FA) → Dashboard (assigned patients, pending tasks) →
  View Assigned Patients →
    Record Vitals (BP, HR, temp, O2, weight) →
      Administer Medications (log dose, time, patient) →
        Create Tasks (assign to selves or other nurses) →
          Shift Handover (document notes, alerts)
```

**Can Access:**
- Assigned patients only
- Vital signs history
- Medication schedules
- Task assignments
- Previous handover notes

**Can Perform:**
- Record vital signs
- Track medication administration
- Create and update tasks
- Document shift handovers

---

## 4. Lab Technician Workflow
```
Login → Dashboard (pending tests, completed tests, stats) →
  View Test Orders (by status: pending, collected, processing) →
    Mark as Collected →
      Upload Results (lab values, images, PDFs) →
        Update Test Status (completed/failed)
```

**Can Access:**
- Test orders assigned to lab
- Patient demographics (for identification)
- Doctor notes on requested test
- Test results (own and others' for verification)

**Can Perform:**
- Update test status
- Upload test results
- View test history

---

## 5. Admin Workflow
```
Login (2FA required) → Dashboard (system metrics, pending approvals) →
  Appointments (approve/reject requests) →
    User Management (view, create, disable users) →
      Audit Logs (search, filter, export) →
        System Health (backup status, database size)
```

**Can Access:**
- All appointments
- All users
- Complete audit trail
- System metrics
- Backup logs

**Can Perform:**
- Approve/reject appointments
- Manage user accounts
- Reset passwords
- View audit logs
- Trigger backups

---

# Getting Started

## Prerequisites
- Node.js 18+ (for frontend)
- Java 21 (for backend)
- PostgreSQL 14+ (or use Docker Compose)
- Docker & Docker Compose (recommended)
- Maven 3.8+

## Option 1: Run with Docker Compose (Recommended)

```bash
# Clone the project
git clone https://github.com/ManvithaDungi/PatientManagementSystem.git
cd PatientManagementSystem

# Create .env file (copy from .env.example)
cp .env.example .env

# Start all services
docker-compose up -d

# Wait for services to start (~30 seconds)
# Frontend: http://localhost:3000
# Backend API: http://localhost:8081
# Database: localhost:5432
# Adminer (DB GUI): http://localhost:8082
```

## Option 2: Run Locally

### Backend Setup
```bash
cd backend/Backend

# Build
mvn clean package

# Run (requires PostgreSQL running on localhost:5432)
java -jar target/backend-0.0.1-SNAPSHOT.jar

# Or using Maven
mvn spring-boot:run
```

### Frontend Setup
```bash
cd frontend/app

# Install dependencies
npm install

# Start development server
npm start

# Runs on http://localhost:3000
```

## Sample Credentials

After seeding, use these to test:

| Role | Email | Password |
|------|-------|----------|
| Patient | patient1@hospital.com | TempPass123! |
| Doctor | doctor1@hospital.com | DoctorPass123! |
| Nurse | nurse1@hospital.com | NursePass123! |
| Lab Tech | lab1@hospital.com | LabPass123! |
| Admin | admin@hospital.com | AdminPass123! |

---

# Project Structure

```
PatientManagementSystem/
│
├── backend/                          # Spring Boot API
│   └── Backend/
│       ├── src/main/java/com/securehealth/backend/
│       │   ├── config/
│       │   │   └── SecurityConfig.java         # Spring Security setup
│       │   ├── controller/              # REST endpoints (15 controllers)
│       │   │   ├── AuthController.java
│       │   │   ├── AppointmentController.java
│       │   │   ├── PatientController.java
│       │   │   ├── DoctorController.java
│       │   │   ├── NurseController.java
│       │   │   ├── LabTechnicianController.java
│       │   │   ├── AdminController.java
│       │   │   ├── PrescriptionController.java
│       │   │   ├── VitalSignController.java
│       │   │   └── ...
│       │   ├── dto/                     # Data Transfer Objects (20+ DTOs)
│       │   ├── exception/               # Custom exceptions
│       │   ├── model/                   # JPA entities (17 entities)
│       │   │   ├── Login.java
│       │   │   ├── PatientProfile.java
│       │   │   ├── DoctorProfile.java
│       │   │   ├── Appointment.java
│       │   │   ├── Prescription.java
│       │   │   ├── VitalSign.java
│       │   │   ├── LabTest.java
│       │   │   ├── Consent.java
│       │   │   └── ...
│       │   ├── repository/              # Spring Data JPA repositories
│       │   ├── security/                # JWT, filters, validators
│       │   │   ├── JwtAuthenticationFilter.java
│       │   │   ├── PatientAccessValidator.java
│       │   │   └── CustomUserDetailsService.java
│       │   ├── service/                 # Business logic (20+ services)
│       │   │   ├── AuthService.java
│       │   │   ├── AppointmentService.java
│       │   │   ├── PatientService.java
│       │   │   ├── DoctorService.java
│       │   │   ├── NurseService.java
│       │   │   ├── LabTechnicianService.java
│       │   │   ├── EmailService.java
│       │   │   ├── BackupService.java
│       │   │   ├── ArchivalService.java
│       │   │   └── ...
│       │   └── util/
│       │       └── JwtUtil.java
│       ├── src/test/java/              # Tests (unit + integration)
│       ├── src/main/resources/
│       │   └── application.properties  # Configuration
│       ├── pom.xml                     # Maven dependencies
│       └── Dockerfile
│
├── frontend/                         # React application
│   └── app/
│       ├── src/
│       │   ├── components/             # Reusable UI components
│       │   │   ├── admin/              # Admin-specific components
│       │   │   ├── appointments/       # Appointment workflows
│       │   │   ├── doctor/             # Doctor dashboard components
│       │   │   ├── nurse/              # Nurse dashboard components
│       │   │   ├── lab/                # Lab technician components
│       │   │   ├── auth/               # Auth UI components
│       │   │   ├── common/             # Shared components
│       │   │   │   ├── VitalsChart.jsx
│       │   │   │   ├── SchedulerView.jsx
│       │   │   │   ├── AppointmentCalendar.jsx
│       │   │   │   └── MiniCalendar.jsx
│       │   │   └── layout/
│       │   ├── contexts/               # State management
│       │   │   ├── AuthContext.jsx
│       │   │   └── ThemeContext.jsx
│       │   ├── layouts/                # Page layouts
│       │   ├── pages/                  # Route pages
│       │   │   ├── login.jsx
│       │   │   ├── createAccount.jsx
│       │   │   ├── TwoFactorAuth.jsx
│       │   │   ├── ForgotPassword.jsx
│       │   │   ├── ResetPassword.jsx
│       │   │   ├── doctor/
│       │   │   ├── patient/
│       │   │   ├── nurse/
│       │   │   ├── lab/
│       │   │   └── admin/
│       │   ├── services/               # API & auth services
│       │   │   ├── api.js              # REST API service
│       │   │   └── supabaseAuth.js     # Auth service
│       │   ├── App.jsx                 # Main app with routing
│       │   ├── App.css
│       │   └── index.js
│       ├── public/
│       ├── tailwind.config.js          # Tailwind theme
│       ├── postcss.config.js
│       ├── package.json
│       └── Dockerfile
│
├── DB/                               # Database
│   ├── schema.sql                       # Complete schema definition
│   ├── seed_users.sql                   # Sample users
│   └── DB_README.md
│
├── docker-compose.yml                  # Multi-service orchestration
├── .github/
│   └── workflows/
│       ├── ci.yml                       # GitHub Actions CI/CD
│       └── deployment.yml
├── application.properties               # Root config
├── .env.example                         # Environment variables template
├── README.md                            # This file
└── TECHNICAL_AUDIT_REPORT.md           # Deep technical audit
```

---

# Security Architecture

## Authentication Flow

```
┌─ User Submits Credentials ─────────────────────────────────┐
│                                                              │
│  POST /api/auth/login                                       │
│  { email: "doctor@hospital.com", password: "..." }         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼
        ┌──── Backend Validates ────┐
        │                            │
        │ 1. Find user by email      │
        │ 2. Compare password hash   │
        │    (Argon2)                │
        │ 3. Check account lockout   │
        │                            │
        └──── If Doctor/Admin ───────┘
                    │
                    ├─── Generate OTP
                    ├─── Send Email
                    └─── Return: { status: '2FA_REQUIRED' }
                                      │
                                      ▼
                    ┌──── User Receives Email ────┐
                    │ "Your OTP: 123456"          │
                    │ Valid for: 10 minutes       │
                    └─────────────────────────────┘
                                      │
                                      ▼
                    ┌──── User Submits OTP ───────┐
                    │ POST /api/auth/verify-otp   │
                    │ { email, otp }              │
                    └─────────────────────────────┘
                                      │
                                      ▼
        ┌──── Backend Verifies OTP ──┐
        │                             │
        │ 1. Check OTP matches        │
        │ 2. Check not expired        │
        │ 3. Mark OTP as used         │
        │                             │
        └── If Valid: Generate JWT ──┘
                    │
                    ▼
        ┌──── Generate Tokens ───────────────────────┐
        │                                             │
        │ ACCESS TOKEN (15 min):                      │
        │   { sub: userId, email, role, exp: +15m }  │
        │   Signed with JWT secret                    │
        │                                             │
        │ REFRESH TOKEN (7 days):                     │
        │   { sub: userId, exp: +7d }                 │
        │   Hashed in database                        │
        │                                             │
        │ SESSION RECORD:                             │
        │   userId, refreshTokenHash, expiresAt       │
        │   ipAddress, userAgent                      │
        │                                             │
        └─────────────────────────────────────────────┘
                    │
                    ▼
        ┌──── Return Response ───────┐
        │ {                          │
        │   status: 'SUCCESS',       │
        │   accessToken: 'jwt...',   │
        │   refreshToken: 'jwt...',  │
        │   user: { id, email, role} │
        │ }                          │
        └────────────────────────────┘
                    │
                    ▼
        ┌──── Frontend Stores ───────┐
        │ localStorage.setItem(      │
        │   'secure_health_user',    │
        │   { accessToken, user }    │
        │ )                          │
        └────────────────────────────┘
                    │
                    ▼
        ┌──── Subsequent Requests ───────────────────┐
        │ All API calls include:                      │
        │ Authorization: Bearer {accessToken}        │
        │                                             │
        │ Backend JWT Filter:                         │
        │   1. Extract token from header              │
        │   2. Validate signature                     │
        │   3. Check expiration                       │
        │   4. Check token blacklist                  │
        │   5. Populate SecurityContext               │
        │                                             │
        └─────────────────────────────────────────────┘
```

## Role-Based Access Control (RBAC)

Three-layer protection:

### Layer 1: Frontend Route Guards
```jsx
<Route path="/dashboard/doctor" element={
  <ProtectedRoute allowedRoles={['DOCTOR']}>
    <DoctorDashboard />
  </ProtectedRoute>
} />
// Non-doctors redirected to unauthorized page
```

### Layer 2: Backend Controller Annotations
```java
@PreAuthorize("hasAuthority('DOCTOR')")
@PostMapping("/prescriptions")
public ResponseEntity<?> createPrescription(...) { }

@PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
@GetMapping("/patients")
public ResponseEntity<?> getAllPatients() { }
```

### Layer 3: Service-Level Validation
```java
public PatientDTO getPatientById(Long id, String requesterEmail, String requesterRole) {
  PatientProfile profile = patientProfileRepository.findById(id)
    .orElseThrow();
    
  // Check 1: Admins/Doctors have general access
  if (requesterRole.equals("ADMIN") || requesterRole.equals("DOCTOR")) {
    return mapToDTO(profile);
  }
  
  // Check 2: Patients can only see their own profile
  if (profile.getUser().getEmail().equals(requesterEmail)) {
    return mapToDTO(profile);
  }
  
  // Deny access
  throw new RuntimeException("403 Forbidden: Unauthorized access");
}
```

## IDOR Protection

Every patient data access validates ownership:

```sql
-- Before: VULNERABLE - returns any patient
SELECT * FROM patient_profiles WHERE id = 5;

-- After: PROTECTED - only returns if user authorized
SELECT p.* FROM patient_profiles p
WHERE p.id = 5
AND (
  -- Patient owns it
  p.user_id = (SELECT user_id FROM login WHERE email = ?)
  -- OR doctor is assigned to it
  OR p.assigned_doctor_id = (SELECT user_id FROM login WHERE email = ?)
)
```

---

# API Documentation

## Authentication Endpoints

### Register User
```
POST /api/auth/register
Content-Type: application/json

Request:
{
  "email": "patient@hospital.com",
  "password": "SecurePass123",
  "role": "PATIENT",
  "fullName": "John Doe",
  "dateOfBirth": "1990-01-01",
  "address": "123 Main St"
}

Response (201 Created):
{
  "message": "User registered successfully"
}
```

### Login
```
POST /api/auth/login
Content-Type: application/json

Request:
{
  "email": "doctor@hospital.com",
  "password": "DoctorPass123"
}

Response (200 OK - Doctor/Admin):
{
  "status": "2FA_REQUIRED",
  "message": "OTP sent to email"
}

Response (200 OK - Patient/Nurse/Lab):
{
  "status": "LOGIN_SUCCESS",
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "user": {
    "id": 123,
    "email": "patient@hospital.com",
    "role": "PATIENT"
  }
}
```

### Verify 2FA OTP
```
POST /api/auth/verify-otp
Content-Type: application/json

Request:
{
  "email": "doctor@hospital.com",
  "otp": "123456"
}

Response (200 OK):
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "user": { ... }
}

Response (401 Unauthorized):
{
  "message": "Invalid or expired OTP"
}
```

### Logout
```
POST /api/auth/logout
Authorization: Bearer {accessToken}

Response (200 OK):
{
  "message": "Logged out successfully"
}
```

## Appointment Endpoints

### Create Appointment (Patient)
```
POST /api/appointments
Authorization: Bearer {accessToken}
Content-Type: application/json

Request:
{
  "doctorId": 5,
  "appointmentDate": "2025-05-01T14:00:00",
  "reasonForVisit": "Regular checkup"
}

Response (200 OK):
{
  "appointmentId": 42,
  "patientId": 3,
  "doctorId": 5,
  "appointmentDate": "2025-05-01T14:00:00",
  "status": "PENDING_APPROVAL",
  "createdAt": "2025-04-20T10:30:00"
}
```

### Get Available Slots (Doctor)
```
GET /api/appointments/doctor/{doctorId}/available-slots?date=2025-05-01
Authorization: Bearer {accessToken}

Response (200 OK):
[
  "09:00",
  "09:30",
  "10:00",
  "10:30",
  ...
]
```

### Approve Appointment (Admin)
```
PUT /api/appointments/{appointmentId}/approve
Authorization: Bearer {accessToken}

Response (200 OK):
{
  "appointmentId": 42,
  "status": "SCHEDULED",
  "updatedAt": "2025-04-20T11:00:00"
}
```

## Complete API Reference

[See Backend README for complete endpoint documentation]

---

# Database Schema

### Core Tables

**login**
- Stores user credentials and security state
- Columns: userId, email, passwordHash, role, twoFactorEnabled, failedAttempts, isLocked, etc.

**patient_profiles**
- Patient demographic and assignment data
- Columns: profileId, userId, assignedDoctorId, assignedNurseId, firstName, lastName, dateOfBirth, medicalHistory, etc.

**doctor_profiles**
- Doctor specialization and scheduling
- Columns: profileId, userId, specialty, department, shiftStartTime, shiftEndTime, slotDurationMinutes

**appointments**
- Appointment requests and scheduling
- Columns: appointmentId, patientProfileId, doctorId, appointmentDate, status, reasonForVisit, doctorNotes, createdAt

**prescriptions**
- Medication prescriptions
- Columns: prescriptionId, patientProfileId, doctorId, medicationName, dosage, frequency, duration, specialInstructions, issuedAt, startDate, endDate, status

**vital_signs**
- Recorded patient vitals
- Columns: vitalId, patientProfileId, nurseId, bloodPressure, heartRate, temperature, respiratoryRate, oxygenSaturation, weight, height, recordedAt

**lab_tests**
- Lab test orders and results
- Columns: testId, patientProfileId, orderedById, testName, testCategory, resultValue, unit, referenceRange, status, fileUrl, createdAt

**medical_records**
- Doctor-documented clinical notes
- Columns: recordId, patientProfileId, doctorId, diagnosis, symptoms, treatmentProvided, attachmentUrl, createdAt

### Security & Audit Tables

**sessions**
- Active JWT sessions
- Columns: id, userId, refreshTokenHash, ipAddress, userAgent, expiresAt, revoked, createdAt

**password_history**
- Password reuse prevention
- Columns: id, userId, passwordHash, createdAt

**password_reset_tokens**
- Time-limited password reset links
- Columns: id, userId, tokenHash, expiresAt, used, createdAt

**audit_logs**
- Complete access trail
- Columns: id, email, action, ipAddress, userAgent, details, timestamp

**consent_log**
- Patient consent tracking
- Columns: id, patientId, consentType, sharedWith, granted, grantedAt, revokedAt

---

# Deployment

## Docker Compose (Development)
```bash
docker-compose up -d
# Starts: PostgreSQL, Redis, Backend API, Frontend, Adminer
```

## Docker Build (Production)
```bash
# Backend
docker build -t secure-health-api:1.0 ./backend

# Frontend
docker build -t secure-health-app:1.0 ./frontend

# Push to registry
docker push your-registry/secure-health-api:1.0
docker push your-registry/secure-health-app:1.0
```

## Environment Variables
```env
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/healthcare_db
SPRING_DATASOURCE_USERNAME=healthcare_user
SPRING_DATASOURCE_PASSWORD=secure_password_here

# JWT
JWT_SECRET=your_secret_key_here
JWT_EXPIRATION=900000

# Frontend
REACT_APP_API_URL=http://localhost:8081

# Email (OTP delivery)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=app_specific_password

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# Backups
BACKUP_ENABLED=true
BACKUP_DIR=./backups
BACKUP_RETENTION=7
```

## CI/CD Pipeline
GitHub Actions automatically:
1. Builds backend (Maven)
2. Runs backend tests
3. Builds frontend (npm)
4. Runs frontend tests
5. (Optional) Deploys to staging

---

# Testing

## Backend Tests
```bash
cd backend/Backend

# Run all tests
mvn test

# Run specific test
mvn test -Dtest=AuthIntegrationTest

# Run with coverage
mvn test jacoco:report
```

**Test Types:**
- Unit tests: Services in isolation
- Controller tests: HTTP layer with MockMvc
- Integration tests: Complete flows with H2 database

## Frontend Tests
```bash
cd frontend/app

# Run all tests
npm test

# Run with coverage
npm test -- --coverage

# Run integration tests only
npm run test:integration

# Run unit tests only
npm run test:unit
```

---

# Known Issues & Improvements

## Known Issues

### Issue 1: Admin Workflows Not Fully Tested
**Status:** Low Priority  
**Impact:** Admin features might have bugs  
**Fix:** Complete integration testing of admin endpoints
**Timeline:** v1.1

### Issue 2: Lab File Upload Format Mismatch
**Status:** Medium Priority  
**Impact:** Lab technicians can't upload results  
**Details:** Backend expects JSON; frontend sends FormData  
**Fix:** Update backend to accept multipart/form-data  
**Timeline:** v1.0.1 (patch)

### Issue 3: No Refresh Token Rotation
**Status:** Low Priority  
**Impact:** Refresh tokens never invalidated  
**Fix:** Implement refresh token rotation on every use  
**Timeline:** v1.1

## Planned Improvements

### v1.0.1 (Hotfixes)
- [ ] Fix lab file upload FormData handling
- [ ] Improve error messages on 401 responses
- [ ] Add retry logic to API calls

### v1.1 (Security & UX)
- [ ] Implement refresh token rotation
- [ ] Move tokens to HttpOnly cookies
- [ ] Add client-side token expiry checking
- [ ] Complete admin workflow testing
- [ ] Add rate limiting per user

### v1.2 (Performance)
- [ ] Implement React Query for caching
- [ ] Add pagination to patient lists
- [ ] Lazy load components
- [ ] Implement virtual scrolling for large lists

### v2.0 (Enterprise Features)
- [ ] OAuth2 integration (Google, Microsoft)
- [ ] SAML for single sign-on
- [ ] Multi-site hospital support
- [ ] Data export/FHIR compatibility
- [ ] Analytics dashboard
- [ ] Mobile app (React Native)

---

# Developer Notes

## Architecture Decisions

### Why React Context over Redux?
- Project has only 2-3 global states (auth, theme)
- Redux adds complexity without clear benefit
- Context API is sufficient and built-in

### Why Argon2 over Bcrypt?
- NIST recommends Argon2 for new systems (as of 2024)
- GPU-resistant (memory-hard)
- Better for sensitive healthcare data

### Why JWT over Sessions?
- Stateless (scales horizontally)
- No server-side session storage needed
- Works with distributed systems/microservices

### Why PostgreSQL over NoSQL?
- ACID transactions essential for medical data
- Healthcare data is relational (patients ↔ doctors, prescriptions, etc.)
- Audit trail requires strong consistency

## Common Development Tasks

### Add New Role
1. Add to `Role.java` enum
2. Add role-specific controller (e.g., `NurseController`)
3. Add role-specific service (e.g., `NurseService`)
4. Create DTOs for role-specific responses
5. Add role-specific frontend pages
6. Add `@PreAuthorize` on endpoints
7. Update routing guards
8. Add tests

### Add New Endpoint
1. Create DTO for request/response
2. Add method to Repository
3. Add logic to Service
4. Add `@PostMapping`/`@GetMapping` to Controller
5. Add `@PreAuthorize` for authorization
6. Add tests (unit + integration)
7. Document in README

### Add New Database Table
1. Create JPA entity in `model/`
2. Create repository extending `JpaRepository`
3. Create service for business logic
4. Add to `schema.sql` for reference
5. Test with integration tests
6. Update related services

## Debugging

### Common Issues

**401 Unauthorized on all requests**
- Check JWT secret in `application.properties`
- Verify token format: `Authorization: Bearer {token}`
- Check token expiration: `jwtUtil.isTokenExpired(token)`

**CORS errors on frontend**
- Check allowed origins in `SecurityConfig`
- Verify frontend URL matches CORS config
- Add `credentials: 'include'` to fetch calls

**Patient can see other patients' data**
- IDOR vulnerability
- Check `PatientAccessValidator` is being called
- Verify service layer access checks
- Review database queries for pre-filtering

**Appointment double-booking occurs**
- Race condition in appointment creation
- Verify unique constraint on database:
  ```sql
  SELECT * FROM information_schema.constraints 
  WHERE table_name='appointments' AND constraint_type='UNIQUE';
  ```

---

# Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [React Documentation](https://react.dev)
- [HIPAA Compliance Guide](https://www.hhs.gov/hipaa)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8949)

---

# License

This project is for educational purposes and interview demonstration.

---

# Contact & Support

For questions or issues:
1. Check the [TECHNICAL_AUDIT_REPORT.md](TECHNICAL_AUDIT_REPORT.md) for detailed architectural documentation
2. Review the integration testing guide in `backend/Backend/INTEGRATION_TESTING_GUIDE.md`
3. Check recent PRs for current issues and solutions

---

**Last Updated:** April 20, 2026  
**Status:** Production-Ready for Education/Interview Use  
**Maintained By:** Manvitha Dungi

