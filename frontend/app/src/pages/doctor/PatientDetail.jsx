import React, { useState, useCallback, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    ArrowLeft, Clock, Activity, Pill, Plus
} from 'lucide-react';

import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';

import TreatmentModal from '../../components/doctor/TreatmentModal';
import PrescriptionModal from '../../components/doctor/PrescriptionModal';
import VitalSignModal from '../../components/doctor/VitalSignModal';
import MedicalRecordModal from '../../components/doctor/MedicalRecordModal';
import MedicalHistoryList from '../../components/doctor/MedicalHistoryList';
import LabResultsList from '../../components/doctor/LabResultsList';

import api from '../../services/api';

const PatientDetail = () => {
    const { id } = useParams();
    const navigate = useNavigate();

    // State
    const [patient, setPatient] = useState(null);
    const [activeTab, setActiveTab] = useState('overview');

    // Data States
    const [prescriptions, setPrescriptions] = useState([]);
    const [treatments, setTreatments] = useState([]);
    const [medicalHistory, setMedicalHistory] = useState([]);
    const [labs, setLabs] = useState([]);
    const [latestVitals, setLatestVitals] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState(null);
    const [isRxModalOpen, setIsRxModalOpen] = useState(false);
    const [isTreatmentModalOpen, setIsTreatmentModalOpen] = useState(false);
    const [isVitalsModalOpen, setIsVitalsModalOpen] = useState(false);
    const [isRecordModalOpen, setIsRecordModalOpen] = useState(false);

    // Fetch Data — no mock fallback: a doctor must never see fabricated
    // clinical data presented as if it were real (see FULL_STACK_INTEGRATION_AUDIT.md DR-13).
    const fetchData = useCallback(async () => {
        if (!id) return;
        setIsLoading(true);
        setLoadError(null);
        try {
            const [patientData, rxData, historyData, labData, vitalsData] = await Promise.all([
                api.patients.getById(id),
                api.prescriptions.getByPatient(id).catch(() => []),
                api.medicalRecords.getByPatient(id).catch(() => []),
                api.labResults.getByPatient(id).catch(() => []),
                api.vitalSigns.getLatest(id).catch(() => null),
            ]);
            setPatient(patientData);
            setPrescriptions(Array.isArray(rxData) ? rxData : []);
            setMedicalHistory(Array.isArray(historyData) ? historyData : []);
            setLabs(Array.isArray(labData) ? labData : []);
            setLatestVitals(vitalsData || null);
        } catch (error) {
            console.error('Error loading patient details:', error);
            setLoadError('Unable to load this patient\'s chart right now. Please try again.');
        } finally {
            setIsLoading(false);
        }
    }, [id]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    if (isLoading) return <div className="p-6 dark:text-slate-100">Loading patient details...</div>;
    if (loadError) {
        return (
            <div className="p-6">
                <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 text-sm rounded p-3 flex items-center justify-between">
                    <span>{loadError}</span>
                    <Button variant="outline" className="text-xs" onClick={fetchData}>Retry</Button>
                </div>
            </div>
        );
    }
    if (!patient) return <div className="p-6 dark:text-slate-100">Patient not found</div>;

    const patientFullName = `${patient.firstName || ''} ${patient.lastName || ''}`.trim() || 'Unknown';
    const patientAge = patient.dateOfBirth
        ? Math.floor((Date.now() - new Date(patient.dateOfBirth)) / (365.25 * 24 * 60 * 60 * 1000))
        : 'N/A';

    const handleAddTreatment = (treatment) => {
        // No backend Treatment entity exists yet (see FULL_STACK_INTEGRATION_AUDIT.md DR-5) —
        // this list is intentionally local-only/session-only until that feature is built.
        const newTreatment = {
            id: Date.now(),
            ...treatment,
            active: true
        };
        setTreatments([newTreatment, ...treatments]);
    };

    const activePrescriptions = prescriptions.filter(rx => rx.status === 'ACTIVE');
    const historyPrescriptions = prescriptions.filter(rx => rx.status && rx.status !== 'ACTIVE');

    return (
        <div className="space-y-3">
            {/* Header */}
            <div className="flex items-center space-x-3">
                <Button variant="outline" onClick={() => navigate('/dashboard/doctor')} className="p-1.5">
                    <ArrowLeft size={16} />
                </Button>
                <div>
                    <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">{patientFullName}</h2>
                    <div className="flex items-center space-x-2 text-xs text-gray-500 dark:text-slate-400">
                        <span>ID: {patient.id}</span>
                        <span>•</span>
                        <span>{patientAge} yrs, {patient.gender}</span>
                    </div>
                </div>
                <div className="ml-auto">
                    <Badge type="green">Active</Badge>
                </div>
            </div>

            {/* Tabs */}
            <div className="border-b border-gray-200 dark:border-slate-700">
                <nav className="-mb-px flex space-x-4">
                    {['overview', 'history', 'labs', 'prescriptions', 'treatments'].map((tab) => (
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
                            {tab}
                        </button>
                    ))}
                </nav>
            </div>

            {/* Content */}
            <div className="min-h-[300px]">
                {activeTab === 'overview' && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <Card className="p-3 dark:bg-slate-800">
                            <div className="flex justify-between items-center mb-2">
                                <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 flex items-center">
                                    <Activity className="w-4 h-4 mr-1.5 text-blue-500" />
                                    Vitals & Condition
                                </h3>
                                <Button variant="outline" className="text-xs py-0.5" onClick={() => setIsVitalsModalOpen(true)}>
                                    <Plus className="w-3.5 h-3.5 mr-1" /> Record
                                </Button>
                            </div>
                            <div className="space-y-2">
                                <div className="flex justify-between border-b dark:border-slate-700 pb-1 text-sm">
                                    <span className="text-gray-600 dark:text-slate-400">Condition</span>
                                    <span className="font-medium dark:text-slate-100">{patient.medicalHistory || 'N/A'}</span>
                                </div>
                                {latestVitals ? (
                                    <>
                                        <div className="flex justify-between border-b dark:border-slate-700 pb-1 text-sm">
                                            <span className="text-gray-600 dark:text-slate-400">Blood Pressure</span>
                                            <span className="font-medium dark:text-slate-100">{latestVitals.bloodPressure || 'N/A'}</span>
                                        </div>
                                        <div className="flex justify-between border-b dark:border-slate-700 pb-1 text-sm">
                                            <span className="text-gray-600 dark:text-slate-400">Heart Rate</span>
                                            <span className="font-medium dark:text-slate-100">{latestVitals.heartRate ? `${latestVitals.heartRate} bpm` : 'N/A'}</span>
                                        </div>
                                        <div className="flex justify-between text-sm">
                                            <span className="text-gray-600 dark:text-slate-400">Weight</span>
                                            <span className="font-medium dark:text-slate-100">{latestVitals.weight ? `${latestVitals.weight} kg` : 'N/A'}</span>
                                        </div>
                                        {latestVitals.recordedAt && (
                                            <p className="text-xs text-gray-400 dark:text-slate-500 pt-1">
                                                Recorded {new Date(latestVitals.recordedAt).toLocaleString()}
                                            </p>
                                        )}
                                    </>
                                ) : (
                                    <p className="text-sm text-gray-500 dark:text-slate-400 py-2">No vitals recorded yet.</p>
                                )}
                            </div>
                        </Card>

                        <Card className="p-3 dark:bg-slate-800">
                            <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
                                <Clock className="w-4 h-4 mr-1.5 text-blue-500" />
                                Recent Activity
                            </h3>
                            {medicalHistory.length > 0 ? (
                                <ul className="space-y-1.5">
                                    {medicalHistory.slice(0, 3).map((item, idx) => (
                                        <li key={item.recordId ?? idx} className="text-xs">
                                            <span className="font-bold text-gray-700 dark:text-slate-300">
                                                {item.recordDate ? new Date(item.recordDate).toLocaleDateString() : ''}:
                                            </span>{' '}
                                            <span className="dark:text-slate-400">
                                                {item.diagnosis}{item.symptoms ? ` — ${item.symptoms}` : ''}
                                            </span>
                                        </li>
                                    ))}
                                </ul>
                            ) : (
                                <p className="text-xs text-gray-500 dark:text-slate-400">No recent activity.</p>
                            )}
                        </Card>
                    </div>
                )}

                {activeTab === 'prescriptions' && (
                    <div className="space-y-4">
                        {/* Active Prescriptions */}
                        <div className="space-y-2">
                            <div className="flex justify-between items-center bg-blue-50 dark:bg-blue-900/20 p-2.5 rounded border border-blue-100 dark:border-blue-800">
                                <div>
                                    <h3 className="font-bold text-sm text-blue-900 dark:text-blue-100">Active Prescriptions</h3>
                                    <p className="text-xs text-blue-700 dark:text-blue-300">Currently being taken by patient</p>
                                </div>
                                <Button onClick={() => setIsRxModalOpen(true)} className="flex items-center text-xs shadow-none">
                                    <Plus className="w-3.5 h-3.5 mr-1" /> Add New
                                </Button>
                            </div>

                            {activePrescriptions.length > 0 ? (
                                activePrescriptions.map(rx => (
                                    <Card key={rx.prescriptionId} className="p-3 flex justify-between items-start group hover:border-blue-300 dark:hover:border-blue-600 transition-colors dark:bg-slate-800">
                                        <div className="flex items-start">
                                            <div className="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 mr-2">
                                                <Pill size={16} />
                                            </div>
                                            <div>
                                                <div className="flex items-center gap-1.5">
                                                    <h4 className="font-bold text-gray-800 dark:text-slate-100 text-sm">{rx.medicationName}</h4>
                                                    <Badge type="green">Active</Badge>
                                                </div>
                                                <div className="text-xs text-gray-600 dark:text-slate-400 font-medium">{rx.dosage} • {rx.frequency}</div>
                                                {rx.duration && <div className="text-xs text-gray-500 dark:text-slate-400">Duration: {rx.duration}</div>}
                                                {rx.specialInstructions && <div className="text-xs text-gray-500 dark:text-slate-400 italic">"{rx.specialInstructions}"</div>}
                                                <div className="text-xs text-gray-400 dark:text-slate-500 mt-1">Prescribed by {rx.doctorName}</div>
                                            </div>
                                        </div>
                                    </Card>
                                ))
                            ) : (
                                <div className="text-center py-4 text-gray-400 dark:text-slate-500 border border-dashed dark:border-slate-600 rounded text-sm">
                                    <Pill className="w-6 h-6 mx-auto mb-1 opacity-50" />
                                    No active prescriptions.
                                </div>
                            )}
                        </div>

                        {/* Prescription History */}
                        <div className="space-y-2">
                            <div className="flex justify-between items-center px-2">
                                <h3 className="font-bold text-sm text-gray-700 dark:text-slate-300">Prescription History</h3>
                            </div>

                            {historyPrescriptions.length > 0 ? (
                                <div className="bg-gray-50 dark:bg-slate-800 rounded overflow-hidden border border-gray-200 dark:border-slate-700">
                                    {historyPrescriptions.map((rx, idx) => (
                                        <div key={rx.prescriptionId} className={`p-2.5 flex justify-between items-center ${idx !== historyPrescriptions.length - 1 ? 'border-b border-gray-200 dark:border-slate-700' : ''}`}>
                                            <div className="opacity-70">
                                                <h4 className="font-bold text-sm text-gray-700 dark:text-slate-300">{rx.medicationName}</h4>
                                                <p className="text-xs text-gray-500 dark:text-slate-400">{rx.dosage} • {rx.frequency}</p>
                                            </div>
                                            <Badge type="gray">{rx.status}</Badge>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <div className="text-center py-3 text-gray-400 dark:text-slate-500 text-xs">
                                    No prescription history.
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {activeTab === 'treatments' && (
                    <div className="space-y-2">
                        <div className="flex justify-between items-center bg-gray-50 dark:bg-slate-800 p-2.5 rounded">
                            <div>
                                <h3 className="font-bold text-sm text-gray-700 dark:text-slate-300">Active Treatments</h3>
                                <p className="text-xs text-gray-500 dark:text-slate-400">Not yet backed by a database — entries are lost on refresh.</p>
                            </div>
                            <Button onClick={() => setIsTreatmentModalOpen(true)} className="flex items-center text-xs">
                                <Plus className="w-3.5 h-3.5 mr-1" /> Add Treatment
                            </Button>
                        </div>
                        {treatments.length > 0 ? treatments.map(item => (
                            <Card key={item.id} className="p-3 flex flex-col md:flex-row justify-between items-start md:items-center dark:bg-slate-800">
                                <div>
                                    <h4 className="font-bold text-sm text-gray-800 dark:text-slate-100">{item.name}</h4>
                                    <p className="text-xs text-gray-600 dark:text-slate-400">{item.notes}</p>
                                    <Badge type="blue" className="mt-1">{item.frequency}</Badge>
                                </div>
                            </Card>
                        )) : (
                            <div className="text-center py-3 text-gray-400 dark:text-slate-500 text-xs">No treatments recorded this session.</div>
                        )}
                    </div>
                )}

                {activeTab === 'history' && (
                    <div className="space-y-2">
                        <div className="flex justify-end">
                            <Button onClick={() => setIsRecordModalOpen(true)} className="flex items-center text-xs">
                                <Plus className="w-3.5 h-3.5 mr-1" /> Add Record
                            </Button>
                        </div>
                        <MedicalHistoryList history={medicalHistory} />
                    </div>
                )}
                {activeTab === 'labs' && (
                    <LabResultsList
                        labs={labs}
                        patientId={Number(id)}
                        onAdd={(newLab) => setLabs(prev => [...prev, newLab])}
                    />
                )}
            </div>

            <PrescriptionModal
                isOpen={isRxModalOpen}
                onClose={() => setIsRxModalOpen(false)}
                patientId={Number(id)}
                onAdd={() => fetchData()}
            />

            <VitalSignModal
                isOpen={isVitalsModalOpen}
                onClose={() => setIsVitalsModalOpen(false)}
                patientId={Number(id)}
                onAdd={() => fetchData()}
            />

            <MedicalRecordModal
                isOpen={isRecordModalOpen}
                onClose={() => setIsRecordModalOpen(false)}
                patientId={Number(id)}
                onAdd={() => fetchData()}
            />

            <TreatmentModal
                isOpen={isTreatmentModalOpen}
                onClose={() => setIsTreatmentModalOpen(false)}
                onAdd={handleAddTreatment}
            />
        </div>
    );
};

export default PatientDetail;
