import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { InterviewCard } from './InterviewCard';
import type { PortalInterview } from '@/types';

afterEach(cleanup);

const base: PortalInterview = {
  id: 'i1',
  kind: 'TECHNICAL',
  interviewerName: 'Sara Adel',
  interviewerHeadline: 'Senior Backend Engineer',
  scheduledStart: '2026-10-28T08:00:00Z',
  durationMinutes: 60,
  placeType: 'ONLINE',
  location: 'https://meet.example.com/abc',
  status: 'SCHEDULED',
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
});
