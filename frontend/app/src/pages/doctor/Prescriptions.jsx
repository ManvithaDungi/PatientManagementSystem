import React, { useState, useEffect, useCallback } from 'react';
import { Plus, Search, Filter, Pill } from 'lucide-react';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';
import Modal from '../../components/common/Modal';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';

const Prescriptions = () => {
    const { user } = useAuth();
    const [searchTerm, setSearchTerm] = useState('');
    const [prescriptions, setPrescriptions] = useState([]);
    const [patients, setPatients] = useState([]);
    const [isNewRxModalOpen, setIsNewRxModalOpen] = useState(false);
    const [isManageModalOpen, setIsManageModalOpen] = useState(false);
    const [selectedRx, setSelectedRx] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    // Form states
    const [newRxData, setNewRxData] = useState({
        patientId: '',
        name: '',
        dosage: '',
        frequency: '',
        duration: '7 days',
        notes: ''
    });

    const [editRxData, setEditRxData] = useState({
        dosage: '',
        frequency: '',
        active: true
    });

    // Load prescriptions on mount
    const loadData = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const doctorId = user?.userId;
            if (!doctorId) throw new Error('User ID not found');

            // Get doctor's patients
            const doctorPatients = await api.doctors.getPatients(doctorId);
            setPatients(doctorPatients);

            // One request for all of this doctor's prescriptions (previously one request per patient)
            const mine = await api.prescriptions.getMine();
            const nameById = new Map(doctorPatients.map((p) => [p.id, `${p.firstName} ${p.lastName}`]));
            const allPrescriptions = (mine || []).map((rx) => ({
                ...rx,
                patientName: rx.patientName || nameById.get(rx.patientId) || 'Unknown patient',
            }));
            setPrescriptions(allPrescriptions);
        } catch (err) {
            console.error('Failed to load prescriptions:', err);
            setError('Failed to load prescriptions. Please refresh the page.');
        } finally {
            setIsLoading(false);
        }
    }, [user?.userId]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const filteredPrescriptions = prescriptions.filter(rx => {
        const patientName = rx.patientName || '';
        const medicationName = rx.medicationName || '';
        return medicationName.toLowerCase().includes(searchTerm.toLowerCase()) ||
            patientName.toLowerCase().includes(searchTerm.toLowerCase());
    });

    const handleNewRxSubmit = async (e) => {
        e.preventDefault();
        
        if (!newRxData.patientId) {
            setError('Please select a patient');
            return;
        }

        try {
            setIsLoading(true);
            setError(null);

            const payload = {
                patientId: parseInt(newRxData.patientId),
                medicationName: newRxData.name,
                dosage: newRxData.dosage,
                frequency: newRxData.frequency,
                duration: newRxData.duration || '7 days',
                specialInstructions: newRxData.notes || '',
                refillsAllowed: 0
            };

            const createdPrescription = await api.prescriptions.create(payload);
            setPrescriptions([createdPrescription, ...prescriptions]);
            setIsNewRxModalOpen(false);
            setNewRxData({ patientId: '', name: '', dosage: '', frequency: '', duration: '7 days', notes: '' });
            
        } catch (err) {
            console.error('Failed to create prescription:', err);
            setError(err.message || 'Failed to create prescription. Please try again.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleManageClick = (rx) => {
        setSelectedRx(rx);
        setEditRxData({
            dosage: rx.dosage,
            frequency: rx.frequency,
            active: rx.status === 'ACTIVE'
        });
        setIsManageModalOpen(true);
    };

    const handleUpdateRx = async (e) => {
        e.preventDefault();
        
        try {
            setIsLoading(true);
            setError(null);

            // Nothing is shown as changed until the server has confirmed it.
            await api.prescriptions.update(selectedRx.prescriptionId, {
                dosage: editRxData.dosage,
                frequency: editRxData.frequency,
            });
            let status = selectedRx.status;
            if (selectedRx.status === 'ACTIVE' && !editRxData.active) {
                const updated = await api.prescriptions.updateStatus(selectedRx.prescriptionId, 'CANCELLED');
                status = updated?.status || 'CANCELLED';
            }
            const updatedPrescription = {
                ...selectedRx,
                dosage: editRxData.dosage,
                frequency: editRxData.frequency,
                status,
            };
            setPrescriptions(prescriptions.map(rx =>
                rx.prescriptionId === selectedRx.prescriptionId ? updatedPrescription : rx
            ));
            setIsManageModalOpen(false);
            setSelectedRx(null);
        } catch (err) {
            console.error('Failed to update prescription:', err);
            setError(err.message || 'Failed to update prescription. Please try again.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="space-y-3">
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-2">
                <div>
                    <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">Prescriptions</h2>
                    <p className="text-xs text-gray-500 dark:text-slate-400">Manage patient medications and refills.</p>
                </div>
                <Button onClick={() => setIsNewRxModalOpen(true)} className="flex items-center text-sm" disabled={isLoading}>
                    <Plus className="w-4 h-4 mr-1" /> New Prescription
                </Button>
            </div>

            {error && (
                <Card className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
                    <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
                </Card>
            )}

            <Card className="p-3 dark:bg-slate-800">
                <div className="flex items-center gap-2">
                    <div className="relative flex-1">
                        <Search className="absolute left-2.5 top-2 text-gray-400 dark:text-slate-500 w-4 h-4" />
                        <input
                            type="text"
                            placeholder="Search prescriptions..."
                            className="w-full pl-8 pr-3 py-1.5 border border-gray-200 dark:border-slate-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-slate-700 dark:text-slate-100 dark:placeholder-slate-400"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            disabled={isLoading}
                        />
                    </div>
                    <Button variant="outline" className="hidden md:flex items-center text-sm">
                        <Filter className="w-3.5 h-3.5 mr-1" /> Filter
                    </Button>
                </div>
            </Card>

            {isLoading ? (
                <Card className="p-6 text-center">
                    <p className="text-gray-500 dark:text-slate-400">Loading prescriptions...</p>
                </Card>
            ) : filteredPrescriptions.length === 0 ? (
                <Card className="p-6 text-center">
                    <p className="text-gray-500 dark:text-slate-400">No prescriptions found. Create one to get started.</p>
                </Card>
            ) : (
                <div className="grid gap-2">
                    {filteredPrescriptions.map(rx => (
                        <Card key={rx.prescriptionId} className="p-3 flex flex-col md:flex-row justify-between items-center hover:shadow-md transition-shadow dark:bg-slate-800">
                        <div className="flex items-center gap-3 w-full md:w-auto">
                            <div className="p-2 bg-purple-50 dark:bg-purple-900/20 text-purple-600 dark:text-purple-400 rounded">
                                <Pill size={16} />
                            </div>
                            <div>
                                <div className="text-xs text-gray-500 dark:text-slate-400 mb-0.5">
                                    Patient: <span className="font-semibold text-gray-700 dark:text-slate-300">{rx.patientName}</span> ({rx.patientId})
                                </div>
                                <h3 className="font-bold text-sm text-gray-800 dark:text-slate-100">{rx.medicationName}</h3>
                                <div className="text-xs text-gray-500 dark:text-slate-400 flex flex-wrap gap-1">
                                    <span>{rx.dosage}</span>
                                    <span>•</span>
                                    <span>{rx.frequency}</span>
                                </div>
                            </div>
                        </div>

                        <div className="flex items-center gap-4 mt-2 md:mt-0 w-full md:w-auto justify-between md:justify-end">
                            <div className="text-right mr-2">
                                <div className="text-xs text-gray-500 dark:text-slate-400">Prescribed By</div>
                                <div className="font-medium text-xs text-gray-800 dark:text-slate-100">{rx.doctorName || 'N/A'}</div>
                            </div>
                            <Badge type={rx.status === 'ACTIVE' ? 'green' : 'gray'}>
                                {rx.status === 'ACTIVE' ? 'Active' : 'Discontinued'}
                            </Badge>
                            <Button
                                variant="outline"
                                className="text-xs"
                                onClick={() => handleManageClick(rx)}
                            >
                                Manage
                            </Button>
                        </div>
                    </Card>
                ))}
                </div>
            )}
            <Modal
                isOpen={isNewRxModalOpen}
                onClose={() => setIsNewRxModalOpen(false)}
                title="New Prescription"
            >
                <form onSubmit={handleNewRxSubmit} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Select Patient</label>
                        <select
                            required
                            className="w-full p-2 border border-gray-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-700 text-sm focus:ring-2 focus:ring-blue-500"
                            value={newRxData.patientId}
                            onChange={(e) => setNewRxData({ ...newRxData, patientId: e.target.value })}
                        >
                            <option value="">-- Select Patient --</option>
                            {patients.map(p => (
                                <option key={p.id} value={p.id}>{p.firstName} {p.lastName} ({p.id})</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Medication Name</label>
                        <input
                            type="text"
                            required
                            placeholder="e.g. Amoxicillin"
                            className="w-full p-2 border border-gray-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-700 text-sm focus:ring-2 focus:ring-blue-500"
                            value={newRxData.name}
                            onChange={(e) => setNewRxData({ ...newRxData, name: e.target.value })}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Dosage</label>
                            <input
                                type="text"
                                required
                                placeholder="e.g. 500mg"
                                className="w-full p-2 border border-gray-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-700 text-sm focus:ring-2 focus:ring-blue-500"
                                value={newRxData.dosage}
                                onChange={(e) => setNewRxData({ ...newRxData, dosage: e.target.value })}
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Frequency</label>
                            <input
                                type="text"
                                required
                                placeholder="e.g. 2x Daily"
                                className="w-full p-2 border border-gray-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-700 text-sm focus:ring-2 focus:ring-blue-500"
                                value={newRxData.frequency}
                                onChange={(e) => setNewRxData({ ...newRxData, frequency: e.target.value })}
                            />
                        </div>
                    </div>

                    <div className="flex justify-end gap-2 pt-2">
                        <Button type="button" variant="secondary" onClick={() => setIsNewRxModalOpen(false)}>Cancel</Button>
                        <Button type="submit">Create Prescription</Button>
                    </div>
                </form>
            </Modal>

            {/* Manage Prescription Modal */}
            <Modal
                isOpen={isManageModalOpen}
                onClose={() => setIsManageModalOpen(false)}
                title="Manage Prescription"
            >
                {selectedRx && (
                    <form onSubmit={handleUpdateRx} className="space-y-4">
                        <div className="bg-gray-50 dark:bg-slate-700/50 p-3 rounded-lg mb-4">
                            <p className="text-sm font-medium text-gray-800 dark:text-slate-200">{selectedRx.medicationName}</p>
                            <p className="text-xs text-gray-500 dark:text-slate-400">Patient: {selectedRx.patientName}</p>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Dosage</label>
                                <input
                                    type="text"
                                    className="w-full p-2 border border-gray-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-700 text-sm focus:ring-2 focus:ring-blue-500"
                                    value={editRxData.dosage}
                                    onChange={(e) => setEditRxData({ ...editRxData, dosage: e.target.value })}
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Frequency</label>
                                <input
                                    type="text"
                                    className="w-full p-2 border border-gray-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-700 text-sm focus:ring-2 focus:ring-blue-500"
                                    value={editRxData.frequency}
                                    onChange={(e) => setEditRxData({ ...editRxData, frequency: e.target.value })}
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-slate-300 mb-1">Status</label>
                            <select
                                className="w-full p-2 border border-gray-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-700 text-sm focus:ring-2 focus:ring-blue-500"
                                value={editRxData.active}
                                onChange={(e) => setEditRxData({ ...editRxData, active: e.target.value === 'true' })}
                            >
                                <option value="true">Active</option>
                                <option value="false">Discontinued</option>
                            </select>
                        </div>

                        <div className="flex justify-end gap-2 pt-2">
                            <Button type="button" variant="secondary" onClick={() => setIsManageModalOpen(false)}>Cancel</Button>
                            <Button type="submit">Update Prescription</Button>
                        </div>
                    </form>
                )}
            </Modal>
        </div>
    );
};

export default Prescriptions;
