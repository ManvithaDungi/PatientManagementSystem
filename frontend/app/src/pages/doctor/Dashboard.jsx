import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users, FileText, ArrowRight, Activity, Bell, Search } from 'lucide-react';

import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';

import AppointmentList from '../../components/AppointmentList';
import MiniCalendar from '../../components/MiniCalendar';
import api from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';
import { getFullName } from '../../utils/formatters';
import { isAbnormal, normalizeStatus } from '../../utils/labRange';


const DoctorDashboard = () => {
   const { user } = useAuth();
   const navigate = useNavigate();
   const [searchTerm, setSearchTerm] = useState('');
   const [isLoading, setIsLoading] = useState(false);
   const [error, setError] = useState(null);

   const doctorName = getFullName(user) || 'Doctor';

   // State for data
   const [patients, setPatients] = useState([]);
   const [appointments, setAppointments] = useState([]);
   const [labs, setLabs] = useState([]);

   // Fetch data from API
   useEffect(() => {
      const fetchData = async () => {
         const doctorId = user?.userId;
         if (!doctorId) return;

         setIsLoading(true);
         setError(null);
         try {
            // Fetch doctor's patients and appointments in parallel
            const [patientsData, appointmentsData, labsData] = await Promise.all([
               api.doctors.getPatients(doctorId),
               api.appointments.getByDoctor(doctorId),
               api.labResults.getAll()
            ]);

            setPatients(patientsData || []);
            setLabs(Array.isArray(labsData) ? labsData : []);
            setAppointments((appointmentsData || []).map(a => ({
               id: a.appointmentId,
               date: a.appointmentDate,
               time: a.appointmentDate
                  ? new Date(a.appointmentDate).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
                  : '',
               patientName: a.patientName,
               type: a.status || 'Appointment',
            })));
         } catch (err) {
            console.error('Failed to fetch dashboard data:', err);
            setError('Failed to load dashboard data. Please refresh the page.');
         } finally {
            setIsLoading(false);
         }
      };

      fetchData();
   }, [user?.userId]);

   // Filter patients based on search
   const filteredPatients = patients.filter(p => {
      const name = `${p.firstName || ''} ${p.lastName || ''}`.toLowerCase();
      const id = (p.id || '').toString().toLowerCase();
      return name.includes(searchTerm.toLowerCase()) || id.includes(searchTerm.toLowerCase());
   });

   // Get today's appointments
   const today = new Date().toISOString().split('T')[0];
   const todaysAppointments = appointments.filter(a => 
      a.date && a.date.split('T')[0] === today
   ).length;

   // Orders still waiting for a published result, and published results outside their range
   const pendingLabs = labs.filter(l => normalizeStatus(l.status) !== 'completed').length;
   const abnormalLabs = labs.filter(isAbnormal).length;

   return (
      <div className="space-y-4">
         <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
            <div>
               <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100 tracking-tight">Doctor Dashboard</h2>
               <p className="text-gray-500 dark:text-slate-400 text-sm">Welcome back, {doctorName}. Here's your daily overview.</p>
            </div>
            <div className="flex gap-3">
               <Button variant="outline" onClick={() => navigate('/dashboard/doctor/appointments')} className="text-gray-600 dark:text-slate-300 border-gray-300 dark:border-slate-600 hover:bg-gray-50 dark:hover:bg-slate-700">View Schedule</Button>
               <Button onClick={() => navigate('/dashboard/doctor/prescriptions')} className="bg-brand-medium hover:bg-brand-deep shadow-md shadow-brand-medium/20">+ New Prescription</Button>
            </div>
         </div>

         {error && (
            <Card className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
               <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
            </Card>
         )}

         {isLoading ? (
            <Card className="p-6 text-center">
               <p className="text-gray-500 dark:text-slate-400">Loading dashboard...</p>
            </Card>
         ) : (
            <>
         {/* Metrics */}
         <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            <Card className="p-3 border border-gray-100 dark:border-slate-700 shadow-soft hover:shadow-lg transition-shadow duration-300 flex items-center justify-between group dark:bg-slate-800">
               <div>
                  <h3 className="text-gray-500 dark:text-slate-400 text-xs font-medium">Total Patients</h3>
                  <p className="text-xl font-bold text-gray-800 dark:text-slate-100 mt-1 group-hover:text-brand-medium transition-colors">{patients.length}</p>
               </div>
               <div className="w-8 h-8 bg-blue-50 dark:bg-blue-900/20 rounded-full flex items-center justify-center text-brand-medium group-hover:scale-110 transition-transform">
                  <Users className="w-4 h-4" />
               </div>
            </Card>
            <Card className="p-3 border border-gray-100 dark:border-slate-700 shadow-soft hover:shadow-lg transition-shadow duration-300 flex items-center justify-between group dark:bg-slate-800">
               <div>
                  <h3 className="text-gray-500 dark:text-slate-400 text-xs font-medium">Appointments</h3>
                  <p className="text-xl font-bold text-gray-800 dark:text-slate-100 mt-1 group-hover:text-brand-medium transition-colors">{todaysAppointments}</p>
               </div>
               <div className="w-8 h-8 bg-green-50 dark:bg-green-900/20 rounded-full flex items-center justify-center text-green-600 group-hover:scale-110 transition-transform">
                  <FileText className="w-4 h-4" />
               </div>
            </Card>
            <Card className="p-3 border border-gray-100 dark:border-slate-700 shadow-soft hover:shadow-lg transition-shadow duration-300 flex items-center justify-between group dark:bg-slate-800">
               <div>
                  <h3 className="text-gray-500 dark:text-slate-400 text-xs font-medium">Pending Labs</h3>
                  <p className="text-xl font-bold text-gray-800 dark:text-slate-100 mt-1 group-hover:text-brand-medium transition-colors">{pendingLabs}</p>
               </div>
               <div className="w-8 h-8 bg-yellow-50 dark:bg-yellow-900/20 rounded-full flex items-center justify-center text-yellow-600 group-hover:scale-110 transition-transform">
                  <Activity className="w-4 h-4" />
               </div>
            </Card>

         </div>

         <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* Left Column (Charts & Lists) */}
            <div className="lg:col-span-2 space-y-4">


               {/* Patient List */}
               <Card className="overflow-hidden border border-gray-100 dark:border-slate-700 shadow-soft dark:bg-slate-800">
                  <div className="px-4 py-3 border-b border-gray-100 dark:border-slate-700 flex flex-col sm:flex-row justify-between items-center bg-white dark:bg-slate-800 gap-2">
                     <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100">Recent Patients</h3>

                     <div className="relative w-full sm:w-64">
                        <input
                           type="text"
                           placeholder="Search patients..."
                           value={searchTerm}
                           onChange={(e) => setSearchTerm(e.target.value)}
                           className="w-full pl-10 pr-4 py-2 border border-gray-200 dark:border-slate-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-medium dark:bg-slate-700 dark:text-slate-100 dark:placeholder-slate-400"
                        />
                        <Search className="w-4 h-4 text-gray-400 dark:text-slate-500 absolute left-3 top-3" />
                     </div>
                  </div>

                  {filteredPatients.length > 0 ? (
                     <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-100 dark:divide-slate-700">
                           <thead className="bg-gray-50/50 dark:bg-slate-700/50">
                              <tr>
                                 <th className="px-4 py-2 text-left text-xs font-semibold text-gray-500 dark:text-slate-300 uppercase tracking-wider">Patient</th>
                                 <th className="px-4 py-2 text-left text-xs font-semibold text-gray-500 dark:text-slate-300 uppercase tracking-wider">Age/Gender</th>
                                 <th className="px-4 py-2 text-left text-xs font-semibold text-gray-500 dark:text-slate-300 uppercase tracking-wider">Condition</th>
                                 <th className="px-4 py-2 text-left text-xs font-semibold text-gray-500 dark:text-slate-300 uppercase tracking-wider">Status</th>
                                 <th className="px-4 py-2 text-left text-xs font-semibold text-gray-500 dark:text-slate-300 uppercase tracking-wider">Last Visit</th>
                                 <th className="px-4 py-2 text-right text-xs font-semibold text-gray-500 dark:text-slate-300 uppercase tracking-wider">Action</th>
                              </tr>
                           </thead>
                           <tbody className="bg-white dark:bg-slate-800 divide-y divide-gray-100 dark:divide-slate-700">
                              {filteredPatients.map((patient) => (
                                 <tr key={patient.id} className="hover:bg-slate-50/80 dark:hover:bg-slate-700/50 transition-colors duration-150 group">
                                    <td className="px-4 py-2 whitespace-nowrap">
                                       <div className="flex items-center">
                                          <div className="flex-shrink-0 h-8 w-8 rounded-full bg-brand-light dark:bg-brand-medium/20 flex items-center justify-center text-brand-deep dark:text-brand-light text-xs font-bold border border-brand-medium/10">
                                             {`${patient.firstName?.charAt(0) || ''}${patient.lastName?.charAt(0) || ''}`}
                                          </div>
                                          <div className="ml-2">
                                             <div className="text-xs font-semibold text-gray-900 dark:text-slate-100 group-hover:text-brand-deep dark:group-hover:text-brand-light transition-colors">{patient.firstName} {patient.lastName}</div>
                                             <div className="text-xs text-gray-500 dark:text-slate-400">ID: {patient.id}</div>
                                          </div>
                                       </div>
                                    </td>
                                    <td className="px-4 py-2 whitespace-nowrap">
                                       <div className="text-xs text-gray-900 dark:text-slate-100">{patient.dateOfBirth ? Math.floor((Date.now() - new Date(patient.dateOfBirth)) / (365.25 * 24 * 60 * 60 * 1000)) + ' yrs' : 'N/A'}</div>
                                       <div className="text-xs text-gray-500 dark:text-slate-400">{patient.gender}</div>
                                    </td>
                                    <td className="px-4 py-2 whitespace-nowrap">
                                       <span className="text-xs text-gray-700 dark:text-slate-300 bg-gray-100 dark:bg-slate-700 px-1.5 py-0.5 rounded">{patient.medicalHistory || 'N/A'}</span>
                                    </td>
                                    <td className="px-4 py-2 whitespace-nowrap">
                                       <Badge type="green">
                                          Active
                                       </Badge>
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-slate-400">
                                       N/A
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                                       <button
                                          className="text-brand-medium hover:text-brand-deep dark:hover:text-brand-light flex items-center justify-end w-full opacity-0 group-hover:opacity-100 transition-opacity"
                                          onClick={() => navigate(`/dashboard/doctor/patient/${patient.id}`)}
                                       >
                                          View <ArrowRight className="w-4 h-4 ml-1" />
                                       </button>
                                    </td>
                                 </tr>
                              ))}
                           </tbody>
                        </table>
                     </div>
                  ) : (
                     <div className="p-6 text-center">
                        <Users className="w-8 h-8 text-gray-300 dark:text-slate-600 mx-auto mb-2" />
                        <h3 className="text-sm font-medium text-gray-900 dark:text-slate-100">No patients found</h3>
                        <p className="text-xs text-gray-500 dark:text-slate-400">Try adjusting your search terms.</p>
                     </div>
                  )}
               </Card>
            </div>

            {/* Right Column (Side Panel) */}
            <div className="space-y-4">
               {/* Mini Calendar */}
               <MiniCalendar appointments={appointments} />

               {/* Upcoming Appointments */}
               <Card className="p-3 border border-gray-100 dark:border-slate-700 shadow-soft h-fit dark:bg-slate-800">
                  <div className="flex justify-between items-center mb-3">
                     <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100">Upcoming List</h3>
                     <button onClick={() => navigate('/dashboard/doctor/appointments')} className="text-xs text-brand-medium hover:underline font-medium bg-transparent border-none cursor-pointer">See all</button>
                  </div>
                  <AppointmentList appointments={appointments.slice(0, 5)} />
               </Card>

               {/* Daily briefing: real counts only */}
               <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-blue-600 to-blue-800 shadow-xl shadow-blue-900/20 group">
                  {/* Decorative Background Elements */}
                  <div className="absolute top-0 right-0 -mr-8 -mt-8 w-32 h-32 bg-white/10 rounded-full blur-2xl group-hover:scale-110 transition-transform duration-700"></div>
                  <div className="absolute bottom-0 left-0 -ml-8 -mb-8 w-24 h-24 bg-purple-500/20 rounded-full blur-xl"></div>
                  <div className="absolute inset-0 bg-gradient-to-t from-black/10 to-transparent"></div>

                  <div className="relative z-10 p-5">
                     <div className="flex items-center gap-3 mb-3">
                        <div className="w-10 h-10 rounded-xl bg-white/20 backdrop-blur-md flex items-center justify-center shadow-inner border border-white/10">
                           <Bell className="w-5 h-5 text-white" />
                        </div>
                        <div>
                           <h3 className="font-bold text-white text-lg leading-tight">Daily Briefing</h3>
                           <p className="text-blue-100 text-xs font-medium">{new Date().toLocaleDateString('en-US', { weekday: 'long', day: 'numeric', month: 'short' })}</p>
                        </div>
                     </div>

                     <div className="space-y-3 mb-5">
                        <div className="flex items-start gap-2 text-blue-50 text-sm">
                           <div className="w-1.5 h-1.5 rounded-full bg-red-400 mt-1.5 shadow-[0_0_8px_rgba(248,113,113,0.6)]"></div>
                           <p className="leading-snug"><span className="font-semibold text-white">{abnormalLabs} abnormal</span> lab {abnormalLabs === 1 ? 'result' : 'results'} to review.</p>
                        </div>
                        <div className="flex items-start gap-2 text-blue-50 text-sm">
                           <div className="w-1.5 h-1.5 rounded-full bg-yellow-400 mt-1.5"></div>
                           <p className="leading-snug">{pendingLabs} lab {pendingLabs === 1 ? 'order' : 'orders'} awaiting results.</p>
                        </div>
                     </div>

                     <button onClick={() => navigate('/dashboard/doctor/labs')} className="w-full py-2.5 px-4 bg-white/90 hover:bg-white text-blue-700 font-bold rounded-xl shadow-lg shadow-black/5 transition-all duration-200 transform hover:-translate-y-0.5 active:translate-y-0 active:scale-95 flex items-center justify-center gap-2">
                        <span>Review Action Items</span>
                        <ArrowRight className="w-4 h-4" />
                     </button>
                  </div>
               </div>
            </div>
         </div>
            </>
         )}
      </div >
   );
};

export default DoctorDashboard;