import React, { useState, useEffect, useCallback } from 'react';
import { Server, Database, Activity, Cpu, RefreshCw } from 'lucide-react';
import Card from '../../../components/common/Card';
import api from '../../../services/api';

const HealthMetric = ({ label, value, ok, icon: Icon }) => (
    <div className="flex items-center justify-between p-3 bg-slate-50 dark:bg-slate-700/50 rounded-lg">
        <div className="flex items-center gap-3">
            <Icon size={18} className="text-slate-400" />
            <span className="text-sm font-medium text-slate-600 dark:text-slate-300">{label}</span>
        </div>
        <div className="flex items-center gap-2">
            <span className="font-bold text-slate-800 dark:text-white">{value}</span>
            <div className={`w-2 h-2 rounded-full ${ok ? 'bg-admin-success' : 'bg-admin-warning'}`}></div>
        </div>
    </div>
);

const Gauge = ({ label, percent, barClass }) => (
    <div className="space-y-2">
        <div className="flex justify-between text-sm">
            <span className="text-slate-500">{label}</span>
            <span className="font-medium text-slate-700 dark:text-slate-200">
                {percent === null || percent === undefined ? 'n/a' : `${percent}%`}
            </span>
        </div>
        <div className="h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
            <div className={`h-full ${barClass}`} style={{ width: `${Math.min(Math.max(percent || 0, 0), 100)}%` }}></div>
        </div>
    </div>
);

const formatUptime = (seconds) => {
    if (seconds === null || seconds === undefined) return 'n/a';
    const d = Math.floor(seconds / 86400);
    const h = Math.floor((seconds % 86400) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m}m` : `${m}m`;
};

const STATUS_STYLE = {
    UP: { label: 'Operational', cls: 'bg-admin-success/10 text-admin-success' },
    DEGRADED: { label: 'Degraded', cls: 'bg-admin-warning/10 text-admin-warning' },
    DOWN: { label: 'Down', cls: 'bg-admin-danger/10 text-admin-danger' },
};

const SystemHealth = () => {
    const [health, setHealth] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(true);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setHealth(await api.admin.getSystemHealth());
            setError(null);
        } catch (err) {
            setHealth(null);
            setError('Unable to read system health.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        load();
        const id = setInterval(load, 30000);
        return () => clearInterval(id);
    }, [load]);

    const badge = STATUS_STYLE[health?.status];

    return (
        <Card className="p-6 border-t-4 border-t-admin-success">
            <div className="flex items-center justify-between mb-6">
                <h3 className="text-lg font-bold text-slate-800 dark:text-white flex items-center gap-2">
                    <Activity className="text-admin-success" size={20} />
                    System Health
                </h3>
                <div className="flex items-center gap-2">
                    {badge && (
                        <span className={`px-2 py-1 text-xs font-bold rounded-full ${badge.cls}`}>{badge.label}</span>
                    )}
                    <button
                        type="button"
                        onClick={load}
                        aria-label="Refresh system health"
                        className="text-slate-400 hover:text-slate-600"
                    >
                        <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
                    </button>
                </div>
            </div>

            {error && <p className="text-sm text-red-600">{error}</p>}
            {!error && !health && <p className="text-sm text-slate-500">Checking...</p>}

            {health && (
                <div className="space-y-6">
                    <Gauge label="CPU Load" percent={health.cpuLoadPercent} barClass="bg-admin-success" />
                    <Gauge label="Memory Usage" percent={health.memoryUsedPercent} barClass="bg-admin-primary" />

                    <div className="space-y-3 pt-4 border-t border-slate-100 dark:border-slate-700">
                        <HealthMetric
                            label="Database"
                            value={health.database?.status === 'UP' ? `Connected (${health.database.responseMillis}ms)` : 'Down'}
                            ok={health.database?.status === 'UP'}
                            icon={Database}
                        />
                        <HealthMetric
                            label="Redis"
                            value={health.redis?.status === 'UP' ? 'Connected' : 'Down'}
                            ok={health.redis?.status === 'UP'}
                            icon={Server}
                        />
                        <HealthMetric
                            label="API Uptime"
                            value={formatUptime(health.uptimeSeconds)}
                            ok
                            icon={Cpu}
                        />
                    </div>
                </div>
            )}
        </Card>
    );
};

export default SystemHealth;
