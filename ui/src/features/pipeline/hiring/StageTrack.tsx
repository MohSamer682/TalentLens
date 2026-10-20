import { buildTrack } from '@/features/pipeline/pipeline';
import { cn } from '@/lib/utils';

/** Status line: follows the interviews the recruiter planned. */
export function StageTrack({ track }: { track: ReturnType<typeof buildTrack> }) {
  return (
  <nav aria-label="Pipeline stages" className="overflow-x-auto pb-1">
    <ol className="flex min-w-max items-center gap-2">
      {track.map((s, idx) => (
        <li key={s.key} className="flex items-center">
          <span
            className={cn(
              'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors',
              s.state === 'done' && 'bg-sunken font-semibold text-ink',
              s.state === 'current' && 'border border-accent-text bg-sunken font-semibold text-ink ring-1 ring-accent-text/20',
              s.state === 'todo' && 'border border-dashed border-line-strong text-ink-muted'
            )}
            {...(s.state === 'current' ? { 'aria-current': 'step' } : {})}
          >
            <span
              aria-hidden="true"
              className={cn(
                'size-1.5 shrink-0 rounded-full',
                s.key === 'SHORTLISTED' ? 'bg-mark' : s.state === 'current' ? 'bg-accent-bar' : s.state === 'done' ? 'bg-ink-muted' : 'bg-transparent'
              )}
            />
            {s.label}
          </span>
          {idx < track.length - 1 && (
            <span aria-hidden="true" className="mx-1 text-xs text-ink-muted/50">
              ›
            </span>
          )}
        </li>
      ))}
    </ol>
  </nav>
  );
}
