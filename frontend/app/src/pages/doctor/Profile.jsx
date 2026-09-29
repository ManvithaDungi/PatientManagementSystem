import React, { useState, useEffect } from 'react';
import { User, Mail, Phone, Clock, Shield } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import Badge from '../../components/common/Badge';

const Profile = () => {
    const { user } = useAuth();
    const [isEditing, setIsEditing] = useState(false);
    const [profile, setProfile] = useState(null);
    const [form, setForm] = useState({ firstName: '', lastName: '', specialty: '', contactNumber: '', department: '' });
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [saveError, setSaveError] = useState(null);

    const fetchProfile = async () => {
        setIsLoading(true);
        setError(null);
        try {
            // Resolved server-side via JWT identity — no ID-space guessing.
            const data = await api.doctors.getMe();
            setProfile(data);
            setForm({
                firstName: data.firstName || '',
                lastName: data.lastName || '',
                specialty: data.specialty || '',
                contactNumber: data.contactNumber || '',
                department: data.department || '',
            });
        } catch (err) {
            console.error('Failed to load profile:', err);
            setError('Unable to load your profile right now. Please try again.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchProfile();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleFieldChange = (field) => (e) => {
        setForm((prev) => ({ ...prev, [field]: e.target.value }));
    };

    const handleSave = async () => {
        setIsSaving(true);
        setSaveError(null);
        try {
            const updated = await api.doctors.updateMe({
                ...profile,
                firstName: form.firstName,
                lastName: form.lastName,
                specialty: form.specialty,
                contactNumber: form.contactNumber,
                department: form.department,
            });
            setProfile(updated);
            setIsEditing(false);
        } catch (err) {
            console.error('Failed to save profile:', err);
            setSaveError('Failed to save changes. Please try again.');
        } finally {
            setIsSaving(false);
        }
    };

    const handleCancel = () => {
        if (profile) {
            setForm({
                firstName: profile.firstName || '',
                lastName: profile.lastName || '',
                specialty: profile.specialty || '',
                contactNumber: profile.contactNumber || '',
                department: profile.department || '',
            });
        }
        setSaveError(null);
        setIsEditing(false);
    };

    const displayName = profile
        ? `${profile.firstName || ''} ${profile.lastName || ''}`.trim()
        : '';
    const email = profile?.email || user?.email || '';
    const specialty = profile?.specialty || '';
    const phone = profile?.contactNumber || '';
    const department = profile?.department || '';
    const initials = displayName ? displayName.charAt(0) : (email ? email.charAt(0).toUpperCase() : '?');

    if (isLoading) {
        return <div className="text-sm text-gray-500 dark:text-slate-400 p-4">Loading profile...</div>;
    }

    if (error) {
        return (
            <div className="p-4">
                <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 text-sm rounded p-3 flex items-center justify-between">
                    <span>{error}</span>
                    <Button variant="outline" className="text-xs" onClick={fetchProfile}>Retry</Button>
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-3">
            <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">Doctor Profile</h2>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
                {/* Profile Card */}
                <Card className="p-4 lg:col-span-1 text-center dark:bg-slate-800">
                    <div className="w-20 h-20 rounded-full bg-blue-100 dark:bg-blue-900/30 mx-auto flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-2xl mb-2">
                        {initials}
                    </div>
                    <h3 className="text-sm font-bold text-gray-900 dark:text-slate-100">{displayName || 'Doctor'}</h3>
                    <p className="text-xs text-blue-600 dark:text-blue-400 font-medium">
                        {specialty || 'No specialty on file'}{department ? ` — ${department}` : ''}
                    </p>

                    <div className="mt-3 flex justify-center space-x-1">
                        <Badge type="green">Active</Badge>
                    </div>

                    <div className="mt-3 pt-3 border-t dark:border-slate-700 text-left space-y-1.5">
                        <div className="flex items-center text-gray-600 dark:text-slate-400 text-xs">
                            <Mail className="w-3.5 h-3.5 mr-2" /> {email || 'No email on file'}
                        </div>
                        <div className="flex items-center text-gray-600 dark:text-slate-400 text-xs">
                            <Phone className="w-3.5 h-3.5 mr-2" /> {phone || 'No phone on file'}
                        </div>
                    </div>
                </Card>

                {/* Settings & Schedule */}
                <div className="lg:col-span-2 space-y-3">
                    <Card className="p-4 dark:bg-slate-800">
                        <div className="flex justify-between items-center mb-3">
                            <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 flex items-center">
                                <User className="w-4 h-4 mr-1.5 text-blue-500" />
                                Personal Information
                            </h3>
                            <Button variant="outline" className="text-xs" onClick={() => (isEditing ? handleCancel() : setIsEditing(true))}>
                                {isEditing ? 'Cancel' : 'Edit Details'}
                            </Button>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                            <Input label="First Name" value={form.firstName} onChange={handleFieldChange('firstName')} disabled={!isEditing} />
                            <Input label="Last Name" value={form.lastName} onChange={handleFieldChange('lastName')} disabled={!isEditing} />
                            <Input label="Specialty" value={form.specialty} onChange={handleFieldChange('specialty')} disabled={!isEditing} />
                            <Input label="Email" value={email} disabled />
                            <Input label="Phone" value={form.contactNumber} onChange={handleFieldChange('contactNumber')} disabled={!isEditing} />
                            <Input label="Department" value={form.department} onChange={handleFieldChange('department')} disabled={!isEditing} />
                        </div>

                        {saveError && <p className="text-xs text-red-600 dark:text-red-400 mt-2">{saveError}</p>}

                        {isEditing && (
                            <div className="mt-3 flex justify-end">
                                <Button className="text-sm" onClick={handleSave} disabled={isSaving}>
                                    {isSaving ? 'Saving...' : 'Save Changes'}
                                </Button>
                            </div>
                        )}
                    </Card>

                    <Card className="p-4 dark:bg-slate-800">
                        <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
                            <Clock className="w-4 h-4 mr-1.5 text-blue-500" />
                            Schedule
                        </h3>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm text-gray-700 dark:text-slate-300">
                            <div><span className="text-gray-500 dark:text-slate-400">Shift start:</span> {profile?.shiftStartTime || 'Not set'}</div>
                            <div><span className="text-gray-500 dark:text-slate-400">Shift end:</span> {profile?.shiftEndTime || 'Not set'}</div>
                            <div><span className="text-gray-500 dark:text-slate-400">Slot duration:</span> {profile?.slotDurationMinutes ? `${profile.slotDurationMinutes} min` : 'Not set'}</div>
                            <div><span className="text-gray-500 dark:text-slate-400">Working days:</span> {(profile?.workingDays || []).join(', ') || 'Not set'}</div>
                        </div>
                    </Card>

                    <Card className="p-4 dark:bg-slate-800">
                        <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
                            <Shield className="w-4 h-4 mr-1.5 text-blue-500" />
                            Security
                        </h3>
                        <Button variant="secondary" className="w-full justify-center text-sm" disabled title="Not yet implemented">Change Password</Button>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default Profile;
