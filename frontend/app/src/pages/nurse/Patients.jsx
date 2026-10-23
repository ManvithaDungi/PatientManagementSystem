import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Eye, Clock, AlertTriangle, CheckCircle } from 'lucide-react';

import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import IconButton from '../../components/common/IconButton';
import Badge from '../../components/common/Badge';
import { Table, TableHead, TableBody, TableRow, TableHeader, TableCell } from '../../components/common/Table';
import Input from '../../components/common/Input';
import Select from '../../components/common/Select';
import api from '../../services/api';

const Patients = () => {
    const navigate = useNavigate();
    const [searchTerm, setSearchTerm] = useState('');
    const [filterStatus, setFilterStatus] = useState('all');
    const [currentPage, setCurrentPage] = useState(1);
    const itemsPerPage = 10;

    const [patients, setPatients] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    const calculateAge = (dateOfBirth) => {
        if (!dateOfBirth) return 'N/A';
        const today = new Date();
        const birth = new Date(dateOfBirth);
        let age = today.getFullYear() - birth.getFullYear();
        const monthDiff = today.getMonth() - birth.getMonth();
        if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) age--;
        return age;
    };

    React.useEffect(() => {
        const fetchPatients = async () => {
            try {
                setIsLoading(true);
                const data = await api.nurse.getAssignedPatients();
                if (data && Array.isArray(data)) {
                    setPatients(data.map(p => ({
                        id: p.profileId ? p.profileId.toString() : '',
                        name: `${p.firstName} ${p.lastName}`,
                        age: calculateAge(p.dateOfBirth),
                        gender: p.gender || 'N/A',
                        room: 'N/A',
                        bed: 'N/A',
                        diagnosis: p.medicalHistory || 'No history recorded',
                        vitalsStatus: 'unknown',
                        lastVitals: 'Not recorded',
                        status: 'unknown',
                    })));
                } else {
                    setPatients([]);
                }
            } catch (err) {
                console.error("Failed to fetch assigned patients:", err);
                setError("Failed to load your assigned patients.");
            } finally {
                setIsLoading(false);
            }
        };
        fetchPatients();
    }, []);

    const filteredPatients = useMemo(() => {
        return patients.filter(patient => {
            const matchesSearch = patient.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                patient.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
                (patient.room && patient.room.includes(searchTerm));
            const matchesFilter = filterStatus === 'all' || patient.status === filterStatus;
            return matchesSearch && matchesFilter;
        });
    }, [searchTerm, filterStatus, patients]);

    const paginatedPatients = useMemo(() => {
        const startIndex = (currentPage - 1) * itemsPerPage;
        return filteredPatients.slice(startIndex, startIndex + itemsPerPage);
    }, [filteredPatients, currentPage]);

    const totalPages = Math.ceil(filteredPatients.length / itemsPerPage);

    const getStatusBadge = (status) => {
        switch (status) {
            case 'stable': return <Badge type="green" variant="soft">Stable</Badge>;
            case 'monitor': return <Badge type="yellow" variant="soft">Monitor</Badge>;
            case 'critical': return <Badge type="red" variant="soft">Critical</Badge>;
            default: return <Badge type="gray" variant="soft">{status}</Badge>;
        }
    };

    const getVitalsStatusIcon = (status) => {
        switch (status) {
            case 'done': return <CheckCircle className="w-4 h-4 text-green-500" />;
            case 'due': return <Clock className="w-4 h-4 text-yellow-500" />;
            case 'overdue': return <AlertTriangle className="w-4 h-4 text-red-500" />;
            default: return null;
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Assigned Patients</h1>
                    <p className="text-gray-500 dark:text-slate-400">Manage your assigned patients and monitor their status.</p>
                </div>
                <div className="flex gap-2 w-full sm:w-auto">
                    <div className="relative flex-1 sm:flex-none">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                        <Input
                            placeholder="Search patients..."
                            className="pl-9 w-full sm:w-64"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </div>
                    <Select
                        value={filterStatus}
                        onChange={(e) => setFilterStatus(e.target.value)}
                        className="w-32"
                    >
                        <option value="all">All Status</option>
                        <option value="stable">Stable</option>
                        <option value="monitor">Monitor</option>
                        <option value="critical">Critical</option>
                    </Select>
                </div>
            </div>

            <Card className="overflow-hidden">
                <Table>
                    <TableHead>
                        <TableHeader>ID</TableHeader>
                        <TableHeader>Patient</TableHeader>
                        <TableHeader>Room</TableHeader>
                        <TableHeader>Diagnosis</TableHeader>
                        <TableHeader>Vitals Status</TableHeader>
                        <TableHeader>Status</TableHeader>
                        <TableHeader align="right">Actions</TableHeader>
                    </TableHead>
                    <TableBody>
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan={7} className="text-center py-8 text-gray-500">
                                    Loading patients...
                                </TableCell>
                            </TableRow>
                        ) : error ? (
                            <TableRow>
                                <TableCell colSpan={7} className="text-center py-8 text-red-500">
                                    {error}
                                </TableCell>
                            </TableRow>
                        ) : paginatedPatients.length > 0 ? (
                            paginatedPatients.map((patient) => (
                                <TableRow key={patient.id} onClick={() => navigate(`/dashboard/nurse/patient/${patient.id}`)}>
                                    <TableCell className="font-medium text-gray-900 dark:text-white">#{patient.id}</TableCell>
                                    <TableCell>
                                        <div>
                                            <div className="font-medium text-gray-900 dark:text-white">{patient.name}</div>
                                            <div className="text-xs text-gray-500">{patient.age} {patient.age !== 'N/A' && 'yrs,'} {patient.gender}</div>
                                        </div>
                                    </TableCell>
                                    <TableCell>Not recorded</TableCell>
                                    <TableCell>{patient.diagnosis}</TableCell>
                                    <TableCell>
                                        <div className="flex items-center gap-2">
                                            {getVitalsStatusIcon(patient.vitalsStatus)}
                                            <span className="capitalize text-sm">{patient.lastVitals}</span>
                                        </div>
                                    </TableCell>
                                    <TableCell>{getStatusBadge(patient.status)}</TableCell>
                                    <TableCell align="right">
                                        <IconButton 
                                           icon={Eye} 
                                           label="View" 
                                           variant="ghost"
                                           size="sm"
                                           onClick={(e) => {
                                               e.stopPropagation();
                                               navigate(`/dashboard/nurse/patient/${patient.id}`);
                                           }}
                                        />
                                    </TableCell>
                                </TableRow>
                            ))
                        ) : (
                            <TableRow>
                                <TableCell colSpan={7} className="text-center py-8 text-gray-500">
                                    No patients found matching your criteria.
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>

                {/* Pagination */}
                {totalPages > 1 && (
                    <div className="px-6 py-4 border-t border-gray-200 dark:border-slate-700 flex justify-between items-center">
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={currentPage === 1}
                            onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
                        >
                            Previous
                        </Button>
                        <span className="text-sm text-gray-600 dark:text-slate-400">
                            Page {currentPage} of {totalPages}
                        </span>
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={currentPage === totalPages}
                            onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))}
                        >
                            Next
                        </Button>
                    </div>
                )}
            </Card>
        </div>
    );
};

export default Patients;
