import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import InvitePage from './InvitePage';
import { ApiRequestError, portalApi } from '@/api/client';
import { useAuthStore } from '@/features/auth/authStore';
import type { User } from '@/types';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return { ...actual, portalApi: { preview: vi.fn(), accept: vi.fn(), claim: vi.fn() } };
});

const api = vi.mocked(portalApi);

function SignIn() {
  const from = (useLocation().state as { from?: { pathname: string; hash?: string } })?.from;
  return <p>Sign in page, back to {from ? from.pathname + (from.hash ?? '') : 'nowhere'}</p>;
}

const at = () =>
  render(
    <MemoryRouter initialEntries={[{ pathname: '/invite', hash: '#tok' }]}>
      <Routes>
        <Route path="/invite" element={<InvitePage />} />
        <Route path="/portal" element={<p>Portal home</p>} />
        <Route path="/login" element={<SignIn />} />
      </Routes>
    </MemoryRouter>
  );

const preview = (accountExists: boolean) =>
  api.preview.mockResolvedValue({ success: true, message: '', data: { jobTitle: 'Senior Backend Engineer', candidateName: 'Nour Hassan', accountExists } } as never);

const signedInAs = (role: User['role']) =>
  useAuthStore.setState({ token: 'jwt', user: { id: 'u1', email: 'me@mail.io', fullName: 'Me', role, permissions: [] } as unknown as User });

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  useAuthStore.setState({ token: null, user: null });
});

describe('InvitePage', () => {
  it('shows the gone state on 410', async () => {
    api.preview.mockRejectedValue(new ApiRequestError('This link is no longer valid. Ask your recruiter for a new one.', 'GONE', '', 410));
    at();
    expect(await screen.findByText('This link is no longer valid. Ask your recruiter for a new one.')).toBeTruthy();
    expect(screen.queryByLabelText('New password')).toBeNull();
  });

  it('asks a first-time candidate to set a password for the job', async () => {
    api.preview.mockResolvedValue({ success: true, message: '', data: { jobTitle: 'Senior Backend Engineer', candidateName: 'Nour Hassan', accountExists: false } } as never);
    at();
    expect(await screen.findByText('Set a password to see your application for Senior Backend Engineer')).toBeTruthy();
    expect(screen.getByLabelText('New password')).toBeTruthy();
  });

  it('sends a returning candidate to sign in first', async () => {
    api.preview.mockResolvedValue({ success: true, message: '', data: { jobTitle: 'Senior Backend Engineer', candidateName: 'Nour', accountExists: true } } as never);
    at();
    expect(await screen.findByRole('link', { name: 'Sign in' })).toBeTruthy();
    expect(screen.queryByLabelText('New password')).toBeNull();
  });

  it('reads the token from the link fragment', async () => {
    preview(false);
    at();
    await screen.findByLabelText('New password');
    expect(api.preview).toHaveBeenCalledWith('tok');
  });

  it('sets the password, signs in and opens the portal', async () => {
    preview(false);
    api.accept.mockResolvedValue({ success: true, message: '', data: { token: 'jwt', user: { id: 'u1', email: 'n@mail.io', fullName: 'Nour', role: 'CANDIDATE', permissions: [] } } } as never);
    at();
    fireEvent.change(await screen.findByLabelText('New password'), { target: { value: 'NourPass2026' } });
    fireEvent.change(screen.getByLabelText('Repeat new password'), { target: { value: 'NourPass2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Set password and continue' }));
    expect(await screen.findByText('Portal home')).toBeTruthy();
    expect(api.accept).toHaveBeenCalledWith('tok', 'NourPass2026');
    expect(useAuthStore.getState().token).toBe('jwt');
  });

  it('shows the reason when accepting is refused', async () => {
    preview(false);
    api.accept.mockRejectedValue(new ApiRequestError('You already have an account. Sign in, then open this link again.', 'CONFLICT', '', 409));
    at();
    fireEvent.change(await screen.findByLabelText('New password'), { target: { value: 'NourPass2026' } });
    fireEvent.change(screen.getByLabelText('Repeat new password'), { target: { value: 'NourPass2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Set password and continue' }));
    expect(await screen.findByText('You already have an account. Sign in, then open this link again.')).toBeTruthy();
  });

  it('keeps the invite when going to sign in from the footer', async () => {
    preview(false);
    at();
    fireEvent.click(await screen.findByRole('link', { name: 'Sign in' }));
    expect(await screen.findByText('Sign in page, back to /invite#tok')).toBeTruthy();
  });

  it('asks someone already signed in to sign out before making a new account', async () => {
    preview(false);
    signedInAs('RECRUITER');
    at();
    expect(await screen.findByText(/you're signed in as me@mail.io/i)).toBeTruthy();
    expect(screen.queryByLabelText('New password')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(await screen.findByLabelText('New password')).toBeTruthy();
    expect(useAuthStore.getState().token).toBeNull();
  });

  it('lets a signed-in candidate add the application', async () => {
    preview(true);
    signedInAs('CANDIDATE');
    api.claim.mockResolvedValue({ success: true, message: '', data: null } as never);
    at();
    fireEvent.click(await screen.findByRole('button', { name: 'Add this application to my account' }));
    expect(await screen.findByText('Portal home')).toBeTruthy();
    expect(api.claim).toHaveBeenCalledWith('tok');
  });

  it('explains a claim from the wrong account', async () => {
    preview(true);
    signedInAs('CANDIDATE');
    api.claim.mockRejectedValue(new ApiRequestError('This link is for a different email. Sign out, then sign in with the email your recruiter has.', 'CONFLICT', '', 409));
    at();
    fireEvent.click(await screen.findByRole('button', { name: 'Add this application to my account' }));
    expect(await screen.findByText(/this link is for a different email/i)).toBeTruthy();
  });
});
