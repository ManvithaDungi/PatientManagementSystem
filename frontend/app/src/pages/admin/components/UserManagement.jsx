import React, { useState, useEffect } from 'react';
import { Search, Filter } from 'lucide-react';
import Card from '../../../components/common/Card';
import Badge from '../../../components/common/Badge';
import Button from '../../../components/common/Button';
import { Table, TableHead, TableBody, TableRow, TableHeader, TableCell } from '../../../components/common/Table';
import api from '../../../services/api';

const UserManagement = () => {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [loadError, setLoadError] = useState(null);
    const [actionMessage, setActionMessage] = useState(null); // { type, text }
    const [busyId, setBusyId] = useState(null);

    // Staff invites
    const [showInvite, setShowInvite] = useState(false);
    const [inviteEmail, setInviteEmail] = useState('');
    const [inviteRole, setInviteRole] = useState('DOCTOR');
    const [inviting, setInviting] = useState(false);
    const [inviteMessage, setInviteMessage] = useState(null); // { type, text }
    const [invites, setInvites] = useState([]);

    useEffect(() => {
        fetchUsers();
        fetchInvites();
    }, []);

    const fetchInvites = async () => {
        try {
            setInvites(await api.invites.list());
        } catch (err) {
            console.error('Failed to load invites', err);
        }
    };

    const handleInvite = async (e) => {
        e.preventDefault();
        setInviting(true);
        setInviteMessage(null);
        try {
            await api.invites.create(inviteEmail.trim(), inviteRole);
            setInviteMessage({ type: 'success', text: `Invitation sent to ${inviteEmail.trim()}.` });
            setInviteEmail('');
            fetchInvites();
        } catch (err) {
            setInviteMessage({ type: 'error', text: err.message || 'Could not send the invitation.' });
        } finally {
            setInviting(false);
        }
    };

    const handleRevoke = async (id) => {
        try {
            await api.invites.revoke(id);
            fetchInvites();
        } catch (err) {
            setInviteMessage({ type: 'error', text: err.message || 'Could not revoke the invitation.' });
        }
    };

    const fetchUsers = async () => {
        try {
            setLoading(true);
            setLoadError(null);
            const data = await api.admin.getAllStaff();
            setUsers(data);
        } catch (err) {
            console.error('Failed to load staff', err);
            setUsers([]);
            setLoadError('Unable to load staff members. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    const runAction = async (userId, successText, action) => {
        setBusyId(userId);
        setActionMessage(null);
        try {
            await action();
            setActionMessage({ type: 'success', text: successText });
            await fetchUsers();
        } catch (err) {
            setActionMessage({ type: 'error', text: err.message || 'The action could not be completed.' });
        } finally {
            setBusyId(null);
        }
    };

    const handleRoleChange = (user, newRole) => {
        if (!newRole || newRole === user.role) return;
        if (!window.confirm(`Change ${user.email} from ${user.role} to ${newRole}?`)) return;
        runAction(user.userId, `Role updated for ${user.email}.`, () => api.admin.updateStaffRole(user.userId, newRole));
    };

    const handleDisable = (user) => {
        if (!window.confirm(`Disable ${user.email}? They will be signed out and unable to log in.`)) return;
        runAction(user.userId, `${user.email} was disabled.`, () => api.admin.deleteStaff(user.userId));
    };

    const handleEnable = (user) =>
        runAction(user.userId, `${user.email} was enabled.`, () => api.admin.enableStaff(user.userId));

    const formatLastLogin = (value) => (value ? new Date(value).toLocaleString() : 'Never');

    const filteredUsers = users.filter(user =>
        user.email?.toLowerCase().includes(searchTerm.toLowerCase())
    );

    const getRoleBadgeVariant = (role) => {
        switch (role) {
            case 'ADMIN': return 'red';
            case 'DOCTOR': return 'blue';
            case 'NURSE': return 'green';
            case 'LAB_TECHNICIAN': return 'yellow';
            case 'PATIENT': return 'gray';
            default: return 'gray';
        }
    };

    const getStatusColor = (enabled) =>
        enabled ? 'text-admin-success bg-admin-success/10' : 'text-slate-500 bg-slate-100 dark:bg-slate-700';

    return (
        <Card className="border-t-4 border-t-admin-secondary">
            <div className="p-6 border-b border-gray-100 dark:border-slate-700 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h2 className="text-lg font-bold text-slate-800 dark:text-white">User Management</h2>
                    <p className="text-sm text-slate-500 dark:text-slate-400">Manage access and roles ({users.length} total users)</p>
                </div>

                <div className="flex items-center gap-3">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
                        <input
                            type="text"
                            placeholder="Search users..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="pl-10 pr-4 py-2 border border-slate-200 dark:border-slate-700 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-admin-primary dark:bg-slate-800 dark:text-white"
                        />
                    </div>
                    <Button variant="outline" className="flex items-center gap-2">
                        <Filter size={16} /> Filter
                    </Button>
                    <Button onClick={() => setShowInvite((v) => !v)}>
                        {showInvite ? 'Close' : 'Invite staff'}
                    </Button>
                </div>
            </div>

            {showInvite && (
                <div className="p-6 border-b border-gray-100 dark:border-slate-700 space-y-4">
                    <form onSubmit={handleInvite} className="flex flex-col sm:flex-row gap-3">
                        <input
                            type="email"
                            required
                            placeholder="staff.member@hospital.org"
                            value={inviteEmail}
                            onChange={(e) => setInviteEmail(e.target.value)}
                            className="flex-1 px-3 py-2 border border-slate-200 dark:border-slate-700 rounded-lg text-sm dark:bg-slate-800 dark:text-white"
                        />
                        <select
                            value={inviteRole}
                            onChange={(e) => setInviteRole(e.target.value)}
                            className="px-3 py-2 border border-slate-200 dark:border-slate-700 rounded-lg text-sm dark:bg-slate-800 dark:text-white"
                        >
                            <option value="DOCTOR">Doctor</option>
                            <option value="NURSE">Nurse</option>
                            <option value="LAB_TECHNICIAN">Lab Technician</option>
                            <option value="ADMIN">Administrator</option>
                        </select>
                        <Button type="submit" disabled={inviting}>
                            {inviting ? 'Sending...' : 'Send invitation'}
                        </Button>
                    </form>

                    {inviteMessage && (
                        <p className={`text-sm ${inviteMessage.type === 'success' ? 'text-green-600' : 'text-red-600'}`}>
                            {inviteMessage.text}
                        </p>
                    )}

                    {invites.length > 0 && (
                        <div>
                            <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200 mb-2">Pending invitations</h3>
                            <ul className="space-y-1">
                                {invites.map((inv) => (
                                    <li key={inv.id} className="flex items-center justify-between text-sm text-slate-600 dark:text-slate-300">
                                        <span>
                                            {inv.email} — {inv.role} ({inv.status.toLowerCase()}, expires {new Date(inv.expiresAt).toLocaleString()})
                                        </span>
                                        <button
                                            type="button"
                                            onClick={() => handleRevoke(inv.id)}
                                            className="text-xs text-red-600 hover:underline"
                                        >
                                            Revoke
                                        </button>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
            )}

            {actionMessage && (
                <p role={actionMessage.type === 'error' ? 'alert' : 'status'}
                    className={`px-6 pt-4 text-sm ${actionMessage.type === 'success' ? 'text-green-600' : 'text-red-600'}`}>
                    {actionMessage.text}
                </p>
            )}

            {loading && (
                <div className="p-6 text-center text-slate-500">
                    Loading users...
                </div>
            )}



            {!loading && loadError && (
                <div className="p-6 text-center text-red-600 text-sm">{loadError}</div>
            )}

            {!loading && !loadError && filteredUsers.length === 0 && (
                <div className="p-6 text-center text-slate-500">
                    {searchTerm ? 'No users found matching your search.' : 'No users found.'}
                </div>
            )}

            {!loading && filteredUsers.length > 0 && (
                <Table>
                    <TableHead>
                        <TableHeader>User</TableHeader>
                        <TableHeader>Role</TableHeader>
                        <TableHeader>Status</TableHeader>
                        <TableHeader>Last Login</TableHeader>
                        <TableHeader align="right">Actions</TableHeader>
                    </TableHead>
                    <TableBody>
                        {filteredUsers.map((user) => (
                            <TableRow key={user.userId} hover>
                                <TableCell>
                                    <div className="flex items-center gap-3">
                                        <div className="w-10 h-10 rounded-full bg-admin-primary/10 flex items-center justify-center text-admin-primary font-bold">
                                            {user.email?.charAt(0)?.toUpperCase()}
                                        </div>
                                        <div>
                                            <h3 className="text-sm font-medium text-slate-800 dark:text-white">{user.email || 'N/A'}</h3>
                                            <p className="text-xs text-slate-500 dark:text-slate-400">ID: {user.userId}</p>
                                        </div>
                                    </div>
                                </TableCell>
                                <TableCell>
                                    <Badge variant={getRoleBadgeVariant(user.role)} label={user.role || 'PATIENT'} />
                                </TableCell>
                                <TableCell>
                                    <span className={`text-xs font-medium px-2 py-1 rounded-full ${getStatusColor(user.enabled !== false)}`}>
                                        {user.enabled !== false ? 'Active' : 'Disabled'}
                                    </span>
                                </TableCell>
                                <TableCell>
                                    <span className="text-sm text-slate-500">{formatLastLogin(user.lastLoginAt)}</span>
                                </TableCell>
                                <TableCell align="right">
                                    <div className="flex items-center gap-2 justify-end">
                                        <select
                                            aria-label={`Role for ${user.email}`}
                                            value={user.role}
                                            disabled={busyId === user.userId}
                                            onChange={(e) => handleRoleChange(user, e.target.value)}
                                            className="px-2 py-1 border border-slate-200 dark:border-slate-700 rounded-lg text-xs dark:bg-slate-800 dark:text-white"
                                        >
                                            <option value="ADMIN">Administrator</option>
                                            <option value="DOCTOR">Doctor</option>
                                            <option value="NURSE">Nurse</option>
                                            <option value="LAB_TECHNICIAN">Lab Technician</option>
                                        </select>
                                        {user.enabled !== false ? (
                                            <button
                                                type="button"
                                                disabled={busyId === user.userId}
                                                onClick={() => handleDisable(user)}
                                                className="text-xs text-red-600 hover:underline"
                                            >
                                                Disable
                                            </button>
                                        ) : (
                                            <button
                                                type="button"
                                                disabled={busyId === user.userId}
                                                onClick={() => handleEnable(user)}
                                                className="text-xs text-green-700 hover:underline"
                                            >
                                                Enable
                                            </button>
                                        )}
                                    </div>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            )}
        </Card>
    );
};

export default UserManagement;
