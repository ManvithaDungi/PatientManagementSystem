import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { useAuth } from '../../contexts/AuthContext';

jest.mock('../../contexts/AuthContext', () => ({
    useAuth: jest.fn(),
}));

const renderWithRoute = (initialPath, allowedRoles) => {
    return render(
        <MemoryRouter initialEntries={[initialPath]}>
            <Routes>
                <Route path="/login" element={<div>Login Page</div>} />
                <Route path="/unauthorized" element={<div>Unauthorized Page</div>} />
                <Route
                    path="/dashboard/doctor"
                    element={
                        <ProtectedRoute allowedRoles={allowedRoles}>
                            <div>Doctor Dashboard</div>
                        </ProtectedRoute>
                    }
                />
            </Routes>
        </MemoryRouter>
    );
};

describe('ProtectedRoute', () => {
    afterEach(() => {
        jest.clearAllMocks();
    });

    test('redirects unauthenticated users to /login', () => {
        useAuth.mockReturnValue({ user: null, loading: false });

        renderWithRoute('/dashboard/doctor', ['DOCTOR']);

        expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    test('renders children when the role matches', () => {
        useAuth.mockReturnValue({ user: { role: 'DOCTOR' }, loading: false });

        renderWithRoute('/dashboard/doctor', ['DOCTOR']);

        expect(screen.getByText('Doctor Dashboard')).toBeInTheDocument();
    });

    test('redirects to /unauthorized when the role does not match', () => {
        useAuth.mockReturnValue({ user: { role: 'PATIENT' }, loading: false });

        renderWithRoute('/dashboard/doctor', ['DOCTOR']);

        expect(screen.getByText('Unauthorized Page')).toBeInTheDocument();
    });

    test('fails closed to /unauthorized when the role is missing/unrecognized, not the default dashboard', () => {
        useAuth.mockReturnValue({ user: { role: undefined }, loading: false });

        renderWithRoute('/dashboard/doctor', ['DOCTOR']);

        expect(screen.getByText('Unauthorized Page')).toBeInTheDocument();
    });

    test('shows a loading state instead of redirecting while auth is resolving', () => {
        useAuth.mockReturnValue({ user: null, loading: true });

        const { container } = renderWithRoute('/dashboard/doctor', ['DOCTOR']);

        expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
        expect(screen.queryByText('Doctor Dashboard')).not.toBeInTheDocument();
        expect(container.querySelector('.animate-spin')).toBeInTheDocument();
    });
});
