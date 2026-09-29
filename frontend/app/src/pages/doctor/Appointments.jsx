import React, { useState, useEffect } from 'react';
import { Calendar as CalendarIcon, Clock, User, AlertCircle, LayoutGrid, List, Columns } from 'lucide-react';
import AppointmentCalendar from '../../components/AppointmentCalendar';
import SchedulerView from '../../components/SchedulerView';
import AppointmentSidePanel from '../../components/AppointmentSidePanel';
import Card from '../../components/common/Card';
import Badge from '../../components/common/Badge';
import Button from '../../components/common/Button';
import Modal from '../../components/common/Modal';
import api from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';

const Appointments = () => {
    const { user } = useAuth();
    const [activeTab, setActiveTab] = useState('upcoming');
    const [viewMode, setViewMode] = useState('calendar'); // 'list', 'calendar' (month), 'day' (scheduler)
    const [appointments, setAppointments] = useState([]);
    const [selectedDate, setSelectedDate] = useState(new Date()); // Shared date state
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    // Side Panel State
    const [selectedAppointment, setSelectedAppointment] = useState(null);
    const [isSidePanelOpen, setIsSidePanelOpen] = useState(false);

    // Cancel Modal State
    const [cancelModalOpen, setCancelModalOpen] = useState(false);
    const [apptToCancel, setApptToCancel] = useState(null);

    // Fetch Appointments
    useEffect(() => {
        const fetchAppointments = async () => {
            const doctorId = user?.userId;
            if (!doctorId) return;

            setIsLoading(true);
            setError(null);
            try {
                const data = await api.appointments.getByDoctor(doctorId);
                const transformed = (data || []).map(a => ({
                    id: a.appointmentId,
                    date: a.appointmentDate ? a.appointmentDate.split('T')[0] : '',
                    time: a.appointmentDate
                        ? new Date(a.appointmentDate).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
                        : '',
                    patientName: a.patientName || 'Unknown',
                    type: a.reasonForVisit || 'Consultation',
                    status: a.status || 'PENDING_APPROVAL',
                    duration: 30,
                }));
                setAppointments(transformed);
            } catch (err) {
                console.error('Failed to fetch appointments:', err);
                setError('Failed to load appointments. Please refresh the page.');
            } finally {
                setIsLoading(false);
            }
        };
        fetchAppointments();
    }, [user?.userId]);

    const filteredAppointments = appointments.filter(appt => {
        const status = (appt.status || 'PENDING').toUpperCase();
        if (activeTab === 'upcoming') return !['CANCELLED', 'COMPLETED'].includes(status);
        if (activeTab === 'history') return ['COMPLETED', 'CANCELLED'].includes(status);
        return true;
    });

    const handleAppointmentClick = (appt) => {
        setSelectedAppointment(appt);
        setIsSidePanelOpen(true);
    };

    const handleSidePanelAction = (action) => {
        if (action === 'cancel') {
            setApptToCancel(selectedAppointment);
            setCancelModalOpen(true);
            setIsSidePanelOpen(false);
        } else {
            alert(`${action} triggered for ${selectedAppointment.patientName}`);
        }
    };

    const handleCancelClick = (appt) => {
        setApptToCancel(appt);
        setCancelModalOpen(true);
    };

    const confirmCancel = async () => {
        if (!apptToCancel) return; // Safety check

        try {
            await api.appointments.cancel(apptToCancel.id);
            setAppointments(appointments.map(a =>
                a.id === apptToCancel.id ? { ...a, status: 'CANCELLED' } : a
            ));
        } catch (err) {
            console.error('Failed to cancel appointment:', err);
            setError('Failed to cancel appointment. Please try again.');
        } finally {
            setCancelModalOpen(false);
            setApptToCancel(null);
            setSelectedAppointment(null);
        }
    };

    const handleComplete = async (id) => {
        try {
            await api.appointments.complete(id);
            setAppointments(appointments.map(a =>
                a.id === id ? { ...a, status: 'COMPLETED' } : a
            ));
        } catch (err) {
            console.error('Failed to complete appointment:', err);
            setError('Failed to check in patient. Please try again.');
        }
    };

    return (
        <div className="space-y-3 relative">
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
            
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-2">
                <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">Appointments</h2>
                <div className="flex items-center space-x-3">
                    <div className="flex bg-gray-100 dark:bg-slate-700 p-1 rounded-lg">
                        <button
                            onClick={() => setViewMode('list')}
                            className={`p-2 rounded-md transition-all ${viewMode === 'list' ? 'bg-white dark:bg-slate-600 shadow-sm text-blue-600 dark:text-blue-400' : 'text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-300'}`}
                            title="List View"
                        >
                            <List size={20} />
                        </button>
                        <button
                            onClick={() => setViewMode('calendar')}
                            className={`p-2 rounded-md transition-all ${viewMode === 'calendar' ? 'bg-white dark:bg-slate-600 shadow-sm text-blue-600 dark:text-blue-400' : 'text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-300'}`}
                            title="Month View"
                        >
                            <LayoutGrid size={20} />
                        </button>
                        <button
                            onClick={() => setViewMode('day')}
                            className={`p-2 rounded-md transition-all ${viewMode === 'day' ? 'bg-white dark:bg-slate-600 shadow-sm text-blue-600 dark:text-blue-400' : 'text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-300'}`}
                            title="Day/Schedule View"
                        >
                            <Columns size={20} className="rotate-90" />
                        </button>
                    </div>
                </div>
            </div>

            {viewMode === 'day' ? (
                <SchedulerView
                    appointments={appointments}
                    onAppointmentClick={handleAppointmentClick}
                    selectedDate={selectedDate}
                    onDateChange={setSelectedDate}
                />
            ) : viewMode === 'calendar' ? (
                <AppointmentCalendar appointments={appointments} />
            ) : (
                <>
                    {/* List View Content */}
                    <div className="border-b border-gray-200 dark:border-slate-700">
                        <nav className="-mb-px flex space-x-4">
                            {['upcoming', 'history'].map((tab) => (
                                <button
                                    key={tab}
                                    onClick={() => setActiveTab(tab)}
                                    className={`
                        whitespace-nowrap py-2 px-1 border-b-2 font-medium text-xs capitalize
                        ${activeTab === tab
                                            ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                            : 'border-transparent text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-300 hover:border-gray-300 dark:hover:border-slate-600'}
                    `}
                                >
                                    {tab} Appointments
                                </button>
                            ))}
                        </nav>
                    </div>

                    <div className="space-y-2">
                        {filteredAppointments.length > 0 ? (
                            filteredAppointments.map(appt => (
                                <Card key={appt.id} className="p-3 flex flex-col md:flex-row justify-between items-start md:items-center dark:bg-slate-800">
                                    <div className="flex items-start space-x-3">
                                        <div className="bg-blue-50 dark:bg-blue-900/20 p-2 rounded text-blue-600 dark:text-blue-400">
                                            <CalendarIcon size={18} />
                                        </div>
                                        <div>
                                            <h4 className="font-bold text-sm text-gray-800 dark:text-slate-100">{appt.type} - {appt.patientName}</h4>
                                            <div className="flex items-center text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                                                <Clock size={12} className="mr-1" />
                                                {appt.date} at {appt.time}
                                            </div>
                                            <div className="flex items-center text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                                                <User size={12} className="mr-1" />
                                                {appt.type}
                                            </div>
                                        </div>
                                    </div>

                                    <div className="mt-2 md:mt-0 flex items-center space-x-2">
                                        <Badge type={
                                            appt.status === 'SCHEDULED' ? 'green' :
                                                appt.status === 'PENDING_APPROVAL' ? 'yellow' :
                                                    appt.status === 'CANCELLED' ? 'red' : 'gray'
                                        }>
                                            {appt.status}
                                        </Badge>

                                        {activeTab === 'upcoming' && (
                                            <div className="flex space-x-1">
                                                <Button variant="outline" className="text-xs py-0.5 px-2" onClick={() => handleComplete(appt.id)}>
                                                    Check In
                                                </Button>
                                                <Button variant="danger" className="text-xs py-0.5 px-2 bg-white dark:bg-slate-700 text-red-600 dark:text-red-400 border-red-200 dark:border-red-800 hover:bg-red-50 dark:hover:bg-red-900/20" onClick={() => handleCancelClick(appt)}>
                                                    Cancel
                                                </Button>
                                            </div>
                                        )}
                                    </div>
                                </Card>
                            ))
                        ) : (
                            <div className="p-12 text-center text-gray-500 dark:text-slate-400 bg-white dark:bg-slate-800 rounded-lg border border-dashed border-gray-300 dark:border-slate-600">
                                <CalendarIcon className="w-12 h-12 mx-auto text-gray-300 dark:text-slate-600 mb-2" />
                                <p>No appointments found in this view.</p>
                            </div>
                        )}
                    </div>
                </>
            )}

            {/* Side Panel */}
            <AppointmentSidePanel
                appointment={selectedAppointment}
                onClose={() => setIsSidePanelOpen(false)}
                isOpen={isSidePanelOpen}
                onAction={handleSidePanelAction}
            />
            {/* Overlay for Side Panel */}
            {isSidePanelOpen && (
                <div
                    className="fixed inset-0 bg-black/20 z-40"
                    onClick={() => setIsSidePanelOpen(false)}
                ></div>
            )}

            <Modal
                isOpen={cancelModalOpen && apptToCancel !== null}
                onClose={() => {
                    setCancelModalOpen(false);
                    setApptToCancel(null);
                }}
                title="Cancel Appointment"
            >
                {apptToCancel && (
                    <div className="space-y-4">
                        <div className="bg-yellow-50 dark:bg-yellow-900/20 p-4 rounded-lg flex items-start">
                            <AlertCircle className="w-5 h-5 text-yellow-600 dark:text-yellow-400 mr-2 mt-0.5" />
                            <p className="text-sm text-yellow-800 dark:text-yellow-200">
                                Are you sure you want to cancel the appointment for <strong>{apptToCancel.patientName}</strong>?
                                This action cannot be undone.
                            </p>
                        </div>
                        <div className="flex justify-end space-x-3 pt-2">
                            <Button variant="secondary" onClick={() => {
                                setCancelModalOpen(false);
                                setApptToCancel(null);
                            }}>Keep Appointment</Button>
                            <Button variant="danger" onClick={confirmCancel}>Confirm Cancellation</Button>
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
};

export default Appointments;
