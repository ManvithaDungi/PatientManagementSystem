import React, { useState, useEffect, useCallback } from 'react';
import { FileText, ChevronLeft, ChevronRight, Search } from 'lucide-react';
import Card from '../../../components/common/Card';
import Button from '../../../components/common/Button';
import { Table, TableHead, TableBody, TableRow, TableHeader, TableCell } from '../../../components/common/Table';
import api from '../../../services/api';

const PAGE_SIZE = 25;

const AuditLogs = () => {
    const [logs, setLogs] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [emailInput, setEmailInput] = useState('');
    const [emailFilter, setEmailFilter] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchAuditLogs = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await api.admin.getAuditLogs({ page, size: PAGE_SIZE, email: emailFilter });
            setLogs(data.content || []);
            setTotalPages(data.totalPages || 0);
            setTotalElements(data.totalElements || 0);
        } catch (err) {
            console.error('Failed to load audit logs', err);
            setLogs([]);
            setTotalPages(0);
            setTotalElements(0);
            setError('Unable to load audit logs. Please try again.');
        } finally {
            setLoading(false);
        }
    }, [page, emailFilter]);

    useEffect(() => {
        fetchAuditLogs();
    }, [fetchAuditLogs]);

    const applyFilter = (e) => {
        e.preventDefault();
        setPage(0);
        setEmailFilter(emailInput.trim());
    };

    return (
        <Card className="border-t-4 border-t-slate-500">
            <div className="p-6 border-b border-gray-100 dark:border-slate-700 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-slate-100 dark:bg-slate-700 rounded-lg text-slate-600 dark:text-slate-300">
                        <FileText size={20} />
                    </div>
                    <div>
                        <h2 className="text-lg font-bold text-slate-800 dark:text-white">Audit Logs</h2>
                        <p className="text-sm text-slate-500 dark:text-slate-400">
                            Newest first ({totalElements.toLocaleString()} entries{emailFilter ? ` for ${emailFilter}` : ''})
                        </p>
                    </div>
                </div>
                <form onSubmit={applyFilter} className="flex gap-2">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
                        <input
                            type="text"
                            placeholder="Filter by user email"
                            value={emailInput}
                            onChange={(e) => setEmailInput(e.target.value)}
                            className="pl-9 pr-3 py-2 border border-slate-200 dark:border-slate-700 rounded-lg text-sm dark:bg-slate-800 dark:text-white"
                        />
                    </div>
                    <Button type="submit" variant="outline" className="text-sm">Apply</Button>
                </form>
            </div>

            {loading && (
                <div className="p-6 text-center text-slate-500">Loading audit logs...</div>
            )}

            {!loading && error && (
                <div className="p-6 text-center text-red-600 text-sm">{error}</div>
            )}

            {!loading && !error && logs.length === 0 && (
                <div className="p-6 text-center text-slate-500">No audit logs found.</div>
            )}

            {!loading && logs.length > 0 && (
                <>
                    <Table>
                        <TableHead>
                            <TableRow>
                                <TableHeader>Time</TableHeader>
                                <TableHeader>User</TableHeader>
                                <TableHeader>Action</TableHeader>
                                <TableHeader>IP Address</TableHeader>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {logs.map((log) => (
                                <TableRow key={log.id} hover>
                                    <TableCell>
                                        <span className="text-sm text-slate-500 whitespace-nowrap">
                                            {new Date(log.timestamp).toLocaleString()}
                                        </span>
                                    </TableCell>
                                    <TableCell>
                                        <div className="flex items-center gap-2">
                                            <div className="w-6 h-6 rounded-full bg-admin-primary/10 flex items-center justify-center text-xs font-bold text-admin-primary">
                                                {(log.email || '?').charAt(0).toUpperCase()}
                                            </div>
                                            <span className="font-medium text-slate-800 dark:text-white">{log.email}</span>
                                        </div>
                                    </TableCell>
                                    <TableCell>
                                        <span className="text-sm text-slate-600 dark:text-slate-300">{log.action}</span>
                                    </TableCell>
                                    <TableCell>
                                        <span className="font-mono text-xs text-slate-400">{log.ipAddress || 'N/A'}</span>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>

                    <div className="p-4 flex items-center justify-between text-sm text-slate-500 dark:text-slate-400">
                        <span>Page {page + 1} of {Math.max(totalPages, 1)}</span>
                        <div className="flex gap-2">
                            <Button
                                variant="outline"
                                className="flex items-center gap-1 text-sm"
                                disabled={page === 0}
                                onClick={() => setPage((p) => Math.max(p - 1, 0))}
                            >
                                <ChevronLeft size={16} /> Previous
                            </Button>
                            <Button
                                variant="outline"
                                className="flex items-center gap-1 text-sm"
                                disabled={page + 1 >= totalPages}
                                onClick={() => setPage((p) => p + 1)}
                            >
                                Next <ChevronRight size={16} />
                            </Button>
                        </div>
                    </div>
                </>
            )}
        </Card>
    );
};

export default AuditLogs;
