import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { RescheduleRequestCard } from './RescheduleRequestCard';
import type { RescheduleRequest } from '@/types';

afterEach(cleanup);

const request: RescheduleRequest = {
  id: 'r1',
  interviewId: 'i1',
  status: 'PENDING',
  reason: 'Exam that morning',
  proposedStarts: ['2026-10-28T08:00:00Z', '2026-10-29T12:30:00Z'],
  resolutionNote: null,
  createdAt: '2026-10-20T10:00:00Z',
  resolvedAt: null,
  version: 0,
  requestedByName: 'Nour Hassan',
};

describe('RescheduleRequestCard', () => {
  it('request card offers each proposed slot as a button', () => {
    const onAccept = vi.fn();
    render(<RescheduleRequestCard request={request} canManage onAccept={onAccept} onDecline={vi.fn()} />);
    expect(screen.getByText('Reschedule requested by Nour Hassan: Exam that morning')).toBeTruthy();
    const moves = screen.getAllByRole('button', { name: /^Move to / });
    expect(moves).toHaveLength(2);
    fireEvent.click(moves[1]);
    expect(onAccept).toHaveBeenCalledWith(request, '2026-10-29T12:30:00Z');
  });

  it('needs a reason to decline', async () => {
    const onDecline = vi.fn();
    render(<RescheduleRequestCard request={request} canManage onAccept={vi.fn()} onDecline={onDecline} />);
    fireEvent.click(screen.getByRole('button', { name: 'Decline' }));
    const confirm = screen.getByRole('button', { name: 'Send decline' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Tell the candidate why'), { target: { value: 'Panel is fully booked' } });
    fireEvent.click(confirm);
    expect(onDecline).toHaveBeenCalledWith(request, 'Panel is fully booked');
  });

  it('lets HR pick another time', () => {
    const onAccept = vi.fn();
    render(<RescheduleRequestCard request={request} canManage onAccept={onAccept} onDecline={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Pick another time' }));
    fireEvent.change(screen.getByLabelText('New time'), { target: { value: '2026-11-02T09:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Move interview' }));
    expect(onAccept).toHaveBeenCalledWith(request, new Date('2026-11-02T09:00').toISOString());
  });

  it('is read-only without pipeline rights', () => {
    render(<RescheduleRequestCard request={request} canManage={false} onAccept={vi.fn()} onDecline={vi.fn()} />);
    expect(screen.queryByRole('button')).toBeNull();
  });
});
