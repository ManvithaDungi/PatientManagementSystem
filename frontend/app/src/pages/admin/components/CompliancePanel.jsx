import React, { useState, useEffect } from 'react';
import { Shield, LogIn, ShieldAlert, AlertTriangle } from 'lucide-react';
import Card from '../../../components/common/Card';
import api from '../../../services/api';

const Metric = ({ title, value, icon: Icon, tone }) => (
    <div className="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-700/50 rounded-lg border border-slate-100 dark:border-slate-600">
        <div className="flex items-center gap-3">
            <div className={`p-2 rounded-lg ${tone === 'warn' ? 'bg-admin-warning/10 text-admin-warning' : 'bg-admin-success/10 text-admin-success'}`}>
                <Icon size={20} />
            </div>
            <h4 className="text-sm font-medium text-slate-700 dark:text-slate-200">{title}</h4>
        </div>
        <span className={`text-lg font-bold ${tone === 'warn' ? 'text-admin-warning' : 'text-admin-success'}`}>{value}</span>
    </div>
);

const readable = (action) => (action || '').replace(/_/g, ' ').toLowerCase();

/**
 * Security monitoring for the last 24 hours, derived entirely from the audit log.
 */
const CompliancePanel = () => {
    const [activity, setActivity] = useState(null);
    const [events, setEvents] = useState([]);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        Promise.all([
            api.admin.getUserActivity('24h'),
            api.admin.getSecurityEvents({ hours: 24, limit: 8 }),
        ])
            .then(([summary, recent]) => {
                if (cancelled) return;
                setActivity(summary);
                setEvents(recent || []);
            })
            .catch(() => {
                if (!cancelled) setError('Unable to load security monitoring data.');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => { cancelled = true; };
    }, []);

    const count = (v) => (loading || v === undefined ? '...' : v.toLocaleString());

    return (
        <Card className="p-6 border-t-4 border-t-admin-primary">
            <h3 className="text-lg font-bold text-slate-800 dark:text-white mb-6 flex items-center gap-2">
                <Shield className="text-admin-primary" size={20} />
                Security Monitoring (Last 24h)
            </h3>

            {error && <p className="text-sm text-red-600 mb-4">{error}</p>}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                <Metric title="Successful logins" value={count(activity?.successfulLogins)} icon={LogIn} />
                <Metric
                    title="Failed logins"
                    value={count(activity?.failedLogins)}
                    icon={ShieldAlert}
                    tone={activity?.failedLogins > 0 ? 'warn' : undefined}
                />
                <Metric
                    title="Security events"
                    value={count(activity?.securityEvents)}
                    icon={AlertTriangle}
                    tone={activity?.securityEvents > 0 ? 'warn' : undefined}
                />
            </div>

            <div className="bg-slate-50 dark:bg-slate-700/30 rounded-xl p-4 border border-slate-100 dark:border-slate-600">
                <h4 className="text-sm font-bold text-slate-700 dark:text-slate-300 mb-3">Recent security events</h4>
                {!loading && !error && events.length === 0 && (
                    <p className="text-sm text-slate-500">No security events in the last 24 hours.</p>
                )}
                <ul className="space-y-2">
                    {events.map((event) => (
                        <li key={event.id} className="flex items-start justify-between gap-3 text-sm">
                            <span className="text-slate-700 dark:text-slate-200">
                                <span className="font-medium capitalize">{readable(event.action)}</span>
                                <span className="text-slate-500"> &middot; {event.email}</span>
                                {event.ipAddress && <span className="font-mono text-xs text-slate-400"> ({event.ipAddress})</span>}
                            </span>
                            <span className="text-xs text-slate-400 whitespace-nowrap">
                                {new Date(event.timestamp).toLocaleString()}
                            </span>
                        </li>
                    ))}
                </ul>
            </div>
        </Card>
    );
};

export default CompliancePanel;
