import React from 'react';
import { render, screen, act, fireEvent } from '../test-utils';
import DashboardLayout from './DashboardLayout';
import api from '../services/api';

jest.mock('../services/api', () => ({
   __esModule: true,
   default: {
      auth: { getCurrentUser: jest.fn() },
      notifications: { getMine: jest.fn() },
   },
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
   ...jest.requireActual('react-router-dom'),
   useNavigate: () => mockNavigate,
   useLocation: () => ({ pathname: '/dashboard/patient' }),
}));

const MINUTE = 60 * 1000;

describe('DashboardLayout idle-session handling', () => {
   beforeEach(() => {
      jest.useFakeTimers();
      jest.clearAllMocks();
      api.auth.getCurrentUser.mockResolvedValue({ id: 1 });
      api.notifications.getMine.mockResolvedValue({ unread: 0, items: [] });
   });

   afterEach(() => {
      jest.useRealTimers();
   });

   test('no warning while the user is active; the server idle timer is kept alive', async () => {
      render(<DashboardLayout role="patient" userName="Pat" />);
      await act(async () => {
         jest.advanceTimersByTime(6 * MINUTE);
      });
      expect(screen.queryByText('Session Timeout Warning')).not.toBeInTheDocument();
      expect(api.auth.getCurrentUser).toHaveBeenCalled();
   });

   test('warns one minute before the 30-minute idle limit and can be extended', async () => {
      render(<DashboardLayout role="patient" userName="Pat" />);
      await act(async () => {
         jest.advanceTimersByTime(29 * MINUTE + 30 * 1000);
      });
      expect(await screen.findByText('Session Timeout Warning')).toBeInTheDocument();

      api.auth.getCurrentUser.mockClear();
      await act(async () => {
         fireEvent.click(screen.getByText('Extend Session'));
      });
      expect(api.auth.getCurrentUser).toHaveBeenCalledTimes(1);
      expect(screen.queryByText('Session Timeout Warning')).not.toBeInTheDocument();
   });
});
