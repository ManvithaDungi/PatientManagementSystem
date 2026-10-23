import React from 'react';
import { render, screen, fireEvent, waitFor } from '../../test-utils';
import StaffAccountProfile from './StaffAccountProfile';

describe('StaffAccountProfile', () => {
   it('shows only server-provided account data and can enable 2FA', async () => {
      global.fetch = jest.fn((url, options = {}) => {
         if (String(url).endsWith('/auth/me')) {
            return Promise.resolve({
               ok: true,
               json: () => Promise.resolve({
                  id: 4, email: 'nina.nurse@h.test', role: 'NURSE', twoFactorEnabled: false, createdAt: '2026-01-02T10:00:00',
               }),
            });
         }
         if (String(url).endsWith('/auth/enable-2fa') && options.method === 'POST') {
            return Promise.resolve({ ok: true, json: () => Promise.resolve({ message: 'ok' }) });
         }
         return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
      });

      render(<StaffAccountProfile title="Nurse Profile" />);

      expect(await screen.findAllByText('nina.nurse@h.test')).not.toHaveLength(0);
      expect(screen.getByText('Nurse Profile')).toBeInTheDocument();
      expect(screen.queryByText(/License #/)).not.toBeInTheDocument();
      expect(screen.getByText('2FA not enabled')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /enable two-factor/i }));
      await waitFor(() => expect(screen.getByText('2FA enabled')).toBeInTheDocument());
      expect(screen.queryByRole('button', { name: /enable two-factor/i })).not.toBeInTheDocument();
   });

   it('reports a load failure instead of showing placeholder data', async () => {
      global.fetch = jest.fn(() => Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));
      render(<StaffAccountProfile title="Administrator Profile" />);
      expect(await screen.findByRole('alert')).toHaveTextContent(/unable to load/i);
   });
});
