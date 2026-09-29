import React, { useState, useMemo, useEffect, useCallback } from 'react';
import {
   Calendar, Clock, Plus, AlertCircle, User, Building2, Check, ChevronRight, ChevronLeft, Bell, List, LayoutGrid,
   RefreshCw,
} from 'lucide-react';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';
import Modal from '../../components/common/Modal';
import Alert from '../../components/common/Alert';
import Input from '../../components/common/Input';
import api from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';
import { format, addDays, startOfMonth, endOfMonth, eachDayOfInterval, isSameDay, parseISO, differenceInDays, differenceInHours, differenceInMinutes } from 'date-fns';

const PatientAppointments = () => {
   const { user } = useAuth();

   const normalizeAppointment = (appt) => {
      const appointmentDate = appt.appointmentDate || appt.startTime || appt.date;
      const statusRaw = (appt.status || '').toString().toUpperCase();
      return {
         ...appt,
         id: appt.appointmentId ?? appt.id,
         appointmentId: appt.appointmentId ?? appt.id,
         startTime: appointmentDate,
         date: appointmentDate,
         time: appointmentDate ? format(new Date(appointmentDate), 'HH:mm') : '',
         type: appt.appointmentType || appt.reasonForVisit || appt.type || 'Consultation',
         statusRaw,
      };
   };

   const [appointments, setAppointments] = useState([]);
   const [doctors, setDoctors] = useState([]);
   const [departments, setDepartments] = useState([]);
   const [availableSlots, setAvailableSlots] = useState([]);
   const [isLoadingSlots, setIsLoadingSlots] = useState(false);
   const [rescheduleSlots, setRescheduleSlots] = useState([]);
   const [isLoadingRescheduleSlots, setIsLoadingRescheduleSlots] = useState(false);
   const [isLoading, setIsLoading] = useState(false);
   const [error, setError] = useState(null);
   const [activeTab, setActiveTab] = useState('upcoming');
   const [viewMode, setViewMode] = useState('list'); // 'list' or 'calendar'

   // Request Appointment Modal State
   const [isRequestModalOpen, setIsRequestModalOpen] = useState(false);
   const [requestStep, setRequestStep] = useState(1);
   const [requestForm, setRequestForm] = useState({
      doctorId: '',
      doctor: '',
      department: '',
      startTime: '',
      date: '',
      time: '',
      type: '',
      reason: '',
      specialRequirements: '',
   });
   const [calendarAddedEvents, setCalendarAddedEvents] = useState([]);

   // Cancel Appointment Modal State
   const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
   const [appointmentToCancel, setAppointmentToCancel] = useState(null);
   const [cancellationReason, setCancellationReason] = useState('');

   // Details View Modal State
   const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
   const [selectedAppointment, setSelectedAppointment] = useState(null);

   // Reschedule Modal State
   const [isRescheduleModalOpen, setIsRescheduleModalOpen] = useState(false);
   const [appointmentToReschedule, setAppointmentToReschedule] = useState(null);
   const [rescheduleData, setRescheduleData] = useState({ date: '', time: '', startTime: '' });

   // Calendar State
   const [currentMonth, setCurrentMonth] = useState(new Date());

   // Load appointments, doctors, and departments on mount
   const loadData = useCallback(async () => {
      if (!user?.userId) return;
      setIsLoading(true);
      setError(null);
      try {
         const patientData = await api.patients.getMe();
         const profileId = patientData?.id;
         if (!profileId) {
            throw new Error('Patient profile not found');
         }
         const [appointmentsData, doctorsData, departmentsData] = await Promise.all([
            api.appointments.getByPatient(profileId),
            api.doctors.getAll(),
            api.catalog.getHospitalDepartments().catch(() => [])
         ]);
         setAppointments((appointmentsData || []).map(normalizeAppointment));
         setDoctors(doctorsData);
         setDepartments(departmentsData || []);
      } catch (err) {
         console.error('Failed to load appointments:', err);
         setError('Failed to load appointments. Please refresh the page.');
      } finally {
         setIsLoading(false);
      }
   }, [user?.userId]);

   // Fetch real open time slots for a doctor on a given date
   const fetchSlotsFor = useCallback(async (doctorId, date, setter, loadingSetter) => {
      if (!doctorId || !date) {
         setter([]);
         return;
      }
      loadingSetter(true);
      try {
         const slots = await api.appointments.getAvailableSlots(doctorId, date);
         setter(Array.isArray(slots) ? slots : []);
      } catch (err) {
         console.error('Failed to load available slots:', err);
         setter([]);
      } finally {
         loadingSetter(false);
      }
   }, []);

   useEffect(() => {
      loadData();
   }, [loadData]);

   // Fetch real slots for the booking wizard whenever the chosen doctor/date changes
   useEffect(() => {
      const doctorId = requestForm.doctor || requestForm.doctorId;
      if (isRequestModalOpen && doctorId && requestForm.date) {
         fetchSlotsFor(doctorId, requestForm.date, setAvailableSlots, setIsLoadingSlots);
      } else {
         setAvailableSlots([]);
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
   }, [isRequestModalOpen, requestForm.doctor, requestForm.doctorId, requestForm.date]);

   // Fetch real slots for the reschedule modal whenever the chosen date changes
   useEffect(() => {
      if (isRescheduleModalOpen && appointmentToReschedule?.doctorId && rescheduleData.date) {
         fetchSlotsFor(appointmentToReschedule.doctorId, rescheduleData.date, setRescheduleSlots, setIsLoadingRescheduleSlots);
      } else {
         setRescheduleSlots([]);
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
   }, [isRescheduleModalOpen, appointmentToReschedule, rescheduleData.date]);

   // Filter appointments by tab
   const filteredAppointments = useMemo(() => {
      return appointments.filter(a => {
         if (activeTab === 'upcoming') {
            return !['COMPLETED', 'CANCELLED', 'REJECTED'].includes(a.statusRaw || 'PENDING_APPROVAL');
         } else if (activeTab === 'past') {
            return a.statusRaw === 'COMPLETED';
         } else if (activeTab === 'cancelled') {
            return ['CANCELLED', 'REJECTED'].includes(a.statusRaw);
         }
         return true;
      });
   }, [appointments, activeTab]);

   // Get next 3 upcoming appointments for reminder sidebar
   const upcomingReminders = useMemo(() => {
      return appointments
         .filter(a => !['COMPLETED', 'CANCELLED', 'REJECTED'].includes(a.statusRaw || ''))
         .sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
         .slice(0, 3);
   }, [appointments]);

   // Get doctors filtered by selected department
   const filteredDoctors = useMemo(() => {
      if (!requestForm.department || !doctors.length) {
         return doctors;
      }
      
      // Find the selected department name
      const selectedDept = departments.find(dept => dept.id == requestForm.department);
      if (!selectedDept) return doctors;
      
      // Get department name for filtering
      const deptName = selectedDept.name.trim();
      
      // Filter doctors based on department match
      return doctors.filter(doctor => {
         const doctorDept = doctor.department || doctor.specialty || '';
         const doctorSpecialty = doctor.specialty || '';
         
         // Match department names (case insensitive)
         if (deptName.toLowerCase() === 'general') {
            return doctorDept.toLowerCase().includes('general') || 
                   doctorDept.toLowerCase().includes('internal') ||
                   doctorSpecialty.toLowerCase().includes('general');
         }
         
         return doctorDept.toLowerCase().includes(deptName.toLowerCase()) ||
                doctorSpecialty.toLowerCase().includes(deptName.toLowerCase());
      });
   }, [doctors, departments, requestForm.department]);

   // Calendar days for current month
   const calendarDays = useMemo(() => {
      const start = startOfMonth(currentMonth);
      const end = endOfMonth(currentMonth);
      return eachDayOfInterval({ start, end });
   }, [currentMonth]);

   // Get appointments for a specific date
   const getAppointmentsForDate = (date) => {
      return appointments.filter(a => isSameDay(parseISO(a.startTime?.split('T')[0]), date));
   };

   // Calculate countdown for appointment
   const getCountdown = (appointment) => {
      const appointmentDate = new Date(appointment.startTime);
      const now = new Date();
      const days = differenceInDays(appointmentDate, now);
      const hours = differenceInHours(appointmentDate, now) % 24;
      const minutes = differenceInMinutes(appointmentDate, now) % 60;

      if (days > 0) return `${days}d ${hours}h`;
      if (hours > 0) return `${hours}h ${minutes}m`;
      return `${minutes}m`;
   };

   // Appointment types
   const appointmentTypes = ['Consultation', 'Follow-up', 'Check-up', 'Emergency'];

   // Cancellation reasons
   const cancellationReasons = [
      'Scheduling conflict',
      'Emergency',
      'Other reasons',
      'Doctor unavailable',
      'Patient unavailable'
   ];

   // Handle request appointment submission
   const handleRequestSubmit = async (e) => {
      e.preventDefault();
      if (requestStep < 3) {
         setRequestStep(requestStep + 1);
      } else {
         try {
            setIsLoading(true);
            setError(null);

            // Validate required fields
            if (!requestForm.doctor && !requestForm.doctorId) {
               throw new Error('Please select a doctor');
            }
            if (!requestForm.date) {
               throw new Error('Please select a date');
            }
            if (!requestForm.time) {
               throw new Error('Please select a time');
            }
            if (!requestForm.type) {
               throw new Error('Please select an appointment type');
            }
            if (!requestForm.reason) {
               throw new Error('Please describe the reason for your visit');
            }

            const dateTimeString = `${requestForm.date}T${requestForm.time}:00`;
            const payload = {
               doctorId: parseInt(requestForm.doctor || requestForm.doctorId),
               appointmentDate: dateTimeString,
               reasonForVisit: requestForm.reason,
               appointmentType: requestForm.type,
               specialRequirements: requestForm.specialRequirements || null,
            };

            const newAppointment = await api.appointments.create(payload);
            setAppointments([normalizeAppointment(newAppointment), ...appointments]);
            setIsRequestModalOpen(false);
            setRequestStep(1);
            setRequestForm({
               doctorId: '',
               doctor: '',
               department: '',
               startTime: '',
               date: '',
               time: '',
               type: '',
               reason: '',
               specialRequirements: '',
            });
            alert('Appointment request submitted! We will confirm shortly.');
         } catch (err) {
            console.error('Failed to create appointment:', err);
            setError(err.message || 'Failed to create appointment. Please try again.');
         } finally {
            setIsLoading(false);
         }
      }
   };

   // Handle cancel appointment
   const handleCancelAppointment = async () => {
      if (!cancellationReason) {
         setError('Please select a cancellation reason');
         return;
      }

      try {
         setIsLoading(true);
         setError(null);

         await api.appointments.cancel(appointmentToCancel.id, { reason: cancellationReason });
         
         setAppointments(
            appointments.map(a =>
               a.id === appointmentToCancel.id
                  ? { ...a, status: 'CANCELLED', statusRaw: 'CANCELLED', cancellationReason }
                  : a
            )
         );

         setIsCancelModalOpen(false);
         setAppointmentToCancel(null);
         setCancellationReason('');
         alert('📧 Appointment cancelled. Confirmation email sent.');
      } catch (err) {
         console.error('Failed to cancel appointment:', err);
         setError(err.message || 'Failed to cancel appointment. Please try again.');
      } finally {
         setIsLoading(false);
      }
   };

   // Open cancel modal
   const openCancelModal = (appointment) => {
      setAppointmentToCancel(appointment);
      setIsCancelModalOpen(true);
   };

   // Open details modal
   const openDetailsModal = (appointment) => {
      setSelectedAppointment(appointment);
      setIsDetailsModalOpen(true);
   };

   // Open reschedule modal
   const openRescheduleModal = (appointment) => {
      setAppointmentToReschedule(appointment);
      setRescheduleData({ date: '', time: '', startTime: appointment.startTime });
      setIsRescheduleModalOpen(true);
   };

   // Handle reschedule submit
   const handleRescheduleSubmit = async (e) => {
      e.preventDefault();
      try {
         setIsLoading(true);
         setError(null);

         const newDateTime = `${rescheduleData.date}T${rescheduleData.time}:00`;
         const updated = await api.appointments.reschedule(appointmentToReschedule.id, newDateTime);

         setAppointments(
            appointments.map(a =>
               a.id === appointmentToReschedule.id
                  ? normalizeAppointment(updated)
                  : a
            )
         );
         setIsRescheduleModalOpen(false);
         setAppointmentToReschedule(null);
         alert('🔄 Reschedule request submitted successfully!');
      } catch (err) {
         console.error('Failed to reschedule appointment:', err);
         setError(err.message || 'Failed to reschedule appointment. Please try again.');
      } finally {
         setIsLoading(false);
      }
   };

   // Handle toggle add to calendar
   const handleToggleCalendar = (appointmentId) => {
      if (calendarAddedEvents.includes(appointmentId)) {
         setCalendarAddedEvents(calendarAddedEvents.filter(id => id !== appointmentId));
      } else {
         setCalendarAddedEvents([...calendarAddedEvents, appointmentId]);
      }
   };

   // Render appointment card - Compact
   const renderAppointmentCard = (appt) => {
      const dateObj = parseISO(appt.date);
      const month = format(dateObj, 'MMM');
      const day = format(dateObj, 'dd');

      return (
         <Card
            key={appt.id}
            className="p-3 flex flex-col md:flex-row justify-between items-start md:items-center hover:border-gray-300 dark:hover:border-slate-600"
         >
            <div className="flex items-start gap-3 w-full md:w-auto">
               {/* Date Badge - Compact */}
               <div className="bg-blue-600 p-2 rounded-md text-white text-center min-w-[48px]">
                  <p className="text-xs font-medium uppercase">{month}</p>
                  <p className="text-lg font-bold leading-tight">{day}</p>
               </div>

               {/* Appointment Details - Compact */}
               <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                     <h4 className="text-sm font-semibold text-gray-800 dark:text-slate-100 truncate">{appt.type}</h4>
                     <Badge
                        size="sm"
                        type={
                           appt.statusRaw === 'SCHEDULED'
                              ? 'green'
                              : appt.statusRaw === 'PENDING_APPROVAL'
                                 ? 'yellow'
                                 : appt.statusRaw === 'COMPLETED'
                                    ? 'blue'
                                    : 'red'
                        }
                     >
                        {(appt.statusRaw || '').replaceAll('_', ' ')}
                     </Badge>
                  </div>

                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-gray-600 dark:text-slate-300">
                     <div className="flex items-center gap-1">
                        <User className="w-3 h-3 text-gray-400" />
                        <span className="truncate">{appt.doctorName}</span>
                     </div>
                     <div className="flex items-center gap-1">
                        <Clock className="w-3 h-3 text-gray-400" />
                        <span>{appt.time}</span>
                     </div>
                     {appt.reasonForVisit && (
                        <div className="col-span-2 flex items-center gap-1">
                           <Building2 className="w-3 h-3 text-gray-400 shrink-0" />
                           <span className="truncate">{appt.reasonForVisit}</span>
                        </div>
                     )}
                  </div>

                  {appt.cancellationReason && (
                     <div className="mt-1 text-xs text-red-600 dark:text-red-400 flex items-center gap-1">
                        <AlertCircle className="w-3 h-3" />
                        {appt.cancellationReason}
                     </div>
                  )}
               </div>
            </div>

            {/* Action Buttons - Compact */}
            <div className="mt-2 md:mt-0 flex gap-2">
               {activeTab === 'upcoming' && appt.statusRaw !== 'CANCELLED' && (
                  <>
                     <Button
                        variant="outline"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); openDetailsModal(appt); }}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-700 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400 font-medium bg-white dark:bg-slate-800"
                        title="View Details"
                     >
                        <Calendar className="w-3.5 h-3.5" />
                        View
                     </Button>
                     <Button
                        variant="outline"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); openRescheduleModal(appt); }}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-700 hover:text-orange-600 dark:text-slate-300 dark:hover:text-orange-400 font-medium bg-white dark:bg-slate-800"
                        title="Reschedule"
                     >
                        <RefreshCw className="w-3.5 h-3.5" />
                        Reschedule
                     </Button>
                     <Button
                        variant="outline"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); openCancelModal(appt); }}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-700 hover:text-red-600 border-gray-200 hover:border-red-300 dark:text-slate-300 dark:hover:text-red-400 dark:border-slate-700 dark:hover:border-red-800 font-medium bg-white dark:bg-slate-800"
                        title="Cancel"
                     >
                        <AlertCircle className="w-3.5 h-3.5" />
                        Cancel
                     </Button>
                  </>
               )}
               {activeTab === 'past' && (
                  <>
                     <Button
                        variant="outline"
                        size="sm"
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-700 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400 font-medium bg-white dark:bg-slate-800"
                        title="View Summary"
                     >
                        <Calendar className="w-3.5 h-3.5" />
                        Summary
                     </Button>
                     <Button
                        variant="outline"
                        size="sm"
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-blue-700 hover:text-blue-800 border-blue-200 hover:bg-blue-50 dark:text-blue-400 dark:hover:text-blue-300 dark:border-blue-800 dark:hover:bg-blue-900/30 font-medium bg-white dark:bg-slate-800"
                        title="Book Follow-up"
                     >
                        <Plus className="w-3.5 h-3.5" />
                        Follow-up
                     </Button>
                  </>
               )}
               {activeTab === 'cancelled' && (
                  <Button
                     variant="outline"
                     size="sm"
                     className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-blue-700 hover:text-blue-800 border-blue-200 hover:bg-blue-50 dark:text-blue-400 dark:hover:text-blue-300 dark:border-blue-800 dark:hover:bg-blue-900/30 font-medium bg-white dark:bg-slate-800"
                     title="Rebook"
                  >
                     <RefreshCw className="w-3.5 h-3.5" />
                     Rebook
                  </Button>
               )}
            </div>
         </Card>
      );
   };

   return (
      <div className="space-y-4">
         {error && (
            <Card className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
               <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
            </Card>
         )}

         {isLoading && (
            <Card className="p-6 text-center">
               <p className="text-gray-500 dark:text-slate-400">Loading appointments...</p>
            </Card>
         )}

         {/* Header - Compact */}
         <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
            <div>
               <h2 className="text-lg font-semibold text-gray-800 dark:text-slate-100">My Appointments</h2>
               <p className="text-sm text-gray-500 dark:text-slate-400">Manage your healthcare appointments</p>
            </div>
            <div className="flex items-center gap-2">
               <div className="flex bg-gray-100 dark:bg-slate-700 p-0.5 rounded-md">
                  <button
                     onClick={() => setViewMode('list')}
                     className={`p-1.5 rounded transition-all ${viewMode === 'list' ? 'bg-white dark:bg-slate-800 shadow-sm text-blue-600 dark:text-blue-400' : 'text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-200'}`}
                     title="List View"
                  >
                     <List size={16} />
                  </button>
                  <button
                     onClick={() => setViewMode('calendar')}
                     className={`p-1.5 rounded transition-all ${viewMode === 'calendar' ? 'bg-white dark:bg-slate-800 shadow-sm text-blue-600 dark:text-blue-400' : 'text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-200'}`}
                     title="Calendar View"
                  >
                     <LayoutGrid size={16} />
                  </button>
               </div>
               <Button
                  onClick={() => setIsRequestModalOpen(true)}
                  size="sm"
                  className="flex items-center gap-1.5"
               >
                  <Plus className="w-4 h-4" />
                  New
               </Button>
            </div>
         </div>

         <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
            {/* Main Content */}
            <div className="lg:col-span-3 space-y-4">
               {viewMode === 'list' ? (
                  <>
                     {/* Tabs - Compact */}
                     <div className="border-b border-gray-200 dark:border-slate-700">
                        <nav className="-mb-px flex space-x-6">
                           {[
                              { key: 'upcoming', label: 'Upcoming' },
                              { key: 'past', label: 'Past' },
                              { key: 'cancelled', label: 'Cancelled' },
                           ].map(tab => (
                              <button
                                 key={tab.key}
                                 onClick={() => setActiveTab(tab.key)}
                                 className={`
                        whitespace-nowrap py-2.5 border-b-2 text-sm font-medium transition-colors
                        ${activeTab === tab.key
                                       ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                       : 'border-transparent text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-200 hover:border-gray-300 dark:hover:border-slate-600'
                                    }
                      `}
                              >
                                 {tab.label}
                                 <span className="ml-1.5 bg-gray-100 dark:bg-slate-700 text-gray-600 dark:text-slate-300 py-0.5 px-1.5 rounded text-xs">
                                    {
                                       appointments.filter(a => {
                                          if (tab.key === 'upcoming') return !['COMPLETED', 'CANCELLED', 'REJECTED'].includes(a.statusRaw);
                                          if (tab.key === 'past') return a.statusRaw === 'COMPLETED';
                                          if (tab.key === 'cancelled') return ['CANCELLED', 'REJECTED'].includes(a.statusRaw);
                                          return false;
                                       }).length
                                    }
                                 </span>
                              </button>
                           ))}
                        </nav>
                     </div>

                     {/* Appointments List - Compact */}
                     <div className="space-y-2">
                        {filteredAppointments.length > 0 ? (
                           filteredAppointments.map(renderAppointmentCard)
                        ) : (
                           <div className="p-8 text-center text-gray-500 dark:text-slate-400 bg-white dark:bg-slate-800 rounded-md border border-dashed border-gray-200 dark:border-slate-700">
                              <Calendar className="w-8 h-8 mx-auto mb-2 text-gray-300 dark:text-slate-600" />
                              <p className="text-sm">No {activeTab} appointments found.</p>
                           </div>
                        )}
                     </div>
                  </>
               ) : (
                  /* Calendar View - Compact */
                  <Card className="p-4">
                     <div className="flex justify-between items-center mb-4">
                        <h3 className="text-base font-semibold text-gray-800 dark:text-slate-100">
                           {format(currentMonth, 'MMMM yyyy')}
                        </h3>
                        <div className="flex gap-1">
                           <button
                              onClick={() => setCurrentMonth(addDays(currentMonth, -30))}
                              className="w-7 h-7 rounded hover:bg-gray-100 dark:hover:bg-slate-700 flex items-center justify-center"
                           >
                              <ChevronLeft className="w-4 h-4" />
                           </button>
                           <button
                              onClick={() => setCurrentMonth(new Date())}
                              className="px-2 py-1 text-xs rounded hover:bg-gray-100 dark:hover:bg-slate-700"
                           >
                              Today
                           </button>
                           <button
                              onClick={() => setCurrentMonth(addDays(currentMonth, 30))}
                              className="w-7 h-7 rounded hover:bg-gray-100 dark:hover:bg-slate-700 flex items-center justify-center"
                           >
                              <ChevronRight className="w-4 h-4" />
                           </button>
                        </div>
                     </div>

                     {/* Calendar Grid - Compact */}
                     <div className="grid grid-cols-7 gap-1">
                        {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(day => (
                           <div key={day} className="text-center text-xs font-medium text-gray-500 dark:text-slate-400 py-1.5">
                              {day}
                           </div>
                        ))}
                        {calendarDays.map(day => {
                           const dayAppointments = getAppointmentsForDate(day);
                           const isToday = isSameDay(day, new Date());

                           return (
                              <div
                                 key={day.toString()}
                                 className={`
                        min-h-[60px] p-1.5 border rounded cursor-pointer hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors text-xs
                        ${isToday ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20' : 'border-gray-200 dark:border-slate-700'}
                      `}
                              >
                                 <div className={`text-xs font-medium ${isToday ? 'text-blue-600 dark:text-blue-400' : 'text-gray-700 dark:text-slate-200'}`}>
                                    {format(day, 'd')}
                                 </div>
                                 <div className="mt-0.5 space-y-0.5">
                                    {dayAppointments.slice(0, 2).map(appt => (
                                       <div
                                          key={appt.id}
                                          onClick={(e) => { e.stopPropagation(); openDetailsModal(appt); }}
                                          className={`text-xs px-1 py-0.5 rounded truncate ${appt.statusRaw === 'SCHEDULED'
                                             ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'
                                             : appt.statusRaw === 'PENDING_APPROVAL'
                                                ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
                                                : appt.statusRaw === 'COMPLETED'
                                                   ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400'
                                                   : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
                                             }`}
                                       >
                                          {appt.time}
                                       </div>
                                    ))}
                                    {dayAppointments.length > 2 && (
                                       <div className="text-xs text-gray-400 dark:text-slate-500">+{dayAppointments.length - 2}</div>
                                    )}
                                 </div>
                              </div>
                           );
                        })}
                     </div>

                     {/* Legend - Compact */}
                     <div className="mt-4 flex flex-wrap gap-3 text-xs">
                        <div className="flex items-center gap-1">
                           <div className="w-2.5 h-2.5 rounded-sm bg-green-500"></div>
                           <span>Confirmed</span>
                        </div>
                        <div className="flex items-center gap-1">
                           <div className="w-2.5 h-2.5 rounded-sm bg-yellow-500"></div>
                           <span>Pending</span>
                        </div>
                        <div className="flex items-center gap-1">
                           <div className="w-2.5 h-2.5 rounded-sm bg-blue-500"></div>
                           <span>Completed</span>
                        </div>
                        <div className="flex items-center gap-1">
                           <div className="w-2.5 h-2.5 rounded-sm bg-red-500"></div>
                           <span>Cancelled</span>
                        </div>
                     </div>
                  </Card>
               )}
            </div>

            {/* Reminders Sidebar - Compact */}
            <div className="lg:col-span-1">
               <Card className="p-3 sticky top-4">
                  <div className="flex items-center justify-between mb-3">
                     <h3 className="text-sm font-semibold text-gray-800 dark:text-slate-100 flex items-center">
                        <Bell className="w-4 h-4 mr-1.5 text-gray-400" />
                        Reminders
                        {upcomingReminders.length > 0 && (
                           <span className="ml-1.5 bg-red-500 text-white text-xs rounded-full w-4 h-4 flex items-center justify-center">
                              {upcomingReminders.length}
                           </span>
                        )}
                     </h3>
                  </div>

                  <div className="space-y-2">
                     {upcomingReminders.length > 0 ? (
                        upcomingReminders.map(appt => (
                           <div
                              key={appt.id}
                              className="p-2 bg-gray-50 dark:bg-slate-800/50 rounded"
                           >
                              <div className="flex justify-between items-start mb-1">
                                 <p className="text-xs font-medium text-gray-800 dark:text-slate-100">{appt.type}</p>
                                 <Badge size="sm" type="green">
                                    {getCountdown(appt)}
                                 </Badge>
                              </div>
                              <p className="text-xs text-gray-500 dark:text-slate-400">{appt.doctorName}</p>
                              <p className="text-xs text-gray-400 dark:text-slate-500">
                                 {format(parseISO(appt.date), 'MMM dd')} at {appt.time}
                              </p>
                              <button
                                 onClick={() => handleToggleCalendar(appt.id)}
                                 className={`mt-1.5 w-full text-xs py-1.5 px-2 rounded border transition-colors flex items-center justify-center gap-1.5 ${calendarAddedEvents.includes(appt.id)
                                    ? 'bg-blue-50 dark:bg-blue-900/40 border-blue-200 dark:border-blue-700 text-blue-700 dark:text-blue-300'
                                    : 'border-gray-200 dark:border-slate-700 hover:bg-gray-100 dark:hover:bg-slate-700 text-gray-700 dark:text-slate-300'
                                    }`}
                              >
                                 {calendarAddedEvents.includes(appt.id) ? (
                                    <>
                                       <Check className="w-3 h-3" />
                                       Remove from Calendar
                                    </>
                                 ) : (
                                    <>
                                       <Calendar className="w-3 h-3" />
                                       Add to Calendar
                                    </>
                                 )}
                              </button>
                           </div>
                        ))
                     ) : (
                        <p className="text-xs text-gray-500 dark:text-slate-400 text-center py-3">No upcoming appointments</p>
                     )}
                  </div>
               </Card>
            </div>
         </div>

         {/* Request Appointment Modal */}
         <Modal
            isOpen={isRequestModalOpen}
            onClose={() => {
               setIsRequestModalOpen(false);
               setRequestStep(1);
            }}
            title="Request New Appointment"
         >
            <form onSubmit={handleRequestSubmit} className="space-y-4">
               {/* Progress Indicator - Compact */}
               <div className="flex items-center justify-between mb-4">
                  {[1, 2, 3].map(step => (
                     <div key={step} className="flex items-center flex-1">
                        <div
                           className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${step <= requestStep
                              ? 'bg-blue-600 text-white'
                              : 'bg-gray-200 dark:bg-slate-700 text-gray-500 dark:text-slate-400'
                              }`}
                        >
                           {step < requestStep ? <Check className="w-3 h-3" /> : step}
                        </div>
                        {step < 3 && (
                           <div
                              className={`flex-1 h-0.5 mx-2 ${step < requestStep ? 'bg-blue-600' : 'bg-gray-200 dark:bg-slate-700'
                                 }`}
                           ></div>
                        )}
                     </div>
                  ))}
               </div>

               {/* Step 1: Select Department and Doctor - Compact */}
               {requestStep === 1 && (
                  <div className="space-y-3">
                     <h3 className="text-sm font-semibold text-gray-800 dark:text-slate-100">Select Department & Doctor</h3>

                     <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Department *</label>
                        <select
                           className="w-full px-3 py-1.5 text-sm border border-gray-300 dark:border-slate-600 rounded focus:ring-1 focus:ring-blue-500 outline-none bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                           value={requestForm.department}
                           onChange={e => setRequestForm({ ...requestForm, department: e.target.value, doctor: '' })}
                           required
                        >
                           <option value="">Choose a department</option>
                           {departments.map(dept => (
                              <option key={dept.id} value={dept.id}>
                                 {dept.name}
                              </option>
                           ))}
                        </select>
                     </div>

                     {requestForm.department && (
                        <div>
                           <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Doctor *</label>
                           <div className="space-y-2 max-h-48 overflow-y-auto">
                              {filteredDoctors.length > 0 ? filteredDoctors.map(doctor => (
                                 <div
                                    key={doctor.id}
                                    onClick={() => setRequestForm({ ...requestForm, doctor: doctor.id, doctorId: doctor.id })}
                                    className={`p-2 border rounded cursor-pointer transition-colors ${requestForm.doctor === doctor.id
                                       ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                       : 'border-gray-200 dark:border-slate-700 hover:border-gray-300 dark:hover:border-slate-600'
                                       }`}
                                 >
                                    <div className="flex items-center justify-between">
                                       <div className="flex items-center gap-2">
                                          <div className="w-7 h-7 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 flex items-center justify-center text-xs font-medium">
                                             {doctor.firstName ? doctor.firstName.charAt(0) : 'D'}
                                          </div>
                                          <div>
                                             <p className="text-sm font-medium text-gray-800 dark:text-slate-100">
                                                Dr. {doctor.firstName} {doctor.lastName}
                                             </p>
                                             <p className="text-xs text-gray-500 dark:text-slate-400">{doctor.specialty}</p>
                                             <p className="text-xs text-gray-400 dark:text-slate-500">{doctor.department}</p>
                                          </div>
                                       </div>
                                       <span className="text-xs text-green-600">Available</span>
                                    </div>
                                 </div>
                              )) : (
                                 <div className="p-3 text-center text-gray-500 dark:text-slate-400 text-sm">
                                    No doctors available for this department
                                 </div>
                              )}
                           </div>
                        </div>
                     )}
                  </div>
               )}

               {/* Step 2: Choose Date and Time - Compact */}
               {requestStep === 2 && (
                  <div className="space-y-3">
                     <h3 className="text-sm font-semibold text-gray-800 dark:text-slate-100">Choose Date & Time</h3>

                     <Input
                        type="date"
                        label="Preferred Date *"
                        value={requestForm.date}
                        onChange={e => setRequestForm({ ...requestForm, date: e.target.value, time: '' })}
                        min={format(new Date(), 'yyyy-MM-dd')}
                        required
                     />

                     {requestForm.date && (
                        <div>
                           <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Time Slots *</label>
                           {isLoadingSlots ? (
                              <p className="text-xs text-gray-500 dark:text-slate-400 py-2">Loading available times...</p>
                           ) : availableSlots.length > 0 ? (
                              <div className="grid grid-cols-4 gap-1 max-h-48 overflow-y-auto">
                                 {availableSlots.map(slot => {
                                    const label = String(slot).substring(0, 5);
                                    return (
                                       <button
                                          key={slot}
                                          type="button"
                                          onClick={() => setRequestForm({ ...requestForm, time: label })}
                                          className={`p-1.5 text-xs border rounded transition-colors ${requestForm.time === label
                                             ? 'border-blue-500 bg-blue-500 text-white'
                                             : 'border-gray-300 dark:border-slate-600 hover:border-blue-300 dark:hover:border-blue-700 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100'
                                             }`}
                                       >
                                          {label}
                                       </button>
                                    );
                                 })}
                              </div>
                           ) : (
                              <p className="text-xs text-gray-500 dark:text-slate-400 py-2">No open slots for this doctor on this date. Please choose another date.</p>
                           )}
                        </div>
                     )}
                  </div>
               )}

               {/* Step 3: Enter Details - Compact */}
               {requestStep === 3 && (
                  <div className="space-y-3">
                     <h3 className="text-sm font-semibold text-gray-800 dark:text-slate-100">Appointment Details</h3>

                     <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Type *</label>
                        <select
                           className="w-full px-3 py-1.5 text-sm border border-gray-300 dark:border-slate-600 rounded focus:ring-1 focus:ring-blue-500 outline-none bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                           value={requestForm.type}
                           onChange={e => setRequestForm({ ...requestForm, type: e.target.value })}
                           required
                        >
                           <option value="">Select type</option>
                           {appointmentTypes.map(type => (
                              <option key={type} value={type}>
                                 {type}
                              </option>
                           ))}
                        </select>
                     </div>

                     <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Reason for Visit *</label>
                        <textarea
                           className="w-full px-3 py-1.5 text-sm border border-gray-300 dark:border-slate-600 rounded focus:ring-1 focus:ring-blue-500 outline-none resize-none h-16 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                           value={requestForm.reason}
                           onChange={e => setRequestForm({ ...requestForm, reason: e.target.value })}
                           placeholder="Briefly describe your symptoms..."
                           required
                        />
                     </div>

                     <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Special Requirements</label>
                        <textarea
                           className="w-full px-3 py-1.5 text-sm border border-gray-300 dark:border-slate-600 rounded focus:ring-1 focus:ring-blue-500 outline-none resize-none h-12 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                           value={requestForm.specialRequirements}
                           onChange={e => setRequestForm({ ...requestForm, specialRequirements: e.target.value })}
                           placeholder="Wheelchair access, interpreter, etc."
                        />
                     </div>

                     <Alert
                        type="info"
                        message="We'll confirm your request within 24 hours."
                     />
                  </div>
               )}

               {/* Navigation Buttons - Compact */}
               <div className="flex justify-between pt-3 border-t dark:border-slate-700">
                  <Button
                     type="button"
                     variant="secondary"
                     size="sm"
                     onClick={() => {
                        if (requestStep > 1) {
                           setRequestStep(requestStep - 1);
                        } else {
                           setIsRequestModalOpen(false);
                           setRequestStep(1);
                        }
                     }}
                  >
                     {requestStep === 1 ? 'Cancel' : 'Back'}
                  </Button>
                  <Button type="submit" size="sm" disabled={
                     (requestStep === 1 && (!requestForm.department || !requestForm.doctor)) ||
                     (requestStep === 2 && (!requestForm.date || !requestForm.time)) ||
                     (requestStep === 3 && (!requestForm.type || !requestForm.reason))
                  }>
                     {requestStep === 3 ? 'Submit' : 'Next'}
                  </Button>
               </div>
            </form>
         </Modal>

         {/* Cancel Appointment Modal - Compact */}
         <Modal
            isOpen={isCancelModalOpen}
            onClose={() => {
               setIsCancelModalOpen(false);
               setAppointmentToCancel(null);
               setCancellationReason('');
            }}
            title="Cancel Appointment"
         >
            {appointmentToCancel && (
               <div className="space-y-3">
                  <Alert
                     type="warning"
                     message="Please cancel at least 24 hours in advance to avoid charges."
                  />

                  {/* Appointment Details - Compact */}
                  <Card className="p-3 bg-gray-50 dark:bg-slate-800/50">
                     <h4 className="text-xs font-medium text-gray-600 dark:text-slate-400 mb-1">Appointment Details</h4>
                     <div className="space-y-0.5 text-sm text-gray-700 dark:text-slate-300">
                        <p><span className="text-gray-500">Type:</span> {appointmentToCancel.type}</p>
                        <p><span className="text-gray-500">Doctor:</span> {appointmentToCancel.doctorName}</p>
                        <p><span className="text-gray-500">Date:</span> {format(parseISO(appointmentToCancel.date), 'MMM dd, yyyy')} at {appointmentToCancel.time}</p>
                     </div>
                  </Card>

                  {/* Cancellation Reason - Compact */}
                  <div>
                     <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Reason *</label>
                     <select
                        className="w-full px-3 py-1.5 text-sm border border-gray-300 dark:border-slate-600 rounded focus:ring-1 focus:ring-blue-500 outline-none bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                        value={cancellationReason}
                        onChange={e => setCancellationReason(e.target.value)}
                        required
                     >
                        <option value="">Select a reason</option>
                        {cancellationReasons.map(reason => (
                           <option key={reason} value={reason}>
                              {reason}
                           </option>
                        ))}
                     </select>
                  </div>

                  {/* Action Buttons - Compact */}
                  <div className="flex justify-end gap-2 pt-3 border-t dark:border-slate-700">
                     <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => {
                           setIsCancelModalOpen(false);
                           setAppointmentToCancel(null);
                           setCancellationReason('');
                        }}
                     >
                        Keep
                     </Button>
                     <Button
                        type="button"
                        size="sm"
                        onClick={handleCancelAppointment}
                        className="bg-red-600 hover:bg-red-700"
                     >
                        Confirm Cancel
                     </Button>
                  </div>
               </div>
            )}
         </Modal>

         {/* Details Modal */}
         <Modal
            isOpen={isDetailsModalOpen}
            onClose={() => {
               setIsDetailsModalOpen(false);
               setSelectedAppointment(null);
            }}
            title="Appointment Details"
         >
            {selectedAppointment && (
               <div className="space-y-4 text-sm text-gray-700 dark:text-slate-300">
                  <div className="flex justify-between items-center mb-2">
                     <h4 className="text-base font-semibold text-gray-800 dark:text-slate-100">{selectedAppointment.type}</h4>
                     <Badge
                        type={
                           selectedAppointment.statusRaw === 'SCHEDULED'
                              ? 'green'
                              : selectedAppointment.statusRaw === 'PENDING_APPROVAL'
                                 ? 'yellow'
                                 : selectedAppointment.statusRaw === 'COMPLETED'
                                    ? 'blue'
                                    : 'red'
                        }
                     >
                        {(selectedAppointment.statusRaw || '').replaceAll('_', ' ')}
                     </Badge>
                  </div>

                  <div className="grid grid-cols-2 gap-4 bg-gray-50 dark:bg-slate-800/50 p-3 rounded-lg">
                     <div>
                        <p className="text-xs text-gray-500 mb-1">Doctor</p>
                        <p className="font-medium flex items-center gap-1.5"><User className="w-3.5 h-3.5 text-gray-400" /> {selectedAppointment.doctorName}</p>
                     </div>
                     <div>
                        <p className="text-xs text-gray-500 mb-1">Date & Time</p>
                        <p className="font-medium flex items-center gap-1.5"><Clock className="w-3.5 h-3.5 text-gray-400" /> {format(parseISO(selectedAppointment.date), 'MMM dd, yyyy')} at {selectedAppointment.time}</p>
                     </div>
                  </div>

                  {selectedAppointment.reasonForVisit && (
                     <div>
                        <p className="text-xs text-gray-500 mb-1">Reason for Visit</p>
                        <p className="bg-white dark:bg-slate-800 p-2.5 rounded border border-gray-200 dark:border-slate-700">{selectedAppointment.reasonForVisit}</p>
                     </div>
                  )}

                  {selectedAppointment.specialRequirements && (
                     <div>
                        <p className="text-xs text-gray-500 mb-1">Special Requirements</p>
                        <p className="bg-white dark:bg-slate-800 p-2.5 rounded border border-gray-200 dark:border-slate-700">{selectedAppointment.specialRequirements}</p>
                     </div>
                  )}

                  {selectedAppointment.doctorNotes && (
                     <div>
                        <p className="text-xs text-gray-500 mb-1">Doctor's Notes</p>
                        <p className="bg-white dark:bg-slate-800 p-2.5 rounded border border-gray-200 dark:border-slate-700">{selectedAppointment.doctorNotes}</p>
                     </div>
                  )}

                  <div className="pt-4 border-t dark:border-slate-700 flex justify-end">
                     <Button variant="secondary" onClick={() => setIsDetailsModalOpen(false)}>Close</Button>
                  </div>
               </div>
            )}
         </Modal>

         {/* Reschedule Modal */}
         <Modal
            isOpen={isRescheduleModalOpen}
            onClose={() => {
               setIsRescheduleModalOpen(false);
               setAppointmentToReschedule(null);
            }}
            title="Reschedule Appointment"
         >
            {appointmentToReschedule && (
               <form onSubmit={handleRescheduleSubmit} className="space-y-4">
                  <Alert type="info" message={`Rescheduling ${appointmentToReschedule.type} with ${appointmentToReschedule.doctorName}.`} />

                  <Input
                     type="date"
                     label="New Preferred Date *"
                     value={rescheduleData.date}
                     onChange={e => setRescheduleData({ ...rescheduleData, date: e.target.value, time: '' })}
                     min={format(new Date(), 'yyyy-MM-dd')}
                     required
                  />

                  {rescheduleData.date && (
                     <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Select New Time *</label>
                        {isLoadingRescheduleSlots ? (
                           <p className="text-xs text-gray-500 dark:text-slate-400 py-2">Loading available times...</p>
                        ) : rescheduleSlots.length > 0 ? (
                           <div className="grid grid-cols-4 gap-1 max-h-48 overflow-y-auto">
                              {rescheduleSlots.map(slot => {
                                 const label = String(slot).substring(0, 5);
                                 return (
                                    <button
                                       key={slot}
                                       type="button"
                                       onClick={() => setRescheduleData({ ...rescheduleData, time: label })}
                                       className={`p-1.5 text-xs border rounded transition-colors ${rescheduleData.time === label
                                          ? 'border-blue-500 bg-blue-500 text-white'
                                          : 'border-gray-300 dark:border-slate-600 hover:border-blue-300 dark:hover:border-blue-700 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100'
                                          }`}
                                    >
                                       {label}
                                    </button>
                                 );
                              })}
                           </div>
                        ) : (
                           <p className="text-xs text-gray-500 dark:text-slate-400 py-2">No open slots for this doctor on this date. Please choose another date.</p>
                        )}
                     </div>
                  )}

                  <div className="flex justify-end gap-2 pt-3 border-t dark:border-slate-700">
                     <Button type="button" variant="secondary" onClick={() => setIsRescheduleModalOpen(false)}>Cancel</Button>
                     <Button type="submit" disabled={!rescheduleData.date || !rescheduleData.time}>Submit Request</Button>
                  </div>
               </form>
            )}
         </Modal>
      </div>
   );
};

export default PatientAppointments;
