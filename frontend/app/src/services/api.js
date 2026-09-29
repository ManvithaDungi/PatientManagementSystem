// API Service - Centralized API calls for the Patient Management System
const API_BASE_URL = process.env.REACT_APP_API_URL || '';

const getAccessToken = () => {
   const userDataStr = localStorage.getItem('secure_health_user');
   if (!userDataStr) return null;
   try {
      return JSON.parse(userDataStr).accessToken || null;
   } catch (e) {
      console.error('Failed to parse secure_health_user from localStorage', e);
      return null;
   }
};

const setAccessToken = (accessToken) => {
   const userDataStr = localStorage.getItem('secure_health_user');
   if (!userDataStr) return;
   try {
      const userSession = JSON.parse(userDataStr);
      userSession.accessToken = accessToken;
      localStorage.setItem('secure_health_user', JSON.stringify(userSession));
   } catch (e) {
      console.error('Failed to update stored access token', e);
   }
};

const forceLogout = () => {
   localStorage.removeItem('secure_health_user');
   window.location.href = '/login';
};

// The 15-minute access token is backed by a 7-day HttpOnly refresh cookie
// (set by /api/auth/login). Rather than force a re-login on every 401, try
// rotating the access token once via /api/auth/refresh-token first.
// Concurrent 401s share a single in-flight refresh instead of each firing one.
let refreshInFlight = null;
const refreshAccessToken = () => {
   if (!refreshInFlight) {
      refreshInFlight = fetch(`${API_BASE_URL}/auth/refresh-token`, {
         method: 'POST',
         credentials: 'include',
      })
         .then(async (response) => {
            if (!response.ok) return null;
            const data = await response.json().catch(() => null);
            if (data?.accessToken) {
               setAccessToken(data.accessToken);
               return data.accessToken;
            }
            return null;
         })
         .catch(() => null)
         .finally(() => {
            refreshInFlight = null;
         });
   }
   return refreshInFlight;
};

// Helper function for JSON API calls
const apiCall = async (endpoint, options = {}, isRetry = false) => {
   const accessToken = getAccessToken();

   // A FormData body (e.g. file upload) must NOT get a manual Content-Type —
   // the browser needs to set its own multipart boundary.
   const isFormData = options.body instanceof FormData;

   const headers = {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers,
   };

   // Inject Authorization header if we have an accessToken
   if (accessToken) {
      headers['Authorization'] = `Bearer ${accessToken}`;
   }

   const config = {
      headers,
      credentials: 'include', // Include cookies for authentication
      ...options,
   };

   try {
      const response = await fetch(`${API_BASE_URL}${endpoint}`, config);

      if (!response.ok) {
         // A 401 on the refresh endpoint itself, or a retry that still 401s,
         // means the session truly can't be recovered — log out.
         const isAuthEndpoint = endpoint.startsWith('/auth/');
         if (response.status === 401 && !isRetry && !isAuthEndpoint) {
            const newAccessToken = await refreshAccessToken();
            if (newAccessToken) {
               return apiCall(endpoint, options, true);
            }
         }

         if (response.status === 401) {
            console.warn('Session expired. Redirecting to login...');
            forceLogout();
         }

         const error = await response.json().catch(() => ({ message: 'Request failed' }));
         throw new Error(error.message || `HTTP error! status: ${response.status}`);
      }

      return await response.json();
   } catch (error) {
      console.error(`API Error [${endpoint}]:`, error);
      throw error;
   }
};

// Helper for endpoints that return a raw file body (not JSON), e.g. GET /files/{filename}.
// Returns a browser-local object URL the caller is responsible for revoking when done.
const apiCallBlob = async (endpoint, options = {}) => {
   const accessToken = getAccessToken();
   const headers = { ...options.headers };
   if (accessToken) {
      headers['Authorization'] = `Bearer ${accessToken}`;
   }

   const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      headers,
      credentials: 'include',
      ...options,
   });

   if (!response.ok) {
      if (response.status === 401) {
         localStorage.removeItem('secure_health_user');
         window.location.href = '/login';
      }
      const error = await response.json().catch(() => ({ message: 'Request failed' }));
      throw new Error(error.message || `HTTP error! status: ${response.status}`);
   }

   const blob = await response.blob();
   return URL.createObjectURL(blob);
};

