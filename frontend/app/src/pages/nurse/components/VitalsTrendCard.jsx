import React from 'react';
import { Download } from 'lucide-react';
import {
    ResponsiveContainer,
    LineChart,
    CartesianGrid,
    XAxis,
    YAxis,
    Tooltip as RechartsTooltip,
    Legend,
    ReferenceArea,
    Line,
} from 'recharts';
import Card from '../../../components/common/Card';
import IconButton from '../../../components/common/IconButton';

const rangeLabels = {
    '24h': 'Last 24 hrs',
    '48h': 'Last 48 hrs',
    '7d': 'Last 7 days',
    custom: 'Custom',
};

const VitalsTrendCard = ({
    timeRange,
    onTimeRangeChange,
    customRange,
    onCustomRangeChange,
    visibleVitals,
    onToggleVital,
    chartData,
    chartDomain,
    vitalLimits,
    onExport,
}) => (
    <Card className="p-3 space-y-3 border border-gray-100 dark:border-slate-700 shadow-soft dark:bg-slate-800">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-2">
            <div>
                <h3 className="text-sm font-bold text-gray-900 dark:text-slate-100">Vitals Trend (Recharts)</h3>
                <p className="text-xs text-gray-500 dark:text-slate-400">Multi-parameter view with normal range shading</p>
            </div>
            <div className="flex flex-wrap gap-1.5">
                {Object.keys(rangeLabels).map((range) => (
                    <button
                        key={range}
                        type="button"
                        className={`px-2.5 py-1 rounded-full text-xs font-semibold border transition ${timeRange === range ? 'bg-brand-medium text-white border-brand-medium' : 'border-gray-200 dark:border-slate-600 text-gray-600 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-700'}`}
                        onClick={() => onTimeRangeChange(range)}
                    >
                        {rangeLabels[range]}
                    </button>
                ))}
            </div>
        </div>

        {timeRange === 'custom' && (
            <div className="flex flex-wrap gap-2">
                <div>
                    <label className="block text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wide mb-0.5">Start</label>
                    <input
                        type="date"
                        className="rounded border border-gray-200 dark:border-slate-600 px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-brand-medium bg-white dark:bg-slate-700 text-gray-900 dark:text-slate-100"
                        value={customRange.start}
                        onChange={(event) => onCustomRangeChange({ ...customRange, start: event.target.value })}
                    />
                </div>
                <div>
                    <label className="block text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wide mb-0.5">End</label>
                    <input
                        type="date"
                        className="rounded border border-gray-200 dark:border-slate-600 px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-brand-medium bg-white dark:bg-slate-700 text-gray-900 dark:text-slate-100"
                        value={customRange.end}
                        onChange={(event) => onCustomRangeChange({ ...customRange, end: event.target.value })}
                    />
                </div>
            </div>
        )}

        <div className="flex flex-wrap gap-1.5 text-[10px] text-gray-500 dark:text-slate-400">
            {[{ key: 'bpSystolic', label: 'Systolic BP' }, { key: 'bpDiastolic', label: 'Diastolic BP' }, { key: 'heartRate', label: 'Heart Rate' }, { key: 'temperature', label: 'Temperature' }, { key: 'respiratoryRate', label: 'Resp Rate' }, { key: 'oxygenSaturation', label: 'SpO2' }].map((item) => (
                <button
                    key={item.key}
                    type="button"
                    className={`px-2.5 py-0.5 rounded-full border transition ${visibleVitals[item.key] ? 'bg-brand-light text-brand-deep border-brand-medium/40' : 'border-gray-200 dark:border-slate-600 text-gray-500 dark:text-slate-400 hover:bg-gray-50 dark:hover:bg-slate-700'}`}
                    onClick={() => onToggleVital(item.key)}
                >
                    {visibleVitals[item.key] ? 'Hide' : 'Show'} {item.label}
                </button>
            ))}
        </div>

        <div className="h-64">
            {chartData.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                        <XAxis dataKey="label" stroke="#94a3b8" tick={{ fontSize: 12 }} />
                        <YAxis domain={chartDomain} stroke="#94a3b8" tick={{ fontSize: 12 }} />
                        <RechartsTooltip
                            contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', padding: 12 }}
                            labelStyle={{ fontWeight: 600, color: '#0f172a' }}
                        />
                        <Legend />
                        {visibleVitals.bpSystolic && (
                            <ReferenceArea y1={vitalLimits.bp.normal.systolic[0]} y2={vitalLimits.bp.normal.systolic[1]} fill="#E3F2FD" fillOpacity={0.2} />
                        )}
                        {visibleVitals.heartRate && (
                            <ReferenceArea y1={vitalLimits.heartRate.normal[0]} y2={vitalLimits.heartRate.normal[1]} fill="#E8F5E9" fillOpacity={0.15} />
                        )}
                        {visibleVitals.temperature && (
                            <ReferenceArea y1={vitalLimits.temperature.normalF[0]} y2={vitalLimits.temperature.normalF[1]} fill="#FFF8E1" fillOpacity={0.18} />
                        )}
                        {visibleVitals.respiratoryRate && (
                            <ReferenceArea y1={vitalLimits.respiratoryRate.normal[0]} y2={vitalLimits.respiratoryRate.normal[1]} fill="#F1F5F9" fillOpacity={0.18} />
                        )}
                        {visibleVitals.oxygenSaturation && (
                            <ReferenceArea y1={vitalLimits.oxygenSaturation.normal[0]} y2={vitalLimits.oxygenSaturation.normal[1]} fill="#ECFDF5" fillOpacity={0.18} />
                        )}
                        {visibleVitals.bpSystolic && <Line type="monotone" dataKey="bpSystolic" name="Systolic BP" stroke="#1565C0" strokeWidth={2} dot={false} />}
                        {visibleVitals.bpDiastolic && <Line type="monotone" dataKey="bpDiastolic" name="Diastolic BP" stroke="#1E88E5" strokeWidth={2} dot={false} strokeDasharray="5 4" />}
                        {visibleVitals.heartRate && <Line type="monotone" dataKey="heartRate" name="Heart Rate" stroke="#F44336" strokeWidth={2} dot={false} />}
                        {visibleVitals.temperature && <Line type="monotone" dataKey="temperature" name="Temperature" stroke="#FB8C00" strokeWidth={2} dot={false} />}
                        {visibleVitals.respiratoryRate && <Line type="monotone" dataKey="respiratoryRate" name="Respiratory Rate" stroke="#43A047" strokeWidth={2} dot={false} />}
                        {visibleVitals.oxygenSaturation && <Line type="monotone" dataKey="oxygenSaturation" name="SpO2" stroke="#8E24AA" strokeWidth={2} dot={false} />}
                    </LineChart>
                </ResponsiveContainer>
            ) : (
                <div className="h-full flex items-center justify-center text-sm text-gray-500 dark:text-slate-400 bg-gray-50 dark:bg-slate-700 rounded-xl border border-dashed border-gray-200 dark:border-slate-600">
                    No vitals data for the selected range.
                </div>
            )}
        </div>

        <div className="flex justify-end">
            <IconButton 
               icon={Download} 
               label="Export Trend PDF" 
               variant="outline" 
               onClick={onExport}
            />
        </div>
    </Card>
);

export default VitalsTrendCard;
