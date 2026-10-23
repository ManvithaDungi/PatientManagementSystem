import React, { useState } from 'react';
import { Search } from 'lucide-react';
import Card from '../../components/common/Card';
import LabResultsList from '../../components/doctor/LabResultsList';
import api from '../../services/api';
import { isAbnormal, normalizeStatus } from '../../utils/labRange';

const LabResults = () => {
    const [searchTerm, setSearchTerm] = useState('');
    const [filter, setFilter] = useState('All');
    const [labs, setLabs] = useState([]);

    React.useEffect(() => {
        const fetchLabs = async () => {
            try {
                const data = await api.labResults.getAll();
                if (Array.isArray(data)) {
                    setLabs(data);
                }
            } catch (error) {
                console.error("Failed to fetch labs", error);
            }
        };
        fetchLabs();
    }, []);

    const filteredLabs = labs.filter(lab => {
        const labName = (lab.name || lab.testName || '').toLowerCase();
        const matchesSearch = labName.includes(searchTerm.toLowerCase());
        const matchesFilter = filter === 'All'
            || (filter === 'Abnormal' ? isAbnormal(lab) : normalizeStatus(lab.status) === normalizeStatus(filter));
        return matchesSearch && matchesFilter;
    });

    return (
        <div className="space-y-3">
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-2">
                <div>
                    <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">Lab Results</h2>
                    <p className="text-xs text-gray-500 dark:text-slate-400">Manage and review patient test results.</p>
                </div>
            </div>

            <Card className="p-3 dark:bg-slate-800">
                <div className="flex flex-col md:flex-row gap-2 justify-between items-center">
                    <div className="relative w-full md:w-80">
                        <Search className="absolute left-2.5 top-2.5 text-gray-400 dark:text-slate-500 w-4 h-4" />
                        <input
                            type="text"
                            placeholder="Search by test name..."
                            className="w-full pl-8 pr-3 py-1.5 border border-gray-200 dark:border-slate-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-slate-700 dark:text-slate-100 dark:placeholder-slate-400"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </div>

                    <div className="flex gap-1 w-full md:w-auto overflow-x-auto">
                        {['All', 'Pending', 'Collected', 'Results Pending', 'Completed', 'Abnormal'].map(status => (
                            <button
                                key={status}
                                onClick={() => setFilter(status)}
                                className={`px-2.5 py-1 rounded text-xs font-medium whitespace-nowrap transition-colors ${filter === status
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 dark:bg-slate-700 text-gray-600 dark:text-slate-300 hover:bg-gray-200 dark:hover:bg-slate-600'
                                    }`}
                            >
                                {status}
                            </button>
                        ))}
                    </div>
                </div>
            </Card>

            <LabResultsList labs={filteredLabs} />
        </div>
    );
};

export default LabResults;
