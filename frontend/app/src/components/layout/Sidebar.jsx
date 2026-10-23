import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
    Home, Users, FileText, LogOut, Activity, Calendar, Shield,
    Upload, LayoutDashboard, Clock, X, Pill,
    ChevronLeft, ChevronRight, CheckSquare
} from 'lucide-react';

const Sidebar = ({ role, userName, isOpen, setIsOpen, isCollapsed, toggleCollapse, onLogout }) => {
    const navigate = useNavigate();
    const location = useLocation();

    // Helper to determine if a route is active
    const isRouteActive = (path) => {
        if (path === `/dashboard/${role}` && location.pathname === path) return true;
        if (path !== `/dashboard/${role}` && location.pathname.startsWith(path)) return true;
        return false;
    };

    const getNavItems = (role) => {
        const common = [
            { label: 'Overview', icon: Home, path: `/dashboard/${role}` },
        ];

        switch (role) {
            case 'doctor':
                return [
                    { icon: LayoutDashboard, label: 'Dashboard', path: '/dashboard/doctor' },
                    { icon: Users, label: 'Patients', path: '/dashboard/doctor/patients' },
                    { icon: Calendar, label: 'Appointments', path: '/dashboard/doctor/appointments' },
                    { icon: Activity, label: 'Lab Results', path: '/dashboard/doctor/labs' },
                    { icon: Pill, label: 'Prescriptions', path: '/dashboard/doctor/prescriptions' },
                ];
            case 'patient':
                return [
                    { icon: LayoutDashboard, label: 'Overview', path: '/dashboard/patient' },
                    { icon: FileText, label: 'Medical History', path: '/dashboard/patient/history' },
                    { icon: Calendar, label: 'Appointments', path: '/dashboard/patient/appointments' },
                    { icon: Activity, label: 'Lab Results', path: '/dashboard/patient/labs' },
                    { icon: Pill, label: 'Medications', path: '/dashboard/patient/prescriptions' },
                    { icon: Shield, label: 'Privacy & Consents', path: '/dashboard/patient/consents' },
                ];
            case 'nurse':
                return [
                    { label: 'Dashboard', icon: LayoutDashboard, path: '/dashboard/nurse' },
                    { label: 'My Patients', icon: Users, path: '/dashboard/nurse/patients' },
                    { label: 'Tasks', icon: CheckSquare, path: '/dashboard/nurse/tasks' },
                    { label: 'Shift Notes', icon: FileText, path: '/dashboard/nurse/shift-notes' },
                    { label: 'Profile', icon: Users, path: '/dashboard/nurse/profile' },
                ];
            case 'lab':
                return [
                    ...common,
                    { label: 'Upload Results', icon: Upload, path: `/dashboard/${role}/upload` },
                    { label: 'Orders', icon: FileText, path: `/dashboard/${role}/orders` },
                    { label: 'History', icon: Clock, path: `/dashboard/${role}/history` },
                ];
            case 'admin':
                return [
                    { label: 'Dashboard', icon: LayoutDashboard, path: '/dashboard/admin' },
                    { label: 'Profile', icon: Users, path: '/dashboard/admin/profile' },
                ];
            default:
                return common;
        }
    };

    const navItems = getNavItems(role);

    return (
        <>
            {/* Mobile Sidebar Overlay */}
            {isOpen && (
                <div
                    className="fixed inset-0 z-20 bg-black/40 dark:bg-black/60 lg:hidden transition-opacity backdrop-blur-sm"
                    onClick={() => setIsOpen(false)}
                />
            )}

            {/* Sidebar Container */}
            <aside
                className={`
                    fixed inset-y-0 left-0 z-30 bg-white dark:bg-slate-800 border-r border-gray-200 dark:border-slate-700 
                    transform transition-all duration-300 ease-in-out
                    lg:static
                    ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
                    ${isCollapsed ? 'w-20' : 'w-64'}
                `}
            >
                <div className="flex flex-col h-full">
                    {/* Header */}
                    <div className={`flex items-center h-16 px-4 border-b border-gray-100 dark:border-slate-700 ${isCollapsed ? 'justify-center' : 'justify-between'}`}>
                        <div className="flex items-center gap-3 overflow-hidden">
                            <div className="flex-shrink-0 h-10 w-10">
                                <img src="/calyx-logo.png" alt="CALYX" className="h-full w-full object-contain" />
                            </div>
                            {!isCollapsed && (
                                <div className="flex flex-col min-w-0">
                                    <h1 className="text-sm font-bold text-blue-600 dark:text-blue-400 tracking-tight whitespace-nowrap">
                                        CALYX
                                    </h1>
                                    <p className="text-xs text-gray-600 dark:text-slate-400 whitespace-nowrap">Healthcare</p>
                                </div>
                            )}
                        </div>



                        {/* Desktop Expand Toggle (When Collapsed - Centered below logo conceptually, but here typically we just toggle) 
                             Actually, if collapsed, the header justifies center, so the logo is there. 
                             We need a way to un-collapse. Usually strictly clicking the toggle.
                             Let's place the toggle strictly to the right if open, or maybe overlay?
                             The design request: "collapse sidebar put a small close sidebar type arrow at top right... removing the collapse sidebar to close it"
                             
                             If collapsed, we need a button to open it. 
                         */}
                        {isCollapsed && (
                            <button
                                onClick={toggleCollapse}
                                className="hidden lg:flex absolute -right-3 top-6 bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-full p-1 shadow-sm text-gray-500 hover:text-gray-900 dark:text-slate-400 dark:hover:text-white"
                            >
                                <ChevronRight size={14} />
                            </button>
                        )}


                        {/* Mobile Close */}
                        <button
                            onClick={() => setIsOpen(false)}
                            className="lg:hidden text-gray-400 hover:text-gray-600 dark:hover:text-slate-200 transition-colors"
                        >
                            <X size={20} />
                        </button>
                    </div>

                    {/* Navigation */}
                    <nav className="flex-1 px-3 py-6 space-y-1 overflow-y-auto custom-scrollbar overflow-x-hidden">
                        {!isCollapsed && (
                            <div className="px-3 mb-3 flex items-center justify-between group">
                                <p className="text-xs font-bold text-gray-400 dark:text-slate-500 uppercase tracking-wider">
                                    Menu
                                </p>
                                <button
                                    onClick={toggleCollapse}
                                    className="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-slate-200 hover:bg-gray-100 dark:hover:bg-slate-700/50 rounded-lg transition-colors"
                                    title="Collapse Sidebar"
                                >
                                    <ChevronLeft size={16} />
                                </button>
                            </div>
                        )}

                        {navItems.map((item) => {
                            const Icon = item.icon;
                            const isActive = isRouteActive(item.path);

                            return (
                                <button
                                    key={item.path}
                                    onClick={() => navigate(item.path)}
                                    title={isCollapsed ? item.label : ''}
                                    className={`
                                        relative flex items-center w-full p-3 rounded-xl transition-all duration-200 group
                                        ${isActive
                                            ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 shadow-sm'
                                            : 'text-gray-600 dark:text-slate-400 hover:bg-gray-50 dark:hover:bg-slate-700/50 hover:text-gray-900 dark:hover:text-slate-200'
                                        }
                                        ${isCollapsed ? 'justify-center' : ''}
                                    `}
                                >
                                    {isActive && (
                                        <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-8 bg-blue-500 rounded-r-full" />
                                    )}

                                    <Icon
                                        className={`
                                            w-5 h-5 transition-transform duration-200
                                            ${isActive ? 'scale-110' : 'group-hover:scale-110'}
                                            ${!isCollapsed && 'mr-3'}
                                        `}
                                    />

                                    {!isCollapsed && (
                                        <span className="font-medium whitespace-nowrap">
                                            {item.label}
                                        </span>
                                    )}

                                    {/* Hover Tooltip for Collapsed State */}
                                    {isCollapsed && (
                                        <div className="absolute left-full ml-4 px-2 py-1 bg-gray-900 dark:bg-slate-700 text-white text-xs rounded opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-50 shadow-lg">
                                            {item.label}
                                            <div className="absolute top-1/2 -left-1 -translate-y-1/2 w-2 h-2 bg-gray-900 dark:bg-slate-700 rotate-45" />
                                        </div>
                                    )}
                                </button>
                            );
                        })}
                    </nav>

                    {/* Footer / User Profile */}
                    <div className="p-4 border-t border-gray-100 dark:border-slate-700">
                        {/* Profile Section with Shadow */}
                        <div
                            onClick={() => navigate(`/dashboard/${role}/profile`)}
                            className={`
                                flex items-center p-2 rounded-xl cursor-pointer hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors shadow-sm ring-1 ring-gray-100 dark:ring-slate-700
                                ${isCollapsed ? 'justify-center' : ''}
                            `}
                        >
                            <div className="relative">
                                <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-blue-500 to-blue-400 flex items-center justify-center text-white text-sm font-bold shadow-md ring-2 ring-white dark:ring-slate-800">
                                    {userName.charAt(0)}
                                </div>
                                <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 border-2 border-white dark:border-slate-800 rounded-full"></div>
                            </div>

                            {!isCollapsed && (
                                <div className="ml-3 flex-1 min-w-0 overflow-hidden">
                                    <p className="text-sm font-semibold text-gray-900 dark:text-white truncate">
                                        {userName}
                                    </p>
                                    <p className="text-xs text-gray-500 dark:text-slate-400 capitalize truncate">
                                        {role}
                                    </p>
                                </div>
                            )}
                        </div>

                        {!isCollapsed && (
                            <button
                                onClick={onLogout}
                                className="flex items-center justify-center w-full mt-3 px-4 py-2.5 text-sm font-medium text-red-600 bg-red-50 dark:bg-red-900/10 dark:text-red-400 rounded-lg hover:bg-red-100 dark:hover:bg-red-900/20 transition-all active:scale-95"
                            >
                                <LogOut className="w-4 h-4 mr-2" />
                                Sign Out
                            </button>
                        )}
                        {isCollapsed && (
                            <button
                                onClick={onLogout}
                                title="Sign Out"
                                className="flex items-center justify-center w-full mt-3 p-2.5 text-red-600 bg-red-50 dark:bg-red-900/10 dark:text-red-400 rounded-lg hover:bg-red-100 dark:hover:bg-red-900/20 transition-all active:scale-95"
                            >
                                <LogOut className="w-5 h-5" />
                            </button>
                        )}
                    </div>
                </div>
            </aside>
        </>
    );
};

export default Sidebar;
