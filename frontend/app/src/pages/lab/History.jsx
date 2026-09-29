import React, { useState, useEffect } from 'react';
import { Search } from 'lucide-react';
import Card from '../../components/common/Card';
import Badge from '../../components/common/Badge';
import api from '../../services/api';

const LabHistory = () => {
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [reportError, setReportError] = useState(null);

    useEffect(() => {
        const fetchHistory = async () => {
            try {
                const data = await api.labTechnician.getOrders('Completed');
                if (data && Array.isArray(data)) {
                    setOrders(data);
                } else {
                    setOrders([]);
                }
            } catch (err) {
                console.error('Failed to fetch history:', err);
                setOrders([]);
            } finally {
                setLoading(false);
            }
        };
        fetchHistory();
    }, []);

    // A plain <a href> to /api/files/{filename} can't carry the app's bearer token,
    // so the file must be fetched with an authenticated request instead (LA-3).
    const handleViewReport = async (filename) => {
        setReportError(null);
        try {
            const objectUrl = await api.files.getObjectUrl(filename);
            window.open(objectUrl, '_blank', 'noopener,noreferrer');
            setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        } catch (err) {
            console.error('Failed to open report:', err);
            setReportError('Unable to open this report. Please try again.');
        }
    };

    const filteredHistory = orders.filter(order => {
        const matchesSearch =
            (order.patientName || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
            (order.testName || '').toLowerCase().includes(searchTerm.toLowerCase());

        let matchesDate = true;
        if (startDate && endDate) {
            const orderDate = order.orderedAt ? new Date(order.orderedAt) : null;
            if (orderDate) {
                const start = new Date(startDate);
                const end = new Date(endDate);
                end.setHours(23, 59, 59, 999);
                matchesDate = orderDate >= start && orderDate <= end;
            }
        }

        return matchesSearch && matchesDate;
    });

    return (
        <div className="space-y-3">
            <div>
                <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">Lab History</h2>
                <p className="text-xs text-gray-500 dark:text-slate-400">Archive of all completed lab tests</p>
            </div>

            <Card className="p-3 dark:bg-slate-800">
                {reportError && (
                    <div className="mb-3 p-2 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 text-xs">{reportError}</div>
                )}
                <div className="flex flex-col gap-3 mb-4">
                    <div className="flex flex-col md:flex-row justify-between items-center gap-3">
                        {/* Search */}
                        <div className="relative w-full md:w-64">
                            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 dark:text-slate-500 w-4 h-4" />
                            <input
                                type="text"
                                placeholder="Search by patient or test..."
                                className="w-full pl-9 pr-4 py-2 border border-gray-200 dark:border-slate-600 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-medium/50 bg-gray-50 dark:bg-slate-900/50 text-gray-900 dark:text-slate-100"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                            />
                        </div>

                        <div className="flex items-center gap-2 w-full md:w-auto">
                            {/* Date Range Inputs */}
                            <div className="flex items-center gap-2 bg-gray-50 dark:bg-slate-900/50 p-1 rounded-xl border border-gray-100 dark:border-slate-700">
                                <span className="text-xs text-gray-500 pl-2">Date Range</span>
                                <input
                                    type="date"
                                    value={startDate}
                                    onChange={(e) => setStartDate(e.target.value)}
                                    className="bg-transparent border-none text-xs text-gray-600 dark:text-slate-300 focus:ring-0 px-2 py-1"
                                />
                                <span className="text-gray-400">-</span>
                                <input
                                    type="date"
                                    value={endDate}
                                    onChange={(e) => setEndDate(e.target.value)}
                                    className="bg-transparent border-none text-xs text-gray-600 dark:text-slate-300 focus:ring-0 px-2 py-1"
                                />
                            </div>
                        </div>
                    </div>
                </div>

                {loading ? (
                    <p className="text-xs text-gray-500 dark:text-slate-400 py-4 text-center">Loading history...</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-100 dark:divide-slate-700">
                            <thead className="bg-gray-50 dark:bg-slate-700/50">
                                <tr>
                                    <th className="px-3 py-2 text-left text-[10px] font-medium text-gray-500 dark:text-slate-300 uppercase tracking-wider">Order ID</th>
                                    <th className="px-3 py-2 text-left text-[10px] font-medium text-gray-500 dark:text-slate-300 uppercase tracking-wider">Patient</th>
                                    <th className="px-3 py-2 text-left text-[10px] font-medium text-gray-500 dark:text-slate-300 uppercase tracking-wider">Test</th>
                                    <th className="px-3 py-2 text-left text-[10px] font-medium text-gray-500 dark:text-slate-300 uppercase tracking-wider">Date</th>
                                    <th className="px-3 py-2 text-left text-[10px] font-medium text-gray-500 dark:text-slate-300 uppercase tracking-wider">Status</th>
                                    <th className="px-3 py-2 text-right text-[10px] font-medium text-gray-500 dark:text-slate-300 uppercase tracking-wider">Result</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white dark:bg-slate-800 divide-y divide-gray-100 dark:divide-slate-700">
                                {filteredHistory.length === 0 ? (
                                    <tr>
                                        <td colSpan={6} className="px-3 py-4 text-center text-xs text-gray-500 dark:text-slate-400">No completed tests found</td>
                                    </tr>
                                ) : filteredHistory.map((order) => (
                                    <tr key={order.testId} className="hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors">
                                        <td className="px-3 py-2 whitespace-nowrap text-xs text-gray-500 dark:text-slate-400">{order.testId}</td>
                                        <td className="px-3 py-2 whitespace-nowrap text-xs font-medium text-gray-900 dark:text-slate-100">{order.patientName}</td>
                                        <td className="px-3 py-2 whitespace-nowrap text-xs text-gray-700 dark:text-slate-300">{order.testName || 'N/A'}</td>
                                        <td className="px-3 py-2 whitespace-nowrap text-xs text-gray-500 dark:text-slate-400">
                                            {order.orderedAt ? new Date(order.orderedAt).toLocaleDateString() : 'N/A'}
                                        </td>
                                        <td className="px-3 py-2 whitespace-nowrap">
                                            <Badge type="green">Completed</Badge>
                                        </td>
                                        <td className="px-3 py-2 whitespace-nowrap text-right text-xs font-medium">
                                            {order.resultValue ? (
                                                <span className="text-gray-700 dark:text-slate-300">{order.resultValue} {order.unit || ''}</span>
                                            ) : order.fileUrl ? (
                                                <button type="button" onClick={() => handleViewReport(order.fileUrl)} className="text-brand-medium hover:text-brand-deep underline">
                                                    View Report
                                                </button>
                                            ) : (
                                                <span className="text-gray-400 dark:text-slate-500">—</span>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </Card>
        </div>
    );
};

export default LabHistory;
