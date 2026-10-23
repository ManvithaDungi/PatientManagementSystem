import React, { useEffect, useMemo, useState } from 'react';
import { Search, UserCheck } from 'lucide-react';
import Card from '../../../components/common/Card';
import Button from '../../../components/common/Button';
import api from '../../../services/api';

/**
 * Admin screen for assigning each patient's treating doctor and nurse (the care team).
 * The pickers list only DOCTOR and NURSE logins that are enabled. Selections are login user ids.
 */
const CareTeamManagement = () => {
   const [patients, setPatients] = useState([]);
   const [staff, setStaff] = useState([]);
   const [loading, setLoading] = useState(true);
   const [loadError, setLoadError] = useState(null);
   const [search, setSearch] = useState('');
   const [selected, setSelected] = useState(null); // patient directory entry
   const [doctorId, setDoctorId] = useState('');
   const [nurseId, setNurseId] = useState('');
   const [saving, setSaving] = useState(false);
   const [message, setMessage] = useState(null); // { type: 'success' | 'error', text }

   const load = async () => {
      setLoading(true);
      setLoadError(null);
      try {
         const [p, s] = await Promise.all([api.admin.getAllUsers(), api.admin.getAllStaff()]);
         setPatients(Array.isArray(p) ? p : []);
         setStaff(Array.isArray(s) ? s : []);
      } catch (err) {
         setLoadError('Unable to load patients and staff. Please try again.');
      } finally {
         setLoading(false);
      }
   };

   useEffect(() => {
      load();
   }, []);

   const doctors = useMemo(() => staff.filter((u) => u.role === 'DOCTOR' && u.enabled !== false), [staff]);
   const nurses = useMemo(() => staff.filter((u) => u.role === 'NURSE' && u.enabled !== false), [staff]);

   const filtered = useMemo(() => {
      const q = search.trim().toLowerCase();
      if (!q) return patients;
      return patients.filter((p) =>
         `${p.firstName || ''} ${p.lastName || ''}`.toLowerCase().includes(q) ||
         (p.email || '').toLowerCase().includes(q));
   }, [patients, search]);

   const nameOf = (id) => {
      const u = staff.find((x) => x.userId === id);
      return u ? u.email : id ? `User #${id}` : 'Not assigned';
   };

   const choose = (patient) => {
      setSelected(patient);
      setDoctorId(patient.assignedDoctorId ? String(patient.assignedDoctorId) : '');
      setNurseId(patient.assignedNurseId ? String(patient.assignedNurseId) : '');
      setMessage(null);
   };

   const save = async () => {
      if (!selected) return;
      setSaving(true);
      setMessage(null);
      try {
         const updated = await api.admin.updateCareTeam(
            selected.profileId,
            doctorId ? Number(doctorId) : null,
            nurseId ? Number(nurseId) : null,
         );
         setPatients((prev) => prev.map((p) => (p.profileId === selected.profileId
            ? { ...p, assignedDoctorId: updated.assignedDoctorId, assignedNurseId: updated.assignedNurseId }
            : p)));
         setSelected((prev) => ({ ...prev, assignedDoctorId: updated.assignedDoctorId, assignedNurseId: updated.assignedNurseId }));
         setMessage({ type: 'success', text: 'Care team saved.' });
      } catch (err) {
         setMessage({ type: 'error', text: err.message || 'Could not save the care team.' });
      } finally {
         setSaving(false);
      }
   };

   return (
      <Card className="border-t-4 border-t-admin-secondary">
         <div className="p-6 border-b border-gray-100 dark:border-slate-700 flex items-center gap-3">
            <UserCheck size={20} className="text-admin-primary" />
            <div>
               <h2 className="text-lg font-bold text-slate-800 dark:text-white">Care Teams</h2>
               <p className="text-sm text-slate-500 dark:text-slate-400">
                  Assign each patient's treating doctor and nurse. Assigned staff can access that patient's clinical record.
               </p>
            </div>
         </div>

         {loading && <div className="p-6 text-center text-slate-500">Loading patients...</div>}
         {!loading && loadError && (
            <div className="p-6 text-center text-red-600 text-sm" role="alert">
               {loadError}{' '}
               <button type="button" className="underline" onClick={load}>Retry</button>
            </div>
         )}

         {!loading && !loadError && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 p-6">
               <div>
                  <div className="relative mb-3">
                     <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
                     <input
                        type="text"
                        aria-label="Search patients"
                        placeholder="Search patients by name or e-mail..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-slate-200 dark:border-slate-700 rounded-lg text-sm dark:bg-slate-800 dark:text-white"
                     />
                  </div>
                  {filtered.length === 0 ? (
                     <p className="text-sm text-slate-500">No patients found.</p>
                  ) : (
                     <ul className="divide-y divide-gray-100 dark:divide-slate-700 max-h-96 overflow-y-auto">
                        {filtered.map((p) => (
                           <li key={p.profileId}>
                              <button
                                 type="button"
                                 onClick={() => choose(p)}
                                 className={`w-full text-left px-3 py-2 text-sm hover:bg-slate-50 dark:hover:bg-slate-800 ${selected?.profileId === p.profileId ? 'bg-slate-100 dark:bg-slate-700' : ''}`}
                              >
                                 <span className="font-medium text-slate-800 dark:text-white">{p.firstName} {p.lastName}</span>
                                 <span className="block text-xs text-slate-500">{p.email}</span>
                              </button>
                           </li>
                        ))}
                     </ul>
                  )}
               </div>

               <div>
                  {!selected ? (
                     <p className="text-sm text-slate-500">Select a patient to edit their care team.</p>
                  ) : (
                     <div className="space-y-4">
                        <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                           {selected.firstName} {selected.lastName}
                        </h3>
                        <p className="text-xs text-slate-500">
                           Current: doctor {nameOf(selected.assignedDoctorId)}; nurse {nameOf(selected.assignedNurseId)}
                        </p>

                        <label className="block text-sm text-slate-700 dark:text-slate-200">
                           Treating doctor
                           <select
                              aria-label="Treating doctor"
                              value={doctorId}
                              onChange={(e) => setDoctorId(e.target.value)}
                              className="mt-1 w-full px-3 py-2 border border-slate-200 dark:border-slate-700 rounded-lg text-sm dark:bg-slate-800 dark:text-white"
                           >
                              <option value="">Not assigned</option>
                              {doctors.map((d) => <option key={d.userId} value={d.userId}>{d.email}</option>)}
                           </select>
                        </label>

                        <label className="block text-sm text-slate-700 dark:text-slate-200">
                           Assigned nurse
                           <select
                              aria-label="Assigned nurse"
                              value={nurseId}
                              onChange={(e) => setNurseId(e.target.value)}
                              className="mt-1 w-full px-3 py-2 border border-slate-200 dark:border-slate-700 rounded-lg text-sm dark:bg-slate-800 dark:text-white"
                           >
                              <option value="">Not assigned</option>
                              {nurses.map((n) => <option key={n.userId} value={n.userId}>{n.email}</option>)}
                           </select>
                        </label>

                        <Button onClick={save} disabled={saving}>
                           {saving ? 'Saving...' : 'Save care team'}
                        </Button>

                        {message && (
                           <p role={message.type === 'error' ? 'alert' : 'status'}
                              className={`text-sm ${message.type === 'success' ? 'text-green-600' : 'text-red-600'}`}>
                              {message.text}
                           </p>
                        )}
                     </div>
                  )}
               </div>
            </div>
         )}
      </Card>
   );
};

export default CareTeamManagement;
