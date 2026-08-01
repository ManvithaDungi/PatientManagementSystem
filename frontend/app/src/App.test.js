import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from './App';
import { AuthContext } from './contexts/AuthContext';

// Mocks removed - using patched package.json for resolution

// Mock auth context value
const mockAuthValue = {
  user: null,
  session: null,
  loading: false,
  login: jest.fn(),
  signup: jest.fn(),
  logout: jest.fn(),
  isAuthenticated: false,
};

test('renders learn react link', () => {
  window.history.pushState({}, 'Test page', '/');

  // We wrap App in AuthContext.Provider because Login page calls useAuth
  render(
    <AuthContext.Provider value={mockAuthValue}>
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <App />
      </MemoryRouter>
    </AuthContext.Provider>
  );

  // Debug output to see what's rendering if it fails
  // debug();

  // App renders Login page by default which has text "Sign In"
  expect(screen.getByRole('button', { name: /Sign In/i })).toBeInTheDocument();
});

test('redirects a wrong-role user to /unauthorized instead of their own dashboard', () => {
  window.history.pushState({}, 'Test page', '/dashboard/doctor');

  render(
    <AuthContext.Provider value={{ ...mockAuthValue, user: { role: 'PATIENT' } }}>
      <MemoryRouter
        initialEntries={['/dashboard/doctor']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <App />
      </MemoryRouter>
    </AuthContext.Provider>
  );

  expect(screen.getByText(/access denied/i)).toBeInTheDocument();
});

test('fails closed to /unauthorized for a user with an unrecognized role, not the patient dashboard', () => {
  window.history.pushState({}, 'Test page', '/dashboard/doctor');

  render(
    <AuthContext.Provider value={{ ...mockAuthValue, user: { role: 'SOMETHING_UNKNOWN' } }}>
      <MemoryRouter
        initialEntries={['/dashboard/doctor']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <App />
      </MemoryRouter>
    </AuthContext.Provider>
  );

  expect(screen.getByText(/access denied/i)).toBeInTheDocument();
});
