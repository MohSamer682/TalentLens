import { useState } from 'react';
import { CalendarClock } from 'lucide-react';
import type { RescheduleRequest } from '@/types';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { formatRelativeDate } from '@/lib/utils';
import { toLocalInput } from './format';

const slotLabel = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' });

interface RescheduleRequestCardProps {
  request: RescheduleRequest;
  canManage: boolean;
  busy?: boolean;
  onAccept: (request: RescheduleRequest, scheduledStart: string) => void;
  onDecline: (request: RescheduleRequest, note: string) => void;
}

export function RescheduleRequestCard({ request, canManage, busy = false, onAccept, onDecline }: RescheduleRequestCardProps) {
  const [mode, setMode] = useState<'idle' | 'other' | 'decline'>('idle');
  const [other, setOther] = useState('');
  const [note, setNote] = useState('');
  const idBase = `reschedule-${request.id}`;
  const now = Date.now();

  return (
    <div className="space-y-3 rounded-lg border border-warn/30 bg-warn-soft p-3 text-sm">
      <div className="flex items-start gap-2">
        <CalendarClock className="mt-0.5 size-4 shrink-0 text-warn" aria-hidden="true" />
        <div className="min-w-0 space-y-0.5">
          <p className="break-words font-semibold text-ink">
            Reschedule requested by {request.requestedByName}: {request.reason}
          </p>
          <p className="text-xs text-ink-muted">Asked {formatRelativeDate(request.createdAt)}</p>
        </div>
      </div>

      {canManage ? (
        <>
          <div className="flex flex-wrap gap-2">
            {request.proposedStarts.map((slot) => (
              <Button key={slot} size="sm" disabled={busy || new Date(slot).getTime() <= now} onClick={() => onAccept(request, slot)}>
                Move to {slotLabel(slot)}
              </Button>
            ))}
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => setMode(mode === 'other' ? 'idle' : 'other')}>
              Pick another time
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => setMode(mode === 'decline' ? 'idle' : 'decline')}>
              Decline
            </Button>
          </div>

          {mode === 'other' && (
            <div className="flex flex-wrap items-end gap-2">
              <div className="space-y-1">
                <label htmlFor={`${idBase}-other`} className="block text-[13px] font-semibold">
                  New time
                </label>
                <Input id={`${idBase}-other`} type="datetime-local" min={toLocalInput(new Date(now).toISOString())} value={other} onChange={(e) => setOther(e.target.value)} />
              </div>
              <Button size="sm" disabled={busy || !other || new Date(other).getTime() <= now} onClick={() => onAccept(request, new Date(other).toISOString())}>
                Move interview
              </Button>
            </div>
          )}

          {mode === 'decline' && (
            <div className="space-y-2">
              <label htmlFor={`${idBase}-note`} className="block text-[13px] font-semibold">
                Tell the candidate why
              </label>
              <Textarea id={`${idBase}-note`} value={note} maxLength={1000} onChange={(e) => setNote(e.target.value)} className="min-h-[72px]" />
              <Button size="sm" variant="secondary" disabled={busy || !note.trim()} onClick={() => onDecline(request, note.trim())}>
                Send decline
              </Button>
            </div>
          )}
        </>
      ) : (
        <p className="text-xs text-ink-muted">Suggested: {request.proposedStarts.map(slotLabel).join('; ')}</p>
      )}
    </div>
  );
}
