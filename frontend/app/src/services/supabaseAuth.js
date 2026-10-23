// Authentication service for backend API
import { getAccessToken, setAccessToken, clearAccessToken } from './tokenStore';

const API_BASE_URL = process.env.REACT_APP_API_URL || '';
const AUTH_URL = API_BASE_URL ? `${API_BASE_URL}/auth` : '/auth';
const STORAGE_KEY = 'secure_health_user';
const PROFILE_STORAGE_KEY = 'secure_health_profiles';

const fetchWithTimeout = async (url, options = {}, timeoutMs = 12000) => {
   const controller = new AbortController();
   const id = setTimeout(() => controller.abort(), timeoutMs);
   try {
      const response = await fetch(url, { credentials: 'include', ...options, signal: controller.signal });
      clearTimeout(id);
      return response;
   } catch (err) {
      clearTimeout(id);
      if (err?.name === 'AbortError') {
         throw new Error('Request timed out');
      }
      if (typeof err?.message === 'string' && err.message.includes('Failed to fetch')) {
         throw new Error('Network error. Please check your connection.');
      }
      throw err;
   }
};

// Auth state change listeners
let authStateListeners = [];

// Only non-sensitive display data (email, role, name, id) is persisted. The
// access token lives in memory (tokenStore) and is never written to storage.
const saveSession = (user) => {
   const { accessToken, ...displayUser } = user;
   if (accessToken) {
      setAccessToken(accessToken);
   }
   localStorage.setItem(STORAGE_KEY, JSON.stringify(displayUser));
   notifyAuthStateChange({ user: { ...displayUser, accessToken: getAccessToken() } });
};

const clearSession = () => {
   clearAccessToken();
   localStorage.removeItem(STORAGE_KEY);
   notifyAuthStateChange(null);
};

const getSession = () => {
   try {
      const data = localStorage.getItem(STORAGE_KEY);
      return data ? JSON.parse(data) : null;
   } catch (e) {
      return null;
   }
};

// After a reload there is a stored profile but no in-memory token: re-issue it
// from the HttpOnly refresh cookie. If that fails the session is gone.
const restoreAccessToken = async () => {
   try {
      const response = await fetchWithTimeout(`${AUTH_URL}/refresh-token`, { method: 'POST' });
      if (!response.ok) return false;
      const data = await response.json().catch(() => null);
      if (!data?.accessToken) return false;
      setAccessToken(data.accessToken);
      return true;
   } catch (e) {
      return false;
   }
};

// Role and identity come from the server (GET /auth/me), never from what is stored in the browser.
const fetchIdentity = async () => {
   try {
      const token = getAccessToken();
      if (!token) return null;
      const response = await fetchWithTimeout(`${AUTH_URL}/me`, {
         method: 'GET',
         headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) return null;
      return await response.json();
   } catch (e) {
      return null;
   }
};

const getProfiles = () => {
   const data = localStorage.getItem(PROFILE_STORAGE_KEY);
   return data ? JSON.parse(data) : {};
};

const saveProfileName = (email, fullName) => {
   if (!email || !fullName) {
      return;
   }
   const profiles = getProfiles();
   profiles[email] = fullName;
   localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profiles));
};

const getProfileName = (email) => {
   if (!email) {
      return null;
   }
   const profiles = getProfiles();
   return profiles[email] || null;
};

export const signup = async (email, password, userData = {}) => {
   try {
      // Public registration is patient-only; the server assigns the role and
      // ignores any role sent here.
      const role = 'PATIENT';
      const { role: _ignoredRole, ...profileFields } = userData;
      const body = { email, password, ...profileFields };

      const response = await fetchWithTimeout(`${AUTH_URL}/register`, {
         method: 'POST',
         headers: { 'Content-Type': 'application/json' },
         body: JSON.stringify(body),
      });

      if (!response.ok) {
         const error = await response.json().catch(() => ({}));
         throw new Error(error.message || 'Registration failed');
      }

      // Backend returns success message but no user object for register
      // We auto-login or ask user to login. Here we'll simulate auto-login for UX
      const fullName = userData.full_name || userData.fullName;
      const user = { email, role, fullName };
      if (fullName) {
         saveProfileName(email, fullName);
      }
      // No session is created here: registration does not issue tokens, the user signs in next.

      return { message: 'User registered successfully', user };
   } catch (error) {
      throw error;
   }
};

