import React, { useState, useEffect } from 'react';
import { Pill, RefreshCw, Clock, CheckCircle } from 'lucide-react';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Badge from '../../components/common/Badge';
import api from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';

const PatientPrescriptions = () => {
   const { user } = useAuth();
   const [prescriptions, setPrescriptions] = useState([]);
   const [isLoading, setIsLoading] = useState(false);
   const [error, setError] = useState(null);

   useEffect(() => {
      const fetchPrescriptions = async () => {
         if (!user?.userId) return;
         setIsLoading(true);
         setError(null);
         try {
            // Get patient profile first to obtain the correct profile ID
            const patientProfile = await api.patients.getMe();
            const profileId = patientProfile?.id;
            if (!profileId) {
               throw new Error('Patient profile not found');
            }
            
            const data = await api.prescriptions.getByPatient(profileId);
            setPrescriptions(data || []);
         } catch (err) {
            console.error('Failed to fetch prescriptions:', err);
            setError('Failed to load prescriptions. Please refresh the page.');
         } finally {
            setIsLoading(false);
         }
      };
      fetchPrescriptions();
   }, [user?.userId]);

   const handleRefill = (medName) => {
      alert(`Refill request sent for ${medName}. Your pharmacy will be notified.`);
   };

   const activeRx = prescriptions.filter(p => p.status === 'ACTIVE');
   const historyRx = prescriptions.filter(p => p.status !== 'ACTIVE');

   if (isLoading) {
      return <div className="p-6 text-center text-gray-500 dark:text-slate-400">Loading prescriptions...</div>;
   }

   return (
      <div className="space-y-4">
         <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">My Medications</h2>

         {error && (
            <Card className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
               <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
            </Card>
         )}

         {/* Active Medications */}
         <div className="space-y-2">
            <h3 className="font-bold text-gray-600 dark:text-slate-300 uppercase tracking-wider text-xs flex items-center">
               <CheckCircle className="w-3.5 h-3.5 mr-1.5 text-green-500 dark:text-green-400" />
               Current Prescriptions
            </h3>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
               {activeRx.map(rx => (
                  <Card key={rx.prescriptionId} className="p-3 border-l-4 border-green-500 space-y-2 hover:shadow-md transition relative overflow-hidden">
                     <div className="absolute top-0 right-0 p-2 opacity-5 pointer-events-none">
                        <Pill size={80} />
                     </div>

                     <div>
                        <h4 className="text-sm font-bold text-gray-800 dark:text-slate-100">{rx.medicationName}</h4>
                        <p className="text-green-600 dark:text-green-400 font-medium text-xs">{rx.dosage}</p>
                     </div>

                     <div className="bg-gray-50 dark:bg-slate-800/50 p-2 rounded space-y-1">
                        <div className="flex justify-between text-xs">
                           <span className="text-gray-500 dark:text-slate-400">Frequency:</span>
                           <span className="font-medium text-gray-800 dark:text-slate-100">{rx.frequency}</span>
                        </div>
                        <div className="flex justify-between text-xs">
                           <span className="text-gray-500 dark:text-slate-400">Refills remaining:</span>
                           <span className="font-medium text-gray-800 dark:text-slate-100">{rx.refillsRemaining ?? 0}</span>
                        </div>
                     </div>

                     <div className="pt-1">
                        <Button className="w-full justify-center text-xs py-1.5" onClick={() => handleRefill(rx.medicationName)}>
                           <RefreshCw className="w-3.5 h-3.5 mr-1" /> Request Refill
                        </Button>
                     </div>
                  </Card>
               ))}
               {activeRx.length === 0 && <p className="text-gray-500 dark:text-slate-400 italic text-sm">No active prescriptions.</p>}
            </div>
         </div>

         {/* History */}
         <div className="space-y-2 pt-3 text-opacity-80">
            <h3 className="font-bold text-gray-600 dark:text-slate-300 uppercase tracking-wider text-xs flex items-center">
               <Clock className="w-3.5 h-3.5 mr-1.5 text-gray-400 dark:text-slate-500" />
               Past Medications
            </h3>

            <div className="bg-white dark:bg-slate-800 rounded border border-gray-200 dark:border-slate-700 overflow-hidden">
               {historyRx.map((rx, idx) => (
                  <div key={rx.prescriptionId} className={`p-2.5 flex justify-between items-center ${idx !== historyRx.length - 1 ? 'border-b border-gray-100 dark:border-slate-700' : ''}`}>
                     <div>
                        <h4 className="font-bold text-gray-700 dark:text-slate-200 text-sm">{rx.medicationName}</h4>
                        <p className="text-xs text-gray-500 dark:text-slate-400">{rx.dosage}</p>
                     </div>
                     <Badge type="gray">Discontinued</Badge>
                  </div>
               ))}
               {historyRx.length === 0 && <div className="p-4 text-center text-gray-400 dark:text-slate-500 text-sm">No history found.</div>}
            </div>
         </div>
      </div>
   );
};

export default PatientPrescriptions;
