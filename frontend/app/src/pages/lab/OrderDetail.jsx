import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, User, Activity, Calendar, FileText, CheckCircle, Upload } from 'lucide-react';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';
import api from '../../services/api';

const LabOrderDetail = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [order, setOrder] = useState(null);
    const [loading, setLoading] = useState(true);
    const [collecting, setCollecting] = useState(false);
    const [reportError, setReportError] = useState(null);

    useEffect(() => {
        const fetchOrder = async () => {
            try {
                const data = await api.labTechnician.getOrders(null);
                if (data && Array.isArray(data)) {
                    const found = data.find(o => String(o.testId) === String(id));
                    setOrder(found || null);
                }
            } catch (err) {
                console.error('Failed to fetch order:', err);
            } finally {
                setLoading(false);
            }
        };
        fetchOrder();
    }, [id]);

    const handleCollectSample = async () => {
        if (!order) return;
        try {
            setCollecting(true);
            const updated = await api.labTechnician.updateOrderStatus(order.testId, 'Collected');
            setOrder(updated);
        } catch (err) {
            console.error('Failed to update status:', err);
        } finally {
            setCollecting(false);
        }
    };

    // A plain <a href> to /api/files/{filename} can't carry the app's bearer token,
    // so the file must be fetched with an authenticated request and opened as a
    // local object URL instead (see FULL_STACK_INTEGRATION_AUDIT.md LA-3).
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

    const getStatusBadgeType = (status) => {
        switch (status) {
            case 'Completed': return 'green';
            case 'Collected': return 'blue';
            case 'Results Pending': return 'indigo';
            default: return 'yellow';
        }
    };

    if (loading) {
        return <div className="p-6 text-gray-500 dark:text-slate-400 text-sm">Loading order...</div>;
    }

    if (!order) {
        return <div className="p-6 text-gray-900 dark:text-slate-100">Order not found</div>;
    }

    return (
        <div className="max-w-4xl mx-auto space-y-3">
            <Button
                variant="ghost"
                onClick={() => navigate('/dashboard/lab/orders')}
                className="text-gray-500 dark:text-slate-400 hover:text-gray-800 dark:hover:text-slate-200 text-xs"
            >
                <ArrowLeft className="w-3.5 h-3.5 mr-1.5" /> Back to Orders
            </Button>

            <div className="flex justify-between items-start">
                <div>
                    <h1 className="text-lg font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        Order #{order.testId}
                        <Badge type={getStatusBadgeType(order.status)}>{order.status}</Badge>
                    </h1>
                    <p className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                        Ordered by {order.orderedByDoctor || 'Doctor'} on {order.orderedAt ? new Date(order.orderedAt).toLocaleDateString() : 'N/A'}
                    </p>
                </div>
                <div className="flex gap-2">
                    {order.status === 'Pending' && (
                        <Button onClick={handleCollectSample} disabled={collecting} className="bg-brand-medium">
                            <CheckCircle className="w-4 h-4 mr-2" /> {collecting ? 'Updating...' : 'Mark Sample Collected'}
                        </Button>
                    )}
                    {(order.status === 'Collected' || order.status === 'Results Pending') && (
                        <Button onClick={() => navigate('/dashboard/lab/upload')} className="bg-brand-medium">
                            <Upload className="w-4 h-4 mr-2" /> Upload Results
                        </Button>
                    )}
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div className="md:col-span-2 space-y-3">
                    <Card className="p-3 dark:bg-slate-800">
                        <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
                            <Activity className="w-4 h-4 mr-1.5 text-brand-medium" /> Test Details
                        </h3>
                        <div className="grid grid-cols-2 gap-3">
                            <div>
                                <label className="text-xs text-gray-500 dark:text-slate-400 block mb-0.5">Test Name</label>
                                <p className="text-sm font-medium text-gray-900 dark:text-slate-100">{order.testName || 'N/A'}</p>
                            </div>
                            <div>
                                <label className="text-xs text-gray-500 dark:text-slate-400 block mb-0.5">Category</label>
                                <Badge type={order.testCategory === 'Urgent' || order.testCategory === 'High' ? 'red' : 'gray'}>
                                    {order.testCategory || 'Standard'}
                                </Badge>
                            </div>
                            {order.unit && (
                                <div>
                                    <label className="text-xs text-gray-500 dark:text-slate-400 block mb-0.5">Unit</label>
                                    <p className="text-sm font-medium text-gray-900 dark:text-slate-100">{order.unit}</p>
                                </div>
                            )}
                            {order.referenceRange && (
                                <div>
                                    <label className="text-xs text-gray-500 dark:text-slate-400 block mb-0.5">Reference Range</label>
                                    <p className="text-sm font-medium text-gray-900 dark:text-slate-100">{order.referenceRange}</p>
                                </div>
                            )}
                            <div className="col-span-2">
                                <label className="text-xs text-gray-500 dark:text-slate-400 block mb-0.5">Remarks</label>
                                <p className="text-xs text-gray-700 dark:text-slate-300">{order.remarks || 'No remarks'}</p>
                            </div>
                            {order.resultValue && (
                                <div className="col-span-2">
                                    <label className="text-xs text-gray-500 dark:text-slate-400 block mb-0.5">Result Value</label>
                                    <p className="text-sm font-medium text-gray-900 dark:text-slate-100">{order.resultValue}</p>
                                </div>
                            )}
                        </div>
                    </Card>

                    <Card className="p-3 dark:bg-slate-800">
                        <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
                            <Calendar className="w-4 h-4 mr-1.5 text-brand-medium" /> Timeline
                        </h3>
                        <div className="space-y-2">
                            <div className="flex gap-2">
                                <div className="flex flex-col items-center">
                                    <div className="w-2.5 h-2.5 rounded-full bg-green-500"></div>
                                    <div className="w-0.5 h-full bg-gray-200 dark:bg-slate-600"></div>
                                </div>
                                <div>
                                    <p className="text-xs font-medium text-gray-900 dark:text-slate-100">Order Placed</p>
                                    <p className="text-[10px] text-gray-500 dark:text-slate-400">
                                        {order.orderedAt ? new Date(order.orderedAt).toLocaleString() : 'N/A'}
                                    </p>
                                </div>
                            </div>
                            {(order.status === 'Collected' || order.status === 'Results Pending' || order.status === 'Completed') && (
                                <div className="flex gap-2">
                                    <div className="flex flex-col items-center">
                                        <div className="w-2.5 h-2.5 rounded-full bg-blue-500"></div>
                                        <div className="w-0.5 h-full bg-gray-200 dark:bg-slate-600"></div>
                                    </div>
                                    <div>
                                        <p className="text-xs font-medium text-gray-900 dark:text-slate-100">Sample Collected</p>
                                    </div>
                                </div>
                            )}
                            {order.status === 'Completed' && (
                                <div className="flex gap-2">
                                    <div className="flex flex-col items-center">
                                        <div className="w-2.5 h-2.5 rounded-full bg-brand-deep"></div>
                                    </div>
                                    <div>
                                        <p className="text-xs font-medium text-gray-900 dark:text-slate-100">Results Uploaded</p>
                                        {order.fileUrl && (
                                            <div className="mt-1">
                                                <Button variant="outline" size="sm" className="text-[10px]" onClick={() => handleViewReport(order.fileUrl)}>
                                                    <FileText className="w-3 h-3 mr-1" /> View Report
                                                </Button>
                                                {reportError && <p className="text-[10px] text-red-600 dark:text-red-400 mt-1">{reportError}</p>}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>
                    </Card>
                </div>

                <div className="space-y-3">
                    <Card className="p-3 dark:bg-slate-800">
                        <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
                            <User className="w-4 h-4 mr-1.5 text-brand-medium" /> Patient Info
                        </h3>
                        <div className="space-y-2">
                            <div>
                                <label className="text-[10px] text-gray-500 dark:text-slate-400 uppercase font-semibold">Name</label>
                                <p className="text-sm font-medium text-gray-900 dark:text-slate-100">{order.patientName}</p>
                            </div>
                            <div>
                                <label className="text-[10px] text-gray-500 dark:text-slate-400 uppercase font-semibold">Gender</label>
                                <p className="text-sm font-medium text-gray-900 dark:text-slate-100">{order.gender || 'N/A'}</p>
                            </div>
                            <div>
                                <label className="text-[10px] text-gray-500 dark:text-slate-400 uppercase font-semibold">Patient ID</label>
                                <p className="text-sm font-medium text-gray-900 dark:text-slate-100">{order.profileId}</p>
                            </div>
                            <div className="p-2 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg text-[10px] text-yellow-800 dark:text-yellow-200 border border-yellow-200 dark:border-yellow-700">
                                <span className="font-bold block mb-0.5">Restricted Access</span>
                                Full medical history and unrelated diagnosis are hidden.
                            </div>
                        </div>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default LabOrderDetail;