// ============================================
// AUTHENTICATION APIs
// ============================================

export const authAPI = {
   // Register a new user
   register: async (email, password, role = 'PATIENT') => {
      return apiCall('/auth/register', {
         method: 'POST',
         body: JSON.stringify({ email, password, role }),
      });
   },

   // Login user
   login: async (email, password) => {
      return apiCall('/auth/login', {
         method: 'POST',
         body: JSON.stringify({ email, password }),
      });
   },

   // Logout user
   logout: async () => {
      return apiCall('/auth/logout', {
         method: 'POST',
      });
   },

   // Get current user
   getCurrentUser: async () => {
      return apiCall('/auth/me', {
         method: 'GET',
      });
   },
};

// ============================================
// PATIENT APIs
// ============================================

export const patientAPI = {
   // Get current patient profile
   getMe: async () => {
      return apiCall('/patients/me', {
         method: 'GET',
      });
   },

   // Get all patients
   getAll: async () => {
      return apiCall('/patients', {
         method: 'GET',
      });
   },

   // Get patient by ID
   getById: async (id) => {
      return apiCall(`/patients/${id}`, {
         method: 'GET',
      });
   },

   // Create new patient
   create: async (patientData) => {
      return apiCall('/patients', {
         method: 'POST',
         body: JSON.stringify(patientData),
      });
   },

   // Update patient
   update: async (id, patientData) => {
      return apiCall(`/patients/${id}`, {
         method: 'PUT',
         body: JSON.stringify(patientData),
      });
   },

   // Note: no DELETE /patients/{id} endpoint exists on the backend by design —
   // patient records are deactivated per hospital policy, not deleted. If a
   // deactivation workflow is needed, add a backend endpoint for it first.
};

// ============================================
// APPOINTMENT APIs
// ============================================

export const appointmentAPI = {
   // Get all appointments
   getAll: async () => {
      return apiCall('/appointments', {
         method: 'GET',
      });
   },

   // Get appointment by ID
   getById: async (id) => {
      return apiCall(`/appointments/${id}`, {
         method: 'GET',
      });
   },

   // Get appointments by patient ID
   getByPatient: async (patientId) => {
      return apiCall(`/appointments/patient/${patientId}`, {
         method: 'GET',
      });
   },

   // Get appointments by doctor ID
   getByDoctor: async (doctorId) => {
      return apiCall(`/appointments/doctor/${doctorId}`, {
         method: 'GET',
      });
   },

   // Get a doctor's open time slots for a given date (YYYY-MM-DD)
   getAvailableSlots: async (doctorId, date) => {
      return apiCall(`/appointments/doctor/${doctorId}/available-slots?date=${date}`, {
         method: 'GET',
      });
   },

   // Create new appointment
   create: async (appointmentData) => {
      return apiCall('/appointments', {
         method: 'POST',
         body: JSON.stringify(appointmentData),
      });
   },

   // Update appointment (DOCTOR only — date, doctor's notes)
   update: async (id, appointmentData) => {
      return apiCall(`/appointments/${id}`, {
         method: 'PUT',
         body: JSON.stringify(appointmentData),
      });
   },

   // Reschedule an appointment (PATIENT only — date/time only, resets to pending approval)
   reschedule: async (id, appointmentDate) => {
      return apiCall(`/appointments/${id}/reschedule`, {
         method: 'PUT',
         body: JSON.stringify({ appointmentDate }),
      });
   },

   // Mark an appointment as completed (DOCTOR only)
   complete: async (id) => {
      return apiCall(`/appointments/${id}/complete`, {
         method: 'PUT',
      });
   },

   // Cancel appointment
   cancel: async (id, data = {}) => {
      return apiCall(`/appointments/${id}/cancel`, {
         method: 'PUT',
         body: JSON.stringify({ status: 'CANCELLED', ...data }),
      });
   },

   // Get all pending-approval appointments (ADMIN only)
   getPending: async () => {
      return apiCall('/appointments/pending', {
         method: 'GET',
      });
   },

   // Approve a pending appointment (ADMIN only)
   approve: async (id) => {
      return apiCall(`/appointments/${id}/approve`, {
         method: 'PUT',
      });
   },

   // Reject a pending appointment with optional reason (ADMIN only)
   reject: async (id, reason = '') => {
      return apiCall(`/appointments/${id}/reject`, {
         method: 'PUT',
         body: JSON.stringify(reason),
      });
   },

   // Delete appointment
   delete: async (id) => {
      return apiCall(`/appointments/${id}`, {
         method: 'DELETE',
      });
   },
};

