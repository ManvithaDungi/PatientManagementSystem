import React from 'react';
import { Clock, FileText } from 'lucide-react';
import Card from '../common/Card';
import Badge from '../common/Badge';

const MedicalHistoryList = ({ history }) => {
    if (!history || history.length === 0) {
        return (
            <Card className="p-8 text-center text-gray-500 dark:text-slate-400 border-dashed">
                <Clock className="w-12 h-12 mx-auto mb-2 text-gray-300 dark:text-slate-600" />
                <p>No medical history records found.</p>
            </Card>
        );
    }

    return (
        <div className="space-y-6">
            <div className="relative border-l-2 border-gray-200 dark:border-slate-700 ml-3 space-y-8 pl-6 py-2">
                {history.map((record) => (
                    <div key={record.recordId} className="relative">
                        <div className="absolute -left-[31px] bg-white dark:bg-slate-800 border-4 border-blue-100 dark:border-blue-900 rounded-full w-4 h-4 mt-1.5 box-content"></div>
                        <Card className="p-5 hover:shadow-md transition-shadow">
                            <div className="flex flex-col sm:flex-row sm:justify-between sm:items-start gap-2">
                                <div>
                                    <div className="flex items-center gap-2 mb-1">
                                        <h4 className="font-bold text-gray-800 dark:text-slate-100 text-lg">{record.diagnosis}</h4>
                                        <Badge type="blue">{record.recordDate ? new Date(record.recordDate).toLocaleDateString() : ''}</Badge>
                                    </div>
                                    {record.symptoms && (
                                        <p className="text-gray-600 dark:text-slate-400 leading-relaxed"><span className="font-medium">Symptoms:</span> {record.symptoms}</p>
                                    )}
                                    {record.treatmentProvided && (
                                        <p className="text-gray-600 dark:text-slate-400 leading-relaxed"><span className="font-medium">Treatment:</span> {record.treatmentProvided}</p>
                                    )}
                                    {record.notes && (
                                        <p className="text-gray-600 dark:text-slate-400 leading-relaxed"><span className="font-medium">Notes:</span> {record.notes}</p>
                                    )}
                                    <p className="text-xs text-gray-400 dark:text-slate-500 mt-1">By {record.doctorName}</p>
                                </div>
                                <div className="flex-shrink-0">
                                    <div className="bg-gray-50 dark:bg-slate-900 p-2 rounded-lg text-gray-400 dark:text-slate-500">
                                        <FileText size={20} />
                                    </div>
                                </div>
                            </div>
                        </Card>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default MedicalHistoryList;
