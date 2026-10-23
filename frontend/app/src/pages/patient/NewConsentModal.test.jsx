import React from 'react';
import { render, screen, fireEvent, waitFor } from '../../test-utils';
import NewConsentModal from './NewConsentModal';

const doctors = [{ userId: 11, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@h.test', specialty: 'Cardiology' }];

describe('NewConsentModal', () => {
   beforeEach(() => {
      global.fetch = jest.fn((url, options = {}) => {
         if (String(url).endsWith('/doctors')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(doctors) });
         }
         if (String(url).endsWith('/consent') && options.method === 'POST') {
            return Promise.resolve({ ok: true, json: () => Promise.resolve({ id: 5, consentType: 'LAB_RESULTS' }) });
         }
         return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
      });
   });

   it('grants a first consent with the chosen provider and a real backend type', async () => {
      const onGranted = jest.fn();
      render(<NewConsentModal isOpen onClose={() => {}} onGranted={onGranted} />);

      await screen.findByRole('option', { name: /Ada Lovelace/ });
      const submit = screen.getByRole('button', { name: 'Grant access' });
      expect(submit).toBeDisabled();

      fireEvent.change(screen.getByLabelText('Provider'), { target: { value: '11' } });
      fireEvent.change(screen.getByLabelText('Data to share'), { target: { value: 'LAB_RESULTS' } });
      fireEvent.click(screen.getByRole('checkbox'));
      expect(submit).toBeEnabled();
      fireEvent.click(submit);

      await waitFor(() => expect(onGranted).toHaveBeenCalled());
      const post = global.fetch.mock.calls.find(([, o]) => o && o.method === 'POST');
      const body = JSON.parse(post[1].body);
      expect(body).toMatchObject({ grantedToId: 11, consentType: 'LAB_RESULTS', expiresAt: null });
   });

   it('shows the server error and stays open when the grant is refused', async () => {
      global.fetch = jest.fn((url, options = {}) => {
         if (String(url).endsWith('/doctors')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(doctors) });
         }
         return Promise.resolve({
            ok: false,
            status: 400,
            json: () => Promise.resolve({ message: 'Active consent already exists for this provider and data type.' }),
         });
      });
      const onGranted = jest.fn();
      render(<NewConsentModal isOpen onClose={() => {}} onGranted={onGranted} />);

      await screen.findByRole('option', { name: /Ada Lovelace/ });
      fireEvent.change(screen.getByLabelText('Provider'), { target: { value: '11' } });
      fireEvent.click(screen.getByRole('checkbox'));
      fireEvent.click(screen.getByRole('button', { name: 'Grant access' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('Active consent already exists');
      expect(onGranted).not.toHaveBeenCalled();
   });
});
