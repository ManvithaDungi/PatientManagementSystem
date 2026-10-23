import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Lock, User, Stethoscope, AlertCircle, CheckCircle } from 'lucide-react';
import api from '../services/api';

const ROLE_LABELS = {
  DOCTOR: 'Doctor',
  NURSE: 'Nurse',
  LAB_TECHNICIAN: 'Lab Technician',
  ADMIN: 'Administrator',
};

const WEAK_PATTERNS = ['password', '123456', 'qwerty', 'admin', 'letmein'];

export default function AcceptInvite() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [checking, setChecking] = useState(true);
  const [invite, setInvite] = useState(null);
  const [loadError, setLoadError] = useState('');

  const [fullName, setFullName] = useState('');
  const [specialty, setSpecialty] = useState('');
  const [contactNumber, setContactNumber] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    const check = async () => {
      if (!token) {
        setLoadError('This invitation link is missing its token.');
        setChecking(false);
        return;
      }
      try {
        const result = await api.invites.validate(token);
        setInvite(result);
      } catch (err) {
        setLoadError(err.message || 'This invitation is invalid or has expired.');
      } finally {
        setChecking(false);
      }
    };
    check();
  }, [token]);

  const validate = () => {
    if (!fullName.trim()) return 'Please enter your full name.';
    if (invite.role === 'DOCTOR' && !specialty.trim()) return 'Specialty is required for doctors.';
    if (password.length < 12) return 'Password must be at least 12 characters.';
    if (WEAK_PATTERNS.some((p) => password.toLowerCase().includes(p))) {
      return 'Password contains a common weak pattern.';
    }
    if (password !== confirmPassword) return 'Passwords do not match.';
    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const problem = validate();
    if (problem) {
      setError(problem);
      return;
    }
    setError('');
    setSubmitting(true);
    try {
      await api.invites.accept({
        token,
        password,
        fullName: fullName.trim(),
        specialty: specialty.trim() || undefined,
        contactNumber: contactNumber.trim() || undefined,
      });
      setDone(true);
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      setError(err.message || 'Unable to complete your registration.');
    } finally {
      setSubmitting(false);
    }
  };

  const inputClass =
    'w-full border border-gray-300 dark:border-slate-600 rounded-lg pl-9 pr-3 py-2 text-sm bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100 focus:ring-2 focus:ring-blue-500 outline-none';

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-gradient-to-br from-blue-50 to-cyan-50 dark:from-slate-900 dark:to-slate-800">
      <div className="w-full max-w-md bg-white dark:bg-slate-800 rounded-2xl shadow-xl p-8 space-y-5">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-slate-100">Accept invitation</h1>

        {checking && <p className="text-sm text-gray-500 dark:text-slate-400">Checking your invitation...</p>}

        {!checking && loadError && (
          <div className="flex items-start gap-2 text-sm bg-red-50 text-red-700 p-3 rounded-lg border border-red-200">
            <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
            <span>{loadError} Please ask an administrator to send a new invitation.</span>
          </div>
        )}

        {done && (
          <div className="flex items-start gap-2 text-sm bg-green-50 text-green-700 p-3 rounded-lg border border-green-200">
            <CheckCircle className="w-4 h-4 mt-0.5 shrink-0" />
            <span>Account created. Redirecting you to sign in...</span>
          </div>
        )}

        {!checking && invite && !done && (
          <form onSubmit={handleSubmit} className="space-y-4">
            <p className="text-sm text-gray-600 dark:text-slate-300">
              You have been invited to join as <strong>{ROLE_LABELS[invite.role] || invite.role}</strong>
              {' '}with the email <strong>{invite.email}</strong>.
            </p>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-gray-700 dark:text-slate-200">Full name</label>
              <div className="relative">
                <User className="absolute left-3 top-2.5 h-4 w-4 text-gray-400" />
                <input className={inputClass} value={fullName} onChange={(e) => setFullName(e.target.value)} required />
              </div>
            </div>

            {invite.role === 'DOCTOR' && (
              <div className="space-y-1">
                <label className="text-xs font-semibold text-gray-700 dark:text-slate-200">Specialty</label>
                <div className="relative">
                  <Stethoscope className="absolute left-3 top-2.5 h-4 w-4 text-gray-400" />
                  <input
                    className={inputClass}
                    placeholder="Cardiology"
                    value={specialty}
                    onChange={(e) => setSpecialty(e.target.value)}
                    required
                  />
                </div>
              </div>
            )}

            <div className="space-y-1">
              <label className="text-xs font-semibold text-gray-700 dark:text-slate-200">Contact number (optional)</label>
              <input
                className="w-full border border-gray-300 dark:border-slate-600 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100 focus:ring-2 focus:ring-blue-500 outline-none"
                value={contactNumber}
                onChange={(e) => setContactNumber(e.target.value)}
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-gray-700 dark:text-slate-200">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-2.5 h-4 w-4 text-gray-400" />
                <input
                  type="password"
                  className={inputClass}
                  placeholder="Min 12 characters"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-gray-700 dark:text-slate-200">Confirm password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-2.5 h-4 w-4 text-gray-400" />
                <input
                  type="password"
                  className={inputClass}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                />
              </div>
            </div>

            {error && (
              <div className="text-xs bg-red-50 text-red-600 p-3 rounded-lg border border-red-200">{error}</div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="w-full bg-gradient-to-r from-blue-600 to-purple-600 text-white py-2.5 rounded-lg text-sm font-bold disabled:opacity-50"
            >
              {submitting ? 'Creating account...' : 'Create account'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
