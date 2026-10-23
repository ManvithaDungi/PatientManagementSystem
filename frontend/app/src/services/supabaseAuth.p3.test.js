import * as authService from './supabaseAuth';
import { getAccessToken, clearAccessToken } from './tokenStore';

const ok = (body) => ({ ok: true, status: 200, json: async () => body, text: async () => JSON.stringify(body) });
const fail = (status, body) => ({ ok: false, status, json: async () => body, text: async () => JSON.stringify(body) });

describe('supabaseAuth: second factor, registration and error messages', () => {
   beforeEach(() => {
      jest.resetAllMocks();
      global.fetch = jest.fn();
      localStorage.clear();
      clearAccessToken();
   });

   test('an OTP_REQUIRED login exposes the pending token and creates no session', async () => {
      global.fetch.mockResolvedValueOnce(ok({ status: 'OTP_REQUIRED', role: 'DOCTOR', userId: 4, pendingToken: 'pend-123' }));

      const result = await authService.signIn('doc@x.test', 'pw');

      expect(result.success).toBe(false);
      expect(result.status).toBe('OTP_REQUIRED');
      expect(result.pendingToken).toBe('pend-123');
      expect(localStorage.getItem('secure_health_user')).toBeNull();
      expect(getAccessToken()).toBeNull();
   });

   test('verify-otp and resend-otp carry the pending token', async () => {
      global.fetch.mockResolvedValueOnce(ok({ accessToken: 'jwt', role: 'DOCTOR', userId: 4 }));
      await authService.verifyOtp('doc@x.test', '123456', 'pend-123');
      expect(JSON.parse(global.fetch.mock.calls[0][1].body)).toEqual({ email: 'doc@x.test', otp: '123456', pendingToken: 'pend-123' });

      global.fetch.mockResolvedValueOnce(ok({ message: 'sent' }));
      await authService.resendOtp('doc@x.test', 'pend-123');
      expect(JSON.parse(global.fetch.mock.calls[1][1].body)).toEqual({ email: 'doc@x.test', pendingToken: 'pend-123' });
   });

   test('registering does not create a signed-in session', async () => {
      global.fetch.mockResolvedValueOnce(ok({ message: 'Registration received. You can now sign in.' }));

      const result = await authService.signUp('new@x.test', 'a-long-passphrase-1', { full_name: 'New Person', date_of_birth: '1990-01-01' });

      expect(result.success).toBe(true);
      expect(localStorage.getItem('secure_health_user')).toBeNull();
      expect(getAccessToken()).toBeNull();
   });

   test('the server message (not a generic fallback) reaches the user on reset failures', async () => {
      global.fetch.mockResolvedValueOnce(fail(400, { message: 'Cannot reuse a recent password. Please choose a different password.' }));
      const reset = await authService.resetPassword('tok', 'pw', 'pw');
      expect(reset.error).toBe('Cannot reuse a recent password. Please choose a different password.');

      global.fetch.mockResolvedValueOnce(fail(400, { valid: false, message: 'Token is invalid or expired' }));
      const validate = await authService.validateResetToken('tok');
      expect(validate.error).toBe('Token is invalid or expired');
   });
});
