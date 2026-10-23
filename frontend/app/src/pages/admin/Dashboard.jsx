import React, { useState } from 'react';
import { Users, AlertCircle, FileText, LayoutDashboard, UserCheck } from 'lucide-react';

import SystemOverview from './components/SystemOverview';
import UserManagement from './components/UserManagement';
import CareTeamManagement from './components/CareTeamManagement';
import CompliancePanel from './components/CompliancePanel';
import AuditLogs from './components/AuditLogs';
import SystemHealth from './components/SystemHealth';
import AppointmentApprovalQueue from '../../components/admin/AppointmentApprovalQueue';

const AdminDashboard = () => {
   const [activeTab, setActiveTab] = useState('overview');

   const renderContent = () => {
      switch (activeTab) {
         case 'overview':
            return (
               <div className="space-y-6 animate-fade-in">
                  <SystemOverview />
                  <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
                     <div className="xl:col-span-2">
                        <CompliancePanel />
                     </div>
                     <div>
                        <SystemHealth />
                     </div>
                  </div>
               </div>
            );
         case 'appointments':
            return <div className="animate-fade-in"><AppointmentApprovalQueue /></div>;
         case 'users':
            return <div className="animate-fade-in"><UserManagement /></div>;
         case 'careteams':
            return <div className="animate-fade-in"><CareTeamManagement /></div>;
         case 'audit':
            return <div className="animate-fade-in"><AuditLogs /></div>;
         default:
            return <SystemOverview />;
      }
   };

   const TabButton = ({ id, label, icon: Icon }) => (
      <button
         onClick={() => setActiveTab(id)}
         className={`
                flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-all duration-200
                ${activeTab === id
               ? 'border-admin-primary text-admin-primary'
               : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400 dark:hover:text-slate-200'
            }
            `}
      >
         <Icon size={18} />
         {label}
      </button>
   );

   return (
      <div className="space-y-6 min-h-screen bg-slate-50/50 dark:bg-slate-900/50 pb-10">
         {/* Header Section */}
         <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
               <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">Admin Console</h1>
               <p className="text-slate-500 dark:text-slate-400">Secure System Monitoring & Compliance Hub</p>
            </div>
         </div>

         {/* Navigation Tabs */}
         <div className="border-b border-slate-200 dark:border-slate-700 overflow-x-auto">
            <div className="flex space-x-2">
               <TabButton id="overview" label="Overview" icon={LayoutDashboard} />
               <TabButton id="appointments" label="Appointment Approvals" icon={AlertCircle} />
               <TabButton id="users" label="User Management" icon={Users} />
               <TabButton id="careteams" label="Care Teams" icon={UserCheck} />
               <TabButton id="audit" label="Audit Logs" icon={FileText} />
            </div>
         </div>

         {/* Content Area */}
         <div className="mt-6">
            {renderContent()}
         </div>
      </div>
   );
};

export default AdminDashboard;