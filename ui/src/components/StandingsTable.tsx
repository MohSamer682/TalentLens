import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import type { CandidateResultDto, Weights } from '@/types';
import { DIMENSIONS, cn, formatScore } from '@/lib/utils';
import { stagger } from '@/lib/motion';

/**
 * The ranking, set like a league table. Rows at or above `shortlistSize` sit in the shortlist zone:
 * their position cell is filled, and a labelled rule is drawn under the last one, like a qualification line.
 */
export function StandingsTable({
  sessionId,
  candidates,
  weights,
  shortlistSize,
}: {
  sessionId: string;
  candidates: CandidateResultDto[];
  weights?: Weights;
  shortlistSize: number;
}) {
  const head = 'px-3 py-2 text-[13px] font-medium text-ink-muted';

  return (
    <div className="relative overflow-x-auto">
      <table className="w-full border-collapse text-sm sm:min-w-[680px] lg:min-w-0">
        <caption className="sr-only">
          Candidates ranked by weighted score. The top {shortlistSize} are inside the shortlist.
        </caption>
        <thead className="border-b border-line bg-sunken">
          <tr>
            <th scope="col" className={cn(head, 'w-14 pl-4 text-center sm:pl-5')}>
              Rank
            </th>
            <th scope="col" className={cn(head, 'text-left')}>
              Candidate
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
            const inZone = c.rank <= shortlistSize;
            const lastInZone = c.rank === shortlistSize;
            return (
              <Fragment key={c.id}>
                <tr style={stagger(i)} className={cn('group relative animate-fade-up border-b border-line transition-colors', inZone ? 'bg-mark-soft/40 hover:bg-mark-soft/70' : 'hover:bg-ground')}>
                  <td className="py-2.5 pl-4 sm:pl-5">
                    <span
                      className={cn(
                        'display mx-auto flex h-7 min-w-8 items-center justify-center px-1.5 text-base font-bold transition-colors duration-300',
                        inZone ? 'marker' : 'text-ink-muted'
                      )}
                    >
                      {c.rank}
                    </span>
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
                {lastInZone && (
                  <tr aria-hidden="true">
                    <td colSpan={DIMENSIONS.length + 4} className="p-0">
                      <div key={shortlistSize} style={{ animationDelay: `${Math.min(i * 35, 420) + 200}ms` }} className="relative flex h-7 origin-left animate-grow-x items-center px-4 sm:px-5">
                        <span className="absolute inset-x-0 top-1/2 border-t border-mark" />
                        <span className="relative animate-fade-in rounded-sm bg-mark px-2 py-0.5 text-[12px] font-semibold text-mark-ink">
                          The call: top {shortlistSize} shortlisted
                        </span>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