export const signUp = async (email, password, userData) => {
   try {
      const result = await signup(email, password, userData);
      // Construct session object matching what AuthContext expects
      const session = { user: result.user };
      return { success: true, user: result.user, session };
   } catch (error) {
      return { success: false, error: error.message };
   }
};

export const login = async (email, password) => {
   try {
      const response = await fetchWithTimeout(`${AUTH_URL}/login`, {
         method: 'POST',
         headers: { 'Content-Type': 'application/json' },
         body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
         const error = await response.json().catch(() => ({}));
         throw new Error(error.message || 'Login failed');
      }

      let data = await response.json();
      const status = data.status || data.message || 'LOGIN_SUCCESS';
      const resolvedEmail = data.email || email;
      const storedName = getProfileName(resolvedEmail);
      const fullName = data.full_name || data.fullName || storedName;

      const accessToken = data.accessToken || null;
      // Use userId directly from the backend response
      const userId = data.userId || null;

      const user = { email: resolvedEmail, role: data.role || 'PATIENT', fullName, accessToken, userId };
      if (status === 'OTP_REQUIRED') {
         return { status, user, pendingToken: data.pendingToken || null };
      }
      if (fullName) {
         saveProfileName(resolvedEmail, fullName);
      }
      saveSession(user);
      return { status, user, ...data };
   } catch (error) {
      throw error;
   }
};

export const signIn = async (email, password) => {
   try {
      const result = await login(email, password);
      if (result.status === 'OTP_REQUIRED') {
         return { success: false, status: 'OTP_REQUIRED', user: result.user, pendingToken: result.pendingToken };
      }
      const session = { user: result.user };
      return { success: true, user: result.user, session, status: result.status };
   } catch (error) {
      return { success: false, error: error.message };
   }
};

export const logout = async () => {
   try {
      const headers = { 'Content-Type': 'application/json' };
      const token = getAccessToken();
      if (token) {
         headers['Authorization'] = `Bearer ${token}`;
      }
      await fetchWithTimeout(`${AUTH_URL}/logout`, { method: 'POST', headers });
   } catch (e) {
      // Ignore network errors; still clear local session
   } finally {
      clearSession();
      return { message: 'Logged out successfully' };
   }
};

export const signOut = async () => {
   try {
      await logout();
      return { success: true };
   } catch (error) {
      return { success: false, error: error.message };
   }
};

export const getCurrentUser = async () => {
   const user = getSession();
   return user ? { ...user, accessToken: getAccessToken() } : null;
};

export const getCurrentSession = async () => {
   const user = getSession();
   if (!user) return null;
   if (!getAccessToken() && !(await restoreAccessToken())) {
      clearSession();
      return null;
   }
   const me = await fetchIdentity();
   if (!me || !me.role) {
      clearSession();
      return null;
   }
   const verified = { ...user, email: me.email || user.email, role: me.role, userId: me.id ?? user.userId };
   try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(verified));
   } catch (e) {
      // storage is only a display cache
   }
   return { user: { ...verified, accessToken: getAccessToken() } };
};

// Auth state change listener
export const onAuthStateChange = (callback) => {
   authStateListeners.push(callback);

   // Initialize with current state
   const currentSession = getSession();
   if (currentSession) {
      // callback({ user: currentSession }); 
      // Don't fire immediately to avoid render loops, 
      // AuthContext calls getCurrentSession on mount anyway.
   }

   // Return unsubscribe function
   return () => {
      authStateListeners = authStateListeners.filter(listener => listener !== callback);
   };
};

