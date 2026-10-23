import React, { useState, useEffect } from 'react';
import { Users, Shield, Activity, Clock } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import Card from '../../../components/common/Card';
import api from '../../../services/api';

const StatCard = ({ title, value, icon: Icon }) => (
    <Card className="p-6 border-l-4 border-l-admin-primary bg-white dark:bg-slate-800 hover:shadow-lg transition-all duration-300">
        <div className="flex justify-between items-start">
            <div>
                <p className="text-sm font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider">{title}</p>
                <h3 className="text-3xl font-bold text-slate-800 dark:text-white mt-2">{value}</h3>
            </div>
            <div className="p-3 rounded-xl bg-admin-primary/10 text-admin-primary">
                <Icon size={24} />
            </div>
        </div>
    </Card>
);

const TOP_ACTIONS = 8;

const SystemOverview = () => {
    const [stats, setStats] = useState(null);
    const [activity, setActivity] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;
        const load = async () => {
            const [metricsResult, activityResult] = await Promise.allSettled([
                api.admin.getMetrics(),
                api.admin.getUserActivity('7d'),
            ]);
            if (cancelled) return;
            setStats(metricsResult.status === 'fulfilled' ? metricsResult.value : null);
            setActivity(activityResult.status === 'fulfilled' ? activityResult.value : null);
            if (metricsResult.status === 'rejected' && activityResult.status === 'rejected') {
                setError('Unable to load system overview. Please try again.');
            }
            setLoading(false);
        };
        load();
        return () => { cancelled = true; };
    }, []);

    const value = (v) => (loading ? '...' : v === null || v === undefined ? 'n/a' : Number(v).toLocaleString());

    const chartData = Object.entries(activity?.byAction || {})
        .map(([action, count]) => ({ action: action.replace(/_/g, ' '), count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, TOP_ACTIONS);

    return (
        <div className="space-y-6">
            {error && <div className="p-4 text-sm text-red-600 bg-red-50 rounded-lg">{error}</div>}

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <StatCard title="Total Patients" value={value(stats?.totalPatients)} icon={Users} />
                <StatCard title="Total Doctors" value={value(stats?.totalDoctors)} icon={Activity} />
                <StatCard title="Pending Approvals" value={value(stats?.pendingApprovals)} icon={Shield} />
                <StatCard title="Today's Appointments" value={value(stats?.todaysAppointments)} icon={Clock} />
            </div>

            <Card className="p-6 bg-white dark:bg-slate-800">
                <h3 className="text-lg font-bold text-slate-800 dark:text-white mb-1">System Activity (Last 7 Days)</h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 mb-4">
                    {activity ? `${activity.totalEvents.toLocaleString()} audited events` : 'Audited events by type'}
                </p>
                {!loading && chartData.length === 0 ? (
                    <p className="text-sm text-slate-500">No audited activity in this period.</p>
                ) : (
                    <ResponsiveContainer width="100%" height={260}>
                        <BarChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                            <XAxis dataKey="action" tick={{ fontSize: 10, fill: '#94a3b8' }} interval={0} />
                            <YAxis tick={{ fontSize: 12, fill: '#94a3b8' }} allowDecimals={false} />
                            <Tooltip
                                contentStyle={{ background: '#1e293b', border: 'none', borderRadius: '8px', color: '#f1f5f9', fontSize: '12px' }}
                                formatter={(count) => [count, 'Events']}
                            />
                            <Bar dataKey="count" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                        </BarChart>
                    </ResponsiveContainer>
                )}
            </Card>
        </div>
    );
};

export default SystemOverview;
