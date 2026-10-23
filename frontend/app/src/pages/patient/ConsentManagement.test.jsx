import React from 'react';
import { render, screen, fireEvent, waitFor } from '../../test-utils';
import ConsentManagement from './ConsentManagement';

// Mock the GrantModifyConsent component
jest.mock('./GrantModifyConsent', () => {
   return function MockGrantModifyConsent({ isOpen, onClose }) {
      return isOpen ? (
         <div data-testid="grant-modify-consent-modal">
            <button onClick={onClose}>Close</button>
         </div>
      ) : null;
   };
});

describe('ConsentManagement', () => {
   beforeEach(() => {
      // Mock global.fetch so apiCall() inside api.js resolves cleanly,
      // allowing isLoading to become false and tab content to render.
      global.fetch = jest.fn().mockResolvedValue({
         ok: true,
         json: () => Promise.resolve([]),
      });
   });

   afterEach(() => {
      jest.restoreAllMocks();
   });


   test('renders consent management page', () => {
      const { container } = render(<ConsentManagement />);
      expect(container).toBeInTheDocument();
   });

   test('displays page header with title', () => {
      render(<ConsentManagement />);
      expect(screen.getByText(/privacy & consent/i)).toBeInTheDocument();
   });

   test('displays HIPAA information banner', async () => {
      render(<ConsentManagement />);
      expect(await screen.findByText(/your healthcare privacy rights under hipaa/i)).toBeInTheDocument();
   });

   test('renders tab navigation with three tabs', () => {
      render(<ConsentManagement />);
      expect(screen.getByText('Consent Overview')).toBeInTheDocument();
      expect(screen.getByText('Modify Consent')).toBeInTheDocument();
      expect(screen.getByText('Data Management')).toBeInTheDocument();
   });

   test('displays summary cards on overview tab', async () => {
      render(<ConsentManagement />);
      await waitFor(() => expect(screen.getAllByText(/active consents/i).length).toBeGreaterThan(0));
      await waitFor(() => expect(screen.getAllByText(/pending review/i).length).toBeGreaterThan(0));
      await waitFor(() => expect(screen.getAllByText(/withdrawn/i).length).toBeGreaterThan(0));
   });

   test('switches to Modify Consent tab when clicked', async () => {
      render(<ConsentManagement />);
      // Wait for loading to finish, then click the tab
      const modifyTab = await screen.findByText('Modify Consent');
      fireEvent.click(modifyTab);
      expect(await screen.findByText(/grant or modify your consents/i)).toBeInTheDocument();
   });

   test('switches to Data Management tab when clicked', async () => {
      render(<ConsentManagement />);
      const dataTab = await screen.findByText('Data Management');
      fireEvent.click(dataTab);
      expect(await screen.findByText(/health data management/i)).toBeInTheDocument();
   });

   test('displays consent history section on overview tab', async () => {
      render(<ConsentManagement />);
      expect(await screen.findByText(/consent history/i)).toBeInTheDocument();
   });

   test('displays help section that can be expanded', async () => {
      render(<ConsentManagement />);
      const helpSection = await screen.findByText(/understanding your privacy rights/i);
      expect(helpSection).toBeInTheDocument();
      fireEvent.click(helpSection);
      expect(await screen.findByText(/what is hipaa/i)).toBeInTheDocument();
   });

   test('displays legal notices footer', async () => {
      render(<ConsentManagement />);
      expect(await screen.findByText(/notice of privacy practices/i)).toBeInTheDocument();
      await waitFor(() => expect(screen.getAllByText(/your privacy rights/i).length).toBeGreaterThan(0));
   });

   test('displays consent categories section', async () => {
      render(<ConsentManagement />);
      expect(await screen.findByText(/consent categories/i)).toBeInTheDocument();
   });

   test('history filter dropdown changes filter value', async () => {
      render(<ConsentManagement />);
      const filterSelect = await screen.findByRole('combobox');
      expect(filterSelect).toBeInTheDocument();
      fireEvent.change(filterSelect, { target: { value: '30days' } });
      expect(filterSelect.value).toBe('30days');
   });

   test('displays data overview stats on data management tab', async () => {
      render(<ConsentManagement />);
      const dataTab = await screen.findByText('Data Management');
      fireEvent.click(dataTab);
      await waitFor(() => expect(screen.getAllByText(/medical records/i).length).toBeGreaterThan(0));
      await waitFor(() => expect(screen.getAllByText(/connected providers/i).length).toBeGreaterThan(0));
   });

   test('displays data access log on data management tab', async () => {
      render(<ConsentManagement />);
      const dataTab = await screen.findByText('Data Management');
      fireEvent.click(dataTab);
      expect(await screen.findByText(/data access log/i)).toBeInTheDocument();
   });

   test('displays export data section on data management tab', async () => {
      render(<ConsentManagement />);
      const dataTab = await screen.findByText('Data Management');
      fireEvent.click(dataTab);
      expect(await screen.findByText(/export your data/i)).toBeInTheDocument();
   });

   test('displays data deletion section on data management tab', async () => {
      render(<ConsentManagement />);
      const dataTab = await screen.findByText('Data Management');
      fireEvent.click(dataTab);
      expect(await screen.findByText(/data deletion requests/i)).toBeInTheDocument();
   });

   test('displays download consent history button', () => {
      render(<ConsentManagement />);
      const downloadButton = screen.getByTitle('Download Consent History');
      expect(downloadButton).toBeInTheDocument();
   });

   test('lets the patient grant a first consent from the modify tab', async () => {
      render(<ConsentManagement />);
      fireEvent.click(await screen.findByText('Modify Consent'));
      expect(await screen.findByText(/share your data with a provider/i)).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /grant new consent/i }));
      expect(await screen.findByText(/grant access to a provider/i)).toBeInTheDocument();
      expect(screen.getByLabelText('Provider')).toBeInTheDocument();
      expect(screen.getByLabelText('Data to share')).toBeInTheDocument();
   });
});
