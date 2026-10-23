import React from 'react';
import { render, screen, fireEvent, waitFor } from '../../test-utils';
import LabResults from './LabResults';
import api from '../../services/api';

jest.mock('../../services/api', () => ({
   __esModule: true,
   default: { labResults: { getAll: jest.fn() } },
}));

const labs = [
   { testId: 1, testName: 'Glucose', status: 'Completed', resultValue: '5.0', referenceRange: '4-6', unit: 'mmol/L' },
   { testId: 2, testName: 'Cholesterol', status: 'Completed', resultValue: '9.1', referenceRange: '<5.2', unit: 'mmol/L' },
   { testId: 3, testName: 'Hemoglobin', status: 'Collected' },
   { testId: 4, testName: 'Sodium', status: 'Results Pending' },
   { testId: 5, testName: 'Urinalysis', status: 'Pending' },
];

describe('doctor lab results: statuses and the Abnormal filter', () => {
   beforeEach(() => {
      jest.clearAllMocks();
      api.labResults.getAll.mockResolvedValue(labs);
   });

   test('offers a filter for every status the backend produces', async () => {
      render(<LabResults />);
      await screen.findByText('Glucose');
      ['All', 'Pending', 'Collected', 'Results Pending', 'Completed', 'Abnormal'].forEach((name) => {
         expect(screen.getByRole('button', { name })).toBeInTheDocument();
      });
   });

   test('Abnormal shows only published results outside their reference range', async () => {
      render(<LabResults />);
      await screen.findByText('Glucose');
      fireEvent.click(screen.getByRole('button', { name: 'Abnormal' }));
      expect(screen.getByText('Cholesterol')).toBeInTheDocument();
      expect(screen.queryByText('Glucose')).not.toBeInTheDocument();
      expect(screen.queryByText('Hemoglobin')).not.toBeInTheDocument();
      expect(screen.getByText(/Abnormal \(outside <5\.2 mmol\/L\)/)).toBeInTheDocument();
   });

   test('Collected and Results Pending have their own filters and are not shown as errors', async () => {
      render(<LabResults />);
      await screen.findByText('Glucose');

      fireEvent.click(screen.getByRole('button', { name: 'Collected' }));
      expect(screen.getByText('Hemoglobin')).toBeInTheDocument();
      expect(screen.queryByText('Sodium')).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'Results Pending' }));
      expect(screen.getByText('Sodium')).toBeInTheDocument();
      expect(screen.queryByText('Hemoglobin')).not.toBeInTheDocument();
   });

   test('a normal published result is not flagged', async () => {
      render(<LabResults />);
      await screen.findByText('Glucose');
      expect(screen.getAllByText(/Abnormal \(outside/)).toHaveLength(1);
   });
});
