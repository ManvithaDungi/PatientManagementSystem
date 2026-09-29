import React, { useState, useEffect } from 'react';
import { Calendar, Clock, AlertCircle, Loader } from 'lucide-react';
import Card from '../common/Card';
import Button from '../common/Button';
import Badge from '../common/Badge';
import api from '../../services/api';

const AvailableSlotSelector = ({ doctorId, onSlotSelect, selectedDate }) => {
    const [availableSlots, setAvailableSlots] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    const [selectedSlot, setSelectedSlot] = useState(null);

    useEffect(() => {
        if (doctorId && selectedDate) {
            fetchAvailableSlots();
        }
    }, [doctorId, selectedDate]);

    const fetchAvailableSlots = async () => {
        setIsLoading(true);
        setError(null);
        try {
            const date = selectedDate instanceof Date 
                ? selectedDate.toISOString().split('T')[0]
                : selectedDate;

            const slots = await api.appointments.getAvailableSlots(doctorId, date);
            
            if (Array.isArray(slots) && slots.length > 0) {
                setAvailableSlots(slots);
            } else {
                setAvailableSlots([]);
                setError('No available slots for this date');
            }
        } catch (err) {
            console.error('Failed to fetch available slots:', err);
            setAvailableSlots([]);
            setError('Unable to load available slots right now. Please try again.');
        } finally {
            setIsLoading(false);
        }
    };

    const formatTime = (timeString) => {
        if (!timeString) return '';
        try {
            const [hours, minutes] = timeString.split(':');
            const date = new Date();
            date.setHours(parseInt(hours), parseInt(minutes));
            return date.toLocaleTimeString('en-US', { 
                hour: '2-digit', 
                minute: '2-digit',
                hour12: true 
            });
        } catch {
            return timeString;
        }
    };

    const formatDate = (date) => {
        if (date instanceof Date) {
            return date.toLocaleDateString('en-US', { 
                weekday: 'long',
                month: 'long',
                day: 'numeric',
                year: 'numeric'
            });
        }
        return date;
    };

    const handleSlotSelect = (slot) => {
        setSelectedSlot(slot);
        if (onSlotSelect) {
            onSlotSelect(slot);
        }
    };

    if (isLoading) {
        return (
            <Card className="p-6 dark:bg-slate-800">
                <div className="flex items-center justify-center gap-3">
                    <Loader className="w-5 h-5 animate-spin text-blue-500" />
                    <p className="text-gray-600 dark:text-slate-400">Loading available slots...</p>
                </div>
            </Card>
        );
    }

    return (
        <Card className="dark:bg-slate-800">
            <div className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 p-4 border-b border-blue-200 dark:border-blue-800 rounded-t-lg">
                <div className="flex items-center gap-3">
                    <Calendar className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                    <div>
                        <h3 className="font-bold text-gray-800 dark:text-slate-100">Available Time Slots</h3>
                        <p className="text-xs text-gray-600 dark:text-slate-400">
                            {formatDate(selectedDate)}
                        </p>
                    </div>
                </div>
            </div>

            <div className="p-4">
                {error && (
                    <div className="mb-4 p-3 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded text-sm text-yellow-700 dark:text-yellow-400 flex items-start gap-2">
                        <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                        {error}
                    </div>
                )}

                {availableSlots.length === 0 && !error ? (
                    <div className="text-center py-8">
                        <Clock className="w-12 h-12 text-gray-300 dark:text-slate-600 mx-auto mb-3" />
                        <p className="text-gray-500 dark:text-slate-400">No available slots for this date</p>
                        <p className="text-xs text-gray-400 dark:text-slate-500 mt-1">Please select a different date</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2">
                        {availableSlots.map((slot) => (
                            <button
                                key={slot}
                                onClick={() => handleSlotSelect(slot)}
                                className={`
                                    p-2 text-sm font-medium rounded-lg border-2 transition-all
                                    ${selectedSlot === slot
                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                        : 'border-gray-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-gray-700 dark:text-slate-300 hover:border-blue-300 dark:hover:border-blue-600'
                                    }
                                `}
                            >
                                <div className="flex items-center justify-center gap-1">
                                    <Clock size={14} />
                                    <span>{formatTime(slot)}</span>
                                </div>
                            </button>
                        ))}
                    </div>
                )}

                {selectedSlot && (
                    <div className="mt-4 p-3 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded">
                        <p className="text-sm text-green-700 dark:text-green-400">
                            <span className="font-medium">Selected Time:</span> {formatTime(selectedSlot)} on {formatDate(selectedDate)}
                        </p>
                    </div>
                )}
            </div>
        </Card>
    );
};

export default AvailableSlotSelector;
