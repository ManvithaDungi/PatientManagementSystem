import React, { useState } from 'react';
import Modal from '../common/Modal';
import Input from '../common/Input';
import Button from '../common/Button';
import api from '../../services/api';

const PrescriptionModal = ({ isOpen, onClose, patientId, onAdd }) => {
    const [prescription, setPrescription] = useState({
        medicationName: '',
        dosage: '',
        frequency: '',
        duration: '',
        route: 'Oral',
        instructions: ''
    });
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState(null);

    const frequencies = [
        'Once daily',
        'Twice daily',
        'Three times daily',
        'Every 12 hours',
        'Every 6 hours',
        'As needed',
        'Weekly',
        'Monthly'
    ];

    const routes = ['Oral', 'IV', 'IM', 'Subcutaneous', 'Topical', 'Inhaled'];

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);
        setIsSubmitting(true);

        try {
            const payload = {
                patientId: patientId,
                medicationName: prescription.medicationName,
                dosage: prescription.dosage,
                frequency: prescription.frequency,
                duration: prescription.duration,
                route: prescription.route,
                specialInstructions: prescription.instructions,
            };

            const created = await api.prescriptions.create(payload);
            onAdd(created);
            resetForm();
            onClose();
        } catch (err) {
            console.error('Failed to add prescription:', err);
            setError(err.message || 'Failed to save prescription. Please try again.');
        } finally {
            setIsSubmitting(false);
        }
    };

    const resetForm = () => {
        setPrescription({
            medicationName: '',
            dosage: '',
            frequency: '',
            duration: '',
            route: 'Oral',
            instructions: ''
        });
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={() => {
                onClose();
                resetForm();
                setError(null);
            }}
            title="Write New Prescription"
        >
            <form onSubmit={handleSubmit} className="space-y-4">
                {error && (
                    <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded text-sm text-red-700 dark:text-red-400">
                        {error}
                    </div>
                )}

                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">
                        Medication Name
                    </label>
                    <Input
                        value={prescription.medicationName}
                        onChange={e => setPrescription({ ...prescription, medicationName: e.target.value })}
                        placeholder="e.g. Lisinopril"
                        required
                    />
                </div>

                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">
                            Dosage
                        </label>
                        <Input
                            value={prescription.dosage}
                            onChange={e => setPrescription({ ...prescription, dosage: e.target.value })}
                            placeholder="e.g. 10mg, 500mg"
                            required
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">
                            Frequency
                        </label>
                        <select
                            value={prescription.frequency}
                            onChange={e => setPrescription({ ...prescription, frequency: e.target.value })}
                            className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                            required
                        >
                            <option value="">Select frequency</option>
                            {frequencies.map(freq => (
                                <option key={freq} value={freq}>{freq}</option>
                            ))}
                        </select>
                    </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">
                            Duration
                        </label>
                        <Input
                            value={prescription.duration}
                            onChange={e => setPrescription({ ...prescription, duration: e.target.value })}
                            placeholder="e.g. 30 days, 3 months"
                            required
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">
                            Route
                        </label>
                        <select
                            value={prescription.route}
                            onChange={e => setPrescription({ ...prescription, route: e.target.value })}
                            className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                            required
                        >
                            {routes.map(r => (
                                <option key={r} value={r}>{r}</option>
                            ))}
                        </select>
                    </div>
                </div>

                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">
                        Instructions / Special Notes
                    </label>
                    <textarea
                        className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all resize-none h-20 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100 placeholder-gray-400 dark:placeholder-slate-500"
                        value={prescription.instructions}
                        onChange={e => setPrescription({ ...prescription, instructions: e.target.value })}
                        placeholder="Take with food, avoid alcohol, monitor side effects, etc."
                    />
                </div>

                <div className="pt-4 flex justify-end space-x-3 border-t border-gray-100 dark:border-slate-700">
                    <Button type="button" variant="secondary" onClick={() => {
                        onClose();
                        resetForm();
                        setError(null);
                    }} disabled={isSubmitting}>
                        Cancel
                    </Button>
                    <Button type="submit" disabled={isSubmitting}>
                        {isSubmitting ? 'Saving...' : 'Write Prescription'}
                    </Button>
                </div>
            </form>
        </Modal>
    );
};

export default PrescriptionModal;
