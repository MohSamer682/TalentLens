import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { RescheduleSheet } from './RescheduleSheet';
import { portalApi } from '@/api/client';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return { ...actual, portalApi: { requestReschedule: vi.fn() } };
});

const api = vi.mocked(portalApi);
const local = (daysAhead: number) => {
  const d = new Date(Date.now() + daysAhead * 86_400_000);
  d.setSeconds(0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('RescheduleSheet', () => {
  it('RescheduleSheet requires reason and at least one future slot', async () => {
    const onSent = vi.fn();
    render(<RescheduleSheet interviewId="i1" onSent={onSent} onCancel={() => {}} />);
    const send = () => fireEvent.click(screen.getByRole('button', { name: 'Send request' }));

    send();
    expect(await screen.findByText('Tell your recruiter why you need to move it')).toBeTruthy();
    expect(api.requestReschedule).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Why do you need to move it?'), { target: { value: 'Exam that morning' } });
    send();
    expect(await screen.findByText('Suggest at least one time')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Time 1'), { target: { value: local(-1) } });
    send();
    expect(await screen.findByText('Pick times in the future')).toBeTruthy();
    expect(api.requestReschedule).not.toHaveBeenCalled();

    api.requestReschedule.mockResolvedValue({ success: true, message: '', data: { id: 'r1', status: 'PENDING' } } as never);
    const later = local(3);
    fireEvent.change(screen.getByLabelText('Time 1'), { target: { value: later } });
    send();
    await vi.waitFor(() => expect(onSent).toHaveBeenCalled());
    expect(api.requestReschedule).toHaveBeenCalledWith('i1', 'Exam that morning', [new Date(later).toISOString()]);
  });
});
