import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { InterviewCard } from './InterviewCard';
import type { PortalInterview } from '@/types';

afterEach(cleanup);

const base: PortalInterview = {
  id: 'i1',
  kind: 'TECHNICAL',
  interviewerName: 'Sara Adel',
  interviewerHeadline: 'Senior Backend Engineer',
  scheduledStart: new Date(Date.now() + 3 * 86_400_000).toISOString(),
  durationMinutes: 60,
  placeType: 'ONLINE',
  location: 'https://meet.example.com/abc',
  status: 'SCHEDULED',
  reschedule: null,
};

describe('InterviewCard', () => {
  it('names the round, interviewer and their job title', () => {
    render(<InterviewCard interview={base} />);
    expect(screen.getByText('Technical interview with Sara Adel, Senior Backend Engineer')).toBeTruthy();
  });

  it('renders link only for ONLINE with http(s) URL', () => {
    const { rerender } = render(<InterviewCard interview={base} />);
    const link = screen.getByRole('link', { name: /join the meeting/i });
    expect(link.getAttribute('href')).toBe('https://meet.example.com/abc');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');

    rerender(<InterviewCard interview={{ ...base, location: 'javascript:alert(1)' }} />);
    expect(screen.queryByRole('link')).toBeNull();

    rerender(<InterviewCard interview={{ ...base, placeType: 'OTHER', location: 'Cairo office, floor 3' }} />);
    expect(screen.queryByRole('link')).toBeNull();
    expect(screen.getByText('Cairo office, floor 3')).toBeTruthy();

    rerender(<InterviewCard interview={{ ...base, placeType: 'HQ', location: null }} />);
    expect(screen.getByText('At the office')).toBeTruthy();
  });

  it('links an older interview whose place was saved as a plain web address', () => {
    render(<InterviewCard interview={{ ...base, placeType: 'OTHER', location: 'https://meet.example.com/old' }} />);
    expect(screen.getByRole('link', { name: /join the meeting/i }).getAttribute('href')).toBe('https://meet.example.com/old');
  });

  it('flags a cancelled interview', () => {
    render(<InterviewCard interview={{ ...base, status: 'CANCELLED' }} />);
    expect(screen.getByText('Cancelled')).toBeTruthy();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('offers a reschedule only for an upcoming interview without an open request', () => {
    const { rerender } = render(<InterviewCard interview={base} />);
    fireEvent.click(screen.getByRole('button', { name: 'Request a different time' }));
    expect(screen.getByLabelText('Why do you need to move it?')).toBeTruthy();

    rerender(<InterviewCard interview={{ ...base, scheduledStart: '2020-01-01T08:00:00Z' }} />);
    expect(screen.queryByRole('button', { name: 'Request a different time' })).toBeNull();
  });

  it('shows where a request stands', () => {
    const reschedule = { id: 'r1', status: 'PENDING' as const, proposedStarts: [], resolutionNote: null, createdAt: '2026-10-20T10:00:00Z' };
    const { rerender } = render(<InterviewCard interview={{ ...base, reschedule }} />);
    expect(screen.getByText('Request sent. Your recruiter will reply here.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Request a different time' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Withdraw request' })).toBeTruthy();

    rerender(<InterviewCard interview={{ ...base, reschedule: { ...reschedule, status: 'DECLINED', resolutionNote: 'Panel is fully booked' } }} />);
    expect(screen.getByText(/couldn't move it: Panel is fully booked/i)).toBeTruthy();

    rerender(<InterviewCard interview={{ ...base, status: 'CANCELLED', reschedule }} />);
    expect(screen.queryByText('Request sent. Your recruiter will reply here.')).toBeNull();

    rerender(<InterviewCard interview={{ ...base, reschedule: { ...reschedule, status: 'ACCEPTED' } }} />);
    expect(screen.getByText('Your recruiter moved this interview to the time above.')).toBeTruthy();
  });
});
