import React from 'react';
import { render, screen, fireEvent, waitFor } from '../../test-utils';
import LabOrderDetail from './OrderDetail';
import api from '../../services/api';

jest.mock('../../services/api', () => ({
   __esModule: true,
   default: {
      labTechnician: { getOrders: jest.fn(), updateOrderStatus: jest.fn() },
      files: { getObjectUrl: jest.fn() },
   },
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
   ...jest.requireActual('react-router-dom'),
   useParams: () => ({ id: '7' }),
   useNavigate: () => mockNavigate,
}));

const order = (status) => ({ testId: 7, testName: 'CBC', testCategory: 'Hematology', status, orderedByDoctor: 'Dr X' });

describe('lab order detail: every workflow step has a button', () => {
   beforeEach(() => {
      jest.clearAllMocks();
   });

   test('Pending -> "Mark Sample Collected"', async () => {
      api.labTechnician.getOrders.mockResolvedValue([order('Pending')]);
      api.labTechnician.updateOrderStatus.mockResolvedValue(order('Collected'));
      render(<LabOrderDetail />);

      fireEvent.click(await screen.findByText('Mark Sample Collected'));
      await waitFor(() => expect(api.labTechnician.updateOrderStatus).toHaveBeenCalledWith(7, 'Collected'));
      expect(await screen.findByText('Mark Results Pending')).toBeInTheDocument();
   });

   test('Collected -> "Mark Results Pending" moves the order and then only offers the upload', async () => {
      api.labTechnician.getOrders.mockResolvedValue([order('Collected')]);
      api.labTechnician.updateOrderStatus.mockResolvedValue(order('Results Pending'));
      render(<LabOrderDetail />);

      expect(await screen.findByText('Upload Results')).toBeInTheDocument();
      fireEvent.click(screen.getByText('Mark Results Pending'));

      await waitFor(() => expect(api.labTechnician.updateOrderStatus).toHaveBeenCalledWith(7, 'Results Pending'));
      await waitFor(() => expect(screen.queryByText('Mark Results Pending')).not.toBeInTheDocument());
      expect(screen.getByText('Upload Results')).toBeInTheDocument();
   });

   test('a rejected transition is reported and the status is left alone', async () => {
      api.labTechnician.getOrders.mockResolvedValue([order('Collected')]);
      api.labTechnician.updateOrderStatus.mockRejectedValue(new Error('Cannot change status from Completed to Collected'));
      render(<LabOrderDetail />);

      fireEvent.click(await screen.findByText('Mark Results Pending'));
      expect(await screen.findByRole('alert')).toHaveTextContent('Cannot change status');
      expect(screen.getByText('Mark Results Pending')).toBeInTheDocument();
   });

   test('a Pending or Completed order does not offer the Results Pending step', async () => {
      api.labTechnician.getOrders.mockResolvedValue([order('Completed')]);
      render(<LabOrderDetail />);
      await screen.findByText('Order #7');
      expect(screen.queryByText('Mark Results Pending')).not.toBeInTheDocument();
   });
});
