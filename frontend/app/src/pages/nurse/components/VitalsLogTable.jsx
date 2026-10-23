import React from 'react';
import { Search, Printer, Download, AlertTriangle } from 'lucide-react';
import Card from '../../../components/common/Card';
import IconButton from '../../../components/common/IconButton';

const VitalsLogTable = ({
    historySearch,
    onSearch,
    historyRange,
    onHistoryRangeChange,
    filteredVitalsLog,
    formatTimestamp,
    parseBpString,
    classifyBp,
    classifyValue,
    getStatusClasses,
    defaultRecorder,
    onPrint,
    onExport,
}) => (
    <Card className="p-3 space-y-3 border border-gray-100 dark:border-slate-700 shadow-soft dark:bg-slate-800">
        <div className="flex flex-col xl:flex-row xl:items-center xl:justify-between gap-2">
            <div>
                <h3 className="text-sm font-bold text-gray-900 dark:text-slate-100">Time-Stamped Vitals Log</h3>
                <p className="text-xs text-gray-500 dark:text-slate-400">Complete record of submissions with color-coded severity</p>
            </div>
            <div className="flex flex-wrap gap-2 items-center">
                <div className="relative">
                    <Search className="w-3.5 h-3.5 text-gray-400 dark:text-slate-500 absolute left-2.5 top-1/2 -translate-y-1/2" aria-hidden="true" />
                    <input
                        type="text"
                        placeholder="Search vitals..."
                        value={historySearch}
                        onChange={(event) => onSearch(event.target.value)}
                        className="pl-8 pr-3 py-1.5 rounded-full border border-gray-200 dark:border-slate-600 text-xs focus:outline-none focus:ring-2 focus:ring-brand-medium bg-white dark:bg-slate-700 text-gray-900 dark:text-slate-100 dark:placeholder-slate-400"
                    />
                </div>
                <div className="flex gap-2 text-xs text-gray-500 dark:text-slate-400">
                    <div>
                        <label className="block text-[10px] font-semibold uppercase tracking-wide mb-1">Start</label>
                        <input
                            type="date"
                            className="rounded-lg border border-gray-200 dark:border-slate-600 px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-brand-medium bg-white dark:bg-slate-700 text-gray-900 dark:text-slate-100"
                            value={historyRange.start}
                            onChange={(event) => onHistoryRangeChange({ ...historyRange, start: event.target.value })}
                        />
                    </div>
                    <div>
                        <label className="block text-[10px] font-semibold uppercase tracking-wide mb-1">End</label>
                        <input
                            type="date"
                            className="rounded-lg border border-gray-200 dark:border-slate-600 px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-brand-medium bg-white dark:bg-slate-700 text-gray-900 dark:text-slate-100"
                            value={historyRange.end}
                            onChange={(event) => onHistoryRangeChange({ ...historyRange, end: event.target.value })}
                        />
                    </div>
                </div>
                <IconButton 
                   icon={Printer} 
                   label="Print" 
                   variant="outline" 
                   size="sm" 
                   onClick={onPrint}
                />
                <IconButton 
                   icon={Download} 
                   label="Export" 
                   variant="outline" 
                   size="sm" 
                   onClick={onExport}
                />
            </div>
        </div>

        <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100 dark:divide-slate-700">
                <thead className="bg-gray-50 dark:bg-slate-700">
                    <tr>
                        <th className="px-3 py-2 text-left text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wider">Timestamp</th>
                        <th className="px-3 py-2 text-left text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wider">BP</th>
                        <th className="px-3 py-2 text-left text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wider">HR</th>
                        <th className="px-3 py-2 text-left text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wider">Temp</th>
                        <th className="px-3 py-2 text-left text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wider">RR</th>
                        <th className="px-3 py-2 text-left text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wider">SpO2</th>
                        <th className="px-3 py-2 text-left text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wider">Pain</th>
                        <th className="px-3 py-2 text-left text-[10px] font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wider">Recorded By</th>
                    </tr>
                </thead>
                <tbody className="bg-white dark:bg-slate-800 divide-y divide-gray-100 dark:divide-slate-700">
                    {filteredVitalsLog.length > 0 ? (
                        filteredVitalsLog.map((entry) => {
                            const bpParsed = parseBpString(entry.bp);
                            const bpStatus = classifyBp(bpParsed.systolic, bpParsed.diastolic);
                            const hrStatus = classifyValue('heartRate', entry.hr);
                            const tempStatus = classifyValue('temperature', entry.temp);
                            const rrStatus = classifyValue('respiratoryRate', entry.rr);
                            const spo2Status = classifyValue('oxygenSaturation', entry.spo2);
                            const painStatus = classifyValue('painLevel', entry.pain);
                            const badgeClass = (status) => `${getStatusClasses(status).bg} ${getStatusClasses(status).text} px-2 py-0.5 rounded-full text-[10px] font-semibold inline-flex items-center gap-0.5`;

                            return (
                                <tr key={entry.timestamp} className="hover:bg-gray-50/70 dark:hover:bg-slate-700/50 transition-colors">
                                    <td className="px-3 py-2 text-xs text-gray-700 dark:text-slate-300 whitespace-nowrap">{formatTimestamp(entry.timestamp)}</td>
                                    <td className="px-3 py-2 text-xs">
                                        <span className={badgeClass(bpStatus)}>{entry.bp}</span>
                                    </td>
                                    <td className="px-3 py-2 text-xs">
                                        <span className={badgeClass(hrStatus)}>{entry.hr} bpm</span>
                                    </td>
                                    <td className="px-3 py-2 text-xs">
                                        <span className={badgeClass(tempStatus)}>{entry.temp} °F</span>
                                    </td>
                                    <td className="px-3 py-2 text-xs">
                                        <span className={badgeClass(rrStatus)}>{entry.rr}</span>
                                    </td>
                                    <td className="px-3 py-2 text-xs">
                                        <span className={badgeClass(spo2Status)}>{entry.spo2}%</span>
                                    </td>
                                    <td className="px-3 py-2 text-xs">
                                        <span className={badgeClass(painStatus)}>
                                            {entry.pain}
                                            {painStatus !== 'normal' && <AlertTriangle className="w-2.5 h-2.5" aria-hidden="true" />}
                                        </span>
                                    </td>
                                    <td className="px-3 py-2 text-xs text-gray-600 dark:text-slate-400 whitespace-nowrap">{entry.recordedBy || defaultRecorder}</td>
                                </tr>
                            );
                        })
                    ) : (
                        <tr>
                            <td colSpan={8} className="px-3 py-6 text-center text-xs text-gray-500 dark:text-slate-400">
                                No vitals found for the selected filters.
                            </td>
                        </tr>
                    )}
                </tbody>
            </table>
        </div>

        <p className="text-[10px] text-gray-400 dark:text-slate-500">TODO: Persist log entries and filters via backend APIs when available.</p>
    </Card>
);

export default VitalsLogTable;
