import React from 'react';
import { render, screen } from '@testing-library/react';
import MedicalHistoryList from './MedicalHistoryList';

// Mock dependencies
jest.mock('../common/Card', () => ({ children, className }) => <div className={`mock-card ${className}`}>{children}</div>);
jest.mock('../common/Badge', () => ({ children }) => <span data-testid="badge">{children}</span>);

describe('MedicalHistoryList', () => {
   // Shape matches the real backend MedicalRecordDTO (recordId, diagnosis, symptoms,
   // treatmentProvided, recordDate, doctorName) — see FULL_STACK_INTEGRATION_AUDIT.md DR-14.
   const mockHistory = [
      {
         recordId: 1,
         diagnosis: 'Diagnosis',
         recordDate: '2023-01-01',
         symptoms: 'Flu',
         doctorName: 'Dr. Adams',
      },
      {
         recordId: 2,
         diagnosis: 'Surgery',
         recordDate: '2022-05-20',
         symptoms: 'Appendectomy',
         doctorName: 'Dr. Adams',
      },
   ];

   test('renders no history message when history is empty', () => {
      render(<MedicalHistoryList history={[]} />);
      expect(screen.getByText(/No medical history records found/i)).toBeInTheDocument();
   });

   test('renders list of history records', () => {
      render(<MedicalHistoryList history={mockHistory} />);
      expect(screen.getByText('Diagnosis')).toBeInTheDocument();
      expect(screen.getByText(/Flu/)).toBeInTheDocument();
      expect(screen.getByText('Surgery')).toBeInTheDocument();
      expect(screen.getByText(/Appendectomy/)).toBeInTheDocument();
   });
});
