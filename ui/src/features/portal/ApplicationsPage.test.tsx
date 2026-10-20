import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ApplicationsPage from './ApplicationsPage';
import { portalApi } from '@/api/client';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return { ...actual, portalApi: { applications: vi.fn() } };
});

const api = vi.mocked(portalApi);
const page = (content: unknown[]) =>
  ({ success: true, message: '', data: { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 } }) as never;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('ApplicationsPage', () => {
  it('ApplicationsPage empty state', async () => {
    api.applications.mockResolvedValue(page([]));
    render(<MemoryRouter><ApplicationsPage /></MemoryRouter>);
    expect(await screen.findByText('No applications yet. When a recruiter invites you, the job will appear here.')).toBeTruthy();
  });

  it('lists each job with its stage and links to it', async () => {
    api.applications.mockResolvedValue(page([
      { id: 'c1', jobTitle: 'Senior Backend Engineer', positionCode: 'BE-204', stage: 'DECISION_PENDING', nextInterviewAt: null },
    ]));
    render(<MemoryRouter><ApplicationsPage /></MemoryRouter>);
    const link = await screen.findByRole('link', { name: /senior backend engineer/i });
    expect(link.getAttribute('href')).toBe('/portal/applications/c1');
    expect(screen.getByText('Decision in progress')).toBeTruthy();
    expect(screen.getByText('BE-204')).toBeTruthy();
  });
});
