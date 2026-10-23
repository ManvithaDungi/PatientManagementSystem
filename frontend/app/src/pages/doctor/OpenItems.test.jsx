import React from 'react';
import { render, screen, fireEvent, waitFor } from '../../test-utils';
import DoctorDashboard from './Dashboard';
import DoctorAppointments from './Appointments';
import api from '../../services/api';

jest.mock('../../contexts/AuthContext', () => ({
   ...jest.requireActual('../../contexts/AuthContext'),
   useAuth: () => ({ user: { userId: 9, firstName: 'Ann', lastName: 'Lee' } }),
}));

jest.mock('../../services/api', () => ({
   __esModule: true,
   default: {
      doctors: { getPatients: jest.fn() },
      appointments: { getByDoctor: jest.fn() },
      labResults: { getAll: jest.fn() },
   },
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
   ...jest.requireActual('react-router-dom'),
   useNavigate: () => mockNavigate,
}));

describe('doctor dashboard shows real lab numbers', () => {
   beforeEach(() => {
      jest.clearAllMocks();
      api.doctors.getPatients.mockResolvedValue([]);
      api.appointments.getByDoctor.mockResolvedValue([]);
      api.labResults.getAll.mockResolvedValue([
         { testId: 1, status: 'Pending' },
         { testId: 2, status: 'Collected' },
         { testId: 3, status: 'Completed', resultValue: '9', referenceRange: '4-6' },
         { testId: 4, status: 'Completed', resultValue: '5', referenceRange: '4-6' },
      ]);
   });

   test('Pending Labs counts unfinished orders and the briefing counts abnormal results', async () => {
      render(<DoctorDashboard />);
      expect(await screen.findByText(/1 abnormal/)).toBeInTheDocument();
      expect(screen.getByText(/2 lab orders awaiting results/)).toBeInTheDocument();
      expect(screen.queryByText(/Patient inquiries/)).not.toBeInTheDocument();
   });
});

describe('doctor appointments link to the patient chart', () => {
   test('View Patient opens the chart of the appointment patient', async () => {
      api.appointments.getByDoctor.mockResolvedValue([
         { appointmentId: 1, patientId: 42, patientName: 'Pat', appointmentDate: '2099-01-01T10:00:00', status: 'SCHEDULED' },
      ]);
      render(<DoctorAppointments />);
      fireEvent.click(await screen.findByTitle('List View'));
      fireEvent.click(await screen.findByText('View Patient'));
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dashboard/doctor/patient/42'));
   });
});
