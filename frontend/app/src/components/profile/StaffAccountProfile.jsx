import React, { useState, useEffect } from 'react';
import { User, Mail, Shield, CalendarDays } from 'lucide-react';
import { Link } from 'react-router-dom';
import Card from '../common/Card';
import Button from '../common/Button';
import Badge from '../common/Badge';
import { authAPI } from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';

const ROLE_LABELS = {
   NURSE: 'Nurse',
   LAB_TECHNICIAN: 'Lab Technician',
   ADMIN: 'Administrator',
   DOCTOR: 'Doctor',
   PATIENT: 'Patient',
};

/**
 * Account page for roles that have no clinical profile record (nurse, lab
 * technician, administrator). It shows only what the server actually holds
 * about the account, so nothing here can be stale or invented.
 */
const StaffAccountProfile = ({ title }) => {
   const { user } = useAuth();
   const [account, setAccount] = useState(null);
   const [error, setError] = useState(null);
   const [loading, setLoading] = useState(true);
   const [twoFactorBusy, setTwoFactorBusy] = useState(false);
   const [message, setMessage] = useState(null);

   useEffect(() => {
      let cancelled = false;
      authAPI.getCurrentUser()
         .then((data) => { if (!cancelled) setAccount(data); })
         .catch(() => { if (!cancelled) setError('Unable to load your account details.'); })
         .finally(() => { if (!cancelled) setLoading(false); });
      return () => { cancelled = true; };
   }, []);

   const enableTwoFactor = async () => {
      setTwoFactorBusy(true);
      setMessage(null);
      try {
         await authAPI.enableTwoFactor();
         setAccount((prev) => ({ ...prev, twoFactorEnabled: true }));
         setMessage({ type: 'success', text: 'Two-factor authentication is now enabled.' });
      } catch (err) {
         setMessage({ type: 'error', text: err.message || 'Could not enable two-factor authentication.' });
      } finally {
         setTwoFactorBusy(false);
      }
   };

   const email = account?.email || user?.email || '';
   const roleLabel = ROLE_LABELS[account?.role || user?.role] || account?.role || '';
   const displayName = user?.fullName || user?.full_name || email;
   const memberSince = account?.createdAt ? new Date(account.createdAt).toLocaleDateString() : null;

   return (
      <div className="space-y-3">
         <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">{title}</h2>

         {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
         {loading && !error && <p className="text-sm text-gray-500">Loading account...</p>}

         {!loading && !error && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
               <Card className="p-4 lg:col-span-1 text-center dark:bg-slate-800">
                  <div className="w-20 h-20 rounded-full bg-blue-100 dark:bg-blue-900/30 mx-auto flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-2xl mb-2">
                     {(displayName || '?').charAt(0).toUpperCase()}
                  </div>
                  <h3 className="text-sm font-bold text-gray-900 dark:text-slate-100 break-all">{displayName}</h3>
                  <p className="text-blue-600 dark:text-blue-400 font-medium">{roleLabel}</p>
                  <div className="mt-3 flex justify-center">
                     <Badge type={account?.twoFactorEnabled ? 'green' : 'gray'}>
                        {account?.twoFactorEnabled ? '2FA enabled' : '2FA not enabled'}
                     </Badge>
                  </div>
               </Card>

               <div className="lg:col-span-2 space-y-3">
                  <Card className="p-4 dark:bg-slate-800">
                     <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-3 flex items-center">
                        <User className="w-4 h-4 mr-2 text-blue-500" />
                        Account
                     </h3>
                     <dl className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                        <div className="flex items-center gap-2 text-gray-700 dark:text-slate-200">
                           <Mail className="w-4 h-4 text-gray-400" />
                           <div>
                              <dt className="text-xs text-gray-500 dark:text-slate-400">Email</dt>
                              <dd className="break-all">{email}</dd>
                           </div>
                        </div>
                        <div className="flex items-center gap-2 text-gray-700 dark:text-slate-200">
                           <Shield className="w-4 h-4 text-gray-400" />
                           <div>
                              <dt className="text-xs text-gray-500 dark:text-slate-400">Role</dt>
                              <dd>{roleLabel}</dd>
                           </div>
                        </div>
                        {memberSince && (
                           <div className="flex items-center gap-2 text-gray-700 dark:text-slate-200">
                              <CalendarDays className="w-4 h-4 text-gray-400" />
                              <div>
                                 <dt className="text-xs text-gray-500 dark:text-slate-400">Member since</dt>
                                 <dd>{memberSince}</dd>
                              </div>
                           </div>
                        )}
                     </dl>
                  </Card>

                  <Card className="p-4 dark:bg-slate-800">
                     <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
                        <Shield className="w-4 h-4 mr-2 text-blue-500" />
                        Security
                     </h3>
                     {message && (
                        <p
                           role="status"
                           className={`text-sm mb-2 ${message.type === 'error' ? 'text-red-600' : 'text-green-700'}`}
                        >
                           {message.text}
                        </p>
                     )}
                     <div className="space-y-2">
                        <Link to="/forgot-password" className="block">
                           <Button variant="secondary" className="w-full justify-center">Change password</Button>
                        </Link>
                        {!account?.twoFactorEnabled && (
                           <Button
                              variant="outline"
                              className="w-full justify-center"
                              onClick={enableTwoFactor}
                              disabled={twoFactorBusy}
                           >
                              {twoFactorBusy ? 'Enabling...' : 'Enable two-factor authentication'}
                           </Button>
                        )}
                     </div>
                  </Card>
               </div>
            </div>
         )}
      </div>
   );
};

export default StaffAccountProfile;
