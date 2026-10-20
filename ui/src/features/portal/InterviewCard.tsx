import { useState } from 'react';
import { Building2, CalendarClock, MapPin, Video } from 'lucide-react';
import { apiErrorMessage, portalApi } from '@/api/client';
import { KIND_LABEL } from '@/features/pipeline/pipeline';
import type { PortalInterview } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';
import { formatSlot, formatStart, meetingLink } from './portal';
import { RescheduleSheet } from './RescheduleSheet';

function Place({ interview }: { interview: PortalInterview }) {
  // Interviews saved before places existed hold their meeting link as plain "other" text
  const type = interview.placeType === 'OTHER' && meetingLink(interview.location) ? 'ONLINE' : interview.placeType;
  const link = type === 'ONLINE' && interview.status !== 'CANCELLED' ? meetingLink(interview.location) : null;
  const Icon = type === 'ONLINE' ? Video : type === 'HQ' ? Building2 : MapPin;
  return (
    <p className="flex items-start gap-2 text-sm">
      <Icon className="mt-0.5 size-4 shrink-0 text-ink-muted" aria-hidden="true" />
      {type === 'HQ' && <span>At the office</span>}
      {type === 'OTHER' && <span className="min-w-0 whitespace-pre-line break-words">{interview.location}</span>}
      {type === 'ONLINE' &&
        (link ? (
          <span>
            Online:{' '}
            <a href={link} target="_blank" rel="noopener noreferrer" className="font-semibold text-accent-text underline-offset-2 hover:underline">
              Join the meeting<span className="sr-only"> (opens in a new tab)</span>
            </a>
          </span>
        ) : (
          <span>Online</span>
        ))}
    </p>
  );
}

function RequestState({ interview, onChanged }: { interview: PortalInterview; onChanged?: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const request = interview.reschedule;
  if (!request || request.status === 'WITHDRAWN') return null;

  const withdraw = async () => {
    setBusy(true);
    setError(null);
    try {
      await portalApi.withdrawReschedule(request.id);
      onChanged?.();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not withdraw your request'));
    } finally {
      setBusy(false);
    }
  };

  if (request.status === 'PENDING')
    return (
      <div className="space-y-2 rounded-lg border border-info/25 bg-info-soft px-3 py-2.5 text-sm" role="status">
        <p className="font-semibold">Request sent. Your recruiter will reply here.</p>
        {request.proposedStarts.length > 0 && (
          <p className="text-ink-muted">You suggested {request.proposedStarts.map((t) => formatStart(t)).join('; ')}.</p>
        )}
        {error && <p className="text-danger">{error}</p>}
        <Button variant="secondary" size="sm" onClick={withdraw} disabled={busy}>
          {busy ? 'Withdrawing…' : 'Withdraw request'}
        </Button>
      </div>
    );
  if (request.status === 'DECLINED')
    return (
      <p className="rounded-lg border border-line bg-sunken px-3 py-2.5 text-sm">
        Your recruiter couldn't move it: {request.resolutionNote}
      </p>
    );
  return <p className="text-sm text-success">Your recruiter moved this interview to the time above.</p>;
}

export function InterviewCard({ interview, onChanged }: { interview: PortalInterview; onChanged?: () => void }) {
  const [asking, setAsking] = useState(false);
  const cancelled = interview.status === 'CANCELLED';
  const movable =
    interview.status === 'SCHEDULED' && new Date(interview.scheduledStart).getTime() > Date.now() && interview.reschedule?.status !== 'PENDING';
  const who = interview.interviewerHeadline ? `${interview.interviewerName}, ${interview.interviewerHeadline}` : interview.interviewerName;
  return (
    <article className={cn('plate space-y-3 p-4', cancelled && 'opacity-75')}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className={cn('min-w-0 break-words text-[15px] font-semibold', cancelled && 'line-through')}>
          {KIND_LABEL[interview.kind]} interview with {who}
        </h3>
        {cancelled && <Badge>Cancelled</Badge>}
        {interview.status === 'COMPLETED' && <Badge tone="success">Done</Badge>}
      </div>
      <p className="flex items-start gap-2 text-sm">
        <CalendarClock className="mt-0.5 size-4 shrink-0 text-ink-muted" aria-hidden="true" />
        <span>{formatSlot(interview.scheduledStart, interview.durationMinutes)}</span>
      </p>
      <Place interview={interview} />
      <RequestState interview={interview} onChanged={onChanged} />
      {movable &&
        (asking ? (
          <RescheduleSheet
            interviewId={interview.id}
            onCancel={() => setAsking(false)}
            onSent={() => {
              setAsking(false);
              onChanged?.();
            }}
          />
        ) : (
          <Button variant="secondary" size="sm" onClick={() => setAsking(true)}>
            Request a different time
          </Button>
        ))}
    </article>
  );
}
