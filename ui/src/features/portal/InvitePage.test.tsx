import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import InvitePage from './InvitePage';
import { ApiRequestError, portalApi } from '@/api/client';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return { ...actual, portalApi: { preview: vi.fn(), accept: vi.fn(), claim: vi.fn() } };
});

const api = vi.mocked(portalApi);

const at = () =>
  render(
    <MemoryRouter initialEntries={['/invite/tok']}>
      <Routes>
        <Route path="/invite/:token" element={<InvitePage />} />
      </Routes>
    </MemoryRouter>
  );

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
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
});
