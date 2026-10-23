import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import UserManagement from './UserManagement';
import api from '../../../services/api';

jest.mock('../../../services/api', () => ({
   __esModule: true,
   default: {
      admin: {
         getAllStaff: jest.fn(),
         updateStaffRole: jest.fn(),
         deleteStaff: jest.fn(),
         enableStaff: jest.fn(),
      },
      invites: { list: jest.fn(), create: jest.fn(), revoke: jest.fn() },
   },
}));

const staff = [
   { userId: 5, email: 'nurse@x.test', role: 'NURSE', enabled: true, lastLoginAt: null },
   { userId: 6, email: 'gone@x.test', role: 'DOCTOR', enabled: false, lastLoginAt: '2026-01-02T10:00:00' },
];

describe('UserManagement', () => {
   beforeEach(() => {
      jest.clearAllMocks();
      api.admin.getAllStaff.mockResolvedValue(staff);
      api.invites.list.mockResolvedValue([]);
      window.confirm = jest.fn(() => true);
   });

   test('shows real status and last login instead of placeholders', async () => {
      render(<UserManagement />);
      expect(await screen.findByText('nurse@x.test')).toBeInTheDocument();
      expect(screen.getByText('Active')).toBeInTheDocument();
      expect(screen.getByText('Disabled')).toBeInTheDocument();
      expect(screen.getByText('Never')).toBeInTheDocument();
      expect(screen.queryByText('Just now')).not.toBeInTheDocument();
   });

   test('disabling a staff member calls the API and reloads the list', async () => {
      api.admin.deleteStaff.mockResolvedValue({ message: 'Staff member disabled.' });
      render(<UserManagement />);
      await screen.findByText('nurse@x.test');
      fireEvent.click(screen.getByText('Disable'));
      await waitFor(() => expect(api.admin.deleteStaff).toHaveBeenCalledWith(5));
      expect(await screen.findByText('nurse@x.test was disabled.')).toBeInTheDocument();
      expect(api.admin.getAllStaff).toHaveBeenCalledTimes(2);
   });

   test('enabling a disabled account calls the enable endpoint', async () => {
      api.admin.enableStaff.mockResolvedValue({});
      render(<UserManagement />);
      await screen.findByText('gone@x.test');
      fireEvent.click(screen.getByText('Enable'));
      await waitFor(() => expect(api.admin.enableStaff).toHaveBeenCalledWith(6));
   });

   test('changing a role asks for confirmation and surfaces server errors', async () => {
      api.admin.updateStaffRole.mockRejectedValue(new Error('You cannot change your own role.'));
      render(<UserManagement />);
      await screen.findByText('nurse@x.test');
      fireEvent.change(screen.getByLabelText('Role for nurse@x.test'), { target: { value: 'DOCTOR' } });
      expect(window.confirm).toHaveBeenCalled();
      await waitFor(() => expect(api.admin.updateStaffRole).toHaveBeenCalledWith(5, 'DOCTOR'));
      expect(await screen.findByRole('alert')).toHaveTextContent('You cannot change your own role.');
   });

   test('declining the confirmation does not call the API', async () => {
      window.confirm = jest.fn(() => false);
      render(<UserManagement />);
      await screen.findByText('nurse@x.test');
      fireEvent.click(screen.getByText('Disable'));
      expect(api.admin.deleteStaff).not.toHaveBeenCalled();
   });
});
