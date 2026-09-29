import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Pill, Check, Clock, AlertCircle, ArrowLeft } from 'lucide-react';

import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';
import Modal from '../../components/common/Modal';
import api from '../../services/api';

const MedicationAdministration = () => {
    const { id } = useParams();
    const navigate = useNavigate();

    const [medications, setMedications] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    const [selectedMed, setSelectedMed] = useState(null);
    const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState(null);

    // For each active prescription, pull its most recent administration record
    // (if any) so "due" vs "administered" reflects real data, not a fabricated status.
    const loadMedications = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const prescriptions = await api.prescriptions.getByPatient(id);
            const active = Array.isArray(prescriptions) ? prescriptions.filter(p => p.status === 'ACTIVE') : [];

            const withHistory = await Promise.all(active.map(async (p) => {
                let lastAdministration = null;
                try {
                    const history = await api.nurse.getMedicationAdministrationHistory(p.prescriptionId);
                    lastAdministration = Array.isArray(history) && history.length > 0 ? history[0] : null;
                } catch (err) {
                    console.error(`Failed to load administration history for prescription ${p.prescriptionId}:`, err);
                }
                return {
                    prescriptionId: p.prescriptionId,
                    name: p.medicationName,
                    dosage: p.dosage,
                    route: p.route || 'Oral',
                    scheduledTime: p.frequency || 'PRN',
                    lastAdministration,
                };
            }));

            setMedications(withHistory);
        } catch (err) {
            console.error('Failed to load medications:', err);
            setError('Could not load medication data from the server.');
            setMedications([]);
        } finally {
            setIsLoading(false);
        }
    }, [id]);

    useEffect(() => {
        loadMedications();
    }, [loadMedications]);

    const handleAdministerClick = (med) => {
        setSelectedMed(med);
        setSubmitError(null);
        setIsConfirmModalOpen(true);
    };

    const confirmAdministration = async () => {
        if (!selectedMed) return;

        setIsSubmitting(true);
        setSubmitError(null);
        try {
            const record = await api.nurse.recordMedicationAdministration(selectedMed.prescriptionId);
            setMedications(prev => prev.map(m =>
                m.prescriptionId === selectedMed.prescriptionId
                    ? { ...m, lastAdministration: record }
                    : m
            ));
            setIsConfirmModalOpen(false);
            setSelectedMed(null);
        } catch (err) {
            console.error('Failed to record medication administration:', err);
            setSubmitError(err.message || 'Failed to record administration. Please try again.');
        } finally {
            setIsSubmitting(false);
        }
    };

    const isAdministeredToday = (med) => {
        if (!med.lastAdministration?.administeredAt) return false;
        const administeredDate = new Date(med.lastAdministration.administeredAt);
        const now = new Date();
        return administeredDate.toDateString() === now.toDateString();
    };

    const getStatusContent = (med) => {
        if (isAdministeredToday(med)) {
            const time = new Date(med.lastAdministration.administeredAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            return (
                <div className="flex flex-col items-end text-green-600">
                    <span className="flex items-center text-sm font-medium"><Check className="w-4 h-4 mr-1" /> Administered</span>
                    <span className="text-xs text-gray-500">at {time}</span>
                </div>
            );
        }
        return (
            <Button size="sm" variant="primary" onClick={() => handleAdministerClick(med)}>
                Administer
            </Button>
        );
    };

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            <Button variant="ghost" onClick={() => navigate(`/dashboard/nurse/patient/${id}`)} className="pl-0 hover:pl-2 transition-all">
                <ArrowLeft className="w-4 h-4 mr-2" /> Back to Patient
            </Button>

            <div>
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Medication Administration</h1>
                <p className="text-gray-500">Verify 5 Rights: Patient, Drug, Dose, Route, Time</p>
            </div>

            {error && (
                <div className="bg-red-50 dark:bg-red-900/10 p-4 rounded-md border border-red-100 dark:border-red-900/20 flex gap-3">
                    <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0" />
                    <div className="text-sm text-red-800 dark:text-red-200 flex-1">{error}</div>
                    <Button variant="outline" size="sm" onClick={loadMedications}>Retry</Button>
                </div>
            )}

            <div className="space-y-4">
                {isLoading ? (
                    <div className="p-4 text-center text-gray-500">Loading medications...</div>
                ) : medications.length > 0 ? (
                    medications.map((med) => (
                    <Card key={med.prescriptionId} className="p-4 flex flex-col sm:flex-row justify-between items-center gap-4">
                        <div className="flex items-start gap-4 w-full">
                            <div className={`p-3 rounded-full ${isAdministeredToday(med) ? 'bg-green-100 text-green-600' : 'bg-blue-100 text-blue-600'}`}>
                                <Pill className="w-6 h-6" />
                            </div>
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900 dark:text-white">{med.name}</h3>
                                <div className="text-sm text-gray-500 dark:text-slate-400 space-x-4">
                                    <span><span className="font-medium">Dose:</span> {med.dosage}</span>
                                    <span><span className="font-medium">Route:</span> {med.route}</span>
                                </div>
                                <div className="mt-2 flex gap-2">
                                    <Badge type="gray" variant="soft"><Clock className="w-3 h-3 mr-1 inline" /> {med.scheduledTime}</Badge>
                                </div>
                            </div>
                        </div>
                        <div className="flex-shrink-0">
                            {getStatusContent(med)}
                        </div>
                    </Card>
                ))) : !error && (
                    <div className="p-6 text-center text-gray-600 dark:text-gray-400 bg-gray-50 dark:bg-slate-800 rounded-lg border border-gray-200 dark:border-slate-700">
                        No active medications found for this patient.
                    </div>
                )}
            </div>

            <Modal
                isOpen={isConfirmModalOpen}
                onClose={() => { setIsConfirmModalOpen(false); setSubmitError(null); }}
                title="Confirm Medication Administration"
            >
                {selectedMed && (
                    <div className="space-y-4">
                        <div className="bg-yellow-50 dark:bg-yellow-900/10 p-4 rounded-md border border-yellow-100 dark:border-yellow-900/20 flex gap-3">
                            <AlertCircle className="w-5 h-5 text-yellow-600" />
                            <div className="text-sm text-yellow-800 dark:text-yellow-200">
                                Verify patient identity and allergies before proceeding.
                            </div>
                        </div>

                        <div className="space-y-2 py-4">
                            <div className="grid grid-cols-2 text-sm gap-2">
                                <span className="text-gray-500">Medication:</span>
                                <span className="font-semibold">{selectedMed.name}</span>

                                <span className="text-gray-500">Dosage:</span>
                                <span className="font-semibold">{selectedMed.dosage}</span>

                                <span className="text-gray-500">Route:</span>
                                <span className="font-semibold">{selectedMed.route}</span>
                            </div>
                        </div>

                        {submitError && (
                            <p className="text-sm text-red-600 dark:text-red-400">{submitError}</p>
                        )}

                        <div className="flex justify-end gap-3">
                            <Button variant="ghost" onClick={() => { setIsConfirmModalOpen(false); setSubmitError(null); }} disabled={isSubmitting}>Cancel</Button>
                            <Button variant="primary" onClick={confirmAdministration} disabled={isSubmitting}>
                                {isSubmitting ? 'Recording...' : 'Confirm & Sign'}
                            </Button>
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
};

export default MedicationAdministration;
