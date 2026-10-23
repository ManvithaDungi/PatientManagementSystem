import React, { useState, useEffect } from 'react';
import { Activity, Calendar, Pill, AlertCircle, Clock, Stethoscope, AlertTriangle, RefreshCw, CalendarClock, FileText } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import { getFullName } from '../../utils/formatters';

const PatientDashboard = () => {
   const { user } = useAuth();
   const navigate = useNavigate();

   // State with NO Mock Fallbacks
   const [patient, setPatient] = useState(null);
   const [appointments, setAppointments] = useState([]);
   const [prescriptions, setPrescriptions] = useState([]);
   const [labs, setLabs] = useState([]);
   const [diagnoses, setDiagnoses] = useState([]);
   const [isLoading, setIsLoading] = useState(false);
   const [error, setError] = useState(null);

   const mapAppointment = (appt) => ({
      id: appt.appointmentId,
      doctorName: appt.doctorName,
      dateTime: appt.appointmentDate,
      reason: appt.reasonForVisit,
      status: (appt.status || '').toString().toUpperCase(),
   });

   // Fetch Patient Profile and related data
   useEffect(() => {
      const fetchData = async () => {
         if (!user?.userId) return;

         setIsLoading(true);
         setError(null);
         try {
            const pData = await api.patients.getMe();
            const profileId = pData?.id;
            if (!profileId) {
               throw new Error('Patient profile not found');
            }

            const [aData, rData, lData, mData] = await Promise.all([
               api.appointments.getByPatient(profileId),
               api.prescriptions.getByPatient(profileId),
               api.labResults.getByPatient(profileId),
               api.medicalRecords.getByPatient(profileId)
            ]);

            setPatient(pData);
            setAppointments((aData || []).map(mapAppointment));
            setPrescriptions(rData || []);
            setLabs(lData || []);
            setDiagnoses(mData || []);
         } catch (err) {
            console.error('Failed to fetch patient dashboard data:', err);
            setError('Failed to load dashboard. Please refresh the page.');
         } finally {
            setIsLoading(false);
         }
      };

      fetchData();
   }, [user?.userId]);


   if (isLoading) return <div className="p-8 text-center text-gray-500 dark:text-slate-400">Loading dashboard...</div>;

   // --- Data Preparation ---
   const upcomingAppointments = appointments
      .filter(a => !['COMPLETED', 'CANCELLED'].includes((a.status || '').toUpperCase()))
      .sort((a, b) => new Date(a.dateTime) - new Date(b.dateTime))
      .slice(0, 3);

   const pendingLabs = labs.filter(l => l.status === 'PENDING' || l.status === 'Pending');
   const activeMedications = prescriptions.filter(p => p.status === 'ACTIVE');
   const recentDiagnoses = diagnoses
      .slice()
      .sort((a, b) => new Date(b.recordDate || b.createdAt) - new Date(a.recordDate || a.createdAt))
      .slice(0, 5);



   return (
      <div className="space-y-4">
         {/* Header - Compact */}
         <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
            <div>
               <h2 className="text-lg font-semibold text-gray-800 dark:text-slate-100">Patient Dashboard</h2>
               <p className="text-sm text-gray-500 dark:text-slate-400">Welcome back, {getFullName(patient) || getFullName(user)}</p>
            </div>
            <div className="flex gap-2">
               <Button variant="outline" size="sm" onClick={() => navigate('/dashboard/patient/appointments')}>
                  My Appointments
               </Button>
               <Button size="sm" onClick={() => navigate('/dashboard/patient/history')}>
                  View Records
               </Button>
            </div>
         </div>

         {error && (
            <Card className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
               <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
            </Card>
         )}

         {/* Pending Labs Alert - High Visibility */}
         {pendingLabs.length > 0 && (
            <div className="bg-orange-50 dark:bg-orange-900/20 border-l-4 border-orange-500 p-4 rounded-r-xl shadow-sm flex items-start sm:items-center gap-4 animate-fade-in">
               <div className="p-2 bg-orange-100 dark:bg-orange-900/40 rounded-full flex-shrink-0">
                  <AlertTriangle className="w-6 h-6 text-orange-600 dark:text-orange-400" />
               </div>
               <div className="flex-1">
                  <h4 className="text-base font-bold text-gray-900 dark:text-white">Action Required</h4>
                  <p className="text-sm text-gray-700 dark:text-slate-300 mt-1">
                     You have <span className="font-bold text-orange-700 dark:text-orange-300">{pendingLabs.length} pending lab results</span> waiting for your review.
                  </p>
               </div>
               <Button className="shrink-0 bg-orange-500 hover:bg-orange-600 text-white border-none shadow-md shadow-orange-500/20 font-semibold px-6 flex items-center gap-2 transition-all transform hover:scale-105 active:scale-95" onClick={() => navigate('/dashboard/patient/labs')}>
                  <span>Review Now</span>
                  <FileText className="w-4 h-4" />
               </Button>
            </div>
         )}



         <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* Left Column (2/3) - Main Content */}
            <div className="lg:col-span-2 space-y-4">

               {/* Upcoming Appointments - Compact */}
               <Card className="overflow-hidden">
                  <div className="px-4 py-3 border-b border-gray-200 dark:border-slate-700 flex justify-between items-center">
                     <h3 className="text-sm font-semibold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <Calendar className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                        Upcoming Appointments
                     </h3>
                     <button className="text-xs text-blue-600 dark:text-blue-400 hover:text-blue-700 font-medium" onClick={() => navigate('/dashboard/patient/appointments')}>
                        View All
                     </button>
                  </div>

                  {upcomingAppointments.length > 0 ? (
                     <div className="divide-y divide-gray-100 dark:divide-slate-700/50">
                        {upcomingAppointments.map((appt) => (
                           <div key={appt.id} className="p-3 hover:bg-gray-50 dark:hover:bg-slate-800/50 flex items-center justify-between gap-3">
                              <div className="flex items-center gap-3 flex-1 min-w-0">
                                 <div className="flex-shrink-0 w-10 text-center">
                                    <div className="text-xs font-semibold text-gray-900 dark:text-slate-100">{new Date(appt.dateTime).getDate()}</div>
                                    <div className="text-xs text-gray-500 dark:text-slate-400 uppercase">{new Date(appt.dateTime).toLocaleString('default', { month: 'short' })}</div>
                                 </div>
                                 <div className="w-px h-8 bg-gray-200 dark:bg-slate-700"></div>
                                 <div className="flex-1 min-w-0">
                                    <div className="text-sm font-medium text-gray-900 dark:text-slate-100 truncate">{appt.doctorName}</div>
                                    <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-slate-400">
                                       <span className="flex items-center gap-1"><Clock size={12} /> {new Date(appt.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                                       <span>•</span>
                                       <span>{appt.reason || 'Consultation'}</span>
                                    </div>
                                 </div>
                              </div>
                              <Button
                                 variant="outline"
                                 size="sm"
                                 onClick={() => navigate('/dashboard/patient/appointments')}
                                 className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400 font-medium whitespace-nowrap"
                                 title="Reschedule"
                              >
                                 <CalendarClock className="w-3.5 h-3.5" />
                                 Reschedule
                              </Button>
                           </div>
                        ))}
                     </div>
                  ) : (
                     <div className="p-6 text-center">
                        <Calendar className="w-8 h-8 text-gray-300 dark:text-slate-600 mx-auto mb-2" />
                        <p className="text-sm text-gray-500 dark:text-slate-400">No upcoming appointments</p>
                        <Button size="sm" className="mt-3" onClick={() => navigate('/dashboard/patient/appointments')}>Schedule Now</Button>
                     </div>
                  )}
               </Card>

               {/* Recent Diagnoses - Compact list */}
               <Card className="overflow-hidden">
                  <div className="px-4 py-3 border-b border-gray-200 dark:border-slate-700">
                     <h3 className="text-sm font-semibold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <Stethoscope className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                        Recent Diagnoses
                     </h3>
                  </div>
                  <div className="divide-y divide-gray-100 dark:divide-slate-700/50">
                     {recentDiagnoses.map((dx) => (
                        <div key={dx.recordId} className="px-4 py-2.5 hover:bg-gray-50 dark:hover:bg-slate-800/50 flex justify-between items-center">
                           <div className="flex-1 min-w-0">
                              <div className="text-sm font-medium text-gray-900 dark:text-slate-100">{dx.diagnosis}</div>
                              <div className="text-xs text-gray-500 dark:text-slate-400">{new Date(dx.recordDate || dx.createdAt).toLocaleDateString()} • {dx.doctorName}</div>
                           </div>
                           <Badge size="sm" type="gray">
                              Recorded
                           </Badge>
                        </div>
                     ))}
                  </div>
               </Card>
            </div>

            {/* Right Column (1/3) - Sidebar */}
            <div className="space-y-4">
               {/* Active Meds - Compact */}
               <Card>
                  <div className="px-4 py-3 border-b border-gray-200 dark:border-slate-700 flex justify-between items-center">
                     <h3 className="text-sm font-semibold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <Pill className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                        Active Meds
                     </h3>
                     <button className="text-xs text-blue-600 dark:text-blue-400 font-medium" onClick={() => navigate('/dashboard/patient/prescriptions')}>View All</button>
                  </div>
                  <div className="divide-y divide-gray-100 dark:divide-slate-700/50">
                     {activeMedications.map(rx => (
                        <div key={rx.prescriptionId} className="group p-3 hover:bg-gray-50 dark:hover:bg-slate-800/50 flex items-center justify-between">
                           <div className="flex items-center gap-3 flex-1 min-w-0">
                              <div className="w-8 h-8 rounded bg-blue-50 dark:bg-blue-900/20 flex items-center justify-center flex-shrink-0">
                                 <Pill className="w-4 h-4 text-blue-600 dark:text-blue-400" />
                              </div>
                              <div className="flex-1 min-w-0">
                                 <div className="flex items-center gap-2">
                                    <span className="text-sm font-medium text-gray-900 dark:text-slate-100">{rx.medicationName}</span>
                                    {(rx.refillsRemaining ?? 0) <= 1 && <AlertCircle className="w-3.5 h-3.5 text-red-500" />}
                                 </div>
                                 <div className="text-xs text-gray-500 dark:text-slate-400">{rx.dosage} • {rx.frequency}</div>
                              </div>
                           </div>
                           <Button
                              variant="outline"
                              size="sm"
                              className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-1.5 px-2.5 py-1 text-xs text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 font-medium"
                              title="Request Refill"
                           >
                              <RefreshCw className="w-3.5 h-3.5" />
                              Refill
                           </Button>
                        </div>
                     ))}
                     {activeMedications.length === 0 && <p className="text-sm text-gray-500 dark:text-slate-400 text-center py-4">No active medications.</p>}
                  </div>
               </Card>

               {/* Quick Help - Compact */}
               {/* Patient Support - High Visibility */}
               <Card className="bg-white dark:bg-slate-800 border-l-4 border-l-blue-500 shadow-md">
                  <div className="p-5">
                     <div className="flex items-center gap-3 mb-4">
                        <div className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center text-blue-600 dark:text-blue-400">
                           <Activity className="w-6 h-6" />
                        </div>
                        <div>
                           <h3 className="text-lg font-bold text-gray-900 dark:text-white">Need Help?</h3>
                           <p className="text-sm text-gray-500 dark:text-slate-400">24/7 Support Center</p>
                        </div>
                     </div>

                     <p className="text-sm text-gray-600 dark:text-slate-300 mb-6 leading-relaxed">
                        If you are experiencing symptoms or need urgent advice, contact your care team immediately.
                     </p>

                     <div className="space-y-3">
                        <button
                           onClick={() => navigate('/dashboard/patient/appointments')}
                           className="w-full py-3 px-4 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl shadow-lg shadow-blue-500/20 flex items-center justify-center gap-2 transition-transform transform active:scale-95"
                        >
                           <Stethoscope className="w-5 h-5" />
                           <span>Contact Doctor</span>
                        </button>
                     </div>
                  </div>
               </Card>
            </div>
         </div>
      </div>
   );
};

export default PatientDashboard;