// Notify all listeners of auth state change
const notifyAuthStateChange = (session) => {
   authStateListeners.forEach(listener => listener(session));
};

// Password Recovery Functions

// Request password reset email
export const forgotPassword = async (email) => {
   try {
      const response = await fetchWithTimeout(`${AUTH_URL}/forgot-password`, {
         method: 'POST',
         headers: {
            'Content-Type': 'application/json',
         },
         body: JSON.stringify({ email }),
      });

      const data = await response.json();

      if (response.ok) {
         return { success: true, message: data.message || 'Password reset email sent successfully' };
      } else {
         return { success: false, error: data.message || data.error || 'Failed to send password reset email' };
      }
   } catch (error) {
      console.error('Forgot password error:', error);
      return { success: false, error: 'Network error. Please try again.' };
   }
};

// Validate password reset token
export const validateResetToken = async (token) => {
   try {
      const response = await fetchWithTimeout(`${AUTH_URL}/validate-reset-token?token=${encodeURIComponent(token)}`, {
         method: 'GET',
         headers: {
            'Content-Type': 'application/json',
         },
      });

      const data = await response.json();

      if (response.ok) {
         return { valid: true, message: data.message };
      } else {
         return { valid: false, error: data.message || data.error || 'Invalid or expired token' };
      }
   } catch (error) {
      console.error('Validate token error:', error);
      return { valid: false, error: 'Network error. Please try again.' };
   }
};

// Reset password with token
export const resetPassword = async (token, newPassword, confirmPassword) => {
   try {
      const response = await fetchWithTimeout(`${AUTH_URL}/reset-password`, {
         method: 'POST',
         headers: {
            'Content-Type': 'application/json',
         },
         body: JSON.stringify({ token, newPassword, confirmPassword }),
      });

      const data = await response.json();

      if (response.ok) {
         return { success: true, message: data.message || 'Password reset successfully' };
      } else {
         return { success: false, error: data.message || data.error || 'Failed to reset password' };
      }
   } catch (error) {
      console.error('Reset password error:', error);
      return { success: false, error: 'Network error. Please try again.' };
   }
};

// Verify OTP for 2FA
export const verifyOtp = async (email, otp, pendingToken) => {
   try {
      const response = await fetchWithTimeout(`${AUTH_URL}/verify-otp`, {
         method: 'POST',
         headers: { 'Content-Type': 'application/json' },
         body: JSON.stringify({ email, otp, pendingToken }),
      });
      const data = await response.json();
      if (response.ok) {
         const resolvedEmail = email;
         const storedName = getProfileName(resolvedEmail);
         const fullName = data.full_name || data.fullName || storedName;
         const user = {
            email: resolvedEmail,
            role: data.role || 'PATIENT',
            fullName,
            accessToken: data.accessToken,
            userId: data.userId
         };
         if (fullName) {
            saveProfileName(resolvedEmail, fullName);
         }
         saveSession(user);
         return { success: true, user, ...data };
      } else {
         return { success: false, error: data.message || 'Invalid or expired OTP' };
      }
   } catch (error) {
      console.error('Verify OTP error:', error);
      return { success: false, error: 'Network error. Please try again.' };
   }
};

// Resend OTP
export const resendOtp = async (email, pendingToken) => {
   try {
      const response = await fetchWithTimeout(`${AUTH_URL}/resend-otp`, {
         method: 'POST',
         headers: { 'Content-Type': 'application/json' },
         body: JSON.stringify({ email, pendingToken }),
      });
      const data = await response.json();
      if (response.ok) {
         return { success: true, message: data.message || 'OTP resent' };
      } else {
         return { success: false, error: data.message || 'Failed to resend OTP' };
      }
   } catch (error) {
      console.error('Resend OTP error:', error);
      return { success: false, error: 'Network error. Please try again.' };
   }
};
