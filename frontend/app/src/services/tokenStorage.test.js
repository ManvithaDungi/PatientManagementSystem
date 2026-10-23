import { login, getCurrentSession, logout } from './supabaseAuth';
import { getAccessToken, clearAccessToken } from './tokenStore';

describe('access token storage', () => {
  beforeEach(() => {
    localStorage.clear();
    clearAccessToken();
    global.fetch = jest.fn();
  });

  it('keeps the access token in memory only, never in localStorage', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ accessToken: 'secret-jwt', role: 'PATIENT', userId: 7, email: 'a@b.co' }),
    });

    await login('a@b.co', 'pw');

    expect(getAccessToken()).toBe('secret-jwt');
    const stored = localStorage.getItem('secure_health_user');
    expect(stored).toContain('a@b.co');
    expect(stored).not.toContain('secret-jwt');
    expect(JSON.parse(stored).accessToken).toBeUndefined();
  });

  it('restores the token from the refresh cookie after a reload', async () => {
    localStorage.setItem('secure_health_user', JSON.stringify({ email: 'a@b.co', role: 'PATIENT' }));
    global.fetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ accessToken: 'fresh-jwt' }) })
      // identity is re-read from the server, not trusted from localStorage
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 7, email: 'a@b.co', role: 'PATIENT' }) });

    const session = await getCurrentSession();

    expect(global.fetch.mock.calls[0][0]).toMatch(/\/auth\/refresh-token$/);
    expect(global.fetch.mock.calls[1][0]).toMatch(/\/auth\/me$/);
    expect(session.user.email).toBe('a@b.co');
    expect(getAccessToken()).toBe('fresh-jwt');
  });

  it('uses the role reported by the server, ignoring a tampered stored role', async () => {
    localStorage.setItem('secure_health_user', JSON.stringify({ email: 'a@b.co', role: 'ADMIN' }));
    global.fetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ accessToken: 'fresh-jwt' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 7, email: 'a@b.co', role: 'PATIENT' }) });

    const session = await getCurrentSession();

    expect(session.user.role).toBe('PATIENT');
    expect(JSON.parse(localStorage.getItem('secure_health_user')).role).toBe('PATIENT');
  });

  it('drops the session when the identity check fails', async () => {
    localStorage.setItem('secure_health_user', JSON.stringify({ email: 'a@b.co', role: 'ADMIN' }));
    global.fetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ accessToken: 'fresh-jwt' }) })
      .mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) });

    expect(await getCurrentSession()).toBeNull();
    expect(localStorage.getItem('secure_health_user')).toBeNull();
  });

  it('drops the stored profile when the refresh cookie is no longer valid', async () => {
    localStorage.setItem('secure_health_user', JSON.stringify({ email: 'a@b.co', role: 'PATIENT' }));
    global.fetch.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    expect(await getCurrentSession()).toBeNull();
    expect(localStorage.getItem('secure_health_user')).toBeNull();
  });

  it('clears the in-memory token on logout', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ accessToken: 't', role: 'PATIENT' }) });
    await login('a@b.co', 'pw');
    await logout();
    expect(getAccessToken()).toBeNull();
  });
});
