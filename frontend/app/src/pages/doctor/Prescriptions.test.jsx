import React from 'react';
import { render, screen, fireEvent, waitFor } from '../../test-utils';
import Prescriptions from './Prescriptions';
import api from '../../services/api';

// Mock dependencies
jest.mock('../../components/common/Modal', () => ({ children, isOpen, title }) => (
   isOpen ? (
      <div data-testid="modal">
         <h1>{title}</h1>
         {children}
      </div>
   ) : null
));
jest.mock('lucide-react', () => ({
   Plus: () => <span>PlusIcon</span>,
   Search: () => <span>SearchIcon</span>,
   Filter: () => <span>FilterIcon</span>,
   Pill: () => <span>PillIcon</span>,
}));

jest.mock('../../services/api', () => ({
   doctors: {
      getPatients: jest.fn(),
   },
   prescriptions: {
      getMine: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      updateStatus: jest.fn(),
   },
}));

const authValue = { user: { userId: 'D001', id: 'D001', name: 'Dr. Test' } };

describe('Prescriptions Page', () => {
   beforeEach(() => {
      jest.clearAllMocks();
      api.doctors.getPatients.mockResolvedValue([
         { id: 'P001', firstName: 'John', lastName: 'Smith' },
         { id: 'P002', firstName: 'Jane', lastName: 'Doe' },
      ]);
      api.prescriptions.getMine.mockResolvedValue([
         { prescriptionId: 'RX001', patientId: 'P001', medicationName: 'Amoxicillin', dosage: '500mg', frequency: '3x daily', status: 'ACTIVE' },
         { prescriptionId: 'RX002', patientId: 'P002', medicationName: 'Ibuprofen', dosage: '200mg', frequency: 'As needed', status: 'DISCONTINUED' },
      ]);
   });

   test('renders prescriptions list', async () => {
      render(<Prescriptions />, { authValue });
      expect(await screen.findByText('Amoxicillin')).toBeInTheDocument();
      expect(screen.getByText('Ibuprofen')).toBeInTheDocument();
   });

   test('filters prescriptions by search', async () => {
      render(<Prescriptions />, { authValue });
      await screen.findByText('Amoxicillin');

      const searchInput = screen.getByPlaceholderText('Search prescriptions...');
      fireEvent.change(searchInput, { target: { value: 'Amoxicillin' } });

      expect(screen.getByText('Amoxicillin')).toBeInTheDocument();
      expect(screen.queryByText('Ibuprofen')).not.toBeInTheDocument();
   });

   test('opens new prescription modal', async () => {
      render(<Prescriptions />, { authValue });
      await screen.findByText('Amoxicillin');

      const newButton = screen.getByText(/New Prescription/i);
      fireEvent.click(newButton);

      expect(screen.getByTestId('modal')).toBeInTheDocument();
      const modal = screen.getByTestId('modal');
      expect(modal).toHaveTextContent('New Prescription');
   });
   test('discontinuing calls the status endpoint and sends only the editable fields', async () => {
      api.prescriptions.update.mockResolvedValue({});
      api.prescriptions.updateStatus.mockResolvedValue({ status: 'CANCELLED' });
      render(<Prescriptions />, { authValue });
      await screen.findByText('Amoxicillin');

      fireEvent.click(screen.getAllByText('Manage')[0]);
      fireEvent.change(screen.getByDisplayValue('Active'), { target: { value: 'false' } });
      fireEvent.click(screen.getByText('Update Prescription'));

      await waitFor(() => expect(api.prescriptions.updateStatus).toHaveBeenCalledWith('RX001', 'CANCELLED'));
      expect(api.prescriptions.update).toHaveBeenCalledWith('RX001', { dosage: '500mg', frequency: '3x daily' });
   });

   test('a server failure is shown and the status call is not made', async () => {
      api.prescriptions.update.mockRejectedValue(new Error('A cancelled prescription cannot be changed.'));
      render(<Prescriptions />, { authValue });
      await screen.findByText('Amoxicillin');

      fireEvent.click(screen.getAllByText('Manage')[0]);
      fireEvent.change(screen.getByDisplayValue('Active'), { target: { value: 'false' } });
      fireEvent.click(screen.getByText('Update Prescription'));

      expect(await screen.findByText(/cannot be changed/)).toBeInTheDocument();
      expect(api.prescriptions.updateStatus).not.toHaveBeenCalled();
   });
});
