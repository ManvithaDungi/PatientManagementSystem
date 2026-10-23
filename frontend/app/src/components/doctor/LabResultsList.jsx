import React, { useState } from 'react';
import { Activity, Download, FileText, Clock, CheckCircle, AlertTriangle } from 'lucide-react';
import Card from '../common/Card';
import Badge from '../common/Badge';
import Button from '../common/Button';
import LabTestModal from './LabTestModal';
import { isAbnormal, normalizeStatus, statusBadgeType } from '../../utils/labRange';

const LabResultsList = ({ labs, patientId, onAdd }) => {
    const [isModalOpen, setIsModalOpen] = useState(false);

    if (!labs || labs.length === 0) {
        return (
            <>
                <Card className="p-8 text-center text-gray-500 dark:text-slate-400 border-dashed">
                    <Activity className="w-12 h-12 mx-auto mb-2 text-gray-300 dark:text-slate-600" />
                    <p>No lab results found.</p>
                    {patientId && (
                        <div className="mt-4 flex justify-center">
                            <Button onClick={() => setIsModalOpen(true)} className="flex items-center text-sm">
                                <Activity className="w-4 h-4 mr-1" /> Order New Labs
                            </Button>
                        </div>
                    )}
                </Card>
                {patientId && (
                    <LabTestModal
                        isOpen={isModalOpen}
                        onClose={() => setIsModalOpen(false)}
                        patientId={patientId}
                        onAdd={(newLab) => { if (onAdd) onAdd(newLab); }}
                    />
                )}
            </>
        );
    }

    const getStatusIcon = (status) => {
        switch (normalizeStatus(status)) {
            case 'completed':
            case 'normal':
                return <CheckCircle size={16} className="text-green-500" />;
            case 'pending':
                return <Clock size={16} className="text-yellow-500" />;
            case 'collected':
                return <Clock size={16} className="text-blue-500" />;
            case 'results pending':
                return <Clock size={16} className="text-indigo-500" />;
            default:
                return <AlertTriangle size={16} className="text-gray-400" />;
        }
    };

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center bg-gray-50 dark:bg-slate-900 p-4 rounded-lg">
                <h3 className="font-bold text-gray-700 dark:text-slate-300">Lab Reports</h3>
                {patientId && (
                    <Button onClick={() => setIsModalOpen(true)} className="flex items-center text-sm">
                        <Activity className="w-4 h-4 mr-1" /> Order New Labs
                    </Button>
                )}
            </div>

            <div className="grid grid-cols-1 gap-4">
                {labs.map((lab, index) => {
                    const displayName = lab.name || lab.testName || 'Unknown Test';
                    const orderedDate = lab.orderedDate || (lab.orderedAt ? new Date(lab.orderedAt).toLocaleDateString() : 'N/A');
                    const fileUrl = lab.file || lab.fileUrl;
                    const isPending = normalizeStatus(lab.status) === 'pending';
                    const abnormal = isAbnormal(lab);
                    const statusLabel = lab.status || 'Unknown';
                    const badgeType = statusBadgeType(lab.status);
                    return (
                    <Card key={lab.id ?? lab.testId ?? index} className="p-4 flex flex-col md:flex-row justify-between items-start md:items-center group hover:border-blue-300 dark:hover:border-blue-600 transition-colors">
                        <div className="flex items-start gap-4">
                            <div className={`p-3 rounded-lg ${isPending ? 'bg-yellow-50 dark:bg-yellow-900/20 text-yellow-600 dark:text-yellow-400' : 'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400'}`}>
                                <FileText size={24} />
                            </div>
                            <div>
                                <h4 className="font-bold text-gray-800 dark:text-slate-100">{displayName}</h4>
                                {lab.testCategory && <p className="text-xs text-gray-500 dark:text-slate-400">{lab.testCategory}</p>}
                                <div className="flex items-center gap-3 text-sm text-gray-500 dark:text-slate-400 mt-1">
                                    <span className="flex items-center gap-1">
                                        <Clock size={14} /> Ordered: {orderedDate}
                                    </span>
                                </div>
                                <div className="mt-2 flex items-center gap-2">
                                    {getStatusIcon(lab.status)}
                                    <Badge type={badgeType}>
                                        {statusLabel}
                                    </Badge>
                                    {abnormal && (
                                        <Badge type="red">
                                            Abnormal (outside {lab.referenceRange}{lab.unit ? ` ${lab.unit}` : ''})
                                        </Badge>
                                    )}
                                </div>
                            </div>
                        </div>

                        <div className="mt-4 md:mt-0 flex items-center gap-3">
                            {fileUrl && (
                                <Button variant="outline" className="text-sm flex items-center gap-2">
                                    <Download size={16} /> Download
                                </Button>
                            )}
                            <Button variant="outline" className="text-sm">Details</Button>
                        </div>
                    </Card>
                    );
                })}
            </div>

            {patientId && (
                <LabTestModal
                    isOpen={isModalOpen}
                    onClose={() => setIsModalOpen(false)}
                    patientId={patientId}
                    onAdd={(newLab) => { if (onAdd) onAdd(newLab); }}
                />
            )}
        </div>
    );
};

export default LabResultsList;
