import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { PortalAccessPanel } from './PortalAccessPanel';
import { portalApi } from '@/api/client';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return {
    ...actual,
    portalApi: {
      access: vi.fn(),
      issueInvite: vi.fn(),
      revokeInvite: vi.fn(),
      createAccount: vi.fn(),
    },
  };
});

const api = vi.mocked(portalApi);

beforeEach(() => {
  api.access.mockResolvedValue({ success: true, message: '', data: {} } as never);
  api.issueInvite.mockResolvedValue({
    success: true, message: '', data: { url: 'http://localhost:5173/invite/tok', expiresAt: '2026-10-27T10:00:00Z' },
  } as never);
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('PortalAccessPanel', () => {
  it('shows Send link only from Shortlisted on', async () => {
    const { rerender } = render(<PortalAccessPanel sessionId="s1" candidateId="c1" stage="NONE" email="n@mail.io" canManage />);
    expect(screen.queryByRole('button', { name: 'Send access link' })).toBeNull();
    expect(screen.getByText(/after you shortlist/i)).toBeTruthy();

    rerender(<PortalAccessPanel sessionId="s1" candidateId="c1" stage="SHORTLISTED" email="n@mail.io" canManage />);
    expect(await screen.findByRole('button', { name: 'Send access link' })).toBeTruthy();
  });

  it('asks for an email before offering access', async () => {
    render(<PortalAccessPanel sessionId="s1" candidateId="c1" stage="SHORTLISTED" email="" canManage />);
    expect(await screen.findByText(/no email/i)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Send access link' })).toBeNull();
  });

  it('shows the new link once with a copy button and the warning', async () => {
    render(<PortalAccessPanel sessionId="s1" candidateId="c1" stage="SHORTLISTED" email="n@mail.io" canManage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Send access link' }));
    expect((await screen.findByLabelText('Access link') as HTMLInputElement).value).toBe('http://localhost:5173/invite/tok');
    expect(screen.getByRole('button', { name: 'Copy link' })).toBeTruthy();
    expect(screen.getByText(/anyone with the link can set the password/i)).toBeTruthy();
  });

  it('shows the linked account instead of the actions', async () => {
    api.access.mockResolvedValue({
      success: true, message: '', data: { account: { email: 'n@mail.io', createdAt: '2026-10-20T10:00:00Z', mustChangePassword: false, active: true } },
    } as never);
    render(<PortalAccessPanel sessionId="s1" candidateId="c1" stage="SHORTLISTED" email="n@mail.io" canManage />);
    expect(await screen.findByText(/can sign in as n@mail.io/i)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Send access link' })).toBeNull();
  });

  it('is read-only without pipeline rights', async () => {
    render(<PortalAccessPanel sessionId="s1" candidateId="c1" stage="SHORTLISTED" email="n@mail.io" canManage={false} />);
    expect(await screen.findByText(/no portal access yet/i)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Send access link' })).toBeNull();
  });
});
