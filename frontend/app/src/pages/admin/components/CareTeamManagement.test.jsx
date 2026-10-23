import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CareTeamManagement from './CareTeamManagement';
import api from '../../../services/api';

jest.mock('../../../services/api', () => ({
   __esModule: true,
   default: {
      admin: {
         getAllUsers: jest.fn(),
         getAllStaff: jest.fn(),
         updateCareTeam: jest.fn(),
      },
   },
}));

const patients = [
   { profileId: 1, firstName: 'Pat', lastName: 'Ient', email: 'pat@x.test', assignedDoctorId: null, assignedNurseId: null },
   { profileId: 2, firstName: 'Other', lastName: 'Person', email: 'other@x.test', assignedDoctorId: 11, assignedNurseId: 12 },
];
const staff = [
   { userId: 11, email: 'doc@x.test', role: 'DOCTOR', enabled: true },
   { userId: 12, email: 'nurse@x.test', role: 'NURSE', enabled: true },
   { userId: 13, email: 'lab@x.test', role: 'LAB_TECHNICIAN', enabled: true },
   { userId: 14, email: 'olddoc@x.test', role: 'DOCTOR', enabled: false },
   { userId: 15, email: 'admin@x.test', role: 'ADMIN', enabled: true },
];

describe('CareTeamManagement', () => {
   beforeEach(() => {
      jest.clearAllMocks();
      api.admin.getAllUsers.mockResolvedValue(patients);
      api.admin.getAllStaff.mockResolvedValue(staff);
   });

   test('lists patients and filters them with the search box', async () => {
      render(<CareTeamManagement />);
      expect(await screen.findByText('Pat Ient')).toBeInTheDocument();
      expect(screen.getByText('Other Person')).toBeInTheDocument();

      fireEvent.change(screen.getByLabelText('Search patients'), { target: { value: 'other' } });
      expect(screen.queryByText('Pat Ient')).not.toBeInTheDocument();
      expect(screen.getByText('Other Person')).toBeInTheDocument();
   });

   test('pickers offer only enabled doctors and nurses', async () => {
      render(<CareTeamManagement />);
      fireEvent.click(await screen.findByText('Pat Ient'));

      const doctorOptions = Array.from(screen.getByLabelText('Treating doctor').querySelectorAll('option')).map((o) => o.textContent);
      const nurseOptions = Array.from(screen.getByLabelText('Assigned nurse').querySelectorAll('option')).map((o) => o.textContent);
      expect(doctorOptions).toEqual(['Not assigned', 'doc@x.test']);
      expect(nurseOptions).toEqual(['Not assigned', 'nurse@x.test']);
   });

   test('saving sends numeric ids and shows a success message', async () => {
      api.admin.updateCareTeam.mockResolvedValue({ patientId: 1, assignedDoctorId: 11, assignedNurseId: 12 });
      render(<CareTeamManagement />);
      fireEvent.click(await screen.findByText('Pat Ient'));
      fireEvent.change(screen.getByLabelText('Treating doctor'), { target: { value: '11' } });
      fireEvent.change(screen.getByLabelText('Assigned nurse'), { target: { value: '12' } });
      fireEvent.click(screen.getByText('Save care team'));

      await waitFor(() => expect(api.admin.updateCareTeam).toHaveBeenCalledWith(1, 11, 12));
      expect(await screen.findByText('Care team saved.')).toBeInTheDocument();
   });

   test('clearing a picker sends null', async () => {
      api.admin.updateCareTeam.mockResolvedValue({ patientId: 2, assignedDoctorId: null, assignedNurseId: 12 });
      render(<CareTeamManagement />);
      fireEvent.click(await screen.findByText('Other Person'));
      fireEvent.change(screen.getByLabelText('Treating doctor'), { target: { value: '' } });
      fireEvent.click(screen.getByText('Save care team'));
      await waitFor(() => expect(api.admin.updateCareTeam).toHaveBeenCalledWith(2, null, 12));
   });

   test('shows the server error when saving fails', async () => {
      api.admin.updateCareTeam.mockRejectedValue(new Error('The selected user is not a doctor.'));
      render(<CareTeamManagement />);
      fireEvent.click(await screen.findByText('Pat Ient'));
      fireEvent.click(screen.getByText('Save care team'));
      expect(await screen.findByRole('alert')).toHaveTextContent('The selected user is not a doctor.');
   });

   test('shows an error with retry when the directory cannot be loaded', async () => {
      api.admin.getAllUsers.mockRejectedValueOnce(new Error('boom'));
      render(<CareTeamManagement />);
      expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load patients and staff');
      fireEvent.click(screen.getByText('Retry'));
      expect(await screen.findByText('Pat Ient')).toBeInTheDocument();
   });
});