// ============================================
// MEDICAL RECORD APIs
// ============================================

export const medicalRecordAPI = {
   // Get all medical records for a patient
   getByPatient: async (patientId) => {
      return apiCall(`/medical-records/patient/${patientId}`, {
         method: 'GET',
      });
   },

   // Get medical record by ID
   getById: async (id) => {
      return apiCall(`/medical-records/${id}`, {
         method: 'GET',
      });
   },

   // Create new medical record
   create: async (recordData) => {
      return apiCall('/medical-records', {
         method: 'POST',
         body: JSON.stringify(recordData),
      });
   },

   // Update medical record
   update: async (id, recordData) => {
      return apiCall(`/medical-records/${id}`, {
         method: 'PUT',
         body: JSON.stringify(recordData),
      });
   },

   // Delete medical record
   delete: async (id) => {
      return apiCall(`/medical-records/${id}`, {
         method: 'DELETE',
      });
   },
};

// ============================================
// PRESCRIPTION APIs
// ============================================

export const prescriptionAPI = {
   // Get all prescriptions for a patient
   getByPatient: async (patientId) => {
      return apiCall(`/prescriptions/patient/${patientId}`, {
         method: 'GET',
      });
   },

   // Get prescription by ID
   getById: async (id) => {
      return apiCall(`/prescriptions/${id}`, {
         method: 'GET',
      });
   },

   // Create new prescription
   create: async (prescriptionData) => {
      return apiCall('/prescriptions', {
         method: 'POST',
         body: JSON.stringify(prescriptionData),
      });
   },

   // Update prescription
   update: async (id, prescriptionData) => {
      return apiCall(`/prescriptions/${id}`, {
         method: 'PUT',
         body: JSON.stringify(prescriptionData),
      });
   },

   // Delete prescription
   delete: async (id) => {
      return apiCall(`/prescriptions/${id}`, {
         method: 'DELETE',
      });
   },
};

// ============================================
// LAB RESULT APIs
// ============================================

export const labResultAPI = {
   // Get all lab results (doctor/admin)
   getAll: async () => {
      return apiCall('/lab-results', {
         method: 'GET',
      });
   },

   // Get all lab results for a patient
   getByPatient: async (patientId) => {
      return apiCall(`/lab-results/patient/${patientId}`, {
         method: 'GET',
      });
   },

   // Get lab result by ID
   getById: async (id) => {
      return apiCall(`/lab-results/${id}`, {
         method: 'GET',
      });
   },

   // Create new lab result
   create: async (labResultData) => {
      return apiCall('/lab-results', {
         method: 'POST',
         body: JSON.stringify(labResultData),
      });
   },

   // Note: no PUT /lab-results/{id} endpoint exists on the backend and no page
   // in this app edits a submitted lab result in place — a lab result is either
   // pending (labTechnicianAPI.uploadResults) or final. If in-place editing of a
   // finalized result becomes a real requirement, add a backend endpoint first,
   // then a corresponding `update` wrapper here.

   // Delete lab result
   delete: async (id) => {
      return apiCall(`/lab-results/${id}`, {
         method: 'DELETE',
      });
   },
};

// ============================================
// DOCTOR APIs
// ============================================

