import React, { useEffect, useState } from 'react';
import { User, Mail, Phone, Shield, Activity, Calendar, Stethoscope, FileText } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import Badge from '../../components/common/Badge';
import api from '../../services/api';

const PatientProfile = () => {
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [doctorName, setDoctorName] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [isEditing, setIsEditing] = useState(false);
  const [editData, setEditData] = useState({ contactNumber: '', address: '', dateOfBirth: '' });
  const [saveError, setSaveError] = useState(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const fetchProfile = async () => {
      if (!user?.userId) return;
      setIsLoading(true);
      setError(null);
      try {
        const data = await api.patients.getMe();
        setProfile(data);
        setEditData({ contactNumber: data.contactNumber || '', address: data.address || '', dateOfBirth: data.dateOfBirth || '' });
        if (data.assignedDoctorId) {
          try {
            // assignedDoctorId is a login id; /doctors/{id} expects a profile id, so resolve via the list.
            const doctors = await api.doctors.getAll();
            const doctor = (doctors || []).find(d => String(d.userId) === String(data.assignedDoctorId));
            if (!doctor) throw new Error('Doctor not found');
            setDoctorName(`Dr. ${doctor.firstName || ''} ${doctor.lastName || ''}`.trim());
          } catch {
            setDoctorName(`Doctor #${data.assignedDoctorId}`);
          }
        }
      } catch (err) {
        setError('Failed to load profile. Please refresh the page.');
      } finally {
        setIsLoading(false);
      }
    };
    fetchProfile();
  }, [user?.userId]);

  const handleSave = async () => {
    setIsSaving(true);
    setSaveError(null);
    try {
      const updated = await api.patients.update(profile.id, {
        ...profile,
        contactNumber: editData.contactNumber,
        address: editData.address,
        dateOfBirth: editData.dateOfBirth,
      });
      setProfile(updated);
      setIsEditing(false);
    } catch {
      setSaveError('Failed to save changes. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancel = () => {
    setEditData({ contactNumber: profile?.contactNumber || '', address: profile?.address || '', dateOfBirth: profile?.dateOfBirth || '' });
    setSaveError(null);
    setIsEditing(false);
  };

  if (isLoading) {
    return <div className="p-6 text-center text-gray-500 dark:text-slate-400">Loading profile...</div>;
  }

  if (error) {
    return (
      <div className="space-y-4">
        <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">Patient Profile</h2>
        <Card className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
        </Card>
      </div>
    );
  }

  const fullName = profile
    ? `${profile.firstName || ''} ${profile.lastName || ''}`.trim()
    : (user?.fullName || user?.email || 'Patient');
  const initials = fullName.charAt(0).toUpperCase();

  return (
    <div className="space-y-3">
      <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">Patient Profile</h2>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
        {/* Left: Avatar Card */}
        <Card className="p-4 lg:col-span-1 text-center dark:bg-slate-800">
          <div className="w-20 h-20 rounded-full bg-green-100 dark:bg-green-900/30 mx-auto flex items-center justify-center text-green-600 dark:text-green-400 font-bold text-2xl mb-2">
            {initials}
          </div>
          <h3 className="text-sm font-bold text-gray-900 dark:text-slate-100">{fullName}</h3>
          {profile?.id && (
            <p className="text-green-600 dark:text-green-400 font-medium text-xs mt-0.5">
              Patient ID: PAT-{profile.id}
            </p>
          )}

          <div className="mt-3 flex justify-center gap-1.5 flex-wrap">
            <Badge type="green">Active</Badge>
          </div>

          <div className="mt-3 pt-3 border-t dark:border-slate-700 text-left space-y-2">
            <div className="flex items-center text-gray-600 dark:text-slate-400 text-xs">
              <Mail className="w-3.5 h-3.5 mr-2 flex-shrink-0" />
              <span className="truncate">{profile?.email || user?.email || 'N/A'}</span>
            </div>
            <div className="flex items-center text-gray-600 dark:text-slate-400 text-xs">
              <Phone className="w-3.5 h-3.5 mr-2 flex-shrink-0" />
              <span>{profile?.contactNumber || 'N/A'}</span>
            </div>
            <div className="flex items-center text-gray-600 dark:text-slate-400 text-xs">
              <Calendar className="w-3.5 h-3.5 mr-2 flex-shrink-0" />
              <span>DOB: {profile?.dateOfBirth || 'N/A'}</span>
            </div>
            <div className="flex items-center text-gray-600 dark:text-slate-400 text-xs">
              <User className="w-3.5 h-3.5 mr-2 flex-shrink-0" />
              <span>Gender: {profile?.gender || 'N/A'}</span>
            </div>
          </div>
        </Card>

        {/* Right: Info Sections */}
        <div className="lg:col-span-2 space-y-3">
          {/* Personal Information */}
          <Card className="p-4 dark:bg-slate-800">
            <div className="flex justify-between items-center mb-3">
              <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 flex items-center">
                <User className="w-4 h-4 mr-1.5 text-blue-500" />
                Personal Information
              </h3>
              {!isEditing ? (
                <Button variant="outline" className="text-xs py-1 px-2" onClick={() => setIsEditing(true)}>
                  Edit
                </Button>
              ) : (
                <div className="flex gap-2">
                  <Button className="text-xs py-1 px-2" onClick={handleSave} disabled={isSaving}>
                    {isSaving ? 'Saving...' : 'Save'}
                  </Button>
                  <Button variant="outline" className="text-xs py-1 px-2" onClick={handleCancel} disabled={isSaving}>
                    Cancel
                  </Button>
                </div>
              )}
            </div>

            {saveError && (
              <p className="text-xs text-red-600 dark:text-red-400 mb-2">{saveError}</p>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
              <Input label="Full Name" value={fullName} disabled={true} readOnly />
              <Input label="Email" value={profile?.email || user?.email || ''} disabled={true} readOnly />
              <Input
                label="Phone"
                value={isEditing ? editData.contactNumber : (profile?.contactNumber || '')}
                disabled={!isEditing}
                onChange={e => setEditData(d => ({ ...d, contactNumber: e.target.value }))}
                placeholder="Contact number"
              />
              <Input
                label="Date of Birth"
                type="date"
                value={isEditing ? editData.dateOfBirth : (profile?.dateOfBirth || '')}
                disabled={!isEditing}
                onChange={e => setEditData(d => ({ ...d, dateOfBirth: e.target.value }))}
              />
              <Input label="Gender" value={profile?.gender || ''} disabled={true} readOnly />
              <div className="md:col-span-1">
                <Input
                  label="Address"
                  value={isEditing ? editData.address : (profile?.address || '')}
                  disabled={!isEditing}
                  onChange={e => setEditData(d => ({ ...d, address: e.target.value }))}
                  placeholder="Address"
                />
              </div>
            </div>
          </Card>

          {/* Medical Overview */}
          <Card className="p-4 dark:bg-slate-800">
            <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
              <Activity className="w-4 h-4 mr-1.5 text-blue-500" />
              Medical Overview
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
              <div className="p-2 bg-gray-50 dark:bg-slate-700 rounded">
                <p className="text-xs text-gray-500 dark:text-slate-400 flex items-center gap-1">
                  <Stethoscope className="w-3 h-3" /> Assigned Doctor
                </p>
                <p className="font-semibold text-gray-800 dark:text-slate-100 text-xs mt-0.5">
                  {doctorName || (profile?.assignedDoctorId ? `Doctor #${profile.assignedDoctorId}` : 'Not assigned')}
                </p>
              </div>
              {profile?.medicalHistory && (
                <div className="p-2 bg-gray-50 dark:bg-slate-700 rounded md:col-span-2">
                  <p className="text-xs text-gray-500 dark:text-slate-400 flex items-center gap-1">
                    <FileText className="w-3 h-3" /> Medical History
                  </p>
                  <p className="font-semibold text-gray-800 dark:text-slate-100 text-xs mt-0.5">
                    {profile.medicalHistory}
                  </p>
                </div>
              )}
              {!profile?.medicalHistory && !profile?.assignedDoctorId && (
                <div className="p-2 bg-gray-50 dark:bg-slate-700 rounded text-xs text-gray-400 dark:text-slate-500">
                  No medical overview data available.
                </div>
              )}
            </div>
          </Card>

          {/* Security */}
          <Card className="p-4 dark:bg-slate-800">
            <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center">
              <Shield className="w-4 h-4 mr-1.5 text-blue-500" />
              Security
            </h3>
            <Button variant="secondary" className="w-full justify-center text-xs py-1.5">
              Change Password
            </Button>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default PatientProfile;

