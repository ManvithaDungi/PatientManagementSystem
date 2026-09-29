import React, { useState, useEffect } from 'react';
import { Upload, CheckCircle } from 'lucide-react';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import api from '../../services/api';

const UploadResults = () => {
    const [orders, setOrders] = useState([]);
    const [selectedOrder, setSelectedOrder] = useState('');
    const [file, setFile] = useState(null);
    const [testValues, setTestValues] = useState('');
    const [remarks, setRemarks] = useState('');
    const [status, setStatus] = useState('idle'); // idle, uploading, success, error
    const [errorMessage, setErrorMessage] = useState(null);

    useEffect(() => {
        const fetchOrders = async () => {
            try {
                const data = await api.labTechnician.getOrders(null);
                if (data && Array.isArray(data)) {
                    setOrders(data);
                } else {
                    setOrders([]);
                }
            } catch (err) {
                console.error('Failed to fetch pending orders:', err);
                setOrders([]);
            }
        };
        fetchOrders();
    }, []);

    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            setFile(e.target.files[0]);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMessage(null);

        // Require: order selection AND test values (test values are the main result)
        if (!selectedOrder || !testValues.trim()) {
            setStatus('error');
            return;
        }

        try {
            setStatus('uploading');

            // Upload the reference file (if any) first, then attach its stored
            // filename as fileUrl when submitting the result.
            let fileUrl = null;
            if (file) {
                fileUrl = await api.files.upload(file);
            }

            await api.labTechnician.uploadResults(
                selectedOrder,
                testValues.trim(),
                remarks.trim() || null,
                fileUrl
            );

            setStatus('success');

            // Reset form after success
            setTimeout(() => {
                setSelectedOrder('');
                setTestValues('');
                setRemarks('');
                setFile(null);
                setStatus('idle');
            }, 2000);
        } catch (err) {
            console.error('Upload failed:', err);
            setErrorMessage(err.message || 'Please select an order and enter the test result value.');
            setStatus('error');
        }
    };

    return (
        <div className="max-w-xl mx-auto space-y-4">
            <div>
                <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">Upload Lab Results</h2>
                <p className="text-xs text-gray-500 dark:text-slate-400">Attach files or enter manual results for lab orders.</p>
            </div>

            <Card className="p-4 dark:bg-slate-800">
                <form onSubmit={handleSubmit} className="space-y-3">
                    <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Select Lab Order</label>
                        <select
                            className="w-full p-2 text-sm border border-gray-200 dark:border-slate-600 rounded-lg focus:ring-2 focus:ring-brand-medium focus:outline-none bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                            value={selectedOrder}
                            onChange={(e) => setSelectedOrder(e.target.value)}
                            required
                        >
                            <option value="">-- Select Order --</option>
                            {orders.filter(o => o.status !== 'Completed').map(order => {
                                const patientName = order.patientName || (order.patient ? `${order.patient.firstName} ${order.patient.lastName}` : 'Unknown');
                                const orderId = order.testId || order.id;
                                const testType = order.testName || order.testType || 'Test';
                                return (
                                    <option key={orderId} value={orderId}>
                                        {orderId} - {patientName} ({testType})
                                    </option>
                                );
                            })}
                        </select>
                    </div>

                    <div className="border-t border-gray-100 dark:border-slate-700 pt-3 pb-3">
                        <p className="text-xs text-gray-500 dark:text-slate-400 mb-3">
                            💡 <span className="font-medium">Optional:</span> Attach a reference file (lab report scan) — it will be encrypted and stored. The system does not automatically parse files, so you must also enter the test results manually in the field below.
                        </p>
                    </div>

                    <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Reference Report File (Optional - PDF/Image)</label>
                        <div className="mt-1 flex justify-center px-4 pt-3 pb-4 border-2 border-gray-300 dark:border-slate-600 border-dashed rounded-lg hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors cursor-pointer relative">
                            <div className="space-y-1 text-center">
                                <Upload className="mx-auto h-8 w-8 text-gray-400 dark:text-slate-500" />
                                <div className="flex text-xs text-gray-600 dark:text-slate-400">
                                    <label htmlFor="file-upload" className="relative cursor-pointer bg-white dark:bg-slate-800 rounded-md font-medium text-brand-medium hover:text-brand-deep focus-within:outline-none">
                                        <span>Upload a file</span>
                                        <input id="file-upload" name="file-upload" type="file" className="sr-only" onChange={handleFileChange} accept=".pdf,.png,.jpg,.jpeg" />
                                    </label>
                                    <p className="pl-1">or drag and drop</p>
                                </div>
                                <p className="text-[10px] text-gray-500 dark:text-slate-500">PDF, PNG, JPG up to 10MB</p>
                            </div>
                            {file && (
                                <div className="absolute inset-0 bg-green-50/90 dark:bg-green-900/30 flex flex-col items-center justify-center rounded-lg">
                                    <CheckCircle className="text-green-600 dark:text-green-400 w-6 h-6 mb-1" />
                                    <p className="text-xs font-medium text-green-800 dark:text-green-200">{file.name}</p>
                                    <button type="button" onClick={() => setFile(null)} className="text-[10px] text-red-500 dark:text-red-400 mt-1 hover:underline">Remove</button>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="relative">
                        <div className="absolute inset-0 flex items-center" aria-hidden="true">
                            <div className="w-full border-t border-gray-300 dark:border-slate-600"></div>
                        </div>
                        <div className="relative flex justify-center">
                            <span className="px-2 bg-white dark:bg-slate-800 text-xs text-gray-500 dark:text-slate-400">OR enter manually below</span>
                        </div>
                    </div>

                    <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">
                            Test Result Value <span className="text-red-500">*</span>
                        </label>
                        <textarea
                            rows="3"
                            className="w-full p-2 text-sm border border-gray-200 dark:border-slate-600 rounded-lg focus:ring-2 focus:ring-brand-medium focus:outline-none bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100 dark:placeholder-slate-500"
                            placeholder="Enter test results, values, and reference ranges. This field is required."
                            value={testValues}
                            onChange={(e) => setTestValues(e.target.value)}
                            required
                        />
                    </div>

                    <div>
                        <label className="block text-xs font-medium text-gray-700 dark:text-slate-300 mb-1">Additional Remarks (Optional)</label>
                        <textarea
                            rows="2"
                            className="w-full p-2 text-sm border border-gray-200 dark:border-slate-600 rounded-lg focus:ring-2 focus:ring-brand-medium focus:outline-none bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100 dark:placeholder-slate-500"
                            placeholder="Add clinical notes, observations, or interpretation..."
                            value={remarks}
                            onChange={(e) => setRemarks(e.target.value)}
                        />
                    </div>

                    <div className="pt-2">
                        <Button
                            type="submit"
                            disabled={!selectedOrder || !testValues.trim() || status === 'uploading'}
                            className="w-full justify-center text-sm"
                        >
                            {status === 'uploading' ? 'Uploading...' : 'Submit Lab Results'}
                        </Button>
                    </div>

                    {status === 'error' && (
                        <div className="p-2.5 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 flex items-center text-xs">
                            <span className="mr-1.5">❌</span>
                            {errorMessage || 'Please select an order and enter the test result value.'}
                        </div>
                    )}

                    {status === 'success' && (
                        <div className="p-2.5 rounded-lg bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 flex items-center text-xs animate-fade-in">
                            <CheckCircle className="w-4 h-4 mr-1.5" />
                            Results uploaded successfully! Doctor has been notified.
                        </div>
                    )}
                </form>
            </Card>
        </div>
    );
};

export default UploadResults;