export const doctorAPI = {
   // Get all doctors
   getAll: async () => {
      return apiCall('/doctors', {
         method: 'GET',
      });
   },

   // Get the currently authenticated doctor's own profile (resolved server-side via JWT identity)
   getMe: async () => {
      return apiCall('/doctors/me', {
         method: 'GET',
      });
   },

   // Update the currently authenticated doctor's own profile
   updateMe: async (doctorData) => {
      return apiCall('/doctors/me', {
         method: 'PUT',
         body: JSON.stringify(doctorData),
      });
   },

   // Get doctor by ID
   getById: async (id) => {
      return apiCall(`/doctors/${id}`, {
         method: 'GET',
      });
   },

   // Get doctor by specialty
   getBySpecialty: async (specialty) => {
      return apiCall(`/doctors/specialty/${specialty}`, {
         method: 'GET',
      });
   },

   // Get patients for a doctor
   getPatients: async (doctorId) => {
      return apiCall(`/doctors/${doctorId}/patients`, {
         method: 'GET',
      });
   },

   // Update doctor profile
   update: async (id, doctorData) => {
      return apiCall(`/doctors/${id}`, {
         method: 'PUT',
         body: JSON.stringify(doctorData),
      });
   },
};

// ============================================
// VITAL SIGNS APIs
// ============================================

export const vitalSignsAPI = {
   // Get all vital signs for a patient
   getByPatient: async (patientId) => {
      return apiCall(`/vital-signs/patient/${patientId}`, {
         method: 'GET',
      });
   },

   // Get latest vital signs for a patient
   getLatest: async (patientId) => {
      return apiCall(`/vital-signs/patient/${patientId}/latest`, {
         method: 'GET',
      });
   },

   // Create new vital signs record
   create: async (vitalSignsData) => {
      return apiCall('/vital-signs', {
         method: 'POST',
         body: JSON.stringify(vitalSignsData),
      });
   },

   // Note: no PUT /vital-signs/{id} endpoint exists on the backend and no page
   // in this app edits a previously recorded vital-sign entry — vitals are
   // append-only observations. If correcting a past entry becomes a real
   // requirement, add a backend endpoint first, then an `update` wrapper here.
};

// ============================================
// NURSE APIs
// ============================================

export const nurseAPI = {
   getDashboardOverview: async () => {
      return apiCall('/nurse/dashboard', { method: 'GET' });
   },
   getAssignedPatients: async () => {
      return apiCall('/nurse/assigned-patients', { method: 'GET' });
   },
   getTasks: async () => {
      return apiCall('/nurse/tasks', { method: 'GET' });
   },
   toggleTaskStatus: async (taskId) => {
      return apiCall(`/nurse/tasks/${taskId}/toggle`, { method: 'PUT' });
   },
   createTask: async (taskData) => {
      return apiCall('/nurse/tasks', { method: 'POST', body: JSON.stringify(taskData) });
   },
   getHandoverNotes: async () => {
      return apiCall('/nurse/handover', { method: 'GET' });
   },
   saveHandoverNote: async (payload) => {
      return apiCall('/nurse/handover', { method: 'POST', body: JSON.stringify(payload) });
   },
   recordVitals: async (vitalSignsData) => {
      return apiCall('/vital-signs', {
         method: 'POST',
         body: JSON.stringify(vitalSignsData)
      });
   },
   // Records a single dose administration against a prescription
   recordMedicationAdministration: async (prescriptionId, notes) => {
      return apiCall(`/nurse/medications/${prescriptionId}/administer`, {
         method: 'POST',
         body: JSON.stringify({ notes })
      });
   },
   getMedicationAdministrationHistory: async (prescriptionId) => {
      return apiCall(`/nurse/medications/${prescriptionId}/history`, { method: 'GET' });
   }
};

// ============================================
// LAB TECHNICIAN APIs
// ============================================

export const labTechnicianAPI = {
   getDashboard: async () => {
      return apiCall('/lab-technician/dashboard', { method: 'GET' });
   },
   getOrders: async (status) => {
      const url = status ? `/lab-technician/orders?status=${status}` : '/lab-technician/orders';
      return apiCall(url, { method: 'GET' });
   },
   updateOrderStatus: async (testId, status) => {
      return apiCall(`/lab-technician/orders/${testId}/status`, {
         method: 'PUT',
         body: JSON.stringify({ status })
      });
   },
   uploadResults: async (testId, resultValue, remarks, fileUrl) => {
      return apiCall(`/lab-technician/orders/${testId}/upload`, {
         method: 'PUT',
         body: JSON.stringify({ resultValue, remarks, fileUrl })
      });
   }
};

