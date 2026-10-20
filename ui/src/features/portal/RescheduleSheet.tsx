import { useState } from 'react';
import { apiErrorMessage, portalApi } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Field, Input, Textarea } from '@/components/ui/Input';
import { Alert } from '@/components/ui/Feedback';
import { browserZone, zoneLabel } from './portal';

const SLOTS = 3;
const MAX_DAYS_AHEAD = 60;

function problem(reason: string, times: string[]): { field: 'reason' | 'times'; message: string } | null {
  if (!reason.trim()) return { field: 'reason', message: 'Tell your recruiter why you need to move it' };
  const picked = times.filter(Boolean).map((t) => new Date(t).getTime());
  if (picked.length === 0) return { field: 'times', message: 'Suggest at least one time' };
  const now = Date.now();
  if (picked.some((t) => t <= now)) return { field: 'times', message: 'Pick times in the future' };
  if (picked.some((t) => t > now + MAX_DAYS_AHEAD * 86_400_000)) return { field: 'times', message: `Pick times within the next ${MAX_DAYS_AHEAD} days` };
  if (new Set(picked).size !== picked.length) return { field: 'times', message: 'Each time should be different' };
  return null;
}

export function RescheduleSheet({ interviewId, onSent, onCancel }: { interviewId: string; onSent: () => void; onCancel: () => void }) {
  const [reason, setReason] = useState('');
  const [times, setTimes] = useState<string[]>(Array(SLOTS).fill(''));
  const [shown, setShown] = useState<ReturnType<typeof problem>>(null);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const found = problem(reason, times);
    setShown(found);
    setError(null);
    if (found) return;
    setSending(true);
    try {
      await portalApi.requestReschedule(
        interviewId,
        reason.trim(),
        times.filter(Boolean).map((t) => new Date(t).toISOString())
      );
      onSent();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not send your request'));
    } finally {
      setSending(false);
    }
  };

  const reasonId = `reschedule-reason-${interviewId}`;
  return (
    <form onSubmit={submit} noValidate className="space-y-4 rounded-lg border border-line bg-surface p-4">
      <Field id={reasonId} label="Why do you need to move it?" error={shown?.field === 'reason' ? shown.message : undefined}>
        <Textarea
          id={reasonId}
          value={reason}
          maxLength={1000}
          onChange={(e) => setReason(e.target.value)}
          aria-invalid={shown?.field === 'reason'}
          aria-describedby={shown?.field === 'reason' ? `${reasonId}-error` : undefined}
        />
      </Field>
      <fieldset className="space-y-2" aria-describedby={`${reasonId}-times`}>
        <legend className="text-sm font-semibold">Times that work for you</legend>
        <p id={`${reasonId}-times`} className="text-[13px] text-ink-muted">
          Up to three, in {zoneLabel(browserZone())}. Your recruiter picks one or suggests another.
        </p>
        <div className="grid gap-3 sm:grid-cols-3">
          {times.map((value, i) => (
            <div key={i} className="space-y-1">
              <label htmlFor={`${reasonId}-slot-${i}`} className="block text-[13px] text-ink-muted">
                Time {i + 1}
              </label>
              <Input
                id={`${reasonId}-slot-${i}`}
                type="datetime-local"
                value={value}
                aria-invalid={shown?.field === 'times'}
                onChange={(e) => setTimes((all) => all.map((t, j) => (j === i ? e.target.value : t)))}
              />
            </div>
          ))}
        </div>
        {shown?.field === 'times' && <p className="text-[13px] text-danger">{shown.message}</p>}
      </fieldset>
      {error && <Alert>{error}</Alert>}
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={sending}>
          Keep this time
        </Button>
        <Button type="submit" disabled={sending}>
          {sending ? 'Sending…' : 'Send request'}
        </Button>
      </div>
    </form>
  );
}
