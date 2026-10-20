import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import StaffLogin from './StaffLogin';
import { ApiRequestError, authApi } from '@/api/client';
import { CandidateRoute, PermissionRoute, SessionsRoute } from './ProtectedRoute';
import { useAuthStore } from './authStore';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return { ...actual, authApi: { staffLogin: vi.fn(), login: vi.fn() } };
});

const api = vi.mocked(authApi);

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  useAuthStore.setState({ token: null, user: null, door: 'staff' });
});

describe('StaffLogin', () => {
  it('StaffLogin shows the WRONG_PORTAL hint with a link to /login', async () => {
    api.staffLogin.mockRejectedValue(new ApiRequestError('This is a candidate account. Sign in at /login.', 'WRONG_PORTAL', undefined, 403));
    render(
      <MemoryRouter initialEntries={['/admins']}>
        <StaffLogin />
      </MemoryRouter>
    );
    fireEvent.change(screen.getByLabelText('Work email'), { target: { value: 'nour@mail.io' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'NourPass2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('This is a candidate account. Sign in at /login.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Go to candidate sign-in' }).getAttribute('href')).toBe('/login');
    expect(api.login).not.toHaveBeenCalled();
    expect(useAuthStore.getState().token).toBeNull();
  });

  it('a wrong password shows no door hint', async () => {
    api.staffLogin.mockRejectedValue(new ApiRequestError('Invalid email or password', 'AUTHENTICATION_FAILED', undefined, 401));
    render(<MemoryRouter><StaffLogin /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText('Work email'), { target: { value: 'sara@talentlens.io' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'nope' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Invalid email or password')).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Go to candidate sign-in' })).toBeNull();
  });
});

describe('signed-out routes', () => {
  const at = (path: string, element: React.ReactNode) =>
    render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/admins" element={<p>Staff door</p>} />
          <Route path="/login" element={<p>Candidate door</p>} />
          <Route path={path} element={element} />
        </Routes>
      </MemoryRouter>
    );

  it('signed-out staff route redirects to /admins', () => {
    at('/dashboard', <SessionsRoute><p>Sessions</p></SessionsRoute>);
    expect(screen.getByText('Staff door')).toBeTruthy();
    cleanup();
    at('/admin/roles', <PermissionRoute permission="ROLE_MANAGE"><p>Roles</p></PermissionRoute>);
    expect(screen.getByText('Staff door')).toBeTruthy();
  });

  it('signed-out portal route redirects to /login', () => {
    at('/portal', <CandidateRoute><p>Portal</p></CandidateRoute>);
    expect(screen.getByText('Candidate door')).toBeTruthy();
  });
});
