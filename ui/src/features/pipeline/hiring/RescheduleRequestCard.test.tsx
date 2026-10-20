import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { RescheduleRequestCard } from './RescheduleRequestCard';
import type { RescheduleRequest } from '@/types';
import { toLocalInput } from './format';

afterEach(cleanup);

const inDays = (days: number) => new Date(Math.floor(Date.now() / 60_000) * 60_000 + days * 86_400_000).toISOString();
const slots = [inDays(8), inDays(9)];

const request: RescheduleRequest = {
  id: 'r1',
  interviewId: 'i1',
  status: 'PENDING',
  reason: 'Exam that morning',
  proposedStarts: slots,
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
    expect(onAccept).toHaveBeenCalledWith(request, slots[1]);
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
    const later = toLocalInput(inDays(12));
    fireEvent.change(screen.getByLabelText('New time'), { target: { value: later } });
    fireEvent.click(screen.getByRole('button', { name: 'Move interview' }));
    expect(onAccept).toHaveBeenCalledWith(request, new Date(later).toISOString());
  });

  it('will not move to a time that has passed', () => {
    const onAccept = vi.fn();
    render(<RescheduleRequestCard request={{ ...request, proposedStarts: [inDays(-1)] }} canManage onAccept={onAccept} onDecline={vi.fn()} />);
    expect((screen.getByRole('button', { name: /^Move to / }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Pick another time' }));
    fireEvent.change(screen.getByLabelText('New time'), { target: { value: toLocalInput(inDays(-2)) } });
    expect((screen.getByRole('button', { name: 'Move interview' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('is read-only without pipeline rights', () => {
    render(<RescheduleRequestCard request={request} canManage={false} onAccept={vi.fn()} onDecline={vi.fn()} />);
    expect(screen.queryByRole('button')).toBeNull();
  });
});
