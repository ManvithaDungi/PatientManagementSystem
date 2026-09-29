import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Save, AlertTriangle, ArrowLeft } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import IconButton from '../../components/common/IconButton';
import Input from '../../components/common/Input';
import api from '../../services/api';

const VITAL_LIMITS = {
    bp: { systolic: [90, 140], diastolic: [60, 90] },
    pulse: [60, 100],
    temp: [97, 99.5],
    spo2: [95, 100],
    rr: [12, 20]
};

const VitalsEntry = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [patient, setPatient] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [submitError, setSubmitError] = useState(null);
    const [vitalsHistory, setVitalsHistory] = useState([]);

    useEffect(() => {
        const loadPatient = async () => {
            try {
                const patients = await api.nurse.getAssignedPatients();
                const found = Array.isArray(patients)
                    ? patients.find(p => String(p.profileId) === id)
                    : null;
                setPatient(found || null);
            } catch (err) {
                console.error('Failed to load patient:', err);
            } finally {
                setIsLoading(false);
            }
        };
        loadPatient();
    }, [id]);

    useEffect(() => {
        const loadHistory = async () => {
            try {
                const data = await api.vitalSigns.getByPatient(id);
                if (Array.isArray(data)) {
                    setVitalsHistory(data.map(record => {
                        const [systolic, diastolic] = (record.bloodPressure || '').split('/').map(Number);
                        return {
                            time: record.recordedAt
                                ? new Date(record.recordedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                                : '',
                            systolic: systolic || 0,
                            diastolic: diastolic || 0,
                            pulse: record.heartRate || 0,
                            temp: record.temperature || 0,
                            spo2: record.oxygenSaturation || 0,
                            rr: record.respiratoryRate || 0,
                            nurseName: record.nurseEmail || '',
                            recordedAt: record.recordedAt,
                        };
                    }));
                }
            } catch (err) {
                console.error('Failed to load vitals history:', err);
            }
        };
        loadHistory();
    }, [id]);

    const [vitals, setVitals] = useState({
        systolic: '',
        diastolic: '',
        pulse: '',
        temp: '',
        spo2: '',
        rr: '',
        notes: ''
    });

    const [errors, setErrors] = useState({});

    const validate = (name, value) => {
        const val = parseFloat(value);
        if (isNaN(val)) return null;

        let isAbnormal = false;
        if (name === 'systolic' && (val < VITAL_LIMITS.bp.systolic[0] || val > VITAL_LIMITS.bp.systolic[1])) isAbnormal = true;
        if (name === 'diastolic' && (val < VITAL_LIMITS.bp.diastolic[0] || val > VITAL_LIMITS.bp.diastolic[1])) isAbnormal = true;
        if (name === 'pulse' && (val < VITAL_LIMITS.pulse[0] || val > VITAL_LIMITS.pulse[1])) isAbnormal = true;
        if (name === 'temp' && (val < VITAL_LIMITS.temp[0] || val > VITAL_LIMITS.temp[1])) isAbnormal = true;
        if (name === 'spo2' && (val < VITAL_LIMITS.spo2[0])) isAbnormal = true;
        if (name === 'rr' && (val < VITAL_LIMITS.rr[0] || val > VITAL_LIMITS.rr[1])) isAbnormal = true;

        return isAbnormal ? 'Abnormal value' : null;
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setVitals(prev => ({ ...prev, [name]: value }));

        const error = validate(name, value);
        setErrors(prev => ({ ...prev, [name]: error }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSubmitError(null);
        try {
            await api.vitalSigns.create({
                patientId: Number(id),
                bloodPressure: `${vitals.systolic}/${vitals.diastolic}`,
                heartRate: vitals.pulse ? Number(vitals.pulse) : undefined,
                temperature: vitals.temp ? Number(vitals.temp) : undefined,
                respiratoryRate: vitals.rr ? Number(vitals.rr) : undefined,
                oxygenSaturation: vitals.spo2 ? Number(vitals.spo2) : undefined,
            });
            navigate(`/dashboard/nurse/patient/${id}`);
        } catch (err) {
            console.error('Failed to save vitals:', err);
            setSubmitError('Failed to save vitals. Please try again.');
        }
    };

    if (isLoading) {
        return <div className="p-8 text-center text-gray-500">Loading patient...</div>;
    }

    if (!patient) {
        return (
            <div className="p-8 text-center">
                <p className="text-gray-500">Patient not found or not assigned to you.</p>
                <Button onClick={() => navigate('/dashboard/nurse/patients')} className="mt-4">Back to Patients</Button>
            </div>
        );
    }

    const patientName = `${patient.firstName || ''} ${patient.lastName || ''}`.trim() || 'Unknown';

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            <IconButton 
               icon={ArrowLeft} 
               label="Back to Patient" 
               variant="ghost"
               onClick={() => navigate(`/dashboard/nurse/patient/${id}`)}
               className="pl-0 hover:pl-2 transition-all"
            />

            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Record Vitals</h1>
                    <p className="text-gray-500">Patient: {patientName}</p>
                </div>
                <div className="text-sm text-gray-400">
                    {new Date().toLocaleString()}
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="md:col-span-2 space-y-6">
                    <Card className="p-6">
                        <form onSubmit={handleSubmit} className="space-y-6">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Blood Pressure (mmHg)</label>
                                    <div className="flex gap-2 items-center">
                                        <div className="flex-1">
                                            <Input
                                                name="systolic"
                                                placeholder="Systolic"
                                                value={vitals.systolic}
                                                onChange={handleChange}
                                                className={errors.systolic ? 'border-red-500 focus:ring-red-500' : ''}
                                            />
                                        </div>
                                        <span className="text-gray-400">/</span>
                                        <div className="flex-1">
                                            <Input
                                                name="diastolic"
                                                placeholder="Diastolic"
                                                value={vitals.diastolic}
                                                onChange={handleChange}
                                                className={errors.diastolic ? 'border-red-500 focus:ring-red-500' : ''}
                                            />
                                        </div>
                                    </div>
                                    {(errors.systolic || errors.diastolic) && (
                                        <p className="text-xs text-red-500 mt-1 flex items-center">
                                            <AlertTriangle className="w-3 h-3 mr-1" /> Abnormal BP reading
                                        </p>
                                    )}
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Pulse (bpm)</label>
                                    <Input
                                        name="pulse"
                                        placeholder="60-100"
                                        type="number"
                                        value={vitals.pulse}
                                        onChange={handleChange}
                                        className={errors.pulse ? 'border-red-500 focus:ring-red-500' : ''}
                                    />
                                    {errors.pulse && <p className="text-xs text-red-500 mt-1">{errors.pulse}</p>}
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Temperature (°F)</label>
                                    <Input
                                        name="temp"
                                        placeholder="98.6"
                                        type="number"
                                        step="0.1"
                                        value={vitals.temp}
                                        onChange={handleChange}
                                        className={errors.temp ? 'border-red-500 focus:ring-red-500' : ''}
                                    />
                                    {errors.temp && <p className="text-xs text-red-500 mt-1">{errors.temp}</p>}
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">SpO2 (%)</label>
                                    <Input
                                        name="spo2"
                                        placeholder="95-100"
                                        type="number"
                                        value={vitals.spo2}
                                        onChange={handleChange}
                                        className={errors.spo2 ? 'border-red-500 focus:ring-red-500' : ''}
                                    />
                                    {errors.spo2 && <p className="text-xs text-red-500 mt-1">{errors.spo2}</p>}
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Respiratory Rate (breaths/min)</label>
                                    <Input
                                        name="rr"
                                        placeholder="12-20"
                                        type="number"
                                        value={vitals.rr}
                                        onChange={handleChange}
                                        className={errors.rr ? 'border-red-500 focus:ring-red-500' : ''}
                                    />
                                    {errors.rr && <p className="text-xs text-red-500 mt-1">{errors.rr}</p>}
                                </div>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Clinical Notes</label>
                                <textarea
                                    className="w-full rounded-md border border-gray-300 dark:border-slate-600 dark:bg-slate-800 dark:text-white p-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                    rows="3"
                                    placeholder="Any observations..."
                                    value={vitals.notes}
                                    onChange={(e) => setVitals(prev => ({ ...prev, notes: e.target.value }))}
                                ></textarea>
                            </div>

                            {submitError && (
                                <p className="text-sm text-red-600 flex items-center gap-1">
                                    <AlertTriangle className="w-4 h-4" /> {submitError}
                                </p>
                            )}
                            <div className="flex justify-end gap-3 pt-4 border-t border-gray-100 dark:border-slate-700">
                                <Button variant="ghost" onClick={() => navigate(-1)}>
                                    Cancel
                                </Button>
                                <IconButton 
                                   icon={Save} 
                                   label="Save Vitals" 
                                   variant="primary"
                                   type="submit"
                                />
                            </div>
                        </form>
                    </Card>
                </div>

                <div className="space-y-6">
                    <Card className="p-4">
                        <h3 className="font-semibold text-gray-700 dark:text-slate-200 mb-4">Vitals Trends (24h)</h3>
                        <div className="h-48 w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={vitalsHistory}>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" />
                                    <XAxis dataKey="time" tick={{ fontSize: 10 }} axisLine={false} tickLine={false} />
                                    <YAxis tick={{ fontSize: 10 }} axisLine={false} tickLine={false} domain={['auto', 'auto']} />
                                    <Tooltip />
                                    <Line type="monotone" dataKey="systolic" stroke="#3b82f6" dot={false} strokeWidth={2} />
                                    <Line type="monotone" dataKey="diastolic" stroke="#93c5fd" dot={false} strokeWidth={2} />
                                    <Line type="monotone" dataKey="pulse" stroke="#ef4444" dot={false} strokeWidth={2} />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                        <div className="flex gap-4 justify-center mt-2 text-xs text-gray-500">
                            <span className="flex items-center gap-1"><div className="w-2 h-2 rounded-full bg-blue-500"></div> BP</span>
                            <span className="flex items-center gap-1"><div className="w-2 h-2 rounded-full bg-red-500"></div> Pulse</span>
                        </div>
                    </Card>

                    <Card className="p-4">
                        <h3 className="font-semibold text-gray-700 dark:text-slate-200 mb-4">Recent History</h3>
                        <div className="space-y-3">
                            {vitalsHistory.slice().reverse().map((record, idx) => (
                                <div key={idx} className="text-sm border-b border-gray-100 dark:border-slate-700 pb-2 last:border-0 last:pb-0">
                                    <div className="flex justify-between mb-1">
                                        <span className="font-medium text-gray-900 dark:text-white">{record.time}</span>
                                        <span className="text-gray-500">{record.nurseName}</span>
                                    </div>
                                    <div className="grid grid-cols-2 gap-x-2 text-xs text-gray-600 dark:text-slate-400">
                                        <span>BP: {record.systolic}/{record.diastolic}</span>
                                        <span>HR: {record.pulse}</span>
                                        <span>Temp: {record.temp}</span>
                                        <span>SpO2: {record.spo2}%</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default VitalsEntry;
