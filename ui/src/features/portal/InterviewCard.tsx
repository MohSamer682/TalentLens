import { Building2, CalendarClock, MapPin, Video } from 'lucide-react';
import { KIND_LABEL } from '@/features/pipeline/pipeline';
import type { PortalInterview } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { cn } from '@/lib/utils';
import { formatSlot, meetingLink } from './portal';

function Place({ interview }: { interview: PortalInterview }) {
  const link = interview.placeType === 'ONLINE' && interview.status !== 'CANCELLED' ? meetingLink(interview.location) : null;
  const Icon = interview.placeType === 'ONLINE' ? Video : interview.placeType === 'HQ' ? Building2 : MapPin;
  return (
    <p className="flex items-start gap-2 text-sm">
      <Icon className="mt-0.5 size-4 shrink-0 text-ink-muted" aria-hidden="true" />
      {interview.placeType === 'HQ' && <span>At the office</span>}
      {interview.placeType === 'OTHER' && <span className="min-w-0 whitespace-pre-line break-words">{interview.location}</span>}
      {interview.placeType === 'ONLINE' &&
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

export function InterviewCard({ interview }: { interview: PortalInterview }) {
  const cancelled = interview.status === 'CANCELLED';
  const who = interview.interviewerHeadline ? `${interview.interviewerName}, ${interview.interviewerHeadline}` : interview.interviewerName;
  return (
    <article className={cn('plate space-y-3 p-4', cancelled && 'opacity-75')}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className={cn('min-w-0 text-[15px] font-semibold', cancelled && 'line-through')}>
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
    </article>
  );
}
