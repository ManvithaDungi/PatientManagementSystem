import { authAPI, patientAPI, appointmentAPI } from './api';

describe('API Service', () => {
   beforeEach(() => {
      jest.resetAllMocks();
      global.fetch = jest.fn();
   });

   describe('authAPI', () => {
      test('login calls correct endpoint', async () => {
         global.fetch.mockResolvedValueOnce({
            ok: true,
            json: async () => ({ token: '123' }),
         });

         await authAPI.login('test@example.com', 'password');

         expect(global.fetch).toHaveBeenCalledWith(
            expect.stringContaining('/auth/login'),
            expect.objectContaining({
               method: 'POST',
               body: JSON.stringify({ email: 'test@example.com', password: 'password' }),
            })
         );
      });
   });

   describe('patientAPI', () => {
      test('getAll calls correct endpoint', async () => {
         global.fetch.mockResolvedValueOnce({
            ok: true,
            json: async () => ([{ id: 1, name: 'John Doe' }]),
         });

         await patientAPI.getAll();

         expect(global.fetch).toHaveBeenCalledWith(
            expect.stringContaining('/patients'),
            expect.objectContaining({ method: 'GET' })
         );
      });
   });

   describe('appointmentAPI', () => {
      test('create calls correct endpoint', async () => {
         global.fetch.mockResolvedValueOnce({
            ok: true,
            json: async () => ({ id: 1 }),
         });

         const appointmentData = { date: '2023-01-01', patientId: 1 };
         await appointmentAPI.create(appointmentData);

         expect(global.fetch).toHaveBeenCalledWith(
            expect.stringContaining('/appointments'),
            expect.objectContaining({
               method: 'POST',
               body: JSON.stringify(appointmentData),
            })
         );
      });
   });
   describe('response parsing', () => {
      test('plain-text and empty success bodies do not throw', async () => {
         global.fetch.mockResolvedValueOnce({ ok: true, text: async () => 'Logged out successfully' });
         await expect(authAPI.logout()).resolves.toBe('Logged out successfully');

         global.fetch.mockResolvedValueOnce({ ok: true, text: async () => '' });
         await expect(authAPI.logout()).resolves.toBeNull();
      });

      test('JSON bodies are still parsed and HTTP errors carry the status', async () => {
         global.fetch.mockResolvedValueOnce({ ok: true, text: async () => '{"a":1}' });
         await expect(patientAPI.getMe()).resolves.toEqual({ a: 1 });

         global.fetch.mockResolvedValueOnce({ ok: false, status: 403, json: async () => ({ message: 'nope' }) });
         await expect(patientAPI.getById(9)).rejects.toMatchObject({ message: 'nope', status: 403 });
      });
   });
});
