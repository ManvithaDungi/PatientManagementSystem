import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
   Activity,
   AlertTriangle,
   Calendar,
   CheckSquare,
   ClipboardList,
   Clock,
   MessageSquare,
   Pill,
   Users,
   X,
} from 'lucide-react';

import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';


const shiftTypeStyles = {
   day: {
      label: 'Day Shift',
      classes: 'bg-brand-light text-brand-medium border border-brand-medium/20',
   },
   night: {
      label: 'Night Shift',
      classes: 'bg-brand-medium/10 text-brand-deep border border-brand-deep/30',
   },
};

const taskPriorityMap = {
   critical: { dot: 'bg-red-500', label: 'Critical' },
   high: { dot: 'bg-orange-500', label: 'High' },
   medium: { dot: 'bg-blue-500', label: 'Medium' },
   low: { dot: 'bg-gray-400', label: 'Low' },
};

const taskCategoryMap = {
   medication: 'Medication',
   assessment: 'Assessment',
   care: 'Care',
   documentation: 'Documentation',
   vitals: 'Vitals',
};

const toastColors = {
   success: 'bg-green-600 text-white',
   error: 'bg-red-600 text-white',
   info: 'bg-brand-medium text-white',
};

const NurseDashboard = () => {
   const { user } = useAuth();
   const navigate = useNavigate();

   const [stats, setStats] = useState({
      assignedPatients: 0,
      pendingVitals: 0,
      overdueVitals: 0,
      medicationsDue: 0,
      overdueMedications: 0,
      nextMedicationIn: 0,
      pendingTasks: 0,
      highPriorityTasks: 0,
   });
   const [tasks, setTasks] = useState([]);
   const [handoverNotes, setHandoverNotes] = useState({ fromPreviousShift: [], forNextShift: [] });
   const [shift] = useState({
      date: new Date().toISOString().split('T')[0],
      startTime: '07:00',
      endTime: '19:00',
   });
   const [currentTime, setCurrentTime] = useState(new Date());
   const [isLoading, setIsLoading] = useState(true);
   const [loadError, setLoadError] = useState(null);
   const [toast, setToast] = useState(null);

   const nurseName = user?.fullName || user?.full_name || 'Nurse';
   const nurseUnit = user?.department || 'Unit not set';

   useEffect(() => {
      const interval = setInterval(() => setCurrentTime(new Date()), 60_000);
      return () => clearInterval(interval);
   }, []);

   const fetchDashboard = async () => {
      setLoadError(null);
      try {
         const [statsData, tasksData, handoverData] = await Promise.all([
            api.nurse.getDashboardOverview(),
            api.nurse.getTasks(),
            api.nurse.getHandoverNotes(),
         ]);
         setStats(statsData || {});
         setTasks(Array.isArray(tasksData) ? tasksData : []);
         setHandoverNotes({
            fromPreviousShift: handoverData?.fromPreviousShift || [],
            forNextShift: handoverData?.forNextShift || [],
         });
      } catch (err) {
         console.error('Failed to load nurse dashboard', err);
         setLoadError('Unable to load your dashboard right now. Please try again.');
      } finally {
         setIsLoading(false);
      }
   };

   useEffect(() => {
      fetchDashboard();
      const refreshInterval = setInterval(fetchDashboard, 120_000);
      return () => clearInterval(refreshInterval);
      // eslint-disable-next-line react-hooks/exhaustive-deps
   }, []);

   useEffect(() => {
      if (!toast) return undefined;
      const timer = setTimeout(() => setToast(null), 4000);
      return () => clearTimeout(timer);
   }, [toast]);

   const quickStats = useMemo(() => [
      {
         id: 'assigned',
         label: 'patients assigned today',
         value: stats.assignedPatients ?? 0,
         icon: Users,
         border: 'border-brand-medium',
         badge: null,
         onClick: () => navigate('/dashboard/nurse/patients'),
      },
      {
         id: 'vitals',
         label: 'vitals checks due',
         value: stats.pendingVitals ?? 0,
         icon: Activity,
         border: (stats.overdueVitals ?? 0) > 0 ? 'border-orange-500' : 'border-brand-medium',
         badge: (stats.overdueVitals ?? 0) > 0
            ? { text: `${stats.overdueVitals} overdue`, classes: 'text-red-600 font-medium' }
            : null,
         onClick: () => navigate('/dashboard/nurse/vitals'),
      },
      {
         id: 'medications',
         label: 'medications pending',
         value: stats.medicationsDue ?? 0,
         icon: Pill,
         border: (stats.overdueMedications ?? 0) > 0
            ? 'border-red-500'
            : (stats.nextMedicationIn ?? 0) >= 0 && stats.nextMedicationIn <= 30
               ? 'border-yellow-400'
               : 'border-brand-medium',
         badge: (stats.overdueMedications ?? 0) > 0
            ? { text: `${stats.overdueMedications} overdue`, classes: 'text-red-600 font-medium' }
            : stats.nextMedicationIn >= 0
               ? { text: `Next in ${stats.nextMedicationIn} min`, classes: 'text-amber-600 font-medium' }
               : null,
         onClick: () => navigate('/dashboard/nurse/patients'),
      },
      {
         id: 'tasks',
         label: 'tasks remaining',
         value: stats.pendingTasks ?? 0,
         icon: CheckSquare,
         border: 'border-green-500',
         badge: { text: `${stats.highPriorityTasks ?? 0} high priority`, classes: 'text-orange-500 font-medium' },
         onClick: () => navigate('/dashboard/nurse/tasks'),
      },
   ], [stats, navigate]);

   const greeting = useMemo(() => {
      const hour = currentTime.getHours();
      if (hour < 12) return 'Good Morning';
      if (hour < 18) return 'Good Afternoon';
      return 'Good Evening';
   }, [currentTime]);

   const formattedDate = useMemo(() =>
      currentTime.toLocaleString(undefined, {
         weekday: 'long',
         month: 'long',
         day: 'numeric',
         year: 'numeric',
         hour: 'numeric',
         minute: '2-digit',
      }),
      [currentTime]);

   const shiftProgress = useMemo(() => {
      const start = new Date(`${shift.date}T${shift.startTime}:00`);
      const end = new Date(`${shift.date}T${shift.endTime}:00`);

      if (end < start) {
         end.setDate(end.getDate() + 1);
      }

      const elapsed = Math.max(0, Math.min(currentTime.getTime() - start.getTime(), end.getTime() - start.getTime()));
      const total = end.getTime() - start.getTime();
      const remainingMs = Math.max(0, end.getTime() - currentTime.getTime());

      const percentage = total === 0 ? 0 : Math.min(100, Math.round((elapsed / total) * 100));
      const remainingHours = Math.floor(remainingMs / (1000 * 60 * 60));
      const remainingMinutes = Math.floor((remainingMs / (1000 * 60)) % 60);

      return {
         percentage,
         remainingLabel: `${remainingHours} hours, ${remainingMinutes.toString().padStart(2, '0')} minutes remaining`,
      };
   }, [currentTime, shift.date, shift.endTime, shift.startTime]);

   const visibleTasks = tasks
      .filter((task) => !task.completed)
      .sort((a, b) => {
         const priorityOrder = ['critical', 'high', 'medium', 'low'];
         return priorityOrder.indexOf(a.priority) - priorityOrder.indexOf(b.priority);
      });
   const overdueTaskCount = tasks.filter((task) => task.status === 'overdue').length;

   const handleTaskToggle = async (taskId) => {
      // Optimistic update, reverted on failure
      setTasks((prev) => prev.map((task) =>
         task.id === taskId ? { ...task, completed: !task.completed } : task,
      ));
      try {
         const result = await api.nurse.toggleTaskStatus(taskId);
         if (result?.task) {
            setTasks((prev) => prev.map((task) => (task.id === taskId ? result.task : task)));
         }
      } catch (err) {
         console.error('Failed to toggle task status', err);
         setTasks((prev) => prev.map((task) =>
            task.id === taskId ? { ...task, completed: !task.completed } : task,
         ));
         setToast({ type: 'error', message: 'Failed to update task. Please try again.' });
      }
   };

   const shiftStyles = shiftTypeStyles.day;

   return (
      <div className="space-y-4" aria-label="Nurse dashboard overview">
         <header className="bg-white dark:bg-slate-800 rounded-lg shadow-soft border border-gray-100 dark:border-slate-700 overflow-hidden">
            <div className="p-4 sm:p-5 flex flex-col lg:flex-row gap-3 lg:gap-4 lg:items-center justify-between">
               <div>
                  <p className="text-xs font-medium text-brand-medium uppercase tracking-wide">{nurseUnit}</p>
                  <h1 className="text-xl font-bold text-gray-900 dark:text-slate-100 mt-1">
                     {`${greeting}, Nurse ${nurseName}`}
                  </h1>
                  <p className="text-gray-500 dark:text-slate-400 mt-1.5 flex items-center gap-1.5 text-sm" aria-live="polite">
                     <Clock className="w-3.5 h-3.5 text-brand-medium" aria-hidden="true" />
                     <span>{formattedDate}</span>
                  </p>
               </div>

               <div className="flex flex-col md:flex-row gap-2 w-full lg:w-auto">
                  <Card className="p-3 border border-gray-100 dark:border-slate-700 shadow-soft flex-1 dark:bg-slate-800">
                     <div className="flex items-center justify-between">
                        <div>
                           <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${shiftStyles.classes}`}>
                              {shiftStyles.label}
                           </span>
                           <p className="text-sm font-semibold text-gray-900 dark:text-slate-100 mt-1.5">{shift.startTime} - {shift.endTime}</p>
                        </div>
                        <Calendar className="w-8 h-8 text-brand-medium bg-brand-light rounded-lg p-1.5" aria-hidden="true" />
                     </div>
                     <div className="mt-3 space-y-1" aria-hidden="true">
                        <div className="relative h-2 bg-gray-100 dark:bg-slate-700 rounded-full overflow-hidden">
                           <div
                              className="absolute inset-y-0 left-0 bg-gradient-to-r from-brand-medium to-brand-deep rounded-full transition-all duration-700"
                              style={{ width: `${shiftProgress.percentage}%` }}
                           />
                        </div>
                        <p className="text-sm text-gray-500 dark:text-slate-400">{shiftProgress.percentage}% of shift completed</p>
                        <p className="text-sm font-medium text-gray-700 dark:text-slate-300" aria-live="polite">{shiftProgress.remainingLabel}</p>
                     </div>
                  </Card>

                  <button
                     type="button"
                     className="min-w-[160px] md:min-w-[180px] rounded-lg border-2 border-gray-200 dark:border-slate-600 bg-gray-50 dark:bg-slate-700 text-gray-400 dark:text-slate-500 font-semibold shadow-soft cursor-not-allowed flex items-center justify-center gap-1.5 px-3 py-2.5 text-sm"
                     aria-label="Call code team (not yet implemented)"
                     title="Emergency escalation is not yet wired to a paging/notification system"
                     disabled
                  >
                     <AlertTriangle className="w-4 h-4" aria-hidden="true" />
                     Call Code Team
                  </button>
               </div>
            </div>
         </header>

         {toast && (
            <div
               className={`fixed top-6 right-6 z-50 shadow-xl rounded-xl px-5 py-4 flex items-start gap-3 ${toastColors[toast.type] || toastColors.info}`}
               role="status"
               aria-live="assertive"
            >
               <div className="flex-1">
                  <p className="font-semibold text-white text-sm">{toast.type === 'error' ? 'Action required' : toast.type === 'success' ? 'Success' : 'Notification'}</p>
                  <p className="text-white/90 text-sm mt-1">{toast.message}</p>
               </div>
               <button
                  type="button"
                  className="text-white/80 hover:text-white"
                  aria-label="Dismiss notification"
                  onClick={() => setToast(null)}
               >
                  <X className="w-4 h-4" />
               </button>
            </div>
         )}

         {loadError && (
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 text-sm rounded-lg p-3 flex items-center justify-between">
               <span>{loadError}</span>
               <Button variant="outline" className="text-xs" onClick={fetchDashboard}>Retry</Button>
            </div>
         )}

         <section aria-labelledby="quick-stats" className="space-y-2">
            <div className="flex items-center justify-between">
               <h2 id="quick-stats" className="text-sm font-bold text-gray-900 dark:text-slate-100">Shift Snapshot</h2>
               <Button variant="link" className="text-brand-medium text-xs font-semibold" onClick={fetchDashboard} aria-label="Refresh dashboard snapshot">
                  Refresh Data
               </Button>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
               {quickStats.map((stat) => {
                  const Icon = stat.icon;
                  return (
                     <Card
                        key={stat.id}
                        className={`p-3 border-l-4 ${stat.border} shadow-soft hover:shadow-lg transition-shadow cursor-pointer focus-within:ring-2 focus-within:ring-brand-medium focus-within:ring-offset-2 dark:bg-slate-800`}
                        hover
                        role="button"
                        tabIndex={0}
                        onClick={stat.onClick}
                        onKeyDown={(event) => {
                           if (event.key === 'Enter' || event.key === ' ') {
                              event.preventDefault();
                              stat.onClick();
                           }
                        }}
                        aria-label={`${stat.value} ${stat.label}`}
                     >
                        <div className="flex items-start justify-between">
                           <div>
                              <p className="text-xs text-gray-500 dark:text-slate-400 font-medium">{isLoading ? '…' : stat.label}</p>
                              <p className="text-xl font-bold text-gray-900 dark:text-slate-100 mt-1">{isLoading ? '—' : stat.value}</p>
                              {!isLoading && stat.badge && <p className={`text-xs mt-1.5 ${stat.badge.classes}`}>{stat.badge.text}</p>}
                           </div>
                           <div className="w-8 h-8 bg-brand-light rounded-lg flex items-center justify-center text-brand-medium">
                              <Icon className="w-4 h-4" aria-hidden="true" />
                           </div>
                        </div>
                     </Card>
                  );
               })}
            </div>
         </section>

         <section aria-labelledby="tasks-reminders" className="space-y-3">
            <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-2">
               <div className="flex items-center gap-2">
                  <ClipboardList className="w-5 h-5 text-brand-medium" aria-hidden="true" />
                  <h2 id="tasks-reminders" className="text-sm font-bold text-gray-900 dark:text-slate-100">Tasks &amp; Reminders</h2>
                  <span className="text-xs text-gray-500 dark:text-slate-400">{stats.pendingTasks ?? 0} active</span>
               </div>
               <div className="flex items-center gap-2">
                  <div className="flex gap-1.5 text-xs text-gray-500 dark:text-slate-400">
                     <span className="flex items-center gap-0.5"><span className="w-1.5 h-1.5 rounded-full bg-red-500" /> Critical</span>
                     <span className="flex items-center gap-0.5"><span className="w-1.5 h-1.5 rounded-full bg-orange-500" /> High</span>
                     <span className="flex items-center gap-0.5"><span className="w-1.5 h-1.5 rounded-full bg-blue-500" /> Medium</span>
                     <span className="flex items-center gap-0.5"><span className="w-1.5 h-1.5 rounded-full bg-gray-400" /> Low</span>
                  </div>
               </div>
            </div>

            {overdueTaskCount > 0 && (
               <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 px-3 py-2 rounded-lg flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4" aria-hidden="true" />
                  <p className="text-xs font-semibold">{overdueTaskCount} overdue task(s) need immediate attention</p>
               </div>
            )}

            <div className="space-y-2">
               {isLoading ? (
                  <p className="text-xs text-gray-500 dark:text-slate-400 py-3">Loading tasks...</p>
               ) : visibleTasks.length > 0 ? (
                  visibleTasks.slice(0, 6).map((task) => {
                     const priority = taskPriorityMap[task.priority] || taskPriorityMap.medium;
                     const categoryLabel = taskCategoryMap[task.category] || 'Task';
                     const isOverdue = task.status === 'overdue';

                     return (
                        <Card key={task.id} className={`p-3 shadow-soft border ${isOverdue ? 'border-red-200 dark:border-red-800 bg-red-50/40 dark:bg-red-900/10' : 'border-gray-100 dark:border-slate-700'} dark:bg-slate-800`}>
                           <div className="flex flex-col md:flex-row md:items-center gap-2 md:gap-3">
                              <label className="flex items-center gap-2 cursor-pointer select-none">
                                 <input
                                    type="checkbox"
                                    className="form-checkbox w-4 h-4 rounded border-gray-300 dark:border-slate-600 text-brand-medium focus:ring-brand-medium dark:bg-slate-700"
                                    checked={task.completed}
                                    onChange={() => handleTaskToggle(task.id)}
                                    aria-label={`Mark task ${task.title} as complete`}
                                 />
                                 <span className={`w-2 h-2 rounded-full ${priority.dot}`} aria-hidden="true" />
                              </label>

                              <div className="flex-1 space-y-1">
                                 <div className="flex flex-wrap items-center gap-1.5">
                                    <h3 className="text-sm font-semibold text-gray-900 dark:text-slate-100">{task.title}</h3>
                                    <Badge type={isOverdue ? 'red' : 'blue'}>{categoryLabel}</Badge>
                                    {isOverdue && <span className="text-xs font-semibold uppercase text-red-600 dark:text-red-400 bg-red-100 dark:bg-red-900/30 px-1.5 py-0.5 rounded-full">Overdue</span>}
                                 </div>
                                 {task.dueTime && (
                                    <p className="text-xs font-semibold text-gray-500 dark:text-slate-400">
                                       Due: {new Date(task.dueTime).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}
                                    </p>
                                 )}
                              </div>
                           </div>
                        </Card>
                     );
                  })
               ) : (
                  <p className="text-xs text-gray-500 dark:text-slate-400 py-3 text-center">No pending tasks.</p>
               )}

               <Button variant="outline" className="w-full text-xs" onClick={() => navigate('/dashboard/nurse/tasks')}>
                  View All Tasks
               </Button>
            </div>
         </section>

         <section aria-labelledby="shift-handover" className="space-y-3">
            <div className="flex items-center justify-between gap-2">
               <div className="flex items-center gap-2">
                  <MessageSquare className="w-5 h-5 text-brand-medium" aria-hidden="true" />
                  <h2 id="shift-handover" className="text-sm font-bold text-gray-900 dark:text-slate-100">Shift Handover</h2>
               </div>
               <Button variant="link" className="text-brand-medium text-xs font-semibold" onClick={() => navigate('/dashboard/nurse/shift-notes')}>
                  View / Add Notes
               </Button>
            </div>

            <Card className="p-3 space-y-2 border border-gray-100 dark:border-slate-700 shadow-soft dark:bg-slate-800">
               {isLoading ? (
                  <p className="text-xs text-gray-500 dark:text-slate-400 py-2">Loading handover notes...</p>
               ) : handoverNotes.fromPreviousShift.length === 0 ? (
                  <div className="text-center text-gray-500 dark:text-slate-400 py-4">
                     <CheckSquare className="w-6 h-6 mx-auto text-gray-300 dark:text-slate-600 mb-1.5" aria-hidden="true" />
                     <p className="text-xs">No handover notes from the previous shift.</p>
                  </div>
               ) : (
                  handoverNotes.fromPreviousShift.slice(0, 3).map((note) => (
                     <div key={note.id} className="rounded-lg border border-blue-100 dark:border-blue-800 bg-blue-50/40 dark:bg-blue-900/20 p-3">
                        <div className="flex items-center justify-between text-xs text-gray-500 dark:text-slate-400 mb-1">
                           <span>{note.author?.email || 'Nurse'}</span>
                           <span>{note.timestamp ? new Date(note.timestamp).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : ''}</span>
                        </div>
                        <p className="text-xs text-gray-700 dark:text-slate-300 leading-relaxed">{note.content}</p>
                     </div>
                  ))
               )}
            </Card>
         </section>
      </div>
   );
};

export default NurseDashboard;
