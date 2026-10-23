import React from 'react';
import { render, screen } from '../test-utils';
import AppointmentSidePanel from './AppointmentSidePanel';

describe('AppointmentSidePanel does not invent patient contact details', () => {
   test('no placeholder phone or e-mail is rendered', () => {
      const { container } = render(
         <AppointmentSidePanel
            appointment={{
               id: '1', patientName: 'Test Patient', patientId: 'P123', status: 'Confirmed',
               date: '2023-10-10', time: '10:00 AM', duration: 30, type: 'Checkup', room: '101', doctorName: 'Dr. Smith',
            }}
            onClose={jest.fn()}
            onAction={jest.fn()}
         />
      );
      expect(container.textContent).not.toContain('555-0123');
      expect(container.textContent).not.toContain('patient@example.com');
      expect(screen.queryByText('Patient Contact')).not.toBeInTheDocument();
   });
});
