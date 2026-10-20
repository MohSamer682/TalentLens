import type { CandidateStageEvent } from '@/types';
import { cn, formatRelativeDate } from '@/lib/utils';
import { eventLabel } from './format';

export function Timeline({ timeline }: { timeline: CandidateStageEvent[] }) {
  return (
  <div className="space-y-3 border-t border-line pt-4">
    <h3 className="text-[15px] font-semibold text-ink">Timeline</h3>
    {timeline.length === 0 ? (
      <p className="text-sm italic text-ink-muted">No stage events recorded yet.</p>
    ) : (
      <ol className="relative ml-2 space-y-4 border-l-2 border-line pl-4">
        {timeline.map((evt) => {
          const isShortlisted = evt.stage === 'SHORTLISTED';
          return (
            <li key={evt.id} className="relative">
              <span aria-hidden="true" className={cn('absolute -left-[21px] top-1 size-2 rounded-full', isShortlisted ? 'bg-mark' : 'bg-ink-muted')} />
              <div className="text-xs">
                <span className={cn('font-semibold', isShortlisted ? 'text-mark-text' : 'text-ink')}>{eventLabel(evt.stage)}</span>
                <span className="text-ink-muted">
                  {' '}
                  · {evt.summary || 'Stage updated'}
                  {evt.actorName ? `, by ${evt.actorName}` : ''} · {formatRelativeDate(evt.createdAt)}
                </span>
              </div>
            </li>
          );
        })}
      </ol>
    )}
  </div>
  );
}
