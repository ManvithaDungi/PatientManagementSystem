import React from 'react';
import { render, screen, fireEvent, waitFor } from '../../test-utils';
import ConsentManagement from './ConsentManagement';

jest.mock('./GrantModifyConsent', () => () => null);

// Routes the fetch mock by URL so the page's several endpoints can answer differently.
const respond = (routes) => {
   global.fetch = jest.fn((url) => {
      const key = Object.keys(routes).find((k) => String(url).includes(k));
      const value = key ? routes[key] : [];
      if (value instanceof Error) {
         return Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({ message: 'boom' }) });
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve(value) });
   });
};

const openDataTab = async () => {
   fireEvent.click(await screen.findByText('Data Management'));
};

describe('ConsentManagement shows only real data', () => {
   afterEach(() => jest.restoreAllMocks());

   test('the access log comes from the API, not a hard-coded list', async () => {
      respond({
         '/consent/access-log': [
            { id: 1, accessor: 'dr.real@hospital.test', action: 'Viewed prescriptions', category: 'PRESCRIPTIONS', timestamp: '2026-10-18T09:30:00' },
         ],
      });
      render(<ConsentManagement />);
      await openDataTab();

      expect(await screen.findByText('dr.real@hospital.test')).toBeInTheDocument();
      expect(screen.getByText('Viewed prescriptions')).toBeInTheDocument();
      // the fabricated entries that used to be shown
      expect(screen.queryByText('Dr. Sarah Johnson')).not.toBeInTheDocument();
      expect(screen.queryByText('Dr. Michael Chen')).not.toBeInTheDocument();
      expect(screen.queryByText('Insurance Provider')).not.toBeInTheDocument();
   });

   test('an empty access log is stated honestly', async () => {
      respond({ '/consent/access-log': [] });
      render(<ConsentManagement />);
      await openDataTab();
      expect(await screen.findByText('No one has accessed your records yet.')).toBeInTheDocument();
   });

   test('a failing access log shows an error rather than sample rows', async () => {
      respond({ '/consent/access-log': new Error('x') });
      render(<ConsentManagement />);
      await openDataTab();
      expect(await screen.findByText('Unable to load the access log.')).toBeInTheDocument();
      expect(screen.queryByText('Dr. Sarah Johnson')).not.toBeInTheDocument();
   });

   test('export and deletion buttons no longer pretend to work', async () => {
      respond({ '/consent/access-log': [] });
      render(<ConsentManagement />);
      await openDataTab();

      const pdf = (await screen.findByText(/Export as PDF/)).closest('button');
      expect(pdf).toBeDisabled();
      expect(screen.getByText(/Export as FHIR/).closest('button')).toBeDisabled();
      expect(screen.getByText(/Export as C-CDA/).closest('button')).toBeDisabled();
      expect(screen.getByText(/Request Data Deletion/).closest('button')).toBeDisabled();
      fireEvent.click(pdf);
      expect(screen.queryByText(/export started/i)).not.toBeInTheDocument();
   });

   test('the fabricated connected apps are gone', async () => {
      respond({ '/consent/access-log': [] });
      render(<ConsentManagement />);
      await openDataTab();
      await waitFor(() => expect(screen.getByText(/Request Data Deletion/)).toBeInTheDocument());
      expect(screen.queryByText('MyChart Mobile App')).not.toBeInTheDocument();
      expect(screen.queryByText('Fitbit')).not.toBeInTheDocument();
   });
});