// ============================================
// ADMIN APIs
// ============================================

export const adminAPI = {
   // Get dashboard metrics
   getMetrics: async () => {
      return apiCall('/admin/metrics', {
         method: 'GET',
      });
   },

   // Get all audit logs (Admin only)
   getAuditLogs: async () => {
      return apiCall('/admin/audit-logs', {
         method: 'GET',
      });
   },

   // Get audit logs for a specific user
   getAuditLogsByEmail: async (email) => {
      return apiCall(`/admin/audit-logs/${encodeURIComponent(email)}`, {
         method: 'GET',
      });
   },

   // Get all staff members (non-patient users)
   getAllStaff: async () => {
      return apiCall('/admin/staff', {
         method: 'GET',
      });
   },

   // Get all patients (patient directory)
   getAllUsers: async () => {
      return apiCall('/admin/patients', {
         method: 'GET',
      });
   },

   // Get all appointments (for admin dashboard)
   getAllAppointments: async () => {
      return apiCall('/appointments', {
         method: 'GET',
      });
   },

   // Delete a staff member
   deleteStaff: async (userId) => {
      return apiCall(`/admin/staff/${userId}`, {
         method: 'DELETE',
      });
   },

   // Update staff role
   updateStaffRole: async (userId, newRole) => {
      return apiCall(`/admin/staff/${userId}/role`, {
         method: 'PUT',
         body: JSON.stringify({ newRole }),
      });
   },
};

// ============================================
// CONSENT APIs
// ============================================

export const consentAPI = {
   // Get all consents for the logged-in patient
   getMyConsents: async () => {
      return apiCall('/consent', {
         method: 'GET',
      });
   },

   // Grant a new consent
   grantConsent: async (payload) => {
      return apiCall('/consent', {
         method: 'POST',
         body: JSON.stringify(payload),
      });
   },

   // Revoke an existing consent by ID
   revokeConsent: async (id) => {
      return apiCall(`/consent/${id}/revoke`, {
         method: 'PUT',
      });
   },
};

// ============================================
// FILE APIs
// ============================================

export const filesAPI = {
   // Uploads a file (encrypted at rest server-side); resolves to the stored filename
   // to be passed as e.g. a lab result's fileUrl.
   upload: async (file) => {
      const formData = new FormData();
      formData.append('file', file);
      const result = await apiCall('/files/upload', {
         method: 'POST',
         body: formData,
      });
      return result.filename;
   },

   // Downloads and decrypts a stored file, returning a browser object URL.
   // Caller should call URL.revokeObjectURL(url) when done with it (e.g. on unmount).
   getObjectUrl: async (filename) => {
      return apiCallBlob(`/files/${encodeURIComponent(filename)}`, { method: 'GET' });
   },
};

// ============================================
// CATALOG APIs (static reference data for dropdowns)
// ============================================

export const catalogAPI = {
   getHospitalDepartments: async () => {
      return apiCall('/hospital-departments', { method: 'GET' });
   },
   getMedications: async () => {
      return apiCall('/medications', { method: 'GET' });
   },
   getTestTypes: async () => {
      return apiCall('/test-types', { method: 'GET' });
   },
   getConditions: async () => {
      return apiCall('/conditions', { method: 'GET' });
   },
};

const api = {
   auth: authAPI,
   patients: patientAPI,
   appointments: appointmentAPI,
   medicalRecords: medicalRecordAPI,
   prescriptions: prescriptionAPI,
   labResults: labResultAPI,
   doctors: doctorAPI,
   vitalSigns: vitalSignsAPI,
   nurse: nurseAPI,
   labTechnician: labTechnicianAPI,
   admin: adminAPI,
   consent: consentAPI,
   catalog: catalogAPI,
   files: filesAPI,
};

export default api;
