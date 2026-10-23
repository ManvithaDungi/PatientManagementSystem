import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    Activity,
    Heart,
    Thermometer,
    Droplets,
    Wind,
    Clock,
    AlertCircle,
    FileText,
    ArrowLeft
} from 'lucide-react';

import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import IconButton from '../../components/common/IconButton';
import Badge from '../../components/common/Badge';
import api from '../../services/api';

const calculateAge = (dateOfBirth) => {
    if (!dateOfBirth) return 'N/A';
    const today = new Date();
    const birth = new Date(dateOfBirth);
    let age = today.getFullYear() - birth.getFullYear();
    const monthDiff = today.getMonth() - birth.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) age--;
    return age;
};

const PatientDetail = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [isLoading, setIsLoading] = useState(true);
    const [patientDetails, setPatientDetails] = useState(null);

    useEffect(() => {
        const fetchPatient = async () => {
            try {
                setIsLoading(true);
                const [patients, vitals] = await Promise.allSettled([
                    api.nurse.getAssignedPatients(),
                    api.vitalSigns.getLatest(id),
                ]);

                const patientList = patients.status === 'fulfilled' && Array.isArray(patients.value)
                    ? patients.value
                    : [];
                const found = patientList.find(p => String(p.profileId) === id);

                const latestVitals = vitals.status === 'fulfilled' && vitals.value
                    ? vitals.value
                    : null;

                if (found) {
                    setPatientDetails({
                        id: String(found.profileId),
                        name: `${found.firstName} ${found.lastName}`,
                        age: calculateAge(found.dateOfBirth),
                        gender: found.gender || 'N/A',
                        bloodGroup: 'N/A',
                        room: 'N/A',
                        bed: 'N/A',
                        status: 'unknown',
                        allergies: [],
                        medicalHistory: found.medicalHistory || '',
                        careInstructions: found.medicalHistory ? [found.medicalHistory] : ['No care instructions on file.'],
                        admissionDate: found.createdAt || new Date().toISOString(),
                        doctor: found.assignedDoctor ? (found.assignedDoctor.email || 'N/A') : 'N/A',
                        lastVitals: latestVitals
                            ? new Date(latestVitals.recordedAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
                            : 'Not recorded',
                        latestVitals: latestVitals
                            ? {
                                bp: latestVitals.bloodPressure || 'N/A',
                                hr: latestVitals.heartRate ?? 'N/A',
                                temp: latestVitals.temperature ?? 'N/A',
                                spo2: latestVitals.oxygenSaturation ?? 'N/A',
                                rr: latestVitals.respiratoryRate ?? 'N/A',
                            }
                            : { bp: 'N/A', hr: 'N/A', temp: 'N/A', spo2: 'N/A', rr: 'N/A' },
                        medications: [],
                    });
                }
            } catch (err) {
                console.error('Failed to fetch patient details:', err);
            } finally {
                setIsLoading(false);
            }
        };
        fetchPatient();
    }, [id]);

    const getMedicationStatusBadge = (status) => {
        switch (status) {
            case 'due': return <Badge type="yellow">Due Soon</Badge>;
            case 'done': return <Badge type="green">Administered</Badge>;
            case 'overdue': return <Badge type="red">Overdue</Badge>;
            default: return <Badge type="gray">PRN</Badge>;
        }
    };

    if (isLoading) {
        return (
            <div className="p-8 text-center text-gray-500">Loading patient details...</div>
        );
    }

    if (!patientDetails) {
        return (
            <div className="p-8 text-center">
                <p className="text-gray-500">Patient not found or not assigned to you.</p>
                <Button onClick={() => navigate('/dashboard/nurse/patients')} className="mt-4">Back to Patients</Button>
            </div>
        );
    }

    return (
        <div className="space-y-6 max-w-7xl mx-auto">
            <IconButton 
               icon={ArrowLeft} 
               label="Back to Patients" 
               variant="ghost"
               onClick={() => navigate('/dashboard/nurse/patients')}
               className="pl-0 hover:pl-2 transition-all"
            />

            {/* Header / Basic Info */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <Card className="md:col-span-2 p-6 flex flex-col justify-between">
                    <div>
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h1 className="text-3xl font-bold text-gray-900 dark:text-white">{patientDetails.name}</h1>
                                <p className="text-gray-500 text-lg">Room/bed: not recorded</p>
                            </div>
                            <div className="text-right">
                                <Badge type={patientDetails.status === 'critical' ? 'red' : patientDetails.status === 'monitor' ? 'yellow' : patientDetails.status === 'stable' ? 'green' : 'gray'} size="lg">
                                    {patientDetails.status.toUpperCase()}
                                </Badge>
                                <p className="text-xs text-gray-400 mt-1">ID: #{patientDetails.id}</p>
                            </div>
                        </div>

                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-6">
                            <div>
                                <p className="text-xs text-gray-500 uppercase tracking-wider">Age / Gender</p>
                                <p className="font-medium text-gray-900 dark:text-white">{patientDetails.age} / {patientDetails.gender}</p>
                            </div>
                            <div>
                                <p className="text-xs text-gray-500 uppercase tracking-wider">Blood Group</p>
                                <p className="font-medium text-gray-900 dark:text-white">{patientDetails.bloodGroup}</p>
                            </div>
                            <div>
                                <p className="text-xs text-gray-500 uppercase tracking-wider">Admission</p>
                                <p className="font-medium text-gray-900 dark:text-white">{new Date(patientDetails.admissionDate).toLocaleDateString()}</p>
                            </div>
                            <div>
                                <p className="text-xs text-gray-500 uppercase tracking-wider">Doctor</p>
                                <p className="font-medium text-gray-900 dark:text-white">{patientDetails.doctor}</p>
                            </div>
                        </div>

                        <div className="mt-6 p-4 bg-red-50 dark:bg-red-900/20 rounded-lg border border-red-100 dark:border-red-900/30">
                            <span className="font-semibold text-red-700 dark:text-red-400 mr-2">Allergies:</span>
                            <span className="text-red-600 dark:text-red-300">{patientDetails.allergies.join(', ')}</span>
                        </div>
                    </div>
                </Card>

                {/* Quick Actions Card */}
                <Card className="p-6 flex flex-col justify-center gap-4 bg-slate-50 dark:bg-slate-800/50">
                    <h3 className="font-semibold text-gray-700 dark:text-slate-200 mb-2">Nurse Actions</h3>
                    <Button
                        variant="primary"
                        fullWidth
                        onClick={() => navigate(`/dashboard/nurse/patient/${id}/vitals`)}
                        className="flex items-center justify-center gap-2 h-12"
                    >
                        <Activity className="w-5 h-5" />
                        Record Vitals
                    </Button>
                    <Button
                        variant="secondary"
                        fullWidth
                        onClick={() => navigate(`/dashboard/nurse/patient/${id}/medication`)}
                        className="flex items-center justify-center gap-2 h-12"
                    >
                        <Clock className="w-5 h-5" />
                        Administer Medication
                    </Button>
                    <div className="text-xs text-center text-gray-500 mt-2 p-2 bg-yellow-50 dark:bg-yellow-900/10 rounded border border-yellow-100 dark:border-yellow-900/20">
                        <AlertCircle className="w-3 h-3 inline mr-1" />
                        Treatment Plan editing is restricted to Physicians.
                    </div>
                </Card>
            </div>

            {/* Vitals Snapshot & Care Instructions */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <Card className="p-6">
                    <h3 className="font-semibold text-lg mb-4 flex items-center gap-2">
                        <Activity className="w-5 h-5 text-blue-500" />
                        Latest Vitals Snapshot
                        <span className="text-xs font-normal text-gray-400 ml-auto">Last checked: {patientDetails.lastVitals}</span>
                    </h3>

                    <div className="grid grid-cols-2 gap-4">
                        <div className="p-3 rounded-lg bg-blue-50 dark:bg-blue-900/10 border border-blue-100 dark:border-blue-900/20">
                            <div className="flex items-center gap-2 text-blue-600 dark:text-blue-400 mb-1">
                                <Heart className="w-4 h-4" /> <span className="text-xs font-bold uppercase">BP / HR</span>
                            </div>
                            <div className="text-xl font-bold dark:text-white">
                                {patientDetails.latestVitals.bp} <span className="text-sm font-normal text-gray-500">mmHg</span>
                            </div>
                            <div className="text-sm text-gray-600 dark:text-slate-400">{patientDetails.latestVitals.hr} bpm</div>
                        </div>

                        <div className="p-3 rounded-lg bg-red-50 dark:bg-red-900/10 border border-red-100 dark:border-red-900/20">
                            <div className="flex items-center gap-2 text-red-600 dark:text-red-400 mb-1">
                                <Thermometer className="w-4 h-4" /> <span className="text-xs font-bold uppercase">Temp</span>
                            </div>
                            <div className="text-xl font-bold dark:text-white">
                                {patientDetails.latestVitals.temp}°F
                            </div>
                        </div>

                        <div className="p-3 rounded-lg bg-cyan-50 dark:bg-cyan-900/10 border border-cyan-100 dark:border-cyan-900/20">
                            <div className="flex items-center gap-2 text-cyan-600 dark:text-cyan-400 mb-1">
                                <Droplets className="w-4 h-4" /> <span className="text-xs font-bold uppercase">SpO2</span>
                            </div>
                            <div className="text-xl font-bold dark:text-white">
                                {patientDetails.latestVitals.spo2}%
                            </div>
                        </div>

                        <div className="p-3 rounded-lg bg-purple-50 dark:bg-purple-900/10 border border-purple-100 dark:border-purple-900/20">
                            <div className="flex items-center gap-2 text-purple-600 dark:text-purple-400 mb-1">
                                <Wind className="w-4 h-4" /> <span className="text-xs font-bold uppercase">Resp</span>
                            </div>
                            <div className="text-xl font-bold dark:text-white">
                                {patientDetails.latestVitals.rr} <span className="text-sm font-normal text-gray-500">/min</span>
                            </div>
                        </div>
                    </div>
                </Card>

                <Card className="p-6">
                    <h3 className="font-semibold text-lg mb-4 flex items-center gap-2">
                        <FileText className="w-5 h-5 text-green-500" />
                        Care Instructions
                    </h3>
                    <ul className="space-y-3">
                        {patientDetails.careInstructions.map((instruction, idx) => (
                            <li key={idx} className="flex gap-3 text-sm text-gray-700 dark:text-slate-300">
                                <span className="flex-shrink-0 w-6 h-6 rounded-full bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400 flex items-center justify-center text-xs font-bold">
                                    {idx + 1}
                                </span>
                                <span className="pt-0.5">{instruction}</span>
                            </li>
                        ))}
                    </ul>
                </Card>
            </div>

            {/* Read-only Medication Schedule */}
            <Card className="p-6">
                <h3 className="font-semibold text-lg mb-4">Medication Schedule (Today)</h3>
                <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-200 dark:divide-slate-700">
                        <thead className="bg-gray-50 dark:bg-slate-800">
                            <tr>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Medication</th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Dosage</th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Frequency</th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Next Due</th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                            </tr>
                        </thead>
                        <tbody className="bg-white dark:bg-slate-800 divide-y divide-gray-200 dark:divide-slate-700">
                            {patientDetails.medications.map((med) => (
                                <tr key={med.id}>
                                    <td className="px-6 py-4 whitespace-nowrap font-medium text-gray-900 dark:text-white">{med.name}</td>
                                    <td className="px-6 py-4 whitespace-nowrap text-gray-500 dark:text-slate-400">{med.dosage}</td>
                                    <td className="px-6 py-4 whitespace-nowrap text-gray-500 dark:text-slate-400">{med.frequency}</td>
                                    <td className="px-6 py-4 whitespace-nowrap text-gray-500 dark:text-slate-400">{med.nextDue}</td>
                                    <td className="px-6 py-4 whitespace-nowrap">
                                        {getMedicationStatusBadge(med.status)}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </Card>
        </div>
    );
};

export default PatientDetail;
