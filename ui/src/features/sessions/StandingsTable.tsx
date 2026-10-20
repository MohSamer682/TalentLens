import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import type { CandidateResultDto, Weights } from '@/types';
import { DIMENSIONS, cn, formatScore } from '@/lib/utils';
import { stagger } from '@/lib/motion';
import { STAGE_LABEL, madeTheCut } from '@/features/pipeline/pipeline';
import { StageBadge } from '@/features/pipeline/StageBadge';

/**
 * The ranking, set like a league table. Candidates who made the cut (shortlisted or any later stage) get
 * the saffron position cell and a warm row wash, and a stage badge says where each one is now. The
 * shortlist need not be contiguous, so there is no cut-off line.
 */
export function StandingsTable({
  sessionId,
  candidates,
  weights,
  selected,
  onToggle,
  onToggleAll,
}: {
  sessionId: string;
  candidates: CandidateResultDto[];
  weights?: Weights;
  /** Leave out the three selection props for a read-only table (someone who can view but not review). */
  selected?: Set<string>;
  onToggle?: (id: string) => void;
  onToggleAll?: (ids: string[]) => void;
}) {
  const head = 'px-3 py-2 text-[13px] font-medium text-ink-muted';
  const pageIds = candidates.map((c) => c.id);
  const selectedOnPage = selected ? pageIds.filter((id) => selected.has(id)).length : 0;

  return (
    <div className="relative overflow-x-auto">
      <table className="w-full border-collapse text-sm sm:min-w-[680px] lg:min-w-0">
        <caption className="sr-only">
          Candidates ranked by weighted score. Shortlisted candidates are marked.
        </caption>
        <thead className="border-b border-line bg-sunken">
          <tr>
            {onToggleAll && <th scope="col" className="w-10 pl-4 sm:pl-5">
              <input
                type="checkbox"
                aria-label="Select all on this page"
                checked={selectedOnPage > 0 && selectedOnPage === pageIds.length}
                ref={(el) => {
                  if (el) el.indeterminate = selectedOnPage > 0 && selectedOnPage < pageIds.length;
                }}
                onChange={() => onToggleAll(pageIds)}
                className="size-[18px] accent-[rgb(var(--ink))]"
              />
            </th>}
            <th scope="col" className={cn(head, 'w-16 text-center', !onToggleAll && 'pl-4 sm:pl-5')}>
              Rank
            </th>
            <th scope="col" className={cn(head, 'text-left')}>
              Candidate
            </th>
            <th scope="col" className={cn(head, 'hidden w-36 text-left md:table-cell')}>
              Stage
            </th>
            {DIMENSIONS.map((d) => (
              <th key={d.key} scope="col" className={cn(head, 'hidden w-16 text-right sm:table-cell')}>
                <abbr title={d.label} className="no-underline">
                  {d.short}
                </abbr>
                {weights && <span className="num block text-[11px] font-medium normal-case tracking-normal">{weights[d.key]}%</span>}
              </th>
            ))}
            <th scope="col" className={cn(head, 'w-20 text-right lg:w-36')}>
              Score
            </th>
            <th scope="col" className="w-10 pr-4 sm:pr-5">
              <span className="sr-only">Open</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {candidates.map((c, i) => {
            const inZone = madeTheCut(c.reviewStatus);
            return (
                <tr key={c.id} style={stagger(i)} className={cn('group relative animate-fade-up border-b border-line transition-colors', inZone ? 'bg-mark-soft/40 hover:bg-mark-soft/70' : 'hover:bg-ground')}>
                  {onToggle && selected && <td className="relative z-10 py-2.5 pl-4 sm:pl-5">
                    <input
                      type="checkbox"
                      aria-label={`Select ${c.name || 'candidate ranked ' + c.rank}`}
                      checked={selected.has(c.id)}
                      onChange={() => onToggle(c.id)}
                      className="size-[18px] accent-[rgb(var(--ink))]"
                    />
                  </td>}
                  <td className={cn('px-2 py-2.5', !onToggle && 'pl-4 sm:pl-5')}>
                    <span
                      className={cn(
                        'display mx-auto flex h-7 min-w-8 items-center justify-center px-1.5 text-base font-bold transition-colors duration-300',
                        inZone ? 'marker' : 'text-ink-muted'
                      )}
                    >
                      {c.rank}
                    </span>
                    {inZone && <span className="sr-only">, {STAGE_LABEL[c.reviewStatus]}</span>}
                  </td>
                  <td className="max-w-0 px-3 py-2.5">
                    <Link
                      to={`/sessions/${sessionId}/candidate/${c.id}`}
                      className="block truncate font-semibold after:absolute after:inset-0 after:content-[''] focus-visible:outline-none group-focus-within:underline"
                    >
                      {c.name || 'Name not found'}
                    </Link>
                    <p className="truncate text-[13px] text-ink-muted">{c.email || 'No email found in resume'}</p>
                  </td>
                  <td className="hidden px-3 py-2.5 md:table-cell">
                    {inZone && <StageBadge stage={c.reviewStatus} />}
                  </td>
                  {DIMENSIONS.map((d) => (
                    <td key={d.key} className={cn('num hidden px-3 py-2.5 text-right sm:table-cell', c[d.key] < 50 ? 'text-danger' : 'text-ink')}>
                      {formatScore(c[d.key])}
                    </td>
                  ))}
                  <td className="px-3 py-2.5">
                    <div className="flex items-center justify-end gap-2.5">
                      <span aria-hidden="true" className="hidden h-1.5 w-14 overflow-hidden rounded-full bg-line lg:block">
                        <span
                          className={cn('block h-full origin-left animate-grow-x rounded-full transition-colors duration-300', inZone ? 'bg-accent-bar' : 'bg-line-strong')}
                          style={{ width: `${Math.max(0, Math.min(100, c.compositeScore))}%`, animationDelay: `${Math.min(i * 35, 420) + 150}ms` }}
                        />
                      </span>
                      <span className="display w-12 text-right text-[17px]" style={{ fontWeight: Math.round(380 + Math.max(0, Math.min(100, c.compositeScore)) * 4.4) }}>{formatScore(c.compositeScore)}</span>
                    </div>
                  </td>
                  <td className="pr-4 text-ink-muted sm:pr-5">
                    <ChevronRight className="ml-auto size-4" aria-hidden="true" />
                  </td>
                </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
