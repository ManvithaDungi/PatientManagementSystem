import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import NotificationBell from './NotificationBell';
import api from '../../services/api';

jest.mock('../../services/api', () => ({
   __esModule: true,
   default: {
      notifications: {
         getMine: jest.fn(),
         markRead: jest.fn(),
         markAllRead: jest.fn(),
      },
   },
}));

const payload = {
   unread: 2,
   items: [
      { id: 1, title: 'Appointment approved', message: 'Your appointment was approved.', createdAt: '2026-10-19T09:00:00', read: false },
      { id: 2, title: 'Lab result available', message: 'A new lab result is ready to view.', createdAt: '2026-10-18T09:00:00', read: false },
      { id: 3, title: 'Old news', message: '', createdAt: '2026-10-01T09:00:00', read: true },
   ],
};

describe('NotificationBell', () => {
   beforeEach(() => {
      jest.clearAllMocks();
      api.notifications.getMine.mockResolvedValue(payload);
      api.notifications.markRead.mockResolvedValue({});
      api.notifications.markAllRead.mockResolvedValue({ updated: 2 });
   });

   test('shows the real unread count and the notifications from the API', async () => {
      render(<NotificationBell />);
      expect(await screen.findByTestId('notification-badge')).toHaveTextContent('2');
      fireEvent.click(screen.getByRole('button', { name: /Notifications \(2 unread\)/ }));
      expect(screen.getByText('Appointment approved')).toBeInTheDocument();
      expect(screen.getByText('A new lab result is ready to view.')).toBeInTheDocument();
   });

   test('shows an empty state when there is nothing', async () => {
      api.notifications.getMine.mockResolvedValue({ unread: 0, items: [] });
      render(<NotificationBell />);
      await waitFor(() => expect(api.notifications.getMine).toHaveBeenCalled());
      fireEvent.click(screen.getByRole('button', { name: /Notifications/ }));
      expect(await screen.findByText('No notifications')).toBeInTheDocument();
      expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
   });

   test('clicking an unread item marks it read and lowers the badge', async () => {
      render(<NotificationBell />);
      await screen.findByTestId('notification-badge');
      fireEvent.click(screen.getByRole('button', { name: /Notifications/ }));
      fireEvent.click(screen.getByText('Appointment approved'));
      await waitFor(() => expect(api.notifications.markRead).toHaveBeenCalledWith(1));
      expect(await screen.findByTestId('notification-badge')).toHaveTextContent('1');
   });

   test('mark all read clears the badge', async () => {
      render(<NotificationBell />);
      await screen.findByTestId('notification-badge');
      fireEvent.click(screen.getByRole('button', { name: /Notifications/ }));
      fireEvent.click(screen.getByText('Mark all read'));
      await waitFor(() => expect(api.notifications.markAllRead).toHaveBeenCalled());
      await waitFor(() => expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument());
   });

   test('a failing request shows an error instead of fake emptiness', async () => {
      api.notifications.getMine.mockRejectedValue(new Error('down'));
      render(<NotificationBell />);
      await waitFor(() => expect(api.notifications.getMine).toHaveBeenCalled());
      fireEvent.click(screen.getByRole('button', { name: /Notifications/ }));
      expect(await screen.findByRole('alert')).toHaveTextContent('Could not load notifications.');
   });
});
